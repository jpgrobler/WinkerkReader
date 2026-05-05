package za.co.jpsoft.winkerkreader.services

import android.app.Notification
import android.content.ContentUris
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import za.co.jpsoft.winkerkreader.data.DatabaseHelper
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.services.OproepDetailService
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
        // Use DatabaseHelper directly – remove DatabaseHelperProvider if not needed
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
        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""

        // Extract once at the beginning
        val extractedNumber = extractPhoneNumber(title, text, bigText, subText)
        val callerInfo = if (extractedNumber.isNotBlank()) {
            CallerInfoResolver.getCallerDisplayInfo(contentResolver, extractedNumber)
        } else {
            // Fall back to raw extraction if no number found
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
                // Reuse the already extracted number and callerInfo
                val callId = activeVoipCalls.remove(notificationKey)
                if (callId != null) {
                    unifiedMonitor.onCallEnded(callId, System.currentTimeMillis())
                } else {
                    // Missed call might not have a start notification; treat directly
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
            if (OproepDetailService.isOn || OproepDetailService.isServiceActive()) return
            if (!settingsManager.callMonitorEnabled) return
            val callerForOverlay = when {
                extractedNumber.isNotBlank() -> extractedNumber
                callerInfo.isNotBlank() -> callerInfo
                else -> return
            }
            if (callerForOverlay == "Unknown Contact") return

            val prefs = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)
            prefs.edit().putString("CallerNumber", callerForOverlay).apply()

            val serviceIntent = Intent(this, OproepDetailService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start caller popup", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not used
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
}