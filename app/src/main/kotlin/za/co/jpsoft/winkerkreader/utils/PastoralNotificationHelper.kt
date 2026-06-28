package za.co.jpsoft.winkerkreader.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.receivers.PastoralReminderActionReceiver
import za.co.jpsoft.winkerkreader.ui.activities.BedieningActivity
import za.co.jpsoft.winkerkreader.utils.Utils.toLocalDateSafe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object PastoralNotificationHelper {

    const val CHANNEL_ID = "pastoral_bediening"
    private const val TAG = "PastoralNotifHelper"

    // -------------------------------------------------------------------------
    // Channel setup — call from MainActivity.setupPermissions()
    // -------------------------------------------------------------------------

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.bediening_title),   // "Bediening"
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.bediening_channel_description)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    // -------------------------------------------------------------------------
    // Post a notification for a single reminder
    // -------------------------------------------------------------------------

    fun postReminderNotification(
        context: Context,
        reminder: FollowUpReminderEntity,
        memberDisplayName: String
    ) {
        val notifId = notificationId(reminder.reminderId)

        val contentText = buildContentText(context, reminder, memberDisplayName)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pastoral)
            .setContentTitle(reminder.title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setAutoCancel(false)   // stays until actioned — pastor must consciously dismiss
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent(context, reminder.reminderId, notifId))
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.herinnering_voltooi),   // "Voltooi"
                completePendingIntent(context, reminder.reminderId, notifId)
            )
            .addAction(
                R.drawable.ic_snooze,
                context.getString(R.string.herinnering_uitstel),   // "Uitstel 1 dag"
                snoozePendingIntent(context, reminder.reminderId, notifId)
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
            if (BuildConfig.DEBUG) Log.d(TAG, "Posted notification $notifId for reminder ${reminder.reminderId}")
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted (Android 13+)
            if (BuildConfig.DEBUG) Log.w(TAG, "POST_NOTIFICATIONS permission missing — notification not shown", e)
        }
    }

    fun cancelNotification(context: Context, reminderId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    // -------------------------------------------------------------------------
    // Stable notification ID
    // -------------------------------------------------------------------------

    fun notificationId(reminderId: String): Int = abs(reminderId.hashCode())

    // -------------------------------------------------------------------------
    // PendingIntents
    // -------------------------------------------------------------------------

    /** Tapping the notification body opens BedieningActivity. */
    private fun openPendingIntent(context: Context, reminderId: String, notifId: Int): PendingIntent {
        val intent = Intent(context, BedieningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(PastoralReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(PastoralReminderActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        return PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** "Voltooi" action button. */
    private fun completePendingIntent(context: Context, reminderId: String, notifId: Int): PendingIntent {
        val intent = Intent(context, PastoralReminderActionReceiver::class.java).apply {
            action = PastoralReminderActionReceiver.ACTION_COMPLETE
            putExtra(PastoralReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(PastoralReminderActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        return PendingIntent.getBroadcast(
            context,
            // Request code must be unique per (reminder × action) to avoid PendingIntent recycling
            notifId + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** "Uitstel 1 dag" action button. */
    private fun snoozePendingIntent(context: Context, reminderId: String, notifId: Int): PendingIntent {
        val intent = Intent(context, PastoralReminderActionReceiver::class.java).apply {
            action = PastoralReminderActionReceiver.ACTION_SNOOZE_1_DAY
            putExtra(PastoralReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(PastoralReminderActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        return PendingIntent.getBroadcast(
            context,
            notifId + 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // -------------------------------------------------------------------------
    // Content text helpers
    // -------------------------------------------------------------------------

    private fun buildContentText(
        context: Context,
        reminder: FollowUpReminderEntity,
        memberDisplayName: String
    ): String {
        val scheduleType = ScheduleType.fromStored(reminder.scheduleType)
        return when {
            // Overdue
            reminder.dueDateUtc < todayStartMillis() ->
                "$memberDisplayName · ${formatOverdueLabel(context, reminder.dueDateUtc)}"
            // Timed — show scheduled time
            scheduleType == ScheduleType.TIMED ->
                "$memberDisplayName · ${formatTime(reminder.dueDateUtc)}"
            // Date-only due today
            else ->
                memberDisplayName
        }
    }

    private fun formatOverdueLabel(context: Context, dueDateUtc: Long): String {
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        val date = dueDateUtc.toLocalDateSafe() ?: LocalDate.now()
        return context.getString(R.string.bediening_agterstallig) + " (${date.format(formatter)})"
    }

    private fun formatTime(dueDateUtc: Long): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        return Instant.ofEpochMilli(dueDateUtc)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(formatter)
    }

    private fun todayStartMillis(): Long {
        val today = LocalDate.now(ZoneId.systemDefault())
        return today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}