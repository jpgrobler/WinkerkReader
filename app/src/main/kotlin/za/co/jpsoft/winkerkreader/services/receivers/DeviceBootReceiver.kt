package za.co.jpsoft.winkerkreader.services.receivers

import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.services.CallMonitoringService

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * Combined boot receiver that handles device startup, package replacement,
 * and sets up necessary services and alarms.
 */
class DeviceBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DeviceBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: run {
            Log.w(TAG, "Received intent with null action")
            return
        }

        Log.d(TAG, "Boot receiver triggered with action: $action")

        // Handle different boot/restart scenarios
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {

                val settings = SettingsManager.getInstance(context)

                // Start monitoring services if enabled
                startMonitoringServiceIfEnabled(context, settings)

                // Setup birthday reminder alarm if enabled
                setupBirthdayAlarmIfEnabled(context, settings)

                // Start main activity only on actual boot events (optional, can be removed)
                // if (action == Intent.ACTION_BOOT_COMPLETED ||
                //     action == "android.intent.action.QUICKBOOT_POWERON"
                // ) {
                //     startMainActivity(context)
                // }
            }
        }
    }

    @Suppress("unused")
    private fun startMainActivity(context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Main activity started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start main activity", e)
        }
    }

    private fun startMonitoringServiceIfEnabled(context: Context, settings: SettingsManager) {
        val autoStartEnabled = settings.autoStartEnabled
        val callMonitorEnabled = settings.callMonitorEnabled

        if (autoStartEnabled || callMonitorEnabled) {
            // Start CallMonitoringService
            try {
                val intent = Intent(context, CallMonitoringService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "CallMonitoringService started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CallMonitoringService", e)
            }

            // Start IncomingCall service
            try {
                val intent = Intent(context, IncomingCall::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "IncomingCall service started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start IncomingCall service", e)
            }
        }
    }

    private fun setupBirthdayAlarmIfEnabled(context: Context, settings: SettingsManager) {
        val reminderEnabled = settings.herinner
        val timeUpdate = settings.smsTimeUpdate

        if (!reminderEnabled && !timeUpdate) {
            Log.d(TAG, "Birthday reminder disabled")
            return
        }

        try {
            val hour = settings.smsHour
            val minute = settings.smsMinute

            val alarmTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour.toIntOrNull() ?: 8)
                set(Calendar.MINUTE, minute.toIntOrNull() ?: 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val now = Calendar.getInstance()

            // Clear the time update flag
            settings.smsTimeUpdate = false
            settings.fromMenu = false

            // Create alarm intent
            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "VerjaarSMS"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                alarmIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager is null")
                return
            }

            // Cancel any existing alarm
            alarmManager.cancel(pendingIntent)

            // Calculate trigger time
            var triggerTime = alarmTime.timeInMillis
            if (triggerTime <= now.timeInMillis) {
                triggerTime += AlarmManager.INTERVAL_DAY
            }

            // Schedule the alarm based on Android version
            scheduleAlarm(alarmManager, triggerTime, pendingIntent)

            Log.d(TAG, "Birthday reminder alarm scheduled for ${alarmTime.time}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup birthday alarm", e)
        }
    }

    private fun scheduleAlarm(alarmManager: AlarmManager, triggerTime: Long, pendingIntent: PendingIntent) {
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    // Android 12+ - Check if we can schedule exact alarms
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                        Log.d(TAG, "Exact alarm scheduled for Android 12+")
                    } else {
                        // Fallback to inexact alarm
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                        Log.w(TAG, "Using inexact alarm - exact alarm permission not granted")
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    // Android 6+ - Use setExactAndAllowWhileIdle
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    Log.d(TAG, "Exact alarm scheduled for Android 6+")
                }
                else -> {
                    // Below Marshmallow, use setExact
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    Log.d(TAG, "Exact alarm scheduled for older Android")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm - permission may be missing", e)
            // Try inexact alarm as fallback
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.w(TAG, "Fallback to inexact alarm due to security exception")
            } catch (fallbackException: Exception) {
                Log.e(TAG, "Failed to schedule fallback alarm", fallbackException)
            }
        }
    }
}