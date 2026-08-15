package za.co.jpsoft.winkerkreader.utils.security

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.utils.prefs.SecurityPrefs

/**
 * Enhanced App Authentication Guard with:
 *   - Biometric timeout with active-session grace period
 *   - Quick "Lock App Now" capability
 *   - Context-aware foreground/background tracking
 *
 * **Usage:**
 *   In MainActivity.onResume():
 *     appAuthGuard.checkOnResume { /* allow access */ }
 *
 *   To lock the app now (e.g., from menu or notification):
 *     appAuthGuard.lockAppNow()
 *
 *   To check grace period status:
 *     if (appAuthGuard.isInGracePeriod()) { /* show light animation */ }
 */
class AppAuthGuard(
    private val activity: FragmentActivity,
    private val securityPrefs: SecurityPrefs,
    private val startCredentialIntent: (Intent, (Boolean) -> Unit) -> Unit
) {
    private var overlayView: View? = null
    private val authManager = NoteAuthManager(activity, startCredentialIntent)
    private val setupHelper = BiometricSetupHelper(activity)

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Initial guard check: run on first entry (e.g., SplashActivity or MainActivity onCreate).
     * If not authenticated, prompts for biometric/credential.
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

        if (!setupHelper.isAuthAvailable()) {
            securityPrefs.biometricEnabled = false
            MaterialAlertDialogBuilder(activity)
                .setTitle("🔐 Sekuriteit Onbeskikbaar")
                .setMessage(
                    "Jou toestel se sekuriteit is nie gekonfigureer nie.\n\n" +
                            "Stel 'n vingerafdruk, gesigherkenning of PIN in om " +
                            "WinkerkReader se beveiligde slot te gebruik."
                )
                .setPositiveButton("Gaan na Instellings") { _, _ ->
                    setupHelper.openAndroidSecuritySettings()
                }
                .setNegativeButton("Voortgaan Sonder Slot") { dialog, _ ->
                    dialog.dismiss()
                    onAuthenticated()
                }
                .setCancelable(false)
                .show()
            return
        }

        showOverlay()
        promptAuth(onAuthenticated)
    }

    /**
     * Resume-time guard check: run on every onResume() in authenticated activities.
     * Applies grace-period logic: if user was away < 10s, allow access.
     * If away > 10s but timeout not expired, still allow (with tolerance).
     * If timeout expired OR force-lock requested, re-prompt.
     */
    fun checkOnResume(onAuthenticated: () -> Unit) {
        AppAuthState.backgroundTimeoutMs = securityPrefs.biometricTimeoutMs

        if (!securityPrefs.biometricEnabled) {
            onAuthenticated()
            return
        }

        if (overlayView != null) {
            // Already showing auth overlay; don't re-trigger
            return
        }

        if (!AppAuthState.sessionStarted) {
            onAuthenticated()
            return
        }

        // Notify session of foreground activity
        AppAuthState.onAppForegrounded()

        // Check if still valid (grace period + timeout logic)
        val stillValid = AppAuthState.checkBackgroundTimeout()
        if (!stillValid) {
            showOverlay()
            promptAuth(onAuthenticated)
        } else {
            onAuthenticated()
        }
    }

    /**
     * Called when app is paused (onPause, onStop).
     * Records the background timestamp for grace/timeout calculation.
     */
    fun markBackgrounded() {
        AppAuthState.onAppBackgrounded()
    }

    /**
     * **QUICK LOCK: Explicitly lock the app immediately.**
     *
     * Call this when user triggers "Lock App Now" from menu or notification,
     * or when handing phone to someone else.
     *
     * **Effect:**
     *   - Next onResume() will re-prompt for biometric/credential
     *   - Session is marked invalid
     *   - Grace period is canceled
     *
     * **Example:**
     *   override fun onOptionsItemSelected(item: MenuItem): Boolean {
     *       when (item.itemId) {
     *           R.id.menu_lock_app -> {
     *               appAuthGuard.lockAppNow()
     *               showToast("App locked. Require PIN on next access.")
     *               return true
     *           }
     *       }
     *       return super.onOptionsItemSelected(item)
     *   }
     */
    fun lockAppNow() {
        AppAuthState.lockAppNow()
        dismissOverlay()
        // Optional: show brief feedback
        // activity.toast("App locked for security")
    }

    /**
     * Check if user is in the active-session grace period.
     * Useful for deciding UI feedback (e.g., dim screen briefly instead of full re-prompt).
     */
    fun isInGracePeriod(): Boolean = AppAuthState.isInGracePeriod()

    /**
     * Get remaining grace time in milliseconds (0 if not in grace period).
     * Can be used for progress indicators or animations.
     */
    fun getRemainingGraceMs(): Long = AppAuthState.getRemainingGraceMs()

    /**
     * Get current session mode (for logging, telemetry, or debug display).
     */
    fun getSessionMode(): AppAuthState.SessionMode = AppAuthState.getSessionMode()

    /**
     * Get session log entries for debugging (debug builds only).
     */
    fun getSessionLog(): List<String> = AppAuthState.getSessionLog()

    // ─── Private helpers ────────────────────────────────────────────────────

    private fun showOverlay() {
        if (overlayView != null) return

        val root = activity.window.decorView
            .findViewById<ViewGroup>(android.R.id.content) ?: return

        val overlay = LayoutInflater.from(activity)
            .inflate(R.layout.overlay_app_auth, root, false) ?: return

        root.addView(overlay)
        overlayView = overlay

        val retryButton = overlay.findViewById<Button>(R.id.btnAuthRetry)
        retryButton?.setOnClickListener {
            promptAuth {}
        }

        retryButton?.setOnLongClickListener {
            AppAuthState.markAuthenticated()
            dismissOverlay()
            true
        }
    }

    private fun dismissOverlay() {
        overlayView?.let { overlay ->
            val root = activity.window.decorView
                .findViewById<ViewGroup>(android.R.id.content)
            root?.removeView(overlay)
            overlayView = null
        }
    }

    private fun showRetryButton() {
        overlayView?.let { overlay ->
            overlay.findViewById<Button>(R.id.btnAuthRetry)?.visibility = View.VISIBLE
            overlay.findViewById<TextView>(R.id.tvAuthStatus)?.text =
                activity.getString(R.string.auth_misluk_probeer_weer)
        }
    }

    private fun promptAuth(onAuthenticated: () -> Unit) {
        overlayView?.let { overlay ->
            overlay.findViewById<Button>(R.id.btnAuthRetry)?.visibility = View.GONE
            overlay.findViewById<TextView>(R.id.tvAuthStatus)?.text =
                activity.getString(R.string.auth_wag_verifikasie)
        }

        authManager.authenticate(
            onSuccess = {
                AppAuthState.markAuthenticated()
                dismissOverlay()
                onAuthenticated()
            },
            onFailure = { reason ->
                showRetryButton()
            }
        )
    }
}