package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs.ThemeMode

class AppearancePrefs(private val prefs: SharedPreferences) {

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    var themeMode: ThemeMode
        get() {
            val value = prefs.getString("theme_mode", "light")
            return when (value) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }
        set(value) = prefs.edit().putString("theme_mode", value.name.lowercase()).apply()

    var whatsapp1: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WHATSAPP1, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WHATSAPP1, value).apply()

    var whatsapp2: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WHATSAPP2, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WHATSAPP2, value).apply()

    var whatsapp3: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WHATSAPP3, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WHATSAPP3, value).apply()

    var eposHtml: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_EPOSHTML, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_EPOSHTML, value).apply()
}