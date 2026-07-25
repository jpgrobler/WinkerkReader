package za.co.jpsoft.winkerkreader.ui.controllers

import android.util.Log
import android.view.View
import androidx.appcompat.widget.SearchView
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.models.MainQueryMode
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.SettingsManager

class MainSearchFilterCoordinator(
    private val tag: String,
    private val viewModel: MemberViewModel,
    private val settingsManager: SettingsManager,
    private val binding: ActivityMainBinding,
    private val memberListAdapter: MemberListAdapter,
    private val findSearchView: () -> SearchView?,
    private val hideFilterPanel: () -> Unit,
    // ─── callbacks ─────────────────────────────────────────────
    private val onUpdateSortOrder: (String) -> Unit,
    private val onRecomputeBirthdayOffset: () -> Unit
) {
    var originalLayoutBeforeSearch: String = ""
    var originalLayoutBeforeFilter: String = ""
    var filterList: ArrayList<FilterBox>? = null
    var onFilterRestored: (() -> Unit)? = null
    var onFilterCancelled: (() -> Unit)? = null

    fun applyFilterResult(list: ArrayList<FilterBox>, currentSortOrder: String) {
        if (BuildConfig.DEBUG) Log.d(tag, "applyFilterResult: list size=${list.size}, currentSortOrder=$currentSortOrder")
        filterList = list
        if (originalLayoutBeforeFilter.isEmpty() && originalLayoutBeforeSearch.isEmpty()) {
            if (currentSortOrder != "Filter" && currentSortOrder != "FILTER_DATA") {
                originalLayoutBeforeFilter = currentSortOrder
            }
        }
        viewModel.updateFilter(list)
        viewModel.refresh()
        onRecomputeBirthdayOffset()
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
            onRecomputeBirthdayOffset()
        }
    }

    fun refresh() {
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

    /**
     * Updates the filter summary chip/bar with current active filters.
     */
    fun updateSummaryView() {
        val filterList = viewModel.getCurrentFilterList()
        if (filterList != null && filterList.any { it.checked }) {
            val summary = buildFilterSummary(filterList)
            binding.searchText.text = summary
            binding.searchItemBlock.visibility = View.VISIBLE
            binding.mainSearchTextClose.visibility = View.VISIBLE
            binding.mainSearchTextClose.setOnClickListener {
                clearAndRestore()
            }
        } else {
            binding.searchItemBlock.visibility = View.GONE
            binding.searchText.text = ""
            binding.mainSearchTextClose.visibility = View.GONE
        }
    }

    private fun buildFilterSummary(filterList: ArrayList<FilterBox>): String {
        val parts = mutableListOf<String>()

        val status = when (viewModel.recordStatus) {
            "0" -> "Aktief"
            "2" -> "Onaktief"
            "*" -> "Almal"
            else -> "Aktief"
        }
        parts.add("Status: $status")

        filterList.filter { it.checked }.forEach { filter ->
            when {
                filter.title == "Selfoon" -> parts.add("Met Selfoon")
                filter.title == "Landlyn" -> parts.add("Met Landlyn")
                filter.title == "E-pos" -> parts.add("Met E-pos")
                filter.title == "Gesinshoof" -> parts.add("Gesinshoofde")

                filter.title == "Geslag" -> {
                    val value = when (filter.text3) {
                        "manlik" -> "Manlik"
                        "vroulik" -> "Vroulik"
                        else -> filter.text3
                    }
                    parts.add("Geslag: $value")
                }

                filter.title == "Huwelikstatus" -> parts.add("Huwelik: ${filter.text3}")
                filter.title == "Lidmaatskap" -> parts.add("Lidmaatskap: ${filter.text3}")

                filter.title == "Ouderdom" -> {
                    when (filter.text3) {
                        "gelyk" -> parts.add("Ouderdom: ${filter.text1}")
                        "kleiner as" -> parts.add("Ouderdom < ${filter.text1}")
                        "groter as" -> parts.add("Ouderdom > ${filter.text1}")
                        "tussen" -> parts.add("Ouderdom: ${filter.text1}-${filter.text2}")
                    }
                }

                filter.text3 == "leeg" -> parts.add("${filter.title} is leeg")
                filter.text1.isNotEmpty() -> {
                    when (filter.text3) {
                        "gelyk aan" -> parts.add("${filter.title}: ${filter.text1}")
                        "nie gelyk aan" -> parts.add("${filter.title} ≠ ${filter.text1}")
                        "begin met" -> parts.add("${filter.title} begin met ${filter.text1}")
                        "eindig met" -> parts.add("${filter.title} eindig met ${filter.text1}")
                    }
                }
            }
        }

        return parts.joinToString(" • ")
    }

    /**
     * Clears filters and restores original sort (X on summary).
     */
    fun clearAndRestore() {
        if (BuildConfig.DEBUG) Log.d(tag, "clearAndRestore called")

        val restoreSort = if (originalLayoutBeforeFilter.isNotEmpty()) {
            originalLayoutBeforeFilter
        } else {
            "VAN"
        }

        viewModel.clearFilters()
        viewModel.recordStatus = "0"

        originalLayoutBeforeFilter = ""
        filterList = null

        binding.searchItemBlock.visibility = View.GONE
        binding.searchText.text = ""
        binding.mainSearchTextClose.visibility = View.GONE
        viewModel.clearFilterSummary()

        // Delegate sort update to activity
        onUpdateSortOrder(restoreSort)

        viewModel.refresh()
        onFilterRestored?.invoke()
    }

    /**
     * Cancels filters + search (back button).
     */
    fun cancelAndRestore() {
        if (BuildConfig.DEBUG) Log.d(tag, "cancelAndRestore called")

        val restoreSort = if (originalLayoutBeforeFilter.isNotEmpty()) {
            originalLayoutBeforeFilter
        } else {
            "VAN"
        }

        filterList = null
        viewModel.soekList = false

        binding.searchItemBlock.visibility = View.GONE
        binding.searchText.text = ""
        binding.mainSearchTextClose.visibility = View.GONE
        viewModel.clearFilterSummary()

        // Delegate sort update to activity
        onUpdateSortOrder(restoreSort)

        originalLayoutBeforeFilter = ""
        viewModel.clearFilters()
        viewModel.recordStatus = "0"

        onFilterCancelled?.invoke()
        viewModel.refresh()
    }

    /**
     * Restores original state (Cancel in bottom sheet).
     */
    fun restoreOriginalState(
        originalFilterList: ArrayList<FilterBox>?,
        originalSortOrder: String,
        originalRecordStatus: String
    ) {
        if (BuildConfig.DEBUG) Log.d(tag, "restoreOriginalState called")

        viewModel.updateFilter(originalFilterList ?: ArrayList())
        viewModel.recordStatus = originalRecordStatus

        val restoreSort = if (originalLayoutBeforeFilter.isNotEmpty()) {
            originalLayoutBeforeFilter
        } else {
            originalSortOrder
        }

        // Delegate sort update to activity
        onUpdateSortOrder(restoreSort)

        originalLayoutBeforeFilter = ""
        filterList = null

        binding.searchItemBlock.visibility = View.GONE
        binding.searchText.text = ""
        binding.mainSearchTextClose.visibility = View.GONE
        viewModel.clearFilterSummary()

        viewModel.refresh()
        onRecomputeBirthdayOffset()

        if (BuildConfig.DEBUG) Log.d(tag, "restoreOriginalState completed, sort=$restoreSort")
    }
}