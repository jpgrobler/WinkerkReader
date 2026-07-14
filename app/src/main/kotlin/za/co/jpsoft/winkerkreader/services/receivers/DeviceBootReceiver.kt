// DeviceBootReceiver.kt - Modified version
package za.co.jpsoft.winkerkreader.services.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.services.BootForegroundServiceStarter
import za.co.jpsoft.winkerkreader.utils.SettingsManager
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
            if (BuildConfig.DEBUG) Log.w(TAG, "Received intent with null action")
            return
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Boot receiver triggered with action: $action")

        // Handle different boot/restart scenarios
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {

                val settings = SettingsManager.getInstance(context)

                // ✅ FIXED: Start monitoring services through bridge service to avoid Android 15 restriction
                startMonitoringServiceIfEnabled(context, settings)

                // Setup birthday reminder alarm if enabled
                setupBirthdayAlarmIfEnabled(context, settings)
            }
        }
    }

    private fun startMonitoringServiceIfEnabled(context: Context, settings: SettingsManager) {
        val autoStartEnabled = settings.autoStartEnabled

        if (autoStartEnabled) {
            try {
                // ✅ Use the bridge service instead of starting CallMonitoringService directly
                // This avoids the Android 15 restriction on starting restricted foreground services
                // from BOOT_COMPLETED receivers
                val intent = Intent(context, BootForegroundServiceStarter::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "BootForegroundServiceStarter started successfully"
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to start BootForegroundServiceStarter", e)
            }
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "Auto-start disabled, not starting services")
        }
    }

    private fun setupBirthdayAlarmIfEnabled(context: Context, settings: SettingsManager) {
        val reminderEnabled = settings.herinner
        val timeUpdate = settings.smsTimeUpdate

        if (!reminderEnabled && !timeUpdate) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Birthday reminder disabled")
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
                if (BuildConfig.DEBUG) Log.e(TAG, "AlarmManager is null")
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

            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Birthday reminder alarm scheduled for ${alarmTime.time}"
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to setup birthday alarm", e)
        }
    }

    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        triggerTime: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    // Android 12+ - Check if we can schedule exact alarms
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        if (BuildConfig.DEBUG) Log.d(TAG, "Exact alarm scheduled for Android 12+")
                    } else {
                        // Fallback to inexact alarm
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        if (BuildConfig.DEBUG) Log.w(
                            TAG,
                            "Using inexact alarm - exact alarm permission not granted"
                        )
                    }
                }

                else -> {
                    // Android 6+ (but since minSdk is 26, this is always true)
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    if (BuildConfig.DEBUG) Log.d(TAG, "Exact alarm scheduled")
                }
            }
        } catch (e: SecurityException) {
            if (BuildConfig.DEBUG) Log.e(
                TAG,
                "SecurityException scheduling alarm - permission may be missing",
                e
            )
            // Try inexact alarm as fallback
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                if (BuildConfig.DEBUG) Log.w(
                    TAG,
                    "Fallback to inexact alarm due to security exception"
                )
            } catch (fallbackException: Exception) {
                if (BuildConfig.DEBUG) Log.e(
                    TAG,
                    "Failed to schedule fallback alarm",
                    fallbackException
                )
            }
        }
    }
}