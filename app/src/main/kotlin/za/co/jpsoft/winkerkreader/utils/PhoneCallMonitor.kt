package za.co.jpsoft.winkerkreader.utils

import za.co.jpsoft.winkerkreader.services.OproepDetailService

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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import za.co.jpsoft.winkerkreader.data.DatabaseHelper
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.models.CallType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*


class PhoneCallMonitor(
    private val context: Context,
    private val databaseHelper: DatabaseHelper,
    private val calendarManager: CalendarManager,
    private val calendarId: Long
) {

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null

    private var currentIncomingNumber: String? = null
    private var currentOutgoingNumber: String? = null
    private var callStartTime: Long = 0
    private var currentCallId: String? = null
    private var isCallActive = false
    private var currentCallType: CallType? = null
    private var pendingIncomingNumber: String? = null

    // Add unified monitor
    private var unifiedMonitor: UnifiedCallMonitor? = null

    init {
        unifiedMonitor = UnifiedCallMonitor(context, databaseHelper, calendarManager, calendarId)
    }

    fun getUnifiedMonitor(): UnifiedCallMonitor? = unifiedMonitor

    fun setIncomingNumber(number: String?) {
        pendingIncomingNumber = number
    }

    fun startMonitoring() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions for phone monitoring")
            return
        }

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

        callStartTime = System.currentTimeMillis()
        val callId = "phone_$callStartTime"
        currentCallId = callId

        unifiedMonitor?.onCallDetected(
            callId = callId,
            number = number,
            direction = "incoming",
            source = "Phone Call",
            timestamp = callStartTime
        )

        // Store for legacy handling
        currentIncomingNumber = if (!number.isNullOrBlank()) number else "Unknown Number"
        currentCallType = CallType.INCOMING

        val settings = context.getSharedPreferences(PREFS_USER_INFO, 0)
        settings.edit().putString("CallerNumber", number).apply()
        Log.d(TAG, "INCOMING call detected: $currentIncomingNumber")
        startCallerIdentificationService(context)
    }

    private fun handleOffHookState(phoneNumber: String?) {
        if (currentIncomingNumber != null) {
            Log.d(TAG, "Incoming call ANSWERED: $currentIncomingNumber")
            isCallActive = true
        } else {
            callStartTime = System.currentTimeMillis()
            val callId = "phone_$callStartTime"
            currentCallId = callId

            unifiedMonitor?.onCallDetected(
                callId = callId,
                number = phoneNumber,
                direction = "outgoing",
                source = "Phone Call",
                timestamp = callStartTime
            )

            currentOutgoingNumber = if (!phoneNumber.isNullOrBlank()) phoneNumber else "Unknown Number"
            currentCallType = CallType.OUTGOING
            isCallActive = true

            Log.d(TAG, "OUTGOING call detected: $currentOutgoingNumber")
        }
    }

    private fun handleIdleState() {
        val callId = currentCallId

        if (isCallActive && callId != null) {
            val callEndTime = System.currentTimeMillis()
            unifiedMonitor?.onCallEnded(callId, callEndTime)
        } else if (currentIncomingNumber != null && callId != null) {
            // Missed call
            unifiedMonitor?.onCallEnded(callId, System.currentTimeMillis())
        }

        if (OproepDetailService.isOn) {
            scheduleServiceStop(context)
        }
        resetCallState()
    }

    private fun resetCallState() {
        currentIncomingNumber = null
        currentOutgoingNumber = null
        callStartTime = 0
        currentCallId = null
        isCallActive = false
        currentCallType = null
    }

    private fun getContactName(phoneNumber: String?): String {
        if (phoneNumber.isNullOrEmpty()) return "Unknown Number"
        return phoneNumber
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
        // Check if service is already running to prevent duplicates
        if (OproepDetailService.isOn) {
            Log.d(TAG, "Caller identification service already running, skipping")
            return
        }
        // Also check via WeakReference if needed
        if (OproepDetailService.isServiceActive()) {
            Log.d(TAG, "Service instance still alive, skipping")
            return
        }

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
        const val ACTION_CALL_LOG_UPDATED = "za.co.jpsoft.winkerkreader.CALL_LOG_UPDATED"
    }
}