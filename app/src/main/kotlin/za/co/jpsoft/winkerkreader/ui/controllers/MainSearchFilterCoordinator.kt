package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.Context
import android.util.Log
import android.view.View
import androidx.appcompat.widget.SearchView
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.models.MainQueryMode
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.SettingsManager

class MainSearchFilterCoordinator(
    private val tag: String,
    private val context: Context,
    private val viewModel: MemberViewModel,
    private val settingsManager: SettingsManager,
    private val binding: ActivityMainBinding,
    private val memberListAdapter: MemberListAdapter,
    private val findSearchView: () -> SearchView?,
    var onDataChanged: (() -> Unit)? = null,
    private val hideFilterPanel: () -> Unit
) {
    var originalLayoutBeforeSearch: String = ""
    var originalLayoutBeforeFilter: String = ""
    var filterList: ArrayList<FilterBox>? = null

    fun applyFilterResult(list: ArrayList<FilterBox>, currentSortOrder: String) {
        if (BuildConfig.DEBUG) Log.d(
            tag,
            "applyFilterResult: list size=${list.size}, currentSortOrder=$currentSortOrder"
        )
        filterList = list
        if (originalLayoutBeforeFilter.isEmpty() && originalLayoutBeforeSearch.isEmpty()) {
            if (currentSortOrder != "Filter" && currentSortOrder != "FILTER_DATA") {
                originalLayoutBeforeFilter = currentSortOrder
            }
        }
        viewModel.updateFilter(list)
        if (BuildConfig.DEBUG) Log.d(
            tag,
            "After updateFilter: _eventType=${viewModel.getEventType()}, _filterList size=${viewModel.getFilterListSize()}"
        )
    }

    fun handleResultCancelled() {
        val restoreSort =
            if (originalLayoutBeforeFilter.isNotEmpty()) originalLayoutBeforeFilter else "VAN"
        viewModel.resetToSort(restoreSort)
        originalLayoutBeforeFilter = ""
        filterList = null
        viewModel.clearFilterSummary()
        refresh()
    }

    fun resetAllFiltersAndSearch() {
        val restoreSort = if (originalLayoutBeforeFilter.isNotEmpty()) originalLayoutBeforeFilter
        else if (originalLayoutBeforeSearch.isNotEmpty()) originalLayoutBeforeSearch
        else "VAN"
        viewModel.resetToSort(restoreSort)
        originalLayoutBeforeFilter = ""
        originalLayoutBeforeSearch = ""
        filterList = null
        hideFilterPanel()
        binding.searchItemBlock.visibility = View.GONE
        binding.searchText.text = ""
        viewModel.clearFilterSummary()
        val searchView = findSearchView()
        searchView?.setQuery("", false)
        searchView?.clearFocus()
        refresh()
    }

    fun onSearchClosed() {
        val restoreSort =
            if (originalLayoutBeforeSearch.isNotEmpty()) originalLayoutBeforeSearch else "VAN"
        viewModel.resetToSort(restoreSort)
        originalLayoutBeforeSearch = ""
        binding.searchItemBlock.visibility = View.GONE
        binding.searchText.text = ""
        viewModel.clearFilterSummary()
        refresh()
    }

    fun performSearch(query: String) {
        if (query.isBlank()) {
            if (!viewModel.soekList) return
            val restoreSort =
                if (originalLayoutBeforeSearch.isNotEmpty()) originalLayoutBeforeSearch else "VAN"
            viewModel.resetToSort(restoreSort)
            originalLayoutBeforeSearch = ""
            binding.searchItemBlock.visibility = View.GONE
            binding.searchText.text = ""
            viewModel.refresh()
        } else {
            if (originalLayoutBeforeSearch.isEmpty() && originalLayoutBeforeFilter.isEmpty()) {
                originalLayoutBeforeSearch = viewModel.sortOrder
            } else if (originalLayoutBeforeFilter.isNotEmpty() && originalLayoutBeforeSearch.isEmpty()) {
                originalLayoutBeforeSearch = "Filter"
            }
            binding.searchItemBlock.visibility = View.VISIBLE
            val searchText = if (viewModel.recordStatus == "2") "Onaktief $query" else query
            binding.searchText.text = searchText
            viewModel.updateSearch(query.trim())
            (context as? MainActivity)?.recomputeBirthdayOffset()
        }
    }

    fun refresh() {
        binding.searchItemBlock.visibility =
            if (viewModel.soekList && viewModel.soek.isNotEmpty()) View.VISIBLE else View.GONE
        // ❌ removed: binding.sortorder.text = viewModel.sortOrder
        // ❌ removed: binding.sortorder.tag = viewModel.sortOrder
        viewModel.refresh()
    }

    fun resolveQueryMode(layout: String): MainQueryMode = when (layout) {
        "SOEK_DATA" -> MainQueryMode.Search
        "FILTER_DATA" -> MainQueryMode.Filter(filterList ?: arrayListOf())
        "ADRES" -> MainQueryMode.Address
        "GESINNE" -> MainQueryMode.Family
        "HUWELIK" -> MainQueryMode.Wedding
        "OUDERDOM" -> MainQueryMode.Age
        "VAN" -> MainQueryMode.Surname
        "VERJAAR" -> MainQueryMode.Birthday
        "WYK" -> MainQueryMode.Ward
        else -> MainQueryMode.Raw(layout)
    }
}