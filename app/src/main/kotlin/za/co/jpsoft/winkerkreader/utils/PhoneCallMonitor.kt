package za.co.jpsoft.winkerkreader.utils

import za.co.jpsoft.winkerkreader.services.OproepDetailService

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import za.co.jpsoft.winkerkreader.data.DatabaseHelper
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.models.CallType


class PhoneCallMonitor(
    private val context: Context,
    databaseHelper: DatabaseHelper,
    calendarManager: CalendarManager,
    calendarId: Long
) {

    private var telephonyManager: TelephonyManager? = null
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: Any? = null // TelephonyCallback on API 31+

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
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        unifiedMonitor = UnifiedCallMonitor.getInstance(context, databaseHelper, calendarManager, calendarId)
    }



    fun setIncomingNumber(number: String?) {
        pendingIncomingNumber = number
    }

    fun startMonitoring() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions for phone monitoring")
            return
        }

        if (telephonyManager == null) {
            telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = CallStateCallback()
            telephonyCallback = callback
            telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
            Log.d(TAG, "TelephonyCallback registered (API 31+)")
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener = object : PhoneStateListener() {
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    super.onCallStateChanged(state, phoneNumber)
                    handleStateChanged(state, phoneNumber)
                }
            }
            @Suppress("DEPRECATION")
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            Log.d(TAG, "PhoneStateListener registered (Legacy)")
        }
    }

    private fun handleStateChanged(state: Int, phoneNumber: String?) {
        Log.d(TAG, "Call state changed: ${getCallStateName(state)}, Number: $phoneNumber")
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> handleRingingState(phoneNumber)
            TelephonyManager.CALL_STATE_OFFHOOK -> handleOffHookState(phoneNumber)
            TelephonyManager.CALL_STATE_IDLE -> handleIdleState()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class CallStateCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            handleStateChanged(state, null) // Number comes from BroadcastReceiver
        }
    }

    fun stopMonitoring() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let {
                    telephonyManager?.unregisterTelephonyCallback(it)
                }
                telephonyCallback = null
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
                phoneStateListener = null
            }
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
        // Priority order for number resolution:
        // 1. pendingIncomingNumber – set by IncomingCall receiver via CallMonitoringService intent
        // 2. phoneNumber – provided by legacy PhoneStateListener (API < 31)
        val number = pendingIncomingNumber ?: phoneNumber
        pendingIncomingNumber = null

        callStartTime = System.currentTimeMillis()
        val callId = "phone_$callStartTime"
        currentCallId = callId

        val displayInfo = CallerInfoResolver.getCallerDisplayInfo(context.contentResolver, number)
        unifiedMonitor?.onCallDetected(
            callId = callId,
            number = number,
            direction = "incoming",
            source = "Phone Call",
            timestamp = callStartTime,
            displayName = displayInfo
        )

        currentIncomingNumber = if (!number.isNullOrBlank()) number else "Unknown Number"
        currentCallType = CallType.INCOMING

        // DO NOT call syncRecentCallsToCalendar here – UnifiedMonitor will handle logging.

        val settings = context.getSharedPreferences(PREFS_USER_INFO, 0)
        settings.edit { putString("CallerNumber", number) }
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

            val displayInfo = CallerInfoResolver.getCallerDisplayInfo(context.contentResolver, phoneNumber)
            unifiedMonitor?.onCallDetected(
                callId = callId,
                number = phoneNumber,
                direction = "outgoing",
                source = "Phone Call",
                timestamp = callStartTime,
                displayName = displayInfo
            )

            currentOutgoingNumber = if (!phoneNumber.isNullOrBlank()) phoneNumber else "Unknown Number"
            currentCallType = CallType.OUTGOING
            isCallActive = true

            Log.d(TAG, "OUTGOING call detected: $currentOutgoingNumber")
        }
    }

    private fun handleIdleState() {
        val callId = currentCallId

        when {
            isCallActive && callId != null -> {
                // Call was answered and then ended
                val callEndTime = System.currentTimeMillis()
                unifiedMonitor?.onCallEnded(callId, callEndTime)
            }
            currentIncomingNumber != null && callId != null -> {
                // Phone rang but was never answered = missed call.
                // If the number is still unknown, try recovering it from the system CallLog
                // (the OS writes the missed-call entry within a few seconds of the call ending).
                if (currentIncomingNumber == "Unknown Number") {
                    val recovered = queryLatestCallFromLog(CallLog.Calls.MISSED_TYPE)
                    if (recovered != null) {
                        Log.d(TAG, "Recovered missed call number from CallLog: ${recovered.first}")
                        // Re-register with the recovered info so the monitor logs correctly
                        val displayInfo = CallerInfoResolver.getCallerDisplayInfo(
                            context.contentResolver, recovered.first
                        )
                        unifiedMonitor?.onCallDetected(
                            callId = callId,
                            number = recovered.first,
                            direction = "missed",
                            source = "Phone Call",
                            timestamp = recovered.second,
                            displayName = displayInfo
                        )
                    }
                }
                unifiedMonitor?.onCallEnded(callId, System.currentTimeMillis())
            }
        }

        if (OproepDetailService.isOn) {
            scheduleServiceStop(context)
        }
        resetCallState()
    }

    /**
     * Queries the system CallLog for the most recent entry of a given type that occurred
     * within the last 60 seconds. Returns a pair of (number, date) or null if not found.
     * Used as a fallback when the phone number was not delivered through normal channels.
     */
    private fun queryLatestCallFromLog(type: Int): Pair<String, Long>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val since = System.currentTimeMillis() - 60_000L // only look at the last 60 seconds
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE)
        val selection = "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?"
        val selectionArgs = arrayOf(type.toString(), since.toString())
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val number = cursor.getString(numIdx)?.takeIf { it.isNotBlank() }
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
                    if (number != null) Pair(number, date) else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query call log for fallback number", e)
            null
        }
    }

    private fun resetCallState() {
        currentIncomingNumber = null
        currentOutgoingNumber = null
        callStartTime = 0
        currentCallId = null
        isCallActive = false
        currentCallType = null
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
        val callerId = currentIncomingNumber?.takeIf { it != "Unknown Number" } ?: return

        val serviceIntent = Intent(context, OproepDetailService::class.java)
            .putExtra(OproepDetailService.EXTRA_CALLER_ID, callerId)
        try {
            context.startForegroundService(serviceIntent)
            Log.d(TAG, "Caller identification service started for $callerId")
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
        prefs.edit { putString("CallerNumber", PLACEHOLDER_NUMBER) }
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