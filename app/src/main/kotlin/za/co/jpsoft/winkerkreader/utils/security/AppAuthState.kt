package za.co.jpsoft.winkerkreader.utils.security

import android.os.SystemClock

object AppAuthState {

    var isAuthenticated: Boolean = false
        private set

    var sessionStarted: Boolean = false
        private set

    private var lastAuthElapsedMs: Long = 0L
    var backgroundTimeoutMs: Long = Long.MAX_VALUE
    private var backgroundTimestamp: Long = 0L

    fun resetForFreshLaunch() {
        isAuthenticated = false
        sessionStarted = false
        backgroundTimestamp = 0L
    }

    fun markAuthenticated() {
        sessionStarted = true
        isAuthenticated = true
        lastAuthElapsedMs = SystemClock.elapsedRealtime()
        backgroundTimestamp = 0L
    }

    fun onAppBackgrounded() {
        // Record timestamp only if authenticated.
        // We track actual backgrounding; device lock screens will trigger this via lifecycle,
        // but we evaluate it strictly against backgroundTimeoutMs.
        if (isAuthenticated && backgroundTimestamp == 0L) {
            backgroundTimestamp = SystemClock.elapsedRealtime()
        }
    }

    fun onAppForegrounded() {
        // Optional: If you want unlocking the device to preserve the session
        // as long as the timeout hasn't elapsed, do not reset backgroundTimestamp here.
        // Leaving backgroundTimestamp intact allows checkBackgroundTimeout() to correctly
        // compute if the total away-time exceeded backgroundTimeoutMs.
    }

    fun checkBackgroundTimeout(): Boolean {
        if (!isAuthenticated) return false
        if (backgroundTimestamp == 0L) return true
        if (backgroundTimeoutMs == Long.MAX_VALUE) return true

        val elapsed = SystemClock.elapsedRealtime() - backgroundTimestamp
        val expired = elapsed > backgroundTimeoutMs

        if (expired) {
            isAuthenticated = false
        } else {
            // If the timeout hasn't expired yet (e.g., user locked the phone for 2 minutes
            // under a 5-minute timeout setting), clear the background timestamp
            // so unlocking the phone lets them right back in without re-prompting!
            backgroundTimestamp = 0L
        }

        return isAuthenticated
    }

    internal fun resetForTest() {
        isAuthenticated = false
        sessionStarted = false
        lastAuthElapsedMs = 0L
        backgroundTimestamp = 0L
    }
}