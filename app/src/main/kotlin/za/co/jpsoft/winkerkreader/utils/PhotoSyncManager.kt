package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.Socket
import java.security.MessageDigest

class PhotoSyncManager(private val context: Context, private val serverIp: String) {
    private val tag = "PhotoSyncManager"
    private val dataPort = 49517
    private val ackPort = 49518
    private val checksumPort = 49519
    private var photoDir: File? = null
    private var listener: SyncListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Coroutine scope tied to this manager – can be cancelled
    private var syncScope: CoroutineScope? = null

    init {
        val externalDir = context.getExternalFilesDir(null)
        photoDir = if (externalDir != null) File(externalDir, "photos") else null
    }

    interface SyncListener {
        fun onProgress(current: Int, total: Int, filename: String)
        fun onComplete(successCount: Int, failCount: Int)
        fun onError(message: String)
    }

    fun startSync(photoGuids: List<String>, listener: SyncListener) {
        // Cancel any ongoing sync first
        cancel()

        this.listener = listener

        if (photoDir == null) {
            notifyError("External storage not available")
            return
        }
        if (serverIp.isBlank()) {
            notifyError("Server IP is empty")
            return
        }
        photoDir!!.mkdirs()

        // Create a new coroutine scope with a SupervisorJob (allows cancellation)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        syncScope = scope

        scope.launch {
            var success = 0
            var failed = 0
            val total = photoGuids.size

            for ((index, guid) in photoGuids.withIndex()) {
                // Check cancellation before each download
                ensureActive()
                val file = File(photoDir, "$guid.jpg")
                if (file.exists()) {
                    success++
                    updateProgress(index + 1, total, "$guid.jpg (exists)")
                    continue
                }
                val result = downloadPhoto(guid, file)
                if (result) {
                    success++
                    updateProgress(index + 1, total, "$guid.jpg")
                } else {
                    failed++
                }
            }

            withContext(Dispatchers.Main) {
                listener.onComplete(success, failed)
                // Clear listener reference after completion
                this@PhotoSyncManager.listener = null
            }
        }.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                Log.d(tag, "Sync was cancelled")
                mainHandler.post {
                    listener?.onError("Synchronisation cancelled")
                    this@PhotoSyncManager.listener = null
                }
            }
            // Scope is about to be cleared, but we keep it until next start
        }
    }

    private suspend fun downloadPhoto(guid: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        // Check cancellation before establishing connections
        ensureActive()
        var dataSocket: Socket? = null
        var ackSocket: Socket? = null
        var checksumSocket: Socket? = null
        try {
            dataSocket = Socket(serverIp, dataPort).apply { soTimeout = 30000 }
            ackSocket = Socket(serverIp, ackPort).apply { soTimeout = 30000 }
            checksumSocket = Socket(serverIp, checksumPort).apply { soTimeout = 30000 }

            val dataOut = dataSocket.getOutputStream()
            val ackIn = ackSocket.getInputStream().bufferedReader()
            val ackOut = ackSocket.getOutputStream()
            val checksumIn = checksumSocket.getInputStream().bufferedReader()

            // Send GUID
            dataOut.write("$guid\n".toByteArray())
            dataOut.flush()

            // Wait for ACK
            val ack1 = ackIn.readLine()
            if (ack1 != "ACK") throw Exception("No ACK for GUID")

            // Receive file size
            val sizeStr = ackIn.readLine() ?: throw Exception("No file size")
            val fileSize = sizeStr.toLongOrNull() ?: throw Exception("Invalid file size")
            ackOut.write("ACK\n".toByteArray())
            ackOut.flush()

            // Receive buffer size
            val bufferSizeStr = ackIn.readLine() ?: throw Exception("No buffer size")
            val bufferSize = bufferSizeStr.toIntOrNull() ?: throw Exception("Invalid buffer size")
            ackOut.write("ACK\n".toByteArray())
            ackOut.flush()

            val buffer = ByteArray(bufferSize)
            val fileOut = FileOutputStream(destFile)
            var totalRead = 0L

            try {
                while (totalRead < fileSize) {
                    // Check cancellation inside the loop
                    ensureActive()
                    var bytesRead = 0
                    while (bytesRead < buffer.size) {
                        val read = dataSocket.getInputStream().read(buffer, bytesRead, buffer.size - bytesRead)
                        if (read == -1) throw Exception("Stream closed prematurely")
                        bytesRead += read
                        if (totalRead + bytesRead == fileSize) break
                    }

                    ackOut.write("ACK\n".toByteArray())
                    ackOut.flush()

                    val serverChecksum = checksumIn.readLine() ?: throw Exception("No checksum")
                    ackOut.write("ACK\n".toByteArray())
                    ackOut.flush()

                    val localChecksum = calculateChecksum(buffer, 0, bytesRead)
                    if (localChecksum != serverChecksum) {
                        ackOut.write("ERROR\n".toByteArray())
                        ackOut.flush()
                        throw Exception("Checksum mismatch")
                    }

                    fileOut.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                }
            } finally {
                fileOut.close()
            }
            true
        } catch (e: CancellationException) {
            Log.d(tag, "Download cancelled for $guid")
            throw e // propagate cancellation
        } catch (e: Exception) {
            Log.e(tag, "downloadPhoto error for $guid", e)
            false
        } finally {
            dataSocket?.close()
            ackSocket?.close()
            checksumSocket?.close()
        }
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data, offset, length)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun updateProgress(current: Int, total: Int, filename: String) {
        withContext(Dispatchers.Main) {
            listener?.onProgress(current, total, filename)
        }
    }

    private fun notifyError(message: String) {
        mainHandler.post {
            listener?.onError(message)
            listener = null
        }
    }

    fun cancel() {
        // Cancel the entire coroutine scope – this will cancel all child jobs
        syncScope?.cancel()
        syncScope = null
        // Clear listener reference immediately to avoid leaks
        listener = null
    }
}