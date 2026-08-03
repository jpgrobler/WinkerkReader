// AppInitializer.kt
package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.calllog.setup.CallLogDatabase
import za.co.jpsoft.winkerkreader.data.members.repository.ChurchInfoRepository
import za.co.jpsoft.winkerkreader.data.members.setup.DatabaseInitializer
import za.co.jpsoft.winkerkreader.services.CallMonitoringService
import za.co.jpsoft.winkerkreader.utils.telephony.ActiveCallReconciler
import za.co.jpsoft.winkerkreader.utils.work.WorkScheduler

object AppInitializer {

    private const val TAG = "AppInitializer"

    fun initialize(
        appContext: Context,
        scope: CoroutineScope,
        onProgress: ((progress: Int) -> Unit)? = null,
        onComplete: ((success: Boolean) -> Unit)? = null,
        onReady: (() -> Unit)? = null,
        churchInfoRepo: ChurchInfoRepository? = null,
        databaseInitializer: DatabaseInitializer,
        workScheduler: WorkScheduler,
        callLogImporter: CallLogImporter,
        autoStartEnabled: Boolean   // <-- added parameter
    ) {
        scope.launch {
            withContext(Dispatchers.IO) {
                // Call log import & reconciliation
                val callLogDb = CallLogDatabase.getInstance(appContext)
                callLogImporter.importIfNeeded(appContext, callLogDb.callLogDao())
                ActiveCallReconciler.reconcile(callLogDb.callLogDao())

                // ─── Database initialization using the injected initializer ───
                databaseInitializer.initializeDatabase(
                    context = appContext,
                    listener = object : DatabaseInitializer.ProgressListener {
                        override fun onProgressUpdate(progress: Int) {
                            onProgress?.invoke(progress)
                        }

                        override fun onInitializationComplete(success: Boolean) {
                            if (BuildConfig.DEBUG) {
                                if (success) Log.d(TAG, "Database initialised")
                                else Log.e(TAG, "Database initialisation failed")
                            }
                            onComplete?.invoke(success)
                            onReady?.invoke()
                        }
                    }
                )
            }

            // Load church info if repository is provided
            churchInfoRepo?.let {
                scope.launch {
                    it.loadChurchInfo()
                }
            }

            withContext(Dispatchers.Main) {
                // Use the passed autoStartEnabled flag instead of SettingsManager
                if (autoStartEnabled) {
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

            withContext(Dispatchers.Main) {
                workScheduler.scheduleAll()
            }
        }
    }

    fun initializeApp(
        appContext: Context,
        scope: CoroutineScope,
        churchInfoRepo: ChurchInfoRepository? = null,
        databaseInitializer: DatabaseInitializer,
        workScheduler: WorkScheduler,
        callLogImporter: CallLogImporter,
        autoStartEnabled: Boolean   // <-- added parameter
    ) {
        initialize(
            appContext,
            scope,
            null,
            null,
            null,
            churchInfoRepo,
            databaseInitializer,
            workScheduler,
            callLogImporter,
            autoStartEnabled
        )
    }
}