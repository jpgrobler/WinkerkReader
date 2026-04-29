// File: utils/CallLogExporter.kt
package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import za.co.jpsoft.winkerkreader.data.models.CallLog
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CallLogExporter {
    private const val TAG = "CallLogExporter"
    private const val CSV_EXTENSION = ".csv"
    private const val CSV_DELIMITER = ","
    private const val CSV_NEWLINE = "\n"

    /**
     * Export call logs to CSV file
     * @param context Context for file operations
     * @param callLogs List of call logs to export
     * @return Uri of the exported file, or null if export failed
     */
    fun exportToCSV(context: Context, callLogs: List<CallLog>): Uri? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "call_log_$timestamp$CSV_EXTENSION"

            // Create file in Downloads directory (sticky for Android 10+)
            val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10 and above, use Downloads folder
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(downloadsDir, fileName)
            } else {
                // For older Android versions
                val externalFilesDir = context.getExternalFilesDir(null) ?: context.filesDir
                File(externalFilesDir, fileName)
            }

            writeCSVToFile(file, callLogs)

            // Get URI for the file
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting call logs: ${e.message}", e)
            null
        }
    }

    /**
     * Write call logs to CSV file
     */
    private fun writeCSVToFile(file: File, callLogs: List<CallLog>) {
        FileOutputStream(file).use { outputStream ->
            OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                // Write BOM for UTF-8 (helps Excel recognize UTF-8)
                writer.write("\uFEFF")  // Write BOM as String

                // Write headers
                writer.write(buildCSVRow(
                    "ID",
                    "Caller Info",
                    "Timestamp",
                    "Date/Time",
                    "Call Type",
                    "Source",
                    "Duration (seconds)",
                    "Duration Formatted"
                ))
                writer.write(CSV_NEWLINE)

                // Write data rows
                callLogs.forEach { callLog ->
                    writer.write(buildCSVRow(
                        callLog.id.toString(),
                        escapeCSVString(callLog.callerInfo),
                        callLog.timestamp.toString(),
                        callLog.formattedDateTime,
                        callLog.callType,
                        callLog.source,
                        callLog.duration.toString(),
                        formatDuration(callLog.duration)
                    ))
                    writer.write(CSV_NEWLINE)
                }

                writer.flush()
            }
        }
    }

    /**
     * Build a CSV row from string values
     */
    private fun buildCSVRow(vararg values: String): String {
        return values.joinToString(CSV_DELIMITER)
    }

    /**
     * Escape CSV string (handle quotes, commas, newlines)
     */
    private fun escapeCSVString(input: String): String {
        if (input.isEmpty()) return ""

        // If the string contains special characters, wrap in quotes
        if (input.contains(CSV_DELIMITER) || input.contains(CSV_NEWLINE) || input.contains("\"")) {
            // Escape double quotes by doubling them
            val escaped = input.replace("\"", "\"\"")
            return "\"$escaped\""
        }
        return input
    }

    /**
     * Format duration from seconds to readable string
     */
    private fun formatDuration(seconds: Long): String {
        return when {
            seconds <= 0 -> "0s"
            seconds < 60 -> "${seconds}s"
            else -> {
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                if (remainingSeconds == 0L) {
                    "${minutes}m"
                } else {
                    "${minutes}m ${remainingSeconds}s"
                }
            }
        }
    }

    /**
     * Share exported CSV file
     * @param context Context
     * @param fileUri Uri of the CSV file
     * @return true if share intent was successfully launched
     */
    fun shareCSV(context: Context, fileUri: Uri): Boolean {
        return try {
            val shareIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                type = "text/csv"
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Export Call Log"))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing CSV: ${e.message}", e)
            false
        }
    }
}