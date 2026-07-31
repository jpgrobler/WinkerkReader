// AppInitializer.kt
package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.DatabaseInitializer
import za.co.jpsoft.winkerkreader.data.calllog.CallLogDatabase
import za.co.jpsoft.winkerkreader.data.repositories.ChurchInfoRepository
import za.co.jpsoft.winkerkreader.services.CallMonitoringService
import za.co.jpsoft.winkerkreader.widget.PastoralWidgetProvider

/**
 * Centralised initialisation logic for the app.
 * Should be called from the Application class and, if needed, after permission grants.
 */
object AppInitializer {

    private const val TAG = "AppInitializer"

    /**
     * Perform initialisation that must happen on app start.
     * This includes database setup, service start, and WorkManager scheduling.
     *
     * @param appContext Application context
     * @param lifecycleScope Optional coroutine scope to launch background tasks.
     *                       If null, tasks are launched on a new CoroutineScope.
     * @param onProgress Optional callback for database initialisation progress.
     */
    fun initialize(
        appContext: Context,
        lifecycleScope: LifecycleCoroutineScope? = null,
        onProgress: ((progress: Int) -> Unit)? = null,
        onComplete: ((success: Boolean) -> Unit)? = null,
        onReady: (() -> Unit)? = null   // <-- NEW: called after DB is ready (or already)
    ) {
        val scope =
            lifecycleScope ?: kotlinx.coroutines.CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            withContext(Dispatchers.IO) {
                val callLogDb = CallLogDatabase.getInstance(appContext)
                CallLogImporter.importIfNeeded(appContext, callLogDb)
                ActiveCallReconciler.reconcile(callLogDb.callLogDao())

                val settings = SettingsManager.getInstance(appContext)
                settings.congregation.ensureDefaultColors()
                if (!settings.sync.isDatabaseInitialized) {
                    DatabaseInitializer.initializeDatabase(
                        context = appContext,
                        listener = object : DatabaseInitializer.ProgressListener {
                            override fun onProgressUpdate(progress: Int) {
                                onProgress?.invoke(progress)
                            }

                            override fun onInitializationComplete(success: Boolean) {
                                if (success) {
                                    settings.sync.isDatabaseInitialized = true
                                    if (BuildConfig.DEBUG) Log.d(TAG, "Database initialised")
                                } else {
                                    if (BuildConfig.DEBUG) Log.e(
                                        TAG,
                                        "Database initialisation failed"
                                    )
                                }
                                onComplete?.invoke(success)
                                // <-- NEW: notify that DB is ready
                                onReady?.invoke()
                            }
                        }
                    )
                } else {
                    // Already initialized, call onReady immediately
                    onReady?.invoke()
                }
            }

            // 2. Start monitoring service if enabled (after DB init)
            withContext(Dispatchers.Main) {
                val settings = SettingsManager.getInstance(appContext)
                if (settings.callMonitor.autoStartEnabled) {
                    try {
                        val intent =
                            android.content.Intent(appContext, CallMonitoringService::class.java)
                        appContext.startForegroundService(intent)
                        if (BuildConfig.DEBUG) Log.d(TAG, "CallMonitoringService started")
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e(
                            TAG,
                            "Failed to start CallMonitoringService",
                            e
                        )
                    }
                }
            }

            // 3. Schedule background tasks (WorkManager)
            withContext(Dispatchers.Main) {
                WorkScheduler(appContext, SettingsManager.getInstance(appContext)).scheduleAll()
                PastoralWidgetProvider.refreshWidgets(appContext)
            }
            ChurchInfoRepository.loadChurchInfo(appContext)
        }
    }

    /**
     * Simplified version without progress callbacks – for use in Application.onCreate().
     */
    fun initializeApp(appContext: Context) {
        initialize(appContext, null, null, null)
    }
}