package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import za.co.jpsoft.winkerkreader.utils.prefs.BirthdaySmsPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.SyncPrefs
import za.co.jpsoft.winkerkreader.widget.PastoralWidgetProvider
import javax.inject.Inject
import javax.inject.Singleton

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
        PastoralWidgetProvider.refreshWidgets(context)
    }

    fun scheduleFollowUpReminders() {
        WorkManagerHelper.scheduleFollowUpReminders(context)
    }

    fun refreshAll() {
        scheduleAll()
    }
}