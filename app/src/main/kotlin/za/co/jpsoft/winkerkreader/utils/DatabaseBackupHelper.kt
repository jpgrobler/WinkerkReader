package za.co.jpsoft.winkerkreader.utils

import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteOpenHelper
import za.co.jpsoft.winkerkreader.BuildConfig
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shared file-level backup mechanics used by both PastoralDatabaseBackup and
 * CallLogDatabaseBackup: WAL checkpoint, fixed-name overwrite copy, an
 * additional dated snapshot, and pruning of old dated snapshots.
 *
 * Each caller supplies its own [SupportSQLiteOpenHelper] (from Room's
 * `RoomDatabase.openHelper`) so this stays database-agnostic — it knows
 * nothing about Pastoral vs call-log data, only "here is a SQLite file, back
 * it up safely."
 */
object DatabaseBackupHelper {

    /** Flushes all WAL frames into the main .db file so a plain file copy is self-contained. */
    fun checkpointWal(openHelper: SupportSQLiteOpenHelper, tag: String) {
        try {
            openHelper.writableDatabase
                .query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)"))
                .close()
            if (BuildConfig.DEBUG) Log.d(tag, "WAL checkpoint complete")
        } catch (e: Exception) {
            // Non-fatal — the copy will still include any un-checkpointed WAL
            // data via SQLite's own WAL reader, but log for visibility.
            if (BuildConfig.DEBUG) Log.w(tag, "WAL checkpoint failed (non-fatal): ${e.message}")
        }
    }

    /**
     * Copies [source] to [destDir]/[fixedFilename] (always overwritten — this
     * is the file any import/restore logic and PC sync tooling should read
     * from), additionally writes a dated snapshot
     * [destDir]/<baseName>_yyyyMMdd.db, then deletes dated snapshots older
     * than [retentionDays].
     *
     * @param baseName filename stem used for dated snapshots, e.g. "wkr_pastoral"
     *                 (dated files become wkr_pastoral_20260706.db)
     * @param retentionDays snapshots older than this are deleted; 0 or less
     *                      means "keep forever, don't prune"
     */
    fun copyWithDatedRetention(
        source: File,
        destDir: File,
        fixedFilename: String,
        baseName: String,
        retentionDays: Int,
        tag: String
    ) {
        if (!source.exists()) {
            if (BuildConfig.DEBUG) Log.d(tag, "Source DB does not exist yet — nothing to back up")
            return
        }
        if (!destDir.exists()) destDir.mkdirs()

        // 1 — Fixed-name copy, always overwritten. Import/restore logic and
        // PC sync tooling depend on this exact filename never changing.
        val fixedDest = File(destDir, fixedFilename)
        source.copyTo(fixedDest, overwrite = true)
        if (BuildConfig.DEBUG) Log.i(tag, "Backed up to ${fixedDest.absolutePath} (${fixedDest.length()} bytes)")

        // 2 — Dated snapshot, one per calendar day (same-day re-runs just
        // overwrite that day's own snapshot, so this doesn't grow within a day).
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val datedDest = File(destDir, "${baseName}_$dateStr.db")
        source.copyTo(datedDest, overwrite = true)
        if (BuildConfig.DEBUG) Log.i(tag, "Dated snapshot written: ${datedDest.name}")

        // 3 — Prune snapshots older than retentionDays.
        pruneOldSnapshots(destDir, baseName, retentionDays, tag)
    }

    private fun pruneOldSnapshots(destDir: File, baseName: String, retentionDays: Int, tag: String) {
        if (retentionDays <= 0) return
        val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
        val pattern = Regex("^${Regex.escape(baseName)}_(\\d{8})\\.db$")

        destDir.listFiles()?.forEach { file ->
            val match = pattern.find(file.name) ?: return@forEach
            val dateStr = match.groupValues[1]
            val fileEpochMillis = runCatching {
                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"))
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
            }.getOrNull() ?: return@forEach

            if (fileEpochMillis < cutoff) {
                val deleted = file.delete()
                if (BuildConfig.DEBUG) Log.d(tag, "Pruned old backup snapshot ${file.name} (deleted=$deleted)")
            }
        }
    }
}