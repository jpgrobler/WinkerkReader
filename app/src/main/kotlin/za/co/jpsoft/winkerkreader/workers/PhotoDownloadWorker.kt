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
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WINKERK_DB
import za.co.jpsoft.winkerkreader.data.WinkerkDbHelper
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest

class PhotoDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PhotoDownloadWorker"
        private const val SOCKET_TIMEOUT_MS = 20000
        private const val MAX_RETRIES = 2
        private const val TRANSFER_SECRET = "Welkom in Sonnige Suid-Afrika@2026"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val serverIp = inputData.getString("SERVER_IP")
        val port = inputData.getInt("SERVER_PORT", 49514)
        val forceSync = inputData.getBoolean("FORCE_SYNC", false)
        val sharedSecret = TRANSFER_SECRET

        if (serverIp.isNullOrEmpty()) {
            Log.e(TAG, "No server IP")
            return@withContext Result.failure(workDataOf("ERROR" to "No server IP"))
        }

        val availableGuids = requestPhotoList(serverIp, port, sharedSecret)
        if (availableGuids == null) {
            Log.e(TAG, "Failed to get photo list")
            return@withContext Result.failure(workDataOf("ERROR" to "Failed to get photo list"))
        }
        Log.d(TAG, "📁 Server has ${availableGuids.size} photos")

        val dbGuids = getAllMemberGuids()
        Log.d(TAG, "📋 DB has ${dbGuids.size} member GUIDs")

        val toDownload = dbGuids.intersect(availableGuids).filter { guid ->
            val photoFile =
                File(WinkerkContract.winkerkEntry.getFotoDir(applicationContext), "$guid.jpg")
            forceSync || !photoFile.exists()
        }
        Log.d(TAG, "📌 Intersection (to download): ${toDownload.size}")
        if (toDownload.isNotEmpty()) {
            Log.d(TAG, "📌 First 5 GUIDs: ${toDownload.take(5).joinToString()}")
        }

        notifyPhotoCount(serverIp, port, toDownload.size)

        var success = 0
        var failed = 0
        toDownload.forEachIndexed { index, guid ->
            if (isStopped) {
                Log.w(TAG, "Worker stopped"); return@forEachIndexed
            }

            setProgress(
                workDataOf(
                    "progress" to (index + 1),
                    "total" to toDownload.size,
                    "currentGuid" to guid
                )
            )

            val dest =
                File(WinkerkContract.winkerkEntry.getFotoDir(applicationContext), "$guid.jpg")

            var downloaded = false
            for (attempt in 1..MAX_RETRIES) {
                if (isStopped) break
                Log.d(TAG, "⬇️ Downloading $guid (attempt $attempt/$MAX_RETRIES)")
                if (downloadPhoto(serverIp, port, guid, dest, sharedSecret)) {
                    downloaded = true
                    break
                }
                if (attempt < MAX_RETRIES) {
                    Log.w(TAG, "Retrying $guid (${MAX_RETRIES - attempt} attempts left)")
                    delay(1000)
                }
            }

            if (downloaded) success++ else failed++
            if (index < toDownload.size - 1) delay(200)
        }

        Log.d(TAG, "✅ Photo sync complete: $success downloaded, $failed failed")
        Result.success(workDataOf("SUCCESS_COUNT" to success, "FAIL_COUNT" to failed))
    }

    // Token is appended to the command so the server can validate it
    private suspend fun requestPhotoList(
        serverIp: String,
        port: Int,
        secret: String
    ): Set<String>? {
        return withContext(Dispatchers.IO) {
            try {
                Socket(serverIp, port).use { socket ->
                    socket.soTimeout = 30000
                    val bis = BufferedInputStream(socket.getInputStream())
                    val outputStream = socket.getOutputStream()

                    outputStream.write("LIST_PHOTOS $secret\n".toByteArray(Charsets.US_ASCII))
                    outputStream.flush()

                    val firstLine = bis.readLine()
                    if (firstLine?.startsWith("ERROR") == true) {
                        Log.e(TAG, "Server rejected LIST_PHOTOS: $firstLine")
                        return@use null
                    }

                    val guids = mutableSetOf<String>()
                    if (!firstLine.isNullOrEmpty()) guids.add(firstLine)  // first line already read
                    while (true) {
                        val line = bis.readLine() ?: break
                        if (line.isEmpty()) break
                        guids.add(line)
                    }
                    guids
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get photo list", e)
                null
            }
        }
    }

    private suspend fun downloadPhoto(
        serverIp: String, port: Int, guid: String, destFile: File, secret: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket(serverIp, port).apply { soTimeout = SOCKET_TIMEOUT_MS }

                val bis = BufferedInputStream(socket.getInputStream())
                val outputStream = socket.getOutputStream()

                // Token appended: GET_PHOTO <guid> <token>
                outputStream.write("GET_PHOTO $guid $secret\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                Log.d(TAG, "📤 Sent GET_PHOTO $guid")

                val statusLine = bis.readLine()
                if (statusLine == null) {
                    Log.w(TAG, "No status for $guid"); return@withContext false
                }
                Log.d(TAG, "📨 Status: $statusLine")

                val parts = statusLine.split(' ')
                if (parts[0] == "ERROR") {
                    Log.w(TAG, "Server error for $guid: ${parts.drop(1)}")
                    return@withContext false
                }
                if (parts[0] != "OK" || parts.size < 3) {
                    Log.w(TAG, "Invalid status for $guid: $statusLine")
                    return@withContext false
                }

                val fileSize = parts[1].toLongOrNull() ?: return@withContext false
                val bufferSize = parts[2].toIntOrNull() ?: return@withContext false
                Log.d(TAG, "📦 File size: $fileSize, buffer: $bufferSize")

                destFile.parentFile?.mkdirs()
                var success = false

                destFile.outputStream().use { fos ->
                    val buffer = ByteArray(bufferSize)
                    var received = 0L
                    Log.d(TAG, "⬇️ Starting to receive file data...")

                    while (received < fileSize) {
                        val toRead = minOf(buffer.size.toLong(), fileSize - received).toInt()
                        var bytesRead = 0
                        while (bytesRead < toRead) {
                            val read = bis.read(buffer, bytesRead, toRead - bytesRead)
                            if (read < 0) break
                            bytesRead += read
                        }
                        if (bytesRead == 0) {
                            Log.w(TAG, "Connection closed mid-transfer for $guid"); break
                        }
                        fos.write(buffer, 0, bytesRead)
                        received += bytesRead

                        val pct = received * 100 / fileSize
                        if (pct % 10 == 0L) Log.d(TAG, "📊 $guid: $pct%")
                    }
                    if (received == fileSize) {
                        success = true
                        Log.d(TAG, "✅ $guid: received $received bytes")
                    } else {
                        Log.w(TAG, "Size mismatch for $guid: got $received expected $fileSize")
                    }
                }

                if (!success) {
                    destFile.delete(); return@withContext false
                }

                Log.d(TAG, "🔐 Reading checksum...")
                val serverChecksum = bis.readLine()
                if (serverChecksum == null) {
                    Log.w(TAG, "No checksum for $guid"); destFile.delete(); return@withContext false
                }

                val localChecksum = destFile.inputStream().use { it.sha256Hex() }
                Log.d(TAG, "🔐 Match: ${serverChecksum == localChecksum}")

                if (serverChecksum != localChecksum) {
                    outputStream.write("ERROR\n".toByteArray(Charsets.US_ASCII))
                    outputStream.flush()
                    destFile.delete()
                    Log.w(TAG, "Checksum mismatch for $guid")
                    return@withContext false
                }

                outputStream.write("ACK\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                Log.d(TAG, "✅ Photo downloaded: $guid")
                true

            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "⏰ Timeout for $guid", e)
                destFile.delete(); false
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $guid", e)
                destFile.delete(); false
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun getAllMemberGuids(): List<String> {
        val guids = mutableListOf<String>()
        try {
            val db = WinkerkDbHelper.getInstance(applicationContext, WINKERK_DB).readableDatabase
            db.rawQuery(
                "SELECT MemberGUID FROM Members WHERE MemberGUID IS NOT NULL AND MemberGUID != ''",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.takeIf { it.isNotEmpty() }?.let { guids.add(it) }
                }
            }
            Log.d(TAG, "✅ Retrieved ${guids.size} member GUIDs from DB")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query member GUIDs", e)
        }
        return guids
    }

    private suspend fun notifyPhotoCount(serverIp: String, port: Int, count: Int) {
        try {
            Socket(serverIp, port).use { socket ->
                socket.getOutputStream()
                    .write("COUNT_PHOTOS $count\n".toByteArray(Charsets.US_ASCII))
                socket.getOutputStream().flush()
            }
        } catch (_: Exception) {
        }
    }

    // ── BufferedInputStream line reader ───────────────────────────────────

    private fun BufferedInputStream.readLine(): String? {
        val sb = StringBuilder()
        while (true) {
            val b = read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            val c = b.toChar()
            if (c == '\n') return sb.trimEnd('\r').toString()
            sb.append(c)
        }
    }

    private fun InputStream.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(8192)
        var read: Int
        while (this.read(buf).also { read = it } > 0) digest.update(buf, 0, read)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}