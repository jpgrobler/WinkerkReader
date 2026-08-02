package za.co.jpsoft.winkerkreader.utils.work

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R

/**
 * Helper to check and request battery optimization exemptions.
 * Essential for background services like [za.co.jpsoft.winkerkreader.services.CallMonitoringService].
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptimizationHelper"

    /**
     * Checks if the app is already ignoring battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Shows a dialog to the user explaining why battery optimization should be disabled,
     * and provides a button to open the system settings.
     */
    fun showBatteryOptimizationDialog(activity: Activity) {
        if (isIgnoringBatteryOptimizations(activity)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "App is already ignoring battery optimizations")
            return
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.battery_optimization_title)
            .setMessage(R.string.battery_optimization_message)
            .setPositiveButton(R.string.battery_optimization_button) { _, _ ->
                requestIgnoreBatteryOptimizations(activity)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    /**
     * Opens the system battery optimization settings page or requests exemption directly.
     */
    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Direct battery request failed", e)
            // Fallback: open general battery settings and show a toast
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                activity.startActivity(intent)
                Toast.makeText(
                    activity,
                    "Find '${activity.getString(R.string.app_name)}' and select 'Don't optimise'",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e2: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Cannot open battery settings", e2)
            }
        }
    }
}
