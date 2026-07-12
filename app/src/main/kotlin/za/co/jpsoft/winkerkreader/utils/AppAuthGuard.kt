package za.co.jpsoft.winkerkreader.utils

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import za.co.jpsoft.winkerkreader.R

/**
 * Manages the full-screen authentication overlay shown at app startup.
 *
 * Usage in MainActivity.onCreate(), BEFORE startupCoordinator.runOnCreate():
 *
 *   authGuard = AppAuthGuard(this, settingsManager)
 *   authGuard.guardIfNeeded(
 *       onAuthenticated = { startupCoordinator.runOnCreate() }
 *   )
 *
 * If biometric auth is disabled in settings, or the user has already
 * authenticated this session, [onAuthenticated] is called immediately.
 */
class AppAuthGuard(
    private val activity: FragmentActivity,
    private val settingsManager: SettingsManager
) {
    private var overlayView: View? = null
    private val authManager = NoteAuthManager(activity)

    /**
     * Shows the lock screen and prompts for biometric/PIN if needed.
     * Calls [onAuthenticated] immediately if auth is not required.
     *
     * @param onAuthenticated Runs on the main thread after successful auth,
     *                        or immediately if auth is disabled / already done.
     */
    fun guardIfNeeded(onAuthenticated: () -> Unit) {
        AppAuthState.backgroundTimeoutMs = settingsManager.appBiometricTimeoutMs

        // Skip if biometric app-lock is turned off in settings
        if (!settingsManager.appBiometricEnabled) {
            onAuthenticated()
            return
        }

        // Skip if already authenticated this session
        if (AppAuthState.isAuthenticated) {
            onAuthenticated()
            return
        }

        // Skip if no PIN/biometric set up on the device
        if (!NoteAuthManager.isAuthAvailable(activity)) {
            onAuthenticated()
            return
        }

        // Show overlay and prompt
        showOverlay()
        promptAuth(onAuthenticated)
    }

    /**
     * Call from [Activity.onResume] to enforce the background timeout.
     * If the timeout has elapsed, the overlay is re-shown.
     *
     * @param onAuthenticated Called if re-auth succeeds or is not needed.
     */
    fun checkOnResume(onAuthenticated: () -> Unit) {
        AppAuthState.backgroundTimeoutMs = settingsManager.appBiometricTimeoutMs

        if (!settingsManager.appBiometricEnabled) return

        // Only check timeout if the app was actually backgrounded
        val stillValid = AppAuthState.checkBackgroundTimeout()

        if (!stillValid) {
            showOverlay()
            promptAuth(onAuthenticated)
        }
    }

    // ── Private ────────────────────────────────────────────────────────────

    private fun showOverlay() {
        if (overlayView != null) return          // already shown

        val root = activity.window.decorView
            .findViewById<ViewGroup>(android.R.id.content)

        val overlay = LayoutInflater.from(activity)
            .inflate(R.layout.overlay_app_auth, root, false)

        overlay.findViewById<Button>(R.id.btnAuthRetry).setOnClickListener {
            promptAuth {}
        }

        root.addView(overlay)
        overlayView = overlay
    }

    private fun dismissOverlay() {
        overlayView?.let { overlay ->
            val root = activity.window.decorView
                .findViewById<ViewGroup>(android.R.id.content)
            root.removeView(overlay)
            overlayView = null
        }
    }

    private fun showRetryButton() {
        overlayView?.findViewById<Button>(R.id.btnAuthRetry)?.visibility = View.VISIBLE
        overlayView?.findViewById<TextView>(R.id.tvAuthStatus)?.text =
            activity.getString(R.string.auth_misluk_probeer_weer)
    }

    private fun promptAuth(onAuthenticated: () -> Unit) {
        overlayView?.findViewById<Button>(R.id.btnAuthRetry)?.visibility = View.GONE
        overlayView?.findViewById<TextView>(R.id.tvAuthStatus)?.text =
            activity.getString(R.string.auth_wag_verifikasie)

        authManager.authenticate(
            onSuccess = {
                AppAuthState.markAuthenticated()
                dismissOverlay()
                onAuthenticated()
            },
            onFailure = { _ ->
                showRetryButton()
            }
        )
    }
}
