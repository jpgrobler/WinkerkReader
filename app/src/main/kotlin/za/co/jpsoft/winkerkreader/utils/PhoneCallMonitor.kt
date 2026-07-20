package za.co.jpsoft.winkerkreader.utils

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.calllog.CallLogDao
import za.co.jpsoft.winkerkreader.data.models.CallType
import za.co.jpsoft.winkerkreader.services.OproepDetailService

class PhoneCallMonitor(
    private val context: Context,
    callLogDao: CallLogDao,
    calendarManager: CalendarManager,
    calendarId: Long
) {

    private var telephonyManager: TelephonyManager? = null

    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null
    private var currentIncomingNumber: String? = null
    private var currentOutgoingNumber: String? = null
    private var callStartTime: Long = 0
    private var currentCallId: String? = null
    private var isCallActive = false
    private var currentCallType: CallType? = null
    private var pendingIncomingNumber: String? = null

    private var unifiedMonitor: UnifiedCallMonitor? = null
    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        unifiedMonitor =
            UnifiedCallMonitor.getInstance(context, callLogDao, calendarManager, calendarId)
    }

    fun setIncomingNumber(number: String?) {
        pendingIncomingNumber = number
    }

    fun startMonitoring() {
        if (!hasRequiredPermissions()) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Missing permissions")
            return
        }

        if (telephonyManager == null) {
            telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        }

        // Use the deprecated listener on all SDK versions – it still provides the number
        @Suppress("DEPRECATION")
        phoneStateListener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleStateChanged(state, phoneNumber)
                super.onCallStateChanged(state, phoneNumber)

            }
        }

        @Suppress("DEPRECATION")
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        if (BuildConfig.DEBUG) Log.d(TAG, "PhoneStateListener registered (all versions)")
    }

    private fun handleStateChanged(state: Int, phoneNumber: String?) {
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "Call state changed: ${getCallStateName(state)}, Number: $phoneNumber"
        )
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> handleRingingState(phoneNumber)
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                OproepDetailService.dismissOnAnswered()
                handleOffHookState(phoneNumber)
            }
            TelephonyManager.CALL_STATE_IDLE -> handleIdleState()
        }
    }

    fun stopMonitoring() {
        monitorScope.cancel()
        try {
            @Suppress("DEPRECATION")
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            phoneStateListener = null
            telephonyManager = null
            if (BuildConfig.DEBUG) Log.d(TAG, "Phone call monitoring stopped")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error stopping phone call monitoring", e)
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
        currentCallType = CallType.INCOMING
        currentIncomingNumber = if (!number.isNullOrBlank()) number else "Unknown Number"

        if (BuildConfig.DEBUG) Log.d(TAG, "INCOMING call: $currentIncomingNumber")

        val settings = context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
        settings.edit { putString("CallerNumber", currentIncomingNumber) }
        startCallerIdentificationService(context, currentIncomingNumber)
    }

//    private fun handleRingingState(phoneNumber: String?) {
//        val number = pendingIncomingNumber ?: phoneNumber
//        pendingIncomingNumber = null
//
//        callStartTime = System.currentTimeMillis()
//        val callId = "phone_$callStartTime"
//        currentCallId = callId
//
//        currentIncomingNumber = if (!number.isNullOrBlank()) number else "Unknown Number"
//
//        currentCallType = CallType.INCOMING
//
//        val settings = context.getSharedPreferences(PREFS_USER_INFO, 0)
//        settings.edit { putString("CallerNumber", number) }
//        if (BuildConfig.DEBUG) Log.d(TAG, "INCOMING call detected: $currentIncomingNumber")
//
//        // Launch coroutine to resolve name and log
//        monitorScope.launch {
//            val displayName = if (number.isNullOrBlank()) {
//                null
//            } else {
//                // Try to resolve from member database first
//                val result = CallerInfoResolver.resolve(number, context.contentResolver)
//                when (result) {
//                    is CallerInfoResult.Member -> result.name
//                    is CallerInfoResult.Contact -> result.name
//                    CallerInfoResult.Unknown -> {
//                        // If not found in app database, try system contacts
//                        getContactNameFromSystem(number)
//                    }
//
//                    else -> {getContactNameFromSystem(number)}
//                }
//            }
//            unifiedMonitor?.onCallDetected(
//                callId = callId,
//                number = number ?: "Unknown Number",
//                direction = "incoming",
//                source = "Phone Call",
//                timestamp = callStartTime,
//                displayName = displayName ?: "Unknown Contact"
//            )
//            startCallerIdentificationService(context, number)
//        }
//    }

    private fun handleOffHookState(phoneNumber: String?) {
        if (currentIncomingNumber != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Incoming call ANSWERED: $currentIncomingNumber")
            isCallActive = true
        } else {
            // Outgoing call
            callStartTime = System.currentTimeMillis()
            val callId = "phone_$callStartTime"
            currentCallId = callId
            currentOutgoingNumber =
                if (!phoneNumber.isNullOrBlank()) phoneNumber else "Unknown Number"
            currentCallType = CallType.OUTGOING
            isCallActive = true

            if (BuildConfig.DEBUG) Log.d(TAG, "OUTGOING call detected: $currentOutgoingNumber")

            monitorScope.launch {
                val number = phoneNumber ?: "Unknown Number"
                val displayName = resolveName(number, context.contentResolver) ?: number
                unifiedMonitor?.onCallDetected(
                    callId = callId,
                    number = number,
                    direction = "outgoing",
                    source = "Phone Call",
                    timestamp = callStartTime,
                    displayName = displayName
                )
            }
        }
    }

    private fun handleIdleState() {
        val callId = currentCallId
        val startTime = callStartTime

        when {
            isCallActive && callId != null -> {
                val direction = if (currentIncomingNumber != null) "incoming" else "outgoing"
                val number = currentIncomingNumber ?: currentOutgoingNumber ?: "Unknown Number"

                monitorScope.launch {
                    val displayName = resolveName(number, context.contentResolver) ?: number
                    unifiedMonitor?.onCallDetected(
                        callId = callId,
                        number = number,
                        direction = direction,
                        source = "Phone Call",
                        timestamp = startTime,
                        displayName = displayName
                    )
                    unifiedMonitor?.onCallEnded(callId, System.currentTimeMillis())
                }
            }

            currentIncomingNumber != null && callId != null -> {
                // Missed call
                val number = currentIncomingNumber!!
                monitorScope.launch {
                    val displayName = resolveName(number, context.contentResolver) ?: number
                    unifiedMonitor?.onCallDetected(
                        callId = callId,
                        number = number,
                        direction = "missed",
                        source = "Phone Call",
                        timestamp = startTime,
                        displayName = displayName
                    )
                    unifiedMonitor?.onCallEnded(callId, System.currentTimeMillis())
                }
            }
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

    private fun startCallerIdentificationService(context: Context, callerId: String?) {
        val number = callerId?.takeIf { it.isNotBlank() && it != "Unknown Number" } ?: return

        val serviceIntent = Intent(context, OproepDetailService::class.java)
            .putExtra(OproepDetailService.EXTRA_CALLER_ID, number)
        try {
            context.startForegroundService(serviceIntent)
            if (BuildConfig.DEBUG) Log.d(TAG, "Caller identification service started for $number")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(
                TAG,
                "Failed to start caller identification service: ${e.message}"
            )
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
            if (BuildConfig.DEBUG) Log.d(TAG, "Caller identification service stopped")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(
                TAG,
                "Failed to stop caller identification service: ${e.message}"
            )
        }
    }

    fun resolveName(number: String, contentResolver: ContentResolver): String? {
        if (number.isBlank() || number == "Unknown Number") return null

        // 1. Try app's member database
        val result = CallerInfoResolver.resolve(number, contentResolver)
        when (result) {
            is CallerInfoResult.Member -> return result.name
            is CallerInfoResult.Contact -> return result.name
            is CallerInfoResult.MultipleMembers -> {
                // If multiple members match, return a concatenated name (optional)
                return result.members.joinToString(", ") { it.name }
            }

            CallerInfoResult.Unknown -> { /* fall through to system contacts */
            }
        }

        // 2. Fallback to system contacts
        return resolveFromSystemContacts(number, contentResolver)
    }

    private fun resolveFromSystemContacts(
        number: String,
        contentResolver: ContentResolver
    ): String? {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(number)
            .build()
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    companion object {
        private const val TAG = "PhoneCallMonitor"
        private const val PLACEHOLDER_NUMBER = "XXXXXXXXXX"
        private const val CALL_END_DELAY_MS = 2000L
    }
}