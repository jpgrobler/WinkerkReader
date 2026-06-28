package za.co.jpsoft.winkerkreader.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateWithSteps
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import java.time.LocalDate
import java.time.LocalTime

class LidmaatDetailPastoralViewModel(
    private val repository: PastoralReminderRepository,
    val memberGuid: String
) : ViewModel() {

    // -------------------------------------------------------------------------
    // Pending reminders for this member (drives mini-list)
    // -------------------------------------------------------------------------

    val pendingReminders: StateFlow<List<FollowUpReminderEntity>> =
        repository.observePendingForMember(memberGuid)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> = pendingReminders
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // -------------------------------------------------------------------------
    // Available templates (drives template picker in BottomSheet)
    // -------------------------------------------------------------------------

    val templates: StateFlow<List<TemplateWithSteps>> =
        repository.observeTemplates()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // -------------------------------------------------------------------------
    // One-shot events
    // -------------------------------------------------------------------------

    private val _created = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    /** Emits the count of reminders just created — used for Toast confirmation. */
    val created: SharedFlow<Int> = _created.asSharedFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    fun createFromTemplate(templateId: String, anchorDate: LocalDate,
                           contextJson: String? = null) {
        viewModelScope.launch {
            try {
                val ids = repository.createFromTemplate(
                    memberGuid  = memberGuid,
                    templateId  = templateId,
                    anchorDate  = anchorDate,
                    contextJson = contextJson
                )
                _created.tryEmit(ids.size)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie herinnerings stel nie: ${e.message}")
            }
        }
    }

    fun createAdHoc(
        title: String,
        note: String?,
        dueDate: LocalDate,
        scheduleType: ScheduleType,
        dueTime: LocalTime? = null
    ) {
        viewModelScope.launch {
            try {
                repository.createAdHocReminder(
                    memberGuid   = memberGuid,
                    title        = title,
                    note         = note,
                    dueDate      = dueDate,
                    scheduleType = scheduleType,
                    dueTime      = dueTime
                )
                _created.tryEmit(1)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie herinnering stel nie: ${e.message}")
            }
        }
    }

    fun completeReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                repository.completeReminder(reminderId)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie herinnering voltooi nie")
            }
        }
    }

    /**
     * Previews what dates a template will produce for [anchorDate].
     * Pure computation — no DB access.
     */
    fun previewTemplateDates(templateId: String, anchorDate: LocalDate): List<PreviewItem> {
        val template = templates.value.find { it.template.templateId == templateId }
            ?: return emptyList()
        return template.steps
            .sortedBy { it.stepOrder }
            .map { step ->
                val date = anchorDate
                    .plusMonths(step.offsetMonths.toLong())
                    .plusDays(step.offsetDays.toLong())
                PreviewItem(
                    stepTitle = step.defaultTitleAf,
                    dueDate   = date,
                    isInPast  = date.isBefore(LocalDate.now())
                )
            }
    }

    data class PreviewItem(
        val stepTitle: String,
        val dueDate: LocalDate,
        val isInPast: Boolean
    )
}