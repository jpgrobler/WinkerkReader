package za.co.jpsoft.winkerkreader.data.members.setup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.calllog.models.CallLog
import za.co.jpsoft.winkerkreader.data.calllog.models.CallType
import za.co.jpsoft.winkerkreader.utils.db.CursorDataExtractor.getSafeLong
import za.co.jpsoft.winkerkreader.utils.db.CursorDataExtractor.getSafeString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

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
        const val DATABASE_VERSION = 3   // was 2 — bumped for active_calls table

        // Table name (finished call log)
        const val TABLE_CALL_LOGS = "call_logs"

        // Column names
        const val COLUMN_ID = "id"
        const val COLUMN_CALLER_INFO = "caller_info"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_DATE_TIME = "date_time"
        const val COLUMN_CALL_TYPE = "call_type"
        const val COLUMN_SOURCE = "source"
        const val COLUMN_DURATION = "duration"

        // Table name (durable "call in progress" backstop)
        const val TABLE_ACTIVE_CALLS = "active_calls"
        const val COL_ACTIVE_CALL_ID = "call_id"
        const val COL_ACTIVE_NUMBER = "number"
        const val COL_ACTIVE_CONTACT_NAME = "contact_name"
        const val COL_ACTIVE_CALL_TYPE = "call_type"
        const val COL_ACTIVE_SOURCE = "source"
        const val COL_ACTIVE_START_TIME = "start_time"
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
        db.execSQL(createActiveCallsTableSql())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.beginTransaction()
        try {
            if (oldVersion < 2) {
                try {
                    db.execSQL("ALTER TABLE $TABLE_CALL_LOGS ADD COLUMN $COLUMN_CALL_TYPE TEXT DEFAULT 'INCOMING'")
                    db.execSQL("ALTER TABLE $TABLE_CALL_LOGS ADD COLUMN $COLUMN_SOURCE TEXT DEFAULT 'WhatsApp'")
                    db.execSQL("ALTER TABLE $TABLE_CALL_LOGS ADD COLUMN $COLUMN_DURATION INTEGER DEFAULT 0")
                } catch (_: Exception) {
                    // If columns already exist or other error, recreate table
                    db.execSQL("DROP TABLE IF EXISTS $TABLE_CALL_LOGS")
                    onCreate(db)
                }
            }
            if (oldVersion < 3) {
                try {
                    db.execSQL(createActiveCallsTableSql())
                } catch (e: Exception) {
                    // Table may already exist from a fresh install; ignore.
                    if (BuildConfig.DEBUG) Log.w(TAG, "active_calls table creation skipped", e)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun createActiveCallsTableSql() = """
        CREATE TABLE IF NOT EXISTS $TABLE_ACTIVE_CALLS (
            $COL_ACTIVE_CALL_ID TEXT PRIMARY KEY,
            $COL_ACTIVE_NUMBER TEXT NOT NULL,
            $COL_ACTIVE_CONTACT_NAME TEXT NOT NULL,
            $COL_ACTIVE_CALL_TYPE TEXT NOT NULL,
            $COL_ACTIVE_SOURCE TEXT NOT NULL,
            $COL_ACTIVE_START_TIME INTEGER NOT NULL
        )
    """.trimIndent()

    // -------------------------------------------------------------------
    // Finished call log (unchanged from before this feature was added)
    // -------------------------------------------------------------------

    private fun isDuplicateCall(
        callerInfo: String,
        timestamp: Long,
        source: String,
        timeWindowMs: Long = 3000
    ): Boolean {
        val query = """
        SELECT COUNT(*) FROM $TABLE_CALL_LOGS 
        WHERE $COLUMN_CALLER_INFO = ? 
        AND ABS($COLUMN_TIMESTAMP - ?) < ?
        AND $COLUMN_SOURCE = ?
    """.trimIndent()

        readableDatabase.rawQuery(
            query,
            arrayOf(callerInfo, timestamp.toString(), timeWindowMs.toString(), source)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0) > 0
            }
        }
        return false
    }

    @Synchronized
    fun insertCallLogWithType(
        callerInfo: String,
        timestamp: Long,
        callType: CallType,
        source: String,
        duration: Long
    ): Boolean {
        // Skip UNKNOWN types for VoIP
        if (callType == CallType.UNKNOWN && source != "Phone Call") {
            if (BuildConfig.DEBUG) Log.d(TAG, "Skipping UNKNOWN call type for source: $source")
            return false
        }

        val db = writableDatabase
        db.beginTransaction()
        try {
            // Duplicate check inside the transaction
            val query = """
            SELECT COUNT(*) FROM $TABLE_CALL_LOGS 
            WHERE $COLUMN_CALLER_INFO = ? 
            AND ABS($COLUMN_TIMESTAMP - ?) < 3000
            AND $COLUMN_SOURCE = ?
        """.trimIndent()
            db.rawQuery(query, arrayOf(callerInfo, timestamp.toString(), source)).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "Duplicate call detected, skipping insert: $callerInfo"
                    )
                    db.setTransactionSuccessful()
                    return false
                }
            }

            // Insert the new call
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

            val result = db.insert(TABLE_CALL_LOGS, null, values)
            if (result == -1L) {
                db.setTransactionSuccessful()
                return false
            }
            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error inserting call log", e)
            return false
        } finally {
            db.endTransaction()
        }
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

    fun clearAllCallLogs(): Boolean {
        val result = writableDatabase.delete(TABLE_CALL_LOGS, null, null)
        return result >= 0
    }

    // -------------------------------------------------------------------
    // Durable "active call" backstop (survives process death mid-call)
    // -------------------------------------------------------------------

    data class PersistedActiveCall(
        val callId: String,
        val number: String,
        val contactName: String,
        val callType: String,
        val source: String,
        val startTime: Long
    )

    fun upsertActiveCall(call: PersistedActiveCall): Boolean {
        val values = ContentValues().apply {
            put(COL_ACTIVE_CALL_ID, call.callId)
            put(COL_ACTIVE_NUMBER, call.number)
            put(COL_ACTIVE_CONTACT_NAME, call.contactName)
            put(COL_ACTIVE_CALL_TYPE, call.callType)
            put(COL_ACTIVE_SOURCE, call.source)
            put(COL_ACTIVE_START_TIME, call.startTime)
        }
        return try {
            writableDatabase.insertWithOnConflict(
                TABLE_ACTIVE_CALLS, null, values, SQLiteDatabase.CONFLICT_REPLACE
            ) != -1L
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to persist active call", e)
            false
        }
    }

    fun removeActiveCall(callId: String) {
        try {
            writableDatabase.delete(TABLE_ACTIVE_CALLS, "$COL_ACTIVE_CALL_ID = ?", arrayOf(callId))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to remove active call", e)
        }
    }

    fun getAllActiveCalls(): List<PersistedActiveCall> {
        val result = mutableListOf<PersistedActiveCall>()
        return try {
            readableDatabase.rawQuery("SELECT * FROM $TABLE_ACTIVE_CALLS", null).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(
                        PersistedActiveCall(
                            callId = getSafeString(cursor, COL_ACTIVE_CALL_ID, "") ?: "",
                            number = getSafeString(cursor, COL_ACTIVE_NUMBER, "") ?: "",
                            contactName = getSafeString(cursor, COL_ACTIVE_CONTACT_NAME, "") ?: "",
                            callType = getSafeString(cursor, COL_ACTIVE_CALL_TYPE, "UNKNOWN")
                                ?: "UNKNOWN",
                            source = getSafeString(cursor, COL_ACTIVE_SOURCE, "") ?: "",
                            startTime = getSafeLong(cursor, COL_ACTIVE_START_TIME, 0L)
                        )
                    )
                }
            }
            result
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to read active calls", e)
            emptyList()
        }
    }
}