package za.co.jpsoft.winkerkreader.utils

import android.os.SystemClock
import za.co.jpsoft.winkerkreader.utils.AppAuthState.isAuthenticated
import za.co.jpsoft.winkerkreader.utils.AppAuthState.markAuthenticated

object AppAuthState {

    var isAuthenticated: Boolean = false
        private set

    /**
     * True once [markAuthenticated] has been called at least once in this process lifetime.
     * Never resets to false — not even when [isAuthenticated] is cleared by a timeout.
     *
     * Purpose: [AppAuthGuard.checkOnResume] cannot tell the difference between
     * "app just launched, guardIfNeeded hasn't run yet" and "session timed out" by
     * looking at [isAuthenticated] alone — both states leave it false.
     * This flag breaks that ambiguity:
     *   • sessionStarted = false → fresh process, skip timeout check and let
     *                              guardIfNeeded handle the initial prompt.
     *   • sessionStarted = true  → session was established at least once; enforce
     *                              the background timeout as normal.
     *
     * In-memory only — not persisted. Process death resets it, so the user always
     * goes through the normal startup auth after a cold start.
     */
    var sessionStarted: Boolean = false
        private set

    private var lastAuthElapsedMs: Long = 0L
    var backgroundTimeoutMs: Long = Long.MAX_VALUE

    /**
     * Timestamp (SystemClock.elapsedRealtime) when the app last went to the background.
     * Zero means the app has not been backgrounded since the last authentication.
     */
    private var backgroundTimestamp: Long = 0L

    fun markAuthenticated() {
        sessionStarted = true   // one-way: never reset after first successful auth
        isAuthenticated = true
        lastAuthElapsedMs = SystemClock.elapsedRealtime()
        // Reset background flag so that returning from background triggers a check only once
        backgroundTimestamp = 0L
    }

    /** Call when the app goes to background (e.g., from ProcessLifecycleOwner). */
    fun onAppBackgrounded() {
        // Only record if the user is currently authenticated
        if (isAuthenticated) {
            backgroundTimestamp = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Checks the background timeout if the app was backgrounded.
     * Returns true if authentication is still valid, false if it should be re‑prompted.
     */
    fun checkBackgroundTimeout(): Boolean {
        if (!isAuthenticated) return false
        if (backgroundTimestamp == 0L) return true   // Not backgrounded since last auth → no check
        if (backgroundTimeoutMs == Long.MAX_VALUE) return true

        val elapsed = SystemClock.elapsedRealtime() - backgroundTimestamp
        val expired = elapsed > backgroundTimeoutMs
        if (expired) {
            isAuthenticated = false
        }
        // Always clear the flag after one check (so further onResume calls don't re‑check)
        backgroundTimestamp = 0L
        return isAuthenticated
    }

    /** For testing only. */
    internal fun resetForTest() {
        isAuthenticated = false
        sessionStarted = false
        lastAuthElapsedMs = 0L
        backgroundTimestamp = 0L
    }
}