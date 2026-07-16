package za.co.jpsoft.winkerkreader.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.widget.PastoralWidgetProvider
import za.co.jpsoft.winkerkreader.widget.WidgetDataRepository
import za.co.jpsoft.winkerkreader.widget.WinkerkReaderWidgetProvider

class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WidgetRefreshWorker"
        const val WORK_NAME = "widget_refresh_work"

        private const val MIN_UPDATE_INTERVAL_MS = 5000L
        private var lastUpdateTime = 0L
        private var isRefreshing = false
        private var forceRefresh = false
    }

    override suspend fun doWork(): Result {
        return try {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "🔄 Starting widget refresh worker (force=$forceRefresh)"
            )

            if (isRefreshing && !forceRefresh) {
                if (BuildConfig.DEBUG) Log.d(TAG, "⏳ Refresh already in progress, skipping")
                return Result.success()
            }

            isRefreshing = true

            try {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastUpdate = currentTime - lastUpdateTime

                if (forceRefresh || timeSinceLastUpdate >= MIN_UPDATE_INTERVAL_MS) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "🔄 Refreshing widgets (force=$forceRefresh, timeSince=${timeSinceLastUpdate}ms)"
                    )

                    // Refresh the shared birthday/event cache once here.
                    WidgetDataRepository.invalidateCache()
                    WidgetDataRepository.refreshCache(applicationContext)

                    // Push updated views to both widgets.
                    // refreshBirthdayWidget() uses the cache we just populated — do NOT
                    // invalidate/refresh again inside that method.
                    // refreshPastoralWidget() must NOT schedule more WorkManager jobs;
                    // it only pushes RemoteViews and notifies the adapter.
                    refreshBirthdayWidget()
                    refreshPastoralWidget()

                    lastUpdateTime = currentTime
                    forceRefresh = false

                    if (BuildConfig.DEBUG) Log.d(TAG, "✅ Widget refresh completed")
                } else {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "⏱️ Widget refresh skipped — only ${timeSinceLastUpdate}ms since last update (min: ${MIN_UPDATE_INTERVAL_MS}ms)"
                    )
                }

                Result.success()
            } finally {
                isRefreshing = false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing widgets", e)
            isRefreshing = false
            forceRefresh = false
            Result.retry()
        }
    }

    private fun refreshBirthdayWidget() {
        try {
            // Cache is already fresh from doWork() — just push views.
            WinkerkReaderWidgetProvider.updateAllWidgets(applicationContext)
            if (BuildConfig.DEBUG) Log.d(TAG, "📅 Birthday widget refreshed")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing birthday widget", e)
        }
    }

    private fun refreshPastoralWidget() {
        try {
            // Call the direct update path so we do NOT re-enter WorkManager scheduling
            // from inside a running worker. refreshWidgets() previously called
            // forceRefreshWidgets() which enqueued more workers, creating a loop.
            val appWidgetManager =
                android.appwidget.AppWidgetManager.getInstance(applicationContext)
            val ids = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(
                    applicationContext,
                    PastoralWidgetProvider::class.java
                )
            )
            ids.forEach {
                PastoralWidgetProvider.updateWidget(
                    applicationContext,
                    appWidgetManager,
                    it
                )
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "📋 Pastoral widget refreshed")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing pastoral widget", e)
        }
    }

    /** Call this from outside (e.g. after a database sync) to force a full refresh. */
    fun forceRefreshAllWidgets(context: Context) {
        try {
            forceRefresh = true
            lastUpdateTime = 0L
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .addTag(WORK_NAME)
                .build()
            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, androidx.work.ExistingWorkPolicy.REPLACE, workRequest)
            if (BuildConfig.DEBUG) Log.d(TAG, "⚡ Force refresh requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error forcing widget refresh", e)
        }
    }
}