package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences

class BackupPrefs(private val prefs: SharedPreferences) {

    var dailyBackupEnabled: Boolean
        get() = prefs.getBoolean("daily_backup_enabled", true)
        set(value) = prefs.edit().putBoolean("daily_backup_enabled", value).apply()

    var backupExportToDownloads: Boolean
        get() = prefs.getBoolean("backup_export_to_downloads", false)
        set(value) = prefs.edit().putBoolean("backup_export_to_downloads", value).apply()

    var lastPastoralBackupTimestamp: Long
        get() = prefs.getLong("pref_last_pastoral_backup_ts", 0L)
        set(value) = prefs.edit().putLong("pref_last_pastoral_backup_ts", value).apply()

    var lastCallLogBackupTimestamp: Long
        get() = prefs.getLong("pref_last_calllog_backup_ts", 0L)
        set(value) = prefs.edit().putLong("pref_last_calllog_backup_ts", value).apply()

    var callLogBackupEnabled: Boolean
        get() = prefs.getBoolean("pref_calllog_backup_enabled", false)
        set(value) = prefs.edit().putBoolean("pref_calllog_backup_enabled", value).apply()

    var backupRetentionDays: Int
        get() = prefs.getInt("pref_backup_retention_days", 7)
        set(value) = prefs.edit().putInt("pref_backup_retention_days", value).apply()
}