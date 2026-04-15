package za.co.jpsoft.winkerkreader.utils

import za.co.jpsoft.winkerkreader.services.OproepDetailService

// PhoneCallMonitor.kt


import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import za.co.jpsoft.winkerkreader.data.DatabaseHelper
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.models.CallType
import java.text.SimpleDateFormat
import java.util.*

class PhoneCallMonitor(
    private val context: Context,
    private val databaseHelper: DatabaseHelper,
    private val calendarManager: CalendarManager,
    private val calendarId: Long
) {

    interface CalendarIdProvider {
        fun getSelectedCalendarId(): Long
    }

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null

    private var currentIncomingNumber: String? = null
    private var currentOutgoingNumber: String? = null
    private var callStartTime: Long = 0
    private var isCallActive = false
    private var currentCallType: CallType? = null
    private var pendingIncomingNumber: String? = null

    fun setIncomingNumber(number: String?) {
        pendingIncomingNumber = number
    }

    fun startMonitoring() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions for phone monitoring")
            return
        }

        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                Log.d(TAG, "Call state changed: ${getCallStateName(state)}, Number: $phoneNumber")
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> handleRingingState(phoneNumber)
                    TelephonyManager.CALL_STATE_OFFHOOK -> handleOffHookState(phoneNumber)
                    TelephonyManager.CALL_STATE_IDLE -> handleIdleState()
                }
            }
        }
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        Log.d(TAG, "Phone call monitoring started")
    }

    fun stopMonitoring() {
        try {
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            phoneStateListener = null
            telephonyManager = null
            Log.d(TAG, "Phone call monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping phone call monitoring", e)
        }
        if (OproepDetailService.isOn) {
            scheduleServiceStop(context)
        }
    }

    private fun handleRingingState(phoneNumber: String?) {
        val number = pendingIncomingNumber ?: phoneNumber
        pendingIncomingNumber = null

        currentIncomingNumber = if (!number.isNullOrBlank()) number else "Unknown Number"
        currentCallType = CallType.INCOMING
        callStartTime = System.currentTimeMillis()

        val settings = context.getSharedPreferences(PREFS_USER_INFO, 0)
        settings.edit().putString("CallerNumber", phoneNumber).apply()
        Log.d(TAG, "INCOMING call detected: $currentIncomingNumber")
        startCallerIdentificationService(context)
        val contactName = getContactName(currentIncomingNumber)
        logPhoneCall(contactName, CallType.INCOMING, callStartTime, 0)
    }

    private fun handleOffHookState(phoneNumber: String?) {
        if (currentIncomingNumber != null) {
            Log.d(TAG, "Incoming call ANSWERED: $currentIncomingNumber")
            isCallActive = true
        } else {
            currentOutgoingNumber = if (!phoneNumber.isNullOrBlank()) phoneNumber else "Unknown Number"
            currentCallType = CallType.OUTGOING
            callStartTime = System.currentTimeMillis()
            isCallActive = true
            Log.d(TAG, "OUTGOING call detected: $currentOutgoingNumber")
            val contactName = getContactName(currentOutgoingNumber)
            logPhoneCall(contactName, CallType.OUTGOING, callStartTime, 0)
        }
    }

    private fun handleIdleState() {
        if (isCallActive) {
            val callEndTime = System.currentTimeMillis()
            val duration = (callEndTime - callStartTime) / 1000
            val number = currentIncomingNumber ?: currentOutgoingNumber
            val contactName = getContactName(number)
            Log.d(TAG, "Call ENDED: $contactName, Duration: ${duration}s, Type: $currentCallType")
            updateCallLogWithDuration(contactName, callStartTime, currentCallType, duration)
        } else if (currentIncomingNumber != null) {
            Log.d(TAG, "Call MISSED: $currentIncomingNumber")
            val contactName = getContactName(currentIncomingNumber)
            logPhoneCall(contactName, CallType.MISSED, callStartTime, 0)
        }
        if (OproepDetailService.isOn) {
            scheduleServiceStop(context)
        }
        resetCallState()
        checkRecentCallLogs()
    }

    private fun resetCallState() {
        currentIncomingNumber = null
        currentOutgoingNumber = null
        callStartTime = 0
        isCallActive = false
        currentCallType = null
    }

    private fun getContactName(phoneNumber: String?): String {
        if (phoneNumber.isNullOrEmpty()) return "Unknown Number"
        val contactName = getContactNameFromCallLog(phoneNumber)
        return if (contactName != null && contactName != phoneNumber) contactName else phoneNumber
    }

    private fun getContactNameFromCallLog(phoneNumber: String): String? {
        try {
            val projection = arrayOf(CallLog.Calls.CACHED_NAME)
            val selection = "${CallLog.Calls.NUMBER} = ?"
            val selectionArgs = arrayOf(phoneNumber)
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val cachedName = CursorDataExtractor.getSafeString(cursor, CallLog.Calls.CACHED_NAME, "")
                    if (!cachedName.isNullOrBlank()) {
                        return cachedName
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact name from call log", e)
        }
        return phoneNumber
    }

    private fun checkRecentCallLogs() {
        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_NAME
            )
            val selection = "${CallLog.Calls.DATE} > ?"
            val selectionArgs = arrayOf((System.currentTimeMillis() - 60000).toString())
            val sortOrder = "${CallLog.Calls.DATE} DESC"
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            cursor?.use {
                var count = 0
                val maxResults = 3
                while (it.moveToNext() && count < maxResults) {
                    val number = CursorDataExtractor.getSafeString(it, CallLog.Calls.NUMBER, "")
                    val type = CursorDataExtractor.getSafeInt(it, CallLog.Calls.TYPE, -1)
                    val date = CursorDataExtractor.getSafeLong(it, CallLog.Calls.DATE, 0L)
                    val duration = CursorDataExtractor.getSafeLong(it, CallLog.Calls.DURATION, 0L)
                    val cachedName = CursorDataExtractor.getSafeString(it, CallLog.Calls.CACHED_NAME, null)

                    val callType = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CallType.MISSED
                        else -> null
                    }
                    if (callType == null) continue

                    val displayName = if (!cachedName.isNullOrBlank()) cachedName else if (!number.isNullOrBlank()) number else "Unknown"

                    if (!isDuplicateCall(displayName, date, callType)) {
                        logPhoneCall(displayName, callType, date, duration)
                    }
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking recent call logs", e)
        }
    }

    private fun logPhoneCall(contactInfo: String, callType: CallType, timestamp: Long, duration: Long) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateTime = dateFormat.format(Date(timestamp))
        Log.d(TAG, "Logging phone call - Type: $callType, Contact: $contactInfo, Time: $dateTime, Duration: ${duration}s")

        if (isDuplicateCall(contactInfo, timestamp, callType)) {
            Log.d(TAG, "Duplicate call detected, skipping")
            return
        }

        val success = databaseHelper.insertCallLogWithType(contactInfo, timestamp, callType, "Phone Call", duration)
        if (success) {
            Log.d(TAG, "Phone call logged to database successfully")
            logToCalendar(contactInfo, timestamp, callType, duration)
            broadcastCallLogUpdate()
        } else {
            Log.e(TAG, "Failed to log phone call to database")
        }
    }

    private fun updateCallLogWithDuration(contactInfo: String, timestamp: Long, callType: CallType?, duration: Long) {
        logPhoneCall(contactInfo, callType ?: CallType.INCOMING, timestamp, duration)
    }

    private fun logToCalendar(contactInfo: String, timestamp: Long, callType: CallType, duration: Long) {
        if (calendarId != -1L) {
            val success = calendarManager.addCallEventToCalendar(
                calendarId, contactInfo, timestamp, callType, "Phone Call", duration
            )
            if (success) {
                Log.d(TAG, "Phone call logged to calendar successfully")
            } else {
                Log.e(TAG, "Failed to log phone call to calendar")
            }
        }
    }

    private fun isDuplicateCall(contactInfo: String, timestamp: Long, callType: CallType): Boolean {
        try {
            val recentCalls = databaseHelper.getRecentCallLogs(30000) // 30 seconds
            for (call in recentCalls) {
                if (call.callerInfo == contactInfo &&
                    Math.abs(call.timestamp - timestamp) < 30000 &&
                    call.callType == callType.name &&
                    call.source == "Phone Call"
                ) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for duplicate calls", e)
        }
        return false
    }

    private fun broadcastCallLogUpdate() {
        val intent = Intent("za.co.jpsoft.winkerkreader.CALL_LOG_UPDATED")
        context.sendBroadcast(intent)
    }

    private fun hasRequiredPermissions(): Boolean {
        val requiredPermissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getCallStateName(state: Int): String = when (state) {
        TelephonyManager.CALL_STATE_IDLE -> "IDLE"
        TelephonyManager.CALL_STATE_RINGING -> "RINGING"
        TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
        else -> "UNKNOWN"
    }

    private fun startCallerIdentificationService(context: Context) {
        val serviceIntent = Intent(context, OproepDetailService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Caller identification service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start caller identification service: ${e.message}")
        }
    }

    private fun scheduleServiceStop(context: Context) {
        Handler(Looper.getMainLooper()).postDelayed({
            stopCallerIdentificationService(context)
        }, CALL_END_DELAY_MS)
    }

    private fun stopCallerIdentificationService(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
        prefs.edit().putString("CallerNumber", PLACEHOLDER_NUMBER).apply()
        val serviceIntent = Intent(context, OproepDetailService::class.java)
        try {
            context.stopService(serviceIntent)
            Log.d(TAG, "Caller identification service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop caller identification service: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PhoneCallMonitor"
        private const val PLACEHOLDER_NUMBER = "XXXXXXXXXX"
        private const val CALL_END_DELAY_MS = 2000L
    }
}