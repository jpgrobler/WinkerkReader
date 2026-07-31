package za.co.jpsoft.winkerkreader.ui.activities

import za.co.jpsoft.winkerkreader.utils.AppAuthGuard
import za.co.jpsoft.winkerkreader.utils.SettingsManager

/**
 * Base for Activities that require the biometric/PIN app‑lock.
 * - Automatically checks background timeout on every `onResume`.
 * - Exposes [appAuthGuard] so subclasses can call `guardIfNeeded()` during `onCreate`.
 *
 * Usage in subclasses:
 *   class MainActivity : AuthBaseActivity() {
 *       override fun onCreate(savedInstanceState: Bundle?) {
 *           super.onCreate(savedInstanceState)
 *           appAuthGuard.guardIfNeeded(
 *               onAuthenticated = { /* initialise UI */ }
 *           )
 *       }
 *
 *       override fun onResumeAfterAuth() {
 *           // Called when the session is still valid (or after re‑auth)
 *           // Add any Activity‑specific resume logic here.
 *       }
 *   }
 */
abstract class AuthBaseActivity : BaseActivity() {

    /**
     * Shared [AppAuthGuard] instance for this Activity.
     * Exposed as `protected` so subclasses can call [AppAuthGuard.guardIfNeeded]
     * on the same instance that [onResume] uses for timeout checks.
     */
    val appAuthGuard: AppAuthGuard by lazy {
        AppAuthGuard(this, SettingsManager.getInstance(this))
    }

    /**
     * Override this to perform actions after authentication is confirmed
     * (either immediately on resume if the session is valid, or after a re‑auth).
     */
    open fun onResumeAfterAuth() {}

    override fun onResume() {
        super.onResume()
        appAuthGuard.checkOnResume(onAuthenticated = { onResumeAfterAuth() })
    }
}