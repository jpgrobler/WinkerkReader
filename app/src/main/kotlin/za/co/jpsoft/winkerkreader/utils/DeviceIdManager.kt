package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.util.UUID

/**
 * DeviceIdManager - Privacy-compliant device identification
 *
 * This class provides a stable device identifier that:
 * - Persists across app launches
 * - Resets only on app reinstall (respects user privacy)
 * - Doesn't require special permissions
 * - Is compliant with Google Play policies
 *
 * IMPORTANT: This ID is app-specific and resets on uninstall/reinstall.
 * This is by design for user privacy.
 */
object DeviceIdManager {
    private const val TAG = "DeviceIdManager"
    private const val PREFS_NAME = "device_id_prefs"
    private const val PREF_DEVICE_ID = "device_id"

    private var cachedDeviceId: String? = null

    /**
     * Get a stable device identifier for this app installation
     *
     * This method:
     * 1. First checks memory cache
     * 2. Then checks SharedPreferences
     * 3. Finally generates a new UUID if needed
     *
     * @param context Application context
     * @return A stable UUID string for this app installation
     */
    @JvmStatic
    fun getDeviceId(context: Context): String {
        // Return cached value if available
        if (!cachedDeviceId.isNullOrEmpty()) {
            return cachedDeviceId!!
        }

        // Get from SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cachedDeviceId = prefs.getString(PREF_DEVICE_ID, null)

        // Generate new ID if not found
        if (cachedDeviceId.isNullOrEmpty()) {
            cachedDeviceId = generateNewDeviceId()

            // Save to SharedPreferences
            prefs.edit()
                .putString(PREF_DEVICE_ID, cachedDeviceId)
                .apply()

            Log.i(TAG, "Generated new device ID")
        } else {
            Log.d(TAG, "Retrieved existing device ID")
        }

        return cachedDeviceId!!
    }

    /**
     * Generate a new random UUID for device identification
     *
     * @return A new UUID string
     */
    private fun generateNewDeviceId(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Get ANDROID_ID (with warning suppression for legacy code)
     *
     * ⚠️ DEPRECATED: Only use this if you absolutely need ANDROID_ID
     * for backward compatibility. Otherwise use getDeviceId().
     *
     * Note: ANDROID_ID behavior varies by Android version:
     * - Android 8.0+: Unique per app and per user
     * - Android 7.1 and below: Device-wide (privacy concern)
     *
     * @param context Application context
     * @return ANDROID_ID string
     */
    @Suppress("HardwareIds")
    @JvmStatic
    @Deprecated("Use getDeviceId() instead for privacy compliance")
    fun getAndroidId(context: Context): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting ANDROID_ID: ${e.message}")
            ""
        }
    }
}