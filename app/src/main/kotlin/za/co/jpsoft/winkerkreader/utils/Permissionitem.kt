package za.co.jpsoft.winkerkreader.utils

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Represents a single permission entry in the permissions list screen.
 *
 * Extracted from the inner class in PermissionsActivity. Now standalone —
 * [updateStatus] takes a [Context] instead of requiring a live PermissionsActivity
 * reference, eliminating the bidirectional coupling that caused the low cohesion score.
 *
 * @param name        Display name shown in the list
 * @param description Short Afrikaans rationale shown below the name
 * @param permission  Android permission string (null for special/settings permissions)
 * @param type        How this permission must be requested
 */
class PermissionItem(
    val name: String,
    val description: String,
    val permission: String? = null,
    val type: PermissionType,
    context: Context
) {
    var isGranted: Boolean = false
        private set

    init {
        updateStatus(context)
    }

    /**
     * Refreshes [isGranted] to reflect the current system state.
     * Call this on every [onResume] to keep the list accurate.
     */
    fun updateStatus(context: Context) {
        isGranted = when (type) {
            PermissionType.RUNTIME ->
                permission != null &&
                        ContextCompat.checkSelfPermission(context, permission) ==
                        PackageManager.PERMISSION_GRANTED

            PermissionType.OVERLAY ->
                Settings.canDrawOverlays(context)

            PermissionType.EXACT_ALARM ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                        .canScheduleExactAlarms()
                } else true

            PermissionType.NOTIFICATION_POLICY -> {
                val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager
                mgr.isNotificationPolicyAccessGranted
            }

            PermissionType.NOTIFICATION_LISTENER ->
                NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
        }
    }
}