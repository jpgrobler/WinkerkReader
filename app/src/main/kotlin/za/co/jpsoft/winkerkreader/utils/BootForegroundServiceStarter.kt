// BootForegroundServiceStarter.kt
package za.co.jpsoft.winkerkreader.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.utils.ForegroundServiceHelper
import za.co.jpsoft.winkerkreader.utils.ForegroundServiceType
import za.co.jpsoft.winkerkreader.utils.SettingsManager

/**
 * Bridge service that starts restricted foreground services after boot.
 * This avoids Android 15's restriction on starting restricted foreground services
 * directly from BOOT_COMPLETED receivers.
 */
class BootForegroundServiceStarter : Service() {

    companion object {
        private const val TAG = "BootServiceStarter"
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "boot_starter_channel"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Log.d(TAG, "BootForegroundServiceStarter created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (BuildConfig.DEBUG) Log.d(TAG, "BootForegroundServiceStarter started")

        // Start as foreground with a non-restricted type
        startAsForeground()

        // Wait a moment then start the actual services
        serviceScope.launch {
            delay(2000) // Small delay to ensure system is ready
            startMonitoringServices()
            stopSelf() // Stop this bridge service after starting the actual services
        }

        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        val notification = createNotification()
        ForegroundServiceHelper.startForeground(
            service = this,
            id = NOTIFICATION_ID,
            notification = notification,
            type = ForegroundServiceType.NONE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Boot Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Starting services after boot"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Starting services...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(false)
            .build()
    }

    private fun startMonitoringServices() {
        val settings = SettingsManager.getInstance(this)

        // Only start if auto-start is enabled
        if (!settings.autoStartEnabled) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Auto-start disabled, not starting services")
            return
        }

        // Check permissions before starting
        if (!hasRequiredPermissions()) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Missing required permissions, cannot start monitoring services")
            }
            return
        }

        // Start CallMonitoringService (restricted type - phone)
        try {
            if (BuildConfig.DEBUG) Log.d(TAG, "Starting CallMonitoringService")
            val callIntent = Intent(this, CallMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(callIntent)
            } else {
                startService(callIntent)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to start CallMonitoringService", e)
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Monitoring services started")
    }

    private fun hasRequiredPermissions(): Boolean {
        val requiredPermissions = arrayOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CALL_LOG
        )
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (BuildConfig.DEBUG) Log.d(TAG, "BootForegroundServiceStarter destroyed")
    }
}