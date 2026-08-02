package za.co.jpsoft.winkerkreader.utils.files

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.workers.calculateChecksum
import java.io.File
import java.io.FileOutputStream
import java.net.Socket

class PhotoSyncManager(private val context: Context, private val serverIp: String) {
    private val tag = "PhotoSyncManager"
    private val dataPort = 49517
    private val ackPort = 49518
    private val checksumPort = 49519
    private var photoDir: File? = null
    private var listener: SyncListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Job that can be cancelled
    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    interface SyncListener {
        fun onProgress(current: Int, total: Int, filename: String)
        fun onComplete(successCount: Int, failCount: Int)
        fun onError(message: String)
    }

    init {
        val externalDir = context.getExternalFilesDir(null)
        photoDir = if (externalDir != null) File(externalDir, "photos") else null
    }

    fun startSync(photoGuids: List<String>, listener: SyncListener) {
        cancel() // cancel any previous sync
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

        // ✅ Store the Job first, then attach completion handler
        val job = scope.launch {
            var success = 0
            var failed = 0
            val total = photoGuids.size

            for ((index, guid) in photoGuids.withIndex()) {
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
                this@PhotoSyncManager.listener = null
            }
        }
        // Now assign to syncJob
        syncJob = job

        // Attach completion handler separately
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                mainHandler.post {
                    this@PhotoSyncManager.listener?.onError("Synchronisation cancelled")
                    this@PhotoSyncManager.listener = null
                }
            }
        }
    }

    private suspend fun downloadPhoto(guid: String, destFile: File): Boolean =
        withContext(Dispatchers.IO) {
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

                dataOut.write("$guid\n".toByteArray())
                dataOut.flush()

                val ack1 = ackIn.readLine()
                if (ack1 != "ACK") throw Exception("No ACK for GUID")

                val sizeStr = ackIn.readLine() ?: throw Exception("No file size")
                val fileSize = sizeStr.toLongOrNull() ?: throw Exception("Invalid file size")
                ackOut.write("ACK\n".toByteArray())
                ackOut.flush()

                val bufferSizeStr = ackIn.readLine() ?: throw Exception("No buffer size")
                val bufferSize =
                    bufferSizeStr.toIntOrNull() ?: throw Exception("Invalid buffer size")
                ackOut.write("ACK\n".toByteArray())
                ackOut.flush()

                val buffer = ByteArray(bufferSize)
                val fileOut = FileOutputStream(destFile)
                var totalRead = 0L

                try {
                    while (totalRead < fileSize) {
                        ensureActive()
                        var bytesRead = 0
                        while (bytesRead < buffer.size) {
                            val read = dataSocket.getInputStream()
                                .read(buffer, bytesRead, buffer.size - bytesRead)
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
                if (BuildConfig.DEBUG) Log.d(tag, "Download cancelled for $guid")
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(tag, "downloadPhoto error for $guid", e)
                false
            } finally {
                dataSocket?.close()
                ackSocket?.close()
                checksumSocket?.close()
            }
        }

//    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): String {
//        val digest = MessageDigest.getInstance("SHA-256")
//        digest.update(data, offset, length)
//        return digest.digest().joinToString("") { "%02x".format(it) }
//    }

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
        syncJob?.cancel()
        syncJob = null
        listener = null
    }
}