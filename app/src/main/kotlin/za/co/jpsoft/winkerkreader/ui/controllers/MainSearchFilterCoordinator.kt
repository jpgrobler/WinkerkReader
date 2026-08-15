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
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs

class MainSearchFilterCoordinator(
    private val tag: String,
    private val viewModel: MemberViewModel,
    private val binding: ActivityMainBinding,
    private val memberListAdapter: MemberListAdapter,
    private val memberListPrefs: MemberListPrefs,
    private val congregationPrefs: CongregationPrefs,
    private val findSearchView: () -> SearchView?,
    private val hideFilterPanel: () -> Unit,
    private val onUpdateSortOrder: (String) -> Unit,
    private val onRecomputeBirthdayOffset: () -> Unit,
    // Close button → select all chips
    private val selectAllChips: () -> Unit,
    // Cancel/back → deselect all chips
    private val deselectChips: () -> Unit
) {
    var originalLayoutBeforeSearch: String = ""
    var originalLayoutBeforeFilter: String = ""
    var filterList: ArrayList<FilterBox>? = null
    var onFilterRestored: (() -> Unit)? = null
    var onFilterCancelled: (() -> Unit)? = null

    private fun updateAdapterState() {
        memberListAdapter.updateState(
            listView = memberListPrefs.listView,
            soekList = viewModel.soekList,
            soek = viewModel.soek,
            recordStatus = viewModel.recordStatus,
            sortOrder = viewModel.sortOrder,
            useCongregationIndicator = congregationPrefs.useCongregationIndicator
        )
    }

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
        updateAdapterState()
        onRecomputeBirthdayOffset()
        updateSummaryView()
    }

    fun handleResultCancelled() {
        val restoreSort =
            if (originalLayoutBeforeFilter.isNotEmpty()) originalLayoutBeforeFilter else "VAN"
        viewModel.resetToSort(restoreSort)
        originalLayoutBeforeFilter = ""
        filterList = null
        viewModel.clearFilterSummary()
        refresh()
        updateSummaryView()
    }

    /**
     * Clears search, filters, and resets status to "Aktief",
     * then selects all congregation chips.
     */
    fun resetAllFiltersAndSearch() {
        val restoreSort = if (originalLayoutBeforeFilter.isNotEmpty()) originalLayoutBeforeFilter
        else if (originalLayoutBeforeSearch.isNotEmpty()) originalLayoutBeforeSearch
        else "GESINNE"

        // Clear search and filters
        viewModel.recordStatus = "0"
        viewModel.resetToSort(restoreSort)

        originalLayoutBeforeFilter = ""
        originalLayoutBeforeSearch = ""
        filterList = null
        hideFilterPanel()
        viewModel.clearFilterSummary()

        val searchView = findSearchView()
        searchView?.setQuery("", false)
        searchView?.clearFocus()

        // Select all congregation chips (this will trigger a refresh via its callback)
        selectAllChips()
    }

    fun onSearchClosed() {
        // Restore from search (clears the saved original sort)
        restoreFromSearch(clearOriginalSort = true)

        // Re‑apply filters if they were active before search
        if (originalLayoutBeforeFilter.isNotEmpty() && filterList != null && filterList!!.any { it.checked }) {
            viewModel.updateFilter(filterList!!)
            viewModel.sortOrder = originalLayoutBeforeFilter
            viewModel._eventType.value = viewModel.eventTypeFor(originalLayoutBeforeFilter)
            viewModel.refresh()
            updateAdapterState()
            updateSummaryView()
        }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) {
            // Clear search state without changing sort
            restoreFromSearch(clearOriginalSort = false)
            return
        }

        // First non‑empty query – save the current sort order
        if (originalLayoutBeforeSearch.isEmpty()) {
            originalLayoutBeforeSearch = viewModel.sortOrder
        }
        viewModel.updateSearch(query.trim())
        viewModel.refresh()
        updateAdapterState()
        updateSummaryView()
    }

    fun refresh() {
        viewModel.refresh()
        updateAdapterState()
    }

    fun updateSummaryView() {
        val filterList = viewModel.getCurrentFilterList()
        val isSearchActive = viewModel.soekList && viewModel.soek.isNotEmpty()
        val hasFilters = filterList != null && filterList.any { it.checked }

        val parts = mutableListOf<String>()

        if (isSearchActive) {
            parts.add("Soek: ${viewModel.soek}")
        }

        if (hasFilters) {
            val filterParts = mutableListOf<String>()
            filterList!!.filter { it.checked }.forEach { filter ->
                when {
                    filter.title == "Selfoon" -> filterParts.add("Met Selfoon")
                    filter.title == "Landlyn" -> filterParts.add("Met Landlyn")
                    filter.title == "E-pos" -> filterParts.add("Met E-pos")
                    filter.title == "Gesinshoof" -> filterParts.add("Gesinshoofde")
                    filter.title == "Geslag" -> {
                        val value = when (filter.text3) {
                            "manlik" -> "Manlik"
                            "vroulik" -> "Vroulik"
                            else -> filter.text3
                        }
                        filterParts.add("Geslag: $value")
                    }

                    filter.title == "Huwelikstatus" -> filterParts.add("Huwelik: ${filter.text3}")
                    filter.title == "Lidmaatskap" -> filterParts.add("Lidmaatskap: ${filter.text3}")
                    filter.title == "Ouderdom" -> {
                        when (filter.text3) {
                            "gelyk" -> filterParts.add("Ouderdom: ${filter.text1}")
                            "kleiner as" -> filterParts.add("Ouderdom < ${filter.text1}")
                            "groter as" -> filterParts.add("Ouderdom > ${filter.text1}")
                            "tussen" -> filterParts.add("Ouderdom: ${filter.text1}-${filter.text2}")
                        }
                    }

                    filter.text3 == "leeg" -> filterParts.add("${filter.title} is leeg")
                    filter.text1.isNotEmpty() -> {
                        when (filter.text3) {
                            "gelyk aan" -> filterParts.add("${filter.title}: ${filter.text1}")
                            "nie gelyk aan" -> filterParts.add("${filter.title} ≠ ${filter.text1}")
                            "begin met" -> filterParts.add("${filter.title} begin met ${filter.text1}")
                            "eindig met" -> filterParts.add("${filter.title} eindig met ${filter.text1}")
                        }
                    }
                }
            }
            if (filterParts.isNotEmpty()) {
                parts.add(filterParts.joinToString(" • "))
            }
        }

        val summary = parts.joinToString(" • ")
        if (summary.isNotEmpty()) {
            binding.searchText.text = summary
            binding.searchItemBlock.visibility = View.VISIBLE
            binding.mainSearchTextClose.visibility = View.VISIBLE
            binding.mainSearchTextClose.setOnClickListener {
                resetAllFiltersAndSearch()
            }
        } else {
            binding.searchItemBlock.visibility = View.GONE
            binding.searchText.text = ""
            binding.mainSearchTextClose.visibility = View.GONE
        }
    }

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
        viewModel.clearFilterSummary()
        onUpdateSortOrder(restoreSort)
        viewModel.refresh()
        updateAdapterState()
        onFilterRestored?.invoke()
        updateSummaryView()
    }

    fun cancelAndRestore() {
        if (BuildConfig.DEBUG) Log.d(tag, "cancelAndRestore called")
        val restoreSort = if (originalLayoutBeforeFilter.isNotEmpty()) {
            originalLayoutBeforeFilter
        } else {
            "VAN"
        }
        filterList = null
        viewModel.soekList = false
        viewModel.clearFilterSummary()
        onUpdateSortOrder(restoreSort)
        originalLayoutBeforeFilter = ""
        viewModel.clearFilters()
        viewModel.recordStatus = "0"
        // Deselect all chips (clear congregation filter)
        deselectChips()
        onFilterCancelled?.invoke()
        viewModel.refresh()
        updateAdapterState()
        updateSummaryView()
    }

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
        onUpdateSortOrder(restoreSort)
        originalLayoutBeforeFilter = ""
        filterList = null
        viewModel.clearFilterSummary()
        viewModel.refresh()
        updateAdapterState()
        onRecomputeBirthdayOffset()
        updateSummaryView()
        if (BuildConfig.DEBUG) Log.d(tag, "restoreOriginalState completed, sort=$restoreSort")
    }

    private fun restoreFromSearch(clearOriginalSort: Boolean) {
        if (originalLayoutBeforeSearch.isNotEmpty()) {
            // A real search happened – restore the sort that was active before it
            viewModel.resetToSort(originalLayoutBeforeSearch)
            if (clearOriginalSort) {
                originalLayoutBeforeSearch = ""
            }
        } else {
            // No search was ever performed; just clear search flags without changing sort
            viewModel.soekList = false
            viewModel.soek = ""
        }
        viewModel.refresh()
        updateAdapterState()
        updateSummaryView()
    }
}