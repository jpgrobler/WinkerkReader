package za.co.jpsoft.winkerkreader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import static za.co.jpsoft.winkerkreader.data.CursorDataExtractor.*;

import za.co.jpsoft.winkerkreader.data.CursorDataExtractor;

public class DatabaseHelper extends SQLiteOpenHelper {
    
    public static final String DATABASE_NAME = "whatsapp_call_logs.db";
    public static final int DATABASE_VERSION = 2;
    
    // Table name
    public static final String TABLE_CALL_LOGS = "call_logs";
    
    // Column names
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_CALLER_INFO = "caller_info";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_DATE_TIME = "date_time";
    public static final String COLUMN_CALL_TYPE = "call_type";
    public static final String COLUMN_SOURCE = "source";
    public static final String COLUMN_DURATION = "duration";
    
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_CALL_LOGS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_CALLER_INFO + " TEXT NOT NULL," +
                COLUMN_TIMESTAMP + " INTEGER NOT NULL," +
                COLUMN_DATE_TIME + " TEXT NOT NULL," +
                COLUMN_CALL_TYPE + " TEXT DEFAULT 'INCOMING'," +
                COLUMN_SOURCE + " TEXT DEFAULT 'WhatsApp'," +
                COLUMN_DURATION + " INTEGER DEFAULT 0" +
                ")";
        
        db.execSQL(createTable);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Add new columns if upgrading from older version
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_CALL_LOGS + " ADD COLUMN " + COLUMN_CALL_TYPE + " TEXT DEFAULT 'INCOMING'");
                db.execSQL("ALTER TABLE " + TABLE_CALL_LOGS + " ADD COLUMN " + COLUMN_SOURCE + " TEXT DEFAULT 'WhatsApp'");
                db.execSQL("ALTER TABLE " + TABLE_CALL_LOGS + " ADD COLUMN " + COLUMN_DURATION + " INTEGER DEFAULT 0");
            } catch (Exception e) {
                // If columns already exist or other error, recreate table
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_CALL_LOGS);
                onCreate(db);
            }
        }
    }
    
    public boolean insertCallLogWithType(String callerInfo, long timestamp, CallType callType, String source, long duration) {
        SQLiteDatabase db = this.getWritableDatabase();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String formattedDateTime = dateFormat.format(new Date(timestamp));
        
        ContentValues values = new ContentValues();
        values.put(COLUMN_CALLER_INFO, callerInfo);
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_DATE_TIME, formattedDateTime);
        values.put(COLUMN_CALL_TYPE, callType.name());
        values.put(COLUMN_SOURCE, source);
        values.put(COLUMN_DURATION, duration);
        
        long result = db.insert(TABLE_CALL_LOGS, null, values);
        db.close();
        
        return result != -1;
    }
    
    public boolean insertCallLogWithType(String callerInfo, long timestamp, CallType callType, String source) {
        return insertCallLogWithType(callerInfo, timestamp, callType, source, 0);
    }
    
    public List<CallLog> getAllCallLogs() {
        List<CallLog> callLogs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String query = "SELECT * FROM " + TABLE_CALL_LOGS + " ORDER BY " + COLUMN_TIMESTAMP + " DESC";
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, null);

            if (cursor.moveToFirst()) {
                do {
                    long id = getSafeLong(cursor, COLUMN_ID, -1L);
                    String callerInfo = getSafeString(cursor, COLUMN_CALLER_INFO, "");
                    long timestamp = getSafeLong(cursor, COLUMN_TIMESTAMP, 0L);
                    String dateTime = getSafeString(cursor, COLUMN_DATE_TIME, "");

                    // Handle both old and new database schema with defaults
                    String callType = getSafeString(cursor, COLUMN_CALL_TYPE, "INCOMING");
                    String source = getSafeString(cursor, COLUMN_SOURCE, "WhatsApp");
                    long duration = getSafeLong(cursor, COLUMN_DURATION, 0L);

                    callLogs.add(new CallLog(id, callerInfo, timestamp, dateTime, callType, source, duration));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return callLogs;
    }
    
    public boolean deleteCallLog(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_CALL_LOGS, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return result > 0;
    }
    
    public boolean clearAllCallLogs() {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_CALL_LOGS, null, null);
        db.close();
        return result > 0;
    }
    
    public int getCallLogsCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        int count = 0;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_CALL_LOGS, null);
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0);
                }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return count;
    }
    
    public List<CallLog> getRecentCallLogs(long timeWindowMs) {
        List<CallLog> recentCalls = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        long currentTime = System.currentTimeMillis();
        long cutoffTime = currentTime - timeWindowMs;
        
        String query = "SELECT * FROM " + TABLE_CALL_LOGS + " WHERE " + COLUMN_TIMESTAMP + " > ? ORDER BY " + COLUMN_TIMESTAMP + " DESC";
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, new String[]{String.valueOf(cutoffTime)});

            if (cursor.moveToFirst()) {
                do {
                    long id = CursorDataExtractor.getSafeLong(cursor, COLUMN_ID, -1L);
                    String callerInfo = CursorDataExtractor.getSafeString(cursor, COLUMN_CALLER_INFO, "");
                    long timestamp = CursorDataExtractor.getSafeLong(cursor, COLUMN_TIMESTAMP, 0L);
                    String dateTime = CursorDataExtractor.getSafeString(cursor, COLUMN_DATE_TIME, "");
                    String callType = CursorDataExtractor.getSafeString(cursor, COLUMN_CALL_TYPE, "INCOMING");
                    String source = CursorDataExtractor.getSafeString(cursor, COLUMN_SOURCE, "WhatsApp");
                    long duration = CursorDataExtractor.getSafeLong(cursor, COLUMN_DURATION, 0L);

                    recentCalls.add(new CallLog(id, callerInfo, timestamp, dateTime, callType, source, duration));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return recentCalls;
    }
}