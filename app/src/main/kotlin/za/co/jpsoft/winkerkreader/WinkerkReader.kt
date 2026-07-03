package za.co.jpsoft.winkerkreader

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import za.co.jpsoft.winkerkreader.utils.AppInitializer
import za.co.jpsoft.winkerkreader.utils.SettingsManager

class WinkerkReader : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        // One‑time app initialisation
        // In WinkerkReader.kt (Application class) or MainActivity.onCreate()
        val settingsManager = SettingsManager.getInstance(this)
        when (settingsManager.themeMode) {
            SettingsManager.ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            SettingsManager.ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            SettingsManager.ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        AppInitializer.initializeApp(this)
    }
}