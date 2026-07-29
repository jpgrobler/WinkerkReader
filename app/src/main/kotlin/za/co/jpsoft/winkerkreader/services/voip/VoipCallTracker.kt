package za.co.jpsoft.winkerkreader.services.voip

import android.os.Handler
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Tracks active VoIP calls (incoming) and manages timeouts, pruning, and reconciliation.
 */
class VoipCallTracker(
    private val mainHandler: Handler,
    private val incomingCallTimeoutMs: Long = 5000L
) {

    data class TrackedVoipCall(
        val callId: String,
        val startTime: Long,
        val number: String,
        val displayName: String?,
        val appName: String,
        var logged: Boolean = false,
        var timeoutRunnable: Runnable? = null
    )

    private val activeVoipCalls = ConcurrentHashMap<String, TrackedVoipCall>()

    /**
     * Track a new incoming call. If a call with the same key already exists, it is not replaced.
     * @return true if the call was newly tracked, false if it already existed.
     */
    fun trackIncomingCall(key: String, trackedCall: TrackedVoipCall): Boolean {
        val existing = activeVoipCalls.putIfAbsent(key, trackedCall)
        if (existing != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Already tracking incoming call for key $key")
            return false
        }
        // Schedule timeout
        val timeoutRunnable = Runnable {
            val call = activeVoipCalls.remove(key)
            if (call != null && !call.logged) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Timeout for incoming call, removing tracking (not logging)")
            }
        }
        trackedCall.timeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, incomingCallTimeoutMs)
        return true
    }

    /**
     * Mark a tracked call as logged (so it won't be processed again) and remove it.
     * Returns the tracked call if it was present and not already logged, else null.
     */
    fun markCallLogged(key: String): TrackedVoipCall? {
        val call = activeVoipCalls[key] ?: return null
        if (call.logged) return null
        call.logged = true
        call.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        activeVoipCalls.remove(key)
        return call
    }

    /**
     * Remove a tracked call without logging it (e.g., for a missed or ended state that we handle elsewhere).
     * Returns the removed call if it was present, else null.
     */
    fun removeTrackedCall(key: String): TrackedVoipCall? {
        val call = activeVoipCalls.remove(key)
        call?.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        return call
    }

    /**
     * Get a tracked call without removing it.
     */
    fun getTrackedCall(key: String): TrackedVoipCall? = activeVoipCalls[key]

    /**
     * Find and remove a tracked call that matches the given number and time (within tolerance).
     * Returns the removed call if found, else null.
     */
    fun findAndRemoveMatching(number: String, time: Long, toleranceMs: Long): TrackedVoipCall? {
        val iter = activeVoipCalls.entries.iterator()
        while (iter.hasNext()) {
            val (key, tracked) = iter.next()
            if (!tracked.logged && tracked.number == number &&
                kotlin.math.abs(tracked.startTime - time) < toleranceMs) {
                // Found match
                tracked.logged = true // mark logged so we don't process again
                tracked.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                iter.remove()
                return tracked
            }
        }
        return null
    }

    /**
     * Prune calls older than the given cutoff.
     */
    fun pruneStaleCalls(cutoff: Long) {
        activeVoipCalls.entries.removeIf { it.value.startTime < cutoff }
    }

    /**
     * Clear all tracked calls and cancel timeouts.
     */
    fun clear() {
        activeVoipCalls.values.forEach { it.timeoutRunnable?.let { mainHandler.removeCallbacks(it) } }
        activeVoipCalls.clear()
    }

    companion object {
        private const val TAG = "VoipCallTracker"
    }
}