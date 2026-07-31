package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences

class QuickActionPrefs(private val prefs: SharedPreferences) {

    var showQuickActionBar: Boolean
        get() = prefs.getBoolean("showQuickActionBar", true)
        set(value) = prefs.edit().putBoolean("showQuickActionBar", value).apply()

    var quickActionDetail: Boolean
        get() = prefs.getBoolean("quick_action_detail", true)
        set(value) = prefs.edit().putBoolean("quick_action_detail", value).apply()

    var quickActionSms: Boolean
        get() = prefs.getBoolean("quick_action_sms", false)
        set(value) = prefs.edit().putBoolean("quick_action_sms", value).apply()

    var quickActionWhatsApp: Boolean
        get() = prefs.getBoolean("quick_action_whatsapp", true)
        set(value) = prefs.edit().putBoolean("quick_action_whatsapp", value).apply()

    var quickActionCall: Boolean
        get() = prefs.getBoolean("quick_action_call", false)
        set(value) = prefs.edit().putBoolean("quick_action_call", value).apply()

    var quickActionEmail: Boolean
        get() = prefs.getBoolean("quick_action_email", false)
        set(value) = prefs.edit().putBoolean("quick_action_email", value).apply()

    var quickActionLandline: Boolean
        get() = prefs.getBoolean("quick_action_landline", false)
        set(value) = prefs.edit().putBoolean("quick_action_landline", value).apply()

    var quickActionNote: Boolean
        get() = prefs.getBoolean("quick_action_note", true)
        set(value) = prefs.edit().putBoolean("quick_action_note", value).apply()

    var quickActionReminder: Boolean
        get() = prefs.getBoolean("quick_action_reminder", true)
        set(value) = prefs.edit().putBoolean("quick_action_reminder", value).apply()

    var quickActionCopy: Boolean
        get() = prefs.getBoolean("quick_action_copy", false)
        set(value) = prefs.edit().putBoolean("quick_action_copy", value).apply()

    var quickActionCopyContacts: Boolean
        get() = prefs.getBoolean("quick_action_copy_contacts", false)
        set(value) = prefs.edit().putBoolean("quick_action_copy_contacts", value).apply()

    var quickActionMore: Boolean
        get() = prefs.getBoolean("quick_action_more", true)
        set(value) = prefs.edit().putBoolean("quick_action_more", value).apply()
}