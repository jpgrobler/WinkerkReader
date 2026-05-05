package za.co.jpsoft.winkerkreader.services

import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import za.co.jpsoft.winkerkreader.utils.PhoneCallMonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import za.co.jpsoft.winkerkreader.data.DatabaseHelper
import za.co.jpsoft.winkerkreader.data.WinkerkContract.KEY_SELECTED_CALENDAR_ID
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO

class CallMonitoringService : Service() {

    private var phoneCallMonitor: PhoneCallMonitor? = null
    private var databaseHelper: DatabaseHelper? = null
    private var pendingIncomingNumber: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Call Monitoring Service created")
        isRunning = true
        createNotificationChannel()
        databaseHelper = DatabaseHelper.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Call Monitoring Service started")
        startForeground(NOTIFICATION_ID, createNotification())

        if (intent != null && intent.hasExtra("incoming_number")) {
            val number = intent.getStringExtra("incoming_number")
            if (number != null) {
                Log.d(TAG, "Received incoming number: $number")
                pendingIncomingNumber = number
                phoneCallMonitor?.setIncomingNumber(pendingIncomingNumber)
                pendingIncomingNumber = null
            }
        }

        if (hasRequiredPermissions()) {
            if (phoneCallMonitor == null) {
                startCallMonitoring()
            } else {
                Log.d(TAG, "Call monitoring already running")
            }
        } else {
            Log.w(TAG, "Missing required permissions, cannot start monitoring")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Call Monitoring Service destroyed")
        isRunning = false
        phoneCallMonitor?.stopMonitoring()
        phoneCallMonitor = null
        databaseHelper?.close()
        databaseHelper = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors incoming calls in the background"
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
            .setContentTitle("Call Logger Active")
            .setContentText("Monitoring Phone & Voip calls")
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
                Log.w(TAG, "Missing required permissions for call monitoring")
                return
            }

            val calendarManager = CalendarManager(this)
            val userPrefs = getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
            var calendarId = userPrefs.getLong(KEY_SELECTED_CALENDAR_ID, DEFAULT_CALENDAR_ID)

            // Simple validation: use default if invalid (e.g., -1)
            if (calendarId < 0) {
                Log.w(TAG, "Invalid calendar ID: $calendarId, using default")
                calendarId = DEFAULT_CALENDAR_ID
            }

            if (databaseHelper == null) {
                databaseHelper = DatabaseHelper.getInstance(this)
            }

            phoneCallMonitor = PhoneCallMonitor(this, databaseHelper!!, calendarManager, calendarId)

            if (pendingIncomingNumber != null) {
                phoneCallMonitor?.setIncomingNumber(pendingIncomingNumber)
                pendingIncomingNumber = null
            }

            phoneCallMonitor?.startMonitoring()
            Log.d(TAG, "Phone call monitoring started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting call monitoring", e)
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "CallMonitoringService"
        private const val CHANNEL_ID = "call_monitoring_channel"
        private const val NOTIFICATION_ID = 1
        private const val DEFAULT_CALENDAR_ID = -1L

        @Volatile
        var isRunning = false
            private set

        @JvmStatic
        fun isServiceRunning(): Boolean = isRunning
    }
}