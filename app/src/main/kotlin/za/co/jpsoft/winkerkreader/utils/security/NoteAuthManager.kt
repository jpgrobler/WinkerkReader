// In main/kotlin/za/co/jpsoft/winkerkreader/utils/security/NoteAuthManager.kt

package za.co.jpsoft.winkerkreader.utils.security

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class NoteAuthManager(
    private val activity: FragmentActivity,
    private val startCredentialIntent: (Intent, (Boolean) -> Unit) -> Unit
) {

    companion object {
        const val REVEAL_TIMEOUT_MS = 30_000L

        fun isAuthAvailable(context: Context): Boolean {
            val biometricManager = BiometricManager.from(context)
            val authStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                biometricManager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            } else {
                biometricManager.canAuthenticate(DEVICE_CREDENTIAL)
            }
            return authStatus == BiometricManager.BIOMETRIC_SUCCESS ||
                    authStatus == BiometricManager.BIOMETRIC_STATUS_UNKNOWN ||
                    authStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        }
    }

    private val executor = ContextCompat.getMainExecutor(activity)
    private val handler = Handler(Looper.getMainLooper())

    fun authenticate(
        onSuccess: () -> Unit,
        onFailure: (reason: String) -> Unit = {}
    ) {
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    if (errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
                        errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT
                    ) {
                        launchDeviceCredential(onSuccess, onFailure)
                        return
                    }

                    val message = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED -> "Verifikasie gekanselleer"
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> "Verifikasie gekanselleer"
                        BiometricPrompt.ERROR_CANCELED -> "Verifikasie gekanselleer"
                        BiometricPrompt.ERROR_LOCKOUT -> "Te veel mislukte pogings. Probeer later weer."
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Te veel mislukte pogings. Gebruik jou skermslot om te ontblokkeer."
                        else -> errString.toString()
                    }
                    onFailure(message)
                }
            }
        )

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Vertroulik Nota")
            .setSubtitle("Verifieer om hierdie vertroulike nota te lees")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        }

        try {
            prompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            onFailure("Kon nie sekuriteitsvenster open nie: ${e.message}")
        }
    }

    private fun launchDeviceCredential(
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km == null || !km.isKeyguardSecure) {
            onFailure("Geen skermslot gekonfigureer nie.")
            return
        }

        val intent = km.createConfirmDeviceCredentialIntent(
            "Vertroulik Nota",
            "Voer jou PIN, patroon of wagwoord in om die nota te lees"
        )

        if (intent == null) {
            onFailure("Kon nie skermslot-venster oopmaak nie.")
            return
        }

        startCredentialIntent(intent) { success ->
            if (success) {
                onSuccess()
            } else {
                onFailure("Verifikasie misluk of gekanselleer")
            }
        }
    }

    fun scheduleAutoHide(onHide: () -> Unit): Runnable {
        val runnable = Runnable { onHide() }
        handler.postDelayed(runnable, REVEAL_TIMEOUT_MS)
        return runnable
    }

    fun cancelAutoHide(token: Runnable) {
        handler.removeCallbacks(token)
    }
}