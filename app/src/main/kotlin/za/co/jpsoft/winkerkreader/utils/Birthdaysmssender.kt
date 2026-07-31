package za.co.jpsoft.winkerkreader.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import android.telephony.SmsManager

/**
 * Sends batch birthday/event greeting SMS messages and logs them to the
 * device's Sent folder.
 *
 * Extracted from VerjaarSmsActivity. Data access ([logSent] writes to
 * [Telephony.Sms.Sent]) belongs in a utility, not an Activity.
 *
 * Usage in VerjaarSmsActivity (replaces sendSmsToSelectedMembers body):
 *
 *   val smsSender = BirthdaySmsSender(contentResolver)
 *   val sentCount = smsSender.sendToMembers(
 *       members    = memberListAdapter.getCurrentItems(),
 *       template   = binding.boodskap.text.toString(),
 *       smsManager = getSystemService(SmsManager::class.java),
 *       shouldSend = { member -> member.tag == 1 || autoSms }
 *   )
 */
class BirthdaySmsSender(private val contentResolver: ContentResolver) {

    private val tag = "BirthdaySmsSender"

    /**
     * Iterates [members], personalises [template] for each one, and sends
     * via [smsManager] for every member where [shouldSend] returns true.
     *
     * A 1-second delay is inserted between sends to avoid carrier throttling.
     *
     * @return The number of messages successfully sent.
     */
    suspend fun sendToMembers(
        members: List<MemberItem>,
        template: String,
        smsManager: SmsManager,
        shouldSend: (MemberItem) -> Boolean
    ): Int = withContext(Dispatchers.IO) {
        var sentCount = 0
        for (member in members) {
            if (!shouldSend(member)) continue
            val success = sendToMember(member, template, smsManager)
            if (success) sentCount++
            delay(1000)
        }
        sentCount
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun sendToMember(
        member: MemberItem,
        template: String,
        smsManager: SmsManager
    ): Boolean = withContext(Dispatchers.IO) {
        val phone = fixphonenumber(member.cellphone)
        if (phone.isNullOrEmpty()) return@withContext false

        val personalized = MessageComposer.personalize(template, member)
        return@withContext try {
            val parts = smsManager.divideMessage(personalized)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            logSent(phone, personalized)
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(tag, "SMS failed for ${member.name}: ${e.message}")
            false
        }
    }

    /**
     * Writes the sent message to the device's SMS Sent folder so it appears
     * in the native messaging app. Silently ignores failures — logging to
     * the Sent folder is best-effort.
     *
     * Was [VerjaarSmsActivity.logSentMessage].
     */
    private fun logSent(phone: String, message: String) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, phone)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.BODY, message)
            }
            contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(tag, "Failed to log SMS to Sent folder", e)
        }
    }
}