package za.co.jpsoft.winkerkreader.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.Socket
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class FileDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val KEY_SERVER_IP = "SERVER_IP"
        const val KEY_SERVER_PORT = "SERVER_PORT"
        private const val TRANSFER_SECRET = "Welkom in Sonnige Suid-Afrika@2026"
        const val KEY_PROGRESS = "PROGRESS"
        const val KEY_FILE_PATH = "FILE_PATH"
        const val KEY_SUCCESS = "SUCCESS"
        const val KEY_ERROR = "ERROR"
        private const val TAG = "FileDownloadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val serverIp = inputData.getString(KEY_SERVER_IP)
        val serverPort = inputData.getInt(KEY_SERVER_PORT, 49514)
        val sharedSecret = TRANSFER_SECRET

        if (serverIp.isNullOrEmpty()) {
            if (BuildConfig.DEBUG) Log.e(TAG, "No server IP")
            return@withContext Result.failure(
                workDataOf(
                    KEY_SUCCESS to false,
                    KEY_ERROR to "No server IP"
                )
            )
        }

        var socket: Socket? = null
        var attempt = 0
        while (attempt < 5 && !isStopped) {
            try {
                socket = Socket(serverIp, serverPort).apply { soTimeout = 120000 }
                if (BuildConfig.DEBUG) Log.d(TAG, "Connected to $serverIp:$serverPort")
                break
            } catch (e: Exception) {
                attempt++
                if (BuildConfig.DEBUG) Log.w(TAG, "Connection attempt $attempt failed", e)
                if (attempt < 5) delay(2000)
            }
        }

        if (socket == null || isStopped) {
            return@withContext Result.failure(
                workDataOf(KEY_SUCCESS to false, KEY_ERROR to "Could not connect")
            )
        }

        val result = downloadFile(socket!!, sharedSecret)
        socket!!.close()

        if (result.first) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Download successful, file saved to ${result.second}")
            Result.success(workDataOf(KEY_SUCCESS to true, KEY_FILE_PATH to result.second))
        } else {
            if (BuildConfig.DEBUG) Log.e(TAG, "Download failed: ${result.third}")
            Result.failure(workDataOf(KEY_SUCCESS to false, KEY_ERROR to result.third))
        }
    }

    private suspend fun downloadFile(
        socket: Socket,
        sharedSecret: String
    ): Triple<Boolean, String, String?> =
        withContext(Dispatchers.IO) {
            try {
                val bis = BufferedInputStream(socket.getInputStream())
                val outputStream = socket.getOutputStream()

                // Send token with command
                outputStream.write("GET_DB $sharedSecret\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                if (BuildConfig.DEBUG) Log.d(TAG, "Sent GET_DB")

                val statusLine = bis.readLine()
                if (statusLine == null) return@withContext Triple(false, "", "No response")
                if (BuildConfig.DEBUG) Log.d(TAG, "Status: $statusLine")

                val parts = statusLine.split(' ')
                if (parts[0] == "ERROR") {
                    val msg = parts.drop(1).joinToString(" ")
                    return@withContext Triple(false, "", msg)
                }
                // Encrypted protocol: OK <encryptedSize> <bufferSize> <ivHex>
                if (parts.size < 4 || parts[0] != "OK") {
                    return@withContext Triple(false, "", "Invalid status line: $statusLine")
                }

                val encryptedSize = parts[1].toLongOrNull()
                    ?: return@withContext Triple(false, "", "Invalid encrypted size")
                val bufferSize = parts[2].toIntOrNull()
                    ?: return@withContext Triple(false, "", "Invalid buffer size")
                val ivHex = parts[3]
                if (BuildConfig.DEBUG) Log.d(TAG, "Encrypted size: $encryptedSize, IV: $ivHex")

                // ── Receive encrypted bytes ───────────────────────────────
                val encryptedBytes = ByteArray(encryptedSize.toInt())
                var received = 0L
                val buffer = ByteArray(bufferSize)

                while (received < encryptedSize) {
                    val toRead = minOf(buffer.size.toLong(), encryptedSize - received).toInt()
                    var bytesRead = 0
                    while (bytesRead < toRead) {
                        val read = bis.read(buffer, bytesRead, toRead - bytesRead)
                        if (read < 0) break
                        bytesRead += read
                    }
                    if (bytesRead == 0) break
                    System.arraycopy(buffer, 0, encryptedBytes, received.toInt(), bytesRead)
                    received += bytesRead

                    val progress = ((received * 100) / encryptedSize).toInt()
                    setProgress(workDataOf(KEY_PROGRESS to progress))
                }

                if (received != encryptedSize) {
                    return@withContext Triple(
                        false,
                        "",
                        "Size mismatch: got $received expected $encryptedSize"
                    )
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Encrypted bytes received: $received")

                // ── Verify checksum of encrypted bytes ───────────────────
                val serverChecksum = bis.readLine()
                if (serverChecksum == null) {
                    return@withContext Triple(false, "", "No checksum")
                }
                val localChecksum = encryptedBytes.sha256Hex()
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "Checksum match: ${serverChecksum == localChecksum}"
                )

                if (serverChecksum != localChecksum) {
                    outputStream.write("ERROR\n".toByteArray(Charsets.US_ASCII))
                    outputStream.flush()
                    return@withContext Triple(false, "", "Checksum mismatch")
                }

                outputStream.write("ACK\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()

                // ── Decrypt ───────────────────────────────────────────────
                val plainBytes = try {
                    decryptAes(encryptedBytes, deriveKey(sharedSecret), parseHex(ivHex))
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Decryption failed", e)
                    return@withContext Triple(false, "", "Decryption failed: ${e.message}")
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Decrypted DB size: ${plainBytes.size} bytes")

                // ── Write DB to disk ──────────────────────────────────────
                val dbPath = File(applicationContext.applicationInfo.dataDir, "databases")
                if (!dbPath.exists()) dbPath.mkdirs()
                val destFile = File(dbPath, "Winkerk.db.new")

//                withContext(Dispatchers.Main) {
//                    WinkerkDatabase.closeInstance()
//                    WinkerkDbHelper.closeInstance(WINKERK_DB)
//                    delay(200)
//                    System.gc()
//                }
                destFile.delete()

                FileOutputStream(destFile).use { it.write(plainBytes) }

                delay(200)
                Triple(true, destFile.absolutePath, null)

            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Download failed", e)
                Triple(false, "", e.message)
            }
        }

    // ── Crypto helpers ────────────────────────────────────────────────────

    /** SHA-256(secret) → 32-byte AES key (same derivation as C# server). */
    private fun deriveKey(secret: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(secret.toByteArray(Charsets.UTF_8))
    }

    private fun decryptAes(encrypted: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(encrypted)
    }

    private fun parseHex(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

//    private fun ByteArray.sha256Hex(): String {
//        val digest = MessageDigest.getInstance("SHA-256")
//        return digest.digest(this).joinToString("") { "%02x".format(it) }
//    }

    // ── BufferedInputStream line reader (no read-ahead leak) ─────────────

//    private fun BufferedInputStream.readLine(): String? {
//        val sb = StringBuilder()
//        while (true) {
//            val b = read()
//            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
//            val c = b.toChar()
//            if (c == '\n') return sb.trimEnd('\r').toString()
//            sb.append(c)
//        }
//    }
}