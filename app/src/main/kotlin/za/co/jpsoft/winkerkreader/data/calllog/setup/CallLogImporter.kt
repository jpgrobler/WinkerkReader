package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.util.Log
import jakarta.inject.Inject
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.calllog.dao.CallLogDao
import za.co.jpsoft.winkerkreader.data.calllog.entities.ActiveCallEntity
import za.co.jpsoft.winkerkreader.data.calllog.entities.CallLogEntity
import za.co.jpsoft.winkerkreader.data.calllog.models.CallType
import za.co.jpsoft.winkerkreader.data.members.setup.DatabaseHelper
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import javax.inject.Singleton

@Singleton
class CallLogImporter @Inject constructor(
    private val callMonitorPrefs: CallMonitorPrefs
) {

    private val TAG = "CallLogImporter"

    suspend fun importIfNeeded(context: Context, callLogDao: CallLogDao) {
        if (callMonitorPrefs.callLogImportedToRoom) return

        val legacyHelper = DatabaseHelper.getInstance(context)

        try {
            // Finished calls
            val legacyLogs = legacyHelper.getAllCallLogs()
            legacyLogs.forEach { log ->
                callLogDao.insert(
                    CallLogEntity(
                        callerInfo = log.callerInfo,
                        timestamp = log.timestamp,
                        dateTime = log.formattedDateTime,
                        callType = runCatching { CallType.valueOf(log.callType) }.getOrDefault(
                            CallType.OTHER
                        ),
                        source = log.source,
                        duration = log.duration
                    )
                )
            }

            // Any in-progress calls left over from before the cutover
            val legacyActive = legacyHelper.getAllActiveCalls()
            legacyActive.forEach { active ->
                callLogDao.upsertActiveCall(
                    ActiveCallEntity(
                        callId = active.callId,
                        number = active.number,
                        contactName = active.contactName,
                        callType = runCatching { CallType.valueOf(active.callType) }.getOrDefault(
                            CallType.OTHER
                        ),
                        source = active.source,
                        startTime = active.startTime
                    )
                )
            }

            callMonitorPrefs.callLogImportedToRoom = true
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Imported ${legacyLogs.size} call log(s) and ${legacyActive.size} active call(s) into Room"
            )

            // Legacy DB file is left in place deliberately (not deleted) as a
            // safety net for one release cycle, in case the import needs to
            // be re-run or manually verified. Delete it in a later release
            // once the migration has proven stable.
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Call log import failed, will retry next launch", e)
            // Don't set the flag — retry on next app start.
        }
    }
}