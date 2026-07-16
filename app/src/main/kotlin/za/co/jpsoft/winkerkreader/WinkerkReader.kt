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
import leakcanary.LeakCanary
import shark.AndroidReferenceMatchers
import za.co.jpsoft.winkerkreader.utils.AppAuthState
import za.co.jpsoft.winkerkreader.utils.AppInitializer
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.widget.PastoralWidgetProvider
import za.co.jpsoft.winkerkreader.widget.WidgetDataRepository
import za.co.jpsoft.winkerkreader.widget.WinkerkReaderWidgetProvider

class WinkerkReader : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            LeakCanary.config = LeakCanary.config.copy(
                referenceMatchers = AndroidReferenceMatchers.appDefaults +
                        AndroidReferenceMatchers.ignoredInstanceField(
                            "android.service.notification.NotificationListenerService\$NotificationListenerWrapper",
                            "this\$0"
                        )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }

        // Theme setup
        val settingsManager = SettingsManager.getInstance(this)
        when (settingsManager.themeMode) {
            SettingsManager.ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            SettingsManager.ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            SettingsManager.ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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

    private fun refreshWidgetsOnStartup() {
        // ✅ Use multiple delays to ensure widget loads
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (BuildConfig.DEBUG) Log.d("WinkerkReader", "🔄 First widget refresh attempt")

                // Refresh birthday widget
                WinkerkReaderWidgetProvider.updateAllWidgets(this)

                // Refresh pastoral widget - force reload
                PastoralWidgetProvider.forceRefreshWidgets(this)

                // Refresh data cache for birthday widget
                WidgetDataRepository.invalidateCache()
                WidgetDataRepository.refreshCache(this)

                if (BuildConfig.DEBUG) {
                    Log.d("WinkerkReader", "✅ Widgets refreshed on startup (attempt 1)")
                }
            } catch (e: Exception) {
                Log.e("WinkerkReader", "Failed to refresh widgets on startup", e)
            }
        }, 1000) // First attempt after 1 second

        // ✅ Second attempt after 3 seconds (to ensure database is ready)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (BuildConfig.DEBUG) Log.d("WinkerkReader", "🔄 Second widget refresh attempt")

                // Force pastoral widget refresh again
                PastoralWidgetProvider.forceRefreshWidgets(this)

                if (BuildConfig.DEBUG) {
                    Log.d("WinkerkReader", "✅ Widgets refreshed on startup (attempt 2)")
                }
            } catch (e: Exception) {
                Log.e("WinkerkReader", "Failed second widget refresh attempt", e)
            }
        }, 3000) // Second attempt after 3 seconds
    }
}