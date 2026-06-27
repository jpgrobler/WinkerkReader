package za.co.jpsoft.winkerkreader.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.DatabaseHelper
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.models.CallType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
        @SuppressLint("StaticFieldLeak")
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
        val source: String,
        var isMissed: Boolean = false
    )
    fun onCallMissed(callId: String, endTime: Long) {
        val activeCall = activeCalls.remove(callId) ?: run {
            if (BuildConfig.DEBUG) Log.w(TAG, "onCallMissed: no active call for ID: $callId")
            return
        }
        // Mark as missed to prevent onCallEnded from logging it again
        activeCall.isMissed = true
        logCall(
            contactInfo = activeCall.contactName,
            callType = CallType.MISSED,
            timestamp = activeCall.startTime,
            duration = 0L,
            source = activeCall.source
        )
        if (BuildConfig.DEBUG) Log.d(TAG, "Call marked as missed: $callId")
    }
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
        pruneStaleActiveCalls()
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
        if (BuildConfig.DEBUG) Log.d(TAG, "Call detected: $callId, number=$sanitizedNumber, contact=$contactName, type=$callType, source=$source")
    }

        /**
     * Call this when the call ends (IDLE state for phone, "call ended" notification for VoIP).
     */
        fun onCallEnded(callId: String, endTime: Long) {
            val activeCall = activeCalls.remove(callId) ?: run {
                if (BuildConfig.DEBUG) Log.w(TAG, "onCallEnded: no active call for ID: $callId")
                return
            }

            // If we already logged this call as missed, skip further logging
            if (activeCall.isMissed) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Call $callId already logged as missed, skipping onCallEnded")
                return
            }

            val durationSeconds = maxOf(0L, (endTime - activeCall.startTime) / 1000)
            logCall(
                contactInfo = activeCall.contactName,
                callType = activeCall.type,
                timestamp = activeCall.startTime,
                duration = durationSeconds,
                source = activeCall.source
            )
            if (BuildConfig.DEBUG) Log.d(TAG, "Call ended: $callId, duration=${durationSeconds}s, type=${activeCall.type}")
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
            else -> {
                // VoIP sources (WhatsApp, Skype, Zoom, Teams, Discord, Telegram,
                // Viber, Messenger, Google Meet, etc.): trust the direction
                when (direction?.lowercase()) {
                    "incoming" -> CallType.INCOMING
                    "outgoing" -> CallType.OUTGOING
                    "missed" -> CallType.MISSED
                    else -> CallType.UNKNOWN
                }
            }
        }
    }

    private fun logCall(contactInfo: String, callType: CallType, timestamp: Long, duration: Long, source: String) {
        val settingsManager = SettingsManager.getInstance(context)
        if (!settingsManager.callLogEnabled) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Call logging disabled, skipping")
            return
        }
        if (callType == CallType.UNKNOWN) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Skipping UNKNOWN call type: $contactInfo from $source")
            return
        }

        // Insert into local database
        val success = databaseHelper.insertCallLogWithType(contactInfo, timestamp, callType, source, duration)
        if (success) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Call logged to DB: $contactInfo, type=$callType, source=$source")
            
            // Notify UI that call logs have been updated
            _callLogUpdates.tryEmit(Unit)

            // Insert into calendar if a valid calendar is selected
            if (calendarId != -1L) {
                val calendarSuccess = calendarManager.addCallEventToCalendar(
                    calendarId, contactInfo, timestamp, callType, source, duration
                )
                if (calendarSuccess) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Call logged to calendar")
                } else {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Failed to log call to calendar")
                }
            }
        } else {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to log call to DB")
        }
    }
    private fun pruneStaleActiveCalls() {
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(4)
        activeCalls.entries.removeIf { it.value.startTime < cutoff }
    }
}