package za.co.jpsoft.winkerkreader.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import za.co.jpsoft.winkerkreader.utils.PastoralNotificationHelper
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class PastoralReminderActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: PastoralReminderRepository

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: run {
            if (BuildConfig.DEBUG) Log.w(TAG, "onReceive: missing reminderId, ignoring")
            return
        }
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> {
                        repository.completeReminder(reminderId)
                        if (BuildConfig.DEBUG) Log.d(
                            TAG,
                            "Reminder $reminderId marked complete via notification"
                        )
                    }

                    ACTION_SNOOZE_1_DAY -> {
                        val until = LocalDateTime.now().plusDays(1)
                            .withHour(8).withMinute(0).withSecond(0).withNano(0)
                        repository.snoozeReminder(reminderId, until)
                        if (BuildConfig.DEBUG) Log.d(
                            TAG,
                            "Reminder $reminderId snoozed to $until via notification"
                        )
                    }

                    else -> Log.w(TAG, "Unknown action: ${intent.action}")
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(
                    TAG,
                    "Error handling reminder action for $reminderId",
                    e
                )
            } finally {
                if (notifId != -1) {
                    PastoralNotificationHelper.cancelNotification(context, reminderId)
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PastoralActionReceiver"

        const val ACTION_COMPLETE = "za.co.jpsoft.winkerkreader.ACTION_PASTORAL_COMPLETE"
        const val ACTION_SNOOZE_1_DAY = "za.co.jpsoft.winkerkreader.ACTION_PASTORAL_SNOOZE"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}