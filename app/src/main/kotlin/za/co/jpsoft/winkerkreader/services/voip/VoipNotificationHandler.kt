// VoipNotificationHandler.kt
package za.co.jpsoft.winkerkreader.services.voip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.services.OproepDetailService
import za.co.jpsoft.winkerkreader.utils.CallNotificationDiagnostics
import za.co.jpsoft.winkerkreader.utils.CallerNameResolver
import za.co.jpsoft.winkerkreader.utils.UnifiedCallMonitor
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import za.co.jpsoft.winkerkreader.utils.VoipDiagnosticHelper

/**
 * Orchestrates VoIP notification processing: detects state, extracts info,
 * tracks calls, logs to UnifiedCallMonitor, and triggers popups.
 */
class VoipNotificationHandler(
    private val context: Context,
    private val callMonitorPrefs: CallMonitorPrefs,
    private val unifiedMonitor: UnifiedCallMonitor,
    private val stateDetector: VoipCallStateDetector,
    private val infoExtractor: VoipCallInfoExtractor,
    private val callTracker: VoipCallTracker,
    private val scope: CoroutineScope,
    private val voipPackages: Map<String, String>,
    private val diagnostics: CallNotificationDiagnostics
) {

    private val TAG = "VoipNotificationHandler"

    /**
     * Process a new VoIP notification.
     */
    fun handleNotification(sbn: StatusBarNotification, appName: String) {
        val notification = sbn.notification
        val extras = notification.extras ?: return

        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(android.app.Notification.EXTRA_TEXT) ?: ""

        if (BuildConfig.DEBUG) {
            VoipDiagnosticHelper.dumpNotificationToFile(context, sbn, appName)
        }

        val category = notification.category
        val callTypeExtra = extras.getInt(android.app.Notification.EXTRA_CALL_TYPE, -1)
        val isCallStyle = callTypeExtra in 1..3

        val callState = stateDetector.detectCallState(category, callTypeExtra, isCallStyle, extras, sbn)
        val callStartTime = System.currentTimeMillis()
        val callId = "voip_${appName}_$callStartTime"

        when (callState) {
            VoipCallStateDetector.CallState.INCOMING,
            VoipCallStateDetector.CallState.SCREENING -> handleIncoming(sbn, appName, callId, callStartTime, extras, title, text)

            VoipCallStateDetector.CallState.OUTGOING -> handleOutgoing(sbn, appName, callId, callStartTime, extras, title, text)

            VoipCallStateDetector.CallState.MISSED -> handleMissed(sbn, appName, callId, callStartTime, extras, title, text)

            VoipCallStateDetector.CallState.ENDED -> handleEnded(sbn, appName, callId, callStartTime, extras, title, text)

            VoipCallStateDetector.CallState.UNKNOWN -> fallbackTextProcessing(sbn, appName)
        }
    }

    /**
     * Process removal of a notification.
     */
    fun handleRemoval(sbn: StatusBarNotification, appName: String) {
        val notificationKey = sbn.key
        val tracked = callTracker.removeTrackedCall(notificationKey)
        if (tracked != null && !tracked.logged) {
            // Incoming notification removed without a missed/ended state → assume answered
            if (BuildConfig.DEBUG) Log.d(TAG, "Incoming notification removed -> call answered: ${tracked.callId}")
            tracked.logged = true
            val endTime = System.currentTimeMillis()
            scope.launch {
                unifiedMonitor.onCallDetected(
                    callId = tracked.callId,
                    number = tracked.number,
                    direction = "incoming",
                    source = tracked.appName,
                    timestamp = tracked.startTime,
                    displayName = tracked.displayName ?: "Unknown"
                )
                unifiedMonitor.onCallEnded(tracked.callId, endTime)
            }
        }
    }

    // ---- State handlers ----

    private fun handleIncoming(
        sbn: StatusBarNotification,
        appName: String,
        callId: String,
        callStartTime: Long,
        extras: Bundle,
        title: String,
        text: String
    ) {
        val number = extractNumber(extras, title, text)
        var displayName: String? = null
        if (number.isBlank()) {
            displayName = infoExtractor.extractCallerInfo(
                title, text,
                extras.getString(android.app.Notification.EXTRA_BIG_TEXT) ?: "",
                extras.getString(android.app.Notification.EXTRA_SUB_TEXT) ?: ""
            ).takeIf { it.isNotBlank() }
        }

        val finalNumber = if (number.isNotBlank()) number else "Unknown"
        val finalName = if (number.isNotBlank()) {
            CallerNameResolver.resolve(number, context) ?: displayName
        } else {
            displayName
        }

        val trackedCall = VoipCallTracker.TrackedVoipCall(
            callId = callId,
            startTime = callStartTime,
            number = finalNumber,
            displayName = finalName,
            appName = appName,
            logged = false
        )
        callTracker.trackIncomingCall(sbn.key, trackedCall)
        if (BuildConfig.DEBUG) Log.d(TAG, "Tracked incoming call: $callId")

        // Optionally trigger popup for incoming call
        if (finalName != null) {
            triggerVoipCallerPopup(finalNumber, finalName)
        }
    }

    private fun handleOutgoing(
        sbn: StatusBarNotification,
        appName: String,
        callId: String,
        callStartTime: Long,
        extras: Bundle,
        title: String,
        text: String
    ) {
        val number = extractNumber(extras, title, text)
        val displayName = if (number.isNotBlank()) {
            CallerNameResolver.resolve(number, context)
        } else {
            infoExtractor.extractCallerInfo(
                title, text,
                extras.getString(android.app.Notification.EXTRA_BIG_TEXT) ?: "",
                extras.getString(android.app.Notification.EXTRA_SUB_TEXT) ?: ""
            ).takeIf { it.isNotBlank() }
        }

        val finalNumber = if (number.isNotBlank()) number else "Unknown"
        val finalDisplay = displayName ?: "Unknown"

        // Track for potential later end (but we already log at start)
        val tracked = VoipCallTracker.TrackedVoipCall(
            callId = callId,
            startTime = callStartTime,
            number = finalNumber,
            displayName = finalDisplay,
            appName = appName,
            logged = true
        )
        callTracker.trackIncomingCall(sbn.key, tracked) // we use trackIncomingCall but it's for tracking; we could just store without timeout

        scope.launch {
            unifiedMonitor.onCallDetected(
                callId = callId,
                number = finalNumber,
                direction = "outgoing",
                source = appName,
                timestamp = callStartTime,
                displayName = finalDisplay
            )
        }
    }

    private fun handleMissed(
        sbn: StatusBarNotification,
        appName: String,
        callId: String,
        callStartTime: Long,
        extras: Bundle,
        title: String,
        text: String
    ) {
        val number = extractNumber(extras, title, text)
        val displayName = if (number.isNotBlank()) {
            CallerNameResolver.resolve(number, context)
        } else {
            infoExtractor.extractCallerInfo(
                title, text,
                extras.getString(android.app.Notification.EXTRA_BIG_TEXT) ?: "",
                extras.getString(android.app.Notification.EXTRA_SUB_TEXT) ?: ""
            ).takeIf { it.isNotBlank() }
        }

        val finalNumber = if (number.isNotBlank()) number else "Unknown"
        val finalDisplay = displayName ?: "Unknown"

        // Check if we have a tracked call that matches this missed call (by number and time proximity)
        val matchedCall = callTracker.findAndRemoveMatching(finalNumber, callStartTime, 5000)
        if (matchedCall != null) {
            // Found matching incoming call that was missed
            scope.launch {
                unifiedMonitor.onCallDetected(
                    callId = matchedCall.callId,
                    number = matchedCall.number,
                    direction = "missed",
                    source = appName,
                    timestamp = matchedCall.startTime,
                    displayName = matchedCall.displayName ?: "Unknown"
                )
                unifiedMonitor.onCallEnded(matchedCall.callId, System.currentTimeMillis())
            }
        } else {
            // No matching tracked call, log fresh missed record
            val missedCallId = "voip_missed_${System.currentTimeMillis()}"
            scope.launch {
                unifiedMonitor.onCallDetected(
                    callId = missedCallId,
                    number = finalNumber,
                    direction = "missed",
                    source = appName,
                    timestamp = callStartTime,
                    displayName = finalDisplay
                )
                unifiedMonitor.onCallEnded(missedCallId, System.currentTimeMillis())
            }
        }
    }

    private fun handleEnded(
        sbn: StatusBarNotification,
        appName: String,
        callId: String,
        callStartTime: Long,
        extras: Bundle,
        title: String,
        text: String
    ) {
        val number = extractNumber(extras, title, text)
        // Try to find a tracked incoming call that matches number/time
        val matchedCall = callTracker.findAndRemoveMatching(number, callStartTime, 5000)
        if (matchedCall != null) {
            // Found matching incoming call that ended
            val endTime = System.currentTimeMillis()
            scope.launch {
                unifiedMonitor.onCallDetected(
                    callId = matchedCall.callId,
                    number = matchedCall.number,
                    direction = "incoming",
                    source = appName,
                    timestamp = matchedCall.startTime,
                    displayName = matchedCall.displayName ?: "Unknown"
                )
                unifiedMonitor.onCallEnded(matchedCall.callId, endTime)
            }
        } else {
            // Could be an outgoing call that we already logged; just ignore.
            if (BuildConfig.DEBUG) Log.d(TAG, "ENDED state without matching tracked call, ignoring")
        }
    }

    private fun fallbackTextProcessing(sbn: StatusBarNotification, appName: String) {
        val extras = sbn.notification.extras ?: return
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(android.app.Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(android.app.Notification.EXTRA_BIG_TEXT) ?: ""
        val subText = extras.getString(android.app.Notification.EXTRA_SUB_TEXT) ?: ""
        if (BuildConfig.DEBUG) Log.w(TAG, "Unrecognized call notification from $appName: '$title' / '$text'")
        // Optionally record to CallNotificationDiagnostics
        // CallNotificationDiagnostics.record(context, appName, title, text, bigText, subText)
    }


    // ---- Helpers ----

    private fun extractNumber(extras: Bundle, title: String, text: String): String {
        return infoExtractor.extractPhoneNumberFromExtras(extras, context.contentResolver)
            .takeIf { it.isNotBlank() }
            ?: infoExtractor.extractPhoneNumber(
                title, text,
                extras.getString(android.app.Notification.EXTRA_BIG_TEXT) ?: "",
                extras.getString(android.app.Notification.EXTRA_SUB_TEXT) ?: ""
            )
    }


    /**
     * Trigger the popup overlay for VoIP calls.
     */
    private fun triggerVoipCallerPopup(number: String, displayName: String) {
        if (!callMonitorPrefs.callMonitorEnabled) return   // <-- use injected prefs
        if (displayName.isBlank()) return

        val serviceIntent = Intent(context, OproepDetailService::class.java).apply {
            putExtra(OproepDetailService.EXTRA_CALLER_ID, number)
            putExtra(OproepDetailService.EXTRA_DISPLAY_NAME, displayName)
        }
        context.startForegroundService(serviceIntent)
    }

    // ─── NEW: Reconcile orphaned calls after listener rebind ────────────────

    /**
     * Closes out any active VoIP calls left over from a previous listener session.
     * Delegates to UnifiedCallMonitor to log them as ended now.
     */
    suspend fun reconcileStaleActiveCalls() {
        unifiedMonitor.endActiveVoipCallsFromOtherSources()
    }
}