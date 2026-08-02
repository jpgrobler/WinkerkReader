package za.co.jpsoft.winkerkreader.services

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.calllog.dao.CallLogDao
import za.co.jpsoft.winkerkreader.data.calllog.setup.CallLogDatabase
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.utils.telephony.PhoneCallMonitor
import za.co.jpsoft.winkerkreader.utils.telephony.UnifiedCallMonitor
import za.co.jpsoft.winkerkreader.utils.work.ForegroundServiceHelper
import za.co.jpsoft.winkerkreader.utils.work.ForegroundServiceType

@AndroidEntryPoint
class CallMonitoringService : Service() {

    @Inject
    lateinit var unifiedMonitor: UnifiedCallMonitor

    private var phoneCallMonitor: PhoneCallMonitor? = null
    private var callLogDao: CallLogDao? = null
    private var pendingIncomingNumber: String? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        if (BuildConfig.DEBUG) Log.d(TAG, "Call Monitoring Service created")
        createNotificationChannel()
        callLogDao = CallLogDatabase.getInstance(this).callLogDao()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (BuildConfig.DEBUG) Log.d(TAG, "Call Monitoring Service destroyed")
        phoneCallMonitor?.stopMonitoring()
        phoneCallMonitor = null
        callLogDao = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (BuildConfig.DEBUG) Log.d(TAG, "Call Monitoring Service started")
        if (hasRequiredPermissions()) {
            if (phoneCallMonitor == null) {
                startCallMonitoring()
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Call monitoring already running")
            }
        } else {
            if (BuildConfig.DEBUG) Log.w(
                TAG,
                "Missing required permissions, cannot start monitoring"
            )
            stopSelf()
        }

        val notification = createNotification()
        ForegroundServiceHelper.startForeground(
            service = this,
            id = NOTIFICATION_ID,
            notification = notification,
            type = ForegroundServiceType.PHONE_CALL
        )

        if (intent != null && intent.hasExtra("incoming_number")) {
            val number = intent.getStringExtra("incoming_number")
            if (number != null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Received incoming number: $number")
                if (phoneCallMonitor != null) {
                    phoneCallMonitor?.setIncomingNumber(number)
                } else {
                    pendingIncomingNumber = number
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.call_monitoring_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.call_monitoring_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.call_logger_active))
            .setContentText(getString(R.string.monitoring_calls))
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
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

    private fun startCallMonitoring() {
        try {
            if (!hasRequiredPermissions()) {
                if (BuildConfig.DEBUG) Log.w(
                    TAG,
                    "Missing required permissions for call monitoring"
                )
                return
            }

            if (callLogDao == null) {
                callLogDao = CallLogDatabase.getInstance(this).callLogDao()
            }

            // Create PhoneCallMonitor with the injected UnifiedCallMonitor
            phoneCallMonitor = PhoneCallMonitor(this, unifiedMonitor)

            if (pendingIncomingNumber != null) {
                phoneCallMonitor?.setIncomingNumber(pendingIncomingNumber)
                pendingIncomingNumber = null
            }

            phoneCallMonitor?.startMonitoring()
            if (BuildConfig.DEBUG) Log.d(TAG, "Phone call monitoring started successfully")

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error starting call monitoring", e)
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "CallMonitoringService"
        private const val CHANNEL_ID = "call_monitoring_channel"
        private const val NOTIFICATION_ID = 1
        private const val NO_CALENDAR_ID = -1L

        private var isServiceRunning = false

        @JvmStatic
        fun isRunning(): Boolean = isServiceRunning

        fun isServiceRunning(context: Context): Boolean {
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(ActivityManager::class.java)
            } else {
                context.getSystemService(ACTIVITY_SERVICE) as? ActivityManager
            }
            if (manager == null) return false
            val serviceName =
                "${context.packageName}.${CallMonitoringService::class.java.simpleName}"
            return try {
                manager.getRunningServices(Int.MAX_VALUE)
                    .any { it.service.className == serviceName }
            } catch (e: Exception) {
                false
            }
        }
    }
}