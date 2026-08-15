package za.co.jpsoft.winkerkreader.ui.bottomsheets

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.pastoral.dao.FollowUpReminderDao
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralNoteRepository
import za.co.jpsoft.winkerkreader.databinding.FragmentFilterBinding
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel

@AndroidEntryPoint
class FilterBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var pastoralNoteRepository: PastoralNoteRepository

    @Inject
    lateinit var followUpReminderDao: FollowUpReminderDao

    private var _binding: FragmentFilterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MemberViewModel by activityViewModels()

    // Store original state for cancel
    private var originalRecordStatus = "0"
    private var originalFilterList: ArrayList<FilterBox>? = null
    private var originalSortOrder: String = ""

    companion object {
        private const val TAG = "FilterBottomSheet"
    }

    override fun onStart() {
        super.onStart()

        // Expand bottom sheet and prevent swipe-to-dismiss
        dialog?.let { dialog ->
            if (dialog is BottomSheetDialog) {
                val behavior = dialog.behavior
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = 0
                behavior.isDraggable = false
                behavior.isHideable = false
                behavior.skipCollapsed = true

                val displayMetrics = resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels
                behavior.maxHeight = (screenHeight * 0.9).toInt()
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

        // Capture original state before any changes
        originalRecordStatus = viewModel.recordStatus
        originalFilterList = viewModel.getCurrentFilterList()?.let { ArrayList(it) }
        originalSortOrder = viewModel.sortOrder

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Original sort order: $originalSortOrder")
            Log.d(TAG, "Original record status: $originalRecordStatus")
            Log.d(TAG, "Original filter list size: ${originalFilterList?.size ?: 0}")
        }

        setupAutoCompleteTextViews()
        restoreFilterState()
        setupListeners()

        // Apply button
        binding.btnApply.setOnClickListener {
            applyFilters()
            dismiss()
        }

        // Cancel button – revert to original state
        binding.btnCancel.setOnClickListener {
            restoreOriginalState()
            dismiss()
        }

        // Reset button – clear all filters within the sheet (does not apply)
        binding.btnReset.setOnClickListener {
            resetAllFilters()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // -------------------------------------------------------------------------
    // UI Setup
    // -------------------------------------------------------------------------

    private fun setupAutoCompleteTextViews() {
        setupAutoCompleteTextView(binding.filterGeslagOpsies, R.array.filtergeslagoptions, "manlik")
        setupAutoCompleteTextView(binding.filterHuwelikstatusOpsies, R.array.filterhuwelikstatusoptions, "Getroud")
        setupAutoCompleteTextView(binding.filterLidmaatskapOpsies, R.array.filterlidmaatsakpstatusoptions, "Belydend")
        setupAutoCompleteTextView(binding.filterVanOpsies, R.array.filtertextoptions, "gelyk aan")
        setupAutoCompleteTextView(binding.filterNoemnaamOpsies, R.array.filtertextoptions, "gelyk aan")
        setupAutoCompleteTextView(binding.filterNooiensvanOpsies, R.array.filtertextoptions, "gelyk aan")
        setupAutoCompleteTextView(binding.filterWykOpsies, R.array.filtertextoptions, "gelyk aan")
        setupAutoCompleteTextView(binding.filterOuderdomOpsies, R.array.filterouderdomoptions, "gelyk")
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
        view.setText(defaultValue, false)
    }

    private fun restoreFilterState() {
        // Record status chips
        when (viewModel.recordStatus) {
            "0" -> binding.filterStatusAktief.isChecked = true
            "2" -> binding.filterStatusOnaktief.isChecked = true
            "*" -> binding.filterStatusAlmal.isChecked = true
            else -> binding.filterStatusAktief.isChecked = true
        }

        // Existing filters
        val currentFilters = viewModel.getCurrentFilterList()
        currentFilters?.forEach { filter ->
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
                "Note" -> binding.filterNota.isChecked = filter.checked
                "Reminder" -> binding.filterHerinnering.isChecked = filter.checked
            }
        }
    }

    private fun setAutoCompleteText(view: MaterialAutoCompleteTextView, value: String) {
        val adapter = view.adapter ?: return
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString() == value) {
                view.setText(value, false)
                break
            }
        }
    }

    private fun setupListeners() {
        // Enable/disable dependent fields when switches are toggled
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

    // -------------------------------------------------------------------------
    // Filter application
    // -------------------------------------------------------------------------

    private fun applyFilters() {
        val filterList = mutableListOf<FilterBox>()

        if (BuildConfig.DEBUG) Log.d(TAG, "Applying filters...")

        // 1. Status
        val status = when (binding.filterStatusChipGroup.checkedChipId) {
            R.id.filter_status_aktief -> "0"
            R.id.filter_status_onaktief -> "2"
            R.id.filter_status_almal -> "*"
            else -> "0"
        }
        viewModel.recordStatus = status

        // 2. Simple switches
        addSwitchFilter("Selfoon", binding.filterSelfoon, filterList)
        addSwitchFilter("Landlyn", binding.filterLandlyn, filterList)
        addSwitchFilter("E-pos", binding.filterEpos, filterList)
        addSwitchFilter("Gesinshoof", binding.filterGesinshoof, filterList)
        addSwitchFilter("Note", binding.filterNota, filterList)
        addSwitchFilter("Reminder", binding.filterHerinnering, filterList)

        // 3. Complex filters (select list)
        addComplexFilter("Geslag", binding.filterGeslagCheck, binding.filterGeslagOpsies.text.toString(), filterList)
        addComplexFilter("Huwelikstatus", binding.filterHuwelikstatusCheck, binding.filterHuwelikstatusOpsies.text.toString(), filterList)
        addComplexFilter("Lidmaatskap", binding.filterLidmaatskapCheck, binding.filterLidmaatskapOpsies.text.toString(), filterList)

        // 4. Text filters
        addTextFilter("Van", binding.filterVanCheck, binding.filterVanOpsies.text.toString(), binding.filterVan.text.toString(), filterList)
        addTextFilter("Noemnaam", binding.filterNoemnaamCheck, binding.filterNoemnaamOpsies.text.toString(), binding.filterNoemnaam.text.toString(), filterList)
        addTextFilter("Nooiensvan", binding.filterNooiensvanCheck, binding.filterNooiensvanOpsies.text.toString(), binding.filterNooiensvan.text.toString(), filterList)
        addTextFilter("Wyk", binding.filterWykCheck, binding.filterWykOpsies.text.toString(), binding.filterWyk.text.toString(), filterList)

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

        val hasActiveFilter = filterList.any { it.checked }
        val activity = activity as? MainActivity

        // ─── NEW: Update note/reminder GUIDs in ViewModel ──────────
        lifecycleScope.launch {
            val noteGuids = if (binding.filterNota.isChecked) {
                withContext(Dispatchers.IO) {
                    pastoralNoteRepository.getMemberGuidsWithNotes().toSet()
                }
            } else {
                emptySet()
            }
            viewModel.updateNoteGuids(noteGuids)

            val reminderGuids = if (binding.filterHerinnering.isChecked) {
                withContext(Dispatchers.IO) {
                    followUpReminderDao.getAllPending()
                        .mapNotNull { it.memberGuid?.takeIf { guid -> guid.isNotBlank() } }
                        .distinct()
                        .toSet()
                }
            } else {
                emptySet()
            }
            viewModel.updateReminderGuids(reminderGuids)

            if (hasActiveFilter) {
                activity?.searchFilterCoordinator?.applyFilterResult(
                    ArrayList(filterList),
                    viewModel.sortOrder
                )
                activity?.searchFilterCoordinator?.updateSummaryView()
            } else {
                activity?.searchFilterCoordinator?.clearAndRestore()
            }
        }

        // Dismiss is handled by the button click listener
    }

    private fun addSwitchFilter(title: String, switch: MaterialSwitch, list: MutableList<FilterBox>) {
        if (switch.isChecked) {
            list.add(FilterBox(title = title, text1 = "", text2 = "", text3 = "", checked = true))
            if (BuildConfig.DEBUG) Log.d(TAG, "Added switch filter: $title")
        }
    }

    private fun addComplexFilter(title: String, switch: MaterialSwitch, value: String, list: MutableList<FilterBox>) {
        if (switch.isChecked) {
            list.add(FilterBox(title = title, text1 = "", text2 = "", text3 = value, checked = true))
            if (BuildConfig.DEBUG) Log.d(TAG, "Added complex filter: $title = $value")
        }
    }

    private fun addTextFilter(
        title: String,
        switch: MaterialSwitch,
        operator: String,
        value: String,
        list: MutableList<FilterBox>
    ) {
        if (switch.isChecked) {
            if (value.isNotEmpty()) {
                list.add(FilterBox(title = title, text1 = value, text2 = "", text3 = operator, checked = true))
                if (BuildConfig.DEBUG) Log.d(TAG, "Added text filter: $title $operator '$value'")
            } else {
                list.add(FilterBox(title = title, text1 = "", text2 = "", text3 = "leeg", checked = true))
                if (BuildConfig.DEBUG) Log.d(TAG, "Added empty filter: $title is leeg")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Reset (within sheet only)
    // -------------------------------------------------------------------------

    private fun resetAllFilters() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Resetting all filters (local)")

        // Reset status chip to Aktief
        binding.filterStatusAktief.isChecked = true

        // Reset all switches
        listOf(
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
            binding.filterOuderdomCheck,
            binding.filterNota,
            binding.filterHerinnering
        ).forEach { it.isChecked = false }

        // Clear EditTexts
        listOf(
            binding.filterVan,
            binding.filterNoemnaam,
            binding.filterNooiensvan,
            binding.filterWyk,
            binding.filterOuderdom1,
            binding.filterOuderdom2
        ).forEach { it.text?.clear() }

        // Reset AutoCompleteTextViews to defaults
        resetAutoCompleteTextView(binding.filterGeslagOpsies, "manlik")
        resetAutoCompleteTextView(binding.filterHuwelikstatusOpsies, "Getroud")
        resetAutoCompleteTextView(binding.filterLidmaatskapOpsies, "Belydend")
        resetAutoCompleteTextView(binding.filterVanOpsies, "gelyk aan")
        resetAutoCompleteTextView(binding.filterNoemnaamOpsies, "gelyk aan")
        resetAutoCompleteTextView(binding.filterNooiensvanOpsies, "gelyk aan")
        resetAutoCompleteTextView(binding.filterWykOpsies, "gelyk aan")
        resetAutoCompleteTextView(binding.filterOuderdomOpsies, "gelyk")

        // Re‑enable/disable fields accordingly
        setupListeners()

        // ─── NEW: Clear GUIDs in ViewModel ──────────
        viewModel.updateNoteGuids(emptySet())
        viewModel.updateReminderGuids(emptySet())
    }

    private fun resetAutoCompleteTextView(view: MaterialAutoCompleteTextView, defaultText: String) {
        view.setText(defaultText, false)
    }

    // -------------------------------------------------------------------------
    // Cancel – restore original state via coordinator
    // -------------------------------------------------------------------------

    private fun restoreOriginalState() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "restoreOriginalState: restoring via coordinator")
        }
        val activity = activity as? MainActivity
        activity?.searchFilterCoordinator?.restoreOriginalState(
            originalFilterList,
            originalSortOrder,
            originalRecordStatus
        )
        // Coordinator already refreshes data and updates UI
    }
}