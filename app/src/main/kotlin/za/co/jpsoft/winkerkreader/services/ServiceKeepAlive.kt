package za.co.jpsoft.winkerkreader.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import za.co.jpsoft.winkerkreader.BuildConfig

class ServiceKeepAlive : Service() {

    companion object {
        private const val TAG = "ServiceKeepAlive"
        private const val KEEP_ALIVE_NOTIFICATION_ID = 8888
        private const val CHANNEL_ID = "keep_alive_channel"
        private var isServiceRunning = false

        @JvmStatic
        fun isRunning(): Boolean = isServiceRunning

        fun start(context: Context) {
            try {
                val intent = Intent(context, ServiceKeepAlive::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "ServiceKeepAlive started")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error starting ServiceKeepAlive", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, ServiceKeepAlive::class.java)
                context.stopService(intent)
                if (BuildConfig.DEBUG) Log.d(TAG, "ServiceKeepAlive stopped")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error stopping ServiceKeepAlive", e)
            }
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            checkAndRestartListener()
            handler.postDelayed(this, 10000) // Check every 10 seconds
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            isServiceRunning = true
            if (BuildConfig.DEBUG) Log.d(TAG, "onCreate - Keep alive service started")

            createNotificationChannel()
            startForeground(KEEP_ALIVE_NOTIFICATION_ID, createKeepAliveNotification())

            // Start checking the listener
            handler.post(checkRunnable)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (BuildConfig.DEBUG) Log.d(TAG, "onDestroy - Keep alive service stopping")
        handler.removeCallbacks(checkRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkAndRestartListener() {
        try {
            // Use the public static method from WhatsAppNotificationService
            val isListenerRunning = WhatsAppNotificationService.isRunning()

            if (!isListenerRunning) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Listener service is not running, restarting...")

                // Restart the listener service
                try {
                    val intent = Intent(this, WhatsAppNotificationService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }

                    if (BuildConfig.DEBUG) Log.d(TAG, "Listener service restart initiated")

                    // Show restart notification
                    showRestartNotification()
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Error restarting listener service", e)
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error checking listener status", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Keep Alive Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Required to keep notification listener running"
                    setShowBadge(false)
                }

                val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)

                if (BuildConfig.DEBUG) Log.d(TAG, "Keep alive notification channel created")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error creating keep alive channel", e)
            }
        }
    }

    private fun createKeepAliveNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Service Running")
            .setContentText("Monitoring notifications")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun showRestartNotification() {
        try {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "restart_channel",
                    "Service Restart",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, "restart_channel")
                .setContentTitle("Service Restarted")
                .setContentText("Notification listener was restarted")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            notificationManager.notify(1111, notification)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error showing restart notification", e)
        }
    }
}