package za.co.jpsoft.winkerkreader.data.pastoral.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderWithMember
import za.co.jpsoft.winkerkreader.data.pastoral.model.VandagDashboard
import java.time.LocalDate
import java.time.ZoneId

/**
 * Handles all read-only queries for pastoral reminders.
 */
class ReminderQueryRepository(
    private val database: PastoralDatabase,
    private val memberResolver: MemberGuidResolver
) {
    private val reminderDao = database.followUpReminderDao()
    private val zoneId = ZoneId.systemDefault()

    /**
     * Combined dashboard for today's due and overdue reminders.
     */
    fun observeVandagDashboard(): Flow<VandagDashboard> {
        val bounds = dayBounds()
        return combine(
            reminderDao.observeDueToday(bounds.endOfDayUtc, bounds.nowUtc),
            reminderDao.observeOverdue(bounds.startOfTodayUtc)
        ) { dueToday, overdue ->
            VandagDashboard(
                dueToday = dueToday.map { toReminderWithMember(it) },
                overdue = overdue.map { toReminderWithMember(it) },
                todayCount = dueToday.count {
                    isDueOnDate(it.dueDateUtc, bounds.startOfTodayUtc, bounds.endOfDayUtc)
                },
                overdueCount = overdue.size
            )
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Pending reminders for a single member.
     */
    fun observePendingForMember(memberGuid: String): Flow<List<FollowUpReminderEntity>> =
        reminderDao.observePendingForMember(memberGuid)

    /**
     * Reminders due this week (after today).
     */
    fun observeDueThisWeek(
        endOfTodayUtc: Long,
        endOfWeekUtc: Long
    ): Flow<List<ReminderWithMember>> =
        reminderDao.observeDueThisWeek(endOfTodayUtc, endOfWeekUtc)
            .map { reminders -> reminders.map { toReminderWithMember(it) } }
            .flowOn(Dispatchers.IO)

    /**
     * All pending reminders from today onwards.
     */
    fun observeFromToday(): Flow<List<ReminderWithMember>> {
        val startOfTodayUtc = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return reminderDao.observeFromToday(startOfTodayUtc)
            .map { reminders -> reminders.map { toReminderWithMember(it) } }
            .flowOn(Dispatchers.IO)
    }

    // ------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------

    private fun toReminderWithMember(reminder: FollowUpReminderEntity): ReminderWithMember {
        val member = memberResolver.resolve(reminder.memberGuid)
        return ReminderWithMember(
            reminder = reminder,
            displayName = member?.displayName ?: reminder.memberDisplayNameCache.orEmpty(),
            cellphone = member?.cellphone,
            photoPath = member?.photoPath
        )
    }

    private fun dayBounds(): DayBounds {
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zoneId)
        val startOfTodayUtc = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDayUtc = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return DayBounds(
            nowUtc = now,
            startOfTodayUtc = startOfTodayUtc,
            endOfDayUtc = endOfDayUtc
        )
    }

    private fun isDueOnDate(dueDateUtc: Long, startOfTodayUtc: Long, endOfDayUtc: Long): Boolean =
        dueDateUtc in startOfTodayUtc..endOfDayUtc

    private data class DayBounds(
        val nowUtc: Long,
        val startOfTodayUtc: Long,
        val endOfDayUtc: Long
    )
}