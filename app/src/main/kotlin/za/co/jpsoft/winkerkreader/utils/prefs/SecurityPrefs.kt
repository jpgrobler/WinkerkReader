package za.co.jpsoft.winkerkreader.utils.prefs

import android.content.SharedPreferences

class SecurityPrefs(
    private val prefs: SharedPreferences,
    private val securePrefs: SharedPreferences
) {
    var lockOnRestart: Boolean
        get() = securePrefs.getBoolean("app_lock_on_restart", true)   // default true
        set(value) = securePrefs.edit().putBoolean("app_lock_on_restart", value).apply()

    var biometricEnabled: Boolean
        get() {
            migrateBooleanToSecure("app_biometric_enabled")
            return securePrefs.getBoolean("app_biometric_enabled", false)
        }
        set(value) = securePrefs.edit().putBoolean("app_biometric_enabled", value).apply()

    var biometricTimeoutMs: Long
        get() {
            migrateLongToSecure("app_biometric_timeout_ms")
            return securePrefs.getLong("app_biometric_timeout_ms", Long.MAX_VALUE)
        }
        set(value) = securePrefs.edit().putLong("app_biometric_timeout_ms", value).apply()

    // ─── Migration helpers ────────────────────────────────────────────────────

    private fun migrateBooleanToSecure(key: String) {
        if (prefs.contains(key) && !securePrefs.contains(key)) {
            securePrefs.edit().putBoolean(key, prefs.getBoolean(key, false)).apply()
            prefs.edit().remove(key).apply()
        }
    }

    private fun migrateLongToSecure(key: String) {
        if (prefs.contains(key) && !securePrefs.contains(key)) {
            securePrefs.edit().putLong(key, prefs.getLong(key, Long.MAX_VALUE)).apply()
            prefs.edit().remove(key).apply()
        }
    }
}