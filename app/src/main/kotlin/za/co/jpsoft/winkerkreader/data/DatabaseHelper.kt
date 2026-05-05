package za.co.jpsoft.winkerkreader.data

import za.co.jpsoft.winkerkreader.utils.CursorDataExtractor

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import za.co.jpsoft.winkerkreader.data.models.CallLog
import za.co.jpsoft.winkerkreader.data.models.CallType
import za.co.jpsoft.winkerkreader.utils.CursorDataExtractor.getSafeLong
import za.co.jpsoft.winkerkreader.utils.CursorDataExtractor.getSafeString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class DatabaseHelper private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "DatabaseHelper"
        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
        const val DATABASE_NAME = "whatsapp_call_logs.db"
        const val DATABASE_VERSION = 2

        // Table name
        const val TABLE_CALL_LOGS = "call_logs"

        // Column names
        const val COLUMN_ID = "id"
        const val COLUMN_CALLER_INFO = "caller_info"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_DATE_TIME = "date_time"
        const val COLUMN_CALL_TYPE = "call_type"
        const val COLUMN_SOURCE = "source"
        const val COLUMN_DURATION = "duration"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_CALL_LOGS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_CALLER_INFO TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_DATE_TIME TEXT NOT NULL,
                $COLUMN_CALL_TYPE TEXT DEFAULT 'INCOMING',
                $COLUMN_SOURCE TEXT DEFAULT 'WhatsApp',
                $COLUMN_DURATION INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_CALL_LOGS ADD COLUMN $COLUMN_CALL_TYPE TEXT DEFAULT 'INCOMING'")
                db.execSQL("ALTER TABLE $TABLE_CALL_LOGS ADD COLUMN $COLUMN_SOURCE TEXT DEFAULT 'WhatsApp'")
                db.execSQL("ALTER TABLE $TABLE_CALL_LOGS ADD COLUMN $COLUMN_DURATION INTEGER DEFAULT 0")
            } catch (e: Exception) {
                // If columns already exist or other error, recreate table
                db.execSQL("DROP TABLE IF EXISTS $TABLE_CALL_LOGS")
                onCreate(db)
            }
        }
    }

    private fun isDuplicateCall(callerInfo: String, timestamp: Long, source: String, timeWindowMs: Long = 3000): Boolean {
        val query = """
        SELECT COUNT(*) FROM $TABLE_CALL_LOGS 
        WHERE $COLUMN_CALLER_INFO = ? 
        AND ABS($COLUMN_TIMESTAMP - ?) < ?
        AND $COLUMN_SOURCE = ?
    """.trimIndent()

        readableDatabase.rawQuery(query, arrayOf(callerInfo, timestamp.toString(), timeWindowMs.toString(), source)).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0) > 0
            }
        }
        return false
    }

    // Update your insertCallLogWithType method to check for duplicates
    fun insertCallLogWithType(
        callerInfo: String,
        timestamp: Long,
        callType: CallType,
        source: String,
        duration: Long
    ): Boolean {
        // Skip UNKNOWN types for VoIP
        if (callType == CallType.UNKNOWN && source != "Phone Call") {
            Log.d(TAG, "Skipping UNKNOWN call type for source: $source")
            return false
        }

        // Check for duplicate
        if (isDuplicateCall(callerInfo, timestamp, source)) {
            Log.d(TAG, "Duplicate call detected, skipping insert: $callerInfo")
            return false
        }

        // Proceed with insertion...
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDateTime = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(formatter)

        val values = ContentValues().apply {
            put(COLUMN_CALLER_INFO, callerInfo)
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_DATE_TIME, formattedDateTime)
            put(COLUMN_CALL_TYPE, callType.name)
            put(COLUMN_SOURCE, source)
            put(COLUMN_DURATION, duration)
        }

        val result = writableDatabase.insert(TABLE_CALL_LOGS, null, values)
        return result != -1L
    }

    fun insertCallLogWithType(
        callerInfo: String,
        timestamp: Long,
        callType: CallType,
        source: String
    ): Boolean {
        return insertCallLogWithType(callerInfo, timestamp, callType, source, 0L)
    }

    fun getAllCallLogs(): List<CallLog> {
        val callLogs = mutableListOf<CallLog>()
        val query = "SELECT * FROM $TABLE_CALL_LOGS ORDER BY $COLUMN_TIMESTAMP DESC"

        readableDatabase.rawQuery(query, null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val id = getSafeLong(cursor, COLUMN_ID, -1L)
                    val callerInfo = getSafeString(cursor, COLUMN_CALLER_INFO, "") ?: ""
                    val timestamp = getSafeLong(cursor, COLUMN_TIMESTAMP, 0L)
                    val dateTime = getSafeString(cursor, COLUMN_DATE_TIME, "") ?: ""
                    val callType = getSafeString(cursor, COLUMN_CALL_TYPE, "INCOMING") ?: "INCOMING"
                    val source = getSafeString(cursor, COLUMN_SOURCE, "WhatsApp") ?: "WhatsApp"
                    val duration = getSafeLong(cursor, COLUMN_DURATION, 0L)

                    callLogs.add(
                        CallLog(
                            id,
                            callerInfo,
                            timestamp,
                            dateTime,
                            callType,
                            source,
                            duration
                        )
                    )
                } while (cursor.moveToNext())
            }
        }
        return callLogs
    }

    fun deleteCallLog(id: Long): Boolean {
        val result = writableDatabase.delete(TABLE_CALL_LOGS, "$COLUMN_ID = ?", arrayOf(id.toString()))
        return result > 0
    }

    fun clearAllCallLogs(): Boolean {
        val result = writableDatabase.delete(TABLE_CALL_LOGS, null, null)
        return result > 0
    }

    fun getCallLogsCount(): Int {
        var count = 0
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_CALL_LOGS", null).use { cursor ->
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0)
            }
        }
        return count
    }

    fun getRecentCallLogs(timeWindowMs: Long): List<CallLog> {
        val recentCalls = mutableListOf<CallLog>()
        val currentTime = System.currentTimeMillis()
        val cutoffTime = currentTime - timeWindowMs

        val query = "SELECT * FROM $TABLE_CALL_LOGS WHERE $COLUMN_TIMESTAMP > ? ORDER BY $COLUMN_TIMESTAMP DESC"
        readableDatabase.rawQuery(query, arrayOf(cutoffTime.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val id = getSafeLong(cursor, COLUMN_ID, -1L)
                    val callerInfo = getSafeString(cursor, COLUMN_CALLER_INFO, "") ?: ""
                    val timestamp = getSafeLong(cursor, COLUMN_TIMESTAMP, 0L)
                    val dateTime = getSafeString(cursor, COLUMN_DATE_TIME, "") ?: ""
                    val callType = getSafeString(cursor, COLUMN_CALL_TYPE, "INCOMING") ?: "INCOMING"
                    val source = getSafeString(cursor, COLUMN_SOURCE, "WhatsApp") ?: "WhatsApp"
                    val duration = getSafeLong(cursor, COLUMN_DURATION, 0L)

                    recentCalls.add(CallLog(id, callerInfo, timestamp, dateTime, callType, source, duration))
                } while (cursor.moveToNext())
            }
        }
        return recentCalls
    }
}