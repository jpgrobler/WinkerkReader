package za.co.jpsoft.winkerkreader.utils.network

import za.co.jpsoft.winkerkreader.utils.network.ChecksumSocketTransfer.downloadByGuid
import za.co.jpsoft.winkerkreader.utils.network.ChecksumSocketTransfer.downloadOverConnectedSockets
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/**
 * Shared implementation of the chunked-checksum socket transfer protocol used to pull
 * files (member photos, and the full congregation database) from the companion
 * WinkerkReader PC application over the local network.
 *
 * IMPORTANT: This is a *client* re-implementation of a fixed, existing wire protocol.
 * The server side (the PC app) is not under our control and is not being changed here,
 * so nothing about the byte-for-byte sequence of reads/writes below may be altered.
 * This class only removes the three near-identical hand-rolled copies of that protocol
 * that previously existed in PhotoSyncManager.downloadPhoto(), PhotoDownloadWorker.downloadPhoto(),
 * and FileDownloadWorker.downloadFile() — it does not change what goes over the wire,
 * in what order, on which socket, or how many times.
 *
 * Two distinct (but very similar) handshakes are preserved exactly as they were, because
 * the three original call sites were NOT byte-identical:
 *
 *  1. [downloadByGuid] — per-file download identified by a GUID (previously duplicated in
 *     PhotoSyncManager.downloadPhoto() and PhotoDownloadWorker.downloadPhoto()). The client
 *     sends "<guid>\n" on the data socket; size/buffer-size and every chunk acknowledgement
 *     are exchanged over the ACK socket. Exactly 2 ACKs are sent per chunk (chunk-received,
 *     checksum-received) — a checksum mismatch sends "ERROR" instead of a 3rd ACK.
 *
 *  2. [downloadOverConnectedSockets] — whole-file download over sockets the caller has
 *     already connected (previously duplicated in FileDownloadWorker.downloadFile(), which
 *     retains its own connection/retry logic — that is unrelated to the transfer protocol
 *     itself and is intentionally left where it was). No GUID is sent; size/buffer-size are
 *     read over the *data* socket and acks are written to the *ack* socket; unlike the
 *     per-GUID protocol, exactly 3 ACKs are sent per chunk (chunk-received,
 *     checksum-received, checksum-verified-ok), and there's a 3rd, unconditional setup ACK
 *     before the transfer loop starts. Both quirks are intentionally preserved.
 *
 * Both entry points share the same SHA-256 chunk-checksum verification and the same
 * "read until this chunk is full or the file is complete" inner read loop, which is what
 * this class actually consolidates.
 */
object ChecksumSocketTransfer {

    private val ACK_BYTES = "ACK\n".toByteArray()
    private val ERROR_BYTES = "ERROR\n".toByteArray()

    private fun OutputStream.writeAck() {
        write(ACK_BYTES)
        flush()
    }

    private fun OutputStream.writeError() {
        write(ERROR_BYTES)
        flush()
    }

    /** Convenience holder for the 3 sockets every transfer uses. */
    class SocketTrio(
        val dataSocket: Socket,
        val ackSocket: Socket,
        val checksumSocket: Socket
    ) {
        fun closeAll() {
            runCatching { dataSocket.close() }
            runCatching { ackSocket.close() }
            runCatching { checksumSocket.close() }
        }
    }

    /**
     * Opens the 3 sockets (data/ack/checksum, in that order) used by every transfer, with
     * [socketTimeoutMs] applied to each. If any socket fails to open, whichever ones did
     * open are closed before the exception is rethrown (a small safety improvement over the
     * original per-file code, which is not itself part of the wire protocol and cannot be
     * observed by the server).
     */
    fun connect(
        serverIp: String,
        dataPort: Int,
        ackPort: Int,
        checksumPort: Int,
        socketTimeoutMs: Int = 30_000
    ): SocketTrio {
        var dataSocket: Socket? = null
        var ackSocket: Socket? = null
        var checksumSocket: Socket? = null
        try {
            dataSocket = Socket(serverIp, dataPort).apply { soTimeout = socketTimeoutMs }
            ackSocket = Socket(serverIp, ackPort).apply { soTimeout = socketTimeoutMs }
            checksumSocket = Socket(serverIp, checksumPort).apply { soTimeout = socketTimeoutMs }
            return SocketTrio(dataSocket, ackSocket, checksumSocket)
        } catch (e: Exception) {
            runCatching { dataSocket?.close() }
            runCatching { ackSocket?.close() }
            runCatching { checksumSocket?.close() }
            throw e
        }
    }

    /** SHA-256 hex digest of data[offset, offset+length) — identical across all 3 original copies. */
    fun calculateChecksum(data: ByteArray, offset: Int, length: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data, offset, length)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // -------------------------------------------------------------------------------
    // Protocol 1: per-GUID download
    // (was PhotoSyncManager.downloadPhoto() / PhotoDownloadWorker.downloadPhoto())
    // -------------------------------------------------------------------------------

    /**
     * Downloads a single file identified by [guid] to [destFile].
     *
     * Wire sequence (unchanged from the original duplicated implementations):
     * ```
     * client -> data socket      : "<guid>\n"
     * server -> ack socket       : "ACK"                (guid accepted)
     * server -> ack socket       : "<fileSize>"
     * client -> ack socket       : "ACK"
     * server -> ack socket       : "<bufferSize>"
     * client -> ack socket       : "ACK"
     * -- per chunk, until fileSize bytes received --
     * client reads chunk from data socket (fills to bufferSize, or remaining file size)
     * client -> ack socket       : "ACK"                (chunk received)
     * server -> checksum socket  : "<sha256 hex>"
     * client -> ack socket       : "ACK"                (checksum received)
     * -- if local sha256 != server's --
     * client -> ack socket       : "ERROR", then abort
     * ```
     *
     * @param isActive polled once per chunk; return false to abort the transfer with a
     *   [java.util.concurrent.CancellationException]. Defaults to always-active — pass a
     *   real check only where the original call site did (PhotoSyncManager); leave the
     *   default everywhere else so no new cancellation behaviour is introduced.
     */
    suspend fun downloadByGuid(
        serverIp: String,
        guid: String,
        destFile: File,
        dataPort: Int,
        ackPort: Int,
        checksumPort: Int,
        socketTimeoutMs: Int = 30_000,
        isActive: () -> Boolean = { true }
    ) {
        val sockets = connect(serverIp, dataPort, ackPort, checksumPort, socketTimeoutMs)
        try {
            val dataOut = sockets.dataSocket.getOutputStream()
            val ackIn = sockets.ackSocket.getInputStream().bufferedReader()
            val ackOut = sockets.ackSocket.getOutputStream()
            val checksumIn = sockets.checksumSocket.getInputStream().bufferedReader()

            dataOut.write("$guid\n".toByteArray())
            dataOut.flush()

            val ack1 = ackIn.readLine()
            if (ack1 != "ACK") throw IOException("No ACK for GUID")

            val sizeStr = ackIn.readLine() ?: throw IOException("No file size")
            val fileSize = sizeStr.toLongOrNull() ?: throw IOException("Invalid file size")
            ackOut.writeAck()

            val bufferSizeStr = ackIn.readLine() ?: throw IOException("No buffer size")
            val bufferSize = bufferSizeStr.toIntOrNull() ?: throw IOException("Invalid buffer size")
            ackOut.writeAck()

            FileOutputStream(destFile).use { fileOut ->
                receiveChunks(
                    dataInputStream = sockets.dataSocket.getInputStream(),
                    ackOut = ackOut,
                    checksumIn = checksumIn,
                    fileSize = fileSize,
                    bufferSize = bufferSize,
                    outputStream = fileOut,
                    ackAfterVerifiedChunk = false,
                    streamClosedMessage = "Stream closed prematurely",
                    isActive = isActive
                )
            }
        } finally {
            sockets.closeAll()
        }
    }

    // -------------------------------------------------------------------------------
    // Protocol 2: download over sockets the caller already connected
    // (was FileDownloadWorker.downloadFile())
    // -------------------------------------------------------------------------------

    /**
     * Downloads a file to [destFile] over sockets the caller has already established.
     * FileDownloadWorker connects with its own retry/backoff loop before calling this and
     * closes the sockets itself afterwards — that connection-management logic is unrelated
     * to the transfer protocol and is intentionally left in FileDownloadWorker rather than
     * folded in here. [destFile]'s parent directory must already exist.
     *
     * Wire sequence (unchanged from the original duplicated implementation):
     * ```
     * server -> data socket   : "<fileSize>"
     * client -> ack socket    : "ACK"
     * server -> data socket   : "<bufferSize>"
     * client -> ack socket    : "ACK"
     * client -> ack socket    : "ACK"                 (unconditional 3rd setup ACK)
     * -- per chunk, until fileSize bytes received --
     * client reads chunk from data socket (fills to bufferSize, or remaining file size)
     * client -> ack socket    : "ACK"                 (chunk received)
     * server -> checksum socket : "<sha256 hex>"
     * client -> ack socket    : "ACK"                 (checksum received)
     * -- if local sha256 == server's --
     * client -> ack socket    : "ACK"                 (verified ok)
     * -- else --
     * client -> ack socket    : "ERROR", then abort
     * ```
     *
     * @param onChunkWritten invoked after each chunk is written to [destFile], with the
     *   running total and the total file size — used by the caller to report WorkManager
     *   progress. No-op by default. May suspend (e.g. to call `setProgress`).
     */
    suspend fun downloadOverConnectedSockets(
        dataSocket: Socket,
        ackSocket: Socket,
        checksumSocket: Socket,
        destFile: File,
        onChunkWritten: suspend (totalReceived: Long, fileSize: Long) -> Unit = { _, _ -> }
    ) {
        val inputStream = dataSocket.getInputStream()
        val reader = BufferedReader(InputStreamReader(inputStream))
        val ackOut = ackSocket.getOutputStream()
        val checksumReader = BufferedReader(InputStreamReader(checksumSocket.getInputStream()))

        val fileSizeStr = reader.readLine() ?: throw IOException("No file size received")
        val fileSize = fileSizeStr.toLongOrNull()
            ?: throw IOException("Invalid file size: $fileSizeStr")
        ackOut.writeAck()

        val bufferSizeStr = reader.readLine() ?: throw IOException("No buffer size received")
        val bufferSize = bufferSizeStr.toIntOrNull()
            ?: throw IOException("Invalid buffer size: $bufferSizeStr")
        ackOut.writeAck()

        // Unconditional 3rd setup ACK — present in the original protocol for this
        // transfer only. Looks redundant next to the per-GUID protocol; it is not — do
        // not remove it, the server side expects it and is not being changed.
        ackOut.writeAck()

        FileOutputStream(destFile).use { outputStream ->
            receiveChunks(
                dataInputStream = inputStream,
                ackOut = ackOut,
                checksumIn = checksumReader,
                fileSize = fileSize,
                bufferSize = bufferSize,
                outputStream = outputStream,
                ackAfterVerifiedChunk = true,
                streamClosedMessage = "Connection closed prematurely",
                onChunkWritten = onChunkWritten
            )
            outputStream.flush()
        }
    }

    // -------------------------------------------------------------------------------
    // Shared low-level chunk loop — the one piece of logic that was genuinely
    // byte-identical (modulo the ackAfterVerifiedChunk difference) across all 3
    // original implementations.
    // -------------------------------------------------------------------------------

    private suspend fun receiveChunks(
        dataInputStream: InputStream,
        ackOut: OutputStream,
        checksumIn: BufferedReader,
        fileSize: Long,
        bufferSize: Int,
        outputStream: OutputStream,
        ackAfterVerifiedChunk: Boolean,
        streamClosedMessage: String,
        isActive: () -> Boolean = { true },
        onChunkWritten: suspend (totalReceived: Long, fileSize: Long) -> Unit = { _, _ -> }
    ) {
        val buffer = ByteArray(bufferSize)
        var totalReceived = 0L

        while (totalReceived < fileSize) {
            if (!isActive()) {
                throw CancellationException("Transfer cancelled")
            }

            var bytesRead = 0
            while (bytesRead < buffer.size) {
                val read = dataInputStream.read(buffer, bytesRead, buffer.size - bytesRead)
                if (read == -1) throw IOException(streamClosedMessage)
                bytesRead += read
                if (totalReceived + bytesRead == fileSize) break
            }

            ackOut.writeAck() // chunk received

            val serverChecksum = checksumIn.readLine() ?: throw IOException("No checksum")
            ackOut.writeAck() // checksum received

            val localChecksum = calculateChecksum(buffer, 0, bytesRead)
            if (localChecksum != serverChecksum) {
                ackOut.writeError()
                throw IOException("Checksum mismatch")
            }

            if (ackAfterVerifiedChunk) {
                ackOut.writeAck() // checksum verified ok — whole-database protocol only
            }

            outputStream.write(buffer, 0, bytesRead)
            totalReceived += bytesRead
            onChunkWritten(totalReceived, fileSize)
        }
    }
}