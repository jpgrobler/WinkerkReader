package za.co.jpsoft.winkerkreader.utils


// CalendarManager.kt

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.models.CalendarInfo
import za.co.jpsoft.winkerkreader.data.models.CallType
import za.co.jpsoft.winkerkreader.utils.CalendarManager.Companion.PASTORAL_REMINDER_TOKEN
import za.co.jpsoft.winkerkreader.utils.CalendarManager.Companion.PASTORAL_TITLE_PREFIX
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone


class CalendarManager(private val context: Context) {
    private val contentResolver = context.contentResolver

    fun getAvailableCalendars(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()

        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.NAME,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
            )

            val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
            val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                if (it.count == 0) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "No calendars found on device")
                    return calendars
                }
                while (it.moveToNext()) {
                    val id = CursorDataExtractor.getSafeLong(it, CalendarContract.Calendars._ID, -1L) ?: -1L
                    val name = CursorDataExtractor.getSafeString(it, CalendarContract.Calendars.NAME, "") ?: ""
                    val displayName = CursorDataExtractor.getSafeString(it, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "") ?: ""
                    val accountName = CursorDataExtractor.getSafeString(it, CalendarContract.Calendars.ACCOUNT_NAME, "") ?: ""

                    calendars.add(CalendarInfo(id, name, displayName, accountName))
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Found ${calendars.size} calendars")
            } ?: run {
                if (BuildConfig.DEBUG) Log.w(TAG, "Calendar query returned null cursor - no calendars available or permission denied")
            }
        } catch (e: SecurityException) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Permission denied accessing calendars", e)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error getting calendars", e)
        }

        return calendars
    }

    /**
     * Inserts a single pastoral reminder event into [calendarId].
     *
     * Title is prefixed with [PASTORAL_TITLE_PREFIX] so pastoral events are visually
     * distinct and scannable by [isDuplicatePastoralEvent].
     * Description footer includes [PASTORAL_REMINDER_TOKEN] + [reminderId] for
     * orphan detection after restore.
     *
     * @return The new `CalendarContract.Events._ID`, or null if insert failed or
     *         a duplicate was detected.
     */
    fun addPastoralEvent(
        calendarId: Long,
        reminderId: String,
        memberDisplayName: String,
        title: String,
        note: String?,
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean
    ): Long? {
        if (isDuplicatePastoralEvent(calendarId, reminderId, startMillis, isAllDay)) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Skipping duplicate pastoral calendar event for reminder $reminderId")
            return null
        }

        val descriptionParts = buildList {
            if (!note.isNullOrBlank()) add(note.trim())
            add("$PASTORAL_REMINDER_TOKEN$reminderId")
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, "$PASTORAL_TITLE_PREFIX${title.trim()}")
            put(CalendarContract.Events.DESCRIPTION, descriptionParts.joinToString("\n\n"))
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 0)  // In-app notification owns alerting
        }

        return try {
            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull().also { id ->
                if (BuildConfig.DEBUG) Log.d(TAG, "Pastoral calendar event created: id=$id reminder=$reminderId")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to insert pastoral calendar event for $reminderId", e)
            null
        }
    }

    /**
     * Deletes the calendar event with [calendarEventId].
     * Safe to call even if the event no longer exists (ContentProvider returns 0 rows).
     *
     * @return true if the event was deleted, false if not found or deletion failed.
     */
    fun deletePastoralEvent(calendarEventId: Long): Boolean {
        val uri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI, calendarEventId
        )
        return try {
            val deleted = contentResolver.delete(uri, null, null)
            (deleted > 0).also {
                if (BuildConfig.DEBUG) Log.d(TAG, "Pastoral calendar event delete: id=$calendarEventId deleted=$it")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to delete pastoral calendar event $calendarEventId", e)
            false
        }
    }

    /**
     * Returns true if a pastoral event for [reminderId] already exists on [calendarId].
     *
     * Checks by description token first (fast, handles post-restore orphans), then
     * falls back to a time-window scan.
     *
     * @param startMillis  The proposed event start (epoch ms). Used for the time-window check.
     * @param isAllDay     When true, the time-window check spans the entire calendar day.
     */
    fun isDuplicatePastoralEvent(
        calendarId: Long,
        reminderId: String,
        startMillis: Long,
        isAllDay: Boolean = false
    ): Boolean {
        return isTokenDuplicate(calendarId, reminderId)
                || isTimeWindowDuplicate(calendarId, startMillis, isAllDay)
    }

    private fun isTokenDuplicate(calendarId: Long, reminderId: String): Boolean {
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? " +
                "AND ${CalendarContract.Events.DESCRIPTION} LIKE ? " +
                "AND ${CalendarContract.Events.DELETED} = 0"
        val token = "$PASTORAL_REMINDER_TOKEN$reminderId"

        return try {
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                arrayOf(calendarId.toString(), "%$token%"),
                null
            )?.use { cursor -> cursor.count > 0 } ?: false
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Token dedup query failed for $reminderId", e)
            false  // Fail open: let the insert proceed; the description token will catch it next time
        }
    }

    private fun isTimeWindowDuplicate(calendarId: Long, startMillis: Long, isAllDay: Boolean): Boolean {
        val (windowStart, windowEnd) = if (isAllDay) {
            // Same calendar day in device timezone
            val cal = Calendar.getInstance()
            cal.timeInMillis = startMillis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            dayStart to cal.timeInMillis
        } else {
            (startMillis - PASTORAL_TIMED_WINDOW_MS) to (startMillis + PASTORAL_TIMED_WINDOW_MS)
        }

        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? " +
                "AND ${CalendarContract.Events.TITLE} LIKE ? " +
                "AND ${CalendarContract.Events.DTSTART} >= ? " +
                "AND ${CalendarContract.Events.DTSTART} < ? " +
                "AND ${CalendarContract.Events.DELETED} = 0"

        return try {
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                arrayOf(
                    calendarId.toString(),
                    "$PASTORAL_TITLE_PREFIX%",
                    windowStart.toString(),
                    windowEnd.toString()
                ),
                null
            )?.use { cursor -> cursor.count > 0 } ?: false
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Time-window dedup query failed", e)
            false
        }
    }
    fun addCallEventToCalendar(
        calendarId: Long,
        callerInfo: String,
        timestamp: Long,
        callType: CallType,
        source: String,
        duration: Long
    ): Boolean {
        try {
            // Check if we have any calendars first
            val availableCalendars = getAvailableCalendars()
            if (availableCalendars.isEmpty()) {
                if (BuildConfig.DEBUG) Log.w(TAG, "No calendars available on device - cannot add event")
                return false
            }

            // Check if the specified calendar ID exists
            val calendarExists = availableCalendars.any { it.id == calendarId }
            if (!calendarExists) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Calendar with ID $calendarId not found")
                return false
            }

            // Check for duplicate calendar events first
            if (isDuplicateCalendarEvent(calendarId, callerInfo, timestamp, callType, source)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Duplicate calendar event detected, skipping - Contact: $callerInfo, Type: $callType, Source: $source")
                return true // Return true as it's not really an error
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, timestamp)
                put(CalendarContract.Events.DTEND, timestamp + duration * 1000) // Convert seconds to milliseconds
                put(CalendarContract.Events.TITLE, createEventTitle(callerInfo, callType, source))
                put(CalendarContract.Events.DESCRIPTION, createEventDescription(callerInfo, callType, source, duration, timestamp))
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.EVENT_COLOR, getEventColor(callType, source))
                put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

            return if (uri != null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Call event added to calendar successfully")
                true
            } else {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to add call event to calendar")
                false
            }

        } catch (e: SecurityException) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Permission denied adding event to calendar", e)
            return false
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error adding event to calendar", e)
            return false
        }
    }

    private fun isDuplicateCalendarEvent(
        calendarId: Long,
        callerInfo: String,
        timestamp: Long,
        callType: CallType,
        source: String
    ): Boolean {
        try {
            val timeWindow = 120000L // 2 minutes in milliseconds
            val startTime = timestamp - timeWindow
            val endTime = timestamp + timeWindow

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART
            )

            val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                    "${CalendarContract.Events.DTSTART} >= ? AND " +
                    "${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(
                calendarId.toString(),
                startTime.toString(),
                endTime.toString()
            )

            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val existingTitle = CursorDataExtractor.getSafeString(it, CalendarContract.Events.TITLE, "") ?: ""
                    val existingDescription = CursorDataExtractor.getSafeString(it, CalendarContract.Events.DESCRIPTION, "") ?: ""
                    val existingTime = CursorDataExtractor.getSafeLong(it, CalendarContract.Events.DTSTART, 0L)   // no Elvis, as it's non-nullable

                    val expectedTitle = createEventTitle(callerInfo, callType, source)

                    // Check if title matches and time is very close
                    if (existingTitle == expectedTitle && kotlin.math.abs(existingTime - timestamp) < timeWindow) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Found duplicate calendar event: $existingTitle at $existingTime")
                        return true
                    }

                    // Also check if description contains same caller and source info
                    if (existingDescription.contains(callerInfo) &&
                        existingDescription.contains(source) &&
                        existingDescription.contains(callType.name) &&
                        kotlin.math.abs(existingTime - timestamp) < timeWindow
                    ) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Found similar calendar event based on description")
                        return true
                    }
                }
            }

            return false
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error checking for duplicate calendar events", e)
            return false // If error, allow event to be created
        }
    }

    private fun createEventTitle(callerInfo: String, callType: CallType, source: String): String {
        val typeEmoji = when (callType) {
            CallType.INCOMING -> "📞"
            CallType.OUTGOING -> "📤"
            CallType.MISSED -> "📵"
            CallType.ENDED -> "📞"
            CallType.UNKNOWN -> "?"
            CallType.OTHER -> "??"
        }

        val sourceEmoji = when {
            "WhatsApp" == source -> "💬"
            "Phone Call" == source || source.contains("Phone") -> "📱"
            else -> "📞"
        }

        val localizedType = when (callType) {
            CallType.INCOMING -> context.getString(R.string.call_type_incoming)
            CallType.OUTGOING -> context.getString(R.string.call_type_outgoing)
            CallType.MISSED -> context.getString(R.string.call_type_missed)
            CallType.ENDED -> context.getString(R.string.call_type_ended)
            CallType.UNKNOWN -> context.getString(R.string.call_type_unknown)
            CallType.OTHER -> context.getString(R.string.call_type_other)
        }

        return context.getString(R.string.calendar_event_title, typeEmoji, sourceEmoji, localizedType, callerInfo)
    }

    private fun createEventDescription(
        callerInfo: String,
        callType: CallType,
        source: String,
        duration: Long,
        timestamp: Long
    ): String {
        val sb = StringBuilder()
        sb.append(context.getString(R.string.calendar_details_header)).append("\n")
        sb.append(context.getString(R.string.calendar_contact, callerInfo)).append("\n")
        
        val localizedType = when (callType) {
            CallType.INCOMING -> context.getString(R.string.call_type_incoming)
            CallType.OUTGOING -> context.getString(R.string.call_type_outgoing)
            CallType.MISSED -> context.getString(R.string.call_type_missed)
            CallType.ENDED -> context.getString(R.string.call_type_ended)
            CallType.UNKNOWN -> context.getString(R.string.call_type_unknown)
            CallType.OTHER -> context.getString(R.string.call_type_other)
        }
        sb.append(context.getString(R.string.calendar_type, localizedType)).append("\n")
        
        val localizedSource = when {
            "WhatsApp" == source -> context.getString(R.string.source_whatsapp)
            "Phone Call" == source || source.contains("Phone") -> context.getString(R.string.source_phone)
            else -> source
        }
        sb.append(context.getString(R.string.calendar_source, localizedSource)).append("\n")

        if (duration > 0) {
            val minutes = duration / 60
            val seconds = duration % 60
            sb.append(context.getString(R.string.calendar_duration, minutes.toInt(), seconds.toInt())).append("\n")
        }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateTime = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
        sb.append(context.getString(R.string.calendar_time, dateTime)).append("\n")
        sb.append("\n").append(context.getString(R.string.calendar_added_by))

        return sb.toString()
    }

    private fun getEventColor(callType: CallType, source: String): Int {
        // Return a color based on call type and source
        // Note: Actual color values depend on the calendar provider
        return CalendarContract.Colors.TYPE_EVENT
    }


    companion object {
        private const val TAG = "CalendarManager"
        private const val PASTORAL_TITLE_PREFIX    = "WKR Bediening: "
        private const val PASTORAL_REMINDER_TOKEN  = "wkr_reminder_id="
        private const val PASTORAL_TIMED_WINDOW_MS = 2 * 60 * 1000L   // ±2 min dedup window
    }
}