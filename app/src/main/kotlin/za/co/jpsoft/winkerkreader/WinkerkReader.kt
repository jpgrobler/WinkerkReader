package za.co.jpsoft.winkerkreader

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.data.calllog.setup.CallLogDatabaseBackup
import za.co.jpsoft.winkerkreader.data.members.repository.ChurchInfoRepository
import za.co.jpsoft.winkerkreader.data.members.setup.DatabaseInitializer
import za.co.jpsoft.winkerkreader.utils.AppInitializer
import za.co.jpsoft.winkerkreader.utils.AssetPhotoCopier
import za.co.jpsoft.winkerkreader.utils.CallLogImporter
import za.co.jpsoft.winkerkreader.utils.db.PastoralDatabaseBackup
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs.ThemeMode
import za.co.jpsoft.winkerkreader.utils.prefs.BackupPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.SyncPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.WidgetPrefs
import za.co.jpsoft.winkerkreader.utils.security.AppAuthState
import za.co.jpsoft.winkerkreader.utils.widget.PastoralWidgetDependencies
import za.co.jpsoft.winkerkreader.utils.work.WorkScheduler
import za.co.jpsoft.winkerkreader.widget.WidgetDataRepository
import java.util.concurrent.TimeUnit

interface LeakCanaryHelper {
    fun setup(application: Application)
}

class NoOpLeakCanaryHelper : LeakCanaryHelper {
    override fun setup(application: Application) {
        // Intentionally empty – no LeakCanary in release.
    }
}

@HiltAndroidApp
open class WinkerkReader : Application(), Configuration.Provider {

    private lateinit var leakCanaryHelper: LeakCanaryHelper

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // ─── WorkManager configuration (property override) ───
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @Inject
    lateinit var widgetPrefs: WidgetPrefs
    @Inject
    lateinit var backupPrefs: BackupPrefs
    @Inject
    lateinit var congregationPrefs: CongregationPrefs
    @Inject
    lateinit var churchInfoRepo: ChurchInfoRepository
    @Inject
    lateinit var databaseInitializer: DatabaseInitializer
    @Inject
    lateinit var workScheduler: WorkScheduler
    @Inject
    lateinit var callLogImporter: CallLogImporter
    @Inject
    lateinit var pastoralDbBackup: PastoralDatabaseBackup
    @Inject
    lateinit var appearancePrefs: AppearancePrefs
    @Inject
    lateinit var callMonitorPrefs: CallMonitorPrefs

    @Inject
    lateinit var syncPrefs: SyncPrefs

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Force WorkManager to use our Hilt factory – overrides any early default init
        try {
            WorkManager.initialize(
                this, Configuration.Builder()
                    .setWorkerFactory(workerFactory)
                    .build()
            )
        } catch (e: IllegalStateException) {
            // If already initialized (should not happen after provider removal), swallow
            if (BuildConfig.DEBUG) Log.w(
                "WinkerkReader",
                "WorkManager already initialized, using existing"
            )
        }

        PastoralDatabaseBackup.init(pastoralDbBackup)
        leakCanaryHelper = createLeakCanaryHelper()
        leakCanaryHelper.setup(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val options = DynamicColorsOptions.Builder()
                .setPrecondition { _, _ -> appearancePrefs.dynamicColorEnabled }
                .build()
            DynamicColors.applyToActivitiesIfAvailable(this, options)
        }

        // Apply theme
        when (appearancePrefs.themeMode) {
            ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        AppInitializer.initialize(
            appContext = this,
            scope = appScope,
            churchInfoRepo = churchInfoRepo,
            databaseInitializer = databaseInitializer,
            workScheduler = workScheduler,
            callLogImporter = callLogImporter,
            autoStartEnabled = callMonitorPrefs.autoStartEnabled,
            onReady = {
                // Only start once DB init has genuinely completed — not racing it
                appScope.launch(Dispatchers.IO) {
                    AssetPhotoCopier.copyPhotosIfNeeded(this@WinkerkReader, syncPrefs)
                }
            }
        )

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> AppAuthState.onAppBackgrounded()
                    Lifecycle.Event.ON_START -> AppAuthState.onAppForegrounded()
                    else -> {}
                }
            }
        )
        congregationPrefs.ensureDefaultColors()
        WidgetDataRepository.init(widgetPrefs)
        CallLogDatabaseBackup.init(backupPrefs)
        PastoralWidgetDependencies.init(congregationPrefs)

        scheduleWidgetRefreshWithDelay()
    }

    protected open fun createLeakCanaryHelper(): LeakCanaryHelper {
        return NoOpLeakCanaryHelper()
    }

    private fun scheduleWidgetRefreshWithDelay() {
        try {
            if (BuildConfig.DEBUG) Log.d(
                "WinkerkReader",
                "Scheduling widget refresh with 30s delay"
            )
            workScheduler.scheduleWidgetRefresh(initialDelay = 30, unit = TimeUnit.SECONDS)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("WinkerkReader", "Failed to schedule widget refresh", e)
        }
    }
}