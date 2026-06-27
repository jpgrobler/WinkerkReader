package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import za.co.jpsoft.winkerkreader.widget.PastoralWidgetProvider

/**
 * Centralised scheduling for all background tasks (WorkManager).
 * All alarm/work scheduling logic from MainActivity is moved here.
 */
class WorkScheduler(
    private val context: Context,
    private val settingsManager: SettingsManager
) {

    /**
     * Schedule all background tasks based on current settings.
     * Called once during app startup.
     */
    fun scheduleAll() {
        scheduleAutoDownload()
        scheduleReminder()
        scheduleWidgetRefresh()
        scheduleFollowUpReminders()
    }

    fun scheduleAutoDownload() {
        if (settingsManager.autoDl || settingsManager.dlTimeUpdate) {
            val hour = settingsManager.dlHour.toInt()
            val minute = settingsManager.dlMinute.toInt()
            val day = settingsManager.dlDay

            WorkManagerHelper.scheduleDropboxDownload(context, hour, minute, day)
            // Reset flags after scheduling
            settingsManager.dlTimeUpdate = false
            settingsManager.fromMenu = false
        }
    }

    fun scheduleReminder() {
        if (settingsManager.herinner || settingsManager.smsTimeUpdate) {
            val hour = settingsManager.smsHour.toInt()
            val minute = settingsManager.smsMinute.toInt()

            WorkManagerHelper.scheduleBirthdayReminder(context, hour, minute)
            settingsManager.smsTimeUpdate = false
            settingsManager.fromMenu = false
        }
    }

    fun scheduleWidgetRefresh() {
        WorkManagerHelper.scheduleWidgetRefresh(context)
        PastoralWidgetProvider.refreshWidgets(context)
    }

    fun scheduleFollowUpReminders() {
        WorkManagerHelper.scheduleFollowUpReminders(context)
    }

    /**
     * Call this when settings change that affect scheduling (e.g., user changes time).
     * It will reschedule all tasks.
     */
    fun refreshAll() {
        scheduleAll()
    }
}