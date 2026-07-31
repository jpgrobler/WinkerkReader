package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderWithMember
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds

class BedieningViewModel(
    private val repository: PastoralReminderRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    // -------------------------------------------------------------------------
    // Filter state
    // -------------------------------------------------------------------------

    enum class Filter { VANDAG, AGTERSTALLIG, HIERDIE_WEEK, ALS }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _activeFilter = MutableStateFlow(Filter.VANDAG)
    val activeFilter: StateFlow<Filter> = _activeFilter.asStateFlow()

    fun setFilter(filter: Filter) {
        _activeFilter.value = filter
    }

    // -------------------------------------------------------------------------
    // Day bounds
    // -------------------------------------------------------------------------

    private val zoneId = ZoneId.systemDefault()
    private val today = LocalDate.now(zoneId)
    private val startOfTodayUtc = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    private val endOfTodayUtc =
        today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
    private val endOfWeekUtc = today.plusDays(7).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
    private val nowUtc get() = System.currentTimeMillis()

    // -------------------------------------------------------------------------
    // Dashboard (infinite flow)
    // -------------------------------------------------------------------------

    private val dashboard = repository.observeVandagDashboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val hierDieWeekItems: Flow<List<ReminderWithMember>> =
        repository.observeDueThisWeek(endOfTodayUtc, endOfWeekUtc)
            .flowOn(kotlinx.coroutines.Dispatchers.IO)

    private val alsItems: Flow<List<ReminderWithMember>> =
        repository.observeFromToday()
            .flowOn(kotlinx.coroutines.Dispatchers.IO)

    val displayItems: StateFlow<List<ReminderWithMember>> = combine(
        _activeFilter,
        dashboard,
        hierDieWeekItems,
        alsItems
    ) { filter, dash, week, als ->
        when (filter) {
            Filter.VANDAG -> dash?.dueToday.orEmpty()
            Filter.AGTERSTALLIG -> dash?.overdue.orEmpty()
            Filter.HIERDIE_WEEK -> week
            Filter.ALS -> als
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayCount: StateFlow<Int> = dashboard
        .map { it?.todayCount ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val overdueCount: StateFlow<Int> = dashboard
        .map { it?.overdueCount ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val tabBadgeCount: StateFlow<Int> = dashboard
        .map { (it?.todayCount ?: 0) + (it?.overdueCount ?: 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val isEmpty: StateFlow<Boolean> = displayItems
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _scrollToReminderId = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val scrollToReminderId: SharedFlow<String> = _scrollToReminderId.asSharedFlow()

    fun requestScrollTo(reminderId: String) {
        _scrollToReminderId.tryEmit(reminderId)
    }

    // -------------------------------------------------------------------------
    // Loading state control
    // -------------------------------------------------------------------------

    init {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Wait for first real emission (skip initial placeholder) with timeout
                withTimeoutOrNull(2000L.milliseconds) {
                    displayItems.drop(1).first()
                }
            } catch (e: Exception) {
                // Ignore – we hide the spinner anyway
            } finally {
                _isLoading.value = false
            }
        }
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    fun completeReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                repository.completeReminder(reminderId)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie herinnering voltooi nie")
            }
        }
    }

    fun snoozeReminder(reminderId: String, snoozeOption: SnoozeOption) {
        val until = when (snoozeOption) {
            SnoozeOption.TOMORROW -> LocalDateTime.now().plusDays(1)
            SnoozeOption.THREE_DAYS -> LocalDateTime.now().plusDays(3)
            SnoozeOption.ONE_WEEK -> LocalDateTime.now().plusDays(7)
        }.withHour(8).withMinute(0).withSecond(0).withNano(0)

        viewModelScope.launch {
            try {
                repository.snoozeReminder(reminderId, until)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie herinnering uitstel nie")
            }
        }
    }

    fun addToCalendar(reminderId: String) {
        viewModelScope.launch {
            try {
                repository.syncToCalendar(reminderId)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie by kalender voeg nie")
            }
        }
    }

    fun syncReminderToGoogleTasks(reminderId: String) {
        viewModelScope.launch {
            try {
                val url = settingsManager.tasks.tasksScriptUrl
                val secret = settingsManager.tasks.tasksScriptSecret
                if (BuildConfig.DEBUG) Log.d("Tasks", "URL: $url, Secret: $secret")
                val pushed = repository.syncToGoogleTasksViaScript(reminderId)
                if (!pushed) {
                    _error.tryEmit("Taak is reeds gesinkroniseer of Apps Script is nie opgestel nie")
                }
            } catch (e: Exception) {
                _error.tryEmit("Kon nie taak stuur nie: ${e.message}")
            }
        }
    }

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    enum class SnoozeOption { TOMORROW, THREE_DAYS, ONE_WEEK }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                repository.deleteReminder(reminderId)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie herinnering verwyder nie")
            }
        }
    }

    fun deleteSeries(reminderId: String) {
        viewModelScope.launch {
            try {
                repository.deleteSeries(reminderId)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie reeks verwyder nie")
            }
        }
    }
}