package za.co.jpsoft.winkerkreader.workers

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.utils.PastoralDatabaseBackup
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class PastoralBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1 — Standard WKR-dir backup (for PC access)
            PastoralDatabaseBackup.backupNow(applicationContext)

            // 2 — Optional dated copy to Downloads (user-accessible restore source)
            if (inputData.getBoolean(KEY_EXPORT_TO_DOWNLOADS, false)) {
                exportToDownloads(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Scheduled backup failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG              = "PastoralBackupWorker"
        private const val WORK_NAME        = "pastoral_daily_backup"
        const val KEY_EXPORT_TO_DOWNLOADS  = "export_to_downloads"

        /** Enqueue a daily backup. Call once from Application.onCreate or a settings toggle. */
        fun schedule(context: Context, exportToDownloads: Boolean = false) {
            val data = Data.Builder()
                .putBoolean(KEY_EXPORT_TO_DOWNLOADS, exportToDownloads)
                .build()

            val request = PeriodicWorkRequestBuilder<PastoralBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // preserve existing schedule on upgrade
                request
            )
        }

        fun cancel(context: Context) =
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

        /**
         * Copies the live DB to the public Downloads/WinkerkReader folder.
         * Uses MediaStore on API 29+ (no WRITE_EXTERNAL_STORAGE needed).
         * Falls back to legacy File API on older devices.
         *
         * Filename pattern: wkr_pastoral_20250627.db
         * (Overwrites same-day file so Downloads doesn't fill up indefinitely.)
         */
        fun exportToDownloads(context: Context): Boolean {
            val source = context.getDatabasePath(winkerkEntry.PASTORAL_DB)
            if (!source.exists()) return false

            val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val fileName = "wkr_pastoral_$dateStr.db"
            val subDir  = "WinkerkReader"

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Remove any existing same-day file first (MediaStore won't overwrite)
                val resolver = context.contentResolver
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
                    arrayOf(fileName, "${Environment.DIRECTORY_DOWNLOADS}/$subDir/")
                )
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/$subDir")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                }
                true
            } else {
                @Suppress("DEPRECATION")
                val destDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    subDir
                ).also { it.mkdirs() }
                source.copyTo(File(destDir, fileName), overwrite = true)
                true
            }
        }
    }
}