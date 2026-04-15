package za.co.jpsoft.winkerkreader.workers


import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.Socket
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.io.use
import kotlin.use
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.delay
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WINKERK_DB
import za.co.jpsoft.winkerkreader.data.WinkerkDbHelper

class PhotoDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    // Add this function inside PhotoDownloadWorker
    private fun getAllMemberGuids(): List<String> {
        val guids = mutableListOf<String>()
        try {
            val helper = WinkerkDbHelper.getInstance(applicationContext, WINKERK_DB)
            val db = helper.readableDatabase
            val cursor = db.query("Members", arrayOf("MemberGUID"), null, null, null, null, null)
            while (cursor.moveToNext()) {
                val guid = cursor.getString(0)
                if (!guid.isNullOrEmpty()) {
                    guids.add(guid)
                }
            }
            cursor.close()
            db.close()
        } catch (e: Exception) {
            Log.e("PhotoDownloadWorker", "Failed to query database", e)
        }
        return guids
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Kry invoerdata

        Log.d("PhotoDownloadWorker", "doWork started")
        val serverIp = inputData.getString("SERVER_IP") ?: return@withContext Result.failure(workDataOf("ERROR" to "Geen IP adres")).also {
            Log.e("PhotoDownloadWorker", "No server IP")
        }
        Log.d("PhotoDownloadWorker", "Server IP: $serverIp")

        // 👇 NEW: get list of available GUIDs from PC
        val availableGuids = mutableSetOf<String>()
        try {
            Log.d("PhotoDownloadWorker", "Connecting to $serverIp:49517 for LIST")
            Socket(serverIp, 49517).use { listSocket ->
                // Send "LIST" command
                listSocket.getOutputStream().write("LIST\n".toByteArray())
                val reader = listSocket.getInputStream().bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                    availableGuids.add(line!!)
                }
            }
            Log.d("PhotoDownloadWorker", "PC has ${availableGuids.size} photos available")
        } catch (e: Exception) {
            Log.e("PhotoDownloadWorker", "Failed to get photo list from PC", e)
            return@withContext Result.failure(workDataOf("ERROR" to "Could not retrieve photo list"))
        }

        // 👇 get all GUIDs from local database
        val dbGuids = getAllMemberGuids()
        Log.d("PhotoDownloadWorker", "Database has ${dbGuids.size} members")

        // 👇 intersect: only download photos that exist on PC AND are in the database
        val guidsToDownload = dbGuids.intersect(availableGuids)
        Log.d("PhotoDownloadWorker", "Will attempt to download ${guidsToDownload.size} photos")


        val forceSync = inputData.getBoolean("FORCE_SYNC", false)

        if (guidsToDownload.isNotEmpty()) {
            Log.d("PhotoDownloadWorker", "First GUID: ${guidsToDownload.first()}")
        }
        // After computing guidsToDownload
        val photoDir = File(applicationContext.getExternalFilesDir(null), "photos")
        photoDir.mkdirs()

// Separate GUIDs into those that need downloading and those already present
        val toDownload = mutableListOf<String>()
        val alreadyPresent = mutableListOf<String>()

        for (guid in guidsToDownload) {
            val destFile = File(photoDir, "$guid.jpg")
            if (forceSync || !destFile.exists()) {
                toDownload.add(guid)
            } else {
                alreadyPresent.add(guid)
            }
        }

        Log.d("PhotoDownloadWorker", "Already present: ${alreadyPresent.size}, to download: ${toDownload.size}")

// Notify PC how many photos will actually be downloaded
        val count = toDownload.size
        try {
            Socket(serverIp, 49517).use { countSocket ->
                countSocket.getOutputStream().write("COUNT $count\n".toByteArray())
            }
            Log.d("PhotoDownloadWorker", "Sent COUNT=$count to PC")
        } catch (e: Exception) {
            Log.e("PhotoDownloadWorker", "Failed to send count", e)
        }

        var successCount = alreadyPresent.size  // count existing as successes
        var failCount = 0
        var processed = 0
        val total = toDownload.size

        for (guid in guidsToDownload) {
            if (isStopped) break

            val destFile = File(photoDir, "$guid.jpg")
            Log.d("PhotoDownloadWorker", "Processing $guid, exists=${destFile.exists()}, forceSync=$forceSync")
            if (!forceSync && destFile.exists()) {
                successCount++
            } else {
                val downloaded = downloadPhoto(serverIp, guid, destFile)
                if (downloaded) successCount++ else failCount++
            }
            processed++
            setProgress(workDataOf("progress" to processed, "total" to total, "currentGuid" to guid))
        }
        val outputData = workDataOf(
            "SUCCESS_COUNT" to successCount,
            "FAIL_COUNT" to failCount
        )
        Result.success(outputData)
    }

    private suspend fun downloadPhoto(serverIp: String, guid: String, destFile: File): Boolean {
        return suspendCoroutine { continuation ->
            Thread {
                Log.d("PhotoDownload", "Thread started for $guid")
                var success = false
                try {
                    Log.d("PhotoDownload", "Connecting data socket for $guid")
                    Socket(serverIp, 49517).apply { soTimeout = 30000 }.use { dataSocket ->
                        Log.d("PhotoDownload", "Data socket connected for $guid")

                        Log.d("PhotoDownload", "Connecting ACK socket for $guid")
                        Socket(serverIp, 49518).apply { soTimeout = 30000 }.use { ackSocket ->
                            Log.d("PhotoDownload", "ACK socket connected for $guid")

                            Log.d("PhotoDownload", "Connecting checksum socket for $guid")
                            Socket(serverIp, 49519).apply { soTimeout = 30000 }.use { checksumSocket ->
                                Log.d("PhotoDownload", "Checksum socket connected for $guid")

                                val dataOut = dataSocket.getOutputStream()
                                val ackIn = ackSocket.getInputStream().bufferedReader()
                                val ackOut = ackSocket.getOutputStream()
                                val checksumIn = checksumSocket.getInputStream().bufferedReader()

                                Log.d("PhotoDownload", "Sending GUID $guid")
                                dataOut.write("$guid\n".toByteArray())
                                dataOut.flush()

                                val ack1 = ackIn.readLine()
                                Log.d("PhotoDownload", "Received ACK1: $ack1")
                                if (ack1 != "ACK") throw Exception("Geen ACK vir GUID")

                                val sizeStr = ackIn.readLine() ?: throw Exception("Geen lêergrootte")
                                val fileSize = sizeStr.toLong()
                                Log.d("PhotoDownload", "File size: $fileSize")
                                ackOut.write("ACK\n".toByteArray())
                                ackOut.flush()

                                val bufferSizeStr = ackIn.readLine() ?: throw Exception("Geen buffer-grootte")
                                val bufferSize = bufferSizeStr.toInt()
                                Log.d("PhotoDownload", "Buffer size: $bufferSize")
                                ackOut.write("ACK\n".toByteArray())
                                ackOut.flush()

                                val buffer = ByteArray(bufferSize)
                                val fileOut = FileOutputStream(destFile)
                                var totalRead = 0L

                                try {
                                    while (totalRead < fileSize) {
                                        val remaining = fileSize - totalRead
                                        val expected = if (remaining >= bufferSize) bufferSize else remaining.toInt()
                                        var bytesRead = 0
                                        Log.d("PhotoDownload", "Reading chunk, expected $expected bytes")
                                        while (bytesRead < expected) {
                                            val result = dataSocket.getInputStream().read(buffer, bytesRead, expected - bytesRead)
                                            if (result == -1) throw Exception("Verbinding gesluit")
                                            bytesRead += result
                                        }
                                        Log.d("PhotoDownload", "Chunk received: $bytesRead bytes")

                                        ackOut.write("ACK\n".toByteArray())
                                        ackOut.flush()
                                        Log.d("PhotoDownload", "Sent chunk ACK")

                                        val serverChecksum = checksumIn.readLine() ?: throw Exception("Geen checksum")
                                        Log.d("PhotoDownload", "Server checksum: $serverChecksum")
                                        ackOut.write("ACK\n".toByteArray())
                                        ackOut.flush()
                                        Log.d("PhotoDownload", "Sent checksum ACK")

                                        val localChecksum = calculateChecksum(buffer, 0, bytesRead)
                                        Log.d("PhotoDownload", "Local checksum: $localChecksum")

                                        if (localChecksum != serverChecksum) {
                                            ackOut.write("ERROR\n".toByteArray())
                                            ackOut.flush()
                                            throw Exception("Checksum mismatch")
                                        }

                                        fileOut.write(buffer, 0, bytesRead)
                                        totalRead += bytesRead
                                        Log.d("PhotoDownload", "Progress: $totalRead / $fileSize")
                                    }
                                    success = true
                                    Log.d("PhotoDownload", "Download successful for $guid")
                                } finally {
                                    fileOut.close()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PhotoDownload", "Error for $guid", e)
                }
                Log.d("PhotoDownload", "Resuming continuation for $guid with success=$success")
                continuation.resume(success)
            }.start()
        }
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data, offset, length)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}