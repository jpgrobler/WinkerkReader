package za.co.jpsoft.winkerkreader;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced helper class for managing runtime permissions
 */
public class PermissionHelper {

    // Request codes for different permission groups
    public static final int REQUEST_CODE_ALL_PERMISSIONS = 1001;
    public static final int REQUEST_CODE_STORAGE = 1002;
    public static final int REQUEST_CODE_CONTACTS = 1003;
    public static final int REQUEST_CODE_SMS = 1004;
    public static final int REQUEST_CODE_PHONE = 1005;
    public static final int REQUEST_CODE_CALENDAR = 1006;
    public static final int REQUEST_CODE_NOTIFICATIONS = 1007;
    public static final int REQUEST_CODE_EXACT_ALARM = 1008;
    public static final int REQUEST_CODE_MEDIA = 1009;
    public static final int REQUEST_CODE_OVERLAY = 1010;

    // Core storage permissions (varies by Android version)
    public static final String[] STORAGE_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ?
            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE} : // Android 11+ only needs READ
            new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

    // Media access permissions (Android 13+)
    public static final String[] MEDIA_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
            new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO
            } : new String[0];

    // Contact permissions
    public static final String[] CONTACT_PERMISSIONS = {
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
    };

    // SMS permissions
    public static final String[] SMS_PERMISSIONS = {
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS
    };

    // Phone and call permissions
    public static final String[] PHONE_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
            new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_PHONE_NUMBERS
            } : new String[]{
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
    };

    // Calendar permissions
    public static final String[] CALENDAR_PERMISSIONS = {
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
    };

    // Notification permissions
    public static final String[] NOTIFICATION_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
            new String[]{
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
                    Manifest.permission.ACCESS_NOTIFICATION_POLICY
            } : new String[]{
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
            Manifest.permission.ACCESS_NOTIFICATION_POLICY
    };

    // Exact alarm permissions
    public static final String[] EXACT_ALARM_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
            new String[]{
                    Manifest.permission.SCHEDULE_EXACT_ALARM,
                    Manifest.permission.USE_EXACT_ALARM
            } : new String[0];

    // Network permissions (usually granted automatically)
    public static final String[] NETWORK_PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
    };

    // Background service permissions (usually granted automatically)
    public static final String[] BACKGROUND_SERVICE_PERMISSIONS = {
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.WAKE_LOCK
    };

    // Foreground service type permissions (Android 14+)
    public static final String[] FOREGROUND_SERVICE_TYPE_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ?
            new String[]{
                    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                    "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
            } : new String[0];

    // Package management permissions (usually require special handling)
    public static final String[] PACKAGE_MANAGEMENT_PERMISSIONS = {
            "android.permission.INSTALL_PACKAGES",
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            "android.permission.QUERY_ALL_PACKAGES"
    };

    // Special permissions that need different handling
    public static final String SYSTEM_ALERT_WINDOW = Manifest.permission.SYSTEM_ALERT_WINDOW;

    /**
     * Check if all permissions in the array are granted
     */
    public static boolean arePermissionsGranted(Context context, String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get a list of permissions that are not yet granted
     */
    public static List<String> getNotGrantedPermissions(Context context, String[] permissions) {
        List<String> notGranted = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                notGranted.add(permission);
            }
        }
        return notGranted;
    }

    /**
     * Get a comprehensive status of all app permissions
     */
    public static PermissionStatus getAllPermissionStatus(Context context) {
        PermissionStatus status = new PermissionStatus();

        // Check regular runtime permissions
        status.storagePermissions = checkPermissionGroup(context, STORAGE_PERMISSIONS);
        status.mediaPermissions = checkPermissionGroup(context, MEDIA_PERMISSIONS);
        status.contactPermissions = checkPermissionGroup(context, CONTACT_PERMISSIONS);
        status.smsPermissions = checkPermissionGroup(context, SMS_PERMISSIONS);
        status.phonePermissions = checkPermissionGroup(context, PHONE_PERMISSIONS);
        status.calendarPermissions = checkPermissionGroup(context, CALENDAR_PERMISSIONS);
        status.notificationPermissions = checkPermissionGroup(context, NOTIFICATION_PERMISSIONS);
        status.exactAlarmPermissions = checkPermissionGroup(context, EXACT_ALARM_PERMISSIONS);
        status.networkPermissions = checkPermissionGroup(context, NETWORK_PERMISSIONS);
        status.backgroundServicePermissions = checkPermissionGroup(context, BACKGROUND_SERVICE_PERMISSIONS);
        status.foregroundServiceTypePermissions = checkPermissionGroup(context, FOREGROUND_SERVICE_TYPE_PERMISSIONS);
        status.packageManagementPermissions = checkPermissionGroup(context, PACKAGE_MANAGEMENT_PERMISSIONS);

        // Check special permissions
        status.systemAlertWindowGranted = checkSystemAlertWindowPermission(context);
        status.notificationPolicyAccessGranted = checkNotificationPolicyAccess(context);

        return status;
    }

    private static PermissionGroupStatus checkPermissionGroup(Context context, String[] permissions) {
        if (permissions.length == 0) {
            return new PermissionGroupStatus(new ArrayList<>(), new ArrayList<>());
        }

        List<String> granted = new ArrayList<>();
        List<String> denied = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                granted.add(permission);
            } else {
                denied.add(permission);
            }
        }

        return new PermissionGroupStatus(granted, denied);
    }

    private static boolean checkSystemAlertWindowPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true; // Granted by default on older versions
    }

    private static boolean checkNotificationPolicyAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return notificationManager != null && notificationManager.isNotificationPolicyAccessGranted();
        }
        return true; // Granted by default on older versions
    }

    /**
     * Request all necessary runtime permissions
     */
    public static void requestAllPermissions(Activity activity, int requestCode) {
        List<String> allPermissions = new ArrayList<>();

        // Add all permission groups that need runtime requests
        addPermissionsIfNeeded(activity, allPermissions, STORAGE_PERMISSIONS);
        addPermissionsIfNeeded(activity, allPermissions, CONTACT_PERMISSIONS);
        addPermissionsIfNeeded(activity, allPermissions, SMS_PERMISSIONS);
        addPermissionsIfNeeded(activity, allPermissions, PHONE_PERMISSIONS);
        addPermissionsIfNeeded(activity, allPermissions, CALENDAR_PERMISSIONS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addPermissionsIfNeeded(activity, allPermissions, NOTIFICATION_PERMISSIONS);
            addPermissionsIfNeeded(activity, allPermissions, MEDIA_PERMISSIONS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addPermissionsIfNeeded(activity, allPermissions, EXACT_ALARM_PERMISSIONS);
        }

        if (!allPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(activity,
                    allPermissions.toArray(new String[0]), requestCode);
        }
    }

    /**
     * Request specific permission group
     */
    public static void requestPermissionGroup(Activity activity, String[] permissions, int requestCode) {
        List<String> notGranted = getNotGrantedPermissions(activity, permissions);
        if (!notGranted.isEmpty()) {
            ActivityCompat.requestPermissions(activity,
                    notGranted.toArray(new String[0]), requestCode);
        }
    }

    /**
     * Check if we have all files access (Android 11+)
     */
    public static boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true; // Not applicable for older versions
    }

    /**
     * Request all files access (Android 11+)
     */
    public static void requestAllFilesAccess(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQUEST_CODE_STORAGE);
            } catch (Exception e) {
                // Fallback to general settings
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivity(intent);
            }
        }
    }

    /**
     * Enhanced storage permission check that considers Android version differences
     */
    public static boolean hasStoragePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - check if we have all files access OR scoped storage permissions
            return Environment.isExternalStorageManager() ||
                    arePermissionsGranted(context, STORAGE_PERMISSIONS);
        } else {
            // Android 10 and below - use traditional permissions
            return arePermissionsGranted(context, STORAGE_PERMISSIONS);
        }
    }

    /**
     * Request storage permissions with Android version considerations
     */
    public static void requestStoragePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - offer choice between scoped storage and all files access
            //showStoragePermissionDialog(activity);
            requestPermissionGroup(activity, STORAGE_PERMISSIONS, REQUEST_CODE_STORAGE);
        } else {
            // Android 10 and below - request traditional permissions
            requestPermissionGroup(activity, STORAGE_PERMISSIONS, REQUEST_CODE_STORAGE);
        }
    }

    private static void showStoragePermissionDialog(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Storage Access Required")
                .setMessage("This app needs storage access. Choose your preferred option:")
                .setPositiveButton("All Files Access", (dialog, which) -> requestAllFilesAccess(activity))
                .setNegativeButton("Scoped Access", (dialog, which) ->
                        requestPermissionGroup(activity, STORAGE_PERMISSIONS, REQUEST_CODE_STORAGE))
                .setNeutralButton("Cancel", null)
                .show();
    }
    public static void requestSystemAlertWindowPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY);
        }
    }

    /**
     * Request notification policy access
     */
    public static void requestNotificationPolicyAccess(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager notificationManager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted()) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                activity.startActivity(intent);
            }
        }
    }

    private static void addPermissionsIfNeeded(Context context, List<String> permissionList, String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
            }
        }
    }

    /**
     * Check if we should show rationale for any of the permissions
     */
    public static boolean shouldShowRationaleForPermissions(Activity activity, String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle the result of permission requests
     */
    public static PermissionResult handlePermissionResult(String[] permissions, int[] grantResults) {
        List<String> granted = new ArrayList<>();
        List<String> denied = new ArrayList<>();

        for (int i = 0; i < permissions.length && i < grantResults.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                granted.add(permissions[i]);
            } else {
                denied.add(permissions[i]);
            }
        }

        return new PermissionResult(granted, denied);
    }

    /**
     * Get user-friendly permission names
     */
    public static String getPermissionDisplayName(String permission) {
        Map<String, String> permissionNames = new HashMap<>();
        permissionNames.put(Manifest.permission.READ_EXTERNAL_STORAGE, "Read Storage");
        permissionNames.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, "Write Storage");
        permissionNames.put(Manifest.permission.READ_MEDIA_IMAGES, "Read Images");
        permissionNames.put(Manifest.permission.READ_MEDIA_AUDIO, "Read Audio");
        permissionNames.put(Manifest.permission.READ_MEDIA_VIDEO, "Read Video");
        permissionNames.put(Manifest.permission.READ_CONTACTS, "Read Contacts");
        permissionNames.put(Manifest.permission.WRITE_CONTACTS, "Write Contacts");
        permissionNames.put(Manifest.permission.SEND_SMS, "Send SMS");
        permissionNames.put(Manifest.permission.READ_SMS, "Read SMS");
        permissionNames.put(Manifest.permission.READ_PHONE_STATE, "Read Phone State");
        permissionNames.put(Manifest.permission.READ_CALL_LOG, "Read Call Log");
        permissionNames.put(Manifest.permission.READ_PHONE_NUMBERS, "Read Phone Numbers");
        permissionNames.put(Manifest.permission.READ_CALENDAR, "Read Calendar");
        permissionNames.put(Manifest.permission.WRITE_CALENDAR, "Write Calendar");
        permissionNames.put(Manifest.permission.POST_NOTIFICATIONS, "Post Notifications");
        permissionNames.put(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, "Notification Listener");
        permissionNames.put(Manifest.permission.ACCESS_NOTIFICATION_POLICY, "Notification Policy Access");
        permissionNames.put(Manifest.permission.SCHEDULE_EXACT_ALARM, "Schedule Exact Alarm");
        permissionNames.put(Manifest.permission.USE_EXACT_ALARM, "Use Exact Alarm");
        permissionNames.put(Manifest.permission.SYSTEM_ALERT_WINDOW, "Display Over Other Apps");

        return permissionNames.getOrDefault(permission, permission.replace("android.permission.", ""));
    }

    /**
     * Result class for permission requests
     */
    public static class PermissionResult {
        public final List<String> grantedPermissions;
        public final List<String> deniedPermissions;

        public PermissionResult(List<String> granted, List<String> denied) {
            this.grantedPermissions = granted;
            this.deniedPermissions = denied;
        }

        public boolean areAllGranted() {
            return deniedPermissions.isEmpty();
        }

        public boolean hasGrantedPermissions() {
            return !grantedPermissions.isEmpty();
        }

        public boolean hasDeniedPermissions() {
            return !deniedPermissions.isEmpty();
        }
    }

    /**
     * Status class for a group of permissions
     */
    public static class PermissionGroupStatus {
        public final List<String> grantedPermissions;
        public final List<String> deniedPermissions;

        public PermissionGroupStatus(List<String> granted, List<String> denied) {
            this.grantedPermissions = granted;
            this.deniedPermissions = denied;
        }

        public boolean areAllGranted() {
            return deniedPermissions.isEmpty();
        }

        public boolean hasAnyGranted() {
            return !grantedPermissions.isEmpty();
        }

        public boolean hasAnyDenied() {
            return !deniedPermissions.isEmpty();
        }

        public int getTotalCount() {
            return grantedPermissions.size() + deniedPermissions.size();
        }

        public int getGrantedCount() {
            return grantedPermissions.size();
        }

        public int getDeniedCount() {
            return deniedPermissions.size();
        }
    }

    /**
     * Comprehensive status of all app permissions
     */
    public static class PermissionStatus {
        public PermissionGroupStatus storagePermissions;
        public PermissionGroupStatus mediaPermissions;
        public PermissionGroupStatus contactPermissions;
        public PermissionGroupStatus smsPermissions;
        public PermissionGroupStatus phonePermissions;
        public PermissionGroupStatus calendarPermissions;
        public PermissionGroupStatus notificationPermissions;
        public PermissionGroupStatus exactAlarmPermissions;
        public PermissionGroupStatus networkPermissions;
        public PermissionGroupStatus backgroundServicePermissions;
        public PermissionGroupStatus foregroundServiceTypePermissions;
        public PermissionGroupStatus packageManagementPermissions;
        public boolean systemAlertWindowGranted;
        public boolean notificationPolicyAccessGranted;

        /**
         * Get a summary string of all permission statuses
         */
        public String getSummaryString() {
            StringBuilder summary = new StringBuilder();
            summary.append("=== PERMISSION STATUS SUMMARY ===\n\n");

            appendGroupStatus(summary, "Storage", storagePermissions);
            appendGroupStatus(summary, "Media", mediaPermissions);
            appendGroupStatus(summary, "Contacts", contactPermissions);
            appendGroupStatus(summary, "SMS", smsPermissions);
            appendGroupStatus(summary, "Phone", phonePermissions);
            appendGroupStatus(summary, "Calendar", calendarPermissions);
            appendGroupStatus(summary, "Notifications", notificationPermissions);
            appendGroupStatus(summary, "Exact Alarms", exactAlarmPermissions);
            appendGroupStatus(summary, "Network", networkPermissions);
            appendGroupStatus(summary, "Background Services", backgroundServicePermissions);
            appendGroupStatus(summary, "Foreground Service Types", foregroundServiceTypePermissions);
            appendGroupStatus(summary, "Package Management", packageManagementPermissions);

            summary.append("Special Permissions:\n");
            summary.append("  System Alert Window: ").append(systemAlertWindowGranted ? "GRANTED" : "DENIED").append("\n");
            summary.append("  Notification Policy Access: ").append(notificationPolicyAccessGranted ? "GRANTED" : "DENIED").append("\n");

            return summary.toString();
        }

        private void appendGroupStatus(StringBuilder summary, String groupName, PermissionGroupStatus status) {
            if (status.getTotalCount() > 0) {
                summary.append(groupName).append(" Permissions: ");
                summary.append(status.getGrantedCount()).append("/").append(status.getTotalCount()).append(" granted\n");

                if (!status.grantedPermissions.isEmpty()) {
                    summary.append("  Granted: ");
                    for (String permission : status.grantedPermissions) {
                        summary.append(getPermissionDisplayName(permission)).append(", ");
                    }
                    summary.setLength(summary.length() - 2); // Remove last comma
                    summary.append("\n");
                }

                if (!status.deniedPermissions.isEmpty()) {
                    summary.append("  Denied: ");
                    for (String permission : status.deniedPermissions) {
                        summary.append(getPermissionDisplayName(permission)).append(", ");
                    }
                    summary.setLength(summary.length() - 2); // Remove last comma
                    summary.append("\n");
                }
                summary.append("\n");
            }
        }
    }
}