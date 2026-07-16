package za.co.jpsoft.winkerkreader.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity

class WhatsAppNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "WhatsAppNotifService"
        private const val NOTIFICATION_CHANNEL_ID = "whatsapp_listener_channel"
        private const val NOTIFICATION_ID = 9999
        private var isServiceRunning = false

        @JvmStatic
        fun isRunning(): Boolean = isServiceRunning
    }

    override fun onCreate() {
        try {
            super.onCreate()
            isServiceRunning = true
            if (BuildConfig.DEBUG) Log.d(TAG, "onCreate")

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createForegroundNotification())

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            isServiceRunning = false
            stopSelf()
        }
    }

    override fun onListenerConnected() {
        try {
            super.onListenerConnected()
            if (BuildConfig.DEBUG) Log.d(TAG, "onListenerConnected")

            if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
                Log.w(TAG, "Notification listener permission not granted")
                requestPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onListenerConnected", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (BuildConfig.DEBUG) Log.d(TAG, "onListenerDisconnected")
        // Don't stop — framework will reconnect
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onNotificationPosted — package: ${sbn.packageName}, id: ${sbn.id}")
            }

            if (sbn.packageName?.contains("whatsapp", ignoreCase = true) == true) {
                processWhatsAppNotification(sbn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNotificationPosted", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onNotificationRemoved — package: ${sbn.packageName}, id: ${sbn.id}")
            }
        } catch (e: Exception) {
            // DeadObjectException is expected during shutdown — swallow silently
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onNotificationRemoved — exception during shutdown: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        isServiceRunning = false

        // FIX: call stopForeground directly — the previous Handler.postDelayed captured `this`
        // in its lambda, keeping the destroyed service instance alive for an extra second and
        // making LeakCanary's transient framework leak appear worse than it is.
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "stopForeground in onDestroy failed: ${e.message}")
        }

        super.onDestroy()
        if (BuildConfig.DEBUG) Log.d(TAG, "onDestroy")
    }

    // FIX: do not override onBind on a NotificationListenerService.
    // The framework depends on the IBinder returned by super.onBind() to manage the
    // NotificationListenerWrapper binding. Wrapping it in a try/catch that can return null
    // risks interfering with the system's ability to cleanly unbind (which worsens the leak).
    // Removing the override lets the framework handle binding correctly.

    private fun processWhatsAppNotification(sbn: StatusBarNotification) {
        try {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE, "")
            val text = extras.getString(Notification.EXTRA_TEXT, "")

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "WhatsApp notification — title: $title, text: $text")
            }

            // Process the WhatsApp notification here as needed

        } catch (e: Exception) {
            Log.e(TAG, "Error processing WhatsApp notification", e)
        }
    }

    private fun createNotificationChannel() {
        try {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "WhatsApp Listener Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Required to keep notification listener service running"
                setShowBadge(false)
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            if (BuildConfig.DEBUG) Log.d(TAG, "Notification channel created")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification channel", e)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("WhatsApp Listener Active")
            .setContentText("Monitoring WhatsApp notifications")
            .setSmallIcon(R.drawable.img)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun requestPermission() {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening notification settings", e)
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .apply {
                        data = android.net.Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Error opening app settings", e2)
            }
        }
    }
}