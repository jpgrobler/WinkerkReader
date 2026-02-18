package za.co.jpsoft.winkerkreader;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.KEY_AUTOSTART;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.KEY_OPROEPMONITOR;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

/**
 * Combined boot receiver that handles device startup, package replacement,
 * and sets up necessary services and alarms
 */
public class DeviceBootReceiver extends BroadcastReceiver {
    private static final String TAG = "DeviceBootReceiver";
    private static final String PREFS_NAME = "WinkerkReader_UserInfo";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action == null) {
            Log.w(TAG, "Received intent with null action");
            return;
        }

        Log.d(TAG, "Boot receiver triggered with action: " + action);

        // Handle different boot/restart scenarios
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                Intent.ACTION_PACKAGE_REPLACED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            // Start monitoring service if enabled
            startMonitoringServiceIfEnabled(context, preferences);

            // Setup birthday reminder alarm if enabled
            setupBirthdayAlarmIfEnabled(context, preferences);

            // Start main activity if it's a boot completed event
            if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                    "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
                startMainActivity(context);
            }
        }
    }

    private void startMainActivity(Context context) {
        try {
            Intent mainActivityIntent = new Intent(context, MainActivity2.class);
            mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(mainActivityIntent);
            Log.d(TAG, "Main activity started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start main activity", e);
        }
    }

    private void startMonitoringServiceIfEnabled(Context context, SharedPreferences preferences) {
        boolean autoStartEnabled = preferences.getBoolean(KEY_AUTOSTART, false);
        boolean callMonitorEnabled = preferences.getBoolean(KEY_OPROEPMONITOR, true);

        if (autoStartEnabled || callMonitorEnabled) {
            try {
                Intent serviceIntent = new Intent(context, CallMonitoringService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }

                Log.d(TAG, "Call monitoring service started successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start call monitoring service", e);
            }
            try {
                Intent serviceIntent = new Intent(context, IncomingCall.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }

                Log.d(TAG, "Call monitoring service started successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start call monitoring service", e);
            }
        }
    }

    private void setupBirthdayAlarmIfEnabled(Context context, SharedPreferences preferences) {
        boolean reminderEnabled = preferences.getBoolean("HERINNER", false);
        boolean timeUpdate = preferences.getBoolean("SMS-TIMEUPDATE", false);

        if (reminderEnabled || timeUpdate) {
            try {
                String hour = preferences.getString("SMS-HOUR", "08");
                String minute = preferences.getString("SMS-MINUTE", "00");

                Calendar alarmTime = Calendar.getInstance();
                alarmTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hour));
                alarmTime.set(Calendar.MINUTE, Integer.parseInt(minute));
                alarmTime.set(Calendar.SECOND, 0);
                alarmTime.set(Calendar.MILLISECOND, 0);

                Calendar now = Calendar.getInstance();

                // Update preferences
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("SMS-TIMEUPDATE", false);
                editor.putBoolean("FROM_MENU", false);
                editor.apply();

                // Create alarm intent
                Intent alarmIntent = new Intent(context, AlarmReceiver.class);
                alarmIntent.setAction("VerjaarSMS");

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context,
                        0,
                        alarmIntent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                );

                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null) {
                    // Cancel any existing alarm
                    alarmManager.cancel(pendingIntent);

                    // Calculate trigger time
                    long triggerTime = alarmTime.getTimeInMillis();
                    if (triggerTime <= now.getTimeInMillis()) {
                        // If time has passed today, schedule for tomorrow
                        triggerTime += AlarmManager.INTERVAL_DAY;
                    }

                    // Schedule the alarm based on Android version
                    scheduleAlarm(alarmManager, triggerTime, pendingIntent);

                    Log.d(TAG, "Birthday reminder alarm scheduled for " + alarmTime.getTime());
                } else {
                    Log.e(TAG, "AlarmManager is null");
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to setup birthday alarm", e);
            }
        } else {
            Log.d(TAG, "Birthday reminder disabled");
        }
    }

    private void scheduleAlarm(AlarmManager alarmManager, long triggerTime, PendingIntent pendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ - Check if we can schedule exact alarms
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    Log.d(TAG, "Exact alarm scheduled for Android 12+");
                } else {
                    // Fallback to inexact alarm
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    Log.w(TAG, "Using inexact alarm - exact alarm permission not granted");
                }
            } else {
                // Android 6+ - Use setExactAndAllowWhileIdle
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                Log.d(TAG, "Exact alarm scheduled for Android 6+");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException scheduling alarm - permission may be missing", e);
            // Try inexact alarm as fallback
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                Log.w(TAG, "Fallback to inexact alarm due to security exception");
            } catch (Exception fallbackException) {
                Log.e(TAG, "Failed to schedule fallback alarm", fallbackException);
            }
        }
    }
}