package za.co.jpsoft.winkerkreader.utils

import android.os.SystemClock

object AppAuthState {

    var isAuthenticated: Boolean = false
        private set

    private var lastAuthElapsedMs: Long = 0L
    var backgroundTimeoutMs: Long = Long.MAX_VALUE

    /**
     * Timestamp (SystemClock.elapsedRealtime) when the app last went to the background.
     * Zero means the app has not been backgrounded since the last authentication.
     */
    private var backgroundTimestamp: Long = 0L

    fun markAuthenticated() {
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
        lastAuthElapsedMs = 0L
        backgroundTimestamp = 0L
    }
}