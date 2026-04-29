package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import za.co.jpsoft.winkerkreader.data.DatabaseHelper
import za.co.jpsoft.winkerkreader.data.models.CallType
import java.util.concurrent.ConcurrentHashMap

class UnifiedCallMonitor(
    private val context: Context,
    private val databaseHelper: DatabaseHelper,
    private val calendarManager: CalendarManager,
    private val calendarId: Long
) {

    private val activeCalls = ConcurrentHashMap<String, ActiveCall>()
    private var lastLoggedCallId: String = ""
    private var lastLogTime: Long = 0

    data class ActiveCall(
        val id: String,
        val number: String,
        val contactName: String,
        val type: CallType,
        val startTime: Long,
        val source: String
    )

    fun onCallDetected(
        callId: String,
        number: String?,
        direction: String?,
        source: String,
        timestamp: Long
    ) {
        // Prevent duplicate within 2 seconds
        if (System.currentTimeMillis() - lastLogTime < 2000 &&
            lastLoggedCallId == callId) {
            Log.d(TAG, "Duplicate call detected, skipping: $callId")
            return
        }

        val sanitizedNumber = number?.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Unknown Number"
        val contactName = getContactName(sanitizedNumber)

        // Determine call type based on source and direction
        val callType = when {
            source == "Phone Call" -> determinePhoneCallType(direction)
            source.contains("WhatsApp", ignoreCase = true) -> {
                // For VoIP, default to UNKNOWN direction and let the call end determine
                if (direction == null) CallType.UNKNOWN else determineVoIPCallType(direction)
            }
            else -> CallType.UNKNOWN
        }

        val activeCall = ActiveCall(
            id = callId,
            number = sanitizedNumber,
            contactName = contactName,
            type = callType,
            startTime = timestamp,
            source = source
        )

        activeCalls[callId] = activeCall
        Log.d(TAG, "Call detected: ID=$callId, Number=$sanitizedNumber, Type=$callType, Source=$source")
    }

    fun onCallEnded(callId: String, endTime: Long) {
        val activeCall = activeCalls.remove(callId) ?: run {
            Log.w(TAG, "No active call found for ID: $callId")
            return
        }

        val duration = (endTime - activeCall.startTime) / 1000
        val finalCallType = if (activeCall.type == CallType.UNKNOWN) {
            // Try to determine from VoIP call log if available
            determineCallTypeFromVoIPLog(activeCall.number, activeCall.startTime)
        } else {
            activeCall.type
        }

        // Only log if not recently logged
        if (System.currentTimeMillis() - lastLogTime > 2000 || lastLoggedCallId != callId) {
            logCall(
                contactInfo = activeCall.contactName,
                callType = finalCallType,
                timestamp = activeCall.startTime,
                duration = duration,
                source = activeCall.source
            )
            lastLoggedCallId = callId
            lastLogTime = System.currentTimeMillis()
        }

        Log.d(TAG, "Call ended: ID=$callId, Duration=${duration}s, Type=$finalCallType")
    }

    private fun determinePhoneCallType(direction: String?): CallType {
        return when (direction?.lowercase()) {
            "incoming" -> CallType.INCOMING
            "outgoing" -> CallType.OUTGOING
            "missed" -> CallType.MISSED
            else -> CallType.UNKNOWN
        }
    }

    private fun determineVoIPCallType(direction: String?): CallType {
        // VoIP apps often report direction incorrectly
        // Return UNKNOWN and let the system determine from call log
        return when (direction?.lowercase()) {
            "incoming" -> CallType.INCOMING
            "outgoing" -> CallType.OUTGOING
            else -> CallType.UNKNOWN
        }
    }

    private fun determineCallTypeFromVoIPLog(number: String, timestamp: Long): CallType {
        // Check notification history or VoIP app's call log
        // For now, default to UNKNOWN (won't be logged with incorrect direction)
        return CallType.UNKNOWN
    }

    private fun getContactName(phoneNumber: String): String {
        // Implementation from your PhoneCallMonitor
        return phoneNumber // Simplified for brevity
    }

    private fun logCall(contactInfo: String, callType: CallType, timestamp: Long, duration: Long, source: String) {
        val settingsManager = SettingsManager.getInstance(context)
        if (!settingsManager.callLogEnabled) {
            Log.d(TAG, "Call logging is disabled")
            return
        }

        // Don't log UNKNOWN type calls as they're likely VoIP misdirection
        if (callType == CallType.UNKNOWN) {
            Log.d(TAG, "Skipping UNKNOWN call type: $contactInfo from $source")
            return
        }

        val success = databaseHelper.insertCallLogWithType(contactInfo, timestamp, callType, source, duration)
        if (success) {
            Log.d(TAG, "Call logged: $contactInfo, Type=$callType, Source=$source, Duration=${duration}s")
            if (calendarId != -1L) {
                calendarManager.addCallEventToCalendar(calendarId, contactInfo, timestamp, callType, source, duration)
            }
        }
    }

    companion object {
        private const val TAG = "UnifiedCallMonitor"
    }
}