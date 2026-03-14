package za.co.jpsoft.winkerkreader.data

import android.database.Cursor
import android.util.Log

/**
 * Helper class for safely extracting data from cursors with validation
 * Eliminates repetitive null checks and column existence validation
 *
 * Usage without static import (Recommended - clearest):
 *   member.name = CursorDataExtractor.getSafeString(cursor, LIDMATE_NOEMNAAM, "")
 *
 * Usage with static import (shorter):
 *   import static za.co.jpsoft.winkerkreader.data.winkerkreader.CursorDataExtractor.*;
 *   member.name = getSafeString(cursor, LIDMATE_NOEMNAAM, "");
 */
object CursorDataExtractor {
    private const val TAG = "CursorDataExtractor"

    /**
     * Safely get a String from cursor with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The string value or default
     */
    @JvmStatic
    fun getSafeString(cursor: Cursor?, columnName: String, defaultValue: String?): String? {
        if (cursor == null || cursor.isClosed) {
            Log.w(TAG, "Cursor is null or closed for column: $columnName")
            return defaultValue
        }

        return try {
            val columnIndex = cursor.getColumnIndex(columnName)
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                cursor.getString(columnIndex) ?: defaultValue
            } else defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Error reading string column $columnName: ${e.message}")
            defaultValue
        }
    }

    /**
     * Safely get an int from cursor with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The int value or default
     */
    @JvmStatic
    fun getSafeInt(cursor: Cursor?, columnName: String, defaultValue: Int): Int {
        if (cursor == null || cursor.isClosed) {
            Log.w(TAG, "Cursor is null or closed for column: $columnName")
            return defaultValue
        }

        return try {
            val columnIndex = cursor.getColumnIndex(columnName)
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                cursor.getInt(columnIndex)
            } else defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Error reading int column $columnName: ${e.message}")
            defaultValue
        }
    }

    /**
     * Safely get a long from cursor with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The long value or default
     */
    @JvmStatic
    fun getSafeLong(cursor: Cursor?, columnName: String, defaultValue: Long): Long {
        if (cursor == null || cursor.isClosed) {
            Log.w(TAG, "Cursor is null or closed for column: $columnName")
            return defaultValue
        }

        return try {
            val columnIndex = cursor.getColumnIndex(columnName)
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                cursor.getLong(columnIndex)
            } else defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Error reading long column $columnName: ${e.message}")
            defaultValue
        }
    }

    /**
     * Safely get a double from cursor with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The double value or default
     */
    @JvmStatic
    fun getSafeDouble(cursor: Cursor?, columnName: String, defaultValue: Double): Double {
        if (cursor == null || cursor.isClosed) {
            Log.w(TAG, "Cursor is null or closed for column: $columnName")
            return defaultValue
        }

        return try {
            val columnIndex = cursor.getColumnIndex(columnName)
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                cursor.getDouble(columnIndex)
            } else defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Error reading double column $columnName: ${e.message}")
            defaultValue
        }
    }

    /**
     * Safely get a float from cursor with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The float value or default
     */
    @JvmStatic
    fun getSafeFloat(cursor: Cursor?, columnName: String, defaultValue: Float): Float {
        if (cursor == null || cursor.isClosed) {
            Log.w(TAG, "Cursor is null or closed for column: $columnName")
            return defaultValue
        }

        return try {
            val columnIndex = cursor.getColumnIndex(columnName)
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                cursor.getFloat(columnIndex)
            } else defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Error reading float column $columnName: ${e.message}")
            defaultValue
        }
    }

    /**
     * Safely get a boolean from cursor (stored as int 0/1) with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The boolean value or default
     */
    @JvmStatic
    fun getSafeBoolean(cursor: Cursor?, columnName: String, defaultValue: Boolean): Boolean {
        if (cursor == null || cursor.isClosed) {
            Log.w(TAG, "Cursor is null or closed for column: $columnName")
            return defaultValue
        }

        return try {
            val columnIndex = cursor.getColumnIndex(columnName)
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                cursor.getInt(columnIndex) != 0
            } else defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Error reading boolean column $columnName: ${e.message}")
            defaultValue
        }
    }

    /**
     * Safely get a byte array from cursor with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The byte array or default
     */
    @JvmStatic
    fun getSafeBlob(cursor: Cursor?, columnName: String, defaultValue: ByteArray?): ByteArray? {
        if (cursor == null || cursor.isClosed) {
            Log.w(TAG, "Cursor is null or closed for column: $columnName")
            return defaultValue
        }

        return try {
            val columnIndex = cursor.getColumnIndex(columnName)
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                cursor.getBlob(columnIndex) ?: defaultValue
            } else defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Error reading blob column $columnName: ${e.message}")
            defaultValue
        }
    }

    /**
     * Check if a column exists in the cursor
     * @param cursor The cursor to check
     * @param columnName The column name
     * @return true if column exists, false otherwise
     */
    @JvmStatic
    fun hasColumn(cursor: Cursor?, columnName: String): Boolean {
        if (cursor == null || cursor.isClosed) return false
        return try {
            cursor.getColumnIndex(columnName) != -1
        } catch (e: Exception) {
            Log.w(TAG, "Error checking column existence $columnName: ${e.message}")
            false
        }
    }

    /**
     * Check if a column value is null
     * @param cursor The cursor to check
     * @param columnName The column name
     * @return true if null or column doesn't exist, false otherwise
     */
    @JvmStatic
    fun isNull(cursor: Cursor?, columnName: String): Boolean {
        if (cursor == null || cursor.isClosed) return true
        return try {
            val columnIndex = cursor.getColumnIndex(columnName)
            columnIndex == -1 || cursor.isNull(columnIndex)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking null for column $columnName: ${e.message}")
            true
        }
    }

    /**
     * Get non-empty string or default (treats empty strings as null)
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist, is null, or is empty
     * @return The string value or default
     */
    @JvmStatic
    fun getNonEmptyString(cursor: Cursor?, columnName: String, defaultValue: String): String {
        val value = getSafeString(cursor, columnName, defaultValue)
        // getSafeString returns defaultValue (non‑null) when column missing or null, so value is never null here.
        return if (!value.isNullOrEmpty()) value else defaultValue
    }

    /**
     * Get trimmed string or default
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist, is null, or is blank after trimming
     * @return The trimmed string value or default
     */
    @JvmStatic
    fun getTrimmedString(cursor: Cursor?, columnName: String, defaultValue: String): String {
        val value = getSafeString(cursor, columnName, null)
        if (value != null) {
            val trimmed = value.trim()
            if (trimmed.isNotEmpty()) return trimmed
        }
        return defaultValue
    }

    /**
     * Get non-blank string or default (checks isEmpty and isBlank)
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist, is null, empty or blank
     * @return The string value or default
     */
    @JvmStatic
    fun getNonBlankString(cursor: Cursor?, columnName: String, defaultValue: String): String {
        val value = getSafeString(cursor, columnName, null)
        if (value != null && value.isNotEmpty() && !value.isBlank()) {
            return value
        }
        return defaultValue
    }
}