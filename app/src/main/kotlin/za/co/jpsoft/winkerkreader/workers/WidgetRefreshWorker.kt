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

        // Minimum interval between updates (5 seconds)
        private const val MIN_UPDATE_INTERVAL_MS = 5000L

        // Track last update time - using a single timestamp for simplicity
        private var lastUpdateTime = 0L

        // Track if a refresh is already in progress
        private var isRefreshing = false

        // Force flag to bypass debounce
        private var forceRefresh = false
    }

    override suspend fun doWork(): Result {
        return try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "🔄 Starting widget refresh worker (force=$forceRefresh)")
            }

            // Prevent concurrent refreshes
            if (isRefreshing && !forceRefresh) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "⏳ Refresh already in progress, skipping")
                }
                return Result.success()
            }

            isRefreshing = true

            try {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastUpdate = currentTime - lastUpdateTime

                // Check if enough time has passed OR force refresh is true
                if (forceRefresh || timeSinceLastUpdate >= MIN_UPDATE_INTERVAL_MS) {

                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "🔄 Refreshing widgets (force=$forceRefresh, timeSince=${timeSinceLastUpdate}ms)"
                        )
                    }

                    // ✅ FIXED: Use invalidateCache() instead of clearCache()
                    WidgetDataRepository.invalidateCache()
                    WidgetDataRepository.refreshCache(applicationContext)

                    // Refresh both widgets
                    refreshBirthdayWidget()
                    refreshPastoralWidget()

                    // Update timestamp
                    lastUpdateTime = currentTime
                    forceRefresh = false // Reset force flag

                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "✅ Widget refresh completed")
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "⏱️ Widget refresh skipped - only ${timeSinceLastUpdate}ms since last update (min: ${MIN_UPDATE_INTERVAL_MS}ms)"
                        )
                    }
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
            // ✅ Birthday widget uses WidgetDataRepository
            WidgetDataRepository.invalidateCache()
            WidgetDataRepository.refreshCache(applicationContext)
            WinkerkReaderWidgetProvider.updateAllWidgets(applicationContext)

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "📅 Birthday widget refreshed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing birthday widget", e)
        }
    }

    private fun refreshPastoralWidget() {
        try {
            // ✅ Pastoral widget queries pastoral database directly
            // No cache needed - RemoteViewsService will query the database
            PastoralWidgetProvider.refreshWidgets(applicationContext)

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "📋 Pastoral widget refresh called")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing pastoral widget", e)
        }
    }

    /**
     * Force refresh all widgets immediately (bypasses debounce)
     * Call this when data changes significantly (e.g., after database update)
     */
    fun forceRefreshAllWidgets(context: Context) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "⚡ Force refresh requested")
            }

            // Set force flag and reset timestamp
            forceRefresh = true
            lastUpdateTime = 0L

            // Trigger a new work request
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .addTag(WORK_NAME)
                .build()

            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    workRequest
                )

        } catch (e: Exception) {
            Log.e(TAG, "Error forcing widget refresh", e)
        }
    }

    /**
     * Reset the debounce timer (useful after data changes)
     */
    fun resetDebounce() {
        lastUpdateTime = 0L
        forceRefresh = false
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Debounce timer reset")
        }
    }
}