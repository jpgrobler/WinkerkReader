package za.co.jpsoft.winkerkreader.ui.controllers

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.winkerkEntry.WINKERK_DB
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DropboxDownloadController(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onFileReady: suspend (Uri) -> Unit,
    private val onError: (String) -> Unit,
    private val onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null
) {
    private val tag = "DropboxDownloadController"

    private var receiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false
    private var downloadId = 0L
    private var tempFile: File? = null
    private var httpDownloadJob: kotlinx.coroutines.Job? = null

    private val pollingHandler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    private var isPolling = false

    val isActive: Boolean get() = downloadId != 0L || httpDownloadJob?.isActive == true

    fun startDownload(url: String) {
        if (context.isFinishing()) return
        cancel() // clean up previous downloads

        val externalDir = context.getExternalFilesDir(null) ?: run {
            onError("Geen eksterne berging beskikbaar nie")
            return
        }
        tempFile = File(externalDir, "WinkerkReader_temp.db").also { it.parentFile?.mkdirs() }
        if (BuildConfig.DEBUG) {
            Log.d(tag, "Temp file will be saved to: ${tempFile?.absolutePath}")
        }

        // ─── Attempt HTTP download first ──────────────────────────────────────
        httpDownloadJob = lifecycleScope.launch {
            val success = tryHttpDownload(url)
            if (success) {
                // HTTP download succeeded – pass the temp file to import
                val file = tempFile ?: return@launch
                val uri = Uri.fromFile(file)
                if (BuildConfig.DEBUG) Log.d(tag, "HTTP download succeeded, calling onFileReady")
                onFileReady(uri)
            } else {
                // HTTP failed (HTML or error) – fallback to DownloadManager
                if (BuildConfig.DEBUG) Log.d(
                    tag,
                    "HTTP download failed, falling back to DownloadManager"
                )
                startDownloadManager(url)
            }
        }
    }

    /**
     * Attempts to download the file directly using HttpURLConnection.
     * Returns true if the download succeeded and the file is a valid binary (not HTML).
     */
    private suspend fun tryHttpDownload(url: String): Boolean = withContext(Dispatchers.IO) {
        val tempFile = tempFile ?: return@withContext false
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                // ─── Browser-like headers ─────────────────────────────────────
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                setRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
                )
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Referer", url)  // send the original URL as referer
                setRequestProperty("Accept-Encoding", "identity") // avoid compression
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true // automatically follow redirects
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                if (BuildConfig.DEBUG) Log.e(tag, "HTTP error: $responseCode")
                return@withContext false
            }

            // ─── Check content type ───────────────────────────────────────────
            val contentType = connection.contentType ?: ""
            if (contentType.contains("text/html") || contentType.contains("text/plain")) {
                if (BuildConfig.DEBUG) Log.e(
                    tag,
                    "HTML/plain response (likely a login page), aborting"
                )
                return@withContext false
            }

            val contentLength = connection.contentLength.toLong()
            if (contentLength < 512) {
                if (BuildConfig.DEBUG) Log.e(tag, "File too small ($contentLength bytes), aborting")
                return@withContext false
            }

            // ─── Download and write to temp file ─────────────────────────────
            val input = connection.inputStream
            val output = FileOutputStream(tempFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                onProgress?.invoke(totalRead, contentLength)
            }
            output.close()
            input.close()

            if (BuildConfig.DEBUG) {
                Log.d(tag, "HTTP download complete: ${tempFile.length()} bytes")
            }
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(tag, "HTTP download failed", e)
            tempFile.delete()
            false
        }
    }

    /**
     * Original DownloadManager-based download (fallback).
     */
    private fun startDownloadManager(url: String) {
        if (context.isFinishing()) return

        val tempFile = tempFile ?: run {
            onError("Geen tydelike lêer beskikbaar")
            return
        }

        registerReceiver()

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setTitle(WINKERK_DB)
            setMimeType("application/vnd.sqlite3")
            setDestinationUri(Uri.fromFile(tempFile))
            addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = manager.enqueue(request)

        if (downloadId <= 0) {
            onError("Aflaai kon nie begin nie (ID=$downloadId)")
            downloadId = 0L
            return
        }

        if (BuildConfig.DEBUG) Log.d(tag, "DownloadManager enqueued ID=$downloadId")
        startPolling(downloadId)
    }

    fun cancel() {
        httpDownloadJob?.cancel()
        httpDownloadJob = null
        stopPolling()
        if (downloadId != 0L) {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager)
                ?.remove(downloadId)
            downloadId = 0L
        }
        unregisterReceiver()
        tempFile?.delete()
        tempFile = null
    }

    // ─── DownloadManager receiver (unchanged, but now only used as fallback) ──

    private fun registerReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (BuildConfig.DEBUG) Log.d(tag, "BroadcastReceiver triggered")
                val manager =
                    ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
                val ref = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId != ref) return

                stopPolling()

                val query = DownloadManager.Query().setFilterById(ref)
                manager.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        onError("Kon nie aflaaistatus lees nie")
                        cleanup()
                        return
                    }
                    val status =
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        val reason = try {
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        } catch (_: Exception) {
                            -1
                        }
                        val msg = when (status) {
                            DownloadManager.STATUS_FAILED -> "Misluk (rede $reason)"
                            DownloadManager.STATUS_PAUSED -> "Gepauseer (rede $reason)"
                            else -> "Onbekende status $status"
                        }
                        onError("Aflaai $msg")
                        cleanup()
                        return
                    }
                    val uri = manager.getUriForDownloadedFile(ref) ?: run {
                        onError("Aflaaileer nie gevind nie")
                        cleanup()
                        return
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(tag, "DownloadManager URI: $uri")
                        val file = File(uri.path ?: "")
                        if (file.exists()) {
                            Log.d(tag, "File exists: ${file.absolutePath}, size: ${file.length()}")
                        } else {
                            Log.e(tag, "File does NOT exist at URI path: ${uri.path}")
                        }
                    }
                    val capturedId = downloadId
                    downloadId = 0L
                    lifecycleScope.launch {
                        onFileReady(uri)
                    }
                    unregisterReceiver()
                }
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_EXPORTED
        } else {
            0
        }
        context.registerReceiver(receiver, filter, flags)
        isReceiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            isReceiverRegistered = false
            receiver = null
        }
    }

    private fun startPolling(id: Long) {
        stopPolling()
        isPolling = true
        val runnable = object : Runnable {
            override fun run() {
                if (!isPolling) return
                logStatus(id)

                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(id)
                manager.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        isPolling = false; return
                    }

                    val status =
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val bytes =
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total =
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    onProgress?.invoke(bytes, total)

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            isPolling = false
                            // Receiver will handle success
                        }
                        DownloadManager.STATUS_FAILED -> {
                            isPolling = false
                            onError("Aflaai misluk")
                        }
                        else -> pollingHandler.postDelayed(this, 2000)
                    }
                }
            }
        }
        pollingRunnable = runnable
        pollingHandler.post(runnable)
    }

    private fun stopPolling() {
        isPolling = false
        pollingRunnable?.let { pollingHandler.removeCallbacks(it) }
        pollingRunnable = null
    }

    private fun cleanup() {
        tempFile?.delete()
        tempFile = null
        downloadId = 0L
        unregisterReceiver()
    }

    private fun logStatus(id: Long) {
        if (!BuildConfig.DEBUG) return
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) {
                if (BuildConfig.DEBUG) Log.e(tag, "No download found for ID $id"); return
            }
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = try {
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            } catch (_: Exception) {
                -1
            }
            val bytes =
                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total =
                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val text = when (status) {
                DownloadManager.STATUS_PENDING -> "Pending"
                DownloadManager.STATUS_RUNNING -> "Running"
                DownloadManager.STATUS_PAUSED -> "Paused (reason=$reason)"
                DownloadManager.STATUS_SUCCESSFUL -> "Successful"
                DownloadManager.STATUS_FAILED -> "Failed (reason=$reason)"
                else -> "Unknown ($status)"
            }
            if (BuildConfig.DEBUG) Log.d(tag, "Poll: $text, $bytes/$total bytes")
        }
    }

    private fun Context.isFinishing(): Boolean =
        (this as? android.app.Activity)?.isFinishing == true ||
                (this as? android.app.Activity)?.isDestroyed == true
}