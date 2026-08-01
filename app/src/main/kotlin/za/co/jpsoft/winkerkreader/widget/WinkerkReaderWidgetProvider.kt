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
import dagger.hilt.android.EntryPointAccessors
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.di.WidgetProviderEntryPoint
import za.co.jpsoft.winkerkreader.services.ListViewWidgetService
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.activities.VerjaarSmsActivity
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

        /**
         * Builds the full RemoteViews tree for a single widget instance.
         * Uses dynamic Material 3 colors for light/dark mode.
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
                setImageViewResource(R.id.widget_image, R.drawable.ic_launcher_roundw)

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
            // Use Hilt entry point to get WidgetPrefs
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                WidgetProviderEntryPoint::class.java
            )
            val widgetPrefs = entryPoint.widgetPrefs()

            val emojis = mutableListOf<String>()
            emojis.add("🎂") // Always include birthdays

            if (widgetPrefs.widgetDoop) emojis.add("💧")
            if (widgetPrefs.widgetHuwelik) emojis.add("💍")
            if (widgetPrefs.widgetBelydenis) emojis.add("⛪")
            if (widgetPrefs.widgetSterf) emojis.add("🪦")

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

        private const val ACTION_SCHEDULED_UPDATE =
            "za.co.jpsoft.winkerkreader.SCHEDULED_WIDGET_UPDATE"
        private const val ACTION_FORCE_UPDATE = "za.co.jpsoft.winkerkreader.FORCE_WIDGET_UPDATE"
        private const val UPDATE_HOUR = 1
        private const val UPDATE_MINUTE = 0
        private const val UPDATE_SECOND = 1

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
            for (appWidgetId in appWidgetIds) {
                updateSingleWidget(context, appWidgetManager, appWidgetId)
            }
            scheduleDebouncedRefresh(context)
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
            forceRefreshWidgets(context)
        }

        try {
            when (action) {
                ACTION_SCHEDULED_UPDATE -> {
                    scheduleDebouncedRefresh(context)
                }
                ACTION_FORCE_UPDATE -> {
                    forceRefreshWidgets(context)
                }
                else -> {
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

    private fun updateSingleWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
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
            @Suppress("DEPRECATION")
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.words)

            if (BuildConfig.DEBUG) Log.d(TAG, "Updated widget $appWidgetId")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error updating widget $appWidgetId", e)
        }
    }

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

    private fun forceRefreshWidgets(context: Context) {
        try {
            if (BuildConfig.DEBUG) Log.d(TAG, "⚡ Force refreshing widgets")

            lastDirectUpdateTime.clear()

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, WinkerkReaderWidgetProvider::class.java)
            )

            for (appWidgetId in appWidgetIds) {
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