package za.co.jpsoft.winkerkreader.data.pastoral.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderWithMember
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateWithSteps
import za.co.jpsoft.winkerkreader.data.pastoral.model.VandagDashboard
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import za.co.jpsoft.winkerkreader.utils.prefs.PastoralPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.TasksPrefs
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Facade for all pastoral reminder operations.
 * Delegates to focused internal repositories/managers.
 */
class PastoralReminderRepository(
    private val crud: ReminderCrudRepository,
    private val queries: ReminderQueryRepository,
    private val templates: TemplateRepository,
    private val calendarSync: CalendarSyncManager,
    private val taskSync: GoogleTaskSyncManager
) {

    // ---- CRUD ----

    suspend fun createFromTemplate(
        memberGuid: String,
        templateId: String,
        anchorDate: LocalDate,
        customTitle: String? = null,
        contextJson: String? = null,
        stepOverrides: Map<String, LocalDate>? = null
    ): List<String> = crud.createFromTemplate(
        memberGuid, templateId, anchorDate, customTitle, contextJson, stepOverrides
    )

    suspend fun createAdHocReminder(
        memberGuid: String,
        title: String,
        note: String?,
        dueDate: LocalDate,
        scheduleType: ScheduleType,
        dueTime: LocalTime? = null
    ): String = crud.createAdHocReminder(memberGuid, title, note, dueDate, scheduleType, dueTime)

    suspend fun completeReminder(reminderId: String) = crud.completeReminder(reminderId)

    suspend fun snoozeReminder(reminderId: String, until: LocalDateTime) =
        crud.snoozeReminder(reminderId, until)

    suspend fun deleteReminder(reminderId: String) = crud.deleteReminder(reminderId)

    suspend fun deleteSeries(reminderId: String) = crud.deleteSeries(reminderId)

    // ---- Queries ----

    fun observeVandagDashboard(): Flow<VandagDashboard> = queries.observeVandagDashboard()

    fun observePendingForMember(memberGuid: String): Flow<List<FollowUpReminderEntity>> =
        queries.observePendingForMember(memberGuid)

    fun observeDueThisWeek(
        endOfTodayUtc: Long,
        endOfWeekUtc: Long
    ): Flow<List<ReminderWithMember>> = queries.observeDueThisWeek(endOfTodayUtc, endOfWeekUtc)

    fun observeFromToday(): Flow<List<ReminderWithMember>> = queries.observeFromToday()

    // ---- Templates ----

    fun observeTemplates(): Flow<List<TemplateWithSteps>> = templates.observeTemplates()

    fun observeAllTemplates(): Flow<List<TemplateWithSteps>> = templates.observeAllTemplates()

    suspend fun createTemplate(titleAf: String, descriptionAf: String?): String =
        templates.createTemplate(titleAf, descriptionAf)

    suspend fun updateTemplateMeta(
        templateId: String,
        titleAf: String,
        descriptionAf: String?,
        symbol: String?
    ) = templates.updateTemplateMeta(templateId, titleAf, descriptionAf, symbol)

    suspend fun setTemplateActive(templateId: String, isActive: Boolean) =
        templates.setTemplateActive(templateId, isActive)

    suspend fun deleteTemplatePermanently(templateId: String) =
        templates.deleteTemplatePermanently(templateId)

    suspend fun resetTemplateToDefault(templateId: String) =
        templates.resetTemplateToDefault(templateId)

    suspend fun addStep(
        templateId: String,
        offsetDays: Int,
        offsetMonths: Int,
        defaultTitleAf: String,
        defaultNoteAf: String?,
        scheduleType: ScheduleType,
        defaultHour: Int? = 8,
        defaultMinute: Int? = 0
    ): String = templates.addStep(
        templateId, offsetDays, offsetMonths, defaultTitleAf, defaultNoteAf,
        scheduleType, defaultHour, defaultMinute
    )

    suspend fun updateStep(step: TemplateStepEntity) = templates.updateStep(step)

    suspend fun deleteStep(stepId: String) = templates.deleteStep(stepId)

    suspend fun reorderSteps(orderedSteps: List<TemplateStepEntity>) =
        templates.reorderSteps(orderedSteps)

    suspend fun getTemplateWithSteps(templateId: String): TemplateWithSteps? =
        templates.getTemplateWithSteps(templateId)

    suspend fun ensureSystemTemplates() = templates.ensureSystemTemplates()

    // ---- Calendar ----

    suspend fun syncToCalendar(reminderId: String): Boolean =
        calendarSync.syncToCalendar(reminderId)

    // ---- Google Tasks ----

    suspend fun syncToGoogleTasksViaScript(reminderId: String): Boolean =
        taskSync.syncToGoogleTasksViaScript(reminderId)

    suspend fun getReminderById(reminderId: String): FollowUpReminderEntity? =
        crud.getReminderById(reminderId)

    // ---- Companion factory ----

    companion object {
        private const val TAG = "PastoralReminderRepository"

        /**
         * Factory method – now accepts the required preference slices directly.
         * Callers must provide [pastoralPrefs] and [tasksPrefs] (available via Hilt injection).
         *
         * This eliminates the dependency on [SettingsManager] and makes the repository
         * testable with custom pref implementations.
         */
        fun create(
            context: Context,
            pastoralPrefs: PastoralPrefs,
            tasksPrefs: TasksPrefs,
            backupHelper: ReminderBackupHelper
        ): PastoralReminderRepository {
            val appContext = context.applicationContext
            val database = PastoralDatabase.getInstance(appContext)
            val calendarManager = CalendarManager(appContext)
            val memberResolver = CongregationMemberGuidResolver(appContext)
            val reminderDao = database.followUpReminderDao()

            val calendarSync = CalendarSyncManager(
                calendarManager,
                pastoralPrefs,
                reminderDao,
                memberResolver
            )

            val taskSync = GoogleTaskSyncManager(
                tasksPrefs,
                reminderDao,
                memberResolver
            )

            val crud = ReminderCrudRepository(
                database,
                memberResolver,
                calendarSync,
                taskSync,
                backupHelper   // <-- pass here
            )

            val queries = ReminderQueryRepository(database, memberResolver)
            val templates = TemplateRepository(database)

            return PastoralReminderRepository(crud, queries, templates, calendarSync, taskSync)
        }
    }

    // ---- Kept for binary compatibility ----

    class MemberNotFoundException(memberGuid: String) :
        IllegalArgumentException("Member not found for GUID: $memberGuid")
}