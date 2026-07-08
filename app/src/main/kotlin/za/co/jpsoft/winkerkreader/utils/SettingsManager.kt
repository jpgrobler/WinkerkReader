package za.co.jpsoft.winkerkreader.utils


import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.KEY_INACTIVE_BG_COLOR
import za.co.jpsoft.winkerkreader.data.WinkerkContract.KEY_PASTORAL_SYNC_CALENDAR

/**
 * Central manager for all app preferences.
 * All preference keys are defined in [WinkerkContract].
 */
class SettingsManager(private val context: Context) {

    companion object {
        private const val PREF_DB_INITIALIZED = "db_initialized"

        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun isDatabaseInitialized(): Boolean {
        return prefs.getBoolean(PREF_DB_INITIALIZED, false)
    }

    fun setDatabaseInitialized(initialized: Boolean) {
        prefs.edit().putBoolean(PREF_DB_INITIALIZED, initialized).apply()
    }

    fun getBirthdayMessage(): String = prefs.getString("VerjaarBoodskap", "") ?: ""

    fun setBirthdayMessage(value: String) = prefs.edit().putString("VerjaarBoodskap", value).apply()

    /** True when the user has opted into calendar mirroring for pastoral reminders. Default false. */
    fun isPastoralCalendarSyncEnabled(): Boolean =
        prefs.getBoolean(KEY_PASTORAL_SYNC_CALENDAR, false)

    /**
     * When true AND [isPastoralCalendarSyncEnabled], TIMED reminders are automatically
     * pushed to the calendar on creation. DATE_ONLY reminders always require explicit
     * "Voeg by kalender" user action.
     */
    fun isPastoralCalendarAutoTimedEnabled(): Boolean =
        prefs.getBoolean(WinkerkContract.KEY_PASTORAL_CALENDAR_AUTO_TIMED, false)

    /** Returns the selected calendar ID, or null if not configured. Reuses existing key. */
    fun selectedCalendarId(): Long? {
        val id = prefs.getLong(WinkerkContract.KEY_SELECTED_CALENDAR_ID, -1L)
        return if (id == -1L) null else id
    }
    var pastoralCalendarSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_PASTORAL_SYNC_CALENDAR, false)
        set(value) = prefs.edit().putBoolean(KEY_PASTORAL_SYNC_CALENDAR, value).apply()

    var voipLogEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.winkerkEntry.KEY_LOG_VOIP, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.winkerkEntry.KEY_LOG_VOIP, value).apply()

    private val prefs: SharedPreferences = context.getSharedPreferences(
        WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE
    )

    // ===== Display settings =====
    var isListFoto: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_FOTO, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_FOTO, value).apply()

    var isListEpos: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_EPOS, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_EPOS, value).apply()

    var isListWhatsapp: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_WHATSAPP, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_WHATSAPP, value).apply()

    var isListVerjaarBlok: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_VERJAARBLOK, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_VERJAARBLOK, value).apply()

    var isListOuderdom: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_OUDERDOM, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_OUDERDOM, value).apply()

    var isListHuwelikBlok: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_HUWELIKBLOK, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_HUWELIKBLOK, value).apply()

    var isListWyk: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_WYK, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_WYK, value).apply()

    var isListSelfoon: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_SELFOON, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_SELFOON, value).apply()

    var isListTelefoon: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_TELEFOON, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_TELEFOON, value).apply()

    // ===== Function settings =====
    var autoStartEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_AUTOSTART, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_AUTOSTART, value).apply()

    var callMonitorEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_OPROEPMONITOR, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_OPROEPMONITOR, value).apply()

    var callLogEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_OPROEPLOG, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_OPROEPLOG, value).apply()

    var defLayout: String
        get() {
            val value = prefs.getString(WinkerkContract.KEY_DEFLAYOUT, "GESINNE") ?: "GESINNE"
            if (BuildConfig.DEBUG) Log.d("SettingsManager", "get defLayout = $value")
            return value
        }
        set(value) {
            if (BuildConfig.DEBUG) Log.d("SettingsManager", "set defLayout = $value")
            prefs.edit().putString(WinkerkContract.KEY_DEFLAYOUT, value).apply()
        }

    var whatsapp1: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WHATSAPP1, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WHATSAPP1, value).apply()

    var whatsapp2: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WHATSAPP2, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WHATSAPP2, value).apply()

    var whatsapp3: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WHATSAPP3, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WHATSAPP3, value).apply()

    var eposHtml: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_EPOSHTML, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_EPOSHTML, value).apply()

    var selectedCalendarId: Long
        get() = prefs.getLong(WinkerkContract.KEY_SELECTED_CALENDAR_ID, -1L)
        set(value) = prefs.edit().putLong(WinkerkContract.KEY_SELECTED_CALENDAR_ID, value).apply()

    // ===== Widget settings =====
    var widgetDoop: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WIDGET_DOOP, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WIDGET_DOOP, value).apply()

    var widgetBelydenis: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WIDGET_BELYDENIS, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WIDGET_BELYDENIS, value).apply()

    var widgetHuwelik: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WIDGET_HUWELIK, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WIDGET_HUWELIK, value).apply()

    var widgetSterf: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WIDGET_STERF, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WIDGET_STERF, value).apply()

    // ===== Color settings =====
    // SettingsManager.kt

    var gemeenteKleur: Int
        get() = prefs.getInt(WinkerkContract.KEY_GEMEENTE_KLEUR, Int.MIN_VALUE)
        set(value) = prefs.edit().putInt(WinkerkContract.KEY_GEMEENTE_KLEUR, value).apply()

    var gemeente2Kleur: Int
        get() = prefs.getInt(WinkerkContract.KEY_GEMEENTE2_KLEUR, Int.MIN_VALUE)  // changed from -3355444
        set(value) = prefs.edit().putInt(WinkerkContract.KEY_GEMEENTE2_KLEUR, value).apply()

    var gemeente3Kleur: Int
        get() = prefs.getInt(WinkerkContract.KEY_GEMEENTE3_KLEUR, Int.MIN_VALUE)  // changed from -256
        set(value) = prefs.edit().putInt(WinkerkContract.KEY_GEMEENTE3_KLEUR, value).apply()

    var inactiveBackgroundColor: Int
        get() = prefs.getInt(KEY_INACTIVE_BG_COLOR, Int.MIN_VALUE)  // changed from ContextCompat.getColor(...)
        set(value) = prefs.edit().putInt(KEY_INACTIVE_BG_COLOR, value).apply()

    var gemeenteNaam: String
        get() = prefs.getString("Gemeente", "") ?: ""
        set(value) = prefs.edit().putString("Gemeente", value).apply()

    var gemeenteEpos: String
        get() = prefs.getString("Gemeente_Epos", "") ?: ""
        set(value) = prefs.edit().putString("Gemeente_Epos", value).apply()

    var gemeente2Naam: String
        get() = prefs.getString("Gemeente2", "") ?: ""
        set(value) = prefs.edit().putString("Gemeente2", value).apply()

    var gemeente2Epos: String
        get() = prefs.getString("Gemeente2_Epos", "") ?: ""
        set(value) = prefs.edit().putString("Gemeente2_Epos", value).apply()

    var gemeente3Naam: String
        get() = prefs.getString("Gemeente3", "") ?: ""
        set(value) = prefs.edit().putString("Gemeente3", value).apply()

    var gemeente3Epos: String
        get() = prefs.getString("Gemeente3_Epos", "") ?: ""
        set(value) = prefs.edit().putString("Gemeente3_Epos", value).apply()

    var dataDatum: String
        get() = prefs.getString("DATA_DATUM", "") ?: ""
        set(value) = prefs.edit().putString("DATA_DATUM", value).apply()

    // ===== SMS reminder settings =====
    var autoSms: Boolean
        get() = prefs.getBoolean("AUTO_SMS", false)
        set(value) = prefs.edit().putBoolean("AUTO_SMS", value).apply()

    var herinner: Boolean
        get() = prefs.getBoolean("HERINNER", false)
        set(value) = prefs.edit().putBoolean("HERINNER", value).apply()

    var smsHour: String
        get() = prefs.getString("SMS-HOUR", "08") ?: "08"
        set(value) = prefs.edit().putString("SMS-HOUR", value).apply()

    var smsMinute: String
        get() = prefs.getString("SMS-MINUTE", "00") ?: "00"
        set(value) = prefs.edit().putString("SMS-MINUTE", value).apply()

    var smsTimeUpdate: Boolean
        get() = prefs.getBoolean("SMS-TIMEUPDATE", false)
        set(value) = prefs.edit().putBoolean("SMS-TIMEUPDATE", value).apply()

    // ===== Auto download settings =====
    var autoDl: Boolean
        get() = prefs.getBoolean("AUTO_DL", false)
        set(value) = prefs.edit().putBoolean("AUTO_DL", value).apply()

    var dlHour: String
        get() = prefs.getString("DL-HOUR", "08") ?: "08"
        set(value) = prefs.edit().putString("DL-HOUR", value).apply()

    var dlMinute: String
        get() = prefs.getString("DL-MINUTE", "00") ?: "00"
        set(value) = prefs.edit().putString("DL-MINUTE", value).apply()

    var dlDay: Int
        get() = prefs.getInt("DL-DAY", 6)
        set(value) = prefs.edit().putInt("DL-DAY", value).apply()

    var dlTimeUpdate: Boolean
        get() = prefs.getBoolean("DL-TIMEUPDATE", false)
        set(value) = prefs.edit().putBoolean("DL-TIMEUPDATE", value).apply()

    // ===== Other flags =====
    var fromMenu: Boolean
        get() = prefs.getBoolean("FROM_MENU", false)
        set(value) = prefs.edit().putBoolean("FROM_MENU", value).apply()

    var listView: Int
        get() = prefs.getInt("LIST_VIEW", 2)
        set(value) = prefs.edit().putInt("LIST_VIEW", value).apply()

    var groepView: Int
        get() = prefs.getInt("GROEP_VIEW", 500) // WkrContract.winkerkEntry.GROEPLIST_LOADER
        set(value) = prefs.edit().putInt("GROEP_VIEW", value).apply()

    enum class GoogleTasksMode { OFF, API, SHARE }

    fun googleTasksMode(): GoogleTasksMode {
        val stored = prefs.getString(WinkerkContract.KEY_GOOGLE_TASKS_MODE, "off")
        return when (stored) {
            "api"   -> GoogleTasksMode.API
            "share" -> GoogleTasksMode.SHARE
            else    -> GoogleTasksMode.OFF
        }
    }

    fun setGoogleTasksMode(mode: GoogleTasksMode) {
        prefs.edit().putString(
            WinkerkContract.KEY_GOOGLE_TASKS_MODE,
            mode.name.lowercase()
        ).apply()
    }

    var googleTasksListId: String?
        get() = prefs.getString(WinkerkContract.KEY_GOOGLE_TASKS_LIST_ID, null)
        set(value) = prefs.edit().putString(WinkerkContract.KEY_GOOGLE_TASKS_LIST_ID, value).apply()

    var googleTasksAccountEmail: String?
        get() = prefs.getString(WinkerkContract.KEY_GOOGLE_TASKS_ACCOUNT, null)
        set(value) = prefs.edit().putString(WinkerkContract.KEY_GOOGLE_TASKS_ACCOUNT, value).apply()

    fun getPastoralCalendarId(): Long? {
        val id = prefs.getLong(WinkerkContract.KEY_PASTORAL_CALENDAR_ID, -1L)
        return if (id == -1L) null else id
    }

    fun setPastoralCalendarId(id: Long?) {
        prefs.edit().putLong(WinkerkContract.KEY_PASTORAL_CALENDAR_ID, id ?: -1L).apply()
    }

    var tasksScriptUrl: String?
        get() {
            migrateToSecure(WinkerkContract.KEY_TASKS_SCRIPT_URL)
            return securePrefs.getString(WinkerkContract.KEY_TASKS_SCRIPT_URL, null)
        }
        set(value) = securePrefs.edit().putString(WinkerkContract.KEY_TASKS_SCRIPT_URL, value?.trim()).apply()

    var tasksScriptSecret: String?
        get() {
            migrateToSecure(WinkerkContract.KEY_TASKS_SCRIPT_SECRET)
            return securePrefs.getString(WinkerkContract.KEY_TASKS_SCRIPT_SECRET, null)
        }
        set(value) = securePrefs.edit().putString(WinkerkContract.KEY_TASKS_SCRIPT_SECRET, value?.trim()).apply()

    fun isTasksScriptConfigured(): Boolean =
        !tasksScriptUrl.isNullOrBlank() && !tasksScriptSecret.isNullOrBlank()

    var appBiometricEnabled: Boolean
        get() {
            migrateBooleanToSecure("app_biometric_enabled")
            return securePrefs.getBoolean("app_biometric_enabled", false)
        }
        set(value) = securePrefs.edit().putBoolean("app_biometric_enabled", value).apply()

    var appBiometricTimeoutMs: Long
        get() {
            migrateLongToSecure("app_biometric_timeout_ms")
            return securePrefs.getLong("app_biometric_timeout_ms", Long.MAX_VALUE)
        }
        set(value) = securePrefs.edit().putLong("app_biometric_timeout_ms", value).apply()

    var databaseSchemaVersion: Int
        get() = prefs.getInt("database_schema_version", 0)
        set(value) = prefs.edit().putInt("database_schema_version", value).apply()

    // Add to SettingsManager.kt
    var dailyBackupEnabled: Boolean
        get() = prefs.getBoolean("daily_backup_enabled", true)
        set(value) = prefs.edit().putBoolean("daily_backup_enabled", value).apply()

    var backupExportToDownloads: Boolean
        get() = prefs.getBoolean("backup_export_to_downloads", false)
        set(value) = prefs.edit().putBoolean("backup_export_to_downloads", value).apply()

    var diagnosticCallCaptureEnabled: Boolean
        get() = prefs.getBoolean("pref_diagnostic_call_capture", false)
        set(value) = prefs.edit().putBoolean("pref_diagnostic_call_capture", value).apply()

    var callLogImportedToRoom: Boolean
        get() = prefs.getBoolean("pref_call_log_imported_to_room", false)
        set(value) = prefs.edit().putBoolean("pref_call_log_imported_to_room", value).apply()

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    var themeMode: ThemeMode
        get() {
            val value = prefs.getString("theme_mode", "light") // ← change default to "light"
            return when (value) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }
        set(value) {
            prefs.edit().putString("theme_mode", value.name.lowercase()).apply()
        }

// Inside SettingsManager class, add this method:

    fun ensureDefaultColors() {
        if (gemeenteKleur == Int.MIN_VALUE) {
            gemeenteKleur = androidx.core.content.ContextCompat.getColor(context, R.color.default_gemeente_1)
        }
        if (gemeente2Kleur == Int.MIN_VALUE) {
            gemeente2Kleur = androidx.core.content.ContextCompat.getColor(context, R.color.default_gemeente_2)
        }
        if (gemeente3Kleur == Int.MIN_VALUE) {
            gemeente3Kleur = androidx.core.content.ContextCompat.getColor(context, R.color.default_gemeente_3)
        }
        if (inactiveBackgroundColor == Int.MIN_VALUE) {
            inactiveBackgroundColor = androidx.core.content.ContextCompat.getColor(context, R.color.inactive_background)
        }
    }

    var lastPastoralBackupTimestamp: Long
        get() = prefs.getLong("pref_last_pastoral_backup_ts", 0L)
        set(value) = prefs.edit().putLong("pref_last_pastoral_backup_ts", value).apply()

    var lastCallLogBackupTimestamp: Long
        get() = prefs.getLong("pref_last_calllog_backup_ts", 0L)
        set(value) = prefs.edit().putLong("pref_last_calllog_backup_ts", value).apply()

    var callLogBackupEnabled: Boolean
        get() = prefs.getBoolean("pref_calllog_backup_enabled", false)   // opt-in — call logs are more sensitive
        set(value) = prefs.edit().putBoolean("pref_calllog_backup_enabled", value).apply()

    var backupRetentionDays: Int
        get() = prefs.getInt("pref_backup_retention_days", 7)
        set(value) = prefs.edit().putInt("pref_backup_retention_days", value).apply()

    private val securePrefs by lazy {
        EncryptedPrefsManager.getSecurePrefs(context)
    }

    // Helper to migrate a key from regular to secure prefs once
    private fun migrateToSecure(key: String) {
        if (prefs.contains(key) && !securePrefs.contains(key)) {
            val value = prefs.getString(key, null)
            if (value != null) {
                securePrefs.edit().putString(key, value).apply()
                prefs.edit().remove(key).apply()
            }
        }
    }

    // For Boolean keys
    private fun migrateBooleanToSecure(key: String) {
        if (prefs.contains(key) && !securePrefs.contains(key)) {
            val value = prefs.getBoolean(key, false)
            securePrefs.edit().putBoolean(key, value).apply()
            prefs.edit().remove(key).apply()
        }
    }

    private fun migrateLongToSecure(key: String) {
        if (prefs.contains(key) && !securePrefs.contains(key)) {
            val value = prefs.getLong(key, Long.MAX_VALUE)
            securePrefs.edit().putLong(key, value).apply()
            prefs.edit().remove(key).apply()
        }
    }
}