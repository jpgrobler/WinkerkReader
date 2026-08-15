package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences

class LanguagePrefs(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_LANGUAGE = "app_language"
        const val DEFAULT_LANGUAGE = "af"  // Afrikaans
    }

    var languageCode: String
        get() = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    fun isAfrikaans(): Boolean = languageCode == "af"
    fun isEnglish(): Boolean = languageCode == "en"
}