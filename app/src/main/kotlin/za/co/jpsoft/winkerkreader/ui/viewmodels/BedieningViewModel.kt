package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderWithMember
import za.co.jpsoft.winkerkreader.data.pastoral.model.VandagAllesItem
import za.co.jpsoft.winkerkreader.data.pastoral.model.VandagAllesSection
import za.co.jpsoft.winkerkreader.data.pastoral.repository.BedieningRepository
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import za.co.jpsoft.winkerkreader.utils.EventMessageStore
import za.co.jpsoft.winkerkreader.utils.messaging.WhatsAppMessageSender
import za.co.jpsoft.winkerkreader.utils.prefs.TasksPrefs
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@HiltViewModel
class BedieningViewModel @Inject constructor(
    private val bedieningRepo: BedieningRepository,   // for combined "Alles" tab
    private val pastoralRepo: PastoralReminderRepository, // for reminder-only tab and CRUD
    private val tasksPrefs: TasksPrefs,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // =========================================================================
    // Tab 1: "Vandag (Alles)" - Combined celebrations + reminders
    // =========================================================================

    private val _allesItems = MutableStateFlow<List<VandagAllesSection>>(emptyList())
    val allesItems: StateFlow<List<VandagAllesSection>> = _allesItems.asStateFlow()

    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    init {
        // Load combined data for Tab 1
        viewModelScope.launch {
            bedieningRepo.observeVandagAllesItems()
                .onStart { _loadingState.value = LoadingState.Loading }
                .onEach { sections ->
                    _allesItems.value = sections
                    _loadingState.value = LoadingState.Success
                }
                .catch { e ->
                    _loadingState.value = LoadingState.Error(e.message ?: "Unknown error")
                }
                .collect()
        }
    }

    // =========================================================================
    // Tab 2: "Vandag (Herinnerings)" - Existing reminder-only view
    // =========================================================================

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Use pastoralRepo for reminder flows
    private val dashboard = pastoralRepo.observeVandagDashboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val zoneId = ZoneId.systemDefault()
    private val today = LocalDate.now(zoneId)
    private val startOfTodayUtc = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    private val endOfTodayUtc =
        today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
    private val endOfWeekUtc = today.plusDays(7).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

    private val hierDieWeekItems: Flow<List<ReminderWithMember>> =
        pastoralRepo.observeDueThisWeek(endOfTodayUtc, endOfWeekUtc)
            .flowOn(Dispatchers.IO)

    private val alsItems: Flow<List<ReminderWithMember>> =
        pastoralRepo.observeFromToday()
            .flowOn(Dispatchers.IO)

    // Filter state
    enum class Filter { VANDAG, AGTERSTALLIG, HIERDIE_WEEK, ALS }

    private val _activeFilter = MutableStateFlow(Filter.VANDAG)
    val activeFilter: StateFlow<Filter> = _activeFilter.asStateFlow()

    fun setFilter(filter: Filter) {
        _activeFilter.value = filter
    }

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

    // =========================================================================
    // Actions (delegated to pastoralRepo)
    // =========================================================================

    fun completeReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                pastoralRepo.completeReminder(reminderId)
            } catch (e: Exception) {
                _errorEvent.tryEmit("Kon nie herinnering voltooi nie")
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
                pastoralRepo.snoozeReminder(reminderId, until)
            } catch (e: Exception) {
                _errorEvent.tryEmit("Kon nie herinnering uitstel nie")
            }
        }
    }

    fun addToCalendar(reminderId: String) {
        viewModelScope.launch {
            try {
                pastoralRepo.syncToCalendar(reminderId)
            } catch (e: Exception) {
                _errorEvent.tryEmit("Kon nie by kalender voeg nie")
            }
        }
    }

    fun syncReminderToGoogleTasks(reminderId: String) {
        viewModelScope.launch {
            try {
                val url = tasksPrefs.tasksScriptUrl
                val secret = tasksPrefs.tasksScriptSecret
                if (BuildConfig.DEBUG) Log.d("Tasks", "URL: $url, Secret: $secret")
                val pushed = pastoralRepo.syncToGoogleTasksViaScript(reminderId)
                if (!pushed) {
                    _errorEvent.tryEmit("Taak is reeds gesinkroniseer of Apps Script is nie opgestel nie")
                }
            } catch (e: Exception) {
                _errorEvent.tryEmit("Kon nie taak stuur nie: ${e.message}")
            }
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                pastoralRepo.deleteReminder(reminderId)
            } catch (e: Exception) {
                _errorEvent.tryEmit("Kon nie herinnering verwyder nie")
            }
        }
    }

    fun deleteSeries(reminderId: String) {
        viewModelScope.launch {
            try {
                pastoralRepo.deleteSeries(reminderId)
            } catch (e: Exception) {
                _errorEvent.tryEmit("Kon nie reeks verwyder nie")
            }
        }
    }

    // =========================================================================
    // Tab persistence
    // =========================================================================

    private val _lastViewedTab = MutableStateFlow(savedStateHandle.get<Int>("last_tab") ?: 0)
    val lastViewedTab: StateFlow<Int> = _lastViewedTab.asStateFlow()

    fun saveViewedTab(tabPosition: Int) {
        _lastViewedTab.value = tabPosition
        savedStateHandle["last_tab"] = tabPosition
    }

    // =========================================================================
    // Navigation events (shared)
    // =========================================================================

    fun callMember(memberGuid: String, phoneNumber: String) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
                _navigationEvent.emit(NavigationEvent.Call(intent))
            } catch (e: Exception) {
                _errorEvent.tryEmit("Kon nie bel nie: ${e.message}")
            }
        }
    }

    fun sendSms(memberGuid: String, phoneNumber: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _navigationEvent.emit(NavigationEvent.SendSms(phoneNumber))
        }
    }

    fun addNote(memberGuid: String, memberName: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _navigationEvent.emit(NavigationEvent.OpenNoteDialog(memberGuid, memberName))
        }
    }

    fun setReminder(memberGuid: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _navigationEvent.emit(NavigationEvent.OpenReminderDialog(memberGuid))
        }
    }

    fun sendWhatsApp(
        memberGuid: String,
        phoneNumber: String,
        eventType: VandagAllesItem.CelebrationType,
        memberName: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // Map celebration type to event type string used in EventMessageStore
            val eventKey = when (eventType) {
                VandagAllesItem.CelebrationType.BIRTHDAY -> "Verjaar"
                VandagAllesItem.CelebrationType.BAPTISM -> "Doop"
                VandagAllesItem.CelebrationType.WEDDING -> "Huwelik"
                VandagAllesItem.CelebrationType.DEATH -> null // no template for death
            }
            if (eventKey == null) {
                _errorEvent.tryEmit("Geen boodskap vir hierdie gebeurtenis")
                return@launch
            }

            val prefs = context.getSharedPreferences("VerjaarSmsPrefs", Context.MODE_PRIVATE)
            val template = EventMessageStore.load(prefs, eventKey)
            val personalizedMessage = template.replace("<<<naam>>>", memberName)

            // Send WhatsApp
            val success = WhatsAppMessageSender.send(
                context,  // now using injected context
                phoneNumber,
                method = 1, // or read from preferences if needed
                message = personalizedMessage
            )
            if (!success) {
                _errorEvent.tryEmit("WhatsApp kon nie gestuur word nie")
            }
        }
    }

    fun emitError(message: String) {
        _errorEvent.tryEmit(message)
    }
    // =========================================================================
    // Sealed classes
    // =========================================================================

    sealed class LoadingState {
        object Idle : LoadingState()
        object Loading : LoadingState()
        object Success : LoadingState()
        data class Error(val message: String) : LoadingState()
    }

    sealed class NavigationEvent {
        data class Call(val intent: Intent) : NavigationEvent()
        data class SendSms(val phoneNumber: String) : NavigationEvent()
        data class OpenNoteDialog(val memberGuid: String, val memberName: String) :
            NavigationEvent()

        data class OpenReminderDialog(val memberGuid: String) : NavigationEvent()
    }

    enum class SnoozeOption { TOMORROW, THREE_DAYS, ONE_WEEK }
}