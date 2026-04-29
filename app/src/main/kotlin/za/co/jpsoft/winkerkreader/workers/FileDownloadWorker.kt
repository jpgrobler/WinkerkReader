package za.co.jpsoft.winkerkreader.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkDbHelper
import za.co.jpsoft.winkerkreader.ui.activities.LaaiDatabasisActivity.Companion.DB_NAME
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class FileDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "file_download_work"
        const val KEY_SERVER_IP = "SERVER_IP"
        const val KEY_SERVER_PORT = "SERVER_PORT"
        const val KEY_PROGRESS = "PROGRESS"
        const val KEY_FILE_PATH = "FILE_PATH"
        const val KEY_SUCCESS = "SUCCESS"
        const val KEY_ERROR = "ERROR"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val serverIp = inputData.getString(KEY_SERVER_IP)
        val serverPort = inputData.getInt(KEY_SERVER_PORT, 49514)

        if (serverIp.isNullOrEmpty()) {
            val error = "No server IP provided"
            Log.e("FileDownloadWorker", error)
            return@withContext Result.failure(workDataOf(KEY_SUCCESS to false, KEY_ERROR to error))
        }

        val retryAttempts = AtomicInteger(5)
        val retryInterval = 2000L
        var socket: Socket? = null
        var ackSocket: Socket? = null
        var checksumSocket: Socket? = null
        var connected = false

        while (!connected && retryAttempts.get() > 0 && !isStopped) {
            try {
                socket = Socket(serverIp, serverPort).apply { soTimeout = 30000 }
                ackSocket = Socket(serverIp, serverPort + 1).apply { soTimeout = 30000 }
                checksumSocket = Socket(serverIp, serverPort + 2).apply { soTimeout = 30000 }
                connected = true
                Log.d("FileDownloadWorker", "Connected to server $serverIp:$serverPort")
            } catch (e: Exception) {
                val remaining = retryAttempts.decrementAndGet()
                Log.w("FileDownloadWorker", "Connection attempt failed, remaining: $remaining", e)
                if (remaining > 0 && !isStopped) {
                    delay(retryInterval)
                } else {
                    val error = "Failed to connect to $serverIp after retries"
                    return@withContext Result.failure(workDataOf(KEY_SUCCESS to false, KEY_ERROR to error))
                }
            }
        }

        if (!connected || isStopped) {
            return@withContext Result.failure(workDataOf(KEY_SUCCESS to false, KEY_ERROR to "Connection cancelled or stopped"))
        }

        // Safe non‑null assertions now that we know they are connected
        val result = downloadFile(socket!!, ackSocket!!, checksumSocket!!)
        socket?.close()
        ackSocket?.close()
        checksumSocket?.close()

        if (result.first) {
            Log.d("FileDownloadWorker", "Download successful, file saved to ${result.second}")
            // Force close any open database helpers to release the file
            WinkerkDbHelper.closeInstance(WinkerkContract.winkerkEntry.WINKERK_DB)
            WinkerkDbHelper.closeInstance(WinkerkContract.winkerkEntry.INFO_DB)
            // Trigger database reload via ContentProvider
            applicationContext.contentResolver.call(
                WinkerkContract.winkerkEntry.CONTENT_URI,
                "reloadDatabase",
                null,
                null
            )
            Result.success(workDataOf(KEY_SUCCESS to true, KEY_FILE_PATH to result.second))
        } else {
            val error = result.third ?: "Download failed"
            Log.e("FileDownloadWorker", error)
            Result.failure(workDataOf(KEY_SUCCESS to false, KEY_ERROR to error))
        }
    }

    private suspend fun downloadFile(
        dataSocket: Socket,
        ackSocket: Socket,
        checksumSocket: Socket
    ): Triple<Boolean, String, String?> = withContext(Dispatchers.IO) {
        var buffer = ByteArray(8192)
        var inputStream = dataSocket.getInputStream()
        var outputStream: BufferedOutputStream? = null
        val ackWriter = BufferedWriter(OutputStreamWriter(ackSocket.getOutputStream()))
        val ackReader = BufferedReader(InputStreamReader(ackSocket.getInputStream()))
        val checksumReader = BufferedReader(InputStreamReader(checksumSocket.getInputStream()))
        val reader = BufferedReader(InputStreamReader(inputStream))

        try {
            // Exchange file metadata
            val fileSizeStr = reader.readLine() ?: return@withContext Triple(false, "", "No file size received")
            val fileSize = fileSizeStr.toLongOrNull() ?: return@withContext Triple(false, "", "Invalid file size: $fileSizeStr")
            ackWriter.write("ACK\n")
            ackWriter.flush()

            val bufferSizeStr = reader.readLine() ?: return@withContext Triple(false, "", "No buffer size received")
            val bufferSize = bufferSizeStr.toIntOrNull() ?: return@withContext Triple(false, "", "Invalid buffer size: $bufferSizeStr")
            ackWriter.write("ACK\n")
            ackWriter.flush()

            buffer = ByteArray(bufferSize)
            ackWriter.write("ACK\n")
            ackWriter.flush()

            // Save directly to the app's internal databases folder
            val dbPath = File(applicationContext.applicationInfo.dataDir, "databases")
            if (!dbPath.exists() && !dbPath.mkdirs()) {
                return@withContext Triple(false, "", "Cannot create databases directory")
            }
            val destFile = File(dbPath, DB_NAME)
            // Delete existing file to ensure clean write
            if (destFile.exists() && !destFile.delete()) {
                Log.w("FileDownloadWorker", "Could not delete existing database file")
            }
            outputStream = BufferedOutputStream(FileOutputStream(destFile))

            var totalBytesReceived = 0L
            while (totalBytesReceived < fileSize) {
                var totalBytesRead = 0
                val chunkSize = buffer.size

                while (totalBytesRead < chunkSize) {
                    val bytesRead = inputStream.read(buffer, totalBytesRead, chunkSize - totalBytesRead)
                    if (bytesRead == -1) throw Exception("Connection closed prematurely")
                    totalBytesRead += bytesRead
                    if (totalBytesReceived + totalBytesRead.toLong() == fileSize) break
                }

                ackWriter.write("ACK\n")
                ackWriter.flush()

                val serverChecksum = checksumReader.readLine() ?: throw Exception("No checksum received")
                ackWriter.write("ACK\n")
                ackWriter.flush()

                val localChecksum = calculateChecksum(buffer, 0, totalBytesRead)
                if (localChecksum != serverChecksum) {
                    ackWriter.write("ERROR\n")
                    ackWriter.flush()
                    throw Exception("Checksum mismatch")
                }
                ackWriter.write("ACK\n")
                ackWriter.flush()

                outputStream.write(buffer, 0, totalBytesRead)
                totalBytesReceived += totalBytesRead

                val progress = (totalBytesReceived * 100 / fileSize).toInt()
                setProgress(workDataOf(KEY_PROGRESS to progress))
            }

            outputStream.flush()
            outputStream.close()
            Log.d("FileDownloadWorker", "File saved to ${destFile.absolutePath}")
            Triple(true, destFile.absolutePath, null)
        } catch (e: SocketTimeoutException) {
            Log.e("FileDownloadWorker", "Socket timeout during download", e)
            Triple(false, "", "Connection timeout: ${e.message}")
        } catch (e: Exception) {
            Log.e("FileDownloadWorker", "Download failed", e)
            Triple(false, "", "Download failed: ${e.message}")
        } finally {
            try {
                outputStream?.close()
                inputStream.close()
                ackWriter.close()
                ackReader.close()
                checksumReader.close()
                reader.close()
            } catch (_: Exception) {}
        }
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data, offset, length)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}