package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.content.SharedPreferences
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.utils.prefs.*
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs.ThemeMode
import za.co.jpsoft.winkerkreader.utils.prefs.TasksPrefs.GoogleTasksMode

class SettingsManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE
    )
    private val securePrefs by lazy { EncryptedPrefsManager.getSecurePrefs(context) }

    // ─── Sub‑managers ──────────────────────────────────────────────────────────
    val widget by lazy { WidgetPrefs(prefs) }
    val quickActions by lazy { QuickActionPrefs(prefs) }
    val backup by lazy { BackupPrefs(prefs) }
    val pastoral by lazy { PastoralPrefs(prefs) }
    val callMonitor by lazy { CallMonitorPrefs(prefs) }
    val birthdaySms by lazy { BirthdaySmsPrefs(prefs) }
    val sync by lazy { SyncPrefs(prefs) }
    val memberList by lazy { MemberListPrefs(prefs) }
    val appearance by lazy { AppearancePrefs(prefs) }
    val congregation by lazy { CongregationPrefs(prefs, context.applicationContext) }
    val security by lazy { SecurityPrefs(prefs, securePrefs) }
    val tasks by lazy { TasksPrefs(prefs, securePrefs) }

    // ─── Calendar (kept here as it's used in multiple places) ──────────────
    // We keep the nullable API for calendar as well.
    var selectedCalendarId: Long?
        get() {
            val id = prefs.getLong(WinkerkContract.KEY_SELECTED_CALENDAR_ID, -1L)
            return if (id == -1L) null else id
        }
        set(value) = prefs.edit().putLong(WinkerkContract.KEY_SELECTED_CALENDAR_ID, value ?: -1L)
            .apply()

    // ─── Singleton ────────────────────────────────────────────────────────────

    companion object {
        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}