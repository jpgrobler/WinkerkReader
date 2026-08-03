package za.co.jpsoft.winkerkreader.utils.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import za.co.jpsoft.winkerkreader.utils.prefs.BirthdaySmsPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.SyncPrefs
import za.co.jpsoft.winkerkreader.workers.PastoralBackupWorker
import za.co.jpsoft.winkerkreader.workers.WidgetRefreshWorker
import java.util.concurrent.TimeUnit

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncPrefs: SyncPrefs,
    private val birthdaySmsPrefs: BirthdaySmsPrefs,
    private val memberListPrefs: MemberListPrefs
) {

    fun scheduleAll() {
        scheduleAutoDownload()
        scheduleReminder()
        scheduleWidgetRefresh()
        scheduleFollowUpReminders()
    }

    fun scheduleAutoDownload() {
        if (syncPrefs.autoDl || syncPrefs.dlTimeUpdate) {
            val hour = syncPrefs.dlHour.toInt()
            val minute = syncPrefs.dlMinute.toInt()
            val day = syncPrefs.dlDay

            WorkManagerHelper.scheduleDropboxDownload(context, hour, minute, day)
            syncPrefs.dlTimeUpdate = false
            memberListPrefs.fromMenu = false
        }
    }

    fun scheduleReminder() {
        if (birthdaySmsPrefs.herinner || birthdaySmsPrefs.smsTimeUpdate) {
            val hour = birthdaySmsPrefs.smsHour.toInt()
            val minute = birthdaySmsPrefs.smsMinute.toInt()

            WorkManagerHelper.scheduleBirthdayReminder(context, hour, minute)
            birthdaySmsPrefs.smsTimeUpdate = false
            memberListPrefs.fromMenu = false
        }
    }

    fun scheduleWidgetRefresh() {
        WorkManagerHelper.scheduleWidgetRefresh(context)
    }

    fun scheduleFollowUpReminders() {
        WorkManagerHelper.scheduleFollowUpReminders(context)
    }

    fun refreshAll() {
        scheduleAll()
    }

    fun scheduleWidgetRefresh(initialDelay: Long = 30, unit: TimeUnit = TimeUnit.SECONDS) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(initialDelay, unit)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "widget_refresh_work",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    // WorkScheduler.kt (add this method)
    fun schedulePastoralBackup(enabled: Boolean, exportToDownloads: Boolean) {
        if (enabled) {
            PastoralBackupWorker.schedule(context, exportToDownloads)
        } else {
            PastoralBackupWorker.cancel(context)
        }
    }
}