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
import za.co.jpsoft.winkerkreader.utils.CallerInfoResolver
import za.co.jpsoft.winkerkreader.utils.CallerInfoResult
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.UnifiedCallMonitor
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class WhatsAppNotificationService : NotificationListenerService() {

    // -------------------------------------------------------------------------
    // Instance fields
    // -------------------------------------------------------------------------

    private lateinit var unifiedMonitor: UnifiedCallMonitor
    private lateinit var settingsManager: SettingsManager

    private data class TrackedVoipCall(val callId: String, val startTime: Long)

    private val activeVoipCalls = ConcurrentHashMap<String, TrackedVoipCall>()
    private val loggedUnclassifiedKeys = ConcurrentHashMap.newKeySet<String>()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pruneHandler = Handler(Looper.getMainLooper())
    private val pruneRunnable = object : Runnable {
        override fun run() {
            pruneStaleVoipCalls()
            pruneHandler.postDelayed(this, TimeUnit.MINUTES.toMillis(30))
        }
    }

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
            Log.e(TAG, "Error in onCreate", e)
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
                Log.w(TAG, "Notification listener permission not granted")
                requestPermission()
            }

            reconcileStaleActiveCalls()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onListenerConnected", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (BuildConfig.DEBUG) Log.w(TAG, "onListenerDisconnected — requesting rebind")
        // Actively request a rebind so orphaned calls are reconciled promptly on reconnect.
        requestRebind(ComponentName(applicationContext, WhatsAppNotificationService::class.java))
    }

    override fun onDestroy() {
        isServiceRunning = false
        pruneHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        activeVoipCalls.clear()
        loggedUnclassifiedKeys.clear()

        // Call stopForeground directly — avoids capturing `this` in a postDelayed lambda
        // which would keep the destroyed instance alive and worsen transient framework leaks.
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "stopForeground in onDestroy failed: ${e.message}")
        }

        super.onDestroy()
        if (BuildConfig.DEBUG) Log.d(TAG, "onDestroy")
    }

    // Do NOT override onBind on a NotificationListenerService.
    // The framework depends on the IBinder returned by super.onBind() to manage the
    // NotificationListenerWrapper binding. Removing this override lets the framework
    // handle binding correctly.

    // -------------------------------------------------------------------------
    // Notification callbacks
    // -------------------------------------------------------------------------

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onNotificationPosted — package: ${sbn.packageName}, id: ${sbn.id}")
            }

            pruneStaleVoipCalls()
            if (!settingsManager.voipLogEnabled) return

            val appName = VOIP_PACKAGES[sbn.packageName] ?: return

            if (!looksLikeCallNotification(sbn)) return

            processVoIPNotification(sbn, appName)

        } catch (e: Exception) {
            Log.e(TAG, "Error in onNotificationPosted", e)
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
            val tracked = activeVoipCalls.remove(notificationKey)
            if (tracked != null) {
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "VoIP notification removed, ending call: ${tracked.callId} ($appName)"
                )
                serviceScope.launch {
                    unifiedMonitor.onCallEnded(tracked.callId, System.currentTimeMillis())
                }
            }
        } catch (e: Exception) {
            // DeadObjectException is expected during shutdown — swallow silently.
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onNotificationRemoved — exception during shutdown: ${e.message}")
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
                val reservation = TrackedVoipCall(callId, callStartTime)
                if (activeVoipCalls.putIfAbsent(notificationKey, reservation) != null) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG, "Ignoring repost of already-tracked call: $notificationKey"
                    )
                    return
                }
                serviceScope.launch {
                    handleIncomingOrScreeningCall(
                        notificationKey, callId, appName, extras, callStartTime, reservation
                    )
                }
            }

            CallState.OUTGOING -> {
                val reservation = TrackedVoipCall(callId, callStartTime)
                if (activeVoipCalls.putIfAbsent(notificationKey, reservation) != null) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG, "Ignoring repost of already-tracked outgoing call: $notificationKey"
                    )
                    return
                }
                serviceScope.launch {
                    try {
                        val number = extractPhoneNumberFromExtras(extras)
                        val finalNumber = if (number.isBlank()) {
                            extractPhoneNumber(
                                title, text,
                                extras.getString(Notification.EXTRA_BIG_TEXT) ?: "",
                                extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
                            )
                        } else number

                        val result = if (finalNumber.isNotBlank()) {
                            CallerInfoResolver.resolve(finalNumber, contentResolver)
                        } else {
                            CallerInfoResult.Unknown
                        }

                        val displayName = when (result) {
                            is CallerInfoResult.Member -> result.name
                            is CallerInfoResult.Contact -> result.name
                            CallerInfoResult.Unknown -> null
                        }

                        if (finalNumber.isBlank() && displayName == null) {
                            if (BuildConfig.DEBUG) Log.d(
                                TAG, "Skipping outgoing call: no usable number/name"
                            )
                            activeVoipCalls.remove(notificationKey, reservation)
                            return@launch
                        }

                        unifiedMonitor.onCallDetected(
                            callId = callId,
                            number = if (finalNumber.isNotBlank()) finalNumber
                            else displayName ?: "Unknown",
                            direction = "outgoing",
                            source = appName,
                            timestamp = System.currentTimeMillis(),
                            displayName = displayName ?: "Unknown"
                        )
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e(
                            TAG, "Failed to process outgoing VoIP call", e
                        )
                        activeVoipCalls.remove(notificationKey, reservation)
                    }
                }
            }

            CallState.MISSED -> {
                // Single-arg remove is safe: no reservation was inserted for this transition.
                val tracked = activeVoipCalls.remove(notificationKey)
                if (tracked != null) {
                    serviceScope.launch {
                        unifiedMonitor.onCallMissed(tracked.callId, System.currentTimeMillis())
                    }
                } else {
                    // No active call tracked — log the missed call from scratch.
                    val callIdMissed = "voip_missed_${System.currentTimeMillis()}"
                    serviceScope.launch {
                        val number = extractPhoneNumberFromExtras(extras)
                        val result = if (number.isNotBlank()) {
                            CallerInfoResolver.resolve(number, contentResolver)
                        } else {
                            CallerInfoResult.Unknown
                        }
                        val displayName = when (result) {
                            is CallerInfoResult.Member -> result.name
                            is CallerInfoResult.Contact -> result.name
                            CallerInfoResult.Unknown -> null
                        }
                        unifiedMonitor.onCallDetected(
                            callId = callIdMissed,
                            number = number,
                            direction = "missed",
                            source = appName,
                            timestamp = callStartTime,
                            displayName = displayName ?: "Unknown"
                        )
                        unifiedMonitor.onCallEnded(callIdMissed, System.currentTimeMillis())
                    }
                }
            }

            CallState.ENDED -> {
                val tracked = activeVoipCalls.remove(notificationKey)
                if (tracked != null) {
                    serviceScope.launch {
                        unifiedMonitor.onCallEnded(tracked.callId, System.currentTimeMillis())
                    }
                } else {
                    if (BuildConfig.DEBUG) Log.w(
                        TAG, "Ended call without matching start: $notificationKey"
                    )
                }
            }

            CallState.UNKNOWN -> {
                fallbackTextBasedProcessing(sbn, appName, notificationKey)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Incoming / screening handler (runs on IO dispatcher)
    // -------------------------------------------------------------------------

    private suspend fun handleIncomingOrScreeningCall(
        notificationKey: String,
        callId: String,
        appName: String,
        extras: Bundle,
        callStartTime: Long,
        reservation: TrackedVoipCall
    ) {
        try {
            val number = extractPhoneNumberFromExtras(extras)
            val finalNumber = if (number.isBlank()) {
                extractPhoneNumber(
                    extras.getString(Notification.EXTRA_TITLE) ?: "",
                    extras.getString(Notification.EXTRA_TEXT) ?: "",
                    extras.getString(Notification.EXTRA_BIG_TEXT) ?: "",
                    extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
                )
            } else number

            val result = if (finalNumber.isNotBlank()) {
                CallerInfoResolver.resolve(finalNumber, contentResolver)
            } else {
                CallerInfoResult.Unknown
            }

            val displayName = when (result) {
                is CallerInfoResult.Member -> result.name
                is CallerInfoResult.Contact -> result.name
                CallerInfoResult.Unknown -> null
            }

            if (finalNumber.isBlank() && displayName == null) {
                if (BuildConfig.DEBUG) Log.d(
                    TAG, "Skipping incoming/screening call: no usable number/name"
                )
                activeVoipCalls.remove(notificationKey, reservation)
                return
            }

            val displayNumber = if (finalNumber.isNotBlank()) finalNumber
            else displayName ?: "Unknown"

            unifiedMonitor.onCallDetected(
                callId = callId,
                number = displayNumber,
                direction = "incoming",
                source = appName,
                timestamp = callStartTime,
                displayName = displayName ?: "Unknown"
            )

            // Trigger caller-info popup only for known Members or Contacts.
            if (result is CallerInfoResult.Member || result is CallerInfoResult.Contact) {
                triggerVoipCallerPopup(result, displayNumber)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to process incoming/screening VoIP call", e)
            activeVoipCalls.remove(notificationKey, reservation)
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
                    // These states were already handled by the primary path if detected earlier;
                    // arriving here via UNKNOWN means the structured path couldn't classify them,
                    // so we fall through silently to avoid double-logging.
                    isCallEndedNotification(title, text, bigText, subText) -> { /* silent */
                    }

                    isMissedCall(title, text, bigText, subText) -> { /* silent */
                    }

                    isIncomingCall(title, text, bigText, subText) -> { /* silent */
                    }

                    isPossibleOutgoingCall(title, text, bigText, subText) -> { /* silent */
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

    private fun triggerVoipCallerPopup(result: CallerInfoResult, number: String) {
        try {
            if (!settingsManager.callMonitorEnabled) return

            val displayName = when (result) {
                is CallerInfoResult.Member -> result.name
                is CallerInfoResult.Contact -> result.name
                CallerInfoResult.Unknown -> return
            }
            if (displayName.isBlank()) return

            val serviceIntent = Intent(this, OproepDetailService::class.java)
                .putExtra(OproepDetailService.EXTRA_CALLER_ID, number)
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to start caller popup", e)
        }
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
            Log.e(TAG, "Error creating notification channel", e)
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
            Log.e(TAG, "Error opening notification settings", e)
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .apply {
                        data = android.net.Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Error opening app settings", e2)
            }
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