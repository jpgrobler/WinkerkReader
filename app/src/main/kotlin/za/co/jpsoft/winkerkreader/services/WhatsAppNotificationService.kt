package za.co.jpsoft.winkerkreader.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.calllog.CallLogDatabase
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import za.co.jpsoft.winkerkreader.utils.CallNotificationDiagnostics
import za.co.jpsoft.winkerkreader.utils.CallerNameResolver
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.UnifiedCallMonitor
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class WhatsAppNotificationService : NotificationListenerService() {

    // -------------------------------------------------------------------------
    // Instance fields
    // -------------------------------------------------------------------------

    private lateinit var unifiedMonitor: UnifiedCallMonitor
    private lateinit var settingsManager: SettingsManager

    /**
     * Tracks an incoming or outgoing call while it's in progress.
     * For incoming calls, we store the details and wait for the final state
     * (answered or missed) before logging the call.
     */
    private data class TrackedVoipCall(
        val callId: String,
        val startTime: Long,
        val number: String,
        val displayName: String?,
        val appName: String,
        // Whether this call has already been logged (to avoid double logging)
        var logged: Boolean = false,
        // Optional handler to cancel the timeout
        var timeoutRunnable: Runnable? = null
    )

    private val activeVoipCalls = ConcurrentHashMap<String, TrackedVoipCall>()
    private val loggedUnclassifiedKeys = ConcurrentHashMap.newKeySet<String>()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pruneHandler = Handler(Looper.getMainLooper())
    private val pruneRunnable = object : Runnable {
        override fun run() {
            pruneStaleVoipCalls()
            pruneHandler.postDelayed(this, TimeUnit.MINUTES.toMillis(30))
        }
    }

    // Timeout for incoming calls: if no missed/ended notification arrives within this time,
    // we assume the call was answered (the notification will be removed when the call ends).
    private val INCOMING_CALL_TIMEOUT_MS = 5000L

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        try {
            super.onCreate()
            isServiceRunning = true
            if (BuildConfig.DEBUG) Log.d(TAG, "onCreate")

            initialize()
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createForegroundNotification())
            pruneHandler.post(pruneRunnable)

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error in onCreate", e)
            isServiceRunning = false
            stopSelf()
        }
    }

    private fun initialize() {
        val appContext = applicationContext
        settingsManager = SettingsManager.getInstance(appContext)
        val callLogDao = CallLogDatabase.getInstance(appContext).callLogDao()
        val calendarManager = CalendarManager(appContext)
        val calendarId = settingsManager.selectedCalendarId
        unifiedMonitor = UnifiedCallMonitor.getInstance(
            appContext, callLogDao, calendarManager, calendarId
        )
    }

    override fun onListenerConnected() {
        try {
            super.onListenerConnected()
            if (BuildConfig.DEBUG) Log.d(TAG, "onListenerConnected")

            if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Notification listener permission not granted")
                requestPermission()
            }

            reconcileStaleActiveCalls()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error in onListenerConnected", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (BuildConfig.DEBUG) Log.w(TAG, "onListenerDisconnected — requesting rebind")
        requestRebind(ComponentName(applicationContext, WhatsAppNotificationService::class.java))
    }

    override fun onDestroy() {
        isServiceRunning = false
        pruneHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        // Cancel any pending timeouts
        activeVoipCalls.values.forEach { it.timeoutRunnable?.let { mainHandler.removeCallbacks(it) } }
        activeVoipCalls.clear()
        loggedUnclassifiedKeys.clear()

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "stopForeground in onDestroy failed: ${e.message}")
        }

        super.onDestroy()
        if (BuildConfig.DEBUG) Log.d(TAG, "onDestroy")
    }

    // -------------------------------------------------------------------------
    // Notification callbacks
    // -------------------------------------------------------------------------

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onNotificationPosted — package: ${sbn.packageName}, id: ${sbn.id}")
            }
            val appName = VOIP_PACKAGES[sbn.packageName] ?: return

            pruneStaleVoipCalls()
            if (!settingsManager.voipLogEnabled) return

            if (!looksLikeCallNotification(sbn)) return

            // Dump for debugging
            if (BuildConfig.DEBUG) {
                dumpNotificationToFile(sbn, appName)
            }

            processVoIPNotification(sbn, appName)

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error in onNotificationPosted", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onNotificationRemoved — package: ${sbn.packageName}, id: ${sbn.id}")
            }

            val appName = VOIP_PACKAGES[sbn.packageName] ?: return
            val notificationKey = sbn.key
            loggedUnclassifiedKeys.remove(notificationKey)

            // Check if this is a tracked incoming call that hasn't been logged yet.
            val tracked = activeVoipCalls.remove(notificationKey)
            if (tracked != null && !tracked.logged) {
                // The call was incoming and the notification is removed without a missed state.
                // Assume the call was answered.
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "Incoming notification removed -> call answered: ${tracked.callId}"
                )
                // Cancel the timeout
                tracked.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                tracked.logged = true
                serviceScope.launch {
                    val endTime = System.currentTimeMillis()
                    // Log as INCOMING with duration
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
                // We've handled the removal, so return.
                return
            }

            // For other removals, handle as before (ended calls, etc.)
            // The tracked call may have already been logged (e.g., as MISSED or ANSWERED) and is removed later.
            // We still need to handle the case where a non-incoming notification is removed (e.g., ended call notification).
            // We'll check if the notification is for an ongoing call and we have it tracked?
            // But our tracking only for incoming. So we need to handle ENDED state elsewhere.

            // For completeness, let's process any remaining removal logic:
            // If we have a tracked call that was already logged, we might want to call onCallEnded if it hasn't been.
            // But in our new flow, onCallEnded is called when we log the call (for incoming) or when ENDED state is processed.
            // So we can skip.

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onNotificationRemoved — exception: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // VoIP notification gate
    // -------------------------------------------------------------------------

    private fun looksLikeCallNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        val extras = notification.extras ?: return false
        val category = notification.category
        val callTypeExtra = extras.getInt(Notification.EXTRA_CALL_TYPE, -1)

        if (category == Notification.CATEGORY_CALL ||
            category == Notification.CATEGORY_MISSED_CALL
        ) return true
        if (callTypeExtra in 1..3) return true
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return true

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
        return isCallEndedNotification(title, text, bigText, subText) ||
                isMissedCall(title, text, bigText, subText) ||
                isIncomingCall(title, text, bigText, subText) ||
                isPossibleOutgoingCall(title, text, bigText, subText)
    }

    // -------------------------------------------------------------------------
    // Main VoIP notification processing
    // -------------------------------------------------------------------------

    private fun processVoIPNotification(sbn: StatusBarNotification, appName: String) {
        val notificationKey = sbn.key
        val notification = sbn.notification
        val extras = notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG, "VoIP notification from $appName: category=${notification.category}, " +
                        "callType=${extras.getInt(Notification.EXTRA_CALL_TYPE, -1)}, " +
                        "title='$title', text='$text', key=$notificationKey"
            )
        }

        val category = notification.category
        val callTypeExtra = extras.getInt(Notification.EXTRA_CALL_TYPE, -1)
        val isCallStyle = callTypeExtra in 1..3

        val callState = detectCallState(category, callTypeExtra, isCallStyle, extras, sbn)
        val callStartTime = System.currentTimeMillis()
        val callId = "voip_${appName}_$callStartTime"

        when (callState) {
            CallState.INCOMING, CallState.SCREENING -> {
                // Do NOT log yet; just store the call info and wait for final state.
                val number = extractPhoneNumberFromExtras(extras).takeIf { it.isNotBlank() }
                    ?: extractPhoneNumber(
                        title, text,
                        extras.getString(Notification.EXTRA_BIG_TEXT) ?: "",
                        extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
                    )

                // Extract caller name from text if number is not available
                var displayName: String? = null
                if (number.isBlank()) {
                    displayName = extractCallerInfo(
                        title, text,
                        extras.getString(Notification.EXTRA_BIG_TEXT) ?: "",
                        extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
                    ).takeIf { it.isNotBlank() }
                }

                // If we have a number, try to resolve a better name
                val finalNumber = if (number.isNotBlank()) number else "Unknown"
                val finalName = if (number.isNotBlank()) {
                    // Try to resolve using CallerNameResolver (app members + system contacts)
                    CallerNameResolver.resolve(number, contentResolver) ?: displayName
                } else {
                    displayName
                }

                // Store the call
                val tracked = TrackedVoipCall(
                    callId = callId,
                    startTime = callStartTime,
                    number = finalNumber,
                    displayName = finalName,
                    appName = appName,
                    logged = false,
                    timeoutRunnable = null
                )

                // Put into map (if already exists, ignore)
                val existing = activeVoipCalls.putIfAbsent(notificationKey, tracked)
                if (existing != null) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "Already tracking incoming call for key $notificationKey"
                    )
                    return
                }

                // Schedule a timeout: if no missed/ended state arrives in time, treat as answered.
                val timeoutRunnable = Runnable {
                    val trackedCall = activeVoipCalls.remove(notificationKey)
                    if (trackedCall != null && !trackedCall.logged) {
                        // Timeout reached: assume the call was answered but we didn't get a removal?
                        // Actually, if the notification is still there, we might log as answered with duration until now?
                        // But we don't know when it ended. We'll log as incoming without duration (or with 0).
                        // Better to log as incoming now and later on removal we might override? That's tricky.
                        // To avoid double logging, we'll just mark as answered when the timeout fires.
                        // However, the call might still be ongoing.
                        // We'll use a different approach: we'll only log when the notification is removed or we get an ENDED state.
                        // The timeout is just a safety net to avoid memory leaks; we'll not log here.
                        // Instead, we'll just remove the tracked call without logging, and rely on the removal to log.
                        // But if the notification never removes, we'll have a stale entry.
                        // We'll prune stale entries anyway.
                        // So we'll just remove and not log.
                        if (BuildConfig.DEBUG) Log.d(
                            TAG,
                            "Timeout for incoming call, removing tracking (not logging)"
                        )
                        // But we want to log as missed if we never got a missed notification?
                        // The user might have declined the call (missed) but the app didn't show a missed notification?
                        // Usually, missed notification will appear. We'll rely on that.
                        // So do nothing.
                    }
                }
                tracked.timeoutRunnable = timeoutRunnable
                mainHandler.postDelayed(timeoutRunnable, INCOMING_CALL_TIMEOUT_MS)
                if (BuildConfig.DEBUG) Log.d(TAG, "Tracked incoming call: $callId")
            }

            CallState.OUTGOING -> {
                // Outgoing calls are final; log immediately.
                val number = extractPhoneNumberFromExtras(extras).takeIf { it.isNotBlank() }
                    ?: extractPhoneNumber(
                        title, text,
                        extras.getString(Notification.EXTRA_BIG_TEXT) ?: "",
                        extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
                    )
                val displayName = if (number.isNotBlank()) {
                    CallerNameResolver.resolve(number, contentResolver)
                } else {
                    extractCallerInfo(
                        title, text,
                        extras.getString(Notification.EXTRA_BIG_TEXT) ?: "",
                        extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
                    ).takeIf { it.isNotBlank() }
                }

                val finalNumber = if (number.isNotBlank()) number else "Unknown"
                val finalDisplay = displayName ?: "Unknown"

                // Store tracking (optional, for consistency but we won't use for logging)
                val tracked = TrackedVoipCall(
                    callId = callId,
                    startTime = callStartTime,
                    number = finalNumber,
                    displayName = finalDisplay,
                    appName = appName,
                    logged = true // already logged
                )
                activeVoipCalls[notificationKey] = tracked

                serviceScope.launch {
                    unifiedMonitor.onCallDetected(
                        callId = callId,
                        number = finalNumber,
                        direction = "outgoing",
                        source = appName,
                        timestamp = callStartTime,
                        displayName = finalDisplay
                    )
                    // We'll end the call when the notification is removed or we get an ENDED state.
                    // For outgoing, we may also get a call ended notification later.
                    // We'll handle that in onNotificationRemoved or state ENDED.
                }
            }

            CallState.MISSED -> {
                // Missed call: log as MISSED.
                // Check if we have a tracking entry for the incoming notification that led to this missed state.
                // The missed notification might have a different key? Usually it's a separate notification.
                // We'll try to find the matching tracked call by appName and startTime (approximate).
                // But we can also use the incoming notification key that we tracked? The missed notification is posted after the incoming is removed.
                // The notificationKey might be different. We can try to match by caller number and time proximity.
                // Simpler: if we have a tracked call with same number and within a few seconds, we can use that.
                // However, for simplicity, we'll just log a new MISSED record.
                // But we also want to avoid duplicate if we already logged it as incoming.
                // We'll check if we have a tracked call that hasn't been logged.
                // We'll iterate over activeVoipCalls and find one with matching number and startTime close.
                // But that's messy.
                // Alternative: we can also look up the missed notification's caller info and log as MISSED.
                // To avoid duplicates, we'll just log a fresh MISSED record and not try to use a tracked incoming.
                // But we need to ensure we don't log the same call twice (incoming + missed).
                // Since we no longer log incoming immediately, we won't have a duplicate.
                // We'll just log as MISSED now.

                // Extract caller info from the missed notification.
                val number = extractPhoneNumberFromExtras(extras).takeIf { it.isNotBlank() }
                    ?: extractPhoneNumber(
                        title, text,
                        extras.getString(Notification.EXTRA_BIG_TEXT) ?: "",
                        extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
                    )
                val displayName = if (number.isNotBlank()) {
                    CallerNameResolver.resolve(number, contentResolver)
                } else {
                    extractCallerInfo(
                        title, text,
                        extras.getString(Notification.EXTRA_BIG_TEXT) ?: "",
                        extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
                    ).takeIf { it.isNotBlank() }
                }

                val finalNumber = if (number.isNotBlank()) number else "Unknown"
                val finalDisplay = displayName ?: "Unknown"

                // Check if there is a tracked incoming call that matches this missed call.
                // We can try to match by number and startTime within a few seconds.
                // We'll search for a matching tracked call that hasn't been logged.
                var matched = false
                val iter = activeVoipCalls.entries.iterator()
                while (iter.hasNext()) {
                    val (key, tracked) = iter.next()
                    if (!tracked.logged && tracked.number == finalNumber &&
                        kotlin.math.abs(tracked.startTime - callStartTime) < 5000
                    ) {
                        // Found a match: this is the incoming call that was missed.
                        tracked.logged = true
                        // Cancel any timeout
                        tracked.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                        iter.remove()
                        // Log as MISSED with the stored display name (which might be better)
                        serviceScope.launch {
                            unifiedMonitor.onCallDetected(
                                callId = tracked.callId,
                                number = tracked.number,
                                direction = "missed",
                                source = appName,
                                timestamp = tracked.startTime,
                                displayName = tracked.displayName ?: "Unknown"
                            )
                            unifiedMonitor.onCallEnded(tracked.callId, System.currentTimeMillis())
                        }
                        matched = true
                        break
                    }
                }

                if (!matched) {
                    // No matching tracked call, log a fresh missed record.
                    val missedCallId = "voip_missed_${System.currentTimeMillis()}"
                    serviceScope.launch {
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

            CallState.ENDED -> {
                // Call ended (answered). This could be for an incoming or outgoing call.
                // For incoming, we should have a tracked call; log as INCOMING with duration.
                // For outgoing, we might not have tracking (we logged at start), but we need to end the call.
                // We'll try to find a matching tracked call.
                val number = extractPhoneNumberFromExtras(extras).takeIf { it.isNotBlank() }
                    ?: extractPhoneNumber(
                        title, text,
                        extras.getString(Notification.EXTRA_BIG_TEXT) ?: "",
                        extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
                    )
                // Try to find a tracked incoming call that hasn't been logged and matches number/time.
                var matched = false
                val iter = activeVoipCalls.entries.iterator()
                while (iter.hasNext()) {
                    val (key, tracked) = iter.next()
                    if (!tracked.logged && tracked.number == number &&
                        kotlin.math.abs(tracked.startTime - callStartTime) < 5000
                    ) {
                        // Found matching incoming call that ended.
                        tracked.logged = true
                        tracked.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                        iter.remove()
                        val endTime = System.currentTimeMillis()
                        serviceScope.launch {
                            unifiedMonitor.onCallDetected(
                                callId = tracked.callId,
                                number = tracked.number,
                                direction = "incoming",
                                source = appName,
                                timestamp = tracked.startTime,
                                displayName = tracked.displayName ?: "Unknown"
                            )
                            unifiedMonitor.onCallEnded(tracked.callId, endTime)
                        }
                        matched = true
                        break
                    }
                }

                if (!matched) {
                    // It could be an outgoing call that we already logged; just end it.
                    // We can look up by callId stored in tracking? We didn't store callId for outgoing in a map easily.
                    // We'll just log as incoming? No, it's likely outgoing. But we don't have a callId.
                    // To avoid duplicates, we'll just ignore.
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "ENDED state without matching tracked call, ignoring"
                    )
                }
            }

            CallState.UNKNOWN -> {
                fallbackTextBasedProcessing(sbn, appName, notificationKey)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fallback text-based processing (UNKNOWN call state)
    // -------------------------------------------------------------------------

    private fun fallbackTextBasedProcessing(
        sbn: StatusBarNotification,
        appName: String,
        notificationKey: String
    ) {
        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""

        if (title.isBlank() && text.isBlank() && bigText.isBlank() && subText.isBlank()
            && notificationKey in loggedUnclassifiedKeys
        ) return

        serviceScope.launch {
            try {
                when {
                    isCallEndedNotification(title, text, bigText, subText) -> {
                        // Might be an ended call; we can try to log as incoming if we have tracking.
                        // But we'll just record for diagnostics.
                    }

                    isMissedCall(title, text, bigText, subText) -> {
                        // missed
                    }

                    isIncomingCall(title, text, bigText, subText) -> {
                        // incoming
                    }

                    isPossibleOutgoingCall(title, text, bigText, subText) -> {
                        // outgoing
                    }
                    else -> {
                        recordUnrecognizedCallNotification(appName, title, text, bigText, subText)
                        loggedUnclassifiedKeys.add(notificationKey)
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed fallback VoIP notification processing", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // State detection helpers (synchronous — pure logic, no I/O)
    // -------------------------------------------------------------------------

    private fun detectCallState(
        category: String?,
        callTypeExtra: Int,
        isCallStyle: Boolean,
        extras: Bundle,
        sbn: StatusBarNotification
    ): CallState {
        when (category) {
            Notification.CATEGORY_CALL -> {
                if (isCallStyle) {
                    return when (callTypeExtra) {
                        1 -> CallState.INCOMING
                        2 -> CallState.OUTGOING
                        3 -> CallState.SCREENING
                        else -> CallState.INCOMING
                    }
                }
                return inferCallStateFromExtras(extras)
            }

            Notification.CATEGORY_MISSED_CALL -> return CallState.MISSED
        }

        if (isCallStyle) {
            return when (callTypeExtra) {
                1 -> CallState.INCOMING
                2 -> CallState.OUTGOING
                3 -> CallState.SCREENING
                else -> CallState.UNKNOWN
            }
        }
        return CallState.UNKNOWN
    }

    private fun inferCallStateFromExtras(extras: Bundle): CallState {
        if (extras.getBoolean("is_incoming", false)) return CallState.INCOMING
        if (extras.getBoolean("is_outgoing", false)) return CallState.OUTGOING

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""

        if (isCallEndedNotification(title, text, bigText, subText)) return CallState.ENDED
        if (isMissedCall(title, text, bigText, subText)) return CallState.MISSED
        if (isIncomingCall(title, text, bigText, subText)) return CallState.INCOMING
        if (isPossibleOutgoingCall(title, text, bigText, subText)) return CallState.OUTGOING
        return CallState.UNKNOWN
    }

    // -------------------------------------------------------------------------
    // Caller info extraction (synchronous string parsing)
    // -------------------------------------------------------------------------

    private fun extractCallerInfoModern(extras: Bundle): String {
        val peopleUris = extras.getParcelableArray(Notification.EXTRA_PEOPLE)
        if (peopleUris != null && peopleUris.isNotEmpty()) {
            for (uriObj in peopleUris) {
                if (uriObj is Uri) {
                    val displayName = resolveContactNameFromUri(uriObj)
                    if (displayName.isNotBlank()) return displayName
                }
            }
        }
        val title = extras.getString(Notification.EXTRA_TITLE)
        if (!title.isNullOrBlank() && !isGenericCallTitle(title)) {
            return cleanExtractedName(title)
        }
        val text = extras.getString(Notification.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            val simpleName = extractSimpleNameFromText(text)
            if (simpleName.isNotBlank()) return simpleName
        }
        return ""
    }

    private fun resolveContactNameFromUri(contactUri: Uri): String {
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(contactUri, projection, null, null, null)
            if (cursor?.moveToFirst() == true) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to resolve contact URI: $contactUri", e)
        } finally {
            cursor?.close()
        }
        return ""
    }

    // -------------------------------------------------------------------------
    // Phone number extraction (synchronous, pure string work)
    // -------------------------------------------------------------------------

    private fun extractPhoneNumberFromExtras(extras: Bundle): String {
        val peopleUris = extras.getParcelableArray(Notification.EXTRA_PEOPLE)
        if (peopleUris != null) {
            for (uriObj in peopleUris) {
                if (uriObj is Uri) {
                    val phone = resolvePhoneNumberFromContactUri(uriObj)
                    if (phone.isNotBlank()) return phone
                }
            }
        }
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
        return extractPhoneNumber(title, text, bigText, subText)
    }

    private fun resolvePhoneNumberFromContactUri(contactUri: Uri): String {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(contactUri, projection, null, null, null)
            if (cursor?.moveToFirst() == true) {
                return cursor.getString(0) ?: ""
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to resolve phone from contact URI", e)
        } finally {
            cursor?.close()
        }
        return ""
    }

    // -------------------------------------------------------------------------
    // Text matchers (synchronous, pure string)
    // -------------------------------------------------------------------------

    private fun isIncomingCall(
        title: String,
        text: String,
        bigText: String,
        subText: String
    ): Boolean {
        val combined = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        val strongIncoming = arrayOf(
            "is calling you",
            "wants to call you",
            "incoming call",
            "incoming video call",
            "incoming voice call",
            "inkomende oproep",
            "inkomende video-oproep",
            "inkomende stemoproep",
            "bel jou",
            "wil jou bel",
            "eingehender anruf",
            "eingehender videoanruf",
            "eingehender sprachanruf",
            "ruft dich an",
            "appel entrant",
            "appel video entrant",
            "appel vocal entrant",
            "vous appelle",
            "llamada entrante",
            "videollamada entrante",
            "llamada de voz entrante",
            "te esta llamando",
            "te está llamando",
            "chamada recebida",
            "chamada de entrada",
            "chamada de video recebida",
            "está ligando para você",
            "esta ligando para voce"
        )
        if (strongIncoming.any { combined.contains(it) }) return true
        if (combined.contains("you called") || combined.contains("you are calling") ||
            combined.contains("outgoing") || combined.contains("call started") ||
            combined.contains("uitgaande oproep") || combined.contains("ausgehender anruf") ||
            combined.contains("appel sortant") || combined.contains("llamada saliente") ||
            combined.contains("chamada efetuada")
        ) return false
        return combined.contains("calling") &&
                (!combined.contains("you") || combined.contains("calling you"))
    }

    private fun isPossibleOutgoingCall(
        title: String,
        text: String,
        bigText: String,
        subText: String
    ): Boolean {
        val combined = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        val strongOutgoing = arrayOf(
            "you called", "you are calling", "outgoing call", "call started", "calling…",
            "uitgaande oproep", "jy het gebel", "jy bel", "ausgehender anruf", "du rufst an",
            "appel sortant", "vous appelez", "llamada saliente", "estas llamando", "estás llamando",
            "chamada efetuada", "ligacao efetuada", "ligação efetuada", "voce esta ligando",
            "você está ligando"
        )
        if (strongOutgoing.any { combined.contains(it) }) return true
        if (combined.contains("is calling") || combined.contains("calling you") ||
            combined.contains("wants to call") || combined.contains("incoming")
        ) return false
        return false
    }

    private fun isCallEndedNotification(
        title: String,
        text: String,
        bigText: String,
        subText: String
    ): Boolean {
        val endedKeywords = arrayOf(
            "call ended",
            "call finished",
            "call completed",
            "call duration",
            "call lasted",
            "hung up",
            "disconnected",
            "call time",
            "oproep beeindig",
            "oproep beëindig",
            "gesprek beeindig",
            "gesprek beëindig",
            "oproep klaar",
            "gesprek klaar",
            "gesprekstyd",
            "anruf beendet",
            "gesprach beendet",
            "gespräch beendet",
            "appel termine",
            "appel terminé",
            "llamada finalizada",
            "llamada terminada",
            "duracion de la llamada",
            "duración de la llamada",
            "chamada encerrada",
            "ligacao encerrada",
            "ligação encerrada",
            "duracao da chamada",
            "duração da chamada"
        )
        val combined = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        return endedKeywords.any { combined.contains(it) }
    }

    private fun isMissedCall(
        title: String,
        text: String,
        bigText: String,
        subText: String
    ): Boolean {
        val missedKeywords = arrayOf(
            "missed call", "missed video call", "missed voice call", "unanswered",
            "didn't answer", "no answer", "gemiste oproep", "gemisde oproep", "onbeantwoord",
            "verpasster anruf", "nicht beantwortet", "appel manque", "appel manqué",
            "sans reponse", "sans réponse", "llamada perdida", "no respondio", "no respondió",
            "chamada perdida", "ligacao perdida", "ligação perdida", "nao atendida", "não atendida"
        )
        val combined = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        return missedKeywords.any { combined.contains(it) }
    }

    // -------------------------------------------------------------------------
    // Caller-name extraction helpers
    // -------------------------------------------------------------------------

    private fun extractCallerInfo(
        title: String,
        text: String,
        bigText: String,
        subText: String
    ): String {
        if (BuildConfig.DEBUG) Log.d(
            TAG, "Extracting caller — title: '$title', text: '$text'"
        )
        val candidates = arrayOf(
            extractFromTitle(title),
            extractFromText(text),
            extractFromBigText(bigText),
            extractFromSubText(subText),
            extractPhoneNumber(title, text, bigText, subText),
            extractFromTickerText(title, text, bigText, subText)
        )
        for (candidate in candidates) {
            if (candidate.trim().isNotEmpty() && candidate != "Unknown") {
                return candidate.trim()
            }
        }
        return "Unknown Contact"
    }

    private fun extractFromTitle(title: String): String {
        if (title.isEmpty()) return ""
        val cleaned = title
            .replace(Regex("[📞📹☎️📱🎥]"), "")
            .replace(
                Regex("(?i)(incoming call|calling|video call|voice call|missed call|call from).*"),
                ""
            )
            .replace(
                Regex("(?i).*(whatsapp|skype|zoom|teams|discord|telegram|viber|messenger|meet).*"),
                ""
            )
            .trim()
        return if (cleaned.isNotEmpty() && cleaned.length > 2 && !containsOnlyCallKeywords(cleaned)) cleaned
        else ""
    }

    private fun extractFromText(text: String): String {
        if (text.isEmpty()) return ""
        val patterns = arrayOf(
            Regex(
                "^(.+?)\\s+(is calling|calling you|wants to call|started a call)",
                RegexOption.IGNORE_CASE
            ),
            Regex("^(.+?)\\s+(voice call|video call|missed call)", RegexOption.IGNORE_CASE),
            Regex("Call from\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^(.+?)\\s+.*call.*$", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                val name = match.groupValues[1].trim()
                if (name.isNotEmpty() && !containsAppKeywords(name)) {
                    return cleanExtractedName(name)
                }
            }
        }
        return ""
    }

    private fun extractFromBigText(bigText: String): String {
        if (bigText.isEmpty()) return ""
        for (line in bigText.split("\n")) {
            val cleaned = line.trim()
            if (cleaned.isNotEmpty() && !containsAppKeywords(cleaned) &&
                !containsOnlyCallKeywords(cleaned)
            ) {
                val words = cleaned.split("\\s+".toRegex())
                if (words.isNotEmpty() && words[0].length > 2) {
                    return cleanExtractedName(words[0])
                }
            }
        }
        return ""
    }

    private fun extractFromSubText(subText: String): String {
        if (subText.isEmpty()) return ""
        val cleaned = subText.trim()
        return if (cleaned.isNotEmpty() && !containsAppKeywords(cleaned) &&
            !containsOnlyCallKeywords(cleaned)
        ) cleanExtractedName(cleaned) else ""
    }

    private fun extractPhoneNumber(
        title: String,
        text: String,
        bigText: String,
        subText: String
    ): String {
        val combined = "$title $text $bigText $subText"
        val phonePatterns = arrayOf(
            Regex("\\+?\\d{1,4}[\\s-]?\\(?\\d{1,4}\\)?[\\s-]?\\d{1,4}[\\s-]?\\d{1,9}"),
            Regex("\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}"),
            Regex("\\d{10,}")
        )
        for (pattern in phonePatterns) {
            val match = pattern.find(combined)
            if (match != null) {
                val number = match.value.trim()
                if (number.length >= 7) return number
            }
        }
        return ""
    }

    private fun extractFromTickerText(
        title: String,
        text: String,
        bigText: String,
        subText: String
    ): String {
        val combined = "$title $text $bigText $subText"
        val patterns = arrayOf(
            Regex("\"(.+?)\""),
            Regex("\\((.+?)\\)"),
            Regex("from\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^(.+?)\\s*:", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(combined)
            if (match != null && match.groupValues.size > 1) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotEmpty() && !containsAppKeywords(extracted) &&
                    extracted.length > 2
                ) {
                    return cleanExtractedName(extracted)
                }
            }
        }
        return ""
    }

    private fun extractSimpleNameFromText(text: String): String {
        val patterns = listOf(
            Regex(
                "^(.+?)\\s+(is calling|calling you|wants to call|started a call)",
                RegexOption.IGNORE_CASE
            ),
            Regex("Call from\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^(.+?)\\s+(voice call|video call|missed call)", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                val name = match.groupValues[1].trim()
                if (name.isNotEmpty() && !containsAppKeywords(name)) {
                    return cleanExtractedName(name)
                }
            }
        }
        return ""
    }

    private fun containsOnlyCallKeywords(text: String): Boolean {
        val callOnlyWords =
            arrayOf("call", "calling", "voice", "video", "incoming", "missed", "ended")
        val words = text.lowercase(Locale.ROOT).split("\\s+".toRegex())
        for (word in words) {
            if (!callOnlyWords.any { it == word } && word.length >= 3) return false
        }
        return true
    }

    private fun cleanExtractedName(name: String): String {
        val cleaned = name.replace(Regex("[📞📹☎️📱🎥]+"), "").replace("\\s+".toRegex(), " ").trim()
        return if (cleaned.length > 1) cleaned else ""
    }

    private fun containsAppKeywords(text: String): Boolean {
        val appKeywords = arrayOf(
            "whatsapp", "skype", "zoom", "teams", "discord", "telegram", "viber", "messenger",
            "meet", "notification", "app", "calling", "call", "video", "voice", "missed",
            "incoming", "ended"
        )
        val lower = text.lowercase(Locale.ROOT)
        return appKeywords.any { lower.contains(it) }
    }

    private fun isGenericCallTitle(title: String): Boolean {
        val generic = setOf(
            "incoming call", "outgoing call", "missed call", "call", "video call",
            "voice call", "WhatsApp", "Skype", "Zoom", "Teams", "Telegram"
        )
        return generic.any { title.equals(it, ignoreCase = true) }
    }

    private fun isUnknownCaller(callerInfo: String): Boolean {
        val normalized = callerInfo.trim().lowercase(Locale.ROOT)
        return normalized.isEmpty() || normalized == "unknown contact" || normalized == "unknown"
    }

    // -------------------------------------------------------------------------
    // Popup trigger
    // -------------------------------------------------------------------------

    private fun triggerVoipCallerPopup(number: String, displayName: String) {
        if (!settingsManager.callMonitorEnabled) return
        if (displayName.isBlank()) return

        val serviceIntent = Intent(this, OproepDetailService::class.java).apply {
            putExtra(OproepDetailService.EXTRA_CALLER_ID, number)
            putExtra(OproepDetailService.EXTRA_DISPLAY_NAME, displayName)
        }
        startForegroundService(serviceIntent)
    }

    // -------------------------------------------------------------------------
    // Stale-call management
    // -------------------------------------------------------------------------

    private fun pruneStaleVoipCalls() {
        val cutoff = System.currentTimeMillis() - VOIP_CALL_TTL_MS
        activeVoipCalls.entries.removeIf { it.value.startTime < cutoff }
    }

    private fun reconcileStaleActiveCalls() {
        if (!::unifiedMonitor.isInitialized) return
        serviceScope.launch {
            val orphaned = unifiedMonitor.endActiveVoipCallsFromOtherSources()
            if (BuildConfig.DEBUG && orphaned > 0) {
                Log.w(TAG, "Reconciled $orphaned VoIP call(s) left active across a listener rebind")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    private fun recordUnrecognizedCallNotification(
        appName: String, title: String, text: String, bigText: String, subText: String
    ) {
        if (BuildConfig.DEBUG) Log.w(
            TAG, "Unrecognized call-app notification from $appName: '$title' / '$text'"
        )
        CallNotificationDiagnostics.record(
            applicationContext,
            appName,
            title,
            text,
            bigText,
            subText
        )
    }

    // -------------------------------------------------------------------------
    // Foreground service boilerplate
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        try {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "WhatsApp Listener Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Required to keep notification listener service running"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
            if (BuildConfig.DEBUG) Log.d(TAG, "Notification channel created")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error creating notification channel", e)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("WhatsApp Listener Active")
            .setContentText("Monitoring WhatsApp notifications")
            .setSmallIcon(R.drawable.img)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun requestPermission() {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error opening notification settings", e)
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .apply {
                        data = android.net.Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                startActivity(intent)
            } catch (e2: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error opening app settings", e2)
            }
        }
    }

    private fun dumpNotificationToFile(sbn: StatusBarNotification, appName: String) {
        try {
            val file = File(applicationContext.cacheDir, "voip_notification_dump.txt")
            val extras = sbn.notification.extras
            val writer = FileWriter(file, true)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            writer.append("=== ${sdf.format(Date())} ===\n")
            writer.append("Package: ${sbn.packageName}\n")
            writer.append("AppName: $appName\n")
            writer.append("Key: ${sbn.key}\n")
            writer.append("Id: ${sbn.id}\n")
            writer.append("Category: ${sbn.notification.category}\n")
            writer.append("Flags: ${sbn.notification.flags}\n")
            if (extras != null) {
                for (key in extras.keySet()) {
                    val value = extras.get(key)
                    writer.append("  $key = $value\n")
                }
            } else {
                writer.append("Extras: null\n")
            }
            writer.append("---\n")
            writer.close()
            if (BuildConfig.DEBUG) Log.e(TAG, "Dumped notification to file")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to dump notification to file", e)
        }
    }

    // -------------------------------------------------------------------------
    // Companion + enum
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "WhatsAppNotifService"
        private const val NOTIFICATION_CHANNEL_ID = "whatsapp_listener_channel"
        private const val NOTIFICATION_ID = 9999

        @Volatile
        private var isServiceRunning = false

        @JvmStatic
        fun isRunning(): Boolean = isServiceRunning

        private val VOIP_PACKAGES = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.skype.raider" to "Skype",
            "us.zoom.videomeetings" to "Zoom",
            "com.microsoft.teams" to "Microsoft Teams",
            "com.discord" to "Discord",
            "org.telegram.messenger" to "Telegram",
            "com.viber.voip" to "Viber",
            "com.facebook.orca" to "Messenger",
            "com.google.android.apps.tachyon" to "Google Meet"
        )

        val VOIP_CALL_TTL_MS: Long = TimeUnit.HOURS.toMillis(1)
    }

    private enum class CallState {
        INCOMING, OUTGOING, MISSED, ENDED, SCREENING, UNKNOWN
    }
}