package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {

    private const val LANGUAGE_PREF_KEY = "app_language"
    private const val DEFAULT_LANGUAGE = "af"  // Afrikaans

    /**
     * Set the app's locale based on stored language preference.
     * Call this in Application.attachBaseContext() and in Activity.onCreate().
     */
    fun setLocale(context: Context, languageCode: String? = null): Context {
        val code = languageCode ?: getPersistedLanguage(context)
        return updateResources(context, code)
    }

    fun getPersistedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("WinkerkReader_UserInfo", Context.MODE_PRIVATE)
        return prefs.getString(LANGUAGE_PREF_KEY, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    fun saveLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences("WinkerkReader_UserInfo", Context.MODE_PRIVATE)
        prefs.edit().putString(LANGUAGE_PREF_KEY, languageCode).apply()
        // Also set default locale for the process
        Locale.setDefault(Locale(languageCode))
    }

    private fun updateResources(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return context.createConfigurationContext(config)
    }
}