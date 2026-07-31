package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences
import za.co.jpsoft.winkerkreader.data.WinkerkContract

class PastoralPrefs(private val prefs: SharedPreferences) {

    // Fixed: keep only the var, remove the separate function
    var pastoralCalendarSyncEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_PASTORAL_SYNC_CALENDAR, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_PASTORAL_SYNC_CALENDAR, value)
            .apply()

    var pastoralCalendarAutoTimedEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_PASTORAL_CALENDAR_AUTO_TIMED, false)
        set(value) = prefs.edit()
            .putBoolean(WinkerkContract.KEY_PASTORAL_CALENDAR_AUTO_TIMED, value).apply()

    // Canonical nullable API – no raw Long variant
    var pastoralCalendarId: Long?
        get() {
            val id = prefs.getLong(WinkerkContract.KEY_PASTORAL_CALENDAR_ID, -1L)
            return if (id == -1L) null else id
        }
        set(value) = prefs.edit().putLong(WinkerkContract.KEY_PASTORAL_CALENDAR_ID, value ?: -1L)
            .apply()
}