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
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WINKERK_DB
import java.io.File

/**
 * Manages cloud/Dropbox database downloads via [DownloadManager].
 *
 * Extracted from LaaiDatabasisActivity. Owns:
 *  - [BroadcastReceiver] registration/unregistration
 *  - Progress polling via [Handler]
 *  - Delegating the completed download Uri to [onFileReady]
 *
 * ── Wiring ───────────────────────────────────────────────────────────────────
 *
 *   dropboxController = DropboxDownloadController(
 *       context         = this,
 *       lifecycleScope  = lifecycleScope,
 *       onFileReady     = { uri -> importController.processDownloadedFile(uri) },
 *       onError         = { msg -> showError(msg) },
 *       onProgress      = { bytes, total ->
 *           binding.laaiBoodskap.text = "$bytes / $total bytes"
 *       }
 *   )
 *
 *   // In handleDropboxDownload():
 *   dropboxController.startDownload(url)
 *
 *   // In cancelOngoingDownloads():
 *   dropboxController.cancel()
 *
 *   // In onDestroy():
 *   dropboxController.cancel()
 */
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

    private val pollingHandler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    private var isPolling = false

    // ── Public API ────────────────────────────────────────────────────────────

    /** True when a download is in progress. */
    val isActive: Boolean get() = downloadId != 0L

    /**
     * Starts a DownloadManager download for [url], registers the completion
     * receiver, and begins progress polling.
     *
     * Was [LaaiDatabasisActivity.downloadFromDropBoxUrl].
     */
    fun startDownload(url: String) {
        if (context.isFinishing()) return

        cancel() // clean up any previous download

        val externalDir = context.getExternalFilesDir(null) ?: run {
            onError("Geen eksterne berging beskikbaar nie")
            return
        }
        tempFile = File(externalDir, "WinkerkReader_temp.db").also { it.parentFile?.mkdirs() }

        registerReceiver()

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setTitle(WINKERK_DB)
            setMimeType("application/vnd.sqlite3")
            setDestinationUri(Uri.fromFile(tempFile))
            addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
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

        if (BuildConfig.DEBUG) Log.d(tag, "Download enqueued ID=$downloadId")
        startPolling(downloadId)
    }

    /**
     * Cancels any active download, unregisters the receiver, and stops polling.
     * Safe to call when idle.
     */
    fun cancel() {
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

    // ── Receiver ──────────────────────────────────────────────────────────────

    private fun registerReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (BuildConfig.DEBUG) Log.d(tag, "BroadcastReceiver triggered")
                val manager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    ?: return
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
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
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
                    val capturedId = downloadId
                    downloadId = 0L
                    lifecycleScope.launch {
                        onFileReady(uri)
                    }
                    unregisterReceiver()
                }
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Context.RECEIVER_NOT_EXPORTED else 0
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

    // ── Polling ───────────────────────────────────────────────────────────────

    /**
     * Polls DownloadManager every 2 s and updates [onProgress].
     * Stops automatically on success or failure (BroadcastReceiver takes over on success).
     *
     * Was [LaaiDatabasisActivity.startProgressPolling].
     */
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

                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    val bytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    onProgress?.invoke(bytes, total)

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            isPolling = false
                            // BroadcastReceiver handles the success path
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

    /** Logs current download status — debug only. Was [LaaiDatabasisActivity.checkDownloadStatus]. */
    private fun logStatus(id: Long) {
        if (!BuildConfig.DEBUG) return
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) {
                Log.e(tag, "No download found for ID $id"); return
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
            Log.d(tag, "Poll: $text, $bytes/$total bytes")
        }
    }

    // Helper: Context doesn't have isFinishing(), route through Activity
    private fun Context.isFinishing(): Boolean =
        (this as? android.app.Activity)?.isFinishing == true ||
                (this as? android.app.Activity)?.isDestroyed == true
}