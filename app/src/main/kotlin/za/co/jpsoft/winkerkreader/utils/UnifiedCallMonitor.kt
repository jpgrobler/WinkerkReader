package za.co.jpsoft.winkerkreader.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.calllog.ActiveCallEntity
import za.co.jpsoft.winkerkreader.data.calllog.CallLogDao
import za.co.jpsoft.winkerkreader.data.calllog.CallLogDatabase
import za.co.jpsoft.winkerkreader.data.calllog.CallLogEntity
import za.co.jpsoft.winkerkreader.data.models.CallType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Central singleton that handles all call logging (regular phone + VoIP).
 * All call events must go through this class.
 */
class UnifiedCallMonitor private constructor(
    private val context: Context,
    private val callLogDao: CallLogDao,
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
            callLogDao: CallLogDao,
            calendarManager: CalendarManager,
            calendarId: Long
        ): UnifiedCallMonitor {
            return instance ?: synchronized(this) {
                instance ?: UnifiedCallMonitor(
                    context.applicationContext,
                    callLogDao,
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

            val dao = CallLogDatabase.getInstance(context).callLogDao()
            val calManager = CalendarManager(context)
            val prefs =
                context.getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)
            val calId = prefs.getLong(WinkerkContract.KEY_SELECTED_CALENDAR_ID, -1L)

            return getInstance(context, dao, calManager, calId)
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

    suspend fun onCallMissed(callId: String, endTime: Long) {
        val activeCall = activeCalls.remove(callId) ?: run {
            if (BuildConfig.DEBUG) Log.w(TAG, "onCallMissed: no active call for ID: $callId")
            return
        }
        callLogDao.removeActiveCall(callId)
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

    suspend fun onCallEnded(callId: String, endTime: Long) {
        val activeCall = activeCalls.remove(callId) ?: run {
            if (BuildConfig.DEBUG) Log.w(TAG, "onCallEnded: no active call for ID: $callId")
            return
        }
        callLogDao.removeActiveCall(callId)

        if (activeCall.isMissed) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Call $callId already logged as missed, skipping onCallEnded"
            )
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
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "Call ended: $callId, duration=${durationSeconds}s, type=${activeCall.type}"
        )
    }

    /**
     * Call this when a call starts (ringing for incoming, off‑hook for outgoing).
     */
    suspend fun onCallDetected(
        callId: String,
        number: String?,
        direction: String?,
        source: String,
        timestamp: Long,
        displayName: String? = null
    ) {
        pruneStaleActiveCalls()
        val sanitizedNumber =
            number?.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Unknown Number"
        val contactName = displayName ?: sanitizedNumber
        val callType = determineCallType(source, direction)

        val activeCall = ActiveCall(
            id = callId, number = sanitizedNumber, contactName = contactName,
            type = callType, startTime = timestamp, source = source
        )
        activeCalls[callId] = activeCall

        // Durable backstop: survives process death.
        callLogDao.upsertActiveCall(
            ActiveCallEntity(
                callId = callId, number = sanitizedNumber, contactName = contactName,
                callType = callType, source = source, startTime = timestamp
            )
        )

        if (BuildConfig.DEBUG) Log.d(TAG, "Call detected: $callId, ...")
    }

    /**
     * Update the calendar ID if the user changes the selection.
     */
    fun updateCalendar(calendarManager: CalendarManager, calendarId: Long) {
        this.calendarManager = calendarManager
        this.calendarId = calendarId
    }

    private fun determineCallType(source: String, direction: String?): CallType {
        return when (direction?.lowercase()) {
            "incoming" -> CallType.INCOMING
            "outgoing" -> CallType.OUTGOING
            "missed" -> CallType.MISSED
            "other" -> CallType.OTHER
            else -> CallType.UNKNOWN
        }
    }

    private suspend fun logCall(
        contactInfo: String,
        callType: CallType,
        timestamp: Long,
        duration: Long,
        source: String
    ) {
        val settingsManager = SettingsManager.getInstance(context)
        if (!settingsManager.callMonitor.callLogEnabled) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Call logging disabled, skipping")
            return
        }
        if (callType == CallType.UNKNOWN) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Skipping UNKNOWN call type: $contactInfo from $source"
            )
            return
        }

        if (callLogDao.countDuplicates(contactInfo, timestamp, source) > 0) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Duplicate call detected, skipping insert: $contactInfo"
            )
            return
        }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDateTime = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(formatter)

        val result = callLogDao.insert(
            CallLogEntity(
                callerInfo = contactInfo,
                timestamp = timestamp,
                dateTime = formattedDateTime,
                callType = callType,
                source = source,
                duration = duration
            )
        )
        val success = result != -1L
        if (success) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Call logged to DB: $contactInfo, type=$callType, source=$source"
            )

            _callLogUpdates.tryEmit(Unit)
            za.co.jpsoft.winkerkreader.data.calllog.CallLogDatabaseBackup.backupDebounced(context)

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

    private suspend fun pruneStaleActiveCalls() {
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(4)
        val stale = activeCalls.entries.filter { it.value.startTime < cutoff }
        stale.forEach { callLogDao.removeActiveCall(it.key) }
        activeCalls.entries.removeIf { it.value.startTime < cutoff }
    }

    /**
     * Ends any currently-active calls whose source is a VoIP app (i.e. not
     * "Phone Call"), logging them with an end time of now. Intended to be
     * called when WhatsAppNotificationService reconnects after a rebind, so
     * calls orphaned by the disconnect don't sit around until the stale-call
     * prune silently discards them.
     * @return number of calls closed out this way.
     */
    suspend fun endActiveVoipCallsFromOtherSources(): Int {
        val staleVoipCallIds = activeCalls.values
            .filter { it.source != "Phone Call" }
            .map { it.id }

        staleVoipCallIds.forEach { callId ->
            if (BuildConfig.DEBUG) Log.w(
                TAG,
                "Closing VoIP call orphaned by listener rebind: $callId"
            )
            onCallEnded(callId, System.currentTimeMillis())
        }
        return staleVoipCallIds.size
    }
}