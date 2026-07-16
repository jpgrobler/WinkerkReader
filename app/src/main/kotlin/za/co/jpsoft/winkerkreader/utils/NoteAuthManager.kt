package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import za.co.jpsoft.winkerkreader.utils.NoteAuthManager.Companion.REVEAL_TIMEOUT_MS

/**
 * Manages biometric / device-PIN authentication for confidential pastoral notes.
 *
 * Usage:
 *   NoteAuthManager(activity).authenticate(
 *       onSuccess = { /* reveal note */ },
 *       onFailure = { /* optional feedback */ }
 *   )
 */
class NoteAuthManager(private val activity: FragmentActivity) {

    companion object {
        /** How long (ms) a revealed note stays visible before auto-hiding. */
        const val REVEAL_TIMEOUT_MS = 30_000L

        /**
         * Returns true if the device has ANY form of authentication set up
         * (biometric or PIN/pattern/password).
         * Use this to decide whether to show the lock icon at all.
         */
        fun isAuthAvailable(context: Context): Boolean {
            val manager = BiometricManager.from(context)
            val result = manager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            return result == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    private val executor = ContextCompat.getMainExecutor(activity)
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Shows the biometric/PIN prompt.
     * Falls back to device PIN/pattern/password if biometric is not enrolled.
     *
     * @param onSuccess Called on the main thread when authentication succeeds.
     * @param onFailure Called on the main thread when authentication fails or is cancelled.
     *                  [reason] is a user-facing message.
     */
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
                    val message = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED -> "Verifikasie gekanselleer"
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> "Verifikasie gekanselleer"
                        BiometricPrompt.ERROR_CANCELED -> "Verifikasie gekanselleer"
                        else -> errString.toString()
                    }
                    onFailure(message)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Vertroulike nota")
            .setSubtitle("Verifieer om nota te lees")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()

        prompt.authenticate(promptInfo)
    }

    /**
     * Schedules [onHide] to be called after [REVEAL_TIMEOUT_MS] milliseconds.
     * Call this immediately after revealing a confidential note.
     * Returns a [Runnable] token that can be passed to [cancelAutoHide] if needed.
     */
    fun scheduleAutoHide(onHide: () -> Unit): Runnable {
        val runnable = Runnable { onHide() }
        handler.postDelayed(runnable, REVEAL_TIMEOUT_MS)
        return runnable
    }

    /** Cancels a previously scheduled auto-hide. */
    fun cancelAutoHide(token: Runnable) {
        handler.removeCallbacks(token)
    }
}
