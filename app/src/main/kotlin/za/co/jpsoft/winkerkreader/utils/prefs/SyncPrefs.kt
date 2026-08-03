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

    // ─── Photo sync preferences ──────────────────────────────
    var photoSyncLastRun: Long
        get() = prefs.getLong("photo_sync_last_run", 0L)
        set(value) = prefs.edit().putLong("photo_sync_last_run", value).apply()

    var photoSyncAuto: Boolean
        get() = prefs.getBoolean("photo_sync_auto", false)
        set(value) = prefs.edit().putBoolean("photo_sync_auto", value).apply()

    // ─── Collapsible card expanded states ─────────────────────
    // Option A: store as a JSON string (e.g., {"card1":true, "card2":false})
    var cardExpandedStates: String
        get() = prefs.getString("card_expanded_states", "{}") ?: "{}"
        set(value) = prefs.edit().putString("card_expanded_states", value).apply()

    // ─── Collapsible card expanded states ──────────────────────────
    var cardLocalExpanded: Boolean
        get() = prefs.getBoolean("CARD_LOCAL_EXPANDED", false)
        set(value) = prefs.edit().putBoolean("CARD_LOCAL_EXPANDED", value).apply()

    var cardDropboxExpanded: Boolean
        get() = prefs.getBoolean("CARD_DROPBOX_EXPANDED", false)
        set(value) = prefs.edit().putBoolean("CARD_DROPBOX_EXPANDED", value).apply()

    var cardWifiExpanded: Boolean
        get() = prefs.getBoolean("CARD_WIFI_EXPANDED", true)
        set(value) = prefs.edit().putBoolean("CARD_WIFI_EXPANDED", value).apply()

    var cardPhotoExpanded: Boolean
        get() = prefs.getBoolean("CARD_PHOTO_EXPANDED", true)
        set(value) = prefs.edit().putBoolean("CARD_PHOTO_EXPANDED", value).apply()
}