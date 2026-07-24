package za.co.jpsoft.winkerkreader.ui.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.utils.AppAuthGuard
import za.co.jpsoft.winkerkreader.utils.SettingsManager

/**
 * Shared base for every Activity that should be protected by the app-lock.
 *
 * ## What this solves
 * [AppAuthGuard.checkOnResume] was previously called only from [MainActivity.onResume].
 * Because [MainActivity] is declared `singleInstance`, every other Activity runs in a
 * separate Android task.  The recents/task-switcher can resume those tasks directly
 * without [MainActivity.onResume] ever running, so the background-timeout check was
 * silently bypassed for every screen other than the member list.
 *
 * Moving the check here means it fires in **every** Activity's `onResume`, regardless
 * of which task brought it to the foreground.
 *
 * ## Activities that MUST extend this class
 * - MainActivity
 * - LidmaatDetailActivity
 * - BedieningActivity
 * - CallLogActivity
 * - SettingsActivity
 * - TemplateManagerActivity
 * …and any future Activity that can display personal or pastoral data.
 *
 * ## Usage in MainActivity
 * Replace the local `authGuard` field with the `appAuthGuard` property exposed here,
 * so both `guardIfNeeded` (initial launch) and `checkOnResume` (timeout re-auth) share
 * the same [AppAuthGuard] instance and the same overlay state:
 *
 * ```kotlin
 * class MainActivity : BaseActivity() {
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         // appAuthGuard is provided by BaseActivity — no separate field needed.
 *         appAuthGuard.guardIfNeeded(
 *             onAuthenticated = { startupCoordinator.runOnCreate() }
 *         )
 *     }
 *
 *     // onResume: do NOT call appAuthGuard.checkOnResume() here — BaseActivity already
 *     // does it.  Add only MainActivity-specific resume logic (e.g. refreshing the list).
 * }
 * ```
 *
 * ## Usage in other Activities
 * Simply extend [BaseActivity] instead of [AppCompatActivity].  No other changes needed;
 * the timeout check is fully automatic.
 *
 * ```kotlin
 * class LidmaatDetailActivity : BaseActivity() { … }
 * ```
 */
abstract class BaseActivity : AppCompatActivity() {

    /**
     * Shared [AppAuthGuard] instance for this Activity.
     *
     * Exposed as `protected` so [MainActivity] can call [AppAuthGuard.guardIfNeeded]
     * on the same instance that [onResume] uses for [AppAuthGuard.checkOnResume],
     * keeping overlay state consistent.
     */
    protected val appAuthGuard: AppAuthGuard by lazy {
        AppAuthGuard(this, SettingsManager.getInstance(this))
    }

    /**
     * Enforces the background-timeout lock on every resume.
     *
     * - If biometric lock is disabled in settings: no-op.
     * - If the session is still within the configured timeout: no-op.
     * - If the timeout has elapsed: re-shows the auth overlay and prompts.
     *
     * [AppAuthGuard.checkOnResume] is a no-op when called before the initial
     * [AppAuthGuard.guardIfNeeded] auth has completed (guarded by
     * [AppAuthState.sessionStarted]), so there is no risk of a double prompt on
     * [MainActivity]'s first launch.
     */
    open fun onResumeAfterAuth() {}

    override fun onResume() {
        super.onResume()
        appAuthGuard.checkOnResume(onAuthenticated = { onResumeAfterAuth() })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called BEFORE setContentView()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }
}