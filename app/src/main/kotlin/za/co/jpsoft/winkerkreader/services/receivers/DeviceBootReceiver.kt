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
import za.co.jpsoft.winkerkreader.utils.ServiceUtils
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import java.util.Calendar

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

        try {
            when (action) {
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.QUICKBOOT_POWERON" -> {

                    if (BuildConfig.DEBUG) Log.d(TAG, "Boot/restart received")

                    val settings = SettingsManager.getInstance(context) ?: return

                    if (settings.autoStartEnabled) {
                        // ✅ SINGLE ENTRY POINT: the bridge starts everything
                        ServiceUtils.startServiceIfNotRunning(
                            context,
                            BootForegroundServiceStarter::class.java
                        )

                        if (BuildConfig.DEBUG) Log.d(
                            TAG,
                            "Bridge service started – it will handle all service starts"
                        )
                    } else {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Auto‑start disabled, skipping")
                    }

                    // Birthday alarm setup remains unchanged
                    setupBirthdayAlarmIfEnabled(context, settings)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in DeviceBootReceiver", e)
        }
    }

    // ---------- Birthday alarm (unchanged) ----------

    private fun setupBirthdayAlarmIfEnabled(context: Context, settings: SettingsManager) {
        try {
            val reminderEnabled = try {
                settings.herinner
            } catch (e: Exception) {
                Log.e(TAG, "Error reading herinner", e)
                false
            }

            val timeUpdate = try {
                settings.smsTimeUpdate
            } catch (e: Exception) {
                Log.e(TAG, "Error reading smsTimeUpdate", e)
                false
            }

            if (!reminderEnabled && !timeUpdate) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Birthday reminder disabled")
                return
            }

            val hour = try {
                settings.smsHour?.toIntOrNull() ?: 8
            } catch (e: Exception) {
                Log.e(TAG, "Error reading smsHour", e)
                8
            }

            val minute = try {
                settings.smsMinute?.toIntOrNull() ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "Error reading smsMinute", e)
                0
            }

            val alarmTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val now = Calendar.getInstance()

            try {
                settings.smsTimeUpdate = false
                settings.fromMenu = false
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing settings flags", e)
            }

            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "VerjaarSMS"
                putExtra("alarm_time", System.currentTimeMillis())
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                alarmIntent,
                flags
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager is null")
                return
            }

            try {
                alarmManager.cancel(pendingIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling existing alarm", e)
            }

            var triggerTime = alarmTime.timeInMillis
            if (triggerTime <= now.timeInMillis) {
                triggerTime += AlarmManager.INTERVAL_DAY
            }

            scheduleAlarm(alarmManager, triggerTime, pendingIntent)

            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Birthday reminder alarm scheduled for ${alarmTime.time}"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup birthday alarm", e)
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
                    try {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerTime,
                                pendingIntent
                            )
                            if (BuildConfig.DEBUG) Log.d(
                                TAG,
                                "Exact alarm scheduled for Android 12+"
                            )
                        } else {
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
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException scheduling exact alarm", e)
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        Log.w(TAG, "Fallback to inexact alarm due to security exception")
                    }
                }

                else -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    if (BuildConfig.DEBUG) Log.d(TAG, "Exact alarm scheduled")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.w(TAG, "Fallback to basic alarm scheduling")
            } catch (fallbackException: Exception) {
                Log.e(TAG, "All alarm scheduling methods failed", fallbackException)
            }
        }
    }
}