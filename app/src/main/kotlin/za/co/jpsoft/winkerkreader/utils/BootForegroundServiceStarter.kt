package za.co.jpsoft.winkerkreader.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.utils.ServiceUtils

class BootForegroundServiceStarter : Service() {

    companion object {
        private const val TAG = "BootServiceStarter"
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "boot_starter_channel"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            if (BuildConfig.DEBUG) Log.d(TAG, "onCreate - Starting bridge service")

            // Create notification channel for Android O+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            }

            // Start as foreground service
            val notification = createForegroundNotification()
            startForeground(NOTIFICATION_ID, notification)

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onStartCommand - Starting/verifying monitoring services")
                // Log current running services
                ServiceUtils.logRunningServices(this)
            }

            // Start WhatsApp Notification Listener (if not already running)
            ServiceUtils.startServiceIfNotRunning(
                this,
                WhatsAppNotificationService::class.java
            )

            // Start Call Monitoring Service (if not already running)
            ServiceUtils.startServiceIfNotRunning(
                this,
                CallMonitoringService::class.java
            )

            // Start Keep Alive Service (if not already running)
            ServiceUtils.startServiceIfNotRunning(
                this,
                ServiceKeepAlive::class.java
            )

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "✅ Monitoring services started/verified")
                // Log services after starting
                ServiceUtils.logRunningServices(this)
            }

            // Stop this bridge service after a delay (only if we started it)
            Handler(Looper.getMainLooper()).postDelayed({
                stopSelf()
            }, 5000)

            return START_NOT_STICKY

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error in onStartCommand", e)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onDestroy - Bridge service destroyed")
            // Log remaining services
            ServiceUtils.logRunningServices(this)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Boot Starter Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Required to start monitoring services on boot"
                    setShowBadge(false)
                }

                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)

                if (BuildConfig.DEBUG) Log.d(TAG, "Notification channel created")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error creating notification channel", e)
            }
        }
    }

    private fun createForegroundNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Starting Services")
            .setContentText("Initializing notification and call monitoring")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}