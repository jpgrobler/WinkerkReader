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

/**
 * Copies [PastoralDatabase] to [winkerkEntry.getWkrDir] for PC-accessible backup.
 *
 * Two entry points:
 * - [backupDebounced] — called after every DB mutation; batches rapid writes with a 2s delay.
 * - [backupNow]       — called before congregation reload to guarantee a fresh copy exists.
 *
 * Copy strategy: WAL checkpoint (TRUNCATE) flushes all committed transactions into
 * the main .db file, then that file is copied both to a fixed name (for
 * import/restore and PC sync tooling) and a dated snapshot (for history —
 * see [DatabaseBackupHelper]). This avoids closing the database singleton
 * and is safe while Room is active.
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
     */
    fun backupDebounced(context: Context) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            runBackup(context.applicationContext)
        }
    }

    /**
     * Runs a backup immediately (blocking on IO).
     * Call this before any operation that might replace or delete congregation data.
     */
    suspend fun backupNow(context: Context) {
        withContext(Dispatchers.IO) {
            runBackup(context.applicationContext)
        }
    }

    // -------------------------------------------------------------------------
    // Core backup logic
    // -------------------------------------------------------------------------

    private fun runBackup(context: Context) {
        try {
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
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Pastoral DB backup failed", e)
        }
    }

    // -------------------------------------------------------------------------
    // Import helpers (used by LaaiDatabasisActivity) — unchanged
    // -------------------------------------------------------------------------

    /**
     * Returns the backup file in WKR dir if it exists, null otherwise.
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
                    ) else ImportResult.Failed
                }
            } finally {
                tempFile.delete()
            }
        }

    sealed class ImportResult {
        object ReadError : ImportResult()   // ContentResolver could not open stream
        object InvalidFile : ImportResult()   // Not a valid SQLite file (version < 1)
        object Failed : ImportResult()   // IO error during swap
        data class TooNew(val backupVersion: Int, val currentVersion: Int) : ImportResult()
        data class Success(val migratedFrom: Int) : ImportResult()
    }
}