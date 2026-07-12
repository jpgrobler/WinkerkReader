package za.co.jpsoft.winkerkreader.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.services.ListViewWidgetService
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.activities.VerjaarSmsActivity
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Enhanced Widget Provider with modern Android compatibility and error handling.
 * Maintains compatibility with original layout while adding reliability improvements.
 */
class WinkerkReaderWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "WinkerkReaderWidget"
        const val EXTRA_WORD = "com.commonsware.android.appwidget.lorem.WORD"
        private const val ACTION_UPDATE_WIDGET = "android.appwidget.action.APPWIDGET_UPDATE"
        private const val UPDATE_HOUR = 1
        private const val UPDATE_MINUTE = 0
        private const val UPDATE_SECOND = 1
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")

        try {
            for (appWidgetId in appWidgetIds) {
                updateSingleWidget(context, appWidgetManager, appWidgetId)
            }
            scheduleNextUpdate(context)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error in onUpdate", e)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (BuildConfig.DEBUG) Log.d(TAG, "onReceive: $action")

        try {
            if (ACTION_UPDATE_WIDGET == action) {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, WinkerkReaderWidgetProvider::class.java)
                )
                onUpdate(context, manager, ids)
                updateWidget(context)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error in onReceive", e)
        }
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        if (BuildConfig.DEBUG) Log.d(TAG, "Widget enabled - scheduling updates")
        scheduleNextUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (BuildConfig.DEBUG) Log.d(TAG, "Widget disabled - canceling updates")
        cancelScheduledUpdates(context)
    }

    /**
     * Update a single widget instance.
     */
    private fun updateSingleWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            val widget = RemoteViews(context.packageName, R.layout.widget).apply {
                val clickIntent = Intent(context, MainActivity::class.java)
                val clickPI = PendingIntent.getActivity(
                    context, 0, clickIntent, pendingIntentFlags
                )
                setOnClickPendingIntent(R.id.widget_image, clickPI)

                val clickUpdateIntent =
                    Intent(context, WinkerkReaderWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                    }
                val clickUpdatePI = PendingIntent.getBroadcast(
                    context, 0, clickUpdateIntent, pendingIntentFlags
                )
                setOnClickPendingIntent(R.id.widget_image3, clickUpdatePI)

                val svcIntent = Intent(context, ListViewWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra("nonce", System.currentTimeMillis())
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                @Suppress("DEPRECATION")
                setRemoteAdapter(R.id.words, svcIntent)

                val listClickIntent = Intent(context, VerjaarSmsActivity::class.java)
                val listClickPI = PendingIntent.getActivity(
                    context, 0, listClickIntent, pendingIntentFlags
                )
                setPendingIntentTemplate(R.id.words, listClickPI)
                // ✅ Add timestamp (same logic as pastoral widget)
                val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                val lastRefresh = prefs.getLong("last_refresh_time", System.currentTimeMillis())
                val timeStr =
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastRefresh))
                setTextViewText(R.id.widget_update_time, "Laas opgedateer: $timeStr")
                setEmptyView(R.id.words, R.id.widget_empty)
                val headerText = getEventEmojis(context)
                setTextViewText(R.id.widget_header, headerText)
            }

            // Notify data changed before updating widget
            @Suppress("DEPRECATION")
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.words)

            appWidgetManager.updateAppWidget(appWidgetId, widget)
            if (BuildConfig.DEBUG) Log.d(TAG, "Updated widget $appWidgetId")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error updating widget $appWidgetId", e)
        }
    }

    private fun getEventEmojis(context: Context): String {
        val settings = SettingsManager.getInstance(context)
        val emojis = mutableListOf<String>()

        // Always include birthdays (Verjaar) – they are not filtered
        emojis.add("🎂")

        // Add other events based on user settings
        if (settings.widgetDoop) emojis.add("💧")
        if (settings.widgetHuwelik) emojis.add("💍")
        if (settings.widgetBelydenis) emojis.add("⛪")
        if (settings.widgetSterf) emojis.add("🪦")

        return emojis.joinToString(" ")
    }

    /**
     * Schedule the next automatic widget update.
     */
    private fun scheduleNextUpdate(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return

            // Build the pending intent for the update broadcast
            val intent = Intent(context, WinkerkReaderWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, pendingIntentFlags
            )

            // Cancel any existing alarm first
            alarmManager.cancel(pendingIntent)

            // Calculate the next desired update time (e.g., 01:00:01 AM daily)
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, UPDATE_HOUR)
                set(Calendar.MINUTE, UPDATE_MINUTE)
                set(Calendar.SECOND, UPDATE_SECOND)
            }
            val now = Calendar.getInstance()
            if (calendar.timeInMillis <= now.timeInMillis) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            // Use inexact repeating alarm – works on all API levels and does not require
            // the SCHEDULE_EXACT_ALARM permission.
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Scheduled inexact daily update at ~${calendar.time}")
            }
        } catch (e: Exception) {
            // Catch any unexpected errors (e.g., security exceptions if any)
            if (BuildConfig.DEBUG) Log.e(TAG, "Error scheduling widget update", e)
        }
    }

    /**
     * Cancel any scheduled updates.
     */
    private fun cancelScheduledUpdates(context: Context) {
        try {
            val intent = Intent(context, WinkerkReaderWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, pendingIntentFlags
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(pendingIntent)
            if (BuildConfig.DEBUG) Log.d(TAG, "Cancelled scheduled updates")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error cancelling updates", e)
        }
    }


    /**
     * Notify data changed for all widgets.
     */
    private fun updateWidget(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, WinkerkReaderWidgetProvider::class.java)
            )
            @Suppress("DEPRECATION")
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.words)
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Notified data change for ${appWidgetIds.size} widgets"
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error updating widget", e)
        }
    }

    /**
     * Get appropriate PendingIntent flags.
     */
    private val pendingIntentFlags: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
}