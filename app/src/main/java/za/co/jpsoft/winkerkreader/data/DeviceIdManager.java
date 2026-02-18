package za.co.jpsoft.winkerkreader.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import java.util.UUID;

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
public class DeviceIdManager {
    private static final String TAG = "DeviceIdManager";
    private static final String PREFS_NAME = "device_id_prefs";
    private static final String PREF_DEVICE_ID = "device_id";

    private static String cachedDeviceId = null;

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
    public static String getDeviceId(Context context) {
        // Return cached value if available
        if (cachedDeviceId != null && !cachedDeviceId.isEmpty()) {
            return cachedDeviceId;
        }

        // Get from SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cachedDeviceId = prefs.getString(PREF_DEVICE_ID, null);

        // Generate new ID if not found
        if (cachedDeviceId == null || cachedDeviceId.isEmpty()) {
            cachedDeviceId = generateNewDeviceId();

            // Save to SharedPreferences
            prefs.edit()
                    .putString(PREF_DEVICE_ID, cachedDeviceId)
                    .apply();

            Log.i(TAG, "Generated new device ID");
        } else {
            Log.d(TAG, "Retrieved existing device ID");
        }

        return cachedDeviceId;
    }

    /**
     * Generate a new random UUID for device identification
     *
     * @return A new UUID string
     */
    private static String generateNewDeviceId() {
        return UUID.randomUUID().toString();
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
    @SuppressWarnings("HardwareIds")
    @Deprecated
    public static String getAndroidId(Context context) {
        try {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
            return androidId != null ? androidId : "";
        } catch (Exception e) {
            Log.e(TAG, "Error getting ANDROID_ID: " + e.getMessage());
            return "";
        }
    }

    /**
     * Reset the device ID (generates a new one)
     *
     * Use this if you need to treat the device as "new"
     *
     * @param context Application context
     * @return The new device ID
     */
    public static String resetDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        cachedDeviceId = generateNewDeviceId();

        prefs.edit()
                .putString(PREF_DEVICE_ID, cachedDeviceId)
                .apply();

        Log.i(TAG, "Device ID reset");
        return cachedDeviceId;
    }

    /**
     * Check if a device ID exists
     *
     * @param context Application context
     * @return true if device ID exists, false otherwise
     */
    public static boolean hasDeviceId(Context context) {
        if (cachedDeviceId != null && !cachedDeviceId.isEmpty()) {
            return true;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String id = prefs.getString(PREF_DEVICE_ID, null);
        return id != null && !id.isEmpty();
    }

    /**
     * Get device ID for display purposes (shortened)
     *
     * @param context Application context
     * @return First 8 characters of device ID
     */
    public static String getDeviceIdShort(Context context) {
        String fullId = getDeviceId(context);
        return fullId.length() >= 8 ? fullId.substring(0, 8) : fullId;
    }

    /**
     * Clear cached device ID (forces reload from SharedPreferences)
     */
    public static void clearCache() {
        cachedDeviceId = null;
    }
}

// ============================================
// USAGE EXAMPLES
// ============================================

/*

1. RECOMMENDED: Use app-specific device ID (privacy-compliant)
   ============================================================

   // In your Activity or wherever you need the ID:
   String deviceId = DeviceIdManager.getDeviceId(context);
   winkerkEntry.id = deviceId;


2. LEGACY: If you MUST use ANDROID_ID (not recommended)
   ======================================================

   // This suppresses the warning but you should really use method 1
   @SuppressWarnings("HardwareIds")
   private void getLegacyAndroidId() {
       winkerkEntry.id = Settings.Secure.getString(
           getContentResolver(),
           Settings.Secure.ANDROID_ID
       );
   }

   // OR use the helper method:
   winkerkEntry.id = DeviceIdManager.getAndroidId(context);


3. BEST PRACTICE: Use Firebase Installation ID (recommended for analytics)
   ========================================================================

   // Add to build.gradle:
   // implementation 'com.google.firebase:firebase-installations:17.1.0'

   // In your code:
   FirebaseInstallations.getInstance().getId()
       .addOnSuccessListener(id -> {
           winkerkEntry.id = id;
       });


4. For User Identification: Use Firebase Authentication
   =====================================================

   // This is the BEST option if you need to track users across devices
   // Add Firebase Auth to your project and use:
   FirebaseAuth.getInstance().getCurrentUser().getUid();

*/