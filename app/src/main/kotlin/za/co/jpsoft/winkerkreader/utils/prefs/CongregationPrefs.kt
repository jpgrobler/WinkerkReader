package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract

class CongregationPrefs(
    private val prefs: SharedPreferences,
    private val context: Context   // applicationContext only – no Activity leak
) {

    var gemeenteNaam: String
        get() = prefs.getString("Gemeente", "") ?: ""
        set(value) = prefs.edit().putString("Gemeente", value).apply()

    var gemeenteEpos: String
        get() = prefs.getString("Gemeente_Epos", "") ?: ""
        set(value) = prefs.edit().putString("Gemeente_Epos", value).apply()

    var gemeente2Naam: String
        get() = prefs.getString("Gemeente2", "") ?: ""
        set(value) = prefs.edit().putString("Gemeente2", value).apply()

    var gemeente2Epos: String
        get() = prefs.getString("Gemeente2_Epos", "") ?: ""
        set(value) = prefs.edit().putString("Gemeente2_Epos", value).apply()

    var gemeente3Naam: String
        get() = prefs.getString("Gemeente3", "") ?: ""
        set(value) = prefs.edit().putString("Gemeente3", value).apply()

    var gemeente3Epos: String
        get() = prefs.getString("Gemeente3_Epos", "") ?: ""
        set(value) = prefs.edit().putString("Gemeente3_Epos", value).apply()

    var gemeenteKleur: Int
        get() = prefs.getInt(WinkerkContract.KEY_GEMEENTE_KLEUR, Int.MIN_VALUE)
        set(value) = prefs.edit().putInt(WinkerkContract.KEY_GEMEENTE_KLEUR, value).apply()

    var gemeente2Kleur: Int
        get() = prefs.getInt(WinkerkContract.KEY_GEMEENTE2_KLEUR, Int.MIN_VALUE)
        set(value) = prefs.edit().putInt(WinkerkContract.KEY_GEMEENTE2_KLEUR, value).apply()

    var gemeente3Kleur: Int
        get() = prefs.getInt(WinkerkContract.KEY_GEMEENTE3_KLEUR, Int.MIN_VALUE)
        set(value) = prefs.edit().putInt(WinkerkContract.KEY_GEMEENTE3_KLEUR, value).apply()

    var inactiveBackgroundColor: Int
        get() = prefs.getInt(WinkerkContract.KEY_INACTIVE_BG_COLOR, Int.MIN_VALUE)
        set(value) = prefs.edit().putInt(WinkerkContract.KEY_INACTIVE_BG_COLOR, value).apply()

    var dataDatum: String
        get() = prefs.getString("DATA_DATUM", "") ?: ""
        set(value) = prefs.edit().putString("DATA_DATUM", value).apply()

    var useCongregationIndicator: Boolean
        get() = prefs.getBoolean("use_congregation_indicator", false)
        set(value) = prefs.edit().putBoolean("use_congregation_indicator", value).apply()

    // Called once during app startup – ensure defaults are set
    fun ensureDefaultColors() {
        if (gemeenteKleur == Int.MIN_VALUE) {
            gemeenteKleur = ContextCompat.getColor(context, R.color.default_gemeente_1)
        }
        if (gemeente2Kleur == Int.MIN_VALUE) {
            gemeente2Kleur = ContextCompat.getColor(context, R.color.default_gemeente_2)
        }
        if (gemeente3Kleur == Int.MIN_VALUE) {
            gemeente3Kleur = ContextCompat.getColor(context, R.color.default_gemeente_3)
        }
        if (inactiveBackgroundColor == Int.MIN_VALUE) {
            inactiveBackgroundColor = ContextCompat.getColor(context, R.color.inactive_background)
        }
    }
}