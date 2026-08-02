package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.pastoral.dao.FollowUpReminderDao
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs
import java.time.LocalDate
import java.time.ZoneId

@HiltViewModel
class MainViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val memberListPrefs: MemberListPrefs,
    private val congregationPrefs: CongregationPrefs,
    private val followUpReminderDao: FollowUpReminderDao
) : ViewModel() {

    // UI state – transient, not saved
    private val _filterVisible = MutableStateFlow(false)
    val filterVisible: StateFlow<Boolean> = _filterVisible.asStateFlow()

    // Sort order – persisted via SavedStateHandle
    private val _sortOrder =
        savedStateHandle.getStateFlow("sortOrder", memberListPrefs.defLayout)
    val sortOrder: StateFlow<String> = _sortOrder

    // Saved sort order before filter – persisted
    private val _savedSortOrderBeforeFilter =
        savedStateHandle.getStateFlow<String?>("savedSortOrderBeforeFilter", null)
    val savedSortOrderBeforeFilter: StateFlow<String?> = _savedSortOrderBeforeFilter

    // Church name – from injected CongregationPrefs
    private val _churchName = MutableStateFlow(congregationPrefs.gemeenteNaam)
    val churchName: StateFlow<String> = _churchName.asStateFlow()

    // Pending reminder count
    private val _pendingReminderCount = MutableStateFlow(0)
    val pendingReminderCount: StateFlow<Int> = _pendingReminderCount.asStateFlow()

    init {
        loadPendingReminderCount()
    }

    fun setFilterVisible(visible: Boolean) {
        _filterVisible.value = visible
    }

    fun setSortOrder(sortOrder: String) {
        savedStateHandle["sortOrder"] = sortOrder
        memberListPrefs.defLayout = sortOrder
    }

    fun setSavedSortOrderBeforeFilter(sortOrder: String?) {
        savedStateHandle["savedSortOrderBeforeFilter"] = sortOrder
    }

    fun updateChurchName(name: String) {
        _churchName.value = name
    }

    private fun loadPendingReminderCount() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val zoneId = ZoneId.systemDefault()
                val now = System.currentTimeMillis()
                val startOfToday =
                    LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endOfDay = LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant()
                    .toEpochMilli() - 1

                val total = followUpReminderDao.countOverdue(startOfToday) +
                        followUpReminderDao.countDueToday(endOfDay, now)

                withContext(Dispatchers.Main) {
                    _pendingReminderCount.value = total
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(
                    "MainViewModel",
                    "Failed to load pending reminder count",
                    e
                )
            }
        }
    }

    fun refreshPendingReminderCount() {
        loadPendingReminderCount()
    }
}