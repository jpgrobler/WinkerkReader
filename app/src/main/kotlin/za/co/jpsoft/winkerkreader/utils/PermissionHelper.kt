// File: utils/PermissionRationaleHelper.kt
package za.co.jpsoft.winkerkreader.utils

import android.app.Activity
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import za.co.jpsoft.winkerkreader.R

object PermissionRationaleHelper {

    /**
     * Request a permission group with an optional rationale dialog.
     * If [shouldShowRationale] is true, a dialog is shown first.
     *
     * @param activity      the calling Activity
     * @param permissions   array of permissions to request
     * @param requestCode   request code for onRequestPermissionsResult
     * @param titleResId    string resource for dialog title (if rationale shown)
     * @param messageResId  string resource for dialog message (if rationale shown)
     * @param onComplete    optional callback after the permission request is sent (or dialog dismissed)
     */
    fun requestWithRationale(
        activity: Activity,
        permissions: Array<String>,
        requestCode: Int,
        titleResId: Int = R.string.rationale_generic_title,
        messageResId: Int = R.string.rationale_generic_message,
        onComplete: (() -> Unit)? = null
    ) {
        // Check if we should show rationale for any of the permissions
        val shouldShow = permissions.any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }

        // Also check if any are already granted – we don't need to re-request granted ones
        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (ungranted.isEmpty()) {
            // All permissions already granted
            onComplete?.invoke()
            return
        }

        if (shouldShow) {
            // Show rationale dialog
            val message = if (messageResId == R.string.rationale_generic_message) {
                // If using generic, we need to insert the permission name(s)
                val permissionNames = ungranted.joinToString { permission ->
                    PermissionManager.getPermissionDisplayName(permission)
                }
                activity.getString(messageResId, permissionNames)
            } else {
                activity.getString(messageResId)
            }

            AlertDialog.Builder(activity)
                .setTitle(titleResId)
                .setMessage(message)
                .setPositiveButton(R.string.rationale_ok) { _, _ ->
                    ActivityCompat.requestPermissions(activity, ungranted, requestCode)
                    onComplete?.invoke()
                }
                .setNegativeButton(R.string.rationale_deny) { _, _ ->
                    onComplete?.invoke()
                }
                .setCancelable(false)
                .show()
        } else {
            // No rationale needed – request directly
            ActivityCompat.requestPermissions(activity, ungranted, requestCode)
            onComplete?.invoke()
        }
    }

    /**
     * Convenience method for a single permission.
     */
    fun requestWithRationale(
        activity: Activity,
        permission: String,
        requestCode: Int,
        titleResId: Int = R.string.rationale_generic_title,
        messageResId: Int = R.string.rationale_generic_message,
        onComplete: (() -> Unit)? = null
    ) {
        requestWithRationale(activity, arrayOf(permission), requestCode, titleResId, messageResId, onComplete)
    }
}