package za.co.jpsoft.winkerkreader.services

import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.CalendarManager

import android.app.Notification
import android.content.ContentUris
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import za.co.jpsoft.winkerkreader.data.DatabaseHelper
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.models.CallType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class WhatsAppNotificationService : NotificationListenerService() {

    private var databaseHelper: DatabaseHelper? = null
    private var settingsManager: SettingsManager? = null
    private val recentVoipEventTimes = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        initializeServices()
        Log.d(TAG, "VoIP Call Notification Service started")
    }

    private fun initializeServices() {
        try {
            if (databaseHelper == null) {
                databaseHelper = DatabaseHelper(this)
            }
            if (settingsManager == null) {
                settingsManager = SettingsManager.getInstance(this)
            }
            Log.d(TAG, "Services initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing services", e)
        }
    }

    private fun ensureServicesInitialized() {
        if (settingsManager == null || databaseHelper == null) {
            Log.w(TAG, "Services not initialized, initializing now")
            initializeServices()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        try {
            ensureServicesInitialized()
            if (settingsManager == null) {
                Log.e(TAG, "SettingsManager is null, cannot check VOIP logging status")
                return
            }
            if (!settingsManager!!.voipLogEnabled) { // Use property instead of isVoipLogEnabled()
                Log.d(TAG, "VOIP logging is disabled, skipping notification")
                return
            }

            val packageName = sbn.packageName
            val appName = VOIP_PACKAGES[packageName]
            if (appName != null) {
                processVoIPNotification(sbn, appName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNotificationPosted", e)
        }
    }

    private fun processVoIPNotification(sbn: StatusBarNotification, appName: String) {
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""

        Log.d(TAG, "=== $appName Notification Debug ===")
        Log.d(TAG, "Title: '$title'")
        Log.d(TAG, "Text: '$text'")
        Log.d(TAG, "BigText: '$bigText'")
        Log.d(TAG, "SubText: '$subText'")
        Log.d(TAG, "InfoText: '$infoText'")
        Log.d(TAG, "SummaryText: '$summaryText'")

        val extractedNumber = extractPhoneNumber(title, text, bigText, subText)
        val rawCallerInfo = extractCallerInfo(title, text, bigText, subText)
        val dbCallerInfo = if (extractedNumber.isNotBlank()) lookupCallerInfoInMemberDb(extractedNumber) else null
        val callerInfo = buildBestCallerInfo(rawCallerInfo, extractedNumber, dbCallerInfo)

        when {
            isMissedCall(title, text, bigText, subText) -> {
                logCall(callerInfo, CallType.MISSED, appName, extractedNumber)
                return
            }
            isCallEndedNotification(title, text, bigText, subText) -> {
                logCall(callerInfo, CallType.ENDED, appName, extractedNumber)
                return
            }
            isIncomingCall(title, text, bigText, subText) -> {
                if (!isUnknownCaller(callerInfo)) {
                    logCall(callerInfo, CallType.INCOMING, appName, extractedNumber)
                    triggerVoipCallerPopup(callerInfo, extractedNumber)
                } else {
                    Log.d(TAG, "VoIP dedupe rule: unknown-skip (incoming, app=$appName)")
                }
                return
            }
            isPossibleOutgoingCall(title, text, bigText, subText) -> {
                if (!isUnknownCaller(callerInfo)) {
                    logCall(callerInfo, CallType.OUTGOING, appName, extractedNumber)
                } else {
                    Log.d(TAG, "VoIP dedupe rule: unknown-skip (outgoing, app=$appName)")
                }
                return
            }
        }
    }

    private fun buildBestCallerInfo(rawCallerInfo: String, number: String, dbCallerInfo: String?): String {
        if (!dbCallerInfo.isNullOrBlank()) {
            return if (number.isNotBlank()) "$dbCallerInfo ($number)" else dbCallerInfo
        }
        if (rawCallerInfo.isNotBlank() && rawCallerInfo != "Unknown Contact") {
            return rawCallerInfo
        }
        return if (number.isNotBlank()) number else "Unknown Contact"
    }

    private fun isUnknownCaller(callerInfo: String): Boolean {
        val normalized = callerInfo.trim().lowercase(Locale.ROOT)
        return normalized.isEmpty() || normalized == "unknown contact" || normalized == "unknown"
    }

    private fun lookupCallerInfoInMemberDb(phoneNumber: String): String? {
        return try {
            val searchNumber = phoneNumber.filter { it.isDigit() }.takeLast(9)
            if (searchNumber.isBlank()) return null

            val queryUri = ContentUris.withAppendedId(WinkerkContract.winkerkEntry.CONTENT_FOON_URI, 0)
            val selection = """
            ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_INFO} FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
            WHERE (REPLACE([${WinkerkContract.winkerkEntry.LIDMATE_SELFOON}],' ','') LIKE '%$searchNumber')
               OR (REPLACE([${WinkerkContract.winkerkEntry.LIDMATE_LANDLYN}],' ','') LIKE '%$searchNumber')
               OR (REPLACE([${WinkerkContract.winkerkEntry.LIDMATE_WERKFOON}],' ','') LIKE '%$searchNumber');
        """.trimIndent()

            val cursor = contentResolver.query(queryUri, arrayOf(""), selection, null, null)
            cursor?.use { c ->
                if (!c.moveToFirst()) return null
                val firstNameIdx = c.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)
                val surnameIdx = c.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_VAN)
                if (firstNameIdx < 0 || surnameIdx < 0) return null
                val firstName = c.getString(firstNameIdx) ?: return null
                val surname = c.getString(surnameIdx) ?: return null
                return "$firstName $surname".trim()
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve VoIP caller", e)
            null
        }
    }

    private fun isIncomingCall(title: String, text: String, bigText: String, subText: String): Boolean {
        val combinedText = "$title $text $bigText $subText".lowercase(Locale.ROOT)
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
        if (strongIncoming.any { combinedText.contains(it) }) return true

        if (combinedText.contains("you called") ||
            combinedText.contains("you are calling") ||
            combinedText.contains("outgoing") ||
            combinedText.contains("call started") ||
            combinedText.contains("uitgaande oproep") ||
            combinedText.contains("ausgehender anruf") ||
            combinedText.contains("appel sortant") ||
            combinedText.contains("llamada saliente") ||
            combinedText.contains("chamada efetuada")
        ) return false

        return combinedText.contains("calling") &&
                (!combinedText.contains("you") || combinedText.contains("calling you"))
    }

    private fun isPossibleOutgoingCall(title: String, text: String, bigText: String, subText: String): Boolean {
        val combinedText = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        val strongOutgoing = arrayOf(
            "you called",
            "you are calling",
            "outgoing call",
            "call started",
            "calling...",
            "uitgaande oproep",
            "jy het gebel",
            "jy bel",
            "ausgehender anruf",
            "du rufst an",
            "appel sortant",
            "vous appelez",
            "llamada saliente",
            "estas llamando",
            "estás llamando",
            "chamada efetuada",
            "ligacao efetuada",
            "ligação efetuada",
            "voce esta ligando",
            "você está ligando"
        )
        if (strongOutgoing.any { combinedText.contains(it) }) return true
        if (combinedText.contains("is calling") ||
            combinedText.contains("calling you") ||
            combinedText.contains("wants to call") ||
            combinedText.contains("incoming")
        ) return false
        return false
    }

    private fun isCallEndedNotification(title: String, text: String, bigText: String, subText: String): Boolean {
        val endedKeywords = arrayOf(
            "call ended", "call finished", "call completed", "call duration",
            "call lasted", "hung up", "disconnected", "call time",
            "oproep beeindig", "oproep beëindig", "gesprek beeindig", "gesprek beëindig",
            "oproep klaar", "gesprek klaar", "gesprekstyd",
            "anruf beendet", "gesprach beendet", "gespräch beendet",
            "appel termine", "appel terminé",
            "llamada finalizada", "llamada terminada", "duracion de la llamada", "duración de la llamada",
            "chamada encerrada", "ligacao encerrada", "ligação encerrada", "duracao da chamada", "duração da chamada"
        )
        val combinedText = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        return endedKeywords.any { combinedText.contains(it) }
    }

    private fun isMissedCall(title: String, text: String, bigText: String, subText: String): Boolean {
        val missedKeywords = arrayOf(
            "missed call", "missed video call", "missed voice call",
            "unanswered", "didn't answer", "no answer",
            "gemiste oproep", "gemisde oproep", "onbeantwoord",
            "verpasster anruf", "nicht beantwortet",
            "appel manque", "appel manqué", "sans reponse", "sans réponse",
            "llamada perdida", "no respondio", "no respondió",
            "chamada perdida", "ligacao perdida", "ligação perdida", "nao atendida", "não atendida"
        )
        val combinedText = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        return missedKeywords.any { combinedText.contains(it) }
    }

    private fun extractCallerInfo(title: String, text: String, bigText: String, subText: String): String {
        Log.d(TAG, "Extracting caller from - Title: '$title', Text: '$text', BigText: '$bigText', SubText: '$subText'")
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
                val result = candidate.trim()
                Log.d(TAG, "Extracted caller: '$result'")
                return result
            }
        }
        Log.d(TAG, "Extracted caller: 'Unknown Contact'")
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
            Regex("\"(.+?)\""),
            Regex("\\((.+?)\\)"),
            Regex("from\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
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
            "whatsapp", "skype", "zoom", "teams", "discord", "telegram",
            "viber", "messenger", "meet", "notification", "app", "calling",
            "call", "video", "voice", "missed", "incoming", "ended"
        )
        val lowerText = text.lowercase(Locale.ROOT)
        return appKeywords.any { lowerText.contains(it) }
    }

    private fun logCall(callerInfo: String, callType: CallType, appName: String, numberHint: String = "") {
        try {
            ensureServicesInitialized()
            if (databaseHelper == null) {
                Log.e(TAG, "DatabaseHelper is null, cannot log call")
                return
            }
            val eventKey = "${appName.lowercase(Locale.ROOT)}|${callType.name}|${callerInfo.lowercase(Locale.ROOT)}|${numberHint.filter { it.isDigit() }}"
            val now = System.currentTimeMillis()
            val recentTime = recentVoipEventTimes[eventKey]
            if (recentTime != null && now - recentTime < 15000L) {
                Log.d(TAG, "VoIP dedupe rule: debounce-skip (key=$eventKey)")
                return
            }

            val timestamp = System.currentTimeMillis()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val dateTime = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(formatter)

            Log.d(TAG, "$appName call detected - Type: $callType, Contact: $callerInfo, Time: $dateTime")

            if (isDuplicateCall(callerInfo, timestamp, callType, appName)) {
                Log.d(TAG, "VoIP dedupe rule: db-duplicate-skip (contact=$callerInfo, type=$callType, app=$appName)")
                return
            }

            val success = databaseHelper!!.insertCallLogWithType(callerInfo, timestamp, callType, appName)
            if (success) {
                Log.d(TAG, "$appName call log saved to database successfully")
                logToCalendar(callerInfo, timestamp, callType, appName)
                broadcastCallLogUpdate()
                recentVoipEventTimes[eventKey] = now
            } else {
                Log.e(TAG, "Failed to save $appName call log")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging call", e)
        }
    }

    private fun triggerVoipCallerPopup(callerInfo: String, extractedNumber: String) {
        try {
            if (OproepDetailService.isOn || OproepDetailService.isServiceActive()) return
            if (settingsManager?.callMonitorEnabled != true) return

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
            Log.d(TAG, "Started caller popup for incoming VoIP call: $callerForOverlay")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start caller popup for VoIP call", e)
        }
    }

    private fun isDuplicateCall(callerInfo: String, timestamp: Long, callType: CallType, appName: String): Boolean {
        try {
            if (databaseHelper == null) {
                Log.w(TAG, "DatabaseHelper is null, cannot check for duplicates")
                return false
            }
            val timeWindow = 30_000L // 30 seconds
            val recentCalls = databaseHelper!!.getRecentCallLogs(timeWindow)

            for (call in recentCalls) {
                val callerMatch = isSimilarCaller(call.callerInfo, callerInfo)
                val sourceMatch = call.source.equals(appName, ignoreCase = true)
                val timeMatch = kotlin.math.abs(call.timestamp - timestamp) < timeWindow

                if (callerMatch && sourceMatch && timeMatch) {
                    Log.d(TAG, "Duplicate detected (same caller, app, time) - Existing: ${call.callType}, New: $callType")
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for duplicates", e)
            return false
        }
    }

    private fun isSimilarCaller(existing: String, newCaller: String): Boolean {
        if (existing.equals(newCaller, ignoreCase = true)) return true
        if ((existing == "Unknown Contact" && newCaller != "Unknown Contact") ||
            (newCaller == "Unknown Contact" && existing != "Unknown Contact")
        ) return false

        val existingClean = existing.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        val newClean = newCaller.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

        return existingClean.contains(newClean) || newClean.contains(existingClean) ||
                levenshteinDistance(existingClean, newClean) <= 2
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun logToCalendar(callerInfo: String, timestamp: Long, callType: CallType, appName: String) {
        try {
            val calendarManager = CalendarManager(this)
            val prefs: SharedPreferences = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)
            val selectedCalendarId = prefs.getLong("selected_calendar_id", -1L)

            if (selectedCalendarId != -1L) {
                val calendarSuccess = calendarManager.addCallEventToCalendar(
                    selectedCalendarId, callerInfo, timestamp, callType, appName, 0
                )
                if (calendarSuccess) {
                    Log.d(TAG, "$appName call logged to calendar successfully")
                } else {
                    Log.e(TAG, "Failed to log $appName call to calendar")
                }
            } else {
                Log.d(TAG, "No calendar selected, skipping calendar logging")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging $appName call to calendar", e)
        }
    }

    private fun broadcastCallLogUpdate() {
        try {
            val intent = Intent(za.co.jpsoft.winkerkreader.utils.PhoneCallMonitor.ACTION_CALL_LOG_UPDATED)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting call log update", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // Not used
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        ensureServicesInitialized()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected - requesting rebind")
    }

    override fun onDestroy() {
        try {
            Log.d(TAG, "Service destroying - cleaning up resources")
            databaseHelper?.close()
            databaseHelper = null
            settingsManager = null
            Log.d(TAG, "VoIP Call Notification Service destroyed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        } finally {
            super.onDestroy()
        }
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
            "com.google.android.apps.tachyon" to "Google Meet",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.snapchat.android" to "Snapchat",
            "jp.naver.line.android" to "LINE",
            "com.kakao.talk" to "KakaoTalk",
            "com.imo.android.imoim" to "imo",
            "com.rebtel.contacts" to "Rebtel",
            "com.enflick.android.TextNow" to "TextNow"
        )
    }
}