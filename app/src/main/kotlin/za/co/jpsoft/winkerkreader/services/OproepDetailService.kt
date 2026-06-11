package za.co.jpsoft.winkerkreader.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.util.Log
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
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.utils.CallerInfoResolver
import java.lang.ref.WeakReference

class OproepDetailService : Service() {

    companion object {
        @Volatile
        var isOn = false
            private set
        private const val TAG = "OproepDetailService"
        const val EXTRA_CALLER_ID = "caller_id"
        const val EXTRA_CALLER_DISPLAY = "caller_display"
        private var serviceInstance: WeakReference<OproepDetailService>? = null

        //fun isServiceActive(): Boolean = serviceInstance?.get() != null

        private var lastProcessedNumber = ""
        private var lastProcessedTime = 0L

        fun canProcessCall(number: String): Boolean {
            synchronized(this) {
                val now = System.currentTimeMillis()
                return if (lastProcessedNumber == number && now - lastProcessedTime < 500) {
                    Log.d(TAG, "Duplicate call number detected, skipping: $number")
                    false
                } else {
                    lastProcessedNumber = number
                    lastProcessedTime = now
                    true
                }
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        serviceInstance = WeakReference(this)
        isOn = true
        createForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callerId = intent?.getStringExtra(EXTRA_CALLER_ID) ?: ""

        if (!isValidPhoneNumber(callerId)) {
            Log.d(TAG, "No valid caller id, ignoring start command")
            return START_NOT_STICKY
        }

        if (!canProcessCall(callerId)) {
            return START_NOT_STICKY
        }

        val displayName = intent?.getStringExtra(EXTRA_CALLER_DISPLAY)
        showCaller(callerId, displayName)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isOn = false
        serviceInstance = null

        val settings = getSharedPreferences(PREFS_USER_INFO, 0)
        settings.edit { putString("CallerNumber", "XXXXXXXXXX") }

        if (::floatingView.isInitialized) {
            try {
                if (::windowManager.isInitialized) {
                    windowManager.removeView(floatingView)
                }
            } catch (e: Exception) {
                Log.e("OproepDetailService", "Error removing floating view", e)
            }
        }
    }

    private fun createForegroundNotification() {
        val channelId = "WinkerkReader"
        val channelName = "Oproep Service"
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW).apply {
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

        startForeground(2, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return phoneNumber.isNotEmpty() && phoneNumber != "XXXXXXXXXX" && phoneNumber != "Unknown"
    }

    private fun showCaller(callerId: String, displayName: String?) {
        getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE).edit {
            putString("CallerNumber", callerId)
        }

        val callerInfo = displayName?.takeIf { it.isNotBlank() }
            ?: CallerInfoResolver.getCallerDisplayInfo(contentResolver, callerId)

        if (::floatingView.isInitialized) {
            floatingView.findViewById<TextView>(R.id.oproepnommer)?.text = callerInfo
            Log.d(TAG, "Updated overlay for caller: $callerInfo")
            return
        }

        floatingView = LayoutInflater.from(this).inflate(R.layout.oproepfloat, null)
        val callerTextView = floatingView.findViewById<TextView>(R.id.oproepnommer)
        callerTextView.text = callerInfo

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
            startActivity(intent)
            stopSelf()
            return
        }
        createFloatingWindow()
        setupClickListeners(callerTextView)
        setupTouchListener()
    }

    private fun createFloatingWindow() {
        val params = createWindowLayoutParams().apply {
            gravity = Gravity.CENTER or Gravity.START
            x = 0
            y = 100
        }
        windowManager = (getSystemService(WINDOW_SERVICE) as WindowManager)
        
        try {
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view", e)
            stopSelf()
        }
    }

    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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
                    v.performClick()
                    return true
                }
                MotionEvent.ACTION_UP -> {
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
