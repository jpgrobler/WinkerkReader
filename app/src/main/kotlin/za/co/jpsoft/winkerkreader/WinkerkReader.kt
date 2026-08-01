package za.co.jpsoft.winkerkreader

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import za.co.jpsoft.winkerkreader.data.DatabaseInitializer
import za.co.jpsoft.winkerkreader.data.calllog.CallLogDatabaseBackup
import za.co.jpsoft.winkerkreader.data.repositories.ChurchInfoRepository
import za.co.jpsoft.winkerkreader.utils.*
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs.ThemeMode
import za.co.jpsoft.winkerkreader.utils.prefs.BackupPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.WidgetPrefs
import za.co.jpsoft.winkerkreader.utils.widget.PastoralWidgetDependencies
import za.co.jpsoft.winkerkreader.widget.PastoralWidgetProvider
import za.co.jpsoft.winkerkreader.widget.WidgetDataRepository
import za.co.jpsoft.winkerkreader.widget.WinkerkReaderWidgetProvider

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
    lateinit var appearancePrefs: AppearancePrefs       // <-- added
    @Inject
    lateinit var callMonitorPrefs: CallMonitorPrefs     // <-- added

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        PastoralDatabaseBackup.init(pastoralDbBackup)
        leakCanaryHelper = createLeakCanaryHelper()
        leakCanaryHelper.setup(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }

        // Use injected appearancePrefs directly
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
            autoStartEnabled = callMonitorPrefs.autoStartEnabled   // <-- pass from injected prefs
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

        refreshWidgetsOnStartup()
    }

    protected open fun createLeakCanaryHelper(): LeakCanaryHelper {
        return NoOpLeakCanaryHelper()
    }

    private fun refreshWidgetsOnStartup() {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (BuildConfig.DEBUG) Log.d("WinkerkReader", "🔄 First widget refresh attempt")
                WinkerkReaderWidgetProvider.updateAllWidgets(this)
                PastoralWidgetProvider.forceRefreshWidgets(this)
                WidgetDataRepository.invalidateCache()
                WidgetDataRepository.refreshCache(this)
                if (BuildConfig.DEBUG) Log.d(
                    "WinkerkReader",
                    "✅ Widgets refreshed on startup (attempt 1)"
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(
                    "WinkerkReader",
                    "Failed to refresh widgets on startup",
                    e
                )
            }
        }, 1000)

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (BuildConfig.DEBUG) Log.d("WinkerkReader", "🔄 Second widget refresh attempt")
                PastoralWidgetProvider.forceRefreshWidgets(this)
                if (BuildConfig.DEBUG) Log.d(
                    "WinkerkReader",
                    "✅ Widgets refreshed on startup (attempt 2)"
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(
                    "WinkerkReader",
                    "Failed second widget refresh attempt",
                    e
                )
            }
        }, 3000)
    }
}