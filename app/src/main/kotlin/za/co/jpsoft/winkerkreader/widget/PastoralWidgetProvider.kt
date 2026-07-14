package za.co.jpsoft.winkerkreader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.ui.activities.BedieningActivity
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.workers.WidgetRefreshWorker
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class PastoralWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")

        // Update each widget
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }

        // Force a refresh after a short delay to ensure data loads
        scheduleImmediateRefresh(context)
        scheduleDebouncedRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Refresh action received - updating now")

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, PastoralWidgetProvider::class.java)
                )

                // ✅ Force data reload by invalidating the RemoteViewsService
                appWidgetIds.forEach { appWidgetId ->
                    // Notify that data has changed so RemoteViewsService reloads
                    appWidgetManager.notifyAppWidgetViewDataChanged(
                        appWidgetId,
                        R.id.widget_pastoral_list
                    )
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
                scheduleDebouncedRefresh(context)
            }

            ACTION_FORCE_REFRESH -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Force refresh action received")
                forceRefreshWidgets(context)
            }
        }
    }

    companion object {
        private const val TAG = "PastoralWidgetProvider"
        const val ACTION_REFRESH = "za.co.jpsoft.winkerkreader.ACTION_REFRESH_PASTORAL_WIDGET"
        const val ACTION_FORCE_REFRESH =
            "za.co.jpsoft.winkerkreader.ACTION_FORCE_REFRESH_PASTORAL_WIDGET"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Updating widget $appWidgetId")

            try {
                val views = RemoteViews(context.packageName, R.layout.widget_pastoral)

                // Update timestamp
                val timeStr = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                    .format(Instant.now().atZone(ZoneId.systemDefault()))
                views.setTextViewText(
                    R.id.widget_pastoral_update_time,
                    context.getString(R.string.widget_last_updated, timeStr)
                )

                // ✅ Set the remote adapter for the list with a unique nonce to force reload
                val intent = Intent(context, PastoralWidgetRemoteViewsService::class.java)
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                intent.putExtra("nonce", System.currentTimeMillis()) // Force reload
                views.setRemoteAdapter(R.id.widget_pastoral_list, intent)

                // Set empty view
                views.setEmptyView(R.id.widget_pastoral_list, R.id.widget_pastoral_empty)

                // Click handlers
                setupClickHandlers(context, views, appWidgetId)

                // ✅ Update widget and force data reload
                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(
                    appWidgetId,
                    R.id.widget_pastoral_list
                )

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "✅ Widget $appWidgetId updated successfully")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget $appWidgetId", e)
            }
        }

        private fun setupClickHandlers(context: Context, views: RemoteViews, appWidgetId: Int) {
            // Click handler 1: Whole widget opens BedieningActivity
            val openBedieningIntent = Intent(context, BedieningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openBedieningPendingIntent = PendingIntent.getActivity(
                context, 0, openBedieningIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_pastoral_root, openBedieningPendingIntent)

            // Click handler 2: Left icon opens MainActivity
            val openMainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openMainPendingIntent = PendingIntent.getActivity(
                context, appWidgetId, openMainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image, openMainPendingIntent)

            // Click handler 3: Right icon refreshes the widget
            val refreshIntent = Intent(context, PastoralWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image2, refreshPendingIntent)
        }

        /**
         * Schedule an immediate refresh (1 second delay)
         */
        fun scheduleImmediateRefresh(context: Context) {
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "📅 Scheduling immediate widget refresh")

                val workRequest = OneTimeWorkRequest.Builder(WidgetRefreshWorker::class.java)
                    .setInitialDelay(1, TimeUnit.SECONDS)
                    .addTag("pastoral_widget_immediate")
                    .build()

                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        "pastoral_widget_immediate_${System.currentTimeMillis()}",
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )

            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling immediate refresh", e)
            }
        }

        fun scheduleDebouncedRefresh(context: Context) {
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

            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling debounced refresh", e)
            }
        }

        fun forceRefreshWidgets(context: Context) {
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "⚡ Force refreshing widgets")

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, PastoralWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                if (appWidgetIds.isNotEmpty()) {
                    appWidgetIds.forEach { appWidgetId ->
                        // ✅ Force data reload
                        appWidgetManager.notifyAppWidgetViewDataChanged(
                            appWidgetId,
                            R.id.widget_pastoral_list
                        )
                        updateWidget(context, appWidgetManager, appWidgetId)
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "✅ Direct refresh completed for ${appWidgetIds.size} widgets")
                    }
                }

                // Also schedule a backup refresh
                scheduleImmediateRefresh(context)
                scheduleDebouncedRefresh(context)

            } catch (e: Exception) {
                Log.e(TAG, "Error forcing widget refresh", e)
            }
        }

        fun refreshWidgets(context: Context) {
            if (BuildConfig.DEBUG) Log.d(TAG, "refreshWidgets called")
            forceRefreshWidgets(context)
        }
    }
}