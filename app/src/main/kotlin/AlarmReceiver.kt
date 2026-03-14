// AlarmReceiver.kt
package za.co.jpsoft.winkerkreader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val whenTime = System.currentTimeMillis()
        val channelId = "wkr-01"
        val channelName = "WinkerkReader"
        val importance = NotificationManager.IMPORTANCE_DEFAULT

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, importance)
            notificationManager.createNotificationChannel(channel)
        }

        val action = intent.action
        if (!action.isNullOrEmpty()) {
            when (action) {
                "VerjaarSMS" -> {
                    val notificationIntent = Intent(context, VerjaarSMS2::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context, 0, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val now = Date()
                    val id = SimpleDateFormat("ddHHmmss", Locale.US).format(now).toInt()

                    val builder = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.mipmap.ic_stat_family_roof)
                        .setContentTitle("WinkerkReader")
                        .setContentText("Stuur verjaarsdag SMS'e")
                        .setSound(alarmSound)
                        .setAutoCancel(true)
                        .setWhen(whenTime)
                        .setColor(39372)
                        .setContentIntent(pendingIntent)

                    notificationManager.notify(id, builder.build())
                }
                "DropBoxDownLoad" -> {
                    val notificationIntent = Intent(context, Laaidatabasis::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context, 0, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val now = Date()
                    val id = SimpleDateFormat("ddHHmmss", Locale.US).format(now).toInt()

                    val builder = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.mipmap.ic_stat_family_roof)
                        .setContentTitle("WinkerkReader")
                        .setContentText("Laai data van DropBox")
                        .setSound(alarmSound)
                        .setAutoCancel(true)
                        .setWhen(whenTime)
                        .setColor(39372)
                        .setContentIntent(pendingIntent)

                    notificationManager.notify(id, builder.build())
                }
            }
        }
    }
}