// OproepUtils.kt
package za.co.jpsoft.winkerkreader

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CallLog
import android.util.Log
import java.util.TimeZone
import kotlin.math.roundToLong

class OproepUtils(
    private val prefs: SharedPreferences,
    private val context: Context
) {

    private val logIncoming: Boolean
    private val logMissed: Boolean
    private val logOutgoing: Boolean

    init {
        logMissed = prefs.getBoolean(context.getString(R.string.log_missed_preference_key), true)
        logOutgoing = prefs.getBoolean(context.getString(R.string.log_outgoing_preference_key), true)
        logIncoming = prefs.getBoolean(context.getString(R.string.log_incoming_preference_key), true)
    }

    fun copyNewCallsToCalendar(naam: String) {
        val projection = arrayOf("number", "type", "duration", "name", "date", "numbertype")
        val cur = context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "date DESC")
        cur?.use {
            val numberCol = it.getColumnIndex("number")
            val typeCol = it.getColumnIndex("type")
            val durationCol = it.getColumnIndex("duration")
            val dateCol = it.getColumnIndex("date")
            val cNameCol = it.getColumnIndex("name")
            val cTypeCol = it.getColumnIndex("numbertype")

            if (it.moveToFirst()) {
                val eventsArray = mutableListOf<ContentValues>()
                var i = 1
                do {
                    val number = it.getString(numberCol)
                    val type = it.getInt(typeCol)
                    val duration = it.getInt(durationCol)
                    val date = roundTimeToSecond(it.getLong(dateCol))
                    // Use passed naam instead of cursor name
                    val name = naam
                    val numberType = it.getInt(cTypeCol)
                    val calId = prefs.getString(context.getString(R.string.kalender_pref_key), "1") ?: "1"
                    val cr = CallRecord(number, name, type, duration, date, numberType, context)
                    if (!isEventInCalendar(calId, cr) && ((logMissed && type == 3) || (logOutgoing && type == 2) || (logIncoming && type == 1))) {
                        val event = createEvent(calId, cr.titel, cr.beskrywing, cr.startTime, cr.endTime)
                        eventsArray.add(event)
                    }
                    i++
                } while (it.moveToNext() && i < 5)

                if (eventsArray.isNotEmpty()) {
                    val eventsUri = CalendarContract.Events.CONTENT_URI
                    val cv = eventsArray.toTypedArray()
                    try {
                        context.contentResolver.bulkInsert(eventsUri, cv)
                    } catch (e: Exception) {
                        Log.e("CallTrack", "Kan nie na kalender skryf nie.")
                    }
                }
            }
        }
    }

    fun copyAllCallsToCalendar() {
        val projection = arrayOf("number", "type", "duration", "name", "date", "numbertype")
        val cur = context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "date DESC")
        cur?.use {
            val numberCol = it.getColumnIndex("number")
            val typeCol = it.getColumnIndex("type")
            val durationCol = it.getColumnIndex("duration")
            val dateCol = it.getColumnIndex("date")
            val cNameCol = it.getColumnIndex("name")
            val cTypeCol = it.getColumnIndex("numbertype")

            if (it.moveToFirst()) {
                val eventsArray = mutableListOf<ContentValues>()
                do {
                    val number = it.getString(numberCol)
                    val type = it.getInt(typeCol)
                    val duration = it.getInt(durationCol)
                    val date = roundTimeToSecond(it.getLong(dateCol))
                    val name = it.getString(cNameCol)
                    val numberType = it.getInt(cTypeCol)
                    val calId = prefs.getString(context.getString(R.string.kalender_pref_key), "1") ?: "1"
                    val cr = CallRecord(number, name, type, duration, date, numberType, context)
                    if (!isEventInCalendar(calId, cr) && ((logMissed && type == 3) || (logOutgoing && type == 2) || (logIncoming && type == 1))) {
                        val event = createEvent(calId, cr.titel, cr.beskrywing, cr.startTime, cr.endTime)
                        eventsArray.add(event)
                    }
                } while (it.moveToNext())

                if (eventsArray.isNotEmpty()) {
                    val eventsUri = CalendarContract.Events.CONTENT_URI
                    val cv = eventsArray.toTypedArray()
                    try {
                        context.contentResolver.bulkInsert(eventsUri, cv)
                    } catch (e: Exception) {
                        Log.e("CallTrack", "Kan nie alle oproepe na kalender skryf nie")
                    }
                }
            }
        }
    }

    fun isEventInCalendar(calId: String, rec: CallRecord): Boolean {
        val projection = arrayOf("calendar_id", "dtstart", "dtend", "title")
        val eventsUri = CalendarContract.Events.CONTENT_URI
        val selectionArgs = arrayOf(
            calId,
            roundTimeToSecond(rec.startTime).toString(),
            roundTimeToSecond(rec.endTime).toString(),
            rec.titel
        )
        val cur = context.contentResolver.query(
            eventsUri,
            projection,
            "calendar_id = ? AND dtstart = ? AND dtend = ? AND title = ?",
            selectionArgs,
            null
        )
        return cur?.use { it.count > 0 } ?: false
    }

    private fun roundTimeToSecond(time: Long): Long {
        return (time / 1000.0).roundToLong() * 1000
    }

    private fun createEvent(id: String, title: String, desc: String, start: Long, end: Long): ContentValues {
        return ContentValues().apply {
            put("calendar_id", id)
            put("title", title)
            put("description", desc)
            put("dtstart", start)
            put("dtend", end)
            put("eventTimezone", TimeZone.getDefault().id)
        }
    }
}