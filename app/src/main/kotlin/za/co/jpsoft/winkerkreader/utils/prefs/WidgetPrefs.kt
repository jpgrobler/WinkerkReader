package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract

class WidgetPrefs(private val prefs: SharedPreferences) {

    var widgetDoop: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WIDGET_DOOP, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WIDGET_DOOP, value).apply()

    var widgetBelydenis: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WIDGET_BELYDENIS, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WIDGET_BELYDENIS, value).apply()

    var widgetHuwelik: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WIDGET_HUWELIK, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WIDGET_HUWELIK, value).apply()

    var widgetSterf: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_WIDGET_STERF, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_WIDGET_STERF, value).apply()
}