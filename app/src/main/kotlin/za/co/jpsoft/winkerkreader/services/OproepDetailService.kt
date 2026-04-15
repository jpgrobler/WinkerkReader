package za.co.jpsoft.winkerkreader.services

import za.co.jpsoft.winkerkreader.utils.OproepUtils
import za.co.jpsoft.winkerkreader.WinkerkReader

// OproepDetailService.kt


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.database.Cursor
import android.database.SQLException
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
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
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO

class OproepDetailService : Service() {

    companion object {
        @Volatile
        var isOn = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var name = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createForegroundNotification()
        val settings = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)
        val incomingNumber = settings.getString("CallerNumber", "") ?: ""
        isOn = true
        if (isValidPhoneNumber(incomingNumber)) {
            processIncomingCall(incomingNumber, settings)
        } else {
            stopSelf()
        }
    }

    private fun createForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(2, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(2, notification)
            }
        }
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return phoneNumber.isNotEmpty() && phoneNumber != "XXXXXXXXXX" && phoneNumber != "Unknown"
    }

    private fun processIncomingCall(incomingNumber: String, settings: SharedPreferences) {
        var cursor: Cursor? = null
        try {
            cursor = searchForCaller(incomingNumber)
            val callerInfo = if (cursor != null && cursor.count > 0) {
                buildCallerInfo(cursor, incomingNumber)
            } else {
                incomingNumber
            }
            createFloatingView(callerInfo, settings)
        } catch (e: SQLException) {
            Log.e("WinkerkReader", "Error querying caller info: ${e.message}")
            createFloatingView(incomingNumber, settings)
        } finally {
            cursor?.close()
        }
    }

    private fun searchForCaller(phoneNumber: String): Cursor? {
        val projection = arrayOf("")
        val numberLength = phoneNumber.length
        val searchStart = maxOf(0, numberLength - 9)
        val searchNumber = phoneNumber.substring(searchStart, numberLength)
        val queryUri = ContentUris.withAppendedId(winkerkEntry.CONTENT_FOON_URI, 0)
        val selection = buildSearchQuery(searchNumber)
        Log.d("WinkerkReader", "Search query: $selection")
        return contentResolver.query(queryUri, projection, selection, null, null)
    }

    private fun buildSearchQuery(searchNumber: String): String {
        val baseQuery = winkerkEntry.SELECTION_LIDMAAT_INFO + " FROM " + WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM + " WHERE "
        val phoneConditions = String.format(
            "(REPLACE(%s,' ','') LIKE '%%%s') OR (REPLACE(%s,' ','') LIKE '%%%s') OR (REPLACE(%s,' ','') LIKE '%%%s')",
            "[${winkerkEntry.LIDMATE_SELFOON}]", searchNumber,
            "[${winkerkEntry.LIDMATE_LANDLYN}]", searchNumber,
            "[${winkerkEntry.LIDMATE_WERKFOON}]", searchNumber
        )
        return " $baseQuery$phoneConditions;"
    }

    private fun buildCallerInfo(cursor: Cursor, phoneNumber: String): String {
        val output = StringBuilder(phoneNumber).append("\r\n")
        val nameIndex = cursor.getColumnIndex(winkerkEntry.LIDMATE_NOEMNAAM)
        val surnameIndex = cursor.getColumnIndex(winkerkEntry.LIDMATE_VAN)
        cursor.moveToPosition(-1)
        while (cursor.moveToNext()) {
            if (!cursor.isNull(nameIndex) && !cursor.isNull(surnameIndex)) {
                val name = cursor.getString(nameIndex)
                val surname = cursor.getString(surnameIndex)
                output.append(name).append("\t ").append(surname).append("\r\n")
            }
        }
        return output.toString()
    }

    private fun createFloatingView(callerInfo: String, prefs: SharedPreferences) {
        floatingView = LayoutInflater.from(this).inflate(R.layout.oproepfloat, null)
        val callerTextView = floatingView.findViewById<TextView>(R.id.oproepnommer)
        callerTextView.text = callerInfo

        val utils = OproepUtils(prefs, this)
        utils.copyNewCallsToCalendar(callerInfo)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            stopSelf()
            return
        }
        setupFloatingWindow()
        setupClickListeners(callerTextView)
        setupTouchListener()
    }

    private fun setupFloatingWindow() {
        val params = createWindowLayoutParams().apply {
            gravity = Gravity.CENTER or Gravity.START
            x = 0
            y = 100
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)
    }

    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun setupClickListeners(callerTextView: TextView) {
        floatingView.findViewById<ImageView>(R.id.close_btn).setOnClickListener {
            stopSelf()
        }
        callerTextView.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("caller_info", callerTextView.text)
                clipboard.setPrimaryClip(clip)
            }
        }
    }

    private fun setupTouchListener() {
        val params = floatingView.layoutParams as WindowManager.LayoutParams
        floatingView.findViewById<View>(R.id.oproepfloaterbase).setOnTouchListener(
            FloatingViewTouchListener(params, windowManager, floatingView)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        val settings = getSharedPreferences(PREFS_USER_INFO, 0)
        settings.edit().putString("CallerNumber", "XXXXXXXXXX").apply()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    private inner class FloatingViewTouchListener(
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