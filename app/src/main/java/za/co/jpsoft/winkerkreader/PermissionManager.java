package za.co.jpsoft.winkerkreader;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;

public class PermissionManager {

    private static final String PREFS_NAME = "PermissionPrefs";
    private static final String KEY_FIRST_LAUNCH = "isFirstLaunch";
    private static final String KEY_CHECK_ON_START = "checkPermissionsOnStart";

    private Context context;
    private SharedPreferences prefs;

    public PermissionManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Check if this is the first time app is launched
     */
    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    /**
     * Mark that app has been launched
     */
    public void setFirstLaunchComplete() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
    }

    /**
     * Check if permission check on start is enabled
     */
    public boolean isCheckOnStartEnabled() {
        return prefs.getBoolean(KEY_CHECK_ON_START, true); // Default: enabled
    }

    /**
     * Enable/disable permission check on start
     */
    public void setCheckOnStart(boolean enabled) {
        prefs.edit().putBoolean(KEY_CHECK_ON_START, enabled).apply();
    }

    /**
     * Check if essential permissions are granted
     */
    public boolean hasEssentialPermissions() {
        // Check critical permissions
        boolean hasContacts = ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;

        boolean hasSms = ContextCompat.checkSelfPermission(context,
                Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;

        boolean hasPhoneState = ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;

        boolean hasCallLog = ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;

        boolean hasReadSms = ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;

        boolean hasCalendar = ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;

        // Check for Android 13+ notification permission
        boolean hasNotifications = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotifications = ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        // Check for media/storage permissions
        boolean hasStorage = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasStorage = ContextCompat.checkSelfPermission(context,
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            hasStorage = ContextCompat.checkSelfPermission(context,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }

        // Return true only if ALL essential permissions are granted
        return hasContacts && hasSms && hasPhoneState && hasCallLog &&
                hasReadSms && hasCalendar && hasNotifications && hasStorage;
    }

    /**
     * Get count of missing essential permissions
     */
    public int getMissingPermissionsCount() {
        int count = 0;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) count++;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) count++;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) count++;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) count++;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) count++;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) count++;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) count++;

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) count++;
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) count++;
        }

        return count;
    }
}