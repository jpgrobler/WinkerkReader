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
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import za.co.jpsoft.winkerkreader.R

/**
 * Centralised permission manager for the entire app.
 * Handles runtime permissions, overlay, notification policy,
 * and exact alarm permissions.
 */
class PermissionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("PermissionPrefs", Context.MODE_PRIVATE)

    companion object {
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

    // ------------------------------------------------------------------------
    // Basic permission checks and requests
    // ------------------------------------------------------------------------

    fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun arePermissionsGranted(permissions: Array<String>): Boolean =
        permissions.all { isPermissionGranted(it) }

    fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int) {
        val notGranted = permissions.filter { !isPermissionGranted(it) }.toTypedArray()
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, notGranted, requestCode)
        }
    }

    fun requestAllPermissions(activity: Activity) {
        requestPermissions(activity, ALL_RUNTIME_PERMISSIONS, RC_ALL_PERMISSIONS)
    }

    // ------------------------------------------------------------------------
    // Special permissions
    // ------------------------------------------------------------------------

    fun isOverlayPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun getOverlayPermissionIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(
                context
            )
        ) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else null
    }

    fun isNotificationPolicyAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.isNotificationPolicyAccessGranted
    }

    fun isNotificationListenerEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    fun getNotificationPolicyIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun getNotificationListenerIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(AlarmManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        }
        return alarmManager?.canScheduleExactAlarms() ?: false
    }

    fun getExactAlarmIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else null
    }

    // ------------------------------------------------------------------------
    // Helpers for prefs and counts
    // ------------------------------------------------------------------------

    fun isFirstLaunch(): Boolean = prefs.getBoolean("isFirstLaunch", true)
    fun setFirstLaunchComplete() = prefs.edit().putBoolean("isFirstLaunch", false).apply()
    fun isCheckOnStartEnabled(): Boolean = prefs.getBoolean("checkPermissionsOnStart", true)
    fun setCheckOnStart(enabled: Boolean) =
        prefs.edit().putBoolean("checkPermissionsOnStart", enabled).apply()

    fun getMissingPermissionsCount(): Int {
        var count = 0
        if (!isPermissionGranted(Manifest.permission.READ_CONTACTS)) count++
        if (!isPermissionGranted(Manifest.permission.SEND_SMS)) count++
        if (!isPermissionGranted(Manifest.permission.READ_PHONE_STATE)) count++
        if (!isPermissionGranted(Manifest.permission.READ_CALL_LOG)) count++
        if (!isPermissionGranted(Manifest.permission.READ_SMS)) count++
        if (!isPermissionGranted(Manifest.permission.READ_CALENDAR)) count++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        ) count++
        return count
    }

    fun hasEssentialPermissions(): Boolean {
        return arePermissionsGranted(CONTACT_PERMISSIONS) &&
                arePermissionsGranted(SMS_PERMISSIONS) &&
                arePermissionsGranted(PHONE_PERMISSIONS) &&
                arePermissionsGranted(CALENDAR_PERMISSIONS) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || isPermissionGranted(
                    Manifest.permission.POST_NOTIFICATIONS
                ))
    }


    // ------------------------------------------------------------------------
    // Request with rationale (using the helper)
    // ------------------------------------------------------------------------

    fun requestWithRationale(
        activity: Activity,
        permissions: Array<String>,
        requestCode: Int,
        rationaleTitleResId: Int,
        rationaleMessageResId: Int
    ) {
        PermissionRationaleHelper.requestWithRationale(
            activity,
            permissions,
            requestCode,
            rationaleTitleResId,
            rationaleMessageResId
        )
    }

    // Specific group methods
    fun requestPhonePermissions(activity: Activity) {
        requestWithRationale(
            activity,
            PHONE_PERMISSIONS,
            RC_PHONE,
            R.string.rationale_phone_title,
            R.string.rationale_phone_message
        )
    }

    fun requestContactsPermissions(activity: Activity) {
        requestWithRationale(
            activity,
            CONTACT_PERMISSIONS,
            RC_CONTACTS,
            R.string.rationale_contacts_title,
            R.string.rationale_contacts_message
        )
    }

    fun requestSmsPermissions(activity: Activity) {
        requestWithRationale(
            activity,
            SMS_PERMISSIONS,
            RC_SMS,
            R.string.rationale_sms_title,
            R.string.rationale_sms_message
        )
    }

    fun requestCalendarPermissions(activity: Activity) {
        requestWithRationale(
            activity,
            CALENDAR_PERMISSIONS,
            RC_CALENDAR,
            R.string.rationale_calendar_title,
            R.string.rationale_calendar_message
        )
    }

    fun requestNotificationPermissions(activity: Activity) {
        requestWithRationale(
            activity,
            NOTIFICATION_PERMISSIONS,
            RC_NOTIFICATIONS,
            R.string.rationale_notifications_title,
            R.string.rationale_notifications_message
        )
    }

    fun requestExactAlarmPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestWithRationale(
                activity,
                EXACT_ALARM_PERMISSIONS,
                RC_EXACT_ALARM,
                R.string.rationale_exact_alarm_title,
                R.string.rationale_exact_alarm_message
            )
        }
    }

    fun requestOverlayPermissionWithRationale(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (Settings.canDrawOverlays(activity)) return

        AlertDialog.Builder(activity)
            .setTitle(R.string.rationale_overlay_title)
            .setMessage(R.string.rationale_overlay_message)
            .setPositiveButton(R.string.rationale_ok) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
            }
            .setNegativeButton(R.string.rationale_deny, null)
            .show()
    }

    fun requestNotificationListenerWithRationale(activity: Activity) {
        // Show rationale first
        AlertDialog.Builder(activity)
            .setTitle(R.string.rationale_notification_listener_title)
            .setMessage(R.string.rationale_notification_listener_message)
            .setPositiveButton(R.string.rationale_ok) { _, _ ->
                activity.startActivity(getNotificationListenerIntent())
            }
            .setNegativeButton(R.string.rationale_deny, null)
            .show()
    }
}