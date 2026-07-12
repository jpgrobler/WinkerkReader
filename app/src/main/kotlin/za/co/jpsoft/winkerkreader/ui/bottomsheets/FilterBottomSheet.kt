package za.co.jpsoft.winkerkreader.ui.bottomsheets

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.databinding.FragmentFilterBinding
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel

class FilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentFilterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MemberViewModel by activityViewModels()
    private val filterList = mutableListOf<FilterBox>()

    // Store original state for reset
    private var originalRecordStatus = "0"
    private var originalFilterList: ArrayList<FilterBox>? = null
    private var originalSortOrder: String = ""  // ✅ ADD THIS - store the sort order

    companion object {
        private const val TAG = "FilterBottomSheet"
    }

    override fun onStart() {
        super.onStart()

        // Make bottom sheet expand to full height and prevent swipe-to-dismiss
        dialog?.let { dialog ->
            if (dialog is BottomSheetDialog) {
                val behavior = dialog.behavior
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = 0
                behavior.isDraggable = false
                behavior.isHideable = false
                behavior.skipCollapsed = true

                // Set a custom height - 90% of screen height
                val displayMetrics = resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels
                val maxHeight = (screenHeight * 0.9).toInt()
                behavior.maxHeight = maxHeight
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Store original state - including current sort order
        originalRecordStatus = viewModel.recordStatus
        originalFilterList = viewModel.getCurrentFilterList()?.let { ArrayList(it) }
        originalSortOrder = viewModel.sortOrder  // ✅ Capture current sort order

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Original sort order captured: $originalSortOrder")
            if (BuildConfig.DEBUG) Log.d(TAG, "Original record status: $originalRecordStatus")
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Original filter list: ${originalFilterList?.size ?: 0} items"
            )
        }

        // Setup AutoCompleteTextViews with adapters
        setupAutoCompleteTextViews()

        // Restore current filter state
        restoreFilterState()

        // Setup listeners
        setupListeners()

        // Apply button
        binding.btnApply.setOnClickListener {
            applyFilters()
            dismiss()
        }

        // Cancel button - restore original state
        binding.btnCancel.setOnClickListener {
            restoreOriginalState()
            dismiss()
        }

        // Reset button - clear all filters
        binding.btnReset.setOnClickListener {
            resetAllFilters()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupAutoCompleteTextViews() {
        // Setup Geslag options: manlik, vroulik
        setupAutoCompleteTextView(
            binding.filterGeslagOpsies,
            R.array.filtergeslagoptions,
            "manlik"
        )

        // Setup Huwelikstatus options: Getroud, Ongetroud, Geskei, Weduwee, Wewenaar, Onbekend
        setupAutoCompleteTextView(
            binding.filterHuwelikstatusOpsies,
            R.array.filterhuwelikstatusoptions,
            "Getroud"
        )

        // Setup Lidmaatskap options: Belydend, Doop, Onbekend
        setupAutoCompleteTextView(
            binding.filterLidmaatskapOpsies,
            R.array.filterlidmaatsakpstatusoptions,
            "Belydend"
        )

        // Setup Text filter options: gelyk aan, nie gelyk aan, begin met, eindig met, leeg
        setupAutoCompleteTextView(
            binding.filterVanOpsies,
            R.array.filtertextoptions,
            "gelyk aan"
        )
        setupAutoCompleteTextView(
            binding.filterNoemnaamOpsies,
            R.array.filtertextoptions,
            "gelyk aan"
        )
        setupAutoCompleteTextView(
            binding.filterNooiensvanOpsies,
            R.array.filtertextoptions,
            "gelyk aan"
        )
        setupAutoCompleteTextView(
            binding.filterWykOpsies,
            R.array.filtertextoptions,
            "gelyk aan"
        )

        // Setup Ouderdom options: gelyk, kleiner as, groter as, tussen
        setupAutoCompleteTextView(
            binding.filterOuderdomOpsies,
            R.array.filterouderdomoptions,
            "gelyk"
        )
    }

    private fun setupAutoCompleteTextView(
        view: MaterialAutoCompleteTextView,
        arrayResId: Int,
        defaultValue: String
    ) {
        val items = resources.getStringArray(arrayResId)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            items
        )
        view.setAdapter(adapter)
        // Set the default value
        view.setText(defaultValue, false)
    }

    private fun restoreFilterState() {
        // 1. Restore status using ChipGroup
        when (viewModel.recordStatus) {
            "0" -> binding.filterStatusAktief.isChecked = true
            "2" -> binding.filterStatusOnaktief.isChecked = true
            "*" -> binding.filterStatusAlmal.isChecked = true
            else -> binding.filterStatusAktief.isChecked = true
        }

        // 2. Restore filters if they exist
        val currentFilters = viewModel.getCurrentFilterList()
        if (currentFilters != null) {
            currentFilters.forEach { filter ->
                when (filter.title) {
                    "Selfoon" -> binding.filterSelfoon.isChecked = filter.checked
                    "Landlyn" -> binding.filterLandlyn.isChecked = filter.checked
                    "E-pos" -> binding.filterEpos.isChecked = filter.checked
                    "Gesinshoof" -> binding.filterGesinshoof.isChecked = filter.checked

                    "Geslag" -> {
                        binding.filterGeslagCheck.isChecked = filter.checked
                        setAutoCompleteText(binding.filterGeslagOpsies, filter.text3)
                    }

                    "Huwelikstatus" -> {
                        binding.filterHuwelikstatusCheck.isChecked = filter.checked
                        setAutoCompleteText(binding.filterHuwelikstatusOpsies, filter.text3)
                    }

                    "Lidmaatskap" -> {
                        binding.filterLidmaatskapCheck.isChecked = filter.checked
                        setAutoCompleteText(binding.filterLidmaatskapOpsies, filter.text3)
                    }

                    "Van" -> {
                        binding.filterVanCheck.isChecked = filter.checked
                        setAutoCompleteText(binding.filterVanOpsies, filter.text3)
                        binding.filterVan.setText(filter.text1)
                    }

                    "Noemnaam" -> {
                        binding.filterNoemnaamCheck.isChecked = filter.checked
                        setAutoCompleteText(binding.filterNoemnaamOpsies, filter.text3)
                        binding.filterNoemnaam.setText(filter.text1)
                    }

                    "Nooiensvan" -> {
                        binding.filterNooiensvanCheck.isChecked = filter.checked
                        setAutoCompleteText(binding.filterNooiensvanOpsies, filter.text3)
                        binding.filterNooiensvan.setText(filter.text1)
                    }

                    "Wyk" -> {
                        binding.filterWykCheck.isChecked = filter.checked
                        setAutoCompleteText(binding.filterWykOpsies, filter.text3)
                        binding.filterWyk.setText(filter.text1)
                    }

                    "Ouderdom" -> {
                        binding.filterOuderdomCheck.isChecked = filter.checked
                        setAutoCompleteText(binding.filterOuderdomOpsies, filter.text3)
                        binding.filterOuderdom1.setText(filter.text1)
                        binding.filterOuderdom2.setText(filter.text2)
                    }
                }
            }
        }
    }

    private fun setAutoCompleteText(view: MaterialAutoCompleteTextView, value: String) {
        val adapter = view.adapter
        if (adapter != null) {
            for (i in 0 until adapter.count) {
                if (adapter.getItem(i).toString() == value) {
                    view.setText(value, false)
                    break
                }
            }
        }
    }

    private fun setupListeners() {
        // Enable/disable related fields when switches are toggled
        binding.filterVanCheck.setOnCheckedChangeListener { _, isChecked ->
            binding.filterVan.isEnabled = isChecked
            binding.filterVanOpsies.isEnabled = isChecked
        }

        binding.filterNoemnaamCheck.setOnCheckedChangeListener { _, isChecked ->
            binding.filterNoemnaam.isEnabled = isChecked
            binding.filterNoemnaamOpsies.isEnabled = isChecked
        }

        binding.filterNooiensvanCheck.setOnCheckedChangeListener { _, isChecked ->
            binding.filterNooiensvan.isEnabled = isChecked
            binding.filterNooiensvanOpsies.isEnabled = isChecked
        }

        binding.filterWykCheck.setOnCheckedChangeListener { _, isChecked ->
            binding.filterWyk.isEnabled = isChecked
            binding.filterWykOpsies.isEnabled = isChecked
        }

        binding.filterOuderdomCheck.setOnCheckedChangeListener { _, isChecked ->
            binding.filterOuderdom1.isEnabled = isChecked
            binding.filterOuderdom2.isEnabled = isChecked
            binding.filterOuderdomOpsies.isEnabled = isChecked
        }

        binding.filterGeslagCheck.setOnCheckedChangeListener { _, isChecked ->
            binding.filterGeslagOpsies.isEnabled = isChecked
        }

        binding.filterHuwelikstatusCheck.setOnCheckedChangeListener { _, isChecked ->
            binding.filterHuwelikstatusOpsies.isEnabled = isChecked
        }

        binding.filterLidmaatskapCheck.setOnCheckedChangeListener { _, isChecked ->
            binding.filterLidmaatskapOpsies.isEnabled = isChecked
        }
    }

    private fun applyFilters() {
        filterList.clear()

        if (BuildConfig.DEBUG) Log.d(TAG, "Applying filters...")

        // 1. Status - get from ChipGroup
        val status = when (binding.filterStatusChipGroup.checkedChipId) {
            R.id.filter_status_aktief -> "0"
            R.id.filter_status_onaktief -> "2"
            R.id.filter_status_almal -> "*"
            else -> "0"
        }
        viewModel.recordStatus = status

        // 2. Simple switch filters
        addSwitchFilter("Selfoon", binding.filterSelfoon)
        addSwitchFilter("Landlyn", binding.filterLandlyn)
        addSwitchFilter("E-pos", binding.filterEpos)
        addSwitchFilter("Gesinshoof", binding.filterGesinshoof)

        // 3. Complex filters with AutoCompleteTextViews
        addComplexFilter(
            "Geslag",
            binding.filterGeslagCheck,
            binding.filterGeslagOpsies.text.toString()
        )

        addComplexFilter(
            "Huwelikstatus",
            binding.filterHuwelikstatusCheck,
            binding.filterHuwelikstatusOpsies.text.toString()
        )

        addComplexFilter(
            "Lidmaatskap",
            binding.filterLidmaatskapCheck,
            binding.filterLidmaatskapOpsies.text.toString()
        )

        // 4. Text filters
        addTextFilter(
            "Van",
            binding.filterVanCheck,
            binding.filterVanOpsies.text.toString(),
            binding.filterVan.text.toString()
        )

        addTextFilter(
            "Noemnaam",
            binding.filterNoemnaamCheck,
            binding.filterNoemnaamOpsies.text.toString(),
            binding.filterNoemnaam.text.toString()
        )

        addTextFilter(
            "Nooiensvan",
            binding.filterNooiensvanCheck,
            binding.filterNooiensvanOpsies.text.toString(),
            binding.filterNooiensvan.text.toString()
        )

        addTextFilter(
            "Wyk",
            binding.filterWykCheck,
            binding.filterWykOpsies.text.toString(),
            binding.filterWyk.text.toString()
        )

        // 5. Ouderdom
        val ouderdomSpinner = binding.filterOuderdomOpsies.text.toString()
        val ouderdom1 = binding.filterOuderdom1.text.toString()
        val ouderdom2 = binding.filterOuderdom2.text.toString()

        if (binding.filterOuderdomCheck.isChecked) {
            filterList.add(
                FilterBox(
                    title = "Ouderdom",
                    text1 = ouderdom1,
                    text2 = ouderdom2,
                    text3 = ouderdomSpinner,
                    checked = true
                )
            )
        }

        // Apply filters to ViewModel
        val hasActiveFilter = filterList.any { it.checked }

        val activity = activity as? MainActivity

        if (hasActiveFilter) {
            val filterArrayList = ArrayList(filterList)

            // Save original layout before filter
            val currentSortOrder = viewModel.sortOrder
            if (activity != null) {
                if (activity.searchFilterCoordinator.originalLayoutBeforeFilter.isEmpty() &&
                    activity.searchFilterCoordinator.originalLayoutBeforeSearch.isEmpty() &&
                    currentSortOrder != "Filter" && currentSortOrder != "FILTER_DATA"
                ) {
                    activity.searchFilterCoordinator.originalLayoutBeforeFilter = currentSortOrder
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Saved original sort order: $currentSortOrder")
                    }
                }
            }

            viewModel.updateFilter(filterArrayList)
            if (BuildConfig.DEBUG) Log.d(TAG, "Applied ${filterArrayList.size} filters")

            viewModel.refresh()

        } else {
            // No active filters - clear everything and restore
            if (BuildConfig.DEBUG) Log.d(TAG, "No active filters - clearing")

            viewModel.updateFilter(ArrayList())
            viewModel.recordStatus = "0"

            if (activity != null && activity.searchFilterCoordinator.originalLayoutBeforeFilter.isNotEmpty()) {
                val restoreSort = activity.searchFilterCoordinator.originalLayoutBeforeFilter
                if (BuildConfig.DEBUG) Log.d(TAG, "Restoring sort order to: $restoreSort")

                activity.updateSortOrder(restoreSort)

                activity.searchFilterCoordinator.originalLayoutBeforeFilter = ""
                activity.searchFilterCoordinator.filterList = null

                activity.binding.searchItemBlock.visibility = View.GONE
                activity.binding.searchText.text = ""
                activity.binding.mainSearchTextClose.visibility = View.GONE
                viewModel.clearFilterSummary()

                activity.memberListAdapter.updateState(
                    listView = activity.settingsManager.listView,
                    soekList = viewModel.soekList,
                    soek = viewModel.soek,
                    recordStatus = viewModel.recordStatus,
                    sortOrder = restoreSort
                )
            }

            viewModel.refresh()
        }

        activity?.updateFilterSummary()
        activity?.recomputeBirthdayOffset()
    }

    private fun addSwitchFilter(title: String, switch: MaterialSwitch) {
        if (switch.isChecked) {
            filterList.add(
                FilterBox(
                    title = title,
                    text1 = "",
                    text2 = "",
                    text3 = "",
                    checked = true
                )
            )
            if (BuildConfig.DEBUG) Log.d(TAG, "Added switch filter: $title")
        }
    }

    private fun addComplexFilter(title: String, switch: MaterialSwitch, value: String) {
        if (switch.isChecked) {
            filterList.add(
                FilterBox(
                    title = title,
                    text1 = "",
                    text2 = "",
                    text3 = value,
                    checked = true
                )
            )
            if (BuildConfig.DEBUG) Log.d(TAG, "Added complex filter: $title = $value")
        }
    }

    private fun addTextFilter(
        title: String,
        switch: MaterialSwitch,
        operator: String,
        value: String
    ) {
        if (switch.isChecked && value.isNotEmpty()) {
            filterList.add(
                FilterBox(
                    title = title,
                    text1 = value,
                    text2 = "",
                    text3 = operator,
                    checked = true
                )
            )
            if (BuildConfig.DEBUG) Log.d(TAG, "Added text filter: $title $operator '$value'")
        } else if (switch.isChecked && value.isEmpty()) {
            filterList.add(
                FilterBox(
                    title = title,
                    text1 = "",
                    text2 = "",
                    text3 = "leeg",
                    checked = true
                )
            )
            if (BuildConfig.DEBUG) Log.d(TAG, "Added empty filter: $title is leeg")
        }
    }

    private fun resetAllFilters() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Resetting all filters")

        // Reset status chip to Aktief
        binding.filterStatusAktief.isChecked = true

        // Reset all switches
        val allSwitches = listOf(
            binding.filterSelfoon,
            binding.filterLandlyn,
            binding.filterEpos,
            binding.filterGesinshoof,
            binding.filterGeslagCheck,
            binding.filterHuwelikstatusCheck,
            binding.filterLidmaatskapCheck,
            binding.filterVanCheck,
            binding.filterNoemnaamCheck,
            binding.filterNooiensvanCheck,
            binding.filterWykCheck,
            binding.filterOuderdomCheck
        )
        allSwitches.forEach { it.isChecked = false }

        // Clear all EditTexts
        listOf(
            binding.filterVan,
            binding.filterNoemnaam,
            binding.filterNooiensvan,
            binding.filterWyk,
            binding.filterOuderdom1,
            binding.filterOuderdom2
        ).forEach { it.text?.clear() }

        // Reset all AutoCompleteTextViews to first item
        resetAutoCompleteTextView(binding.filterGeslagOpsies, "manlik")
        resetAutoCompleteTextView(binding.filterHuwelikstatusOpsies, "Getroud")
        resetAutoCompleteTextView(binding.filterLidmaatskapOpsies, "Belydend")
        resetAutoCompleteTextView(binding.filterVanOpsies, "gelyk aan")
        resetAutoCompleteTextView(binding.filterNoemnaamOpsies, "gelyk aan")
        resetAutoCompleteTextView(binding.filterNooiensvanOpsies, "gelyk aan")
        resetAutoCompleteTextView(binding.filterWykOpsies, "gelyk aan")
        resetAutoCompleteTextView(binding.filterOuderdomOpsies, "gelyk")

        // Update enabled states
        setupListeners()
    }

    private fun resetAutoCompleteTextView(view: MaterialAutoCompleteTextView, defaultText: String) {
        view.setText(defaultText, false)
    }

    /**
     * ✅ FIXED: Restore original state when cancel is pressed
     * This properly handles both cases:
     * 1. Filter was already applied (originalLayoutBeforeFilter has the saved sort)
     * 2. Filter was NEVER applied (use captured originalSortOrder)
     */
    private fun restoreOriginalState() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "restoreOriginalState called")
            if (BuildConfig.DEBUG) Log.d(TAG, "originalSortOrder = $originalSortOrder")
            if (BuildConfig.DEBUG) Log.d(TAG, "originalRecordStatus = $originalRecordStatus")
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "originalFilterList size = ${originalFilterList?.size ?: 0}"
            )
        }

        val activity = activity as? MainActivity

        // ✅ Step 1: Restore the filter list to original (or clear if none)
        if (originalFilterList != null && originalFilterList!!.isNotEmpty()) {
            viewModel.updateFilter(originalFilterList!!)
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Restored filter list with ${originalFilterList!!.size} filters"
            )
        } else {
            viewModel.updateFilter(ArrayList())
            if (BuildConfig.DEBUG) Log.d(TAG, "Cleared filter list (no original filters)")
        }

        // ✅ Step 2: Restore the record status
        viewModel.recordStatus = originalRecordStatus
        if (BuildConfig.DEBUG) Log.d(TAG, "Restored record status: $originalRecordStatus")

        // ✅ Step 3: Restore the sort order - THIS IS THE FIX
        // If originalLayoutBeforeFilter has a value, use it (filter was applied)
        // Otherwise, use the captured originalSortOrder (no filter was ever applied)
        val restoreSort = if (activity != null &&
            activity.searchFilterCoordinator.originalLayoutBeforeFilter.isNotEmpty()
        ) {
            val saved = activity.searchFilterCoordinator.originalLayoutBeforeFilter
            if (BuildConfig.DEBUG) Log.d(TAG, "Using originalLayoutBeforeFilter: $saved")
            saved
        } else {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Using captured originalSortOrder: $originalSortOrder"
            )
            originalSortOrder
        }

        // ✅ Step 4: Apply the restored sort order
        if (activity != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Restoring sort order to: $restoreSort")

            // Update the sort order in MainActivity
            activity.updateSortOrder(restoreSort)

            // Clear the saved state
            activity.searchFilterCoordinator.originalLayoutBeforeFilter = ""
            activity.searchFilterCoordinator.filterList = null
        }

        // ✅ Step 5: Clean up UI
        activity?.let {
            it.binding.searchItemBlock.visibility = View.GONE
            it.binding.searchText.text = ""
            it.binding.mainSearchTextClose.visibility = View.GONE
        }
        viewModel.clearFilterSummary()

        // ✅ Step 6: Update adapter state
        activity?.memberListAdapter?.updateState(
            listView = activity.settingsManager.listView,
            soekList = viewModel.soekList,
            soek = viewModel.soek,
            recordStatus = viewModel.recordStatus,
            sortOrder = restoreSort
        )

        // ✅ Step 7: Refresh the data
        viewModel.refresh()

        // ✅ Step 8: Update UI
        activity?.updateFilterSummary()
        activity?.recomputeBirthdayOffset()

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "restoreOriginalState completed. Sort order: $restoreSort, Status: ${viewModel.recordStatus}"
            )
        }
    }
}