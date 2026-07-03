package za.co.jpsoft.winkerkreader.utils

import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.models.MainQueryMode
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.BuildConfig
/**
 * Handles filter dialog operations
 */
class FilterHandler(
    private val activity: MainActivity,
    private val viewModel: MemberViewModel
) {

    private var filterList: MutableList<FilterBox> = mutableListOf()
    private var savedSortOrder: String = ""  // Store the original sort order
    private var originalRecordStatus: String = "0"   // ← store original

    fun showFilterDialog() {
        if (BuildConfig.DEBUG) Log.d("FilterHandler", "showFilterDialog called")
        val mainLayout = activity.findViewById<View>(R.id.main_main)
        val filterLayout = activity.findViewById<View>(R.id.main_filter)

        if (mainLayout == null || filterLayout == null) {
            if (BuildConfig.DEBUG) Log.e("FilterHandler", "mainLayout or filterLayout is null!")
            return
        }

        // Save original state
        savedSortOrder = viewModel.sortOrder
        originalRecordStatus = viewModel.recordStatus

        mainLayout.visibility = View.GONE
        filterLayout.visibility = View.VISIBLE
        activity.mainViewModel.setFilterVisible(true)
        setupFilterControls()
    }
    private fun updateActiveButtons(active: Button?, inactive: Button?) {
        when (viewModel.recordStatus) {
            "0" -> {
                active?.setBackgroundResource(R.drawable.aktief0)
                inactive?.setBackgroundResource(R.drawable.onaktief2)
            }
            "2" -> {
                active?.setBackgroundResource(R.drawable.aktief)
                inactive?.setBackgroundResource(R.drawable.onaktief2)
            }
            else -> {
                active?.setBackgroundResource(R.drawable.aktief0)
                inactive?.setBackgroundResource(R.drawable.onaktief2)
            }
        }
    }
    private fun setupFilterControls() {
        // Active/inactive buttons – set initial state from viewModel
        val activeButton = activity.findViewById<Button>(R.id.filter_aktief)
        val inactiveButton = activity.findViewById<Button>(R.id.filter_onaktief)
        updateActiveButtons(activeButton, inactiveButton)

        activeButton?.setOnClickListener {
            viewModel.recordStatus = "0"
            updateActiveButtons(activeButton, inactiveButton)
        }

        inactiveButton?.setOnClickListener {
            viewModel.recordStatus = "2"
            updateActiveButtons(activeButton, inactiveButton)
        }

        // Run and Cancel buttons
        activity.findViewById<Button>(R.id.run_filter2)?.setOnClickListener {
            applyFilters()
        }

        activity.findViewById<Button>(R.id.cancel_filter2)?.setOnClickListener {
            cancelFilter()   // ← now restores everything
        }
    }

    private fun applyFilters() {
        // Clear and collect filters
        filterList.clear()
        addFilterFromUI("Van", R.id.filter_van, R.id.filter_van_opsies, R.id.filter_van_check)
        addFilterFromUI("Noemnaam", R.id.filter_noemnaam, R.id.filter_noemnaam_opsies, R.id.filter_noemnaam_check)
        addFilterFromUI("Nooiensvan", R.id.filter_nooiensvan, R.id.filter_nooiensvan_opsies, R.id.filter_nooiensvan_check)
        addFilterFromUI("Ouderdom", R.id.filter_ouderdom1, R.id.filter_ouderdom2, R.id.filter_ouderdom_opsies, R.id.filter_ouderdom_check)
        addFilterFromUI("Wyk", R.id.filter_wyk, R.id.filter_wyk_opsies, R.id.filter_wyk_check)

        addSpecialFilter("Geslag", R.id.filter_geslag_opsies, R.id.filter_geslag_check)
        addSpecialFilter("Huwelikstatus", R.id.filter_huwelikstatus_opsies, R.id.filter_huwelikstatus_check)
        addSpecialFilter("Lidmaatskap", R.id.filter_lidmaatsakp_opsies, R.id.filter_lidmaatsakapstatus_check)

        addCheckboxFilter("Selfoon", R.id.filter_selfoon)
        addCheckboxFilter("Landlyn", R.id.filter_landlyn)
        addCheckboxFilter("E-pos", R.id.filter_epos)
        addCheckboxFilter("Gesinshoof", R.id.filter_gesinshoof)

        // ✅ Check if any filter is actually active
        val hasActiveFilter = filterList.any { it.checked }
        if (!hasActiveFilter) {
            if (BuildConfig.DEBUG) Log.d("FilterHandler", "No active filters – cancelling")
            cancelFilter()
            return
        }

        // Close dialog and apply filters
        closeFilterDialog()

        val mainActivity = activity as MainActivity
        mainActivity.applyFilterList(ArrayList(filterList))

        // Save original sort order BEFORE updating ViewModel
        val currentSortOrder = viewModel.sortOrder
        if (mainActivity.searchFilterCoordinator.originalLayoutBeforeFilter.isEmpty() &&
            mainActivity.searchFilterCoordinator.originalLayoutBeforeSearch.isEmpty() &&
            currentSortOrder != "Filter" && currentSortOrder != "FILTER_DATA") {
            mainActivity.searchFilterCoordinator.originalLayoutBeforeFilter = currentSortOrder
        }

        viewModel.updateFilter(ArrayList(filterList))
        activity.recomputeBirthdayOffset()
        mainActivity.clearFilterRestoreState()

        //SettingsManager.getInstance(activity).defLayout = "FILTER_DATA"
        val sortOrderView = activity.findViewById<TextView>(R.id.sortorder)
        sortOrderView?.text = "Filter"
        if (BuildConfig.DEBUG) Log.d("FilterHandler", "Filters applied, size=${filterList.size}")
    }

//    private fun cancelFilter() {
//        Log.d("FilterHandler", "cancelFilter called")
//        Toast.makeText(activity, "Cancel clicked", Toast.LENGTH_SHORT).show()
//        filterList.clear()
//        // Delegate to MainActivity's cancelFilter()
//        activity.cancelFilter()
//    }
private fun cancelFilter() {
    if (BuildConfig.DEBUG) Log.d("FilterHandler", "cancelFilter called")
    // Restore original sort order
    val restoreSort = if (activity.searchFilterCoordinator.originalLayoutBeforeFilter.isNotEmpty()) {
        activity.searchFilterCoordinator.originalLayoutBeforeFilter
    } else {
        "VAN"
    }

    // ✅ Restore record status
    viewModel.recordStatus = originalRecordStatus

    // Clear filter state
    filterList.clear()
    activity.searchFilterCoordinator.originalLayoutBeforeFilter = ""
    activity.searchFilterCoordinator.filterList = null

    // Close dialog and show main layout
    closeFilterDialog()
    activity.mainViewModel.setFilterVisible(false)

    // Update sort order – this refreshes data and updates the TextView
    activity.updateSortOrder(restoreSort)
    // Clear filter summary
    viewModel.clearFilterSummary()
}
    private fun addFilterFromUI(fieldName: String, editTextId: Int, spinnerId: Int, checkBoxId: Int) {
        val editText = activity.findViewById<EditText>(editTextId)
        val spinner = activity.findViewById<Spinner>(spinnerId)
        val checkBox = activity.findViewById<CheckBox>(checkBoxId)

        if (editText != null && spinner != null && checkBox != null) {
            filterList.add(
                FilterBox(
                    fieldName,
                    editText.text.toString(),
                    "",
                    spinner.selectedItem?.toString() ?: "",
                    checkBox.isChecked
                )
            )
        }
    }

    private fun addFilterFromUI(fieldName: String, editText1Id: Int, editText2Id: Int, spinnerId: Int, checkBoxId: Int) {
        val editText1 = activity.findViewById<EditText>(editText1Id)
        val editText2 = activity.findViewById<EditText>(editText2Id)
        val spinner = activity.findViewById<Spinner>(spinnerId)
        val checkBox = activity.findViewById<CheckBox>(checkBoxId)

        if (editText1 != null && editText2 != null && spinner != null && checkBox != null) {
            filterList.add(
                FilterBox(
                    fieldName,
                    editText1.text.toString(),
                    editText2.text.toString(),
                    spinner.selectedItem?.toString() ?: "",
                    checkBox.isChecked
                )
            )
        }
    }

    private fun addSpecialFilter(fieldName: String, spinnerId: Int, checkBoxId: Int) {
        val spinner = activity.findViewById<Spinner>(spinnerId)
        val checkBox = activity.findViewById<CheckBox>(checkBoxId)

        if (spinner != null && checkBox != null) {
            filterList.add(
                FilterBox(
                    fieldName,
                    "",
                    "",
                    spinner.selectedItem?.toString() ?: "",
                    checkBox.isChecked
                )
            )
        }
    }

    private fun addCheckboxFilter(fieldName: String, checkBoxId: Int) {
        val checkBox = activity.findViewById<CheckBox>(checkBoxId)

        if (checkBox != null) {
            filterList.add(
                FilterBox(
                    fieldName,
                    "",
                    "",
                    "",
                    checkBox.isChecked
                )
            )
        }
    }

    private fun closeFilterDialog() {
        val mainLayout = activity.findViewById<View>(R.id.main_main)
        val filterLayout = activity.findViewById<View>(R.id.main_filter)
        mainLayout?.visibility = View.VISIBLE
        filterLayout?.visibility = View.GONE
        activity.mainViewModel.setFilterVisible(false)
    }

    fun getFilterList(): MutableList<FilterBox> = filterList
}