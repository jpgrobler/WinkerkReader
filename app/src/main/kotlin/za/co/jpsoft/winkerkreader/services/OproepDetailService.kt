package za.co.jpsoft.winkerkreader.services

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.utils.telephony.CallerInfoResolver
import za.co.jpsoft.winkerkreader.utils.telephony.CallerInfoResult
import za.co.jpsoft.winkerkreader.utils.work.ForegroundServiceHelper
import za.co.jpsoft.winkerkreader.utils.work.ForegroundServiceType
import java.lang.ref.WeakReference

class OproepDetailService : Service() {

    companion object {
        private const val TAG = "OproepDetailService"
        const val EXTRA_CALLER_ID = "caller_id"
        const val EXTRA_DISPLAY_NAME = "display_name"

        // Action sent when the call transitions to OFFHOOK (answered)
        const val ACTION_CALL_ANSWERED = "za.co.jpsoft.winkerkreader.ACTION_CALL_ANSWERED"

        private var serviceInstance: WeakReference<OproepDetailService>? = null

        @Volatile
        var isOn = false
            private set

        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return false
            val serviceName = "${context.packageName}.${OproepDetailService::class.java.simpleName}"
            return try {
                manager.getRunningServices(Int.MAX_VALUE)
                    .any { it.service.className == serviceName }
            } catch (e: Exception) {
                false
            }
        }

        private var lastProcessedNumber = ""
        private var lastProcessedTime = 0L

        //        fun canProcessCall(number: String): Boolean {
//            synchronized(this) {
//                val now = System.currentTimeMillis()
//                return if (lastProcessedNumber == number && now - lastProcessedTime < 500) {
//                    false
//                } else {
//                    lastProcessedNumber = number
//                    lastProcessedTime = now
//                    true
//                }
//            }
//        }
        fun canProcessCall(number: String): Boolean {
            synchronized(this) {
                val now = System.currentTimeMillis()
                // Reset or prune if stale (> 30 seconds old) to prevent memory accumulation or stuck states
                if (now - lastProcessedTime > 30_000L) {
                    lastProcessedNumber = ""
                }

                return if (lastProcessedNumber == number && now - lastProcessedTime < 500) {
                    false
                } else {
                    lastProcessedNumber = number
                    lastProcessedTime = now
                    true
                }
            }
        }

        // Add a explicit reset utility if needed
        fun resetTrackers() {
            synchronized(this) {
                lastProcessedNumber = ""
                lastProcessedTime = 0L
            }
        }

        // Called from PhoneCallMonitor on OFFHOOK — dismisses overlay immediately
        fun dismissOnAnswered() {
            serviceInstance?.get()?.stopSelf()
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var viewAdded = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        serviceInstance = WeakReference(this)
        isOn = true
        createForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Dismiss immediately when the call is answered
        if (intent?.action == ACTION_CALL_ANSWERED) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Call answered — dismissing overlay")
            stopSelf()
            return START_NOT_STICKY
        }

        val callerId = intent?.getStringExtra(EXTRA_CALLER_ID) ?: ""
        val displayNameExtra = intent?.getStringExtra(EXTRA_DISPLAY_NAME) ?: ""

        // Allow the service to start if we have either a valid phone number OR a display name
        if (!isValidPhoneNumber(callerId) && displayNameExtra.isEmpty()) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "No valid caller id or display name, ignoring start command"
            )
            return START_NOT_STICKY
        }

        // Only enforce the duplicate check if we have a phone number
        if (callerId.isNotEmpty() && !canProcessCall(callerId)) {
            return START_NOT_STICKY
        }

        serviceScope.launch {
            val lookupKey = if (callerId.isNotEmpty() && callerId != "Unknown" && callerId != "XXXXXXXXXX") {
                callerId
            } else {
                displayNameExtra
            }

            // ✅ Use service's context explicitly
            val context = this@OproepDetailService
            val result = if (lookupKey.isNotEmpty()) {
                CallerInfoResolver.resolve(lookupKey, context)   // ← fixed
            } else {
                CallerInfoResult.Unknown
            }

            val finalName = when (result) {
                is CallerInfoResult.Member -> {
                    buildString {
                        append(result.name)
                        val details = mutableListOf<String>()
                        if (!result.gemeente.isNullOrBlank()) details.add(result.gemeente)
                        if (details.isNotEmpty()) {
                            append(" (")
                            append(details.joinToString(", "))
                            append(")")
                        }
                    }
                }
                is CallerInfoResult.Contact -> result.name
                is CallerInfoResult.MultipleMembers -> {
                    result.members.joinToString("\n") { member ->
                        buildString {
                            append(member.name)
                            if (!member.gemeente.isNullOrBlank()) {
                                append(" (${member.gemeente})")
                            }
                        }
                    }
                }
                CallerInfoResult.Unknown -> {
                    // If we have an explicit name from the notification, use it
                    if (displayNameExtra.isNotEmpty()) displayNameExtra
                    // FALLBACK: use the raw number if available
                    else if (callerId.isNotEmpty() && callerId != "Unknown" && callerId != "XXXXXXXXXX") callerId
                    else null
                }
            }

            if (finalName == null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "No name to display")
                withContext(Dispatchers.Main) { stopSelf() }
                return@launch
            }

            withContext(Dispatchers.Main) {
                showCaller(callerId, finalName)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        isOn = false
        serviceInstance = null
        resetTrackers()

        if (::floatingView.isInitialized && viewAdded) {
            try {
                windowManager.removeView(floatingView)
                viewAdded = false
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error removing floating view", e)
            }
        }

        getSharedPreferences(PREFS_USER_INFO, 0).edit { putString("CallerNumber", "XXXXXXXXXX") }
        super.onDestroy()
    }

    private fun createForegroundNotification() {
        val channelId = "WinkerkReader"
        val channelName = "Oproep Service"
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            lightColor = Color.BLUE
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setOngoing(true)
            .setSmallIcon(R.drawable.img)
            .setContentTitle("Caller ID Service")
            .setContentText("Monitoring incoming calls")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build()

        ForegroundServiceHelper.startForeground(
            service = this,
            id = 2,
            notification = notification,
            type = ForegroundServiceType.DATA_SYNC
        )
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return phoneNumber.isNotEmpty() && phoneNumber != "XXXXXXXXXX" && phoneNumber != "Unknown"
    }

    private fun showCaller(callerId: String, displayName: String) {
        getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE).edit {
            putString("CallerNumber", callerId)
        }

        if (::floatingView.isInitialized && viewAdded) {
            floatingView.findViewById<TextView>(R.id.oproepnommer)?.text = displayName
            return
        }

        val themedContext = ContextThemeWrapper(this, R.style.Theme_WinkerkReader)
        floatingView = LayoutInflater.from(themedContext).inflate(R.layout.oproepfloat, null)
        val callerTextView = floatingView.findViewById<TextView>(R.id.oproepnommer)
        callerTextView.text = displayName

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
            stopSelf()
            return
        }

        createFloatingWindow()
        setupClickListeners(callerTextView)
        setupTouchListener()
        viewAdded = true
    }

    private fun createFloatingWindow() {
        // Place in the upper-middle of the screen so we clear both:
        // - top heads-up incoming-call controls (answer/decline)
        // - bottom full-screen dialer answer/decline buttons
        val screenHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val metrics = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            metrics.heightPixels
        }

        val params = createWindowLayoutParams().apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (screenHeight * 0.32f).toInt()
        }
        try {
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to add floating view", e)
            stopSelf()
        }
    }

    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        // NOT_FOCUSABLE + NOT_TOUCH_MODAL: answer/decline stay usable.
        // Touches on the popup still work; touches outside pass through to the dialer.
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun setupClickListeners(callerTextView: TextView) {
        floatingView.findViewById<ImageView>(R.id.close_btn)?.setOnClickListener {
            stopSelf()
        }
        callerTextView.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("caller_info", callerTextView.text))
        }
    }

    private fun setupTouchListener() {
        val params = floatingView.layoutParams as? WindowManager.LayoutParams ?: return
        floatingView.findViewById<View>(R.id.oproepfloaterbase)?.setOnTouchListener(
            FloatingViewTouchListener(params, windowManager, floatingView)
        )
    }

    private class FloatingViewTouchListener(
        private val params: WindowManager.LayoutParams,
        private val windowManager: WindowManager,
        private val floatingView: View
    ) : View.OnTouchListener {

        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    return true
                }
            }
            return false
        }
    }
}