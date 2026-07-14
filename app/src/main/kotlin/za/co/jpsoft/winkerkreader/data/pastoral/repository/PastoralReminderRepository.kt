package za.co.jpsoft.winkerkreader.data.pastoral.repository

//import za.co.jpsoft.winkerkreader.utils.GoogleTasksManager
import android.content.Context
import android.util.Log
import androidx.room.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabaseInitializer
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.ReminderTemplateEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.MemberDisplay
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderStatus
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderWithMember
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContext
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateWithSteps
import za.co.jpsoft.winkerkreader.data.pastoral.model.VandagDashboard
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import za.co.jpsoft.winkerkreader.utils.PastoralDatabaseBackup
import za.co.jpsoft.winkerkreader.utils.PastoralTaskScriptManager
import za.co.jpsoft.winkerkreader.utils.ReminderEventBus
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.Utils.toLocalDateSafe
import za.co.jpsoft.winkerkreader.widget.PastoralWidgetProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class PastoralReminderRepository(
    private val appContext: Context,
    private val database: PastoralDatabase,
    private val memberResolver: MemberGuidResolver,
    private val calendarManager: CalendarManager,
    private val settingsManager: SettingsManager
) {

    private val reminderDao = database.followUpReminderDao()
    private val templateDao = database.reminderTemplateDao()
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val TAG = "PastoralReminderRepository"

    @Transaction
    suspend fun createFromTemplate(
        memberGuid: String,
        templateId: String,
        anchorDate: LocalDate,
        customTitle: String? = null,
        contextJson: String? = null,
        // Keys = which steps to actually create a reminder for (an inclusion filter).
        // Values = the due date to use for that step, overriding the anchor-derived
        // date — this is how per-reminder date edits from the preview screen reach here.
        // Null (the default) preserves the old behaviour: every step, anchor-derived dates.
        stepOverrides: Map<String, LocalDate>? = null
    ): List<String> = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) Log.w(TAG, "Creating template reminder")
        val member = try {
            requireMember(memberGuid).also {
                if (it.isArchived && BuildConfig.DEBUG) {
                    Log.w(TAG, "Creating template reminder for archived member $memberGuid")
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "RequireMember fail", e)
            requireMember(memberGuid) // fallback call
        }
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
                dueDateOverride = stepOverrides?.get(step.stepId)
            )
        }

        withContext(NonCancellable) {
            reminderDao.insertAll(reminders)
            ReminderEventBus.notifyReminderChanged()
            // Auto-sync to calendar for all reminders
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "createFromTemplate: about to call syncRemindersToCalendar (NonCancellable)"
            )
            syncRemindersToCalendar(reminders)
            syncRemindersToGoogleTasks(reminders)
            try {
                requestBackup()
                ReminderEventBus.notifyReminderChanged()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Backup failed, but continuing", e)
            }
            PastoralWidgetProvider.refreshWidgets(appContext)
        }

        reminders.map { it.reminderId }
    }

    suspend fun createAdHocReminder(
        memberGuid: String,
        title: String,
        note: String?,
        dueDate: LocalDate,
        scheduleType: ScheduleType,
        dueTime: LocalTime? = null
    ): String = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) Log.w(TAG, "Creating ad-hoc reminder")
        try {
            val member = requireMember(memberGuid)
            if (member.isArchived) {
                if (BuildConfig.DEBUG) Log.w(
                    TAG,
                    "Creating ad-hoc reminder for archived member $memberGuid"
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "RequireMember fail", e)
        }
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
            // Auto-sync to calendar
            syncRemindersToCalendar(listOf(reminder))
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "createAdHocReminder: about to call syncRemindersToCalendar (NonCancellable)"
            )
            syncRemindersToGoogleTasks(listOf(reminder))
            try {
                requestBackup()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Backup failed, but continuing", e)
            }
            PastoralWidgetProvider.refreshWidgets(appContext)
        }
        ReminderEventBus.notifyReminderChanged()
        reminder.reminderId
    }


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
                    isDueOnDate(
                        it.dueDateUtc,
                        bounds.startOfTodayUtc,
                        bounds.endOfDayUtc
                    )
                },
                overdueCount = overdue.size
            )
        }.flowOn(Dispatchers.IO)
    }

    fun observePendingForMember(memberGuid: String): Flow<List<FollowUpReminderEntity>> {
        return reminderDao.observePendingForMember(memberGuid)
    }

    private fun requestBackup() {
        PastoralDatabaseBackup.backupDebounced(appContext)
    }

    private fun requireMember(memberGuid: String): MemberDisplay {
        return memberResolver.resolve(memberGuid)
            ?: throw MemberNotFoundException(memberGuid)
    }

    private fun buildReminderFromStep(
        member: MemberDisplay,
        templateId: String,
        templateSymbol: String?,
        step: TemplateStepEntity,
        anchorDate: LocalDate,
        titleOverride: String?,
        contextJson: String? = null,
        now: Long,
        dueDateOverride: LocalDate? = null
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

        return FollowUpReminderEntity(
            reminderId = UUID.randomUUID().toString(),
            memberGuid = member.guid,
            familyHeadGuid = member.familyHeadGuid,
            templateId = templateId,
            templateStepId = step.stepId,
            symbol = templateSymbol,
            anchorDateUtc = PastoralReminderDates.anchorDateUtc(anchorDate, zoneId),
            title = titleOverride?.trim()?.ifBlank { null } ?: step.defaultTitleAf,
            note = step.defaultNoteAf,
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

    private fun isDueOnDate(dueDateUtc: Long, startOfTodayUtc: Long, endOfDayUtc: Long): Boolean {
        return dueDateUtc in startOfTodayUtc..endOfDayUtc
    }

    private data class DayBounds(
        val nowUtc: Long,
        val startOfTodayUtc: Long,
        val endOfDayUtc: Long
    )

    class MemberNotFoundException(memberGuid: String) :
        IllegalArgumentException("Member not found for GUID: $memberGuid")


    private data class CalendarEventParams(
        val startMillis: Long,
        val endMillis: Long,
        val isAllDay: Boolean
    )

    private fun buildCalendarEventParams(reminder: FollowUpReminderEntity): CalendarEventParams {
        val scheduleType = ScheduleType.fromStored(reminder.scheduleType)
        return when (scheduleType) {
            ScheduleType.DATE_ONLY -> {
                // All-day event: start = midnight of due date, end = next midnight
                val endMillis = reminder.dueDateUtc +
                        TimeUnit.DAYS.toMillis(1)
                CalendarEventParams(
                    startMillis = reminder.dueDateUtc,
                    endMillis = endMillis,
                    isAllDay = true
                )
            }

            ScheduleType.TIMED -> {
                // 1-hour block starting at dueDateUtc; use dueEndUtc if provided
                val endMillis = reminder.dueEndUtc
                    ?: (reminder.dueDateUtc + TimeUnit.HOURS.toMillis(1))
                CalendarEventParams(
                    startMillis = reminder.dueDateUtc,
                    endMillis = endMillis,
                    isAllDay = false
                )
            }
        }
    }

    /**
     * Pushes [reminderId] to the Android calendar selected in settings.
     *
     * - No-ops if calendar sync is disabled in settings.
     * - No-ops if no calendar is selected.
     * - Deduplication is enforced by [CalendarManager.isDuplicatePastoralEvent].
     * - On success, updates [FollowUpReminderEntity.calendarEventId] and
     *   [FollowUpReminderEntity.calendarSynced] in the DB.
     *
     * @return true if a new calendar event was created, false if skipped or failed.
     */
    suspend fun syncToCalendar(reminderId: String): Boolean = withContext(Dispatchers.IO) {
        if (!settingsManager.isPastoralCalendarSyncEnabled()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "syncToCalendar: sync disabled, skipping $reminderId")
            return@withContext false
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "syncToCalendar:  $reminderId")
        val calendarId = settingsManager.getPastoralCalendarId()
            ?: run {
                if (BuildConfig.DEBUG) Log.w(
                    TAG,
                    "syncToCalendar: no pastoral calendar selected, skipping $reminderId"
                )
                return@withContext false
            }

        val reminder = reminderDao.getById(reminderId)
            ?: throw IllegalArgumentException("Reminder not found: $reminderId")

        if (reminder.calendarSynced && reminder.calendarEventId != null) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "syncToCalendar: already synced, eventId=${reminder.calendarEventId}"
            )
            return@withContext false
        }

        if (reminder.status != ReminderStatus.PENDING.name) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "syncToCalendar: reminder $reminderId is not PENDING, skipping"
            )
            return@withContext false
        }

        val displayName = memberResolver.resolve(reminder.memberGuid)?.displayName
            ?: reminder.memberDisplayNameCache.orEmpty()

        val context = TemplateContext.from(reminder.contextJson)
        val (calendarTitle, calendarNote) = buildCalendarEventDetails(
            reminder,
            displayName,
            context
        )

        val params = buildCalendarEventParams(reminder)

        val eventId = calendarManager.addPastoralEvent(
            calendarId = calendarId,
            reminderId = reminderId,
            memberDisplayName = displayName,
            title = calendarTitle,          // ← now customised
            note = calendarNote,            // ← now includes context
            startMillis = params.startMillis,
            endMillis = params.endMillis,
            isAllDay = params.isAllDay
        )



        if (eventId != null) {
            reminderDao.update(
                reminder.copy(
                    calendarEventId = eventId,
                    calendarSynced = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
            requestBackup()
            if (BuildConfig.DEBUG) Log.i(
                TAG,
                "syncToCalendar: created event $eventId for reminder $reminderId"
            )
            true
        } else {
            if (BuildConfig.DEBUG) Log.w(
                TAG,
                "syncToCalendar: addPastoralEvent returned null for $reminderId (duplicate or error)"
            )
            false
        }
    }

    private fun buildCalendarEventDetails(
        reminder: FollowUpReminderEntity,
        displayName: String,
        context: TemplateContext
    ): Pair<String, String> {
        val symbol = reminder.symbol ?: ""
        val titleBase = reminder.title
        val noteBase = reminder.note ?: ""
        val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        val anchorDateStr = reminder.anchorDateUtc.toLocalDateSafe()
            ?.format(dateFormatter) ?: ""

        // Build title based on templateId
        val title = when (reminder.templateId) {
            "sys-NA_STERF" -> {
                val deceasedName = context.getString("deceasedName") ?: ""
                val deceasedDate = context.getDate("deceasedDate")
                    ?.format(dateFormatter) ?: ""
                "$displayName - $symbol$deceasedName${if (deceasedDate.isNotEmpty()) " ($deceasedDate)" else ""}"
            }

            "sys-OPERASIE" -> {
                val hospital = context.getString("hospital") ?: ""
                val anchorDate =
                    reminder.anchorDateUtc.toLocalDateSafe()?.format(dateFormatter) ?: ""
                "$displayName - $symbol $hospital ${if (anchorDate.isNotEmpty()) " ($anchorDate)" else ""}"
            }

            "sys-NUWE_LID" -> {
                val anchorDate = if (reminder.anchorDateUtc != null) {
                    Instant.ofEpochMilli(reminder.anchorDateUtc)
                        .atZone(zoneId).toLocalDate()
                        .format(dateFormatter)
                } else ""
                "$displayName - $symbol ${if (anchorDate.isNotEmpty()) " (Intrek datum: $anchorDate)" else ""}"
            }

            "sys-SIEKTE" -> {
                val illness = context.getString("illness") ?: ""
                "$symbol$displayName - $illness${if (anchorDateStr.isNotEmpty()) " (💊 $anchorDateStr)" else ""}"
            }

            "sys-TRAUMA" -> {
                val traumaType = context.getString("traumaType") ?: ""
                "$symbol$displayName - $traumaType${if (anchorDateStr.isNotEmpty()) " (⚠️ $anchorDateStr)" else ""}"
            }

            else -> {
                // Fallback: symbol + displayName + title
                "$symbol$displayName - $titleBase"
            }
        }

        // Build description: include titleBase, noteBase, and all context details
        val note = buildString {
            append(titleBase)
            if (noteBase.isNotEmpty()) {
                append("\n\n").append(noteBase)
            }
            // Add context details as a structured list
            val contextLines = mutableListOf<String>()
            context.values.forEach { (key, value) ->
                if (value.isNotBlank()) {
                    when (key) {
                        "deceasedName" -> contextLines.add("Oorledene: $value")
                        "deceasedDob" -> contextLines.add("Geboortedatum: $value")
                        "deceasedDate" -> contextLines.add("Sterfdatum: $value")
                        "hospital" -> contextLines.add("Hospitaal: $value")
                        "illness" -> contextLines.add("Siekte: $value")
                        "traumaType" -> contextLines.add("Tipe trauma: $value")
                        "traumaDate" -> contextLines.add("Traumadatum: $value")
                        // Add other keys if you want them in the description
                    }
                }
            }
            if (contextLines.isNotEmpty()) {
                append("\n\n").append(contextLines.joinToString("\n"))
            }
            // Optionally add the step title and due date – but they are already in the event start time.
        }

        return Pair(title, note)
    }

    @Transaction
    suspend fun deleteReminder(reminderId: String) = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            val reminder = reminderDao.getById(reminderId)
                ?: throw IllegalArgumentException("Reminder not found: $reminderId")
            deleteCalendarEventIfSynced(reminder)
            deleteGoogleTaskIfSynced(reminder)
            reminderDao.deleteById(reminderId)
            requestBackup()
            PastoralWidgetProvider.refreshWidgets(appContext)
            ReminderEventBus.notifyReminderChanged()   // <-- ADD THIS
        }
    }

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
                deleteCalendarEventIfSynced(it)
                deleteGoogleTaskIfSynced(it)
            }
            reminderDao.deleteAll(series.map { it.reminderId })
            requestBackup()
            PastoralWidgetProvider.refreshWidgets(appContext)
            ReminderEventBus.notifyReminderChanged()   // <-- ADD THIS
        }
    }

    suspend fun completeReminder(reminderId: String) = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            val reminder = reminderDao.getById(reminderId)
                ?: throw IllegalArgumentException("Reminder not found: $reminderId")

            completeGoogleTaskIfSynced(reminder)

            val now = System.currentTimeMillis()
            reminderDao.update(
                reminder.copy(
                    status = ReminderStatus.COMPLETED.name,
                    completedAtUtc = now,
                    updatedAt = now
                )
            )
            requestBackup()
            PastoralWidgetProvider.refreshWidgets(appContext)
            ReminderEventBus.notifyReminderChanged()   // <-- ADD THIS
        }
    }

    suspend fun snoozeReminder(reminderId: String, until: LocalDateTime) =
        withContext(Dispatchers.IO) {
            val reminder = reminderDao.getById(reminderId)
                ?: throw IllegalArgumentException("Reminder not found: $reminderId")

            deleteCalendarEventIfSynced(reminder)

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
            requestBackup()
            PastoralWidgetProvider.refreshWidgets(appContext)
            ReminderEventBus.notifyReminderChanged()   // <-- ADD THIS
        }

    /**
     * Deletes the linked calendar event if the reminder is synced.
     * Safe to call unconditionally — no-ops if not synced or deletion fails.
     */
    private fun deleteCalendarEventIfSynced(reminder: FollowUpReminderEntity) {
        if (reminder.calendarSynced && reminder.calendarEventId != null) {
            val deleted = calendarManager.deletePastoralEvent(reminder.calendarEventId)
            if (!deleted) {
                // Event may have been deleted from calendar app directly — not an error
                if (BuildConfig.DEBUG) {
                    Log.w(
                        TAG, "Calendar event ${reminder.calendarEventId} not found on delete " +
                                "(already removed externally?)"
                    )
                }
            }
        }
    }

    fun observeDueThisWeek(
        endOfTodayUtc: Long,
        endOfWeekUtc: Long
    ): Flow<List<ReminderWithMember>> {
        return reminderDao.observeDueThisWeek(endOfTodayUtc, endOfWeekUtc)
            .map { reminders -> reminders.map { toReminderWithMember(it) } }
            .flowOn(Dispatchers.IO)
    }

    fun observeFromToday(): Flow<List<ReminderWithMember>> {
        val startOfTodayUtc = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return reminderDao.observeFromToday(startOfTodayUtc)
            .map { reminders -> reminders.map { toReminderWithMember(it) } }
            .flowOn(Dispatchers.IO)
    }

    fun observeTemplates(): Flow<List<TemplateWithSteps>> =
        templateDao.observeTemplatesWithSteps()
            .flowOn(Dispatchers.IO)


    // Add to PastoralReminderRepository.kt

    fun observeAllTemplates(): Flow<List<TemplateWithSteps>> =
        templateDao.observeAllTemplatesWithSteps()
            .flowOn(Dispatchers.IO)

    suspend fun createTemplate(
        titleAf: String,
        descriptionAf: String?
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val templateId = "custom-${UUID.randomUUID()}"
        templateDao.insertTemplate(
            ReminderTemplateEntity(
                templateId = templateId,
                code = templateId,
                titleAf = titleAf.trim(),
                descriptionAf = descriptionAf?.trim()?.ifBlank { null },
                isSystem = false,
                isActive = true,
                sortOrder = templateDao.nextTemplateSortOrder(),
                createdAt = now,
                updatedAt = now
            )
        )
        templateId
    }

    suspend fun updateTemplateMeta(
        templateId: String,
        titleAf: String,
        descriptionAf: String?,
        symbol: String?
    ) = withContext(Dispatchers.IO) {
        val template = templateDao.getTemplateById(templateId)
            ?: throw IllegalArgumentException("Template not found: $templateId")
        templateDao.updateTemplate(
            template.copy(
                titleAf = titleAf.trim(),
                descriptionAf = descriptionAf?.trim()?.ifBlank { null },
                symbol = symbol?.trim()?.ifBlank { null },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Reversible — hides the template from the picker without losing data. */
    suspend fun setTemplateActive(templateId: String, isActive: Boolean) =
        withContext(Dispatchers.IO) {
            templateDao.setActive(templateId, isActive, System.currentTimeMillis())
        }

    /**
     * Permanently removes a template and its steps.
     * @throws IllegalStateException if [templateId] belongs to a system template —
     *         system templates can only be deactivated, never hard-deleted.
     */
    suspend fun deleteTemplatePermanently(templateId: String) = withContext(Dispatchers.IO) {
        val template = templateDao.getTemplateById(templateId)
            ?: return@withContext
        check(!template.isSystem) {
            "System templates cannot be permanently deleted — use setTemplateActive(false) instead"
        }
        templateDao.deleteTemplate(templateId)   // cascades to template_steps via FK
    }

    /**
     * Re-seeds [templateId]'s steps from the original hardcoded definition.
     * Only valid for system templates.
     */
    suspend fun resetTemplateToDefault(templateId: String) = withContext(Dispatchers.IO) {
        val template = templateDao.getTemplateById(templateId)
            ?: throw IllegalArgumentException("Template not found: $templateId")
        check(template.isSystem) { "resetTemplateToDefault is only valid for system templates" }

        val now = System.currentTimeMillis()
        val originalSteps = PastoralDatabaseInitializer.originalStepsFor(template.code, now)
            ?: throw IllegalStateException("No original definition found for code ${template.code}")

        templateDao.deleteAllStepsForTemplate(templateId)
        originalSteps.forEach { templateDao.insertStep(it) }
    }

    suspend fun addStep(
        templateId: String,
        offsetDays: Int,
        offsetMonths: Int,
        defaultTitleAf: String,
        defaultNoteAf: String?,
        scheduleType: ScheduleType,
        defaultHour: Int? = 8,
        defaultMinute: Int? = 0
    ): String = withContext(Dispatchers.IO) {
        val stepId = UUID.randomUUID().toString()
        templateDao.insertStep(
            TemplateStepEntity(
                stepId = stepId,
                templateId = templateId,
                stepOrder = templateDao.nextStepOrder(templateId),
                offsetDays = offsetDays,
                offsetMonths = offsetMonths,
                defaultTitleAf = defaultTitleAf.trim(),
                defaultNoteAf = defaultNoteAf?.trim()?.ifBlank { null },
                scheduleType = scheduleType.name,
                defaultHour = defaultHour,
                defaultMinute = defaultMinute
            )
        )
        stepId
    }

    suspend fun updateStep(step: TemplateStepEntity) = withContext(Dispatchers.IO) {
        templateDao.updateStep(step)
    }

    suspend fun deleteStep(stepId: String) = withContext(Dispatchers.IO) {
        templateDao.deleteStep(stepId)
    }

    /** Persists a new step order after drag-and-drop reordering. */
    suspend fun reorderSteps(orderedSteps: List<TemplateStepEntity>) = withContext(Dispatchers.IO) {
        orderedSteps.forEachIndexed { index, step ->
            templateDao.updateStep(step.copy(stepOrder = index + 1))
        }
    }

    suspend fun getTemplateWithSteps(templateId: String): TemplateWithSteps? =
        withContext(Dispatchers.IO) {
            val template = templateDao.getTemplateById(templateId) ?: return@withContext null
            val steps = templateDao.getStepsForTemplate(templateId)
            TemplateWithSteps(template, steps)
        }

    // -------------------------------------------------------------------------
// Google Tasks via Apps Script
// -------------------------------------------------------------------------
// Inside PastoralReminderRepository.kt

    private suspend fun syncRemindersToGoogleTasks(reminders: List<FollowUpReminderEntity>) {
        // Only auto-sync if mode is API and script is configured
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "syncRemindersToGoogleTasks called with ${reminders.size} reminders"
        )
        if (settingsManager.googleTasksMode() != SettingsManager.GoogleTasksMode.API) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Google Tasks auto-sync disabled (mode != API)")
            return
        }
        if (!settingsManager.isTasksScriptConfigured()) {
            if (BuildConfig.DEBUG) Log.w(
                TAG,
                "Google Tasks script not configured – skipping auto-sync"
            )
            return
        }

        reminders.forEach { reminder ->
            try {
                // Run each sync inside NonCancellable to avoid coroutine cancellation
                withContext(NonCancellable) {
                    syncToGoogleTasksViaScript(reminder.reminderId)
                }
            } catch (e: CancellationException) {
                // Ignore – we are inside NonCancellable
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(
                    TAG,
                    "Auto Google Tasks sync failed for ${reminder.reminderId}",
                    e
                )
            }
        }
    }

    /**
     * Pushes [reminderId] to Google Tasks via the pastor's own Apps Script deployment.
     * No-ops if the script is not configured or the reminder is already synced.
     */
    suspend fun syncToGoogleTasksViaScript(reminderId: String): Boolean =
        withContext(Dispatchers.IO) {
            val url = settingsManager.tasksScriptUrl ?: return@withContext false
            val secret = settingsManager.tasksScriptSecret ?: return@withContext false
            //if (BuildConfig.DEBUG) Log.d(TAG, "URL: $url, Secret: ${secret?.take(4)}…")

            val reminder = reminderDao.getById(reminderId)
                ?: throw IllegalArgumentException("Reminder not found: $reminderId")

            if (url.isNullOrBlank() || secret.isNullOrBlank()) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Script not configured")
                return@withContext false
            }

            if (reminder.googleTaskSynced && reminder.googleTaskId != null) {
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "Already synced to Google Tasks: ${reminder.googleTaskId}"
                )
                return@withContext false
            }

            val displayName = memberResolver.resolve(reminder.memberGuid)?.displayName
                ?: reminder.memberDisplayNameCache.orEmpty()

            // Build a richer notes field
            val noteDetails = buildString {
                append("Lidmaat: $displayName")
                if (!reminder.memberSurname.isNullOrBlank()) {
                    append("\nVan: ${reminder.memberSurname}")
                }
                if (!reminder.memberGivenName.isNullOrBlank()) {
                    append("\nNoemnaam: ${reminder.memberGivenName}")
                }
                append("\nHerinnering: ${reminder.title}")
                if (!reminder.note.isNullOrBlank()) {
                    append("\nNota: ${reminder.note}")
                }
                val dueDateStr = reminder.dueDateUtc.toLocalDateSafe()?.toString() ?: "Onbekend"
                append("\nSperdatum: $dueDateStr")
                // Add context if available
                val context = TemplateContext.from(reminder.contextJson)
                context.values.forEach { (key, value) ->
                    if (value.isNotBlank()) {
                        append("\n$key: $value")
                    }
                }
            }
            val listId = settingsManager.googleTasksListId

            val taskId = PastoralTaskScriptManager.pushTask(
                scriptUrl = url,
                secret = secret,
                title = "$displayName — ${reminder.title}",
                notes = noteDetails,
                dueDateUtc = reminder.dueDateUtc,
                listId = listId
            )

            if (BuildConfig.DEBUG) Log.d(TAG, "pushTask result: $taskId")

            if (taskId != null) {
                reminderDao.update(
                    reminder.copy(
                        googleTaskId = taskId,
                        googleTaskSynced = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                requestBackup()
                if (BuildConfig.DEBUG) Log.i(
                    TAG,
                    "Google Task created $taskId for reminder $reminderId"
                )
                true
            } else {
                false
            }
        }

    /**
     * Deletes the linked Google Task for [reminder] if one was pushed by this app.
     * Non-blocking fire-and-forget — called from IO context by callers.
     */
    private fun deleteGoogleTaskIfSynced(reminder: FollowUpReminderEntity) {
        if (BuildConfig.DEBUG) Log.d(TAG, "deleteGoogleTask")
        if (!reminder.googleTaskSynced || reminder.googleTaskId == null) return
        val url = settingsManager.tasksScriptUrl ?: return
        val secret = settingsManager.tasksScriptSecret ?: return

        val deleted = PastoralTaskScriptManager.deleteTask(url, secret, reminder.googleTaskId)
        if (BuildConfig.DEBUG) Log.d(TAG, "Google Task delete ${reminder.googleTaskId}: $deleted")
    }

    /**
     * Marks the linked Google Task as completed for [reminder].
     */
    private fun completeGoogleTaskIfSynced(reminder: FollowUpReminderEntity) {
        if (BuildConfig.DEBUG) Log.d(TAG, "completeGoogleTask")
        if (!reminder.googleTaskSynced || reminder.googleTaskId == null) return
        val url = settingsManager.tasksScriptUrl ?: return
        val secret = settingsManager.tasksScriptSecret ?: return

        val done = PastoralTaskScriptManager.completeTask(url, secret, reminder.googleTaskId)
        if (BuildConfig.DEBUG) Log.d(TAG, "Google Task complete ${reminder.googleTaskId}: $done")
    }

    private suspend fun syncRemindersToCalendar(reminders: List<FollowUpReminderEntity>) {
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "syncRemindersToCalendar: called with ${reminders.size} reminders"
        )
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "syncRemindersToCalendar: syncEnabled = ${settingsManager.isPastoralCalendarSyncEnabled()}"
        )
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "syncRemindersToCalendar: CalenderID = ${settingsManager.getPastoralCalendarId()}"
        )
        if (!settingsManager.isPastoralCalendarSyncEnabled()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Auto-sync disabled, skipping calendar sync")
            return
        }
        val calendarId = settingsManager.getPastoralCalendarId()
        if (calendarId == null) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Auto-sync enabled but no pastoral calendar selected")
            return
        }
        reminders.forEach { reminder ->
            try {
                // Use NonCancellable to prevent cancellation from interrupting the sync
                withContext(NonCancellable) {
                    syncToCalendar(reminder.reminderId)
                }
            } catch (e: CancellationException) {
                // This won't happen inside NonCancellable, but keep for safety
                if (BuildConfig.DEBUG) Log.d(TAG, "Auto-sync cancelled for ${reminder.reminderId}")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(
                    TAG,
                    "Failed to auto-sync reminder ${reminder.reminderId}",
                    e
                )
            }
        }
    }

    /**
     * Ensures all system templates (hardcoded in [PastoralDatabaseInitializer]) exist.
     * If a system template is missing, it is inserted together with its steps.
     * This runs once per app start (or you can call it from the Application class).
     */
    suspend fun ensureSystemTemplates() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existingTemplates = templateDao.getTemplatesWithSteps()
        val existingIds = existingTemplates.map { it.template.templateId }.toSet()

        val allSystemTemplates = PastoralDatabaseInitializer.buildSystemTemplates(now)
        val missing = allSystemTemplates.filter { it.template.templateId !in existingIds }

        if (missing.isNotEmpty()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Inserting ${missing.size} missing system templates")
            missing.forEach { seed ->
                templateDao.insertTemplate(seed.template)
                templateDao.insertSteps(seed.steps)
            }
            // No need to update meta – these are just data.
        }
    }


    companion object {
        private const val TAG = "PastoralReminderRepository"
        fun create(context: Context): PastoralReminderRepository {
            val appContext = context.applicationContext
            val database = PastoralDatabase.getInstance(appContext)
            return PastoralReminderRepository(
                appContext = appContext,
                database = database,
                memberResolver = CongregationMemberGuidResolver(appContext),
                calendarManager = CalendarManager(appContext),   // ← Sprint B
                settingsManager = SettingsManager(appContext)    // ← Sprint B
            )
        }
    }
}