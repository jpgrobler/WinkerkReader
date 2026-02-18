package za.co.jpsoft.winkerkreader;

import static za.co.jpsoft.winkerkreader.SettingsManager.isValidCalendarId;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.KEY_SELECTED_CALENDAR_ID;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class CallMonitoringService extends Service {
    private long selectedCalendarId = -1;
    private static final long DEFAULT_CALENDAR_ID = -1;
    private static final String TAG = "CallMonitoringService";
    private static final String CHANNEL_ID = "call_monitoring_channel";
    private static final int NOTIFICATION_ID = 1;

    private PhoneCallMonitor phoneCallMonitor;
    private DatabaseHelper databaseHelper;

    private static boolean isRunning = false;

    public static boolean isServiceRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Call Monitoring Service created");
        isRunning = true;
        createNotificationChannel();
        databaseHelper = new DatabaseHelper(this);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Call Monitoring Service started");
        // Start foreground notification
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Check permissions before starting monitoring
        if (hasRequiredPermissions()) {
            startCallMonitoring();
        } else {
            Log.w(TAG, "Missing required permissions, cannot start monitoring");
            // Could send a notification to user about missing permissions
        }
        
        // Return START_STICKY so the service restarts if killed
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Call Monitoring Service destroyed");
        isRunning = false;
        if (phoneCallMonitor != null) {
            phoneCallMonitor.stopMonitoring();
        }
        // Also clean up database helper
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Call Monitoring",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitors incoming calls in the background");
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity2.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Logger Active")
            .setContentText("Monitoring Phone & Voip calls")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }
    
    private boolean hasRequiredPermissions() {
        String[] requiredPermissions = {
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CALL_LOG
        };
        
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startCallMonitoring() {
        try {
            if (!hasRequiredPermissions()) {
                Log.w(TAG, "Missing required permissions for call monitoring");
                return;
            }

            CalendarManager calendarManager = new CalendarManager(this);
            long calendarId = DEFAULT_CALENDAR_ID;
            SharedPreferences userPrefs = this.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE);

            calendarId = userPrefs.getLong(KEY_SELECTED_CALENDAR_ID, DEFAULT_CALENDAR_ID);
            if (!isValidCalendarId(calendarId)) {
                Log.w(TAG, "Invalid calendar ID: " + calendarId);
                calendarId = DEFAULT_CALENDAR_ID;
            }

            if (databaseHelper == null) {
                databaseHelper = new DatabaseHelper(this);
            }

            phoneCallMonitor = new PhoneCallMonitor(this, databaseHelper, calendarManager, calendarId);
            phoneCallMonitor.startMonitoring();
            Log.d(TAG, "Phone call monitoring started successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error starting call monitoring", e);
            // Consider stopping the service if monitoring can't start
            stopSelf();
        }
    }



}