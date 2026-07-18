package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.net.Uri
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
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.utils.PastoralDatabaseBackup.DEBOUNCE_MS
import za.co.jpsoft.winkerkreader.utils.PastoralDatabaseBackup.backupDebounced
import za.co.jpsoft.winkerkreader.utils.PastoralDatabaseBackup.backupNow
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Manages backup and restore of the pastoral database (wkr_pastoral.db).
 *
 * Two entry points:
 * - [backupDebounced] – called after every DB mutation; batches rapid writes with a 2s delay.
 * - [backupNow]       – called before congregation reload to guarantee a fresh copy exists.
 *
 * Backup strategy: WAL checkpoint (TRUNCATE) flushes all committed transactions into the main .db file,
 * then that file is copied both to a fixed name (for import/restore and PC sync tooling) and a dated
 * snapshot (for history). This avoids closing the database singleton and is safe while Room is active.
 */
object PastoralDatabaseBackup {

    private const val TAG = "PastoralDbBackup"
    private const val DEBOUNCE_MS = 2_000L
    private const val BACKUP_FILENAME = "wkr_pastoral.db"
    private const val BACKUP_BASENAME = "wkr_pastoral"

    /**
     * Single source of truth for the current pastoral DB schema version.
     * MUST match the [version] parameter in [PastoralDatabase]'s @Database annotation.
     * Bump both together whenever a new migration is added.
     */
    const val CURRENT_PASTORAL_SCHEMA_VERSION = 6

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var debounceJob: Job? = null

    @Volatile
    private var importInProgress = false

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Schedules a backup after [DEBOUNCE_MS] of inactivity.
     * Cancels any pending backup first — only the last mutation in a burst triggers a copy.
     * Errors are logged but not propagated (the caller is typically a repository mutation that
     * should not fail because a backup couldn't be written).
     */
    fun backupDebounced(context: Context) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            try {
                runBackup(context.applicationContext)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Debounced backup failed", e)
            }
        }
    }

    /**
     * Runs a backup immediately (blocking on IO).
     * Call this before any operation that might replace or delete congregation data.
     *
     * @return true if the backup succeeded, false if an error occurred.
     */
    suspend fun backupNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            runBackup(context.applicationContext)
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Immediate backup failed", e)
            false
        }
    }

    // -------------------------------------------------------------------------
    // Core backup logic
    // -------------------------------------------------------------------------

    /**
     * Performs the actual file copy. This function does NOT catch exceptions – they are
     * handled by the callers ([backupDebounced] and [backupNow]) so each can decide
     * whether to log, swallow, or propagate the error.
     */
    private fun runBackup(context: Context) {
        val db = PastoralDatabase.getInstance(context)
        DatabaseBackupHelper.checkpointWal(db.openHelper, TAG)

        val source = context.getDatabasePath(winkerkEntry.PASTORAL_DB)
        val destDir = File(winkerkEntry.getWkrDir(context))
        val settings = SettingsManager.getInstance(context)

        DatabaseBackupHelper.copyWithDatedRetention(
            source = source,
            destDir = destDir,
            fixedFilename = BACKUP_FILENAME,
            baseName = BACKUP_BASENAME,
            retentionDays = settings.backupRetentionDays,
            tag = TAG
        )
        settings.lastPastoralBackupTimestamp = System.currentTimeMillis()
    }

    // -------------------------------------------------------------------------
    // Import helpers (used by LaaiDatabasisActivity & PastoralBackupActivity)
    // -------------------------------------------------------------------------

    /**
     * Returns the backup file in the WKR dir if it exists, null otherwise.
     */
    fun findBackupFile(context: Context): File? {
        val file = File(winkerkEntry.getWkrDir(context), BACKUP_FILENAME)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Reads the Room schema version (`PRAGMA user_version`) from [backupFile]
     * without opening it through Room.
     *
     * @return The version integer, or -1 if unreadable.
     */
    fun readSchemaVersion(backupFile: File): Int {
        return try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                backupFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            ).use { rawDb ->
                rawDb.version   // Room stores @Database(version) in PRAGMA user_version
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(
                TAG,
                "Could not read schema version from ${backupFile.name}",
                e
            )
            -1
        }
    }

    /**
     * Swaps [backupFile] into Room's database directory atomically.
     * Copies to a temporary file in the same directory, then renames.
     * Returns true on success.
     */
    suspend fun importBackup(context: Context, backupFile: File): Boolean =
        withContext(Dispatchers.IO) {
            // Prevent overlapping imports
            if (importInProgress) return@withContext false
            importInProgress = true
            try {
                // 1 — Close Room to release file handles
                PastoralDatabase.closeInstance()

                val appContext = context.applicationContext
                val dest = appContext.getDatabasePath(winkerkEntry.PASTORAL_DB)
                val parent = dest.parentFile ?: return@withContext false
                if (!parent.exists() && !parent.mkdirs()) return@withContext false

                // 2 — Copy backup to a temporary file in the same directory
                val temp = File(parent, "wkr_pastoral_import_temp.db")
                if (temp.exists()) temp.delete()
                backupFile.copyTo(temp, overwrite = true)

                // 3 — Delete existing live DB + WAL/SHM
                listOf(
                    dest,
                    File("${dest.path}-wal"),
                    File("${dest.path}-shm")
                ).forEach { it.delete() }

                // 4 — Atomically rename temp to dest
                val renamed = temp.renameTo(dest)
                if (!renamed) {
                    // If rename fails, try to copy directly as fallback
                    temp.copyTo(dest, overwrite = true)
                    temp.delete()
                }
                true
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Import failed", e)
                false
            } finally {
                importInProgress = false
            }
        }

    suspend fun importFromUri(context: Context, uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val tempFile =
                File(appContext.cacheDir, "pastoral_import_${System.currentTimeMillis()}.db")
            try {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext ImportResult.ReadError

                val version = readSchemaVersion(tempFile)
                when {
                    version < 1 -> ImportResult.InvalidFile
                    version > CURRENT_PASTORAL_SCHEMA_VERSION -> ImportResult.TooNew(
                        version,
                        CURRENT_PASTORAL_SCHEMA_VERSION
                    )

                    else -> if (importBackup(appContext, tempFile)) ImportResult.Success(
                        migratedFrom = version
                    )
                    else ImportResult.Failed
                }
            } finally {
                tempFile.delete()
            }
        }
// PastoralDatabaseBackup.kt

    /**
     * Returns a list of all pastoral backup files in the WKR directory,
     * sorted by last modified descending (newest first).
     * Includes both the fixed "wkr_pastoral.db" and dated snapshots.
     */
    fun listBackupFiles(context: Context): List<BackupFileInfo> {
        val dir = File(winkerkEntry.getWkrDir(context))
        if (!dir.exists()) return emptyList()

        val pattern = Regex("^wkr_pastoral(?:_(\\d{8}))?\\.db$")
        return dir.listFiles()
            ?.filter { it.isFile && pattern.matches(it.name) }
            ?.map { file ->
                val match = pattern.find(file.name)
                val dateStr = match?.groupValues?.get(1)
                val date = dateStr?.let {
                    try {
                        LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyyMMdd"))
                    } catch (_: Exception) {
                        null
                    }
                }
                BackupFileInfo(
                    file = file,
                    displayName = if (dateStr == null) "Huidige rugsteun" else "Rugsteun van ${
                        date?.format(
                            DateTimeFormatter.ofPattern("d MMM yyyy")
                        ) ?: "Onbekende datum"
                    }",
                    isLatest = dateStr == null,
                    date = date,
                    size = file.length()
                )
            }
            ?.sortedByDescending { it.file.lastModified() }
            ?: emptyList()
    }

    data class BackupFileInfo(
        val file: File,
        val displayName: String,
        val isLatest: Boolean,
        val date: LocalDate?,
        val size: Long
    )

    /**
     * Deletes all dated snapshots older than [retentionDays] (default 7).
     * The fixed "wkr_pastoral.db" is never deleted.
     * Returns the number of deleted files.
     */
    fun pruneOldBackups(context: Context, retentionDays: Int = 7): Int {
        val cutoff = LocalDate.now().minusDays(retentionDays.toLong())
        val dir = File(winkerkEntry.getWkrDir(context))
        if (!dir.exists()) return 0
        val pattern = Regex("^wkr_pastoral_(\\d{8})\\.db$")
        var deleted = 0
        dir.listFiles()?.forEach { file ->
            val match = pattern.find(file.name)
            if (match != null) {
                val dateStr = match.groupValues[1]
                try {
                    val date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"))
                    if (date.isBefore(cutoff) && file.delete()) {
                        deleted++
                    }
                } catch (_: Exception) { /* skip malformed names */
                }
            }
        }
        return deleted
    }
    sealed class ImportResult {
        object ReadError : ImportResult()   // ContentResolver could not open stream
        object InvalidFile : ImportResult()   // Not a valid SQLite file (version < 1)
        object Failed : ImportResult()   // IO error during swap
        data class TooNew(val backupVersion: Int, val currentVersion: Int) : ImportResult()
        data class Success(val migratedFrom: Int) : ImportResult()
    }
}