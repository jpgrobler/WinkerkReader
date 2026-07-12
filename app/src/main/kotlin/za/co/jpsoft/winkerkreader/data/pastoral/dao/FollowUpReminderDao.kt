package za.co.jpsoft.winkerkreader.data.pastoral.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderStatus

@Dao
interface FollowUpReminderDao {

    /**
     * One-shot query for the worker — returns all PENDING reminders due today or earlier
     * that are not actively snoozed. Mirrors [observeDueToday] logic without the Flow wrapper.
     */

    @Query(
        """
        SELECT COUNT(*) FROM follow_up_reminders
        WHERE status = :pendingStatus
          AND dueDateUtc <= :endOfDayUtc
          AND (snoozedUntilUtc IS NULL OR snoozedUntilUtc <= :nowUtc)
        """
    )

    suspend fun countDueToday(
        endOfDayUtc: Long,
        nowUtc: Long,
        pendingStatus: String = ReminderStatus.PENDING.name
    ): Int

    /**
     * Reminders due strictly after today through [endOfWeekUtc].
     * Does NOT include today (covered by [observeDueToday]) or overdue.
     * Used by the "Hierdie week" filter chip in BedieningVandagFragment.
     */
    @Query(
        """
    SELECT * FROM follow_up_reminders
    WHERE status = :pendingStatus
      AND dueDateUtc > :endOfTodayUtc
      AND dueDateUtc <= :endOfWeekUtc
    ORDER BY dueDateUtc ASC
    """
    )
    fun observeDueThisWeek(
        endOfTodayUtc: Long,
        endOfWeekUtc: Long,
        pendingStatus: String = ReminderStatus.PENDING.name
    ): Flow<List<FollowUpReminderEntity>>

    @Query(
        """
    SELECT * FROM follow_up_reminders
    WHERE status = :pendingStatus
      AND dueDateUtc >= :startOfToday
    ORDER BY dueDateUtc ASC
"""
    )
    fun observeFromToday(
        startOfToday: Long,
        pendingStatus: String = "PENDING"
    ): Flow<List<FollowUpReminderEntity>>

    @Query(
        """
    SELECT * FROM follow_up_reminders
    WHERE status = :pendingStatus
      AND dueDateUtc <= :endOfDayUtc
      AND (snoozedUntilUtc IS NULL OR snoozedUntilUtc <= :nowUtc)
    ORDER BY dueDateUtc ASC
    """
    )
    suspend fun getPendingDue(
        endOfDayUtc: Long,
        nowUtc: Long,
        pendingStatus: String = ReminderStatus.PENDING.name
    ): List<FollowUpReminderEntity>

    @Query(
        """
    SELECT * FROM follow_up_reminders
    WHERE status = :pendingStatus
      AND dueDateUtc <= :endOfDayUtc
      AND (snoozedUntilUtc IS NULL OR snoozedUntilUtc <= :nowUtc)
    ORDER BY dueDateUtc ASC
    """
    )
    fun observeDueToday(
        endOfDayUtc: Long,
        nowUtc: Long,
        pendingStatus: String = ReminderStatus.PENDING.name
    ): Flow<List<FollowUpReminderEntity>>


    @Query(
        """
        SELECT * FROM follow_up_reminders
        WHERE status = :pendingStatus AND dueDateUtc < :startOfTodayUtc
        ORDER BY dueDateUtc ASC
        """
    )
    fun observeOverdue(
        startOfTodayUtc: Long,
        pendingStatus: String = ReminderStatus.PENDING.name
    ): Flow<List<FollowUpReminderEntity>>

    @Query(
        """
        SELECT * FROM follow_up_reminders
        WHERE memberGuid = :memberGuid AND status = :pendingStatus
        ORDER BY dueDateUtc ASC
        """
    )
    fun observePendingForMember(
        memberGuid: String,
        pendingStatus: String = ReminderStatus.PENDING.name
    ): Flow<List<FollowUpReminderEntity>>

    @Query("SELECT * FROM follow_up_reminders WHERE reminderId = :reminderId LIMIT 1")
    suspend fun getById(reminderId: String): FollowUpReminderEntity?

    @Query(
        """
        SELECT COUNT(*) FROM follow_up_reminders
        WHERE status = :pendingStatus AND dueDateUtc < :startOfTodayUtc
        """
    )
    suspend fun countOverdue(
        startOfTodayUtc: Long,
        pendingStatus: String = ReminderStatus.PENDING.name
    ): Int

    @Query(
        """
    SELECT * FROM follow_up_reminders
    WHERE memberGuid = :memberGuid
      AND templateId = :templateId
      AND anchorDateUtc = :anchorDateUtc
    ORDER BY dueDateUtc ASC
    """
    )
    suspend fun getSeries(
        memberGuid: String,
        templateId: String,
        anchorDateUtc: Long
    ): List<FollowUpReminderEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reminder: FollowUpReminderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(reminders: List<FollowUpReminderEntity>)

    @Update
    suspend fun update(reminder: FollowUpReminderEntity)

    @Query("DELETE FROM follow_up_reminders WHERE reminderId = :reminderId")
    suspend fun deleteById(reminderId: String)

    @Query("DELETE FROM follow_up_reminders WHERE status = :status AND completedAtUtc < :beforeUtc")
    suspend fun deleteStaleBefore(status: String, beforeUtc: Long): Int

    @Query("SELECT DISTINCT memberGuid FROM follow_up_reminders WHERE status = :pendingStatus")
    suspend fun getDistinctMemberGuidsWithPending(pendingStatus: String = ReminderStatus.PENDING.name): List<String>

    @Query("SELECT * FROM follow_up_reminders WHERE status = 'PENDING' ORDER BY dueDateUtc ASC")
    fun getAllPending(): List<FollowUpReminderEntity>

    @Query("DELETE FROM follow_up_reminders WHERE reminderId IN (:reminderIds)")
    suspend fun deleteAll(reminderIds: List<String>)
}
