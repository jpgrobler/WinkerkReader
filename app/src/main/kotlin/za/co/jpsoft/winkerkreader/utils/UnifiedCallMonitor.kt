package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import za.co.jpsoft.winkerkreader.data.DatabaseHelper
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.models.CallType
import java.util.concurrent.ConcurrentHashMap

/**
 * Central singleton that handles all call logging (regular phone + VoIP).
 * All call events must go through this class.
 */
class UnifiedCallMonitor private constructor(
    private val context: Context,
    private val databaseHelper: DatabaseHelper,
    private var calendarManager: CalendarManager,
    private var calendarId: Long
) {

    companion object {
        private const val TAG = "UnifiedCallMonitor"
        @Volatile
        private var instance: UnifiedCallMonitor? = null

        fun getInstance(
            context: Context,
            databaseHelper: DatabaseHelper,
            calendarManager: CalendarManager,
            calendarId: Long
        ): UnifiedCallMonitor {
            return instance ?: synchronized(this) {
                instance ?: UnifiedCallMonitor(
                    context.applicationContext,
                    databaseHelper,
                    calendarManager,
                    calendarId
                ).also { instance = it }
            }
        }

        /**
         * Convenience method to get the instance without providing all dependencies.
         * Useful for UI observers.
         */
        fun getInstance(context: Context): UnifiedCallMonitor {
            val current = instance
            if (current != null) return current

            val dbHelper = DatabaseHelper.getInstance(context)
            val calManager = CalendarManager(context)
            val prefs = context.getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)
            val calId = prefs.getLong(WinkerkContract.KEY_SELECTED_CALENDAR_ID, -1L)

            return getInstance(context, dbHelper, calManager, calId)
        }
    }
    
    private val _callLogUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val callLogUpdates = _callLogUpdates.asSharedFlow()

    // Active calls: key = callId, value = ActiveCall
    private val activeCalls = ConcurrentHashMap<String, ActiveCall>()

    data class ActiveCall(
        val id: String,
        val number: String,
        val contactName: String,
        val type: CallType,
        val startTime: Long,
        val source: String
    )

    /**
     * Call this when a call starts (ringing for incoming, off‑hook for outgoing).
     */
    fun onCallDetected(
    callId: String,
    number: String?,
    direction: String?,
    source: String,
    timestamp: Long,
    displayName: String? = null
    ) {
        val sanitizedNumber = number?.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Unknown Number"
        val contactName = displayName ?: sanitizedNumber
        val callType = determineCallType(source, direction)

        val activeCall = ActiveCall(
            id = callId,
            number = sanitizedNumber,
            contactName = contactName,
            type = callType,
            startTime = timestamp,
            source = source
        )
        activeCalls[callId] = activeCall
        Log.d(TAG, "Call detected: $callId, number=$sanitizedNumber, contact=$contactName, type=$callType, source=$source")
    }

        /**
     * Call this when the call ends (IDLE state for phone, "call ended" notification for VoIP).
     */
    fun onCallEnded(callId: String, endTime: Long) {
        val activeCall = activeCalls.remove(callId) ?: run {
            Log.w(TAG, "No active call found for ID: $callId")
            return
        }

        val durationSeconds = (endTime - activeCall.startTime) / 1000
        // For VoIP, if type is UNKNOWN try to infer from call log (could be improved)
        val finalType = if (activeCall.type == CallType.UNKNOWN) {
            // Default to INCOMING for VoIP if we have a number, otherwise UNKNOWN
            if (activeCall.number != "Unknown Number") CallType.INCOMING else CallType.UNKNOWN
        } else {
            activeCall.type
        }

        logCall(
            contactInfo = activeCall.contactName,
            callType = finalType,
            timestamp = activeCall.startTime,
            duration = durationSeconds,
            source = activeCall.source
        )
        Log.d(TAG, "Call ended: $callId, duration=${durationSeconds}s, type=$finalType")
    }

    /**
     * Update the calendar ID if the user changes the selection.
     */
    fun updateCalendar(calendarManager: CalendarManager, calendarId: Long) {
        this.calendarManager = calendarManager
        this.calendarId = calendarId
    }

    private fun determineCallType(source: String, direction: String?): CallType {
        return when {
            source == "Phone Call" -> when (direction?.lowercase()) {
                "incoming" -> CallType.INCOMING
                "outgoing" -> CallType.OUTGOING
                "missed" -> CallType.MISSED
                else -> CallType.UNKNOWN
            }
            source.contains("WhatsApp", ignoreCase = true) ||
                    source.contains("Skype", ignoreCase = true) ||
                    source.contains("Zoom", ignoreCase = true) -> {
                // VoIP: trust the direction only if explicitly given
                when (direction?.lowercase()) {
                    "incoming" -> CallType.INCOMING
                    "outgoing" -> CallType.OUTGOING
                    "missed" -> CallType.MISSED
                    else -> CallType.UNKNOWN
                }
            }
            else -> CallType.UNKNOWN
        }
    }

    private fun logCall(contactInfo: String, callType: CallType, timestamp: Long, duration: Long, source: String) {
        val settingsManager = SettingsManager.getInstance(context)
        if (!settingsManager.callLogEnabled) {
            Log.d(TAG, "Call logging disabled, skipping")
            return
        }
        if (callType == CallType.UNKNOWN) {
            Log.d(TAG, "Skipping UNKNOWN call type: $contactInfo from $source")
            return
        }

        // Insert into local database
        val success = databaseHelper.insertCallLogWithType(contactInfo, timestamp, callType, source, duration)
        if (success) {
            Log.d(TAG, "Call logged to DB: $contactInfo, type=$callType, source=$source")
            
            // Notify UI that call logs have been updated
            LocalBroadcastManager.getInstance(context).sendBroadcast(
                Intent(PhoneCallMonitor.ACTION_CALL_LOG_UPDATED)
            )
            _callLogUpdates.tryEmit(Unit)

            // Insert into calendar if a valid calendar is selected
            if (calendarId != -1L) {
                val calendarSuccess = calendarManager.addCallEventToCalendar(
                    calendarId, contactInfo, timestamp, callType, source, duration
                )
                if (calendarSuccess) {
                    Log.d(TAG, "Call logged to calendar")
                } else {
                    Log.e(TAG, "Failed to log call to calendar")
                }
            }
        } else {
            Log.e(TAG, "Failed to log call to DB")
        }
    }
}