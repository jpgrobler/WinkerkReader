package za.co.jpsoft.winkerkreader.data

import za.co.jpsoft.winkerkreader.utils.CursorDataExtractor

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import za.co.jpsoft.winkerkreader.data.models.CallLog
import za.co.jpsoft.winkerkreader.data.models.CallType
import za.co.jpsoft.winkerkreader.utils.CursorDataExtractor.getSafeLong
import za.co.jpsoft.winkerkreader.utils.CursorDataExtractor.getSafeString
import java.text.SimpleDateFormat
import java.util.*

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
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

    fun insertCallLogWithType(
        callerInfo: String,
        timestamp: Long,
        callType: CallType,
        source: String,
        duration: Long
    ): Boolean {
        val db = writableDatabase
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDateTime = dateFormat.format(Date(timestamp))

        val values = ContentValues().apply {
            put(COLUMN_CALLER_INFO, callerInfo)
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_DATE_TIME, formattedDateTime)
            put(COLUMN_CALL_TYPE, callType.name)
            put(COLUMN_SOURCE, source)
            put(COLUMN_DURATION, duration)
        }

        val result = db.insert(TABLE_CALL_LOGS, null, values)
        db.close()
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
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_CALL_LOGS ORDER BY $COLUMN_TIMESTAMP DESC"

        db.rawQuery(query, null).use { cursor ->
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
        val db = writableDatabase
        val result = db.delete(TABLE_CALL_LOGS, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return result > 0
    }

    fun clearAllCallLogs(): Boolean {
        val db = writableDatabase
        val result = db.delete(TABLE_CALL_LOGS, null, null)
        db.close()
        return result > 0
    }

    fun getCallLogsCount(): Int {
        val db = readableDatabase
        var count = 0
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_CALL_LOGS", null).use { cursor ->
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0)
            }
        }
        return count
    }

    fun getRecentCallLogs(timeWindowMs: Long): List<CallLog> {
        val recentCalls = mutableListOf<CallLog>()
        val db = readableDatabase
        val currentTime = System.currentTimeMillis()
        val cutoffTime = currentTime - timeWindowMs

        val query = "SELECT * FROM $TABLE_CALL_LOGS WHERE $COLUMN_TIMESTAMP > ? ORDER BY $COLUMN_TIMESTAMP DESC"
        db.rawQuery(query, arrayOf(cutoffTime.toString())).use { cursor ->
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