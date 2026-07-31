package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences
import za.co.jpsoft.winkerkreader.data.WinkerkContract

class MemberListPrefs(private val prefs: SharedPreferences) {

    var isListFoto: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_FOTO, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_FOTO, value).apply()

    var isListEpos: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_EPOS, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_EPOS, value).apply()

    var isListWhatsapp: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_WHATSAPP, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_WHATSAPP, value).apply()

    var isListVerjaarBlok: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_VERJAARBLOK, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_VERJAARBLOK, value).apply()

    var isListOuderdom: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_OUDERDOM, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_OUDERDOM, value).apply()

    var isListHuwelikBlok: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_HUWELIKBLOK, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_HUWELIKBLOK, value).apply()

    var isListWyk: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_WYK, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_WYK, value).apply()

    var isListSelfoon: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_SELFOON, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_SELFOON, value).apply()

    var isListTelefoon: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_LIST_TELEFOON, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_LIST_TELEFOON, value).apply()

    var defLayout: String
        get() = prefs.getString(WinkerkContract.KEY_DEFLAYOUT, "GESINNE") ?: "GESINNE"
        set(value) = prefs.edit().putString(WinkerkContract.KEY_DEFLAYOUT, value).apply()

    var listView: Int
        get() = prefs.getInt("LIST_VIEW", 2)
        set(value) = prefs.edit().putInt("LIST_VIEW", value).apply()

    var groepView: Int
        get() = prefs.getInt("GROEP_VIEW", 500) // WkrContract.winkerkEntry.GROEPLIST_LOADER
        set(value) = prefs.edit().putInt("GROEP_VIEW", value).apply()

    var fromMenu: Boolean
        get() = prefs.getBoolean("FROM_MENU", false)
        set(value) = prefs.edit().putBoolean("FROM_MENU", value).apply()
}