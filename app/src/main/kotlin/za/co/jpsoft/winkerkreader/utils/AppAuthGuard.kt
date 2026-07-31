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
 * Manages the full-screen authentication overlay shown at app startup and after a
 * background timeout.
 *
 * ## Startup (called from MainActivity.onCreate)
 *
 *   appAuthGuard.guardIfNeeded(
 *       onAuthenticated = { startupCoordinator.runOnCreate() }
 *   )
 *
 * ## Timeout re-auth (called automatically from BaseActivity.onResume)
 *
 * [BaseActivity] holds an instance of this class and calls [checkOnResume] on every
 * `onResume`.  Nothing needs to be wired manually in subclasses.
 *
 * If biometric auth is disabled in settings, or the user has already authenticated
 * within the configured timeout, both entry points are no-ops.
 *
 * ## AppAuthState contract (what AppAuthState must provide)
 *
 *   var backgroundTimeoutMs: Long          — writable, set before each check
 *   val isAuthenticated: Boolean           — true while session is valid
 *   val sessionStarted: Boolean            — true once markAuthenticated() has ever
 *                                            been called; never resets to false.
 *                                            Used to distinguish "fresh process, no
 *                                            auth yet" from "was authenticated but
 *                                            timed out".
 *   fun markAuthenticated()                — sets isAuthenticated = true,
 *                                            sessionStarted = true, resets the
 *                                            background timer
 *   fun checkBackgroundTimeout(): Boolean  — returns true if the session is still
 *                                            within the timeout window; sets
 *                                            isAuthenticated = false and returns false
 *                                            when the timeout has elapsed
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
        AppAuthState.backgroundTimeoutMs = settingsManager.security.biometricTimeoutMs

        // Skip if biometric app-lock is turned off in settings
        if (!settingsManager.security.biometricEnabled) {
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
     * Enforces the background timeout on every [Activity.onResume].
     *
     * Called by [BaseActivity.onResume] — do not call this directly from individual
     * Activities.
     *
     * Two early-exit guards prevent spurious double-prompts:
     *
     * 1. **Initial auth in progress** — if [guardIfNeeded]'s overlay is already
     *    visible (`overlayView != null`), the biometric prompt is already running.
     *    Adding a second prompt on top would create a confusing stack.
     *
     * 2. **Fresh process start** — [AppAuthState.checkBackgroundTimeout] returns
     *    `false` for a brand-new session that has never been authenticated, which
     *    would trigger a second overlay before [guardIfNeeded] has run.  The
     *    [AppAuthState.sessionStarted] flag distinguishes "never authenticated"
     *    from "was authenticated but timed out", so timeout re-auth is only
     *    attempted after the first successful [guardIfNeeded] call.
     *
     * @param onAuthenticated Called when re-auth succeeds.  For non-MainActivity
     *                        Activities this is typically `{}` — the overlay is
     *                        dismissed and the Activity content is already loaded.
     */
    // AppAuthGuard.kt
    fun checkOnResume(onAuthenticated: () -> Unit) {
        AppAuthState.backgroundTimeoutMs = settingsManager.security.biometricTimeoutMs

        // If lock is off, auth is not required – run the callback immediately.
        if (!settingsManager.security.biometricEnabled) {
            onAuthenticated()
            return
        }

        // Guard 1: don't add a second prompt while guardIfNeeded is still running.
        if (overlayView != null) return   // still pending, don't call callback

        // Guard 2: don't enforce a timeout before the first successful auth.
        if (!AppAuthState.sessionStarted) return   // initial auth not done yet

        val stillValid = AppAuthState.checkBackgroundTimeout()
        if (!stillValid) {
            showOverlay()
            promptAuth(onAuthenticated)   // callback runs after successful re‑auth
        } else {
            // Session is still valid – run the callback immediately.
            onAuthenticated()
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