package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences

class SyncPrefs(private val prefs: SharedPreferences) {

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

    var isDatabaseInitialized: Boolean
        get() = prefs.getBoolean("db_initialized", false)
        set(value) = prefs.edit().putBoolean("db_initialized", value).apply()

    var databaseSchemaVersion: Int
        get() = prefs.getInt("database_schema_version", 0)
        set(value) = prefs.edit().putInt("database_schema_version", value).apply()

    // ─── Additional properties for LaaiDatabasisActivity ──────────────────────

    var serverIp: String
        get() = prefs.getString("IP", "") ?: ""
        set(value) = prefs.edit().putString("IP", value).apply()

    var dropboxUrl: String
        get() = prefs.getString("DropBox", "") ?: ""
        set(value) = prefs.edit().putString("DropBox", value).apply()

    var syncPhotos: Boolean
        get() = prefs.getBoolean("SYNC_PHOTOS", false)
        set(value) = prefs.edit().putBoolean("SYNC_PHOTOS", value).apply()

    var pcProtocolVersion: String
        get() = prefs.getString("PC_PROTOCOL_VERSION", "v2") ?: "v2"
        set(value) = prefs.edit().putString("PC_PROTOCOL_VERSION", value).apply()

    var fromMenu: Boolean
        get() = prefs.getBoolean("FROM_MENU", false)
        set(value) = prefs.edit().putBoolean("FROM_MENU", value).apply()
}