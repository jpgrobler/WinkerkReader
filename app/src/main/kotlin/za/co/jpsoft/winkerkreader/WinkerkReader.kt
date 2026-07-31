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
import za.co.jpsoft.winkerkreader.utils.AppAuthState
import za.co.jpsoft.winkerkreader.utils.AppInitializer
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.widget.PastoralWidgetProvider
import za.co.jpsoft.winkerkreader.widget.WidgetDataRepository
import za.co.jpsoft.winkerkreader.widget.WinkerkReaderWidgetProvider
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs.ThemeMode
/**
 * Interface for LeakCanary setup – implemented in debug builds only.
 * Release builds use the no‑op implementation.
 */
interface LeakCanaryHelper {
    fun setup(application: Application)
}

/**
 * No‑operation helper used in release builds.
 */
class NoOpLeakCanaryHelper : LeakCanaryHelper {
    override fun setup(application: Application) {
        // Intentionally empty – no LeakCanary in release.
    }
}

/**
 * Main Application class.
 * The debug variant will substitute a subclass that provides the real LeakCanary setup.
 */
open class WinkerkReader : Application() {

    // Will be set in onCreate; uses the helper to avoid direct LeakCanary references.
    private lateinit var leakCanaryHelper: LeakCanaryHelper

    @OptIn(ExperimentalStdlibApi::class)
    override fun onCreate() {
        super.onCreate()

        // Obtain the appropriate helper (overridden in debug source set)
        leakCanaryHelper = createLeakCanaryHelper()
        leakCanaryHelper.setup(this)

        // Apply dynamic colors on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }

        // Theme setup
        val settingsManager = SettingsManager.getInstance(this)
        when (settingsManager.appearance.themeMode) {
            ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        AppInitializer.initializeApp(this)

        // Register foreground/background listener
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        // App went to background
                        AppAuthState.onAppBackgrounded()
                    }
                    Lifecycle.Event.ON_START -> {
                        // App returns to foreground – the check will happen in MainActivity.onResume
                        // Nothing to do here, but you could log if needed.
                    }
                    else -> {}
                }
            }
        )
        refreshWidgetsOnStartup()
    }

    /**
     * Factory method for the LeakCanary helper.
     * Overridden in the debug source set to return a real implementation.
     */
    protected open fun createLeakCanaryHelper(): LeakCanaryHelper {
        return NoOpLeakCanaryHelper()
    }

    private fun refreshWidgetsOnStartup() {
        // First attempt after 1 second
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (BuildConfig.DEBUG) Log.d("WinkerkReader", "🔄 First widget refresh attempt")

                WinkerkReaderWidgetProvider.updateAllWidgets(this)
                PastoralWidgetProvider.forceRefreshWidgets(this)

                WidgetDataRepository.invalidateCache()
                WidgetDataRepository.refreshCache(this)

                if (BuildConfig.DEBUG) {
                    Log.d("WinkerkReader", "✅ Widgets refreshed on startup (attempt 1)")
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(
                    "WinkerkReader",
                    "Failed to refresh widgets on startup",
                    e
                )
            }
        }, 1000)

        // Second attempt after 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (BuildConfig.DEBUG) Log.d("WinkerkReader", "🔄 Second widget refresh attempt")

                PastoralWidgetProvider.forceRefreshWidgets(this)

                if (BuildConfig.DEBUG) {
                    Log.d("WinkerkReader", "✅ Widgets refreshed on startup (attempt 2)")
                }
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