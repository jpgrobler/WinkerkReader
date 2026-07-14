package za.co.jpsoft.winkerkreader.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import za.co.jpsoft.winkerkreader.BuildConfig
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
            if (BuildConfig.DEBUG) Log.d(TAG, "onCreate - Service created, isRunning=true")

            // Create notification channel for Android O+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            }

            // Start as foreground service to prevent being killed
            startForeground(NOTIFICATION_ID, createForegroundNotification())

            if (BuildConfig.DEBUG) Log.d(TAG, "onCreate - Service started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            isServiceRunning = false
            stopSelf()
        }
    }

    override fun onListenerConnected() {
        try {
            super.onListenerConnected()
            if (BuildConfig.DEBUG) Log.d(TAG, "onListenerConnected - Successfully connected")

            // Verify we have permission to listen to notifications
            if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
                Log.w(TAG, "Notification listener permission not granted by user")
                // Request permission - this will open settings
                requestPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onListenerConnected", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) {
                Log.w(TAG, "onNotificationPosted - null notification received")
                return
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onNotificationPosted - Package: ${sbn.packageName}, ID: ${sbn.id}")
            }

            // Check if this is a WhatsApp notification
            if (sbn.packageName?.contains("whatsapp", ignoreCase = true) == true) {
                processWhatsAppNotification(sbn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNotificationPosted", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) {
                Log.w(TAG, "onNotificationRemoved - null notification received")
                return
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onNotificationRemoved - Package: ${sbn.packageName}, ID: ${sbn.id}")
            }

            // Handle notification removal if needed
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNotificationRemoved", e)
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            isServiceRunning = false

            if (BuildConfig.DEBUG) Log.d(TAG, "onDestroy - Service destroyed, isRunning=false")

            // Don't stop foreground service immediately - let it finish properly
            Handler(Looper.getMainLooper()).postDelayed({
                stopForeground(STOP_FOREGROUND_REMOVE)
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
            isServiceRunning = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return try {
            super.onBind(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onBind", e)
            null
        }
    }

    private fun processWhatsAppNotification(sbn: StatusBarNotification) {
        try {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE, "")
            val text = extras.getString(Notification.EXTRA_TEXT, "")

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "WhatsApp Notification - Title: $title")
                Log.d(TAG, "WhatsApp Notification - Text: $text")
            }

            // Here you can process the WhatsApp notification
            // Example: Extract sender name, message content, etc.

        } catch (e: Exception) {
            Log.e(TAG, "Error processing WhatsApp notification", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    }

    private fun createForegroundNotification(): Notification {
        // Create an intent to open the app when notification is tapped
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun requestPermission() {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening notification settings", e)
            // Fallback to app settings
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.parse("package:$packageName")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Error opening app settings", e2)
            }
        }
    }
}