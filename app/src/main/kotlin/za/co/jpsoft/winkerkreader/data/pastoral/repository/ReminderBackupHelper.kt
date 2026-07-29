package za.co.jpsoft.winkerkreader.data.pastoral.repository

import android.content.Context
import za.co.jpsoft.winkerkreader.utils.PastoralDatabaseBackup
import za.co.jpsoft.winkerkreader.widget.PastoralWidgetProvider

/**
 * Encapsulates backup and widget refresh side-effects.
 */
class ReminderBackupHelper(
    private val context: Context
) {
    fun requestBackupAndRefresh() {
        PastoralDatabaseBackup.backupDebounced(context)
        PastoralWidgetProvider.refreshWidgets(context)
    }
}