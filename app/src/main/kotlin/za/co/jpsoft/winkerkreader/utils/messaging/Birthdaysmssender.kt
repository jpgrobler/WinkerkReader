package za.co.jpsoft.winkerkreader.utils.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import za.co.jpsoft.winkerkreader.data.members.models.MemberItem
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber

/**
 * Builds SMS-compose intents for birthday/event greetings, handed to the
 * device's default SMS app. No SEND_SMS/READ_SMS/WRITE_SMS permission
 * required — the OS owns the actual send.
 */
class BirthdaySmsSender {

    /**
     * Returns members with a valid phone number, paired with the
     * personalised message, ready to hand off to [sendViaIntent] one at a
     * time (each opens the SMS app for the user to confirm/send).
     */
    fun buildQueue(
        members: List<MemberItem>,
        template: String,
        shouldSend: (MemberItem) -> Boolean
    ): List<Pair<MemberItem, String>> =
        members.filter(shouldSend)
            .mapNotNull { member ->
                val phone = fixphonenumber(member.cellphone) ?: return@mapNotNull null
                member to MessageComposer.personalize(template, member)
            }

    fun sendViaIntent(context: Context, phone: String, message: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phone")
            putExtra("sms_body", message)
        }
        context.startActivity(intent)
    }
}