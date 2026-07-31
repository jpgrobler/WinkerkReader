package za.co.jpsoft.winkerkreader.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import za.co.jpsoft.winkerkreader.services.receivers.AlarmReceiver
import java.util.Calendar

/**
 * Schedules (and cancels) the daily repeating alarm for automatic birthday SMS sending.
 *
 * Extracted from VerjaarSmsActivity.setupAlarm(). The Activity now just calls:
 *
 *   BirthdayAlarmScheduler.schedule(this, selectedHour, selectedMinute)
 */
object BirthdayAlarmScheduler {

    private const val ACTION = "VerjaarSMS"
    private const val REQUEST_CODE = 0

    /**
     * Sets a daily repeating alarm that fires at [hour]:[minute].
     * If the time has already passed today, the first trigger is set to tomorrow.
     *
     * Was [VerjaarSmsActivity.setupAlarm].
     */
    fun schedule(context: Context, hour: Int, minute: Int) {
        val triggerTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            AlarmManager.INTERVAL_DAY,
            buildPendingIntent(context)
        )
    }

    /**
     * Cancels the repeating alarm if one is set.
     */
    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}