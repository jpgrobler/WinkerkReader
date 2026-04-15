package za.co.jpsoft.winkerkreader.utils


import android.content.Context
import android.content.SharedPreferences
import za.co.jpsoft.winkerkreader.data.WinkerkContract


/**
 * Central manager for all app preferences.
 * All preference keys are defined in [WinkerkContract].
 */
class SettingsManager(context: Context) {

    fun getBirthdayMessage(): String = prefs.getString("VerjaarBoodskap", "") ?: ""

    fun setBirthdayMessage(value: String) = prefs.edit().putString("VerjaarBoodskap", value).apply()


    var voipLogEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.winkerkEntry.KEY_LOG_VOIP, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.winkerkEntry.KEY_LOG_VOIP, value).apply()

    private val prefs: SharedPreferences = context.getSharedPreferences(
        WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE
    )

    // ===== Display settings =====
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

    // ===== Function settings =====
    var autoStartEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_AUTOSTART, false)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_AUTOSTART, value).apply()

    var callMonitorEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_OPROEPMONITOR, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_OPROEPMONITOR, value).apply()

    var callLogEnabled: Boolean
        get() = prefs.getBoolean(WinkerkContract.KEY_OPROEPLOG, true)
        set(value) = prefs.edit().putBoolean(WinkerkContract.KEY_OPROEPLOG, value).apply()

    var defLayout: String
        get() = prefs.getString(WinkerkContract.KEY_DEFLAYOUT, "GESINNE") ?: "GESINNE"
        set(value) = prefs.edit().putString(WinkerkContract.KEY_DEFLAYOUT, value).apply()

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

    var selectedCalendarId: Long
        get() = prefs.getLong(WinkerkContract.KEY_SELECTED_CALENDAR_ID, -1L)
        set(value) = prefs.edit().putLong(WinkerkContract.KEY_SELECTED_CALENDAR_ID, value).apply()

    // ===== Widget settings =====
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

    // ===== Color settings =====
    var gemeenteKleur: Int
        get() = prefs.getInt(WinkerkContract.KEY_GEMEENTE_KLEUR, -1)
        set(value) = prefs.edit().putInt(WinkerkContract.KEY_GEMEENTE_KLEUR, value).apply()

    var gemeente2Kleur: Int
        get() = prefs.getInt(WinkerkContract.KEY_GEMEENTE2_KLEUR, -3355444)
        set(value) = prefs.edit().putInt(WinkerkContract.KEY_GEMEENTE2_KLEUR, value).apply()

    var gemeente3Kleur: Int
        get() = prefs.getInt(WinkerkContract.KEY_GEMEENTE3_KLEUR, -256)
        set(value) = prefs.edit().putInt(WinkerkContract.KEY_GEMEENTE3_KLEUR, value).apply()

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

    var dataDatum: String
        get() = prefs.getString("DATA_DATUM", "") ?: ""
        set(value) = prefs.edit().putString("DATA_DATUM", value).apply()

    // ===== SMS reminder settings =====
    var autoSms: Boolean
        get() = prefs.getBoolean("AUTO_SMS", false)
        set(value) = prefs.edit().putBoolean("AUTO_SMS", value).apply()

    var herinner: Boolean
        get() = prefs.getBoolean("HERINNER", false)
        set(value) = prefs.edit().putBoolean("HERINNER", value).apply()

    var smsHour: String
        get() = prefs.getString("SMS-HOUR", "08") ?: "08"
        set(value) = prefs.edit().putString("SMS-HOUR", value).apply()

    var smsMinute: String
        get() = prefs.getString("SMS-MINUTE", "00") ?: "00"
        set(value) = prefs.edit().putString("SMS-MINUTE", value).apply()

    var smsTimeUpdate: Boolean
        get() = prefs.getBoolean("SMS-TIMEUPDATE", false)
        set(value) = prefs.edit().putBoolean("SMS-TIMEUPDATE", value).apply()

    // ===== Auto download settings =====
    var autoDl: Boolean
        get() = prefs.getBoolean("AUTO_DL", false)
        set(value) = prefs.edit().putBoolean("AUTO_DL", value).apply()

    var dlHour: String
        get() = prefs.getString("DL-HOUR", "08") ?: "08"
        set(value) = prefs.edit().putString("DL-HOUR", value).apply()

    var dlMinute: String
        get() = prefs.getString("DL-MINUTE", "00") ?: "00"
        set(value) = prefs.edit().putString("DL-MINUTE", value).apply()

    var dlDay: Int
        get() = prefs.getInt("DL-DAY", 6)
        set(value) = prefs.edit().putInt("DL-DAY", value).apply()

    var dlTimeUpdate: Boolean
        get() = prefs.getBoolean("DL-TIMEUPDATE", false)
        set(value) = prefs.edit().putBoolean("DL-TIMEUPDATE", value).apply()

    // ===== Other flags =====
    var fromMenu: Boolean
        get() = prefs.getBoolean("FROM_MENU", false)
        set(value) = prefs.edit().putBoolean("FROM_MENU", value).apply()
}