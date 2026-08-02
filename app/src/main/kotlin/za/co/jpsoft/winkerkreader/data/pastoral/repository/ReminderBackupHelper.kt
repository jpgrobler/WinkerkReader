package za.co.jpsoft.winkerkreader.data.pastoral.repository

import android.content.Context
import jakarta.inject.Inject
import za.co.jpsoft.winkerkreader.utils.db.PastoralDatabaseBackup
import za.co.jpsoft.winkerkreader.widget.PastoralWidgetProvider
import javax.inject.Singleton

/**
 * Encapsulates backup and widget refresh side-effects.
 */
@Singleton
class ReminderBackupHelper @Inject constructor(
    private val context: Context,
    private val pastoralDbBackup: PastoralDatabaseBackup   // <-- injected
) {
    fun requestBackupAndRefresh() {
        pastoralDbBackup.backupDebounced(context)   // <-- call instance method
        PastoralWidgetProvider.refreshWidgets(context)
    }
}