// File: ui/viewmodels/MainViewModel.kt
package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import java.time.LocalDate
import java.time.ZoneId

class MainViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {  // ← extend AndroidViewModel

    private val settingsManager = SettingsManager.getInstance(application)  // ← get from app

    // UI state – transient, not saved
    private val _filterVisible = MutableStateFlow(false)
    val filterVisible: StateFlow<Boolean> = _filterVisible.asStateFlow()

    // Sort order – persisted via SavedStateHandle
    private val _sortOrder = savedStateHandle.getStateFlow("sortOrder", settingsManager.defLayout)
    val sortOrder: StateFlow<String> = _sortOrder

    // Saved sort order before filter – persisted
    private val _savedSortOrderBeforeFilter = savedStateHandle.getStateFlow<String?>("savedSortOrderBeforeFilter", null)
    val savedSortOrderBeforeFilter: StateFlow<String?> = _savedSortOrderBeforeFilter

    // Church name – from SettingsManager
    private val _churchName = MutableStateFlow(settingsManager.gemeenteNaam)
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
        settingsManager.defLayout = sortOrder
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
                val db = PastoralDatabase.getInstance(getApplication())
                val zoneId = ZoneId.systemDefault()
                val now = System.currentTimeMillis()
                val startOfToday = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endOfDay = LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

                val total = db.followUpReminderDao().countOverdue(startOfToday) +
                        db.followUpReminderDao().countDueToday(endOfDay, now)

                withContext(Dispatchers.Main) {
                    _pendingReminderCount.value = total
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("MainViewModel", "Failed to load pending reminder count", e)
            }
        }
    }

    fun refreshPendingReminderCount() {
        loadPendingReminderCount()
    }
}