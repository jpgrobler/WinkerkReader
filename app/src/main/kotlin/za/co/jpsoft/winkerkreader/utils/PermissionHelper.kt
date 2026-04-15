package za.co.jpsoft.winkerkreader.utils

// PermissionHelper.kt (updated)


import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Enhanced helper class for managing runtime permissions
 */
object PermissionHelper {

    // Request codes for different permission groups
    const val REQUEST_CODE_ALL_PERMISSIONS = 1001
    const val REQUEST_CODE_STORAGE = 1002
    const val REQUEST_CODE_CONTACTS = 1003
    const val REQUEST_CODE_SMS = 1004
    const val REQUEST_CODE_PHONE = 1005
    const val REQUEST_CODE_CALENDAR = 1006
    const val REQUEST_CODE_NOTIFICATIONS = 1007
    const val REQUEST_CODE_EXACT_ALARM = 1008
    const val REQUEST_CODE_MEDIA = 1009
    const val REQUEST_CODE_OVERLAY = 1010

    // Core storage permissions (varies by Android version)
    @JvmField
    val STORAGE_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE) // Android 11+ only needs READ
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    // Media access permissions (Android 13+)


    // Contact permissions
    @JvmField
    val CONTACT_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS
    )

    // SMS permissions
    @JvmField
    val SMS_PERMISSIONS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS
    )

    // Phone and call permissions
    @JvmField
    val PHONE_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_NUMBERS
        )
    } else {
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
    }

    // Calendar permissions
    @JvmField
    val CALENDAR_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    // Notification permissions
    @JvmField
    val NOTIFICATION_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
            Manifest.permission.ACCESS_NOTIFICATION_POLICY
        )
    } else {
        arrayOf(
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
            Manifest.permission.ACCESS_NOTIFICATION_POLICY
        )
    }

    // Exact alarm permissions
    @JvmField
    val EXACT_ALARM_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.SCHEDULE_EXACT_ALARM,
            Manifest.permission.USE_EXACT_ALARM
        )
    } else emptyArray()

    // Network permissions (usually granted automatically)
    @JvmField
    val NETWORK_PERMISSIONS = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE
    )

    // Background service permissions (usually granted automatically)
    @JvmField
    val BACKGROUND_SERVICE_PERMISSIONS = arrayOf(
        Manifest.permission.RECEIVE_BOOT_COMPLETED,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.WAKE_LOCK
    )

    // Foreground service type permissions (Android 14+)
    @JvmField
    val FOREGROUND_SERVICE_TYPE_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
        )
    } else emptyArray()

    // Package management permissions (usually require special handling)
        // Special permissions that need different handling
    const val SYSTEM_ALERT_WINDOW = Manifest.permission.SYSTEM_ALERT_WINDOW

    /**
     * Check if all permissions in the array are granted
     */
    @JvmStatic
    fun arePermissionsGranted(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get a list of permissions that are not yet granted
     */
    @JvmStatic
    fun getNotGrantedPermissions(context: Context, permissions: Array<String>): List<String> {
        return permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get a comprehensive status of all app permissions
     */
    @JvmStatic
    fun getAllPermissionStatus(context: Context): PermissionStatus {
        return PermissionStatus().apply {
            storagePermissions = checkPermissionGroup(context, STORAGE_PERMISSIONS)

            contactPermissions = checkPermissionGroup(context, CONTACT_PERMISSIONS)
            smsPermissions = checkPermissionGroup(context, SMS_PERMISSIONS)
            phonePermissions = checkPermissionGroup(context, PHONE_PERMISSIONS)
            calendarPermissions = checkPermissionGroup(context, CALENDAR_PERMISSIONS)
            notificationPermissions = checkPermissionGroup(context, NOTIFICATION_PERMISSIONS)
            exactAlarmPermissions = checkPermissionGroup(context, EXACT_ALARM_PERMISSIONS)
            networkPermissions = checkPermissionGroup(context, NETWORK_PERMISSIONS)
            backgroundServicePermissions = checkPermissionGroup(context, BACKGROUND_SERVICE_PERMISSIONS)
            foregroundServiceTypePermissions = checkPermissionGroup(context, FOREGROUND_SERVICE_TYPE_PERMISSIONS)
            systemAlertWindowGranted = checkSystemAlertWindowPermission(context)
            notificationPolicyAccessGranted = checkNotificationPolicyAccess(context)
        }
    }

    private fun checkPermissionGroup(context: Context, permissions: Array<String>): PermissionGroupStatus {
        if (permissions.isEmpty()) {
            return PermissionGroupStatus(emptyList(), emptyList())
        }
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED) {
                granted.add(it)
            } else {
                denied.add(it)
            }
        }
        return PermissionGroupStatus(granted, denied)
    }

    private fun checkSystemAlertWindowPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    private fun checkNotificationPolicyAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.isNotificationPolicyAccessGranted
        } else true
    }

    /**
     * Request all necessary runtime permissions
     */
    @JvmStatic
    fun requestAllPermissions(activity: Activity, requestCode: Int) {
        val allPermissions = mutableListOf<String>()

        // Add all permission groups that need runtime requests
        addPermissionsIfNeeded(activity, allPermissions, STORAGE_PERMISSIONS)
        addPermissionsIfNeeded(activity, allPermissions, CONTACT_PERMISSIONS)
        addPermissionsIfNeeded(activity, allPermissions, SMS_PERMISSIONS)
        addPermissionsIfNeeded(activity, allPermissions, PHONE_PERMISSIONS)
        addPermissionsIfNeeded(activity, allPermissions, CALENDAR_PERMISSIONS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addPermissionsIfNeeded(activity, allPermissions, NOTIFICATION_PERMISSIONS)

        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addPermissionsIfNeeded(activity, allPermissions, EXACT_ALARM_PERMISSIONS)
        }

        if (allPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, allPermissions.toTypedArray(), requestCode)
        }
    }

    /**
     * Request specific permission group
     */
    @JvmStatic
    fun requestPermissionGroup(activity: Activity, permissions: Array<String>, requestCode: Int) {
        val notGranted = getNotGrantedPermissions(activity, permissions)
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, notGranted.toTypedArray(), requestCode)
        }
    }

    /**
     * Check if we have all files access (Android 11+)
     */
    @JvmStatic
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    /**
     * Request all files access (Android 11+)
     */
    @JvmStatic
    fun requestAllFilesAccess(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:${activity.packageName}"))
                activity.startActivityForResult(intent, REQUEST_CODE_STORAGE)
            } catch (e: Exception) {
                // Fallback to general settings
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivity(intent)
            }
        }
    }

    /**
     * Enhanced storage permission check that considers Android version differences
     */
    @JvmStatic
    fun hasStoragePermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - check if we have all files access OR scoped storage permissions
            Environment.isExternalStorageManager() || arePermissionsGranted(context, STORAGE_PERMISSIONS)
        } else {
            // Android 10 and below - use traditional permissions
            arePermissionsGranted(context, STORAGE_PERMISSIONS)
        }
    }

    /**
     * Request storage permissions with Android version considerations
     */
    @JvmStatic
    fun requestStoragePermissions(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - offer choice between scoped storage and all files access
            //showStoragePermissionDialog(activity);
            requestPermissionGroup(activity, STORAGE_PERMISSIONS, REQUEST_CODE_STORAGE)
        } else {
            // Android 10 and below - request traditional permissions
            requestPermissionGroup(activity, STORAGE_PERMISSIONS, REQUEST_CODE_STORAGE)
        }
    }

    private fun showStoragePermissionDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Storage Access Required")
            .setMessage("This app needs storage access. Choose your preferred option:")
            .setPositiveButton("All Files Access") { _, _ -> requestAllFilesAccess(activity) }
            .setNegativeButton("Scoped Access") { _, _ ->
                requestPermissionGroup(activity, STORAGE_PERMISSIONS, REQUEST_CODE_STORAGE)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    @JvmStatic
    fun requestSystemAlertWindowPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY)
        }
    }

    /**
     * Request notification policy access
     */
    @JvmStatic
    fun requestNotificationPolicyAccess(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val manager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!manager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                activity.startActivity(intent)
            }
        }
    }

    private fun addPermissionsIfNeeded(context: Context, list: MutableList<String>, permissions: Array<String>) {
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED) {
                list.add(it)
            }
        }
    }

    /**
     * Check if we should show rationale for any of the permissions
     */
    @JvmStatic
    fun shouldShowRationaleForPermissions(activity: Activity, permissions: Array<String>): Boolean {
        return permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }

    /**
     * Handle the result of permission requests
     */
    @JvmStatic
    fun handlePermissionResult(permissions: Array<String>, grantResults: IntArray): PermissionResult {
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()

        for (i in permissions.indices) {
            if (i < grantResults.size && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                granted.add(permissions[i])
            } else {
                denied.add(permissions[i])
            }
        }
        return PermissionResult(granted, denied)
    }

    /**
     * Get user-friendly permission names
     */
    @JvmStatic
    fun getPermissionDisplayName(permission: String): String {
        val map = mapOf(
            Manifest.permission.READ_EXTERNAL_STORAGE to "Read Storage",
            Manifest.permission.WRITE_EXTERNAL_STORAGE to "Write Storage",
            Manifest.permission.READ_CONTACTS to "Read Contacts",
            Manifest.permission.WRITE_CONTACTS to "Write Contacts",
            Manifest.permission.SEND_SMS to "Send SMS",
            Manifest.permission.READ_SMS to "Read SMS",
            Manifest.permission.READ_PHONE_STATE to "Read Phone State",
            Manifest.permission.READ_CALL_LOG to "Read Call Log",
            Manifest.permission.READ_PHONE_NUMBERS to "Read Phone Numbers",
            Manifest.permission.READ_CALENDAR to "Read Calendar",
            Manifest.permission.WRITE_CALENDAR to "Write Calendar",
            Manifest.permission.POST_NOTIFICATIONS to "Post Notifications",
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE to "Notification Listener",
            Manifest.permission.ACCESS_NOTIFICATION_POLICY to "Notification Policy Access",
            Manifest.permission.SCHEDULE_EXACT_ALARM to "Schedule Exact Alarm",
            Manifest.permission.USE_EXACT_ALARM to "Use Exact Alarm",
            Manifest.permission.SYSTEM_ALERT_WINDOW to "Display Over Other Apps"
        )
        return map[permission] ?: permission.removePrefix("android.permission.")
    }

    /**
     * Result class for permission requests
     */
    class PermissionResult(
        val grantedPermissions: List<String>,
        val deniedPermissions: List<String>
    ) {
        fun areAllGranted(): Boolean = deniedPermissions.isEmpty()
        fun hasGrantedPermissions(): Boolean = grantedPermissions.isNotEmpty()
        fun hasDeniedPermissions(): Boolean = deniedPermissions.isNotEmpty()
    }

    /**
     * Status class for a group of permissions
     */
    class PermissionGroupStatus(
        val grantedPermissions: List<String>,
        val deniedPermissions: List<String>
    ) {
        fun areAllGranted(): Boolean = deniedPermissions.isEmpty()
        fun hasAnyGranted(): Boolean = grantedPermissions.isNotEmpty()
        fun hasAnyDenied(): Boolean = deniedPermissions.isNotEmpty()
        fun getTotalCount(): Int = grantedPermissions.size + deniedPermissions.size
        fun getGrantedCount(): Int = grantedPermissions.size
        fun getDeniedCount(): Int = deniedPermissions.size
    }

    /**
     * Comprehensive status of all app permissions
     */
    class PermissionStatus {
        var storagePermissions: PermissionGroupStatus = PermissionGroupStatus(emptyList(), emptyList())
        var mediaPermissions: PermissionGroupStatus = PermissionGroupStatus(emptyList(), emptyList())
        var contactPermissions: PermissionGroupStatus = PermissionGroupStatus(emptyList(), emptyList())
        var smsPermissions: PermissionGroupStatus = PermissionGroupStatus(emptyList(), emptyList())
        var phonePermissions: PermissionGroupStatus = PermissionGroupStatus(emptyList(), emptyList())
        var calendarPermissions: PermissionGroupStatus = PermissionGroupStatus(emptyList(), emptyList())
        var notificationPermissions: PermissionGroupStatus = PermissionGroupStatus(emptyList(), emptyList())
        var exactAlarmPermissions: PermissionGroupStatus = PermissionGroupStatus(emptyList(), emptyList())
        var networkPermissions: PermissionGroupStatus = PermissionGroupStatus(emptyList(), emptyList())
        var backgroundServicePermissions: PermissionGroupStatus = PermissionGroupStatus(emptyList(), emptyList())
        var foregroundServiceTypePermissions: PermissionGroupStatus = PermissionGroupStatus(emptyList(), emptyList())
        var packageManagementPermissions: PermissionGroupStatus = PermissionGroupStatus(emptyList(), emptyList())
        var systemAlertWindowGranted: Boolean = false
        var notificationPolicyAccessGranted: Boolean = false

        /**
         * Get a summary string of all permission statuses
         */
        fun getSummaryString(): String {
            val summary = StringBuilder()
            summary.append("=== PERMISSION STATUS SUMMARY ===\n\n")

            fun appendGroup(name: String, status: PermissionGroupStatus) {
                if (status.getTotalCount() > 0) {
                    summary.append("$name Permissions: ${status.getGrantedCount()}/${status.getTotalCount()} granted\n")
                    if (status.grantedPermissions.isNotEmpty()) {
                        summary.append("  Granted: ${status.grantedPermissions.joinToString { getPermissionDisplayName(it) }}\n")
                    }
                    if (status.deniedPermissions.isNotEmpty()) {
                        summary.append("  Denied: ${status.deniedPermissions.joinToString { getPermissionDisplayName(it) }}\n")
                    }
                    summary.append("\n")
                }
            }

            appendGroup("Storage", storagePermissions)
            appendGroup("Contacts", contactPermissions)
            appendGroup("SMS", smsPermissions)
            appendGroup("Phone", phonePermissions)
            appendGroup("Calendar", calendarPermissions)
            appendGroup("Notifications", notificationPermissions)
            appendGroup("Exact Alarms", exactAlarmPermissions)
            appendGroup("Network", networkPermissions)
            appendGroup("Background Services", backgroundServicePermissions)
            appendGroup("Foreground Service Types", foregroundServiceTypePermissions)
            appendGroup("Package Management", packageManagementPermissions)
            summary.append("Special Permissions:\n")
            summary.append("  System Alert Window: ${if (systemAlertWindowGranted) "GRANTED" else "DENIED"}\n")
            summary.append("  Notification Policy Access: ${if (notificationPolicyAccessGranted) "GRANTED" else "DENIED"}\n")
            return summary.toString()
        }
    }
}