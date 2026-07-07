package za.co.jpsoft.winkerkreader

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import za.co.jpsoft.winkerkreader.utils.AppAuthState
import za.co.jpsoft.winkerkreader.utils.AppInitializer
import za.co.jpsoft.winkerkreader.utils.SettingsManager

class WinkerkReader : Application() {

    override fun onCreate() {
        super.onCreate()

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
    }
}