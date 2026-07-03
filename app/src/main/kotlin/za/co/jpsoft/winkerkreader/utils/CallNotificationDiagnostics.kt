package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight, capped, opt-in log of VoIP notifications that could not be
 * classified by WhatsAppNotificationService's keyword/pattern matching.
 * Intended purely as a diagnostic aid for improving the matcher over time —
 * not shown to the pastor-user as part of normal call history.
 */
object CallNotificationDiagnostics {
    private const val PREF_KEY = "pref_unrecognized_call_samples"
    private const val MAX_SAMPLES = 20

    fun record(context: Context, appName: String, title: String, text: String, bigText: String, subText: String) {
        val settings = SettingsManager.getInstance(context)
        if (!settings.diagnosticCallCaptureEnabled) return

        val prefs = context.getSharedPreferences(WinkerkContractPrefsName(), Context.MODE_PRIVATE)
        val existing = JSONArray(prefs.getString(PREF_KEY, "[]"))

        val entry = JSONObject().apply {
            put("app", appName)
            put("time", System.currentTimeMillis())
            put("title", title)
            put("text", text)
            put("bigText", bigText)
            put("subText", subText)
        }

        val updated = JSONArray()
        // Keep newest MAX_SAMPLES entries only (drop oldest first).
        val start = maxOf(0, existing.length() - (MAX_SAMPLES - 1))
        for (i in start until existing.length()) updated.put(existing.get(i))
        updated.put(entry)

        prefs.edit().putString(PREF_KEY, updated.toString()).apply()
    }

    fun getSamples(context: Context): List<String> {
        val prefs = context.getSharedPreferences(WinkerkContractPrefsName(), Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(PREF_KEY, "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it).toString(2) }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(WinkerkContractPrefsName(), Context.MODE_PRIVATE)
            .edit().remove(PREF_KEY).apply()
    }

    private fun WinkerkContractPrefsName() =
        za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
}