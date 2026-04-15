package za.co.jpsoft.winkerkreader.workers

import za.co.jpsoft.winkerkreader.services.receivers.AlarmReceiver

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

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