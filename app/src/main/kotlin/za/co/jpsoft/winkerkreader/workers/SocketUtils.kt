package za.co.jpsoft.winkerkreader.workers

import java.io.BufferedInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Shared socket and checksum utilities for the worker classes.
 *
 * Consolidates four private duplicates that were copy-pasted across:
 *  - FileDownloadWorker   (BufferedInputStream.readLine, ByteArray.sha256Hex)
 *  - PhotoDownloadWorker  (BufferedInputStream.readLine, InputStream.sha256Hex)
 *  - FileDownloadWorkerOld  (calculateChecksum)
 *  - PhotoDownloadWorkerOld (calculateChecksum)
 *  - PhotoSyncManager       (calculateChecksum)
 *
 * Usage after migration:
 *   - Remove the private copies from each worker class.
 *   - Add `import za.co.jpsoft.winkerkreader.workers.calculateChecksum`
 *     (or the relevant extension) in each caller.
 */

/**
 * Reads one line from a [BufferedInputStream] byte-by-byte, without the
 * read-ahead buffering that [java.io.BufferedReader] applies over raw sockets.
 *
 * Returns null only when the stream is at EOF before any character is read.
 * A CRLF sequence is collapsed to the string before the CR.
 */
fun BufferedInputStream.readLine(): String? {
    val sb = StringBuilder()
    while (true) {
        val b = read()
        if (b < 0) return if (sb.isEmpty()) null else sb.toString()
        val c = b.toChar()
        if (c == '\n') return sb.trimEnd('\r').toString()
        sb.append(c)
    }
}

/**
 * Returns the SHA-256 digest of this [ByteArray] as a lowercase hex string.
 * Equivalent to the server-side C# `BitConverter.ToString(sha256.ComputeHash(bytes))`.
 */
fun ByteArray.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(this).joinToString("") { "%02x".format(it) }
}

/**
 * Streams this [InputStream] through SHA-256 and returns the hex digest.
 * Used by [PhotoDownloadWorker] to checksum a just-written file without
 * loading it entirely into memory.
 */
fun InputStream.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(8192)
    var read: Int
    while (this.read(buf).also { read = it } > 0) digest.update(buf, 0, read)
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Computes SHA-256 over a slice [offset]..[offset+length) of [data] and
 * returns the lowercase hex string.
 *
 * Used in the old (v2) three-socket protocol where chunks are verified
 * individually rather than the whole encrypted payload at once.
 */
fun calculateChecksum(data: ByteArray, offset: Int, length: Int): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(data, offset, length)
    return digest.digest().joinToString("") { "%02x".format(it) }
}