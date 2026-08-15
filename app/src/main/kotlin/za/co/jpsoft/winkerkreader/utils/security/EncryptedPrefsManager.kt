package za.co.jpsoft.winkerkreader.utils.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.harrytmthy.safebox.SafeBox
import za.co.jpsoft.winkerkreader.BuildConfig

object EncryptedPrefsManager {

    private const val SECURE_PREFS_NAME = "WinkerkReader_SecurePrefs"
    private const val TAG = "EncryptedPrefsManager"

    @Volatile
    private var securePrefs: SharedPreferences? = null

    @Volatile
    private var encryptionAvailable = true

    @JvmStatic
    fun getSecurePrefs(context: Context): SharedPreferences {
        securePrefs?.let { return it }

        return synchronized(this) {
            securePrefs?.let { return it }

            val result = try {
                val prefs = SafeBox.create(context.applicationContext, SECURE_PREFS_NAME)
                encryptionAvailable = true
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "SafeBox SharedPreferences created successfully.")
                }
                prefs
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to create SafeBox SharedPreferences", e)
                encryptionAvailable = false
                createFallbackPrefs(context)
            }

            securePrefs = result
            return result
        }
    }

    private fun createFallbackPrefs(context: Context): SharedPreferences {
        encryptionAvailable = false
        val fallbackName = "${SECURE_PREFS_NAME}_fallback"
        val fallback =
            context.applicationContext.getSharedPreferences(fallbackName, Context.MODE_PRIVATE)
        fallback.edit()
            .putBoolean("app_biometric_enabled", false)
            .putBoolean("app_lock_on_restart", false)
            .apply()
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "Using plain SharedPreferences fallback. Security features disabled.")
        }
        return fallback
    }

    fun isEncryptionAvailable(): Boolean = encryptionAvailable

    /**
     * Optional: Call this ONCE on app startup before using EncryptedPrefsManager
     * to migrate data from old EncryptedSharedPreferences to SafeBox.
     *
     * Only needed if you must preserve user preferences across the update.
     * If data loss is acceptable, skip this and leave it commented out.
     *
     * Usage example in Application or MainActivity:
     *   EncryptedPrefsManager.attemptMigration(context)
     */
    @JvmStatic
    fun attemptMigration(context: Context) {
        try {
            val oldPrefsName = "WinkerkReader_SecurePrefs"
            val oldPrefs = try {
                // Try to access old encrypted prefs
                context.applicationContext.getSharedPreferences(
                    "${oldPrefsName}_old",
                    Context.MODE_PRIVATE
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Old prefs unavailable or corrupted")
                null
            }

            val newPrefs = getSecurePrefs(context)

            oldPrefs?.let { old ->
                newPrefs.edit().apply {
                    old.all.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Int -> putInt(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Float -> putFloat(key, value)
                            is Long -> putLong(key, value)
                            is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(
                                key,
                                value as Set<String>
                            )
                        }
                    }
                }.apply()
                if (BuildConfig.DEBUG) Log.i(TAG, "Migration from old prefs complete")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Migration failed", e)
        }
    }
}