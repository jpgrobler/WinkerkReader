package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.utils.prefs.TasksPrefs.GoogleTasksMode

class TasksPrefs(
    private val prefs: SharedPreferences,
    private val securePrefs: SharedPreferences
) {

    enum class GoogleTasksMode { OFF, API, SHARE }

    fun googleTasksMode(): GoogleTasksMode {
        val stored = prefs.getString(WinkerkContract.KEY_GOOGLE_TASKS_MODE, "off")
        return when (stored) {
            "api" -> GoogleTasksMode.API
            "share" -> GoogleTasksMode.SHARE
            else -> GoogleTasksMode.OFF
        }
    }

    fun setGoogleTasksMode(mode: GoogleTasksMode) {
        prefs.edit().putString(
            WinkerkContract.KEY_GOOGLE_TASKS_MODE,
            mode.name.lowercase()
        ).apply()
    }

    var googleTasksListId: String?
        get() = prefs.getString(WinkerkContract.KEY_GOOGLE_TASKS_LIST_ID, null)
        set(value) = prefs.edit().putString(WinkerkContract.KEY_GOOGLE_TASKS_LIST_ID, value).apply()

    var googleTasksAccountEmail: String?
        get() = prefs.getString(WinkerkContract.KEY_GOOGLE_TASKS_ACCOUNT, null)
        set(value) = prefs.edit().putString(WinkerkContract.KEY_GOOGLE_TASKS_ACCOUNT, value).apply()

    // ─── Secure (encrypted) ───────────────────────────────────────────────────

    var tasksScriptUrl: String?
        get() {
            migrateToSecure(WinkerkContract.KEY_TASKS_SCRIPT_URL)
            return securePrefs.getString(WinkerkContract.KEY_TASKS_SCRIPT_URL, null)
        }
        set(value) = securePrefs.edit()
            .putString(WinkerkContract.KEY_TASKS_SCRIPT_URL, value?.trim()).apply()

    var tasksScriptSecret: String?
        get() {
            migrateToSecure(WinkerkContract.KEY_TASKS_SCRIPT_SECRET)
            return securePrefs.getString(WinkerkContract.KEY_TASKS_SCRIPT_SECRET, null)
        }
        set(value) = securePrefs.edit()
            .putString(WinkerkContract.KEY_TASKS_SCRIPT_SECRET, value?.trim()).apply()

    fun isTasksScriptConfigured(): Boolean =
        !tasksScriptUrl.isNullOrBlank() && !tasksScriptSecret.isNullOrBlank()

    // ─── Migration ────────────────────────────────────────────────────────────

    private fun migrateToSecure(key: String) {
        if (prefs.contains(key) && !securePrefs.contains(key)) {
            val value = prefs.getString(key, null)
            if (value != null) {
                securePrefs.edit().putString(key, value).commit()
                prefs.edit().remove(key).commit()
            }
        }
    }
}