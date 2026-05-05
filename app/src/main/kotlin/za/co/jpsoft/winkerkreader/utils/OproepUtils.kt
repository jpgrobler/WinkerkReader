package za.co.jpsoft.winkerkreader.utils

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CallLog
import android.util.Log
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.models.CallRecord
import java.util.TimeZone

class OproepUtils(
    private val prefs: SharedPreferences,
    private val context: Context
) {
    companion object {
        private const val TAG = "OproepUtils"
        private const val PREF_LAST_SYNCED_CALL_ID = "last_synced_call_id"
    }

    private val logIncoming: Boolean
    private val logMissed: Boolean
    private val logOutgoing: Boolean

    init {
        logMissed = prefs.getBoolean(context.getString(R.string.log_missed_preference_key), true)
        logOutgoing = prefs.getBoolean(context.getString(R.string.log_outgoing_preference_key), true)
        logIncoming = prefs.getBoolean(context.getString(R.string.log_incoming_preference_key), true)
    }

    /**
     * Call this whenever a new call is detected (e.g., incoming ringing or outgoing start).
     * It syncs all calls that have not yet been added to the calendar.
     */
    fun syncRecentCallsToCalendar() {
        val calendarId = prefs.getString(context.getString(R.string.kalender_pref_key), "1") ?: "1"
        val lastSyncedId = prefs.getLong(PREF_LAST_SYNCED_CALL_ID, 0L)

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_NUMBER_TYPE
        )

        val selection = "${CallLog.Calls._ID} > ?"
        val selectionArgs = arrayOf(lastSyncedId.toString())
        val sortOrder = "${CallLog.Calls._ID} ASC"

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        ) ?: return

        val eventsToInsert = mutableListOf<ContentValues>()
        var maxSyncedId = lastSyncedId

        cursor.use {
            val idCol = it.getColumnIndex(CallLog.Calls._ID)
            val numberCol = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeCol = it.getColumnIndex(CallLog.Calls.TYPE)
            val durationCol = it.getColumnIndex(CallLog.Calls.DURATION)
            val dateCol = it.getColumnIndex(CallLog.Calls.DATE)
            val nameCol = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberTypeCol = it.getColumnIndex(CallLog.Calls.CACHED_NUMBER_TYPE)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val number = it.getString(numberCol) ?: ""
                val type = it.getInt(typeCol)
                val duration = it.getInt(durationCol)
                val date = it.getLong(dateCol)
                val name = it.getString(nameCol)
                val numberType = it.getInt(numberTypeCol)

                // Skip according to user preferences
                val shouldLog = when (type) {
                    CallLog.Calls.MISSED_TYPE -> logMissed
                    CallLog.Calls.OUTGOING_TYPE -> logOutgoing
                    CallLog.Calls.INCOMING_TYPE -> logIncoming
                    else -> false
                }

                if (shouldLog) {
                    val callRecord = CallRecord(number, name, type, duration, date, numberType, context)
                    // Build event with call log ID in description for perfect deduplication
                    val event = createEventWithCallId(
                        calendarId = calendarId,
                        title = callRecord.titel,
                        description = "${callRecord.beskrywing}\n[call_log_id=$id]",
                        start = callRecord.startTime,       // ✅ correct
                        end = callRecord.endTime            // ✅ correct
                    )
                    eventsToInsert.add(event)
                }

                if (id > maxSyncedId) {
                    maxSyncedId = id
                }
            }
        }

        if (eventsToInsert.isNotEmpty()) {
            try {
                val eventsUri = CalendarContract.Events.CONTENT_URI
                val insertCount = context.contentResolver.bulkInsert(eventsUri, eventsToInsert.toTypedArray())
                Log.d(TAG, "Inserted $insertCount call events into calendar")
                // Only update last synced ID after successful insertion
                prefs.edit().putLong(PREF_LAST_SYNCED_CALL_ID, maxSyncedId).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert calls into calendar", e)
            }
        } else {
            Log.d(TAG, "No new calls to sync")
        }
    }

    /**
     * Legacy method for backward compatibility – calls the incremental sync.
     * Kept to avoid breaking existing callers (e.g., OproepDetailService).
     */
    fun copyNewCallsToCalendar(naam: String) {
        syncRecentCallsToCalendar()
    }

    /**
     * Original method that copies ALL calls (no incremental). Kept for completeness.
     * In practice you should use syncRecentCallsToCalendar() instead.
     */
    fun copyAllCallsToCalendar() {
        val calendarId = prefs.getString(context.getString(R.string.kalender_pref_key), "1") ?: "1"
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_NUMBER_TYPE
        )
        val cursor = context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC")
        cursor?.use {
            val eventsArray = mutableListOf<ContentValues>()
            val numberCol = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeCol = it.getColumnIndex(CallLog.Calls.TYPE)
            val durationCol = it.getColumnIndex(CallLog.Calls.DURATION)
            val dateCol = it.getColumnIndex(CallLog.Calls.DATE)
            val nameCol = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberTypeCol = it.getColumnIndex(CallLog.Calls.CACHED_NUMBER_TYPE)

            while (it.moveToNext()) {
                val number = it.getString(numberCol)
                val type = it.getInt(typeCol)
                val duration = it.getInt(durationCol)
                val date = it.getLong(dateCol)
                val name = it.getString(nameCol)
                val numberType = it.getInt(numberTypeCol)
                val callRecord = CallRecord(number, name, type, duration, date, numberType, context)
                val shouldLog = (logMissed && type == CallLog.Calls.MISSED_TYPE) ||
                        (logOutgoing && type == CallLog.Calls.OUTGOING_TYPE) ||
                        (logIncoming && type == CallLog.Calls.INCOMING_TYPE)
                if (shouldLog) {
                    val event = createEvent(calendarId, callRecord.titel, callRecord.beskrywing, callRecord.startTime, callRecord.endTime)
                    eventsArray.add(event)
                }
            }
            if (eventsArray.isNotEmpty()) {
                try {
                    context.contentResolver.bulkInsert(CalendarContract.Events.CONTENT_URI, eventsArray.toTypedArray())
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to insert all calls into calendar", e)
                }
            }
        }
    }

    // Helper to create event with call log ID in description
    private fun createEventWithCallId(calendarId: String, title: String, description: String, start: Long, end: Long): ContentValues {
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId.toLong())
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
    }

    // Original createEvent (kept for compatibility)
    private fun createEvent(calendarId: String, title: String, description: String, start: Long, end: Long): ContentValues {
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId.toLong())
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
    }

    // For checking existence using description (not used in incremental sync, but kept if needed)
    fun isEventInCalendar(calId: String, rec: CallRecord): Boolean {
        val eventsUri = CalendarContract.Events.CONTENT_URI
        val selection = "calendar_id = ? AND dtstart = ? AND dtend = ? AND title = ?"
        val selectionArgs = arrayOf(
            calId,
            rec.startTime.toString(),
            rec.endTime.toString(),
            rec.titel
        )
        val cursor = context.contentResolver.query(eventsUri, arrayOf("_id"), selection, selectionArgs, null)
        return cursor?.use { it.count > 0 } ?: false
    }
}