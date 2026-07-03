package za.co.jpsoft.winkerkreader.data.calllog

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import za.co.jpsoft.winkerkreader.data.calllog.ActiveCallEntity
import za.co.jpsoft.winkerkreader.data.calllog.CallLogEntity

@Dao
interface CallLogDao {

    // --- Finished call log ---

    @Query("""
        SELECT COUNT(*) FROM call_logs
        WHERE callerInfo = :callerInfo
          AND ABS(timestamp - :timestamp) < :timeWindowMs
          AND source = :source
    """)
    suspend fun countDuplicates(callerInfo: String, timestamp: Long, source: String, timeWindowMs: Long = 3000): Int

    @Insert
    suspend fun insert(entity: CallLogEntity): Long

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    suspend fun getAll(): List<CallLogEntity>

    @Query("DELETE FROM call_logs")
    suspend fun clearAll(): Int

    // --- Durable "active call" backstop ---

    @Upsert
    suspend fun upsertActiveCall(entity: ActiveCallEntity)

    @Query("DELETE FROM active_calls WHERE callId = :callId")
    suspend fun removeActiveCall(callId: String)

    @Query("SELECT * FROM active_calls")
    suspend fun getAllActiveCalls(): List<ActiveCallEntity>
}