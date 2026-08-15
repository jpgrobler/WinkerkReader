package za.co.jpsoft.winkerkreader.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.services.voip.VoipCallInfoExtractor
import za.co.jpsoft.winkerkreader.services.voip.VoipCallStateDetector
import za.co.jpsoft.winkerkreader.services.voip.VoipCallTracker
import za.co.jpsoft.winkerkreader.services.voip.VoipNotificationHandler
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import za.co.jpsoft.winkerkreader.utils.telephony.CallNotificationDiagnostics
import za.co.jpsoft.winkerkreader.utils.telephony.UnifiedCallMonitor
import za.co.jpsoft.winkerkreader.utils.telephony.VoipDiagnosticHelper
import za.co.jpsoft.winkerkreader.utils.work.ForegroundServiceHelper
import za.co.jpsoft.winkerkreader.utils.work.ForegroundServiceType
import java.util.concurrent.TimeUnit
import android.content.pm.ServiceInfo
@AndroidEntryPoint
class WhatsAppNotificationService : NotificationListenerService() {

    @Inject
    lateinit var callMonitorPrefs: CallMonitorPrefs
    @Inject
    lateinit var unifiedMonitor: UnifiedCallMonitor

    private lateinit var notificationHandler: VoipNotificationHandler
    private lateinit var callTracker: VoipCallTracker
    @Inject
    lateinit var diagnostics: CallNotificationDiagnostics
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val pruneHandler = Handler(Looper.getMainLooper())
    private val pruneRunnable = object : Runnable {
        override fun run() {
            callTracker.pruneStaleCalls(System.currentTimeMillis() - VOIP_CALL_TTL_MS)
            pruneHandler.postDelayed(this, TimeUnit.MINUTES.toMillis(30))
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        if (BuildConfig.DEBUG) Log.d(TAG, "onCreate")

        initialize()
        createNotificationChannel()

        // 🛡️ FIX: Wrap startForeground to prevent ForegroundServiceStartNotAllowedException crash
        try {
            ForegroundServiceHelper.startForeground(
                service = this,
                id = NOTIFICATION_ID,
                notification = createForegroundNotification(),
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(
                    TAG,
                    "Failed to start foreground service from background, running un-foregrounded",
                    e
                )
            }
            // On Android 14+, if background start restrictions block this, catching
            // prevents a fatal crash. The system may eventually kill or restrict
            // the service, but it avoids an immediate app termination.
        }

        pruneHandler.post(pruneRunnable)
    }

    private fun initialize() {
        val appContext = applicationContext

        val voipPackages = mapOf(
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

        callTracker = VoipCallTracker(mainHandler)
        val stateDetector = VoipCallStateDetector()
        val infoExtractor = VoipCallInfoExtractor()

        notificationHandler = VoipNotificationHandler(
            context = appContext,
            callMonitorPrefs = callMonitorPrefs,   // <-- injected
            unifiedMonitor = unifiedMonitor,       // <-- injected
            stateDetector = stateDetector,
            infoExtractor = infoExtractor,
            callTracker = callTracker,
            scope = serviceScope,
            voipPackages = voipPackages,
            diagnostics = diagnostics
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (BuildConfig.DEBUG) Log.d(TAG, "onListenerConnected")

        if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            requestPermission()
        }

        serviceScope.launch {
            notificationHandler.reconcileStaleActiveCalls()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (BuildConfig.DEBUG) Log.w(TAG, "onListenerDisconnected — requesting rebind")
        requestRebind(ComponentName(applicationContext, WhatsAppNotificationService::class.java))
    }

    override fun onDestroy() {
        isServiceRunning = false
        serviceScope.cancel()
        callTracker.clear()
        pruneHandler.removeCallbacksAndMessages(null)

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "stopForeground in onDestroy failed: ${e.message}")
        }

        super.onDestroy()
        if (BuildConfig.DEBUG) Log.d(TAG, "onDestroy")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onNotificationPosted — package: ${sbn.packageName}, id: ${sbn.id}")
        }

        val appName = VOIP_PACKAGES[sbn.packageName] ?: return

        // Use injected pref
        if (!callMonitorPrefs.voipLogEnabled) return

        if (!VoipCallStateDetector().looksLikeCallNotification(sbn)) return

        if (BuildConfig.DEBUG) {
            VoipDiagnosticHelper.dumpNotificationToFile(applicationContext, sbn, appName)
        }

        notificationHandler.handleNotification(sbn, appName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onNotificationRemoved — package: ${sbn.packageName}, id: ${sbn.id}")
        }

        val appName = VOIP_PACKAGES[sbn.packageName] ?: return
        notificationHandler.handleRemoval(sbn, appName)
    }

    // ─── Foreground service boilerplate (unchanged) ──────────────────────

    private fun createNotificationChannel() {
        try {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "WhatsApp Listener Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Required to keep notification listener service running"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
            if (BuildConfig.DEBUG) Log.d(TAG, "Notification channel created")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error creating notification channel", e)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("WhatsApp Listener Active")
            .setContentText("Monitoring WhatsApp notifications")
            .setSmallIcon(R.drawable.img)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun requestPermission() {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error opening notification settings", e)
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .apply {
                        data = android.net.Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                startActivity(intent)
            } catch (e2: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error opening app settings", e2)
            }
        }
    }

    companion object {
        private const val TAG = "WhatsAppNotifService"
        private const val NOTIFICATION_CHANNEL_ID = "whatsapp_listener_channel"
        private const val NOTIFICATION_ID = 9999

        @Volatile
        private var isServiceRunning = false

        @JvmStatic
        fun isRunning(): Boolean = isServiceRunning

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

        private val VOIP_CALL_TTL_MS: Long = TimeUnit.HOURS.toMillis(1)
    }
}