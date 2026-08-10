package za.co.jpsoft.winkerkreader.utils.security

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import za.co.jpsoft.winkerkreader.BuildConfig

class BiometricSetupHelper(private val activity: FragmentActivity) {

    /**
     * Checks if device has authentication available (Biometric OR PIN/Pattern/Password).
     */
    fun isAuthAvailable(): Boolean {
        val manager = BiometricManager.from(activity)
        // ✅ Match the same authenticators rule (Biometric + Screen Lock Credentials)
        val result = manager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        val available = result == BiometricManager.BIOMETRIC_SUCCESS

        if (BuildConfig.DEBUG) {
            Log.d(
                "BiometricSetupHelper",
                "Auth available: $available (result code: $result)"
            )
        }
        return available
    }

    fun checkAndPromptSetup(): Boolean {
        if (isAuthAvailable()) return true
        showSetupDialog()
        return false
    }

    private fun showSetupDialog() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("🔐 Opstel Aparaatsekuriteit")
            .setMessage(
                "Om jou pastorale gegewens te beskerm, moet jy eers jou " +
                        "Android-toestel se skermslot (PIN, patroon of wagwoord) opstel.\n\n" +
                        "Stel dit in by jou toestel se instellings, en kom terug na WinkerkReader."
            )
            .setPositiveButton("Gaan na Instellings") { _, _ ->
                openAndroidSecuritySettings()
            }
            .setNegativeButton("Kanselleer") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    fun openAndroidSecuritySettings() {
        val intents = listOf(
            Intent("android.app.action.SET_NEW_PASSWORD"),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                activity.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    fun reCheckAuthAvailability(): Boolean = isAuthAvailable()

    fun showInfoDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}