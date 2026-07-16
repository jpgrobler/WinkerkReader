package za.co.jpsoft.winkerkreader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
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
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
        scheduleDebouncedRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Refresh action received")
                val mgr = AppWidgetManager.getInstance(context)
                val ids =
                    mgr.getAppWidgetIds(ComponentName(context, PastoralWidgetProvider::class.java))
                ids.forEach { appWidgetId ->
                    mgr.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_pastoral_list)
                    updateWidget(context, mgr, appWidgetId)
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

        /**
         * Tint a drawable resource to a given colour and return it as a Bitmap.
         * This is used to tint the "bediening" icon to match the theme.
         */
        private fun tintDrawableToBitmap(
            context: Context,
            drawableRes: Int,
            tintColor: Int
        ): Bitmap {
            val drawable = ContextCompat.getDrawable(context, drawableRes)
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

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Updating widget $appWidgetId")
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_pastoral)

                // ---------- Dynamic Material 3 Colors ----------
                val surfaceColor = ContextCompat.getColor(context, R.color.md_theme_surface)
                val onSurfaceColor = ContextCompat.getColor(context, R.color.md_theme_onSurface)
                val onSurfaceVariantColor =
                    ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)

                // Root background
                views.setInt(R.id.widget_pastoral_root, "setBackgroundColor", surfaceColor)

                // Header text
                views.setTextColor(R.id.widget_pastoral_header, onSurfaceColor)

                // Empty view & update time
                views.setTextColor(R.id.widget_pastoral_empty, onSurfaceVariantColor)
                views.setTextColor(R.id.widget_pastoral_update_time, onSurfaceVariantColor)

                // Tint the "bediening" icon (widget_image2) to match the theme
                val tintedBediening =
                    tintDrawableToBitmap(context, R.drawable.updatew, onSurfaceColor)
                views.setImageViewBitmap(R.id.widget_image2, tintedBediening)

                // App icon (widget_image) remains unchanged – keep its original colours
                // (no tint applied)

                // ---------- Timestamp ----------
                val timeStr = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                    .format(Instant.now().atZone(ZoneId.systemDefault()))
                views.setTextViewText(
                    R.id.widget_pastoral_update_time,
                    context.getString(R.string.widget_last_updated, timeStr)
                )

                // ---------- ListView adapter ----------
                val intent = Intent(context, PastoralWidgetRemoteViewsService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra("nonce", System.currentTimeMillis())
                }
                views.setRemoteAdapter(R.id.widget_pastoral_list, intent)
                views.setEmptyView(R.id.widget_pastoral_list, R.id.widget_pastoral_empty)

                // ---------- Click handlers ----------
                setupClickHandlers(context, views, appWidgetId)

                // ---------- Apply update ----------
                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(
                    appWidgetId,
                    R.id.widget_pastoral_list
                )

                if (BuildConfig.DEBUG) Log.d(TAG, "✅ Widget $appWidgetId updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget $appWidgetId", e)
            }
        }

        private fun setupClickHandlers(context: Context, views: RemoteViews, appWidgetId: Int) {
            // Click on the widget root → open BedieningActivity
            val openBedieningPendingIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, BedieningActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_pastoral_root, openBedieningPendingIntent)

            // Click on the launcher icon (top‑left) → open MainActivity
            val openMainPendingIntent = PendingIntent.getActivity(
                context, appWidgetId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image_launcher, openMainPendingIntent)

            // Click on the bediening icon (background) → refresh the widget
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId,
                Intent(context, PastoralWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image_bediening, refreshPendingIntent)
        }

        /**
         * Debounced refresh — the default path triggered by system/alarm updates.
         * Uses REPLACE policy so rapid calls collapse into one job.
         */
        fun scheduleDebouncedRefresh(context: Context) {
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "📅 Scheduling debounced widget refresh")
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WidgetRefreshWorker.WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequest.Builder(WidgetRefreshWorker::class.java)
                        .setInitialDelay(2, TimeUnit.SECONDS)
                        .addTag(WidgetRefreshWorker.WORK_NAME)
                        .build()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling debounced refresh", e)
            }
        }

        /**
         * Force-refresh: push immediate RemoteViews update then schedule a debounced
         * WorkManager job to reload the underlying data.
         */
        fun forceRefreshWidgets(context: Context) {
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "⚡ Force refreshing widgets")
                val mgr = AppWidgetManager.getInstance(context)
                val ids =
                    mgr.getAppWidgetIds(ComponentName(context, PastoralWidgetProvider::class.java))
                ids.forEach { appWidgetId ->
                    mgr.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_pastoral_list)
                    updateWidget(context, mgr, appWidgetId)
                }
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "✅ Direct refresh completed for ${ids.size} widgets"
                )
                scheduleDebouncedRefresh(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error forcing widget refresh", e)
            }
        }

        /**
         * Called from WidgetRefreshWorker — schedule a debounced WorkManager refresh
         * so the pastoral RemoteViewsService re-queries its data.
         */
        fun refreshWidgets(context: Context) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "refreshWidgets called — scheduling debounced refresh"
            )
            scheduleDebouncedRefresh(context)
        }
    }
}