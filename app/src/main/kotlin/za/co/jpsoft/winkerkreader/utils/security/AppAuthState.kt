package za.co.jpsoft.winkerkreader.utils.security

import android.os.SystemClock
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig

/**
 * Enhanced session & timeout management with:
 *   - Active-session grace period (forgiving for rapid app switches)
 *   - Context-aware background timeout logic
 *   - Explicit "lock now" capability (for emergency hand-offs)
 *   - Session tracking & logging for debugging
 *
 * **Timeout Modes:**
 *   - `Long.MAX_VALUE` = Never lock (only on restart)
 *   - `0L` = Always lock on foreground
 *   - `N ms` = Lock after N ms away from app
 *
 * **Grace Period Behavior:**
 *   Active session (< 10s since last activity) = forgiving (timeout extended)
 *   Inactive session (> 10s away) = strict (timeout applies)
 */
object AppAuthState {

    private const val TAG = "AppAuthState"

    // ─── Grace period constants ──────────────────────────────────────────────
    /** If user returns within this window (during active session), skip re-prompt */
    private const val ACTIVE_SESSION_GRACE_MS = 10_000L  // 10 seconds

    /** After this inactivity, grace period ends and strict timeout applies */
    private const val GRACE_PERIOD_INACTIVITY_MS = 10_000L

    // ─── Session state ──────────────────────────────────────────────────────
    var isAuthenticated: Boolean = false
        private set

    var sessionStarted: Boolean = false
        private set

    // ─── Timing & grace period ──────────────────────────────────────────────
    private var lastAuthElapsedMs: Long = 0L
    private var lastForegroundElapsedMs: Long = 0L  // Track app resume time
    private var backgroundElapsedMs: Long = 0L      // When app went to background
    private var backgroundTimestamp: Long = 0L      // Backup timestamp

    /** Configured timeout (read from SecurityPrefs) */
    var backgroundTimeoutMs: Long = Long.MAX_VALUE

    /** Override: force-lock regardless of timeout setting */
    private var forceLockRequested: Boolean = false

    // ─── Mode tracking ──────────────────────────────────────────────────────
    enum class SessionMode {
        AUTHENTICATED,  // User is actively using the app
        GRACE_PERIOD,   // Recently backgrounded, grace window active
        TIMEOUT,        // Timeout elapsed, lock required
        FORCE_LOCK      // User explicitly locked the app
    }

    private var lastSessionMode: SessionMode = SessionMode.TIMEOUT

    // ─── Debugging / logging ────────────────────────────────────────────────
    private data class SessionEvent(
        val timestamp: Long,
        val event: String,
        val details: String = ""
    )

    private val sessionLog = mutableListOf<SessionEvent>()
    private const val MAX_LOG_ENTRIES = 50

    fun getSessionLog(): List<String> {
        return sessionLog.map { "${it.timestamp}: ${it.event} ${it.details}" }
    }

    private fun logEvent(event: String, details: String = "") {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "$event $details")
        }
        sessionLog.add(SessionEvent(SystemClock.elapsedRealtime(), event, details))
        if (sessionLog.size > MAX_LOG_ENTRIES) sessionLog.removeAt(0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reset authentication state on fresh app launch (e.g., after process death).
     */
    fun resetForFreshLaunch() {
        isAuthenticated = false
        sessionStarted = false
        backgroundTimestamp = 0L
        backgroundElapsedMs = 0L
        lastForegroundElapsedMs = 0L
        lastAuthElapsedMs = 0L
        forceLockRequested = false
        lastSessionMode = SessionMode.TIMEOUT
        logEvent("RESET", "Fresh app launch")
    }

    /**
     * Called immediately after user authenticates (biometric/credential success).
     * Starts or resumes an active session.
     */
    fun markAuthenticated() {
        sessionStarted = true
        isAuthenticated = true
        lastAuthElapsedMs = SystemClock.elapsedRealtime()
        lastForegroundElapsedMs = lastAuthElapsedMs
        backgroundElapsedMs = 0L
        backgroundTimestamp = 0L
        forceLockRequested = false
        lastSessionMode = SessionMode.AUTHENTICATED
        logEvent("AUTHENTICATED", "User passed biometric/credential check")
    }

    /**
     * Called when app moves to background (onPause, onStop).
     * Records the timestamp for timeout calculation.
     */
    fun onAppBackgrounded() {
        if (!isAuthenticated) return

        if (backgroundElapsedMs == 0L) {
            backgroundElapsedMs = SystemClock.elapsedRealtime()
            backgroundTimestamp = backgroundElapsedMs  // Backup for compatibility
            logEvent("BACKGROUNDED", "at elapsed time ${backgroundElapsedMs}ms")
        }
    }

    /**
     * Called when app returns to foreground (onResume).
     * Updates foreground timestamp for grace period calculation.
     */
    fun onAppForegrounded() {
        if (!isAuthenticated) return

        lastForegroundElapsedMs = SystemClock.elapsedRealtime()
        logEvent("FOREGROUNDED", "at elapsed time ${lastForegroundElapsedMs}ms")
    }

    /**
     * Explicitly lock the app immediately (e.g., user triggered "Lock App Now").
     * Does NOT force re-auth unless checkBackgroundTimeout() is called.
     */
    fun lockAppNow() {
        forceLockRequested = true
        isAuthenticated = false
        backgroundElapsedMs = SystemClock.elapsedRealtime()
        backgroundTimestamp = backgroundElapsedMs
        lastSessionMode = SessionMode.FORCE_LOCK
        logEvent("LOCK_REQUESTED", "User triggered explicit lock")
    }

    /**
     * Check if the session should remain valid after returning from background.
     * Returns `true` if still authenticated (within grace/timeout window).
     * Returns `false` if timeout expired or force-lock was requested.
     *
     * **Grace Period Logic:**
     *   - If user was gone for < 10s (ACTIVE_SESSION_GRACE_MS), extend timeout
     *   - If user was gone for > 10s but timeout not yet expired, grant grace
     *   - If timeout expired OR force-lock requested, invalidate session
     */
    fun checkBackgroundTimeout(): Boolean {
        if (!isAuthenticated) return false
        if (backgroundElapsedMs == 0L) return true  // Never backgrounded
        if (backgroundTimeoutMs == Long.MAX_VALUE) return true  // No timeout configured

        // ─── Force-lock takes precedence ───────────────────────────────────
        if (forceLockRequested) {
            logEvent("SESSION_INVALID", "Force-lock was requested")
            lastSessionMode = SessionMode.FORCE_LOCK
            return false
        }

        // ─── Calculate actual away time ─────────────────────────────────────
        val now = SystemClock.elapsedRealtime()
        val awayTimeMs = now - backgroundElapsedMs

        // ─── Grace period: if gone < 10s, always allow ─────────────────────
        if (awayTimeMs < ACTIVE_SESSION_GRACE_MS) {
            logEvent("GRACE_PERIOD", "Away ${awayTimeMs}ms < ${ACTIVE_SESSION_GRACE_MS}ms grace")
            lastSessionMode = SessionMode.GRACE_PERIOD
            backgroundElapsedMs = 0L  // Clear so next background resets the timer
            return true
        }

        // ─── After grace period: check strict timeout ─────────────────────
        if (awayTimeMs > backgroundTimeoutMs) {
            logEvent("SESSION_EXPIRED", "Away ${awayTimeMs}ms > timeout ${backgroundTimeoutMs}ms")
            isAuthenticated = false
            lastSessionMode = SessionMode.TIMEOUT
            return false
        }

        // ─── Still within timeout, but past grace period ───────────────────
        logEvent("TIMEOUT_ACTIVE", "Away ${awayTimeMs}ms, timeout ${backgroundTimeoutMs}ms")
        lastSessionMode = SessionMode.AUTHENTICATED  // Still valid, but warning
        backgroundElapsedMs = 0L  // Clear, ready for next background
        return true
    }

    /**
     * Get current session mode (for UI, logging, or analytics).
     */
    fun getSessionMode(): SessionMode = lastSessionMode

    /**
     * Check if we're in the grace period (rapid app-switching scenario).
     * Useful for deciding whether to show a "returning to app" animation vs re-prompt.
     */
    fun isInGracePeriod(): Boolean {
        if (!isAuthenticated || backgroundElapsedMs == 0L) return false
        if (backgroundTimeoutMs == Long.MAX_VALUE) return false

        val now = SystemClock.elapsedRealtime()
        val awayTimeMs = now - backgroundElapsedMs
        return awayTimeMs < ACTIVE_SESSION_GRACE_MS
    }

    /**
     * Estimate remaining grace period time (in milliseconds).
     * Returns 0 if not in grace period, or negative if grace has ended.
     */
    fun getRemainingGraceMs(): Long {
        if (!isInGracePeriod()) return 0L
        val now = SystemClock.elapsedRealtime()
        val awayTimeMs = now - backgroundElapsedMs
        return ACTIVE_SESSION_GRACE_MS - awayTimeMs
    }

    /**
     * Get timestamp of last successful authentication (for debugging).
     */
    fun getLastAuthElapsedMs(): Long = lastAuthElapsedMs

    /**
     * Get background elapsed time (for debugging).
     */
    fun getBackgroundElapsedMs(): Long = backgroundElapsedMs

    // ─────────────────────────────────────────────────────────────────────────
    // Test & diagnostics
    // ─────────────────────────────────────────────────────────────────────────

    internal fun resetForTest() {
        isAuthenticated = false
        sessionStarted = false
        lastAuthElapsedMs = 0L
        backgroundElapsedMs = 0L
        lastForegroundElapsedMs = 0L
        backgroundTimestamp = 0L
        forceLockRequested = false
        lastSessionMode = SessionMode.TIMEOUT
    }
}