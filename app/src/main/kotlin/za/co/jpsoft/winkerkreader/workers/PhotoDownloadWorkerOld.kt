package za.co.jpsoft.winkerkreader.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract
import za.co.jpsoft.winkerkreader.data.members.setup.WinkerkDatabase
import java.io.File
import java.io.FileOutputStream
import java.net.Socket

class PhotoDownloadWorkerOld(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    // ─── Replace the old DB query with Room ──────────────────────
    private fun getAllMemberGuids(): List<String> {
        return try {
            val dao = WinkerkDatabase.getInstance(applicationContext).memberDao()
            dao.getAllMemberGuids()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("PhotoDownloadWorkerOld", "Failed to query database", e)
            emptyList()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val serverIp = inputData.getString("SERVER_IP")
        if (serverIp.isNullOrEmpty()) {
            return@withContext Result.failure(workDataOf("ERROR" to "Geen IP adres"))
        }
        if (BuildConfig.DEBUG) Log.d("PhotoDownloadWorkerOld", "Server IP: $serverIp")

        val availableGuids = mutableSetOf<String>()
        try {
            Socket(serverIp, 49517).use { listSocket ->
                listSocket.getOutputStream().write("LIST\n".toByteArray())
                val reader = listSocket.getInputStream().bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                    availableGuids.add(line!!)
                }
            }
            if (BuildConfig.DEBUG) Log.d(
                "PhotoDownloadWorkerOld",
                "PC has ${availableGuids.size} photos available"
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("PhotoDownloadWorkerOld", "Failed to get photo list", e)
            return@withContext Result.failure(workDataOf("ERROR" to "Could not retrieve photo list"))
        }

        val dbGuids = getAllMemberGuids()
        val guidsToDownload = dbGuids.intersect(availableGuids)
        val forceSync = inputData.getBoolean("FORCE_SYNC", false)

        val photoDir = File(WinkerkContract.winkerkEntry.getFotoDir(applicationContext))
        if (!photoDir.exists() && !photoDir.mkdirs()) {
            return@withContext Result.failure(workDataOf("ERROR" to "Cannot create photo directory"))
        }

        val toDownload = guidsToDownload.filter { guid ->
            val destFile = File(photoDir, "$guid.jpg")
            forceSync || !destFile.exists()
        }

        // Notify PC how many photos will be downloaded
        try {
            Socket(serverIp, 49517).use { countSocket ->
                countSocket.getOutputStream().write("COUNT ${toDownload.size}\n".toByteArray())
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("PhotoDownloadWorkerOld", "Failed to send count", e)
        }

        var successCount = guidsToDownload.size - toDownload.size // already present count
        var failCount = 0
        var processed = 0
        val total = toDownload.size

        for (guid in toDownload) {
            if (isStopped) break
            val destFile = File(photoDir, "$guid.jpg")
            val downloaded = downloadPhoto(serverIp, guid, destFile)
            if (downloaded) successCount++ else failCount++
            processed++
            setProgress(
                workDataOf(
                    "progress" to processed,
                    "total" to total,
                    "currentGuid" to guid
                )
            )
        }
        Result.success(workDataOf("SUCCESS_COUNT" to successCount, "FAIL_COUNT" to failCount))
    }

    private suspend fun downloadPhoto(serverIp: String, guid: String, destFile: File): Boolean =
        withContext(Dispatchers.IO) {
            var dataSocket: Socket? = null
            var ackSocket: Socket? = null
            var checksumSocket: Socket? = null
            try {
                dataSocket = Socket(serverIp, 49517).apply { soTimeout = 30000 }
                ackSocket = Socket(serverIp, 49518).apply { soTimeout = 30000 }
                checksumSocket = Socket(serverIp, 49519).apply { soTimeout = 30000 }

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
                val bufferSize = bufferSizeStr.toIntOrNull() ?: throw Exception("Invalid buffer size")
                ackOut.write("ACK\n".toByteArray())
                ackOut.flush()

                val buffer = ByteArray(bufferSize)
                val fileOut = FileOutputStream(destFile)
                var totalRead = 0L
                try {
                    while (totalRead < fileSize) {
                        var bytesRead = 0
                        while (bytesRead < buffer.size) {
                            val read = dataSocket.getInputStream()
                                .read(buffer, bytesRead, buffer.size - bytesRead)
                            if (read == -1) throw Exception("Stream closed")
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
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("PhotoDownloadOld", "Error for $guid", e)
                false
            } finally {
                dataSocket?.close()
                ackSocket?.close()
                checksumSocket?.close()
            }
        }
}