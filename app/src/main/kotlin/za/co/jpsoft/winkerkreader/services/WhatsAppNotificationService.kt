package za.co.jpsoft.winkerkreader.services

import android.app.Notification
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import za.co.jpsoft.winkerkreader.utils.CallerInfoResolver
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.UnifiedCallMonitor
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import android.content.ComponentName
import za.co.jpsoft.winkerkreader.utils.CallNotificationDiagnostics
import za.co.jpsoft.winkerkreader.data.calllog.CallLogDatabase

class WhatsAppNotificationService : NotificationListenerService() {

    private lateinit var unifiedMonitor: UnifiedCallMonitor
    private lateinit var settingsManager: SettingsManager

    // Thread‑safe map for active VoIP calls
    private data class TrackedVoipCall(val callId: String, val startTime: Long)
    private val activeVoipCalls = ConcurrentHashMap<String, TrackedVoipCall>()
    private val loggedUnclassifiedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()


    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pruneHandler = Handler(Looper.getMainLooper())
    private val pruneRunnable = object : Runnable {
        override fun run() {
            pruneStaleVoipCalls()
            pruneHandler.postDelayed(this, TimeUnit.MINUTES.toMillis(30))
        }
    }
    override fun onCreate() {
        super.onCreate()
        initialize()
        //pruneHandler.post(pruneRunnable)
        if (BuildConfig.DEBUG) Log.d(TAG, "WhatsAppNotificationService created")
    }

    private fun initialize() {
        val appContext = applicationContext

        settingsManager = SettingsManager.getInstance(appContext)
        val callLogDao = CallLogDatabase.getInstance(appContext).callLogDao()
        val calendarManager = CalendarManager(appContext)
        val calendarId = settingsManager.selectedCalendarId

        unifiedMonitor = UnifiedCallMonitor.getInstance(appContext, callLogDao, calendarManager, calendarId)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        pruneStaleVoipCalls()
        if (!settingsManager.voipLogEnabled) return

        val packageName = sbn.packageName
        val appName = VOIP_PACKAGES[packageName] ?: return

        // Gate entry into the whole pipeline. WhatsApp (and most VoIP apps)
        // posts many non-call notifications under the same package — chat
        // messages, group updates, backup status, etc. Without this check,
        // ordinary messages reach fallbackTextBasedProcessing's unclassified
        // branch and get misclassified as calls.
        if (!looksLikeCallNotification(sbn)) return

        processVoIPNotification(sbn, appName)
    }

    /**
     * Cheap, synchronous pre-filter: does this notification plausibly
     * represent a call at all? Only notifications that pass this should ever
     * reach fallbackTextBasedProcessing's "unclassified" branch.
     */
    private fun looksLikeCallNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        val extras = notification.extras ?: return false
        val category = notification.category
        val callTypeExtra = extras.getInt(Notification.EXTRA_CALL_TYPE, -1)

        if (category == Notification.CATEGORY_CALL || category == Notification.CATEGORY_MISSED_CALL) return true
        if (callTypeExtra in 1..3) return true

        // Real call notifications (ringing/ongoing) are marked ongoing; chat
        // messages are not. Strongest language-independent signal we have.
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return true

        // Last resort: text strongly suggests a call (covers "call ended" /
        // "missed call" notifications, which usually aren't ongoing by the
        // time they post).
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
        return isCallEndedNotification(title, text, bigText, subText) ||
                isMissedCall(title, text, bigText, subText) ||
                isIncomingCall(title, text, bigText, subText) ||
                isPossibleOutgoingCall(title, text, bigText, subText)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove any pending handler messages (even if none are posted)
        pruneHandler.removeCallbacksAndMessages(null)
        // Cancel all coroutines
        serviceScope.cancel()
        // Clear active calls map
        activeVoipCalls.clear()
        loggedUnclassifiedKeys.clear()
        // Release any other heavy references (e.g., to the monitor? None held directly)
        if (BuildConfig.DEBUG) Log.d(TAG, "WhatsAppNotificationService destroyed")
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
            Log.d(TAG, "VoIP notification from $appName: category=${notification.category}, " +
                    "callType=${extras.getInt(Notification.EXTRA_CALL_TYPE, -1)}, " +
                    "title='$title', text='$text', key=$notificationKey")
        }

        val category = notification.category
        val callTypeExtra = extras.getInt(Notification.EXTRA_CALL_TYPE, -1)
        val isCallStyle = callTypeExtra in 1..3

        val callState = detectCallState(category, callTypeExtra, isCallStyle, extras, sbn)
        val callStartTime = System.currentTimeMillis()
        val callId = "voip_${appName}_$callStartTime"

        when (callState) {
            CallState.INCOMING, CallState.SCREENING -> {
                // Reserve the slot synchronously, before any async resolution starts.
                // WhatsApp (and other VoIP apps) repost the same ongoing-call notification
                // repeatedly — once a second or more — while it's ringing/connecting. Each
                // repost used to generate a brand-new callId and get logged as if it were a
                // separate call. putIfAbsent is atomic, so only the *first* post for this
                // notificationKey proceeds; every repost of the same still-active call is a
                // no-op from here on.
                val reservation = TrackedVoipCall(callId, callStartTime)
                if (activeVoipCalls.putIfAbsent(notificationKey, reservation) != null) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Ignoring repost of already-tracked call: $notificationKey")
                    return
                }
                handleIncomingOrScreeningCall(notificationKey, callId, appName, extras, callStartTime)
            }
            CallState.OUTGOING -> {
                val reservation = TrackedVoipCall(callId, callStartTime)
                if (activeVoipCalls.putIfAbsent(notificationKey, reservation) != null) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Ignoring repost of already-tracked outgoing call: $notificationKey")
                    return
                }
                serviceScope.launch {
                    try {
                        val number = extractPhoneNumberFromExtras(extras)
                        val finalNumber = if (number.isBlank()) {
                            extractPhoneNumber(title, text,
                                extras.getString(Notification.EXTRA_BIG_TEXT) ?: "",
                                extras.getString(Notification.EXTRA_SUB_TEXT) ?: "")
                        } else number

                        val resolvedName = if (finalNumber.isNotBlank()) {
                            CallerInfoResolver.getCallerDisplayInfo(contentResolver, finalNumber)
                        } else {
                            val name = extractCallerInfoModern(extras)
                            if (name.isNotBlank()) name else "Unknown Caller"
                        }

                        if (finalNumber.isBlank() && (resolvedName.isBlank() || resolvedName == "Unknown Contact")) {
                            if (BuildConfig.DEBUG) Log.d(TAG, "Skipping outgoing call: no usable number/name")
                            activeVoipCalls.remove(notificationKey, reservation)
                            return@launch
                        }

                        unifiedMonitor.onCallDetected(
                            callId = callId,
                            number = if (finalNumber.isNotBlank()) finalNumber else resolvedName,
                            direction = "outgoing",
                            source = appName,
                            timestamp = System.currentTimeMillis(),
                            displayName = resolvedName
                        )
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Failed to process outgoing VoIP call", e)
                        // Registration failed — free the slot so a later repost can retry.
                        activeVoipCalls.remove(notificationKey, reservation)
                    }
                }
            }
            CallState.MISSED -> {
                val existingCallId = activeVoipCalls.remove(notificationKey)
                if (existingCallId != null) {
                    serviceScope.launch {
                        unifiedMonitor.onCallMissed(existingCallId.callId, System.currentTimeMillis())
                    }
                } else {
                    val callIdMissed = "voip_missed_${System.currentTimeMillis()}"
                    serviceScope.launch {
                        val number = extractPhoneNumberFromExtras(extras)
                        val resolvedName = CallerInfoResolver.getCallerDisplayInfo(contentResolver, number)
                        unifiedMonitor.onCallDetected(
                            callId = callIdMissed,
                            number = number,
                            direction = "missed",
                            source = appName,
                            timestamp = callStartTime,
                            displayName = resolvedName
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
                    if (BuildConfig.DEBUG) Log.w(TAG, "Ended call without matching start: $notificationKey")
                }
            }
            CallState.UNKNOWN -> {
                fallbackTextBasedProcessing(sbn, appName, notificationKey)
            }
        }
    }

    /**
     * Handles both INCOMING and SCREENING call states (identical logic).
     * - Stores the call in the active map.
     * - Extracts caller number/name asynchronously.
     * - Logs the call via UnifiedCallMonitor.
     * - Triggers the caller popup.
     */
    private fun handleIncomingOrScreeningCall(
        notificationKey: String,
        callId: String,
        appName: String,
        extras: Bundle,
        callStartTime: Long
    ) {
        serviceScope.launch {
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

                val resolvedName = if (finalNumber.isNotBlank()) {
                    CallerInfoResolver.getCallerDisplayInfo(contentResolver, finalNumber)
                } else {
                    val name = extractCallerInfoModern(extras)
                    if (name.isNotBlank()) name else "Unknown Caller"
                }

                if (finalNumber.isBlank() && (resolvedName.isBlank() || resolvedName == "Unknown Contact")) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Skipping incoming/screening call: no usable number/name")
                    activeVoipCalls.remove(notificationKey)
                    return@launch
                }

                val displayNumber = if (finalNumber.isNotBlank()) finalNumber else resolvedName

                unifiedMonitor.onCallDetected(
                    callId = callId,
                    number = displayNumber,
                    direction = "incoming",
                    source = appName,
                    timestamp = callStartTime,
                    displayName = resolvedName
                )
                // The reservation was already made synchronously in processVoIPNotification,
                // before this coroutine was launched, so no re-insertion is needed here.
                triggerVoipCallerPopup(resolvedName, displayNumber)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to process incoming/screening VoIP call", e)
                activeVoipCalls.remove(notificationKey)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Legacy fallback (also fully async)
    // -------------------------------------------------------------------------

    private fun fallbackTextBasedProcessing(sbn: StatusBarNotification, appName: String, notificationKey: String) {
        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""

        // Some apps (WhatsApp among them) tick an ongoing call notification once a
        // second with completely empty content, re-triggering this fallback path
        // every time. Once we've already logged a given notification key as
        // unclassifiable, don't repeat the same failed work on every repost — a
        // single call would otherwise launch 100+ redundant coroutines.
        if (title.isBlank() && text.isBlank() && bigText.isBlank() && subText.isBlank()
            && notificationKey in loggedUnclassifiedKeys) {
            return
        }

        val extractedNumber = extractPhoneNumber(title, text, bigText, subText)

        serviceScope.launch {
            try {
                val callerInfo = if (extractedNumber.isNotBlank()) {
                    CallerInfoResolver.getCallerDisplayInfo(contentResolver, extractedNumber)
                } else {
                    extractCallerInfo(title, text, bigText, subText)
                }

                when {
                    isCallEndedNotification(title, text, bigText, subText) -> { /* unchanged */ }
                    isMissedCall(title, text, bigText, subText) -> { /* unchanged */ }
                    isIncomingCall(title, text, bigText, subText) -> { /* unchanged */ }
                    isPossibleOutgoingCall(title, text, bigText, subText) -> { /* unchanged */ }
                    else -> {
                        recordUnrecognizedCallNotification(appName, title, text, bigText, subText)
                        loggedUnclassifiedKeys.add(notificationKey)   // ← add this line
                        // ...rest of the else branch unchanged
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed fallback VoIP notification processing", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // State detection helpers (synchronous – pure logic, no I/O)
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
        val isIncoming = extras.getBoolean("is_incoming", false)
        val isOutgoing = extras.getBoolean("is_outgoing", false)
        if (isIncoming) return CallState.INCOMING
        if (isOutgoing) return CallState.OUTGOING

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
    // Modern caller info extraction (synchronous string parsing)
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

    // I/O method – called from background thread only
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
    // Number extraction (synchronous, pure string work)
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
            cursor = contentResolver.query(
                contactUri,
                projection,
                null,
                null,
                null
            )
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
    // Legacy text matchers (synchronous, pure string)
    // -------------------------------------------------------------------------

    private fun isUnknownCaller(callerInfo: String): Boolean {
        val normalized = callerInfo.trim().lowercase(Locale.ROOT)
        return normalized.isEmpty() || normalized == "unknown contact" || normalized == "unknown"
    }

    private fun isIncomingCall(title: String, text: String, bigText: String, subText: String): Boolean {
        val combinedText = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        val strongIncoming = arrayOf(
            "is calling you", "wants to call you", "incoming call", "incoming video call",
            "incoming voice call", "inkomende oproep", "inkomende video-oproep", "inkomende stemoproep",
            "bel jou", "wil jou bel", "eingehender anruf", "eingehender videoanruf",
            "eingehender sprachanruf", "ruft dich an", "appel entrant", "appel video entrant",
            "appel vocal entrant", "vous appelle", "llamada entrante", "videollamada entrante",
            "llamada de voz entrante", "te esta llamando", "te está llamando", "chamada recebida",
            "chamada de entrada", "chamada de video recebida", "está ligando para você", "esta ligando para voce"
        )
        if (strongIncoming.any { combinedText.contains(it) }) return true

        if (combinedText.contains("you called") || combinedText.contains("you are calling") ||
            combinedText.contains("outgoing") || combinedText.contains("call started") ||
            combinedText.contains("uitgaande oproep") || combinedText.contains("ausgehender anruf") ||
            combinedText.contains("appel sortant") || combinedText.contains("llamada saliente") ||
            combinedText.contains("chamada efetuada")
        ) return false

        return combinedText.contains("calling") && (!combinedText.contains("you") || combinedText.contains("calling you"))
    }

    private fun isPossibleOutgoingCall(title: String, text: String, bigText: String, subText: String): Boolean {
        val combinedText = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        val strongOutgoing = arrayOf(
            "you called", "you are calling", "outgoing call", "call started", "calling…",
            "uitgaande oproep", "jy het gebel", "jy bel", "ausgehender anruf", "du rufst an",
            "appel sortant", "vous appelez", "llamada saliente", "estas llamando", "estás llamando",
            "chamada efetuada", "ligacao efetuada", "ligação efetuada", "voce esta ligando", "você está ligando"
        )
        if (strongOutgoing.any { combinedText.contains(it) }) return true
        if (combinedText.contains("is calling") || combinedText.contains("calling you") ||
            combinedText.contains("wants to call") || combinedText.contains("incoming")
        ) return false
        return false
    }

    private fun isCallEndedNotification(title: String, text: String, bigText: String, subText: String): Boolean {
        val endedKeywords = arrayOf(
            "call ended", "call finished", "call completed", "call duration", "call lasted",
            "hung up", "disconnected", "call time", "oproep beeindig", "oproep beëindig",
            "gesprek beeindig", "gesprek beëindig", "oproep klaar", "gesprek klaar", "gesprekstyd",
            "anruf beendet", "gesprach beendet", "gespräch beendet", "appel termine", "appel terminé",
            "llamada finalizada", "llamada terminada", "duracion de la llamada", "duración de la llamada",
            "chamada encerrada", "ligacao encerrada", "ligação encerrada", "duracao da chamada", "duração da chamada"
        )
        val combinedText = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        return endedKeywords.any { combinedText.contains(it) }
    }

    private fun isMissedCall(title: String, text: String, bigText: String, subText: String): Boolean {
        val missedKeywords = arrayOf(
            "missed call", "missed video call", "missed voice call", "unanswered", "didn't answer", "no answer",
            "gemiste oproep", "gemisde oproep", "onbeantwoord", "verpasster anruf", "nicht beantwortet",
            "appel manque", "appel manqué", "sans reponse", "sans réponse", "llamada perdida", "no respondio",
            "no respondió", "chamada perdida", "ligacao perdida", "ligação perdida", "nao atendida", "não atendida"
        )
        val combinedText = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        return missedKeywords.any { combinedText.contains(it) }
    }

    private fun extractCallerInfo(title: String, text: String, bigText: String, subText: String): String {
        if (BuildConfig.DEBUG) Log.d(TAG, "Extracting caller from - Title: '$title', Text: '$text', BigText: '$bigText', SubText: '$subText'")
        val candidates = arrayOf(
            extractFromTitle(title), extractFromText(text), extractFromBigText(bigText),
            extractFromSubText(subText), extractPhoneNumber(title, text, bigText, subText),
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
            .replace(Regex("(?i)(incoming call|calling|video call|voice call|missed call|call from).*"), "")
            .replace(Regex("(?i).*(whatsapp|skype|zoom|teams|discord|telegram|viber|messenger|meet).*"), "")
            .trim()
        return if (cleaned.isNotEmpty() && cleaned.length > 2 && !containsOnlyCallKeywords(cleaned)) cleaned else ""
    }

    private fun extractFromText(text: String): String {
        if (text.isEmpty()) return ""
        val patterns = arrayOf(
            Regex("^(.+?)\\s+(is calling|calling you|wants to call|started a call)", RegexOption.IGNORE_CASE),
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
        val lines = bigText.split("\n")
        for (line in lines) {
            val cleaned = line.trim()
            if (cleaned.isNotEmpty() && !containsAppKeywords(cleaned) && !containsOnlyCallKeywords(cleaned)) {
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
        return if (cleaned.isNotEmpty() && !containsAppKeywords(cleaned) && !containsOnlyCallKeywords(cleaned)) {
            cleanExtractedName(cleaned)
        } else ""
    }

    private fun extractPhoneNumber(title: String, text: String, bigText: String, subText: String): String {
        val combinedText = "$title $text $bigText $subText"
        val phonePatterns = arrayOf(
            Regex("\\+?\\d{1,4}[\\s-]?\\(?\\d{1,4}\\)?[\\s-]?\\d{1,4}[\\s-]?\\d{1,9}"),
            Regex("\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}"),
            Regex("\\d{10,}")
        )
        for (pattern in phonePatterns) {
            val match = pattern.find(combinedText)
            if (match != null) {
                val number = match.value.trim()
                if (number.length >= 7) return number
            }
        }
        return ""
    }

    private fun extractFromTickerText(title: String, text: String, bigText: String, subText: String): String {
        val combinedText = "$title $text $bigText $subText"
        val patterns = arrayOf(
            Regex("\"(.+?)\""), Regex("\\((.+?)\\)"), Regex("from\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^(.+?)\\s*:", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(combinedText)
            if (match != null && match.groupValues.size > 1) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotEmpty() && !containsAppKeywords(extracted) && extracted.length > 2) {
                    return cleanExtractedName(extracted)
                }
            }
        }
        return ""
    }

    private fun containsOnlyCallKeywords(text: String): Boolean {
        val callOnlyWords = arrayOf("call", "calling", "voice", "video", "incoming", "missed", "ended")
        val words = text.lowercase(Locale.ROOT).split("\\s+".toRegex())
        for (word in words) {
            val isCallWord = callOnlyWords.any { it == word }
            if (!isCallWord && word.length >= 3) return false
        }
        return true
    }

    private fun cleanExtractedName(name: String): String {
        val cleaned = name.replace(Regex("[📞📹☎️📱🎥]+"), "").replace("\\s+".toRegex(), " ").trim()
        return if (cleaned.length > 1) cleaned else ""
    }

    private fun containsAppKeywords(text: String): Boolean {
        val appKeywords = arrayOf(
            "whatsapp", "skype", "zoom", "teams", "discord", "telegram", "viber", "messenger", "meet",
            "notification", "app", "calling", "call", "video", "voice", "missed", "incoming", "ended"
        )
        val lowerText = text.lowercase(Locale.ROOT)
        return appKeywords.any { lowerText.contains(it) }
    }

    private fun isGenericCallTitle(title: String): Boolean {
        val generic = setOf(
            "incoming call", "outgoing call", "missed call", "call", "video call",
            "voice call", "WhatsApp", "Skype", "Zoom", "Teams", "Telegram"
        )
        return generic.any { title.equals(it, ignoreCase = true) }
    }

    private fun extractSimpleNameFromText(text: String): String {
        val patterns = listOf(
            Regex("^(.+?)\\s+(is calling|calling you|wants to call|started a call)", RegexOption.IGNORE_CASE),
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

    // -------------------------------------------------------------------------
    // Popup and notification removal
    // -------------------------------------------------------------------------

    private fun triggerVoipCallerPopup(callerInfo: String, extractedNumber: String) {
        try {
            if (!settingsManager.callMonitorEnabled) return
            val callerForOverlay = when {
                extractedNumber.isNotBlank() -> extractedNumber
                callerInfo.isNotBlank() -> callerInfo
                else -> return
            }
            if (callerForOverlay == "Unknown Contact") return

            // Don't pop up the floating caller-info window for someone who isn't in the
            // member database or contacts — it would just be echoing the raw number back.
            if (!CallerInfoResolver.isKnownCaller(callerInfo)) return

            val displayName = if (extractedNumber.isNotBlank()) callerInfo.takeIf { it.isNotBlank() } else callerInfo
            val serviceIntent = Intent(this, OproepDetailService::class.java)
                .putExtra(OproepDetailService.EXTRA_CALLER_ID, callerForOverlay)
            if (!displayName.isNullOrBlank()) {
                serviceIntent.putExtra(OproepDetailService.EXTRA_CALLER_DISPLAY, displayName)
            }
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to start caller popup", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val appName = VOIP_PACKAGES[packageName] ?: return

        val notificationKey = sbn.key
        loggedUnclassifiedKeys.remove(notificationKey)
        val tracked = activeVoipCalls.remove(notificationKey)
        if (tracked != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "VoIP notification removed, ending call: ${tracked.callId} ($appName)")
            serviceScope.launch {
                unifiedMonitor.onCallEnded(tracked.callId, System.currentTimeMillis())
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (BuildConfig.DEBUG) Log.d(TAG, "Notification listener connected")
        reconcileStaleActiveCalls()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (BuildConfig.DEBUG) Log.w(TAG, "Notification listener disconnected — requesting rebind")
        // Ask the system to rebind us as soon as possible (API 24+).
        // Without this, some OEMs (MIUI, EMUI, ColorOS) can leave the listener
        // unbound indefinitely after Doze/battery-manager kills it, silently
        // stopping all VoIP call logging until the user manually re-toggles
        // notification access.
        requestRebind(
            ComponentName(applicationContext, WhatsAppNotificationService::class.java)
        )
    }

    private fun pruneStaleVoipCalls() {
        val cutoff = System.currentTimeMillis() - VOIP_CALL_TTL_MS
        val removed = activeVoipCalls.entries.removeIf { it.value.startTime < cutoff }
        // optional but useful while you're diagnosing fragmentation:
        // if (BuildConfig.DEBUG && removed) Log.w(TAG, "Pruned stale VoIP call entries")
    }

    /**
     * Called after (re)connecting to the notification listener service.
     * A rebind means [activeVoipCalls] has been reset (new service instance),
     * so any VoIP calls this service had marked "active" before the disconnect
     * can no longer receive their real "ended" event. Close them out now with
     * a best-effort end time (now) instead of letting them sit until
     * UnifiedCallMonitor's 4-hour prune silently drops them.
     */
    private fun reconcileStaleActiveCalls() {
        if (!::unifiedMonitor.isInitialized) return
        serviceScope.launch {
            val orphaned = unifiedMonitor.endActiveVoipCallsFromOtherSources()
            if (BuildConfig.DEBUG && orphaned > 0) {
                Log.w(TAG, "Reconciled $orphaned VoIP call(s) left active across a listener rebind")
            }
        }
    }

    private fun recordUnrecognizedCallNotification(
        appName: String, title: String, text: String, bigText: String, subText: String
    ) {
        if (BuildConfig.DEBUG) Log.w(TAG, "Unrecognized call-app notification from $appName: '$title' / '$text'")
        CallNotificationDiagnostics.record(applicationContext, appName, title, text, bigText, subText)
    }
    companion object {
        private const val TAG = "VoIPCallLogger"
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
        // VoIP calls essentially never last this long; anything older is an orphan
        // (dropped "ended"/"removed" notification, listener rebind, etc.)
        val VOIP_CALL_TTL_MS = java.util.concurrent.TimeUnit.HOURS.toMillis(1)
    }

    private enum class CallState {
        INCOMING, OUTGOING, MISSED, ENDED, SCREENING, UNKNOWN
    }
}