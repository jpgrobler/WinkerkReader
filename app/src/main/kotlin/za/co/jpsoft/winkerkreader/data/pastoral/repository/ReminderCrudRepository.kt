package za.co.jpsoft.winkerkreader.data.pastoral.repository

import android.util.Log
import androidx.room.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.MemberDisplay
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderStatus
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.utils.ReminderEventBus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Handles all write operations for pastoral reminders: create, update, complete, snooze, delete.
 */
class ReminderCrudRepository(
    private val database: PastoralDatabase,
    private val memberResolver: MemberGuidResolver,
    private val calendarSync: CalendarSyncManager,
    private val taskSync: GoogleTaskSyncManager,
    private val backupHelper: ReminderBackupHelper
) {
    private val reminderDao = database.followUpReminderDao()
    private val templateDao = database.reminderTemplateDao()
    private val zoneId = ZoneId.systemDefault()
    private val TAG = "ReminderCrudRepo"

    /**
     * Create a series of reminders from a template.
     */
    @Transaction
    suspend fun createFromTemplate(
        memberGuid: String,
        templateId: String,
        anchorDate: LocalDate,
        customTitle: String? = null,
        contextJson: String? = null,
        stepOverrides: Map<String, LocalDate>? = null,
        customNote: String? = null
    ): List<String> = withContext(Dispatchers.IO) {
        val member = requireMember(memberGuid)
        val template = templateDao.getTemplateById(templateId)
            ?: throw IllegalArgumentException("Template not found: $templateId")
        var steps = templateDao.getStepsForTemplate(templateId)
        if (steps.isEmpty()) {
            throw IllegalArgumentException("Template has no steps: $templateId")
        }
        if (stepOverrides != null) {
            steps = steps.filter { stepOverrides.containsKey(it.stepId) }
            if (steps.isEmpty()) {
                throw IllegalArgumentException("Geen stappe gekies nie")
            }
        }
        val now = System.currentTimeMillis()
        val reminders = steps.map { step ->
            buildReminderFromStep(
                member = member,
                templateId = templateId,
                templateSymbol = template.symbol,
                step = step,
                anchorDate = anchorDate,
                titleOverride = customTitle,
                contextJson = contextJson,
                now = now,
                dueDateOverride = stepOverrides?.get(step.stepId),
                customNote = customNote
            )
        }

        withContext(NonCancellable) {
            reminderDao.insertAll(reminders)
            ReminderEventBus.notifyReminderChanged()
            calendarSync.syncRemindersToCalendar(reminders)
            taskSync.syncRemindersToGoogleTasks(reminders)
            backupHelper.requestBackupAndRefresh()
        }

        reminders.map { it.reminderId }
    }

    /**
     * Create a single ad‑hoc reminder.
     */
    suspend fun createAdHocReminder(
        memberGuid: String,
        title: String,
        note: String?,
        dueDate: LocalDate,
        scheduleType: ScheduleType,
        dueTime: LocalTime? = null
    ): String = withContext(Dispatchers.IO) {
        val member = requireMember(memberGuid)
        val now = System.currentTimeMillis()
        val dueDateUtc = PastoralReminderDates.toDueDateUtc(
            dueDate = dueDate,
            scheduleType = scheduleType,
            dueTime = dueTime,
            defaultHour = 8,
            defaultMinute = 0,
            zoneId = zoneId
        )
        val reminder = FollowUpReminderEntity(
            reminderId = UUID.randomUUID().toString(),
            memberGuid = member.guid,
            familyHeadGuid = member.familyHeadGuid,
            templateId = null,
            templateStepId = null,
            anchorDateUtc = null,
            title = title.trim(),
            note = note?.trim()?.ifBlank { null },
            scheduleType = scheduleType.name,
            dueDateUtc = dueDateUtc,
            dueEndUtc = null,
            status = ReminderStatus.PENDING.name,
            completedAtUtc = null,
            snoozedUntilUtc = null,
            lastNotifiedDateUtc = null,
            calendarEventId = null,
            calendarSynced = false,
            memberDisplayNameCache = member.displayName,
            memberSurname = member.surname,
            memberGivenName = member.givenName,
            createdAt = now,
            updatedAt = now
        )

        withContext(NonCancellable) {
            reminderDao.insert(reminder)
            calendarSync.syncRemindersToCalendar(listOf(reminder))
            taskSync.syncRemindersToGoogleTasks(listOf(reminder))
            backupHelper.requestBackupAndRefresh()
        }
        ReminderEventBus.notifyReminderChanged()
        reminder.reminderId
    }

    /**
     * Complete a reminder (mark as COMPLETED).
     */
    suspend fun completeReminder(reminderId: String) = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            val reminder = reminderDao.getById(reminderId)
                ?: throw IllegalArgumentException("Reminder not found: $reminderId")
            taskSync.completeGoogleTaskIfSynced(reminder)
            val now = System.currentTimeMillis()
            reminderDao.update(
                reminder.copy(
                    status = ReminderStatus.COMPLETED.name,
                    completedAtUtc = now,
                    updatedAt = now
                )
            )
            backupHelper.requestBackupAndRefresh()
        }
    }

    /**
     * Snooze a reminder to a specific date/time.
     */
    suspend fun snoozeReminder(reminderId: String, until: LocalDateTime) =
        withContext(Dispatchers.IO) {
            val reminder = reminderDao.getById(reminderId)
                ?: throw IllegalArgumentException("Reminder not found: $reminderId")
            calendarSync.deleteCalendarEventIfSynced(reminder)
            val snoozedUntilUtc = until.atZone(zoneId).toInstant().toEpochMilli()
            reminderDao.update(
                reminder.copy(
                    snoozedUntilUtc = snoozedUntilUtc,
                    lastNotifiedDateUtc = null,
                    calendarEventId = null,
                    calendarSynced = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
            backupHelper.requestBackupAndRefresh()
        }

    /**
     * Delete a single reminder (permanently).
     */
    @Transaction
    suspend fun deleteReminder(reminderId: String) = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            val reminder = reminderDao.getById(reminderId)
                ?: throw IllegalArgumentException("Reminder not found: $reminderId")
            calendarSync.deleteCalendarEventIfSynced(reminder)
            taskSync.deleteGoogleTaskIfSynced(reminder)
            reminderDao.deleteById(reminderId)
            backupHelper.requestBackupAndRefresh()
        }
    }

    /**
     * Delete an entire series (all reminders sharing the same template and anchor).
     */
    @Transaction
    suspend fun deleteSeries(reminderId: String) = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            val reminder = reminderDao.getById(reminderId)
                ?: throw IllegalArgumentException("Reminder not found: $reminderId")
            if (reminder.templateId == null || reminder.anchorDateUtc == null) {
                deleteReminder(reminderId)
                return@withContext
            }
            val series = reminderDao.getSeries(
                memberGuid = reminder.memberGuid,
                templateId = reminder.templateId,
                anchorDateUtc = reminder.anchorDateUtc
            )
            series.forEach {
                calendarSync.deleteCalendarEventIfSynced(it)
                taskSync.deleteGoogleTaskIfSynced(it)
            }
            reminderDao.deleteAll(series.map { it.reminderId })
            backupHelper.requestBackupAndRefresh()
        }
    }

    // ------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------

    private suspend fun requireMember(memberGuid: String): MemberDisplay {
        return memberResolver.resolve(memberGuid)
            ?: throw PastoralReminderRepository.MemberNotFoundException(memberGuid)
    }

    private fun buildReminderFromStep(
        member: MemberDisplay,
        templateId: String,
        templateSymbol: String?,
        step: TemplateStepEntity,
        anchorDate: LocalDate,
        titleOverride: String?,
        contextJson: String?,
        now: Long,
        dueDateOverride: LocalDate? = null,
        customNote: String? = null
    ): FollowUpReminderEntity {
        val dueDate = dueDateOverride ?: PastoralReminderDates.expandDueDate(anchorDate, step)
        val scheduleType = ScheduleType.fromStored(step.scheduleType)
        val dueDateUtc = PastoralReminderDates.toDueDateUtc(
            dueDate = dueDate,
            scheduleType = scheduleType,
            dueTime = null,
            defaultHour = step.defaultHour,
            defaultMinute = step.defaultMinute,
            zoneId = zoneId
        )
        val note = customNote?.takeIf { it.isNotBlank() } ?: step.defaultNoteAf
        return FollowUpReminderEntity(
            reminderId = UUID.randomUUID().toString(),
            memberGuid = member.guid,
            familyHeadGuid = member.familyHeadGuid,
            templateId = templateId,
            templateStepId = step.stepId,
            symbol = templateSymbol,
            anchorDateUtc = PastoralReminderDates.anchorDateUtc(anchorDate, zoneId),
            title = titleOverride?.trim()?.ifBlank { null } ?: step.defaultTitleAf,
            note = note,
            contextJson = contextJson,
            scheduleType = scheduleType.name,
            dueDateUtc = dueDateUtc,
            dueEndUtc = null,
            status = ReminderStatus.PENDING.name,
            completedAtUtc = null,
            snoozedUntilUtc = null,
            lastNotifiedDateUtc = null,
            calendarEventId = null,
            calendarSynced = false,
            memberDisplayNameCache = member.displayName,
            memberSurname = member.surname,
            memberGivenName = member.givenName,
            createdAt = now,
            updatedAt = now
        )
    }
    suspend fun getReminderById(reminderId: String): FollowUpReminderEntity? =
        reminderDao.getById(reminderId)
}