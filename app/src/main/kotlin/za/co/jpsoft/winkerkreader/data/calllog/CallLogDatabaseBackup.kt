package za.co.jpsoft.winkerkreader.data.calllog

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.utils.DatabaseBackupHelper
import za.co.jpsoft.winkerkreader.utils.prefs.BackupPrefs
import java.io.File

/**
 * Mirrors [za.co.jpsoft.winkerkreader.utils.PastoralDatabaseBackup] for the
 * call-log database.
 *
 * Backup is **opt-in** via [BackupPrefs.callLogBackupEnabled] — call
 * history is more sensitive than reminders/notes (it can reveal contact
 * patterns even without any note content), so it shouldn't start landing in
 * a PC-accessible folder without the pastor deliberately choosing that. Both
 * [backupDebounced] and [backupNow] check the setting themselves, so callers
 * don't need to guard every call site individually.
 */
object CallLogDatabaseBackup {

    private const val TAG = "CallLogDbBackup"
    private const val DEBOUNCE_MS = 2_000L
    private const val DB_FILENAME = "wkr_call_log.db"
    private const val BACKUP_FILENAME = "wkr_call_log.db"
    private const val BACKUP_BASENAME = "wkr_call_log"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var debounceJob: Job? = null
    private lateinit var backupPrefs: BackupPrefs
    private var isInitialized = false

    /**
     * Must be called once before any other method (typically from Application).
     * @param prefs The BackupPrefs instance injected via Hilt.
     */
    fun init(prefs: BackupPrefs) {
        backupPrefs = prefs
        isInitialized = true
        if (BuildConfig.DEBUG) Log.d(TAG, "CallLogDatabaseBackup initialized")
    }

    /**
     * Schedules a backup after [DEBOUNCE_MS] of inactivity, same pattern as
     * PastoralDatabaseBackup. No-ops immediately if the user hasn't opted in.
     */
    fun backupDebounced(context: Context) {
        checkInitialized()
        if (!backupPrefs.callLogBackupEnabled) return

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            runBackup(context.applicationContext)
        }
    }

    /** Runs a backup immediately (blocking on IO). No-ops if not opted in. */
    suspend fun backupNow(context: Context) {
        checkInitialized()
        if (!backupPrefs.callLogBackupEnabled) return
        withContext(Dispatchers.IO) {
            runBackup(context.applicationContext)
        }
    }

    private fun runBackup(context: Context) {
        try {
            val db = CallLogDatabase.getInstance(context)
            DatabaseBackupHelper.checkpointWal(db.openHelper, TAG)

            val source = context.getDatabasePath(DB_FILENAME)
            val destDir = File(winkerkEntry.getWkrDir(context))

            DatabaseBackupHelper.copyWithDatedRetention(
                source = source,
                destDir = destDir,
                fixedFilename = BACKUP_FILENAME,
                baseName = BACKUP_BASENAME,
                retentionDays = backupPrefs.backupRetentionDays,
                tag = TAG
            )
            backupPrefs.lastCallLogBackupTimestamp = System.currentTimeMillis()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Call log DB backup failed", e)
        }
    }

    /** Returns the backup file in the WKR dir if it exists, null otherwise. */
    fun findBackupFile(context: Context): File? {
        val file = File(winkerkEntry.getWkrDir(context), BACKUP_FILENAME)
        return if (file.exists() && file.length() > 0) file else null
    }

    // ─── Internal helpers ──────────────────────────────────────────────────────

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("CallLogDatabaseBackup not initialized – call init() first")
        }
    }
}