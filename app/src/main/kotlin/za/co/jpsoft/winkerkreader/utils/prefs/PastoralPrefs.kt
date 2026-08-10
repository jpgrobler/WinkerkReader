package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract

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

    /** Timestamp the demo pastoral data was generated at, or null if never seeded / using real data. */
    var demoDataAnchorUtc: Long?
        get() {
            val v = prefs.getLong("PASTORAL_DEMO_ANCHOR_UTC", -1L)
            return if (v == -1L) null else v
        }
        set(value) = prefs.edit().putLong("PASTORAL_DEMO_ANCHOR_UTC", value ?: -1L).apply()
}