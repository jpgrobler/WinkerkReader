package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract

class CallMonitorPrefs(private val prefs: SharedPreferences) {

    var oproepTimeoutSeconds: Int
        get() = prefs.getString("oproep_timeout", "5")?.toIntOrNull() ?: 5
        set(value) = prefs.edit().putString("oproep_timeout", value.toString()).apply()

    var callMonitorEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_OPROEPMONITOR, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_OPROEPMONITOR, value).apply()

    var callLogEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_OPROEPLOG, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_OPROEPLOG, value).apply()

    var voipLogEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.winkerkEntry.KEY_LOG_VOIP, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.winkerkEntry.KEY_LOG_VOIP, value)
            .apply()

    var autoStartEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_AUTOSTART, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_AUTOSTART, value).apply()

    var diagnosticCallCaptureEnabled: Boolean
        get() = prefs.getBoolean("pref_diagnostic_call_capture", false)
        set(value) = prefs.edit().putBoolean("pref_diagnostic_call_capture", value).apply()

    var callLogImportedToRoom: Boolean
        get() = prefs.getBoolean("pref_call_log_imported_to_room", false)
        set(value) = prefs.edit().putBoolean("pref_call_log_imported_to_room", value).apply()

    // Calendar where call log events are written — was CalendarPrefs.selectedCalendarId
    var callCalendarId: Long?
        get() {
            val id = prefs.getLong(WinkerkContract.KEY_SELECTED_CALENDAR_ID, -1L)
            return if (id == -1L) null else id
        }
        set(value) = prefs.edit()
            .putLong(WinkerkContract.KEY_SELECTED_CALENDAR_ID, value ?: -1L).apply()
}