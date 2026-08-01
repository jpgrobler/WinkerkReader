package za.co.jpsoft.winkerkreader.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.utils.prefs.SecurityPrefs

/**
 * Manages the full-screen authentication overlay shown at app startup and after a
 * background timeout.
 */
class AppAuthGuard(
    private val activity: FragmentActivity,
    private val securityPrefs: SecurityPrefs
) {
    private var overlayView: View? = null
    private val authManager = NoteAuthManager(activity)

    /**
     * Shows the lock screen and prompts for biometric/PIN if needed.
     * Calls [onAuthenticated] immediately if auth is not required.
     */
    fun guardIfNeeded(onAuthenticated: () -> Unit) {
        AppAuthState.backgroundTimeoutMs = securityPrefs.biometricTimeoutMs

        if (!securityPrefs.biometricEnabled) {
            onAuthenticated()
            return
        }

        if (AppAuthState.isAuthenticated) {
            onAuthenticated()
            return
        }

        if (!NoteAuthManager.isAuthAvailable(activity)) {
            onAuthenticated()
            return
        }

        showOverlay()
        promptAuth(onAuthenticated)
    }

    /**
     * Enforces the background timeout on every [Activity.onResume].
     */
    fun checkOnResume(onAuthenticated: () -> Unit) {
        AppAuthState.backgroundTimeoutMs = securityPrefs.biometricTimeoutMs

        if (!securityPrefs.biometricEnabled) {
            onAuthenticated()
            return
        }

        if (overlayView != null) return

        if (!AppAuthState.sessionStarted) return

        val stillValid = AppAuthState.checkBackgroundTimeout()
        if (!stillValid) {
            showOverlay()
            promptAuth(onAuthenticated)
        } else {
            onAuthenticated()
        }
    }

    // ── Private ────────────────────────────────────────────────────────────

    private fun showOverlay() {
        if (overlayView != null) return

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