package za.co.jpsoft.winkerkreader;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.Calendar;

/**
 * Enhanced Widget Provider with modern Android compatibility and error handling.
 * Maintains compatibility with original layout while adding reliability improvements.
 *
 * FIXED: Removed duplicate onUpdate() method and corrected ListView ID reference
 */
public class WinkerkReaderWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "WinkerkReaderWidget";

    // Keep your original EXTRA_WORD constant
    public static String EXTRA_WORD = "com.commonsware.android.appwidget.lorem.WORD";

    // Action for manual updates
    private static final String ACTION_UPDATE_WIDGET = "android.appwidget.action.APPWIDGET_UPDATE";

    // Update scheduling
    private static final int UPDATE_HOUR = 1;
    private static final int UPDATE_MINUTE = 0;
    private static final int UPDATE_SECOND = 1;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate called for " + appWidgetIds.length + " widgets");

        try {
            for (int appWidgetId : appWidgetIds) {
                updateSingleWidget(context, appWidgetManager, appWidgetId);
            }

            // Schedule next update (keeping your original logic but improved)
            scheduleNextUpdate(context);

        } catch (Exception e) {
            Log.e(TAG, "Error in onUpdate", e);
        }

        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);

        try {
            if (ACTION_UPDATE_WIDGET.equals(action)) {
                AppWidgetManager manager = AppWidgetManager.getInstance(context);
                int[] ids = manager.getAppWidgetIds(
                        new ComponentName(context, WinkerkReaderWidgetProvider.class)
                );
                onUpdate(context, manager, ids);
                updateWidget(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onReceive", e);
        }

        super.onReceive(context, intent);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Log.d(TAG, "Widget enabled - scheduling updates");
        scheduleNextUpdate(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.d(TAG, "Widget disabled - canceling updates");
        cancelScheduledUpdates(context);
    }

    /**
     * Update a single widget instance using your original layout
     * FIXED: Added data refresh notification to clear cached views
     */
    private void updateSingleWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        try {
            RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.widget);

            // Main app click (your original widget_image)
            Intent clickIntent = new Intent(context, MainActivity2.class);
            PendingIntent clickPI = PendingIntent.getActivity(
                    context,
                    0,
                    clickIntent,
                    getPendingIntentFlags()
            );
            widget.setOnClickPendingIntent(R.id.widget_image, clickPI);

            // Update/refresh click (your original widget_image3)
            Intent clickUpdateIntent = new Intent(context, WinkerkReaderWidgetProvider.class);
            clickUpdateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            clickUpdateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});

            PendingIntent clickUpdatePI = PendingIntent.getBroadcast(
                    context,
                    0,
                    clickUpdateIntent,
                    getPendingIntentFlags()
            );
            widget.setOnClickPendingIntent(R.id.widget_image3, clickUpdatePI);

            // ListView service setup (enhanced but compatible)
            Intent svcIntent = new Intent(context, ListViewWidgetService.class);
            svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            // Add timestamp to force refresh
            svcIntent.putExtra("nonce", System.currentTimeMillis());
            svcIntent.setData(Uri.parse(svcIntent.toUri(Intent.URI_INTENT_SCHEME)));

            widget.setRemoteAdapter(R.id.words, svcIntent);

            Intent listClickIntent = new Intent(context, VerjaarSMS2.class);
            PendingIntent listClickPI = PendingIntent.getActivity(
                    context,
                    0,
                    listClickIntent,
                    getPendingIntentFlags()
            );
            widget.setPendingIntentTemplate(R.id.words, listClickPI);

            // CRITICAL: Notify data changed BEFORE updating widget
            // This ensures the ListView refreshes and doesn't use stale/cached views
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.words);

            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, widget);

            Log.d(TAG, "Updated widget " + appWidgetId);

        } catch (Exception e) {
            Log.e(TAG, "Error updating widget " + appWidgetId, e);
        }
    }

    /**
     * Enhanced version of your original _scheduleNextUpdate method
     */
    private void scheduleNextUpdate(Context context) {
        try {
            Calendar calendar2 = Calendar.getInstance();
            calendar2.set(Calendar.HOUR_OF_DAY, UPDATE_HOUR);
            calendar2.set(Calendar.MINUTE, UPDATE_MINUTE);
            calendar2.set(Calendar.SECOND, UPDATE_SECOND);

            // If it's already past the update time today, schedule for tomorrow
            Calendar now = Calendar.getInstance();
            if (calendar2.getTimeInMillis() <= now.getTimeInMillis()) {
                calendar2.add(Calendar.DAY_OF_MONTH, 1);
            }

            Intent intentw = new Intent(context, WinkerkReaderWidgetProvider.class);
            intentw.setAction(ACTION_UPDATE_WIDGET);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    intentw,
                    getPendingIntentFlags()
            );

            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                // Use modern alarm scheduling for better battery optimization
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar2.getTimeInMillis(),
                            pendingIntent
                    );
                } else {
                    am.setExact(
                            AlarmManager.RTC_WAKEUP,
                            calendar2.getTimeInMillis(),
                            pendingIntent
                    );
                }

                Log.d(TAG, "Scheduled next update for: " + calendar2.getTime());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error scheduling update", e);
        }
    }

    /**
     * Cancel scheduled updates
     */
    private void cancelScheduledUpdates(Context context) {
        try {
            Intent intentw = new Intent(context, WinkerkReaderWidgetProvider.class);
            intentw.setAction(ACTION_UPDATE_WIDGET);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    intentw,
                    getPendingIntentFlags()
            );

            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.cancel(pendingIntent);
                Log.d(TAG, "Cancelled scheduled updates");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error cancelling updates", e);
        }
    }

    /**
     * Enhanced version of your original updateWidget method
     * FIXED: Uses correct ListView ID (R.id.words)
     */
    private void updateWidget(Context context) {
        try {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, WinkerkReaderWidgetProvider.class)
            );
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.words);
            Log.d(TAG, "Notified data change for " + appWidgetIds.length + " widgets");
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget", e);
        }
    }

    /**
     * Get appropriate PendingIntent flags based on Android version
     * Critical for Android 12+ compatibility (SDK 31+)
     */
    private int getPendingIntentFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        } else {
            return PendingIntent.FLAG_UPDATE_CURRENT;
        }
    }

    /**
     * Public method to manually trigger widget update from anywhere in your app
     */
    public static void updateWidgets(Context context) {
        try {
            Intent updateIntent = new Intent(context, WinkerkReaderWidgetProvider.class);
            updateIntent.setAction(ACTION_UPDATE_WIDGET);
            context.sendBroadcast(updateIntent);
        } catch (Exception e) {
            Log.e("WinkerkReaderWidget", "Error triggering widget update", e);
        }
    }

    /**
     * Force immediate refresh of all widgets (useful after settings change)
     */
    public static void forceRefreshAll(Context context) {
        try {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, WinkerkReaderWidgetProvider.class)
            );

            // Notify data changed
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.words);

            // Trigger update
            Intent updateIntent = new Intent(context, WinkerkReaderWidgetProvider.class);
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
            context.sendBroadcast(updateIntent);

            Log.d("WinkerkReaderWidget", "Forced refresh of " + appWidgetIds.length + " widgets");
        } catch (Exception e) {
            Log.e("WinkerkReaderWidget", "Error forcing refresh", e);
        }
    }
}