package za.co.jpsoft.winkerkreader.utils

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Centralised permission manager for the entire app.
 * Handles runtime permissions, overlay, notification policy,
 * and exact alarm permissions.
 */
class PermissionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("PermissionPrefs", Context.MODE_PRIVATE)

    companion object {
        // Request codes for different permission groups
        const val RC_ALL_PERMISSIONS = 1001
        const val RC_STORAGE = 1002
        const val RC_CONTACTS = 1003
        const val RC_SMS = 1004
        const val RC_PHONE = 1005
        const val RC_CALENDAR = 1006
        const val RC_NOTIFICATIONS = 1007
        const val RC_EXACT_ALARM = 1008
        const val RC_OVERLAY = 1010

        // Permission groups
        val CONTACT_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        val SMS_PERMISSIONS = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS
        )
        val PHONE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        val CALENDAR_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        val NOTIFICATION_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
        val EXACT_ALARM_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.SCHEDULE_EXACT_ALARM)
        } else {
            emptyArray()
        }

        /** All runtime permissions that the app needs (excluding special ones). */
        val ALL_RUNTIME_PERMISSIONS = mutableListOf<String>().apply {
            addAll(CONTACT_PERMISSIONS)
            addAll(SMS_PERMISSIONS)
            addAll(PHONE_PERMISSIONS)
            addAll(CALENDAR_PERMISSIONS)
            addAll(NOTIFICATION_PERMISSIONS)
            addAll(EXACT_ALARM_PERMISSIONS)
        }.toTypedArray()
    }

    /**
     * Check if a specific permission is granted.
     */
    fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Check if all permissions in the given array are granted.
     */
    fun arePermissionsGranted(permissions: Array<String>): Boolean =
        permissions.all { isPermissionGranted(it) }

    /**
     * Request a set of permissions from an Activity.
     */
    fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int) {
        val notGranted = permissions.filter { !isPermissionGranted(it) }.toTypedArray()
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, notGranted, requestCode)
        }
    }

    /**
     * Request all essential app permissions from an Activity.
     */
    fun requestAllPermissions(activity: Activity) {
        requestPermissions(activity, ALL_RUNTIME_PERMISSIONS, RC_ALL_PERMISSIONS)
    }

    /**
     * Check if the overlay permission (SYSTEM_ALERT_WINDOW) is granted.
     */
    fun isOverlayPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /**
     * Get an Intent to request overlay permission.
     */
    fun getOverlayPermissionIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else null
    }

    /**
     * Check if notification policy access is granted.
     */
    fun isNotificationPolicyAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.isNotificationPolicyAccessGranted
    }

    /**
     * Check if notification listener is enabled for this app.
     */
    fun isNotificationListenerEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    /**
     * Get an Intent to open notification policy settings.
     */
    fun getNotificationPolicyIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /**
     * Get an Intent to open notification listener settings.
     */
    fun getNotificationListenerIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /**
     * Check if the app can schedule exact alarms.
     * Uses the type‑safe getSystemService(AlarmManager::class.java) on API 23+,
     * otherwise falls back to the legacy cast with the import.
     */
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Type‑safe, no cast needed
            context.getSystemService(AlarmManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        }
        return alarmManager?.canScheduleExactAlarms() ?: false
    }

    /**
     * Get an Intent to request exact alarm permission.
     */
    fun getExactAlarmIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else null
    }

    /**
     * Check if all essential permissions for core functionality are granted.
     */
    fun hasEssentialPermissions(): Boolean {
        return arePermissionsGranted(CONTACT_PERMISSIONS) &&
                arePermissionsGranted(SMS_PERMISSIONS) &&
                arePermissionsGranted(PHONE_PERMISSIONS) &&
                arePermissionsGranted(CALENDAR_PERMISSIONS) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS))
    }

    /**
     * Get a friendly name for a permission (for debugging/UI).
     */
    fun getPermissionDisplayName(permission: String): String {
        val map = mapOf(
            Manifest.permission.READ_CONTACTS to "Read Contacts",
            Manifest.permission.WRITE_CONTACTS to "Write Contacts",
            Manifest.permission.SEND_SMS to "Send SMS",
            Manifest.permission.READ_SMS to "Read SMS",
            Manifest.permission.READ_PHONE_STATE to "Phone State",
            Manifest.permission.READ_CALL_LOG to "Call Log",
            Manifest.permission.READ_PHONE_NUMBERS to "Phone Numbers",
            Manifest.permission.READ_CALENDAR to "Read Calendar",
            Manifest.permission.WRITE_CALENDAR to "Write Calendar",
            Manifest.permission.POST_NOTIFICATIONS to "Notifications",
            Manifest.permission.SCHEDULE_EXACT_ALARM to "Exact Alarms"
        )
        return map[permission] ?: permission.substringAfterLast('.')
    }
    fun isFirstLaunch(): Boolean = prefs.getBoolean("isFirstLaunch", true)
    fun setFirstLaunchComplete() = prefs.edit().putBoolean("isFirstLaunch", false).apply()
    fun isCheckOnStartEnabled(): Boolean = prefs.getBoolean("checkPermissionsOnStart", true)
    fun setCheckOnStart(enabled: Boolean) = prefs.edit().putBoolean("checkPermissionsOnStart", enabled).apply()

    fun getMissingPermissionsCount(): Int {
        var count = 0
        if (!isPermissionGranted(Manifest.permission.READ_CONTACTS)) count++
        if (!isPermissionGranted(Manifest.permission.SEND_SMS)) count++
        if (!isPermissionGranted(Manifest.permission.READ_PHONE_STATE)) count++
        if (!isPermissionGranted(Manifest.permission.READ_CALL_LOG)) count++
        if (!isPermissionGranted(Manifest.permission.READ_SMS)) count++
        if (!isPermissionGranted(Manifest.permission.READ_CALENDAR)) count++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) count++
        return count
    }
}