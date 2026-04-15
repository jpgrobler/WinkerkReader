package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket
import java.security.MessageDigest

class PhotoSyncManager(private val context: Context, private val serverIp: String) {
    private val tag = "PhotoSyncManager"
    private val dataPort = 49517
    private val ackPort = 49518
    private val checksumPort = 49519
    private var photoDir: File? = null
    private var isCancelled = false
    private var listener: SyncListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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
        this.listener = listener
        if (photoDir == null) {
            mainHandler.post { listener.onError("External storage not available") }
            return
        }
        if (serverIp.isBlank()) {
            mainHandler.post { listener.onError("Server IP is empty") }
            return
        }
        photoDir!!.mkdirs()
        isCancelled = false

        CoroutineScope(Dispatchers.IO).launch {
            var success = 0
            var failed = 0
            val total = photoGuids.size

            photoGuids.forEachIndexed { index, guid ->
                if (isCancelled) return@launch
                val file = File(photoDir, "$guid.jpg")
                if (file.exists()) {
                    success++
                    updateProgress(index + 1, total, "$guid.jpg (exists)")
                    return@forEachIndexed
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
            }
        }
    }

    private suspend fun downloadPhoto(guid: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Socket(serverIp, dataPort).use { dataSocket ->
                Socket(serverIp, ackPort).use { ackSocket ->
                    Socket(serverIp, checksumPort).use { checksumSocket ->
                        val dataOut = dataSocket.getOutputStream()
                        val ackOut = ackSocket.getOutputStream()
                        val ackIn = ackSocket.getInputStream().bufferedReader()
                        val checksumIn = checksumSocket.getInputStream().bufferedReader()

                        // Send GUID
                        dataOut.write("$guid\n".toByteArray())
                        dataOut.flush()

                        // Wait for ACK on request
                        val ack1 = ackIn.readLine()
                        if (ack1 == null || ack1 != "ACK") return@use false

                        // Receive file size
                        val sizeStr = ackIn.readLine() ?: return@use false
                        val fileSize = sizeStr.toLongOrNull() ?: return@use false
                        ackOut.write("ACK\n".toByteArray())
                        ackOut.flush()

                        // Receive buffer size
                        val bufferSizeStr = ackIn.readLine() ?: return@use false
                        val bufferSize = bufferSizeStr.toIntOrNull() ?: return@use false
                        ackOut.write("ACK\n".toByteArray())
                        ackOut.flush()

                        val buffer = ByteArray(bufferSize)
                        val fileOut = FileOutputStream(destFile)
                        var totalRead = 0L

                        try {
                            while (totalRead < fileSize) {
                                val bytesRead = dataSocket.getInputStream().read(buffer)
                                if (bytesRead == -1) throw IOException("Stream closed prematurely")

                                ackOut.write("ACK\n".toByteArray())
                                ackOut.flush()

                                val serverChecksum = checksumIn.readLine()
                                if (serverChecksum == null) throw IOException("No checksum received")
                                ackOut.write("ACK\n".toByteArray())
                                ackOut.flush()

                                val localChecksum = calculateChecksum(buffer, 0, bytesRead)
                                if (localChecksum != serverChecksum) {
                                    ackOut.write("ERROR\n".toByteArray())
                                    ackOut.flush()
                                    throw IOException("Checksum mismatch")
                                }
                                ackOut.write("ACK\n".toByteArray())
                                ackOut.flush()

                                fileOut.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                            }
                        } finally {
                            fileOut.close()
                        }
                        true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "downloadPhoto error for $guid", e)
            false
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

    fun cancel() {
        isCancelled = true
    }
}