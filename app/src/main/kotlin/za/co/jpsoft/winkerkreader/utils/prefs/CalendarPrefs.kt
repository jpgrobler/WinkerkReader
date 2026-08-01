package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences

class CalendarPrefs(private val prefs: SharedPreferences) {

    var selectedCalendarId: Long?
        get() {
            val id = prefs.getLong("selected_calendar_id", -1L)
            return if (id == -1L) null else id
        }
        set(value) = prefs.edit().putLong("selected_calendar_id", value ?: -1L).apply()
}