package za.co.jpsoft.winkerkreader

import android.database.Cursor
import za.co.jpsoft.winkerkreader.data.CursorDataExtractor.getSafeInt
import za.co.jpsoft.winkerkreader.data.CursorDataExtractor.getSafeString

/**
 * Extension functions for safe cursor data extraction.
 * These replace manual column index checks and null handling.
 */
fun Cursor.getStringOrEmpty(columnName: String): String =
    getSafeString(this, columnName, "") ?: ""

fun Cursor.getStringOrNull(columnName: String): String? =
    getSafeString(this, columnName, null)

fun Cursor.getIntOrDefault(columnName: String, default: Int = 0): Int =
    getSafeInt(this, columnName, default)

fun Cursor.getBoolean(columnName: String, default: Boolean = false): Boolean {
    val intValue = getSafeInt(this, columnName, if (default) 1 else 0)
    return intValue == 1
}

// Optional: add long, double, etc. if needed
fun Cursor.getLongOrDefault(columnName: String, default: Long = 0L): Long {
    val index = getColumnIndex(columnName)
    return if (index != -1 && !isNull(index)) getLong(index) else default
}