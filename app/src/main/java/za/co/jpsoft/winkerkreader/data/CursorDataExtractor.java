package za.co.jpsoft.winkerkreader.data;

import android.database.Cursor;
import android.util.Log;

/**
 * Helper class for safely extracting data from cursors with validation
 * Eliminates repetitive null checks and column existence validation
 *
 * Usage without static import (Recommended - clearest):
 *   member.name = CursorDataExtractor.getSafeString(cursor, LIDMATE_NOEMNAAM, "");
 *
 * Usage with static import (shorter):
 *   import static za.co.jpsoft.winkerkreader.data.winkerkreader.CursorDataExtractor.*;
 *   member.name = getSafeString(cursor, LIDMATE_NOEMNAAM, "");
 */
public class CursorDataExtractor {
    private static final String TAG = "CursorDataExtractor";

    /**
     * Safely get a String from cursor with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The string value or default
     */
    public static String getSafeString(Cursor cursor, String columnName, String defaultValue) {
        if (cursor == null || cursor.isClosed()) {
            Log.w(TAG, "Cursor is null or closed for column: " + columnName);
            return defaultValue;
        }

        try {
            int columnIndex = cursor.getColumnIndex(columnName);
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                String value = cursor.getString(columnIndex);
                return value != null ? value : defaultValue;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading string column " + columnName + ": " + e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Safely get an int from cursor with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The int value or default
     */
    public static int getSafeInt(Cursor cursor, String columnName, int defaultValue) {
        if (cursor == null || cursor.isClosed()) {
            Log.w(TAG, "Cursor is null or closed for column: " + columnName);
            return defaultValue;
        }

        try {
            int columnIndex = cursor.getColumnIndex(columnName);
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                return cursor.getInt(columnIndex);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading int column " + columnName + ": " + e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Safely get a long from cursor with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The long value or default
     */
    public static long getSafeLong(Cursor cursor, String columnName, long defaultValue) {
        if (cursor == null || cursor.isClosed()) {
            Log.w(TAG, "Cursor is null or closed for column: " + columnName);
            return defaultValue;
        }

        try {
            int columnIndex = cursor.getColumnIndex(columnName);
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                return cursor.getLong(columnIndex);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading long column " + columnName + ": " + e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Safely get a double from cursor with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The double value or default
     */
    public static double getSafeDouble(Cursor cursor, String columnName, double defaultValue) {
        if (cursor == null || cursor.isClosed()) {
            Log.w(TAG, "Cursor is null or closed for column: " + columnName);
            return defaultValue;
        }

        try {
            int columnIndex = cursor.getColumnIndex(columnName);
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                return cursor.getDouble(columnIndex);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading double column " + columnName + ": " + e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Safely get a float from cursor with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The float value or default
     */
    public static float getSafeFloat(Cursor cursor, String columnName, float defaultValue) {
        if (cursor == null || cursor.isClosed()) {
            Log.w(TAG, "Cursor is null or closed for column: " + columnName);
            return defaultValue;
        }

        try {
            int columnIndex = cursor.getColumnIndex(columnName);
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                return cursor.getFloat(columnIndex);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading float column " + columnName + ": " + e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Safely get a boolean from cursor (stored as int 0/1) with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The boolean value or default
     */
    public static boolean getSafeBoolean(Cursor cursor, String columnName, boolean defaultValue) {
        if (cursor == null || cursor.isClosed()) {
            Log.w(TAG, "Cursor is null or closed for column: " + columnName);
            return defaultValue;
        }

        try {
            int columnIndex = cursor.getColumnIndex(columnName);
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                return cursor.getInt(columnIndex) != 0;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading boolean column " + columnName + ": " + e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Safely get a byte array from cursor with default value
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist or is null
     * @return The byte array or default
     */
    public static byte[] getSafeBlob(Cursor cursor, String columnName, byte[] defaultValue) {
        if (cursor == null || cursor.isClosed()) {
            Log.w(TAG, "Cursor is null or closed for column: " + columnName);
            return defaultValue;
        }

        try {
            int columnIndex = cursor.getColumnIndex(columnName);
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                byte[] value = cursor.getBlob(columnIndex);
                return value != null ? value : defaultValue;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading blob column " + columnName + ": " + e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Check if a column exists in the cursor
     * @param cursor The cursor to check
     * @param columnName The column name
     * @return true if column exists, false otherwise
     */
    public static boolean hasColumn(Cursor cursor, String columnName) {
        if (cursor == null || cursor.isClosed()) {
            return false;
        }

        try {
            return cursor.getColumnIndex(columnName) != -1;
        } catch (Exception e) {
            Log.w(TAG, "Error checking column existence " + columnName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a column value is null
     * @param cursor The cursor to check
     * @param columnName The column name
     * @return true if null or column doesn't exist, false otherwise
     */
    public static boolean isNull(Cursor cursor, String columnName) {
        if (cursor == null || cursor.isClosed()) {
            return true;
        }

        try {
            int columnIndex = cursor.getColumnIndex(columnName);
            if (columnIndex != -1) {
                return cursor.isNull(columnIndex);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking null for column " + columnName + ": " + e.getMessage());
        }

        return true;
    }

    /**
     * Get non-empty string or default (treats empty strings as null)
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist, is null, or is empty
     * @return The string value or default
     */
    public static String getNonEmptyString(Cursor cursor, String columnName, String defaultValue) {
        String value = getSafeString(cursor, columnName, defaultValue);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Get trimmed string or default
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist, is null, or is blank after trimming
     * @return The trimmed string value or default
     */
    public static String getTrimmedString(Cursor cursor, String columnName, String defaultValue) {
        String value = getSafeString(cursor, columnName, null);
        if (value != null) {
            value = value.trim();
            return !value.isEmpty() ? value : defaultValue;
        }
        return defaultValue;
    }

    /**
     * Get non-blank string or default (checks isEmpty and isBlank)
     * @param cursor The cursor to read from
     * @param columnName The column name
     * @param defaultValue Value to return if column doesn't exist, is null, empty or blank
     * @return The string value or default
     */
    public static String getNonBlankString(Cursor cursor, String columnName, String defaultValue) {
        String value = getSafeString(cursor, columnName, null);
        if (value != null && !value.isEmpty() && !value.isBlank()) {
            return value;
        }
        return defaultValue;
    }
}