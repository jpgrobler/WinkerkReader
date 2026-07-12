package za.co.jpsoft.winkerkreader.utils

import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.calllog.CallLogDao
import za.co.jpsoft.winkerkreader.data.calllog.CallLogEntity
import za.co.jpsoft.winkerkreader.data.models.CallType

/**
 * Closes out any "active call" rows left behind by a process death mid-call
 * (crash, low-memory kill, forced stop). Should run once, early, on every
 * app cold start — any row present at that point is by definition orphaned,
 * since no call legitimately survives a full process restart.
 */
object ActiveCallReconciler {
    private const val TAG = "ActiveCallReconciler"

    suspend fun reconcile(callLogDao: CallLogDao) {
        val orphaned = callLogDao.getAllActiveCalls()
        if (orphaned.isEmpty()) return

        if (BuildConfig.DEBUG) Log.w(
            TAG,
            "Reconciling ${orphaned.size} orphaned active call(s) from a prior process death"
        )

        orphaned.forEach { call ->
            // We have a start time but no real end time — log with duration 0
            // rather than guessing, so it's clearly flagged as incomplete
            // rather than silently wrong.
            callLogDao.insert(
                CallLogEntity(
                    callerInfo = call.contactName,
                    timestamp = call.startTime,
                    dateTime = formatDateTime(call.startTime),
                    callType = if (call.callType == CallType.UNKNOWN) CallType.OTHER else call.callType,
                    source = call.source,
                    duration = 0L
                )
            )
            callLogDao.removeActiveCall(call.callId)
        }
    }

    private fun formatDateTime(timestamp: Long): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss",
            java.util.Locale.getDefault()
        )
        return java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .format(formatter)
    }
}