package za.co.jpsoft.winkerkreader.utils.telephony

import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic helper for VoIP notifications – dumps raw notification data to a file
 * for debugging and improving the call-state matcher.
 *
 * Extracted from WhatsAppNotificationService.dumpNotificationToFile().
 */
object VoipDiagnosticHelper {

    private const val TAG = "VoipDiagnosticHelper"

    /**
     * Appends a full dump of [sbn] to the diagnostic file in the app's cache directory.
     * Does nothing if the file cannot be written.
     *
     * @param context  Application or activity context (used for cache dir)
     * @param sbn      The notification to dump
     * @param appName  Display name of the VoIP app (e.g., "WhatsApp")
     */
    fun dumpNotificationToFile(context: Context, sbn: StatusBarNotification, appName: String) {
        if (!BuildConfig.DEBUG) return  // Only run in debug builds; ensures no accidental leakage

        try {
            val file = File(context.cacheDir, "voip_notification_dump.txt")
            val extras = sbn.notification.extras
            val writer = FileWriter(file, true)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

            writer.append("=== ${sdf.format(Date())} ===\n")
            writer.append("Package: ${sbn.packageName}\n")
            writer.append("AppName: $appName\n")
            writer.append("Key: ${sbn.key}\n")
            writer.append("Id: ${sbn.id}\n")
            writer.append("Category: ${sbn.notification.category}\n")
            writer.append("Flags: ${sbn.notification.flags}\n")
            if (extras != null) {
                for (key in extras.keySet()) {
                    val value = extras.get(key)
                    writer.append("  $key = $value\n")
                }
            } else {
                writer.append("Extras: null\n")
            }
            writer.append("---\n")
            writer.close()

            if (BuildConfig.DEBUG) Log.d(TAG, "Notification dumped to ${file.absolutePath}")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to dump notification to file", e)
        }
    }
}