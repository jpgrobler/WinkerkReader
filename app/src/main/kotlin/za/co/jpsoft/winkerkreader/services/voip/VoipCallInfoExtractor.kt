package za.co.jpsoft.winkerkreader.services.voip

import android.app.Notification
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.ContactsContract
import java.util.Locale

/**
 * Extracts phone numbers and caller names from notification extras and text.
 * All methods are stateless and thread-safe.
 */
class VoipCallInfoExtractor {

    // ---- Public extractors ----

    fun extractPhoneNumberFromExtras(extras: Bundle, contentResolver: ContentResolver): String {
        val peopleUris = extras.getParcelableArrayCompat(Notification.EXTRA_PEOPLE)
        if (peopleUris != null) {
            for (uriObj in peopleUris) {
                if (uriObj is Uri) {
                    val phone = resolvePhoneNumberFromContactUri(uriObj, contentResolver)
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

    fun extractPhoneNumber(title: String, text: String, bigText: String, subText: String): String {
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

    fun extractCallerInfo(
        title: String,
        text: String,
        bigText: String,
        subText: String
    ): String {
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

    @Suppress("DEPRECATION")
    private fun Bundle.getParcelableArrayCompat(key: String): Array<out Parcelable>? =
        getParcelableArray(key)

    fun extractCallerInfoModern(extras: Bundle, contentResolver: ContentResolver): String {
        val peopleUris = extras.getParcelableArrayCompat(Notification.EXTRA_PEOPLE)
        if (peopleUris != null && peopleUris.isNotEmpty()) {
            for (uriObj in peopleUris) {
                if (uriObj is Uri) {
                    val displayName = resolveContactNameFromUri(uriObj, contentResolver)
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

    fun resolveContactNameFromUri(contactUri: Uri, contentResolver: ContentResolver): String {
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(contactUri, projection, null, null, null)
            if (cursor?.moveToFirst() == true) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        } catch (e: Exception) {
            // ignored
        } finally {
            cursor?.close()
        }
        return ""
    }

    fun resolvePhoneNumberFromContactUri(contactUri: Uri, contentResolver: ContentResolver): String {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(contactUri, projection, null, null, null)
            if (cursor?.moveToFirst() == true) {
                return cursor.getString(0) ?: ""
            }
        } catch (e: Exception) {
            // ignored
        } finally {
            cursor?.close()
        }
        return ""
    }

    fun cleanExtractedName(name: String): String {
        return name.replace(Regex("[📞📹☎️📱🎥]+"), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .takeIf { it.length > 1 } ?: ""
    }

    fun extractSimpleNameFromText(text: String): String {
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

    // ---- Internal extractors ----

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

    fun containsAppKeywords(text: String): Boolean {
        val appKeywords = arrayOf(
            "whatsapp", "skype", "zoom", "teams", "discord", "telegram", "viber", "messenger",
            "meet", "notification", "app", "calling", "call", "video", "voice", "missed",
            "incoming", "ended"
        )
        val lower = text.lowercase(Locale.ROOT)
        return appKeywords.any { lower.contains(it) }
    }

    fun containsOnlyCallKeywords(text: String): Boolean {
        val callOnlyWords = arrayOf("call", "calling", "voice", "video", "incoming", "missed", "ended")
        val words = text.lowercase(Locale.ROOT).split("\\s+".toRegex())
        for (word in words) {
            if (!callOnlyWords.any { it == word } && word.length >= 3) return false
        }
        return true
    }

    fun isGenericCallTitle(title: String): Boolean {
        val generic = setOf(
            "incoming call", "outgoing call", "missed call", "call", "video call",
            "voice call", "WhatsApp", "Skype", "Zoom", "Teams", "Telegram"
        )
        return generic.any { title.equals(it, ignoreCase = true) }
    }

    fun isUnknownCaller(callerInfo: String): Boolean {
        val normalized = callerInfo.trim().lowercase(Locale.ROOT)
        return normalized.isEmpty() || normalized == "unknown contact" || normalized == "unknown"
    }
}