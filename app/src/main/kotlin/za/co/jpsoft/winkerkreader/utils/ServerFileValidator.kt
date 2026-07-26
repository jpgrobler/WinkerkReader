package za.co.jpsoft.winkerkreader.utils

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.utils.ServerFileValidator.MIN_MEMBER_COUNT
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Validates files downloaded from a server, ensuring they are genuine SQLite databases.
 * Provides both single and batch validation with per‑file results.
 */
object ServerFileValidator {

    private const val TAG = "ServerFileValidator"
    private const val MIN_DATABASE_SIZE_BYTES = 512L
    private const val MIN_MEMBER_COUNT = 10

    /**
     * Result of a single file check.
     * @param fileName Name of the file checked.
     * @param success True if the file is valid (SQLite header + row count >= [MIN_MEMBER_COUNT]).
     * @param fileSize Size of the file in bytes, or null if the check failed or the file does not exist.
     * @param errorMessage Optional error description if the check failed.
     */
    data class FileCheckResult(
        val fileName: String,
        val success: Boolean,
        val fileSize: Long? = null,
        val errorMessage: String? = null
    )

    // -------------------------------------------------------------------------
    // Single file checks (overloaded for File, String, Uri)
    // -------------------------------------------------------------------------

    /**
     * Checks a [File] for validity.
     */
    fun checkSingleFile(file: File): FileCheckResult {
        try {
            if (!file.exists()) {
                return FileCheckResult(
                    fileName = file.name,
                    success = false,
                    errorMessage = "File does not exist"
                )
            }
            if (file.length() < MIN_DATABASE_SIZE_BYTES) {
                return FileCheckResult(
                    fileName = file.name,
                    success = false,
                    fileSize = file.length(),
                    errorMessage = "File too small (min ${MIN_DATABASE_SIZE_BYTES}B)"
                )
            }

            // Validate SQLite header
            val headerBytes = try {
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(16)
                    input.read(buffer)
                    buffer
                }
            } catch (e: IOException) {
                return FileCheckResult(
                    fileName = file.name,
                    success = false,
                    fileSize = file.length(),
                    errorMessage = "Failed to read header: ${e.message}"
                )
            }

            val expectedHeader = byteArrayOf(
                0x53, 0x51, 0x4C, 0x69, 0x74, 0x65, 0x20, 0x66,
                0x6F, 0x72, 0x6D, 0x61, 0x74, 0x20, 0x33, 0x00
            )
            if (!headerBytes.contentEquals(expectedHeader)) {
                return FileCheckResult(
                    fileName = file.name,
                    success = false,
                    fileSize = file.length(),
                    errorMessage = "Invalid SQLite header"
                )
            }

            // Verify the Members table has a minimum number of rows.
            val memberCount = countMembersInDatabase(file)
            if (memberCount < MIN_MEMBER_COUNT) {
                return FileCheckResult(
                    fileName = file.name,
                    success = false,
                    fileSize = file.length(),
                    errorMessage = "Members table has only $memberCount rows (min $MIN_MEMBER_COUNT)"
                )
            }

            return FileCheckResult(
                fileName = file.name,
                success = true,
                fileSize = file.length()
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Unexpected error checking file ${file.name}", e)
            return FileCheckResult(
                fileName = file.name,
                success = false,
                fileSize = file.lengthOrNull(),
                errorMessage = "Unexpected error: ${e.message}"
            )
        }
    }

    /**
     * Checks a file given its path [filePath] for validity.
     */
    fun checkSingleFile(filePath: String): FileCheckResult {
        return checkSingleFile(File(filePath))
    }

    /**
     * Checks a file given a [Uri] for validity.
     * Only supports `file://` schemes; other schemes will return a failure result.
     */
    fun checkSingleFile(uri: Uri): FileCheckResult {
        return if (uri.scheme == "file") {
            uri.path?.let { checkSingleFile(File(it)) }
                ?: FileCheckResult(
                    fileName = uri.toString(),
                    success = false,
                    errorMessage = "Invalid file path in URI"
                )
        } else {
            FileCheckResult(
                fileName = uri.toString(),
                success = false,
                errorMessage = "Unsupported URI scheme (use file://)"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Batch checks – using @JvmName to avoid signature clash after erasure
    // -------------------------------------------------------------------------

    /**
     * Checks a list of [File] objects.
     * @return A list of [FileCheckResult] in the same order as the input.
     */
    @JvmName("checkMultipleFilesFromFiles")
    fun checkMultipleFiles(files: List<File>): List<FileCheckResult> {
        return files.map { checkSingleFile(it) }
    }

    /**
     * Checks a list of [Uri] objects.
     * Only supports `file://` schemes; unsupported URIs will return failure results.
     * @return A list of [FileCheckResult] in the same order as the input.
     */
    @JvmName("checkMultipleFilesFromUris")
    fun checkMultipleFiles(uris: List<Uri>): List<FileCheckResult> {
        return uris.map { checkSingleFile(it) }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Counts the number of rows in the `Members` table of an SQLite database.
     * Returns 0 if the query fails or the table does not exist.
     */
    /**
     * Counts rows in the Members table using the Android SQLite API.
     *
     * Previously used java.sql.DriverManager (JDBC), which requires a separate
     * SQLite JDBC driver and does not work reliably on Android. The Android
     * SQLiteDatabase API is the correct approach on this platform.
     */
    private fun countMembersInDatabase(file: File): Int {
        return try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM Members", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to count Members in ${file.name}", e)
            0
        }
    }

    /**
     * Extension function to get file length or null if the file does not exist.
     */
    private fun File.lengthOrNull(): Long? = if (exists()) length() else null
}