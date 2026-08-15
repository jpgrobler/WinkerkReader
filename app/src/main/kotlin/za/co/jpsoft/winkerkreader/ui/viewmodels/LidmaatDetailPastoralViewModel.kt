package za.co.jpsoft.winkerkreader.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.FamilyMember
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateWithSteps
import za.co.jpsoft.winkerkreader.data.pastoral.repository.FamilyMemberRepository
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import java.time.LocalDate
import java.time.LocalTime

class LidmaatDetailPastoralViewModel(
    private val familyRepo: FamilyMemberRepository,
    private val repository: PastoralReminderRepository,
    val memberGuid: String
) : ViewModel() {

    // ── Pending reminders ────────────────────────────────────────────────────
    val pendingReminders: StateFlow<List<FollowUpReminderEntity>> =
        repository.observePendingForMember(memberGuid)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> = pendingReminders
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Templates ────────────────────────────────────────────────────────────
    val templates: StateFlow<List<TemplateWithSteps>> =
        repository.observeTemplates()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Events ──────────────────────────────────────────────────────────────
    private val _created = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val created: SharedFlow<Int> = _created.asSharedFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    private val _familyMembers = MutableStateFlow<List<FamilyMember>>(emptyList())
    val familyMembers: StateFlow<List<FamilyMember>> = _familyMembers

    fun loadFamilyMembers(memberGuid: String, familyHeadGuid: String?) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                familyRepo.getFamilyMembers(memberGuid, familyHeadGuid)
            }
            _familyMembers.value = list
        }
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    fun createFromTemplate(
        templateId: String,
        anchorDate: LocalDate,
        selectedItems: List<PreviewItem>,
        contextJson: String? = null,
        note: String? = null
    ) {
        val selectedDates = selectedItems
            .filter { it.isSelected }
            .associate { it.stepId to it.dueDate }

        if (selectedDates.isEmpty()) {
            _error.tryEmit("Geen stappe gekies nie")
            return
        }

        viewModelScope.launch {
            try {
                val ids = repository.createFromTemplate(
                    memberGuid = memberGuid,
                    templateId = templateId,
                    anchorDate = anchorDate,
                    contextJson = contextJson,
                    stepOverrides = selectedDates,
                    customNote = note
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
                    memberGuid = memberGuid,
                    title = title,
                    note = note,
                    dueDate = dueDate,
                    scheduleType = scheduleType,
                    dueTime = dueTime
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
            } catch (_: Exception) {
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
                val past = date.isBefore(LocalDate.now())
                PreviewItem(
                    stepId = step.stepId,
                    stepTitle = step.defaultTitleAf,
                    dueDate = date,
                    isInPast = past,
                    // Reminders that would already be overdue by the time they're created
                    // (e.g. a "day before" step when the anchor event was reported late)
                    // start deselected — the user opts back in if they still want it.
                    isSelected = !past
                )
            }
    }

    suspend fun getReminderById(reminderId: String): FollowUpReminderEntity? =
        repository.getReminderById(reminderId)

    data class PreviewItem(
        val stepId: String,
        val stepTitle: String,
        val dueDate: LocalDate,
        val isInPast: Boolean,
        val isSelected: Boolean
    )
}