package za.co.jpsoft.winkerkreader.utils

import za.co.jpsoft.winkerkreader.workers.BirthdayReminderWorker
import za.co.jpsoft.winkerkreader.workers.WidgetRefreshWorker
import za.co.jpsoft.winkerkreader.workers.DropboxDownloadWorker

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkManagerHelper {

    private const val TAG_DROPBOX = "dropbox_download"
    private const val TAG_WIDGET = "widget_refresh"
    private const val TAG_BIRTHDAY = "birthday_reminder"

    /**
     * Schedule weekly Dropbox download
     */
    fun scheduleDropboxDownload(context: Context, hour: Int, minute: Int, dayOfWeek: Int) {
        val workManager = WorkManager.getInstance(context)

        // Cancel existing work
        workManager.cancelUniqueWork(DropboxDownloadWorker.WORK_NAME)

        // Calculate initial delay
        val now = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
        }

        var initialDelay = targetTime.timeInMillis - now.timeInMillis
        if (initialDelay <= 0) {
            initialDelay += TimeUnit.DAYS.toMillis(7)
        }

        // Create constraints (optional: require network)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create periodic work request (runs weekly)
        val workRequest = PeriodicWorkRequestBuilder<DropboxDownloadWorker>(
            7, TimeUnit.DAYS,
            15, TimeUnit.MINUTES // Flex period for better scheduling
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(TAG_DROPBOX)
            .build()

        // Enqueue unique periodic work
        workManager.enqueueUniquePeriodicWork(
            DropboxDownloadWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Schedule daily widget refresh at 6:00 AM
     */
    fun scheduleWidgetRefresh(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // Cancel existing work
        workManager.cancelUniqueWork(WidgetRefreshWorker.WORK_NAME)

        // Calculate initial delay until 6:00 AM
        val now = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var initialDelay = targetTime.timeInMillis - now.timeInMillis
        if (initialDelay <= 0) {
            initialDelay += TimeUnit.DAYS.toMillis(1)
        }

        // Create periodic work request (runs daily)
        val workRequest = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            1, TimeUnit.DAYS,
            15, TimeUnit.MINUTES
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(TAG_WIDGET)
            .build()

        // Enqueue unique periodic work
        workManager.enqueueUniquePeriodicWork(
            WidgetRefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Schedule daily birthday reminder SMS
     */
    fun scheduleBirthdayReminder(context: Context, hour: Int, minute: Int) {
        val workManager = WorkManager.getInstance(context)

        // Cancel existing work
        workManager.cancelUniqueWork(BirthdayReminderWorker.WORK_NAME)

        // Calculate initial delay
        val now = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var initialDelay = targetTime.timeInMillis - now.timeInMillis
        if (initialDelay <= 0) {
            initialDelay += TimeUnit.DAYS.toMillis(1)
        }

        // Create periodic work request (runs daily)
        val workRequest = PeriodicWorkRequestBuilder<BirthdayReminderWorker>(
            1, TimeUnit.DAYS,
            15, TimeUnit.MINUTES
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(TAG_BIRTHDAY)
            .build()

        // Enqueue unique periodic work
        workManager.enqueueUniquePeriodicWork(
            BirthdayReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Cancel all scheduled work
     */
    fun cancelAllWork(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(DropboxDownloadWorker.WORK_NAME)
        workManager.cancelUniqueWork(WidgetRefreshWorker.WORK_NAME)
        workManager.cancelUniqueWork(BirthdayReminderWorker.WORK_NAME)
    }

    /**
     * Get work status (optional - for debugging)
     */
    fun getWorkStatus(context: Context, workName: String): androidx.work.WorkInfo? {
        val workManager = WorkManager.getInstance(context)
        // This would require collecting from LiveData or using getWorkInfosForUniqueWork
        return null
    }
}