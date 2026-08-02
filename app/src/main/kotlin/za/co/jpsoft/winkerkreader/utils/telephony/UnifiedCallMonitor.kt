package za.co.jpsoft.winkerkreader.utils.telephony

import android.content.Context
import android.util.Log
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.calllog.dao.CallLogDao
import za.co.jpsoft.winkerkreader.data.calllog.entities.ActiveCallEntity
import za.co.jpsoft.winkerkreader.data.calllog.entities.CallLogEntity
import za.co.jpsoft.winkerkreader.data.calllog.models.CallType
import za.co.jpsoft.winkerkreader.data.calllog.setup.CallLogDatabaseBackup
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Singleton
class UnifiedCallMonitor @Inject constructor(
    private val context: Context,
    private val callLogDao: CallLogDao,
    private val calendarManager: CalendarManager,
    private val callMonitorPrefs: CallMonitorPrefs
) {

    private val _callLogUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val callLogUpdates = _callLogUpdates.asSharedFlow()

    private var calendarId: Long = callMonitorPrefs.callCalendarId ?: -1L

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

        callLogDao.upsertActiveCall(
            ActiveCallEntity(
                callId = callId, number = sanitizedNumber, contactName = contactName,
                callType = callType, source = source, startTime = timestamp
            )
        )

        if (BuildConfig.DEBUG) Log.d(TAG, "Call detected: $callId, ...")
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
        if (!callMonitorPrefs.callLogEnabled) {
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
            // CallLogDatabaseBackup is now an injected dependency? We'll need to inject it.
            // For now, we'll keep the static backup call, but we can later inject it.
            // Since CallLogDatabaseBackup is also an object, we'll need to refactor it similarly.
            // For now, keep the static call.
            CallLogDatabaseBackup.backupDebounced(context)

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

    companion object {
        private const val TAG = "UnifiedCallMonitor"
    }
}