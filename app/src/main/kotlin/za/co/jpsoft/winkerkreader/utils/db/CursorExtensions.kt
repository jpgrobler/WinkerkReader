package za.co.jpsoft.winkerkreader.utils.db

import android.database.Cursor

/**
 * Extension functions for safe cursor data extraction.
 * These replace manual column index checks and null handling.
 */
fun Cursor.getStringOrEmpty(columnName: String): String =
    CursorDataExtractor.getSafeString(this, columnName, "") ?: ""

fun Cursor.getStringOrNull(columnName: String): String? =
    CursorDataExtractor.getSafeString(this, columnName, null)

fun Cursor.getIntOrDefault(columnName: String, default: Int = 0): Int =
    CursorDataExtractor.getSafeInt(this, columnName, default)

fun Cursor.getBoolean(columnName: String, default: Boolean = false): Boolean {
    val intValue = CursorDataExtractor.getSafeInt(this, columnName, if (default) 1 else 0)
    return intValue == 1
}

// Optional: add long, double, etc. if needed
fun Cursor.getLongOrDefault(columnName: String, default: Long = 0L): Long {
    val index = getColumnIndex(columnName)
    return if (index != -1 && !isNull(index)) getLong(index) else default
}