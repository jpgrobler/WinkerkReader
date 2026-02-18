package za.co.jpsoft.winkerkreader;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

/**
 * Helper class for safe database operations with proper resource management
 */
public class DatabaseHelper2 {
    private static final String TAG = "DatabaseHelper";

    public static <T> T executeDatabaseQuery(Context context, String databaseName, DatabaseQuery<T> query) {
        SQLiteAssetHelper helper = null;
        SQLiteDatabase database = null;

        try {
            helper = new SQLiteAssetHelper(context, databaseName, null, 1);
            database = helper.getReadableDatabase();
            return query.execute(database);

        } catch (SQLiteException e) {
            Log.e(TAG, "Database error executing query", e);
            return null;
        } finally {
            if (database != null && database.isOpen()) {
                database.close();
            }
            if (helper != null) {
                helper.close();
            }
        }
    }

    public static void executeDatabaseOperation(Context context, String databaseName, DatabaseOperation operation) {
        SQLiteAssetHelper helper = null;
        SQLiteDatabase database = null;

        try {
            helper = new SQLiteAssetHelper(context, databaseName, null, 1);
            database = helper.getWritableDatabase();
            operation.execute(database);

        } catch (SQLiteException e) {
            Log.e(TAG, "Database error executing operation", e);
        } finally {
            if (database != null && database.isOpen()) {
                database.close();
            }
            if (helper != null) {
                helper.close();
            }
        }
    }

    public interface DatabaseQuery<T> {
        T execute(SQLiteDatabase database);
    }

    public interface DatabaseOperation {
        void execute(SQLiteDatabase database);
    }

    /**
     * Safe cursor operations with automatic resource management
     */
    public static <T> T executeCursorQuery(Cursor cursor, CursorQuery<T> query) {
        if (cursor == null) return null;

        try {
            return query.execute(cursor);
        } catch (Exception e) {
            Log.e(TAG, "Error executing cursor query", e);
            return null;
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    public interface CursorQuery<T> {
        T execute(Cursor cursor);
    }
}