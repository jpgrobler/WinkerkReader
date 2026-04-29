package za.co.jpsoft.winkerkreader.utils

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import za.co.jpsoft.winkerkreader.WinkerkReader

class VoIPCallDetector : NotificationListenerService() {

    private var unifiedMonitor: UnifiedCallMonitor? = null

    private val voipApps = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.telegram" to "Telegram",
        "com.skype.raider" to "Skype",
        "com.discord" to "Discord",
        "com.signal" to "Signal",
        "com.viber.voip" to "Viber",
        "com.google.android.apps.messaging" to "Google Messages"
    )

    override fun onCreate() {
        super.onCreate()
        // Get from application class instead
        unifiedMonitor = WinkerkReader.getUnifiedCallMonitor()
        Log.d(TAG, "VoIPCallDetector created, unifiedMonitor=${unifiedMonitor != null}")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (unifiedMonitor == null) {
            Log.w(TAG, "UnifiedMonitor not available")
            return
        }

        sbn?.let { notification ->
            val packageName = notification.packageName
            val callingApp = voipApps[packageName]

            if (callingApp != null && isVoIPCallNotification(notification.notification)) {
                val callDetails = extractCallDetails(notification, callingApp)
                if (callDetails != null) {
                    unifiedMonitor?.onCallDetected(
                        callId = "${packageName}_${notification.postTime}",
                        number = callDetails.number,
                        direction = callDetails.direction,
                        source = callingApp,
                        timestamp = notification.postTime
                    )
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (unifiedMonitor == null) return

        sbn?.let { notification ->
            val packageName = notification.packageName
            if (voipApps.containsKey(packageName)) {
                unifiedMonitor?.onCallEnded(
                    callId = "${packageName}_${notification.postTime}",
                    endTime = System.currentTimeMillis()
                )
            }
        }
    }

    private fun isVoIPCallNotification(notification: Notification): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notification.category == Notification.CATEGORY_CALL ||
                    notification.extras?.getString(Notification.EXTRA_SUB_TEXT)?.contains("call", ignoreCase = true) == true ||
                    notification.extras?.getString(Notification.EXTRA_TEXT)?.contains("call", ignoreCase = true) == true
        } else {
            true
        }
    }

    private data class CallDetails(val number: String?, val direction: String?)

    private fun extractCallDetails(notification: StatusBarNotification, appName: String): CallDetails? {
        val extras = notification.notification.extras ?: return null

        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getString(Notification.EXTRA_TEXT)
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT)

        val direction = when {
            text?.contains("incoming", ignoreCase = true) == true ||
                    text?.contains("calling", ignoreCase = true) == true ||
                    subText?.contains("incoming", ignoreCase = true) == true -> "incoming"

            text?.contains("outgoing", ignoreCase = true) == true ||
                    text?.contains("dialing", ignoreCase = true) == true -> "outgoing"

            text?.contains("missed", ignoreCase = true) == true -> "missed"

            else -> null
        }

        val number = extractPhoneNumber(title, text, subText)

        if (direction == null && number != null) {
            Log.d(TAG, "VoIP call from $appName with unknown direction, will be logged as UNKNOWN")
        }

        return CallDetails(number, direction)
    }

    private fun extractPhoneNumber(vararg texts: String?): String? {
        val phoneRegex = Regex("""[\+]?[(]?[0-9]{1,4}[)]?[-\s\.]?[(]?[0-9]{1,4}[)]?[-\s\.]?[0-9]{1,5}[-\s\.]?[0-9]{1,5}[-\s\.]?[0-9]{1,5}""")

        for (text in texts) {
            text?.let {
                val match = phoneRegex.find(it)
                if (match != null) {
                    return match.value
                }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "VoIPCallDetector"
    }
}