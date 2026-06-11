package za.co.jpsoft.winkerkreader.services

import android.app.Notification
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import za.co.jpsoft.winkerkreader.data.DatabaseHelper
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import za.co.jpsoft.winkerkreader.utils.CallerInfoResolver
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.UnifiedCallMonitor
import java.util.*

class WhatsAppNotificationService : NotificationListenerService() {

    private lateinit var unifiedMonitor: UnifiedCallMonitor
    private lateinit var settingsManager: SettingsManager
    // Map notification key -> callId (used to end the correct call later)
    private val activeVoipCalls = mutableMapOf<String, String>()

    override fun onCreate() {
        super.onCreate()
        initialize()
        Log.d(TAG, "WhatsAppNotificationService created")
    }

    private fun initialize() {
        settingsManager = SettingsManager.getInstance(this)
        val databaseHelper = DatabaseHelper.getInstance(this)
        val calendarManager = CalendarManager(this)
        val calendarId = settingsManager.selectedCalendarId
        unifiedMonitor = UnifiedCallMonitor.getInstance(this, databaseHelper, calendarManager, calendarId)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        if (!settingsManager.voipLogEnabled) return

        val packageName = sbn.packageName
        val appName = VOIP_PACKAGES[packageName] ?: return

        processVoIPNotification(sbn, appName)
    }

    private fun processVoIPNotification(sbn: StatusBarNotification, appName: String) {
        val notificationKey = sbn.key
        val notification = sbn.notification
        val extras = notification.extras ?: return

        // Diagnostic logging — helps debug new VoIP apps and notification format changes
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        Log.d(TAG, "VoIP notification from $appName: category=${notification.category}, " +
                "callType=${extras.getInt(Notification.EXTRA_CALL_TYPE, -1)}, " +
                "title='$title', text='$text', key=$notificationKey")

        // 1. Primary detection using standard Android notification properties
        val category = notification.category
        val callTypeExtra = extras.getInt(Notification.EXTRA_CALL_TYPE, -1)
        val isCallStyle = callTypeExtra in 1..3  // 1=incoming, 2=outgoing, 3=screening

        // 2. Extract caller info using modern APIs first
        val callerInfo = extractCallerInfoModern(extras)

        // 3. Determine call direction/state
        val callState = detectCallState(category, callTypeExtra, isCallStyle, extras, sbn)

        // 4. Process based on detected state
        when (callState) {
            CallState.INCOMING -> {
                if (isUnknownCaller(callerInfo)) return
                val callId = "voip_${appName}_${System.currentTimeMillis()}"
                activeVoipCalls[notificationKey] = callId
                unifiedMonitor.onCallDetected(
                    callId = callId,
                    number = extractPhoneNumberFromExtras(extras),
                    direction = "incoming",
                    source = appName,
                    timestamp = System.currentTimeMillis(),
                    displayName = callerInfo
                )
                triggerVoipCallerPopup(callerInfo, extractPhoneNumberFromExtras(extras))
            }
            CallState.SCREENING -> {
                // Treat screening calls as incoming for logging
                if (isUnknownCaller(callerInfo)) return
                val callId = "voip_${appName}_${System.currentTimeMillis()}"
                activeVoipCalls[notificationKey] = callId
                unifiedMonitor.onCallDetected(
                    callId = callId,
                    number = extractPhoneNumberFromExtras(extras),
                    direction = "incoming",
                    source = appName,
                    timestamp = System.currentTimeMillis(),
                    displayName = callerInfo
                )
                triggerVoipCallerPopup(callerInfo, extractPhoneNumberFromExtras(extras))
            }
            CallState.OUTGOING -> {
                if (isUnknownCaller(callerInfo)) return
                val callId = "voip_${appName}_${System.currentTimeMillis()}"
                activeVoipCalls[notificationKey] = callId
                unifiedMonitor.onCallDetected(
                    callId = callId,
                    number = extractPhoneNumberFromExtras(extras),
                    direction = "outgoing",
                    source = appName,
                    timestamp = System.currentTimeMillis(),
                    displayName = callerInfo
                )
            }
            CallState.MISSED -> {
                val callId = activeVoipCalls.remove(notificationKey)
                if (callId != null) {
                    unifiedMonitor.onCallEnded(callId, System.currentTimeMillis())
                } else {
                    // Missed call without a start notification
                    val callIdMissed = "voip_missed_${System.currentTimeMillis()}"
                    unifiedMonitor.onCallDetected(
                        callId = callIdMissed,
                        number = extractPhoneNumberFromExtras(extras),
                        direction = "missed",
                        source = appName,
                        timestamp = System.currentTimeMillis(),
                        displayName = callerInfo
                    )
                    unifiedMonitor.onCallEnded(callIdMissed, System.currentTimeMillis())
                }
            }
            CallState.ENDED -> {
                val callId = activeVoipCalls.remove(notificationKey)
                if (callId != null) {
                    unifiedMonitor.onCallEnded(callId, System.currentTimeMillis())
                } else {
                    Log.w(TAG, "Ended call without matching start: $notificationKey")
                }
            }
            CallState.UNKNOWN -> {
                // Fallback to legacy text-based detection for apps that don't use modern APIs
                fallbackTextBasedProcessing(sbn, appName, notificationKey)
            }
        }
    }

    /**
     * Modern caller info extraction using standard notification fields
     */
    private fun extractCallerInfoModern(extras: Bundle): String {
        // Try EXTRA_PEOPLE first (array of contact URIs)
        val peopleUris = extras.getParcelableArray(Notification.EXTRA_PEOPLE)
        if (peopleUris != null && peopleUris.isNotEmpty()) {
            for (uriObj in peopleUris) {
                if (uriObj is Uri) {
                    val displayName = resolveContactNameFromUri(uriObj)
                    if (displayName.isNotBlank()) return displayName
                }
            }
        }

        // Then EXTRA_TITLE (often the caller name)
        val title = extras.getString(Notification.EXTRA_TITLE)
        if (!title.isNullOrBlank() && !isGenericCallTitle(title)) {
            return cleanExtractedName(title)
        }

        // Then EXTRA_TEXT if it contains a simple name
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
            Log.w(TAG, "Failed to resolve contact URI: $contactUri", e)
        } finally {
            cursor?.close()
        }
        return ""
    }

    private fun extractPhoneNumberFromExtras(extras: Bundle): String {
        // First, try to get from EXTRA_PEOPLE URIs
        val peopleUris = extras.getParcelableArray(Notification.EXTRA_PEOPLE)
        if (peopleUris != null) {
            for (uriObj in peopleUris) {
                if (uriObj is Uri) {
                    val phone = resolvePhoneNumberFromContactUri(uriObj)
                    if (phone.isNotBlank()) return phone
                }
            }
        }

        // Fallback to text extraction
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
        return extractPhoneNumber(title, text, bigText, subText)
    }

    private fun resolvePhoneNumberFromContactUri(contactUri: Uri): String {
        // Query contact to get a phone number (simplified - you may need to choose the right number type)
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
            Log.w(TAG, "Failed to resolve phone from contact URI", e)
        } finally {
            cursor?.close()
        }
        return ""
    }

//    private fun extractPhoneNumberFromExtras(extras: Bundle): String {
//        // Attempt to extract from notification text fields
//        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
//        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
//        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
//        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
//        return extractPhoneNumber(title, text, bigText, subText)
//    }

    private fun isGenericCallTitle(title: String): Boolean {
        val generic = setOf(
            "incoming call", "outgoing call", "missed call", "call", "video call",
            "voice call", "WhatsApp", "Skype", "Zoom", "Teams", "Telegram"
        )
        return generic.any { title.equals(it, ignoreCase = true) }
    }

    private fun extractSimpleNameFromText(text: String): String {
        // Look for patterns like "John Doe is calling" or "Call from Jane Smith"
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

    /**
     * Detect call state using standard Android notification fields
     */
    private fun detectCallState(
        category: String?,
        callTypeExtra: Int,
        isCallStyle: Boolean,
        extras: Bundle,
        sbn: StatusBarNotification
    ): CallState {
        // 1. Primary: Use notification category
        when (category) {
            Notification.CATEGORY_CALL -> {
                // CallStyle provides exact type
                if (isCallStyle) {
                    return when (callTypeExtra) {
                        1 -> CallState.INCOMING
                        2 -> CallState.OUTGOING
                        3 -> CallState.SCREENING
                        else -> CallState.INCOMING // fallback
                    }
                }
                // Without CallStyle, try to infer from extras
                return inferCallStateFromExtras(extras)
            }
            Notification.CATEGORY_MISSED_CALL -> return CallState.MISSED
        }

        // 2. No standard category, but maybe CallStyle extras without category
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
        // Some apps set flags like isIncoming via extras (non-standard)
        val isIncoming = extras.getBoolean("is_incoming", false)
        val isOutgoing = extras.getBoolean("is_outgoing", false)
        if (isIncoming) return CallState.INCOMING
        if (isOutgoing) return CallState.OUTGOING

        // Check all text fields for call state indicators
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""

        // Use the full legacy keyword checks for best coverage
        if (isCallEndedNotification(title, text, bigText, subText)) return CallState.ENDED
        if (isMissedCall(title, text, bigText, subText)) return CallState.MISSED
        if (isIncomingCall(title, text, bigText, subText)) return CallState.INCOMING
        if (isPossibleOutgoingCall(title, text, bigText, subText)) return CallState.OUTGOING

        // If we have an active call for CATEGORY_CALL, this is an ongoing update
        return CallState.INCOMING // Default: CATEGORY_CALL usually means incoming
    }

    /**
     * Legacy fallback processing using text keyword matching
     */
    private fun fallbackTextBasedProcessing(sbn: StatusBarNotification, appName: String, notificationKey: String) {
        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""

        val extractedNumber = extractPhoneNumber(title, text, bigText, subText)
        val callerInfo = if (extractedNumber.isNotBlank()) {
            CallerInfoResolver.getCallerDisplayInfo(contentResolver, extractedNumber)
        } else {
            extractCallerInfo(title, text, bigText, subText)
        }

        when {
            isCallEndedNotification(title, text, bigText, subText) -> {
                val callId = activeVoipCalls.remove(notificationKey)
                if (callId != null) {
                    unifiedMonitor.onCallEnded(callId, System.currentTimeMillis())
                } else {
                    Log.w(TAG, "Ended call without matching start: $notificationKey")
                }
            }
            isMissedCall(title, text, bigText, subText) -> {
                val callId = activeVoipCalls.remove(notificationKey)
                if (callId != null) {
                    unifiedMonitor.onCallEnded(callId, System.currentTimeMillis())
                } else {
                    val callIdMissed = "voip_missed_${System.currentTimeMillis()}"
                    unifiedMonitor.onCallDetected(
                        callId = callIdMissed,
                        number = extractedNumber,
                        direction = "missed",
                        source = appName,
                        timestamp = System.currentTimeMillis(),
                        displayName = callerInfo
                    )
                    unifiedMonitor.onCallEnded(callIdMissed, System.currentTimeMillis())
                }
            }
            isIncomingCall(title, text, bigText, subText) -> {
                if (isUnknownCaller(callerInfo)) return
                val callId = "voip_${appName}_${System.currentTimeMillis()}"
                activeVoipCalls[notificationKey] = callId
                unifiedMonitor.onCallDetected(
                    callId = callId,
                    number = extractedNumber,
                    direction = "incoming",
                    source = appName,
                    timestamp = System.currentTimeMillis(),
                    displayName = callerInfo
                )
                triggerVoipCallerPopup(callerInfo, extractedNumber)
            }
            isPossibleOutgoingCall(title, text, bigText, subText) -> {
                if (isUnknownCaller(callerInfo)) return
                val callId = "voip_${appName}_${System.currentTimeMillis()}"
                activeVoipCalls[notificationKey] = callId
                unifiedMonitor.onCallDetected(
                    callId = callId,
                    number = extractedNumber,
                    direction = "outgoing",
                    source = appName,
                    timestamp = System.currentTimeMillis(),
                    displayName = callerInfo
                )
            }
        }
    }

    // ==== Legacy text matching helpers (unchanged except for minor improvements) ====
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
            "you called", "you are calling", "outgoing call", "call started", "calling...",
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
        Log.d(TAG, "Extracting caller from - Title: '$title', Text: '$text', BigText: '$bigText', SubText: '$subText'")
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

    private fun triggerVoipCallerPopup(callerInfo: String, extractedNumber: String) {
        try {
            if (!settingsManager.callMonitorEnabled) return
            val callerForOverlay = when {
                extractedNumber.isNotBlank() -> extractedNumber
                callerInfo.isNotBlank() -> callerInfo
                else -> return
            }
            if (callerForOverlay == "Unknown Contact") return

            val displayName = if (extractedNumber.isNotBlank()) callerInfo.takeIf { it.isNotBlank() } else callerInfo
            val serviceIntent = Intent(this, OproepDetailService::class.java)
                .putExtra(OproepDetailService.EXTRA_CALLER_ID, callerForOverlay)
            if (!displayName.isNullOrBlank()) {
                serviceIntent.putExtra(OproepDetailService.EXTRA_CALLER_DISPLAY, displayName)
            }
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start caller popup", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // When a VoIP app removes the call notification, the call has ended.
        // This is often the ONLY signal for call ending — many apps don't post
        // a separate "call ended" notification.
        val packageName = sbn.packageName
        val appName = VOIP_PACKAGES[packageName] ?: return

        val notificationKey = sbn.key
        val callId = activeVoipCalls.remove(notificationKey)
        if (callId != null) {
            Log.d(TAG, "VoIP notification removed, ending call: $callId ($appName)")
            unifiedMonitor.onCallEnded(callId, System.currentTimeMillis())
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
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
    }

    private enum class CallState {
        INCOMING, OUTGOING, MISSED, ENDED, SCREENING, UNKNOWN
    }
}