// utils/EncryptedPrefsManager.kt
package za.co.jpsoft.winkerkreader.utils.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages encrypted SharedPreferences for sensitive data (biometric settings,
 * Google Tasks script credentials, etc.).
 *
 * Uses the latest androidx.security:security-crypto API (1.1.0+).
 */
object EncryptedPrefsManager {

    private const val SECURE_PREFS_NAME = "WinkerkReader_SecurePrefs"

    @Volatile
    private var securePrefs: SharedPreferences? = null

    fun getSecurePrefs(context: Context): SharedPreferences {
        return securePrefs ?: synchronized(this) {
            securePrefs ?: run {
                // MasterKey.Builder is the recommended way in both old and new versions.
                // The KeyScheme.AES256_GCM is still the standard.
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    SECURE_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { securePrefs = it }
            }
        }
    }
}