package za.co.jpsoft.winkerkreader.utils.messaging

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import za.co.jpsoft.winkerkreader.BuildConfig
import java.net.URLEncoder

/**
 * Sends a WhatsApp message via one of three Intent strategies.
 *
 * Consolidates three sets of identical private methods that were copy-pasted across:
 *  - VerjaarSmsActivity  (with personalized message)
 *  - MemberUtils         (phone-only, no message)
 *  - MemberListInteractionController  (phone-only)
 *
 * MemberUtils.sendWhatsApp() now delegates here with message = "".
 * VerjaarSmsActivity uses it directly with a personalized message.
 *
 * Usage:
 *   // From VerjaarSmsActivity (with personalized message):
 *   WhatsAppMessageSender.send(activity, phone, method = 1, message = personalizedMsg)
 *
 *   // From MemberUtils (no message, existing callers unchanged):
 *   WhatsAppMessageSender.send(activity, phone, method = 1)
 */

object WhatsAppMessageSender {

    private const val TAG = "WhatsAppMessageSender"

    fun send(
        context: Context,          // ← was Activity
        phone: String,
        method: Int,
        message: String = ""
    ): Boolean {
        if (phone.isBlank()) return false
        return try {
            when (method) {
                1 -> method1(context, phone, message)
                2 -> method2(context, phone, message)
                3 -> method3(context, phone, message)
                else -> false
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "WhatsApp not available (method=$method)", e)
            false
        }
    }

    // Update private methods to accept Context
    private fun method1(context: Context, phone: String, message: String): Boolean {
        val uri = "smsto:$phone".toUri()
        Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("jid", phone)
            `package` = "com.whatsapp"
            if (message.isNotEmpty()) {
                putExtra("sms_body", message)
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(Intent.createChooser(this, ""))
        }
        return true
    }

    private fun method2(context: Context, phone: String, message: String): Boolean {
        val encoded = if (message.isNotEmpty())
            URLEncoder.encode(message, "UTF-8")
        else ""
        val url = buildString {
            append("https://api.whatsapp.com/send?phone=")
            append(phone)
            if (encoded.isNotEmpty()) append("&text=").append(encoded)
        }
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            `package` = "com.whatsapp"
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else false
    }

    private fun method3(context: Context, phone: String, message: String): Boolean {
        Intent(Intent.ACTION_SEND).apply {
            `package` = "com.whatsapp"
            type = "text/plain"
            if (message.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, message)
            putExtra("jid", "${phone}@s.whatsapp.net")
            context.startActivity(this)
        }
        return true
    }
}