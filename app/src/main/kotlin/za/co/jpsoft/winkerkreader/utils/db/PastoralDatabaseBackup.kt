package za.co.jpsoft.winkerkreader.utils.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.utils.prefs.BackupPrefs
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Singleton
class PastoralDatabaseBackup @Inject constructor(
    private val backupPrefs: BackupPrefs
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var debounceJob: Job? = null

    @Volatile
    private var importInProgress = false

    companion object {
        @Volatile
        private var instance: PastoralDatabaseBackup? = null

        fun init(instance: PastoralDatabaseBackup) {
            this.instance = instance
        }

        fun getInstance(): PastoralDatabaseBackup {
            return instance ?: throw IllegalStateException("PastoralDatabaseBackup not initialized")
        }

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
    }

    // ─── Public API ───────────────────────────────────────────────────────

    /**
     * Schedules a backup after DEBOUNCE_MS of inactivity.
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

    // ─── Core backup logic ──────────────────────────────────────────────

    private fun runBackup(context: Context) {
        val db = PastoralDatabase.getInstance(context)
        DatabaseBackupHelper.checkpointWal(db.openHelper, TAG)

        val source = context.getDatabasePath(winkerkEntry.PASTORAL_DB)
        val destDir = File(winkerkEntry.getWkrDir(context))

        DatabaseBackupHelper.copyWithDatedRetention(
            source = source,
            destDir = destDir,
            fixedFilename = BACKUP_FILENAME,
            baseName = BACKUP_BASENAME,
            retentionDays = backupPrefs.backupRetentionDays,
            tag = TAG
        )
        backupPrefs.lastPastoralBackupTimestamp = System.currentTimeMillis()
    }

    // ─── Import helpers ──────────────────────────────────────────────────

    /**
     * Returns the backup file in the WKR dir if it exists, null otherwise.
     */
    fun findBackupFile(context: Context): File? {
        val file = File(winkerkEntry.getWkrDir(context), BACKUP_FILENAME)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Reads the Room schema version (`PRAGMA user_version`) from [backupFile].
     */
    fun readSchemaVersion(backupFile: File): Int {
        return try {
            SQLiteDatabase.openDatabase(
                backupFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { rawDb ->
                rawDb.version
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
     */
    suspend fun importBackup(context: Context, backupFile: File): Boolean =
        withContext(Dispatchers.IO) {
            if (importInProgress) return@withContext false
            importInProgress = true
            try {
                PastoralDatabase.closeInstance()

                val appContext = context.applicationContext
                val dest = appContext.getDatabasePath(winkerkEntry.PASTORAL_DB)
                val parent = dest.parentFile ?: return@withContext false
                if (!parent.exists() && !parent.mkdirs()) return@withContext false

                val temp = File(parent, "wkr_pastoral_import_temp.db")
                if (temp.exists()) temp.delete()
                backupFile.copyTo(temp, overwrite = true)

                listOf(
                    dest,
                    File("${dest.path}-wal"),
                    File("${dest.path}-shm")
                ).forEach { it.delete() }

                val renamed = temp.renameTo(dest)
                if (!renamed) {
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

                    else -> if (importBackup(
                            appContext,
                            tempFile
                        )
                    ) ImportResult.Success(version) else ImportResult.Failed
                }
            } finally {
                tempFile.delete()
            }
        }

    // ─── List & prune ─────────────────────────────────────────────────────

    data class BackupFileInfo(
        val file: File,
        val displayName: String,
        val isLatest: Boolean,
        val date: LocalDate?,
        val size: Long
    )

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
        object ReadError : ImportResult()
        object InvalidFile : ImportResult()
        object Failed : ImportResult()
        data class TooNew(val backupVersion: Int, val currentVersion: Int) : ImportResult()
        data class Success(val migratedFrom: Int) : ImportResult()
    }
}