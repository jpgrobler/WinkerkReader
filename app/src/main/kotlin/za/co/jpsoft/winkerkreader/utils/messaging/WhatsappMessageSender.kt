package za.co.jpsoft.winkerkreader.utils.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import za.co.jpsoft.winkerkreader.BuildConfig
import java.net.URLEncoder

object WhatsAppMessageSender {

    private const val TAG = "WhatsAppMessageSender"

    fun send(
        context: Context,
        phone: String,
        method: Int = 1,
        message: String = ""
    ): Boolean {
        if (phone.isBlank()) return false
        val cleanPhone = phone.replace(Regex("[^0-9+]"), "")

        // 1. Try primary method: modern universal link (wa.me)
        try {
            val encodedMessage = if (message.isNotBlank()) {
                URLEncoder.encode(message, "UTF-8")
            } else {
                ""
            }

            val url = if (encodedMessage.isNotEmpty()) {
                "https://wa.me/$cleanPhone?text=$encodedMessage"
            } else {
                "https://wa.me/$cleanPhone"
            }

            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(
                TAG,
                "wa.me intent failed, attempting fallback to direct JID",
                e
            )
        }

        // 2. Fallback method (Equivalent to old Method 3: direct package send via JID)
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                `package` = "com.whatsapp"
                if (message.isNotEmpty()) {
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            true
        } catch (e2: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "All WhatsApp sending methods failed", e2)
            false
        }
    }
}