package za.co.jpsoft.winkerkreader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.ui.activities.BedieningActivity
import za.co.jpsoft.winkerkreader.widget.PastoralWidgetProvider.Companion.updateWidget
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PastoralWidgetProvider : AppWidgetProvider() {



    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called")
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            Log.d(TAG, "Refresh action received")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, PastoralWidgetProvider::class.java)
            )
            appWidgetIds.forEach { appWidgetId ->
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    companion object {
        private const val TAG = "PastoralWidgetProvider"
        const val ACTION_REFRESH = "za.co.jpsoft.winkerkreader.ACTION_REFRESH_PASTORAL_WIDGET"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            Log.d(TAG, "Updating widget $appWidgetId")
            val views = RemoteViews(context.packageName, R.layout.widget_pastoral)

            // Update timestamp
            val timeStr = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                .format(Instant.now().atZone(ZoneId.systemDefault()))
            views.setTextViewText(
                R.id.widget_pastoral_update_time,
                context.getString(R.string.widget_last_updated, timeStr)
            )

            // Set the remote adapter
            val intent = Intent(context, PastoralWidgetRemoteViewsService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            views.setRemoteAdapter(R.id.widget_pastoral_list, intent)

            // Set empty view
            views.setEmptyView(R.id.widget_pastoral_list, R.id.widget_pastoral_empty)

            // ===== Click handler 1: Open BedieningActivity (whole widget) =====
            val openIntent = Intent(context, BedieningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_pastoral_root, openPendingIntent)

            // ===== Click handler 2: Refresh (widget_image2) =====
            val refreshIntent = Intent(context, PastoralWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image2, refreshPendingIntent)

            // Update widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_pastoral_list)
        }

        fun refreshWidgets(context: Context) {
            Log.d(TAG, "refreshWidgets called")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, PastoralWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            if (appWidgetIds.isNotEmpty()) {
                // Force a full update (onUpdate will be called)
                val intent = Intent(context, PastoralWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            } else {
                Log.d(TAG, "No pastoral widgets to update")
            }
        }
    }
}