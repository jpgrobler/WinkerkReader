package za.co.jpsoft.winkerkreader.utils

import za.co.jpsoft.winkerkreader.WinkerkReader

// CalendarManager.kt


import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import za.co.jpsoft.winkerkreader.data.models.CalendarInfo
import za.co.jpsoft.winkerkreader.data.models.CallType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class CalendarManager(private val context: Context) {

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
                    Log.w(TAG, "No calendars found on device")
                    return calendars
                }
                while (it.moveToNext()) {
                    val id = CursorDataExtractor.getSafeLong(it, CalendarContract.Calendars._ID, -1L) ?: -1L
                    val name = CursorDataExtractor.getSafeString(it, CalendarContract.Calendars.NAME, "") ?: ""
                    val displayName = CursorDataExtractor.getSafeString(it, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "") ?: ""
                    val accountName = CursorDataExtractor.getSafeString(it, CalendarContract.Calendars.ACCOUNT_NAME, "") ?: ""

                    calendars.add(CalendarInfo(id, name, displayName, accountName))
                }
                Log.d(TAG, "Found ${calendars.size} calendars")
            } ?: run {
                Log.w(TAG, "Calendar query returned null cursor - no calendars available or permission denied")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied accessing calendars", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting calendars", e)
        }

        return calendars
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
                Log.w(TAG, "No calendars available on device - cannot add event")
                return false
            }

            // Check if the specified calendar ID exists
            val calendarExists = availableCalendars.any { it.id == calendarId }
            if (!calendarExists) {
                Log.e(TAG, "Calendar with ID $calendarId not found")
                return false
            }

            // Check for duplicate calendar events first
            if (isDuplicateCalendarEvent(calendarId, callerInfo, timestamp, callType, source)) {
                Log.d(TAG, "Duplicate calendar event detected, skipping - Contact: $callerInfo, Type: $callType, Source: $source")
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
                Log.d(TAG, "Call event added to calendar successfully")
                true
            } else {
                Log.e(TAG, "Failed to add call event to calendar")
                false
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied adding event to calendar", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error adding event to calendar", e)
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
                        Log.d(TAG, "Found duplicate calendar event: $existingTitle at $existingTime")
                        return true
                    }

                    // Also check if description contains same caller and source info
                    if (existingDescription.contains(callerInfo) &&
                        existingDescription.contains(source) &&
                        existingDescription.contains(callType.name) &&
                        kotlin.math.abs(existingTime - timestamp) < timeWindow
                    ) {
                        Log.d(TAG, "Found similar calendar event based on description")
                        return true
                    }
                }
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for duplicate calendar events", e)
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
        }

        val sourceEmoji = when {
            "WhatsApp" == source -> "💬"
            "Phone Call" == source -> "📱"
            else -> "📞"
        }

        return "$typeEmoji $sourceEmoji ${callType.name} Oproep - $callerInfo"
    }

    private fun createEventDescription(
        callerInfo: String,
        callType: CallType,
        source: String,
        duration: Long,
        timestamp: Long
    ): String {
        val sb = StringBuilder()
        sb.append("Oproep Besonderhede:\n")
        sb.append("Kontak: ").append(callerInfo).append("\n")
        sb.append("Tipe: ").append(callType.name).append("\n")
        sb.append("Bron: ").append(source).append("\n")

        if (duration > 0) {
            val minutes = duration / 60
            val seconds = duration % 60
            sb.append("Duur: ").append(minutes).append("m ").append(seconds).append("s\n")
        }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateTime = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
        sb.append("Tyd: ").append(dateTime).append("\n")
        sb.append("\nBygevoeg deur WinkerkReader App")

        return sb.toString()
    }

    private fun getEventColor(callType: CallType, source: String): Int {
        // Return a color based on call type and source
        // Note: Actual color values depend on the calendar provider
        return CalendarContract.Colors.TYPE_EVENT
    }


    companion object {
        private const val TAG = "CalendarManager"
    }
}