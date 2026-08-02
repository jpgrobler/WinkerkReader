package za.co.jpsoft.winkerkreader

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import za.co.jpsoft.winkerkreader.data.calllog.setup.CallLogDatabaseBackup
import za.co.jpsoft.winkerkreader.data.members.repository.ChurchInfoRepository
import za.co.jpsoft.winkerkreader.data.members.setup.DatabaseInitializer
import za.co.jpsoft.winkerkreader.utils.AppInitializer
import za.co.jpsoft.winkerkreader.utils.CallLogImporter
import za.co.jpsoft.winkerkreader.utils.db.PastoralDatabaseBackup
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs.ThemeMode
import za.co.jpsoft.winkerkreader.utils.prefs.BackupPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
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
open class WinkerkReader : Application() {

    private lateinit var leakCanaryHelper: LeakCanaryHelper

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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        PastoralDatabaseBackup.init(pastoralDbBackup)
        leakCanaryHelper = createLeakCanaryHelper()
        leakCanaryHelper.setup(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }

        // Apply theme
        when (appearancePrefs.themeMode) {
            ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        AppInitializer.initializeApp(
            appContext = this,
            scope = appScope,
            churchInfoRepo = churchInfoRepo,
            databaseInitializer = databaseInitializer,
            workScheduler = workScheduler,
            callLogImporter = callLogImporter,
            autoStartEnabled = callMonitorPrefs.autoStartEnabled
        )

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> AppAuthState.onAppBackgrounded()
                    else -> {}
                }
            }
        )

        WidgetDataRepository.init(widgetPrefs)
        CallLogDatabaseBackup.init(backupPrefs)
        PastoralWidgetDependencies.init(congregationPrefs)

        // ✅ Replace fragile Handler delays with WorkManager‑scheduled refresh
        scheduleWidgetRefreshWithDelay()
    }

    protected open fun createLeakCanaryHelper(): LeakCanaryHelper {
        return NoOpLeakCanaryHelper()
    }

    /**
     * Schedules a single widget refresh via WorkManager with a 30‑second delay.
     * WorkManager handles retries and respects device idle state.
     */
    private fun scheduleWidgetRefreshWithDelay() {
        try {
            // Use the existing WorkScheduler if it has a dedicated method,
            // or enqueue a one‑time work directly.
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