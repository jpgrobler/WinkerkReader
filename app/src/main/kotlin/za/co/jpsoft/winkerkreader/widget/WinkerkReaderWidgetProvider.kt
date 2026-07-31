package za.co.jpsoft.winkerkreader.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.services.ListViewWidgetService
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.activities.VerjaarSmsActivity
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.workers.WidgetRefreshWorker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Enhanced Widget Provider with debounced updates via WorkManager.
 * Maintains compatibility with original layout while adding reliability improvements.
 */
class WinkerkReaderWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "WinkerkReaderWidget"
        const val EXTRA_WORD = "com.commonsware.android.appwidget.lorem.WORD"

        private fun tintDrawableToBitmap(
            context: Context,
            drawableRes: Int,
            tintColor: Int
        ): Bitmap {
            val drawable = AppCompatResources.getDrawable(context, drawableRes)
                ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.takeIf { it > 0 } ?: 48,
                drawable.intrinsicHeight.takeIf { it > 0 } ?: 48,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            drawable.draw(canvas)
            return bitmap
        }
        /**
         * Fully rebuild and push RemoteViews for every placed instance of this widget,
         * bypassing the direct-update debounce, and notify the ListView adapter to reload.
         *
         * Call this from background refresh paths (e.g. WidgetRefreshWorker) instead of
         * broadcasting AppWidgetManager.ACTION_APPWIDGET_UPDATE — sending that broadcast
         * would just re-trigger this class's own onReceive() debounce branch rather than
         * actually pushing new content to the screen.
         */
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, WinkerkReaderWidgetProvider::class.java)
                )
                if (appWidgetIds.isEmpty()) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "updateAllWidgets: no widgets placed")
                    return
                }
                for (appWidgetId in appWidgetIds) {
                    lastDirectUpdateTime[appWidgetId] = System.currentTimeMillis()
                    val widget = buildWidgetRemoteViews(context, appWidgetId)
                    appWidgetManager.updateAppWidget(appWidgetId, widget)
                    @Suppress("DEPRECATION")
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.words)
                }
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "✅ updateAllWidgets pushed refresh to ${appWidgetIds.size} widget(s)"
                    )
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error in updateAllWidgets", e)
            }
        }

        private fun isNightMode(context: Context): Boolean {
            val configuration = context.resources.configuration
            return (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        /**
         * Builds the full RemoteViews tree for a single widget instance: click intents,
         * the ListView remote adapter, timestamp text and header emojis.
         *
         * This is the single source of truth for widget content — used by the normal
         * update path, the force-refresh path, and updateAllWidgets() so they can never
         * drift out of sync again.
         */
        /**
         * Builds the full RemoteViews tree for a single widget instance: click intents,
         * the ListView remote adapter, timestamp text and header emojis, plus dynamic
         * Material 3 colors for light/dark mode.
         */
        private fun buildWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget).apply {
                // ---------- Click intents ----------
                val clickIntent = Intent(context, MainActivity::class.java)
                val clickPI = PendingIntent.getActivity(
                    context, 0, clickIntent, pendingIntentFlags
                )
                setOnClickPendingIntent(R.id.widget_image, clickPI)

                val forceUpdateIntent =
                    Intent(context, WinkerkReaderWidgetProvider::class.java).apply {
                        action = ACTION_FORCE_UPDATE
                    }
                val forceUpdatePI = PendingIntent.getBroadcast(
                    context, 0, forceUpdateIntent, pendingIntentFlags
                )
                setOnClickPendingIntent(R.id.widget_image3, forceUpdatePI)

                // ---------- ListView adapter ----------
                val svcIntent = Intent(context, ListViewWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra("nonce", System.currentTimeMillis())
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                @Suppress("DEPRECATION")
                setRemoteAdapter(appWidgetId, R.id.words, svcIntent)

                val listClickIntent = Intent(context, VerjaarSmsActivity::class.java)
                val listClickPI = PendingIntent.getActivity(
                    context, 0, listClickIntent, pendingIntentFlags
                )
                setPendingIntentTemplate(R.id.words, listClickPI)

                // ---------- Dynamic Material 3 Colors ----------
                val isNight = (context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

                val surfaceColor = ContextCompat.getColor(context, R.color.md_theme_surface)
                val onSurfaceColor = ContextCompat.getColor(context, R.color.md_theme_onSurface)
                val onSurfaceVariantColor = ContextCompat.getColor(
                    context, R.color.md_theme_onSurfaceVariant
                )

                // Root background
                setInt(R.id.widget_root, "setBackgroundColor", surfaceColor)

                // Header text
                setTextColor(R.id.widget_header, onSurfaceColor)

                // Update time and empty view
                setTextColor(R.id.widget_update_time, onSurfaceVariantColor)
                setTextColor(R.id.widget_empty, onSurfaceVariantColor)

                // ---- Icons ----
                // widget_image: use original drawable (no tint) – keeps your branded icon
                setImageViewResource(R.id.widget_image, R.drawable.ic_launcher_roundw)

                // widget_image3: tint to match the theme (update icon is usually a simple vector)
                val tintedUpdate = tintDrawableToBitmap(context, R.drawable.updatew, onSurfaceColor)
                setImageViewBitmap(R.id.widget_image3, tintedUpdate)

                // ---------- Timestamp & header emojis ----------
                val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                val lastRefresh = prefs.getLong("last_refresh_time", System.currentTimeMillis())
                val timeStr =
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastRefresh))
                setTextViewText(R.id.widget_update_time, "Laas opgedateer: $timeStr")
                setEmptyView(R.id.words, R.id.widget_empty)

                val headerText = getEventEmojis(context)
                setTextViewText(R.id.widget_header, headerText)
            }
        }

        private fun getEventEmojis(context: Context): String {
            val settings = SettingsManager.getInstance(context)
            val emojis = mutableListOf<String>()

            // Always include birthdays (Verjaar) – they are not filtered
            emojis.add("🎂")

            // Add other events based on user settings
            if (settings.widget.widgetDoop) emojis.add("💧")
            if (settings.widget.widgetHuwelik) emojis.add("💍")
            if (settings.widget.widgetBelydenis) emojis.add("⛪")
            if (settings.widget.widgetSterf) emojis.add("🪦")

            return emojis.joinToString(" ")
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

        // IMPORTANT: this must NOT equal AppWidgetManager.ACTION_APPWIDGET_UPDATE
        // ("android.appwidget.action.APPWIDGET_UPDATE"). Reusing that string here used to
        // shadow the real system broadcast — onReceive() would intercept every genuine
        // APPWIDGET_UPDATE and just re-schedule work instead of letting AppWidgetProvider's
        // base onReceive() dispatch it to onUpdate(). Keep this as our own private alarm action.
        private const val ACTION_SCHEDULED_UPDATE =
            "za.co.jpsoft.winkerkreader.SCHEDULED_WIDGET_UPDATE"
        private const val ACTION_FORCE_UPDATE = "za.co.jpsoft.winkerkreader.FORCE_WIDGET_UPDATE"
        private const val UPDATE_HOUR = 1
        private const val UPDATE_MINUTE = 0
        private const val UPDATE_SECOND = 1

        // Track last update time per widget for debounce
        private val lastDirectUpdateTime = mutableMapOf<Int, Long>()
        private const val MIN_DIRECT_UPDATE_MS = 5000L
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")

        try {
            // Update widgets immediately for first display
            for (appWidgetId in appWidgetIds) {
                updateSingleWidget(context, appWidgetManager, appWidgetId)
            }

            // Schedule debounced refresh for future updates
            scheduleDebouncedRefresh(context)

            // Schedule next automatic update
            scheduleNextUpdate(context)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error in onUpdate", e)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (BuildConfig.DEBUG) Log.d(TAG, "onReceive: $action")
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            // Force refresh all widgets when configuration changes (e.g., dark/light mode)
            forceRefreshWidgets(context)
        }
        try {
            when (action) {
                ACTION_SCHEDULED_UPDATE -> {
                    // Our own daily-alarm trigger: schedule a debounced refresh
                    scheduleDebouncedRefresh(context)
                }

                ACTION_FORCE_UPDATE -> {
                    // Force immediate update (bypass debounce)
                    forceRefreshWidgets(context)
                }

                else -> {
                    // Includes the real AppWidgetManager.ACTION_APPWIDGET_UPDATE broadcast,
                    // which AppWidgetProvider's base onReceive() will route to onUpdate().
                    super.onReceive(context, intent)
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error in onReceive", e)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        if (BuildConfig.DEBUG) Log.d(TAG, "Widget enabled - scheduling updates")
        scheduleNextUpdate(context)
        scheduleDebouncedRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (BuildConfig.DEBUG) Log.d(TAG, "Widget disabled - canceling updates")
        cancelScheduledUpdates(context)
    }

    /**
     * Update a single widget instance with debounce.
     */
    private fun updateSingleWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            // Debounce direct updates
            val currentTime = System.currentTimeMillis()
            val lastUpdate = lastDirectUpdateTime[appWidgetId] ?: 0L

            if (currentTime - lastUpdate < MIN_DIRECT_UPDATE_MS) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "⏱️ Skipping direct update for widget $appWidgetId - debounce active"
                    )
                }
                return
            }
            lastDirectUpdateTime[appWidgetId] = currentTime

            val widget = buildWidgetRemoteViews(context, appWidgetId)

            appWidgetManager.updateAppWidget(appWidgetId, widget)

            // Notify data changed AFTER the adapter has been (re)applied via updateAppWidget
            @Suppress("DEPRECATION")
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.words)

            if (BuildConfig.DEBUG) Log.d(TAG, "Updated widget $appWidgetId")

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error updating widget $appWidgetId", e)
        }
    }

    /**
     * Schedule a debounced refresh using WorkManager
     */
    private fun scheduleDebouncedRefresh(context: Context) {
        try {
            if (BuildConfig.DEBUG) Log.d(TAG, "📅 Scheduling debounced widget refresh")

            val workRequest = OneTimeWorkRequest.Builder(WidgetRefreshWorker::class.java)
                .setInitialDelay(2, TimeUnit.SECONDS)
                .addTag(WidgetRefreshWorker.WORK_NAME)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WidgetRefreshWorker.WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "✅ Debounced refresh scheduled (2s delay)")
            }

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error scheduling debounced refresh", e)
        }
    }

    /**
     * Force refresh widgets immediately (bypass debounce)
     */
    private fun forceRefreshWidgets(context: Context) {
        try {
            if (BuildConfig.DEBUG) Log.d(TAG, "⚡ Force refreshing widgets")

            // Clear debounce timestamps
            lastDirectUpdateTime.clear()

            // Update immediately
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, WinkerkReaderWidgetProvider::class.java)
            )

            for (appWidgetId in appWidgetIds) {
                // Bypass debounce by directly updating
                try {
                    val currentTime = System.currentTimeMillis()
                    lastDirectUpdateTime[appWidgetId] = currentTime

                    val widget = buildWidgetRemoteViews(context, appWidgetId)
                    appWidgetManager.updateAppWidget(appWidgetId, widget)
                    @Suppress("DEPRECATION")
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.words)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(
                        TAG,
                        "Error in force update for widget $appWidgetId",
                        e
                    )
                }
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "✅ Force refresh completed for ${appWidgetIds.size} widgets")
            }

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error force refreshing widgets", e)
        }
    }

    /**
     * Schedule the next automatic widget update.
     */
    private fun scheduleNextUpdate(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return

            val intent = Intent(context, WinkerkReaderWidgetProvider::class.java).apply {
                action = ACTION_SCHEDULED_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, pendingIntentFlags
            )

            alarmManager.cancel(pendingIntent)

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, UPDATE_HOUR)
                set(Calendar.MINUTE, UPDATE_MINUTE)
                set(Calendar.SECOND, UPDATE_SECOND)
            }
            val now = Calendar.getInstance()
            if (calendar.timeInMillis <= now.timeInMillis) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

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
            if (BuildConfig.DEBUG) Log.e(TAG, "Error scheduling widget update", e)
        }
    }

    /**
     * Cancel any scheduled updates.
     */
    private fun cancelScheduledUpdates(context: Context) {
        try {
            val intent = Intent(context, WinkerkReaderWidgetProvider::class.java).apply {
                action = ACTION_SCHEDULED_UPDATE
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

}