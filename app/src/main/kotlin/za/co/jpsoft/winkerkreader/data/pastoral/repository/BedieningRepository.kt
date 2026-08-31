// File: BedieningRepository.kt (FIXED - NO DUPLICATION)
package za.co.jpsoft.winkerkreader.data.pastoral.repository

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import za.co.jpsoft.winkerkreader.data.members.repository.MemberRepository
import za.co.jpsoft.winkerkreader.data.pastoral.dao.FollowUpReminderDao
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderWithMember
import za.co.jpsoft.winkerkreader.data.pastoral.model.VandagAllesItem
import za.co.jpsoft.winkerkreader.data.pastoral.model.VandagAllesSection
import java.time.LocalDate
import java.time.ZoneId

@Singleton
class BedieningRepository @Inject constructor(
    private val memberRepository: MemberRepository,
    private val reminderDao: FollowUpReminderDao,
    private val memberResolver: MemberGuidResolver
) {
    private val zoneId = ZoneId.systemDefault()

    fun observeVandagAllesItems(): Flow<List<VandagAllesSection>> {
        val today = LocalDate.now(zoneId)
        val startOfTodayUtc = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDayUtc = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        val nowUtc = System.currentTimeMillis()

        // Flow for celebrations
        val celebrationsFlow = flow {
            emit(memberRepository.getCelebrationsForToday())
        }.flowOn(Dispatchers.IO)

        // Overdue: strictly less than start of today
        val overdueFlow = reminderDao.observeOverdue(startOfTodayUtc)
            .map { it.map { toReminderWithMember(it) } }
            .flowOn(Dispatchers.IO)

        // Due Today: strictly between start of today and end of today
        // Filter to ensure NO overlap with overdue
        val dueTodayFlow = reminderDao.observeDueToday(endOfDayUtc, nowUtc)
            .map { list ->
                list.filter { it.dueDateUtc >= startOfTodayUtc && it.dueDateUtc <= endOfDayUtc }
                    .map { toReminderWithMember(it) }
            }
            .flowOn(Dispatchers.IO)

        return combine(
            celebrationsFlow,
            overdueFlow,
            dueTodayFlow
        ) { celebrations, overdue, dueToday ->
            // Deduplicate: create a set of reminder IDs from overdue
            val overdueIds = overdue.map { it.reminder.reminderId }.toSet()

            // Filter dueToday to exclude any IDs already in overdue
            val dueTodayFiltered = dueToday.filter {
                it.reminder.reminderId !in overdueIds
            }

            buildSections(celebrations, overdue, dueTodayFiltered)
        }.flowOn(Dispatchers.IO)
    }

    private fun toReminderWithMember(reminder: FollowUpReminderEntity): ReminderWithMember {
        val member = memberResolver.resolve(reminder.memberGuid)
        return ReminderWithMember(
            reminder = reminder,
            displayName = member?.displayName ?: reminder.memberDisplayNameCache.orEmpty(),
            cellphone = member?.cellphone,
            photoPath = member?.photoPath
        )
    }

    private fun buildSections(
        celebrations: List<VandagAllesItem.Celebration>,
        overdue: List<ReminderWithMember>,
        dueToday: List<ReminderWithMember>
    ): List<VandagAllesSection> {
        val sections = mutableListOf<VandagAllesSection>()

        // Add celebrations section
        if (celebrations.isNotEmpty()) {
            sections.add(VandagAllesSection.Celebrations("🎂 Vieringe", celebrations))
        }

        // Add overdue section (these items have red background)
        if (overdue.isNotEmpty()) {
            val items = overdue.map { VandagAllesItem.Reminder(it) }
            sections.add(VandagAllesSection.Overdue("⚠️ Agterstallig", items))
        }

        // Add due today section (these items have normal background)
        if (dueToday.isNotEmpty()) {
            val items = dueToday.map { VandagAllesItem.Reminder(it) }
            sections.add(VandagAllesSection.DueToday("❤️ Herinnerings Vandag", items))
        }

        return sections
    }
}