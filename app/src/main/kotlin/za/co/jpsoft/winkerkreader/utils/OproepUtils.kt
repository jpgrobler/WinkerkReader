package za.co.jpsoft.winkerkreader.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CallLog
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import za.co.jpsoft.winkerkreader.BuildConfig
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
    @WorkerThread
    fun syncRecentCallsToCalendar() {
        // Check permissions
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                    != PackageManager.PERMISSION_GRANTED) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "READ_CALL_LOG permission missing, skipping sync")
                    return
                }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            if (BuildConfig.DEBUG) Log.w(TAG, "WRITE_CALENDAR permission missing, skipping sync")
            return
        }
        val calendarId = prefs.getString(context.getString(R.string.kalender_pref_key), "1")
            ?.toLongOrNull()
            ?: 1L
        var lastSyncedId = prefs.getLong(PREF_LAST_SYNCED_CALL_ID, 0L)

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

        var maxProcessedId = lastSyncedId

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
                    val tag = "[call_log_id=$id]"

                    // Check if an event with this call_log_id already exists in the calendar
                    val exists = eventExistsWithTag(calendarId, tag)

                    if (!exists) {
                        // Build and insert the event
                        val values = createEventWithCallId(
                            calendarId = calendarId,
                            title = callRecord.titel,
                            description = "${callRecord.beskrywing}\n$tag",
                            start = callRecord.startTime,
                            end = callRecord.endTime
                        )

                        try {
                            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                            if (uri == null) {
                                if (BuildConfig.DEBUG) Log.e(TAG, "Insert returned null for call ID $id")
                                // Stop processing to avoid gaps; next sync will retry from maxProcessedId
                                break
                            }
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to insert call ID $id", e)
                            // Stop processing – next sync retries from current maxProcessedId
                            break
                        }
                    }

                    // Update maxProcessedId after successfully processing this call (inserted or already existed)
                    if (id > maxProcessedId) {
                        maxProcessedId = id
                    }
                } else {
                    // Even skipped calls should advance the last synced ID if they are newer
                    if (id > maxProcessedId) {
                        maxProcessedId = id
                    }
                }
            }
        }

        // Persist the new last synced ID after all processing
        if (maxProcessedId > lastSyncedId) {
            prefs.edit { putLong(PREF_LAST_SYNCED_CALL_ID, maxProcessedId) }
            if (BuildConfig.DEBUG) Log.d(TAG, "Advanced last synced ID to $maxProcessedId")
        }
    }

    /**
     * Checks whether an event with the given [tag] (containing the call_log_id)
     * already exists in the specified calendar.
     */
    @WorkerThread
    private fun eventExistsWithTag(calendarId: Long, tag: String): Boolean {
        val selection = "${CalendarContract.Events.DESCRIPTION} LIKE ? AND ${CalendarContract.Events.CALENDAR_ID} = ?"
        val pattern = "%$tag%"
        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            selection,
            arrayOf(pattern, calendarId.toString()),
            null
        )
        return cursor?.use { it.moveToFirst() } == true
    }

    // Helper to create event with call log ID in description
    // Helper to create event with call log ID in description
    @WorkerThread
    private fun createEventWithCallId(calendarId: Long, title: String, description: String, start: Long, end: Long): ContentValues {
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
    }
}