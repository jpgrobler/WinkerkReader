package za.co.jpsoft.winkerkreader.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.repository.CongregationMemberGuidResolver
import za.co.jpsoft.winkerkreader.utils.PastoralNotificationHelper
import java.time.LocalDate
import java.time.ZoneId

class FollowUpReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "FollowUpReminderWorker started")

        val zoneId    = ZoneId.systemDefault()
        val nowUtc    = System.currentTimeMillis()
        val today     = LocalDate.now(zoneId)
        val startOfTodayUtc = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDayUtc     = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

        return try {
            val database    = PastoralDatabase.getInstance(applicationContext)
            val reminderDao = database.followUpReminderDao()
            val resolver    = CongregationMemberGuidResolver(applicationContext)

            // Single query: all PENDING reminders due today-or-earlier, not snoozed
            val dueReminders = reminderDao.getPendingDue(endOfDayUtc, nowUtc)
            Log.d(TAG, "Found ${dueReminders.size} due reminders")

            var notified = 0
            dueReminders.forEach { reminder ->
                val scheduleType = ScheduleType.fromStored(reminder.scheduleType)

                val shouldNotify = when (scheduleType) {
                    ScheduleType.DATE_ONLY -> {
                        // Only notify once per calendar day
                        val lastNotified = reminder.lastNotifiedDateUtc
                        lastNotified == null || lastNotified < startOfTodayUtc
                    }
                    ScheduleType.TIMED -> {
                        // Notify if the scheduled time is within the current day
                        // (worker fires at 7am — TIMED reminders for today appear here)
                        // lastNotifiedDateUtc guards against re-notification if worker
                        // retries or is re-scheduled
                        val lastNotified = reminder.lastNotifiedDateUtc
                        lastNotified == null || lastNotified < startOfTodayUtc
                    }
                }

                if (shouldNotify) {
                    val displayName = resolver.resolve(reminder.memberGuid)?.displayName
                        ?: reminder.memberDisplayNameCache.orEmpty()

                    PastoralNotificationHelper.postReminderNotification(
                        context          = applicationContext,
                        reminder         = reminder,
                        memberDisplayName = displayName
                    )

                    // Stamp lastNotifiedDateUtc so the worker doesn't re-notify today
                    reminderDao.update(
                        reminder.copy(
                            lastNotifiedDateUtc = startOfTodayUtc,
                            updatedAt           = nowUtc
                        )
                    )
                    notified++
                }
            }

            Log.i(TAG, "FollowUpReminderWorker complete — notified $notified reminders")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "FollowUpReminderWorker failed", e)
            // Retry once; WorkManager will back off automatically
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "follow_up_reminder_worker"
        private const val TAG = "FollowUpReminderWorker"
    }
}