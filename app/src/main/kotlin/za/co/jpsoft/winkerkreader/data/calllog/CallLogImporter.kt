package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.DatabaseHelper
import za.co.jpsoft.winkerkreader.data.calllog.ActiveCallEntity
import za.co.jpsoft.winkerkreader.data.calllog.CallLogDatabase
import za.co.jpsoft.winkerkreader.data.calllog.CallLogEntity
import za.co.jpsoft.winkerkreader.data.models.CallType


object CallLogImporter {
    private const val TAG = "CallLogImporter"

    suspend fun importIfNeeded(context: Context, pastoralDb: CallLogDatabase) {
        val settings = SettingsManager.getInstance(context)
        if (settings.callMonitor.callLogImportedToRoom) return

        val legacyHelper = DatabaseHelper.getInstance(context)
        val dao = pastoralDb.callLogDao()

        try {
            // Finished calls
            val legacyLogs = legacyHelper.getAllCallLogs()
            legacyLogs.forEach { log ->
                dao.insert(
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
                dao.upsertActiveCall(
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

            settings.callMonitor.callLogImportedToRoom = true
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