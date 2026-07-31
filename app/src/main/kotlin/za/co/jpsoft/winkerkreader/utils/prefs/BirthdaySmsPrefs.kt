package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences

class BirthdaySmsPrefs(private val prefs: SharedPreferences) {

    fun getBirthdayMessage(): String = prefs.getString("VerjaarBoodskap", "") ?: ""
    fun setBirthdayMessage(value: String) = prefs.edit().putString("VerjaarBoodskap", value).apply()

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
}