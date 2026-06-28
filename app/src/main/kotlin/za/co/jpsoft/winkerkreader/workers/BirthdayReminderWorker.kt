package za.co.jpsoft.winkerkreader.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BirthdayReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "birthday_reminder_work"
    }

    override suspend fun doWork(): Result {
        return try {
            sendBirthdayReminders()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendBirthdayReminders() {
        // TODO: Move your birthday SMS logic here from AlarmReceiver for "VerjaarSMS"
    }
}