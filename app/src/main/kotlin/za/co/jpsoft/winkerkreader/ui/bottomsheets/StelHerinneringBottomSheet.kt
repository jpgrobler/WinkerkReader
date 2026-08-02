// StelHerinneringBottomSheet.kt
package za.co.jpsoft.winkerkreader.ui.bottomsheets

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.model.FamilyMember
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContext
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContextSchema
import za.co.jpsoft.winkerkreader.databinding.BottomSheetStelHerinneringBinding
import za.co.jpsoft.winkerkreader.ui.adapters.ReminderPreviewAdapter
import za.co.jpsoft.winkerkreader.ui.adapters.TemplatePickerAdapter
import za.co.jpsoft.winkerkreader.ui.bottomsheets.controllers.FamilyMemberSpinnerController
import za.co.jpsoft.winkerkreader.ui.bottomsheets.controllers.TemplateContextFormBuilder
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModelFactory
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@AndroidEntryPoint
class StelHerinneringBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var pastoralViewModelFactory: LidmaatDetailPastoralViewModelFactory

    private var _binding: BottomSheetStelHerinneringBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LidmaatDetailPastoralViewModel by viewModels {
        val guid = requireArguments().getString(ARG_MEMBER_GUID) ?: ""
        pastoralViewModelFactory.create(guid)
    }

    private lateinit var formBuilder: TemplateContextFormBuilder
    private lateinit var spinnerController: FamilyMemberSpinnerController
    private lateinit var templateAdapter: TemplatePickerAdapter
    private lateinit var previewAdapter: ReminderPreviewAdapter

    private var mode = Mode.TEMPLATE
    private var selectedTemplateId: String? = null
    private var selectedTemplateCode: String? = null
    private var anchorDate = LocalDate.now()
    private var dueDate = LocalDate.now()
    private var dueTime: LocalTime? = null
    private var isTimedMode = false
    private var currentPreviewItems: List<LidmaatDetailPastoralViewModel.PreviewItem> = emptyList()

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    enum class Mode { TEMPLATE, ADHOC }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetStelHerinneringBinding.inflate(inflater, container, false)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialise builders/controllers
        formBuilder = TemplateContextFormBuilder(requireContext(), binding.layoutContextFields)
        spinnerController = FamilyMemberSpinnerController(
            context = requireContext(),
            container = binding.layoutContextFields,
            onMemberSelected = { member: FamilyMember? ->
                if (member != null) {
                    // Fill the 'deceasedName' text field if it exists
                    formBuilder.getTextField("deceasedName")?.setText(member.displayName)
                    // Set the 'deceasedDob' date button if it exists
                    formBuilder.getDateButton("deceasedDob")?.let { btn ->
                        val label = btn.text.toString().substringBefore(':').trim()
                        val formatted = member.birthday.takeIf { it.isNotBlank() }?.let {
                            try {
                                LocalDate.parse(it).format(dateFormatter)
                            } catch (_: Exception) {
                                it
                            }
                        } ?: ""
                        btn.text = if (formatted.isNotEmpty()) "$label: $formatted" else label
                    }
                } else {
                    // Clear fields
                    formBuilder.getTextField("deceasedName")?.setText("")
                    formBuilder.getDateButton("deceasedDob")?.let { btn ->
                        val label = btn.text.toString().substringBefore(':').trim()
                        btn.text = label
                    }
                }
            }
        )

        setupModeTabs()
        setupTemplatePanel()
        setupAdHocPanel()
        setupConfirmButton()
        observeViewModel()

        // Window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentContainer) { v, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val originalBottomPadding =
                resources.getDimensionPixelSize(R.dimen.bottom_sheet_bottom_padding)
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                navBarHeight + originalBottomPadding
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.contentContainer)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // -------------------------------------------------------------------------
    // Mode tabs
    // -------------------------------------------------------------------------

    private fun setupModeTabs() {
        binding.tabMode.addTab(
            binding.tabMode.newTab().setText(getString(R.string.herinnering_stel_sjabloon))
        )
        binding.tabMode.addTab(
            binding.tabMode.newTab().setText(getString(R.string.herinnering_enkel))
        )

        binding.tabMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                mode = if (tab.position == 0) Mode.TEMPLATE else Mode.ADHOC
                binding.panelTemplate.visibility =
                    if (mode == Mode.TEMPLATE) View.VISIBLE else View.GONE
                binding.panelAdhoc.visibility = if (mode == Mode.ADHOC) View.VISIBLE else View.GONE
                updateConfirmButton()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // -------------------------------------------------------------------------
    // Template panel
    // -------------------------------------------------------------------------

    private fun setupTemplatePanel() {
        templateAdapter = TemplatePickerAdapter { selected ->
            selectedTemplateId = selected?.template?.templateId
            selectedTemplateCode = selected?.template?.code
            if (selected != null) {
                onTemplateSelected(selected.template.code)
                refreshPreview()
            } else {
                currentPreviewItems = emptyList()
                previewAdapter.submitList(emptyList())
                formBuilder.clear()
                spinnerController.remove()
                binding.tvPreviewHeader.visibility = View.GONE
                binding.tvPreviewHint.visibility = View.GONE
                binding.rvPreview.visibility = View.GONE
            }
            updateConfirmButton()
        }

        binding.rvTemplates.apply {
            adapter = templateAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        previewAdapter = ReminderPreviewAdapter(
            onToggleSelected = ::onPreviewItemToggled,
            onDateClick = ::onPreviewItemDateClick
        )
        binding.rvPreview.apply {
            adapter = previewAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
        }

        binding.btnAnchorDate.text = anchorDate.format(dateFormatter)
        binding.btnAnchorDate.setOnClickListener { showAnchorDatePicker() }
    }

    private fun showAnchorDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                anchorDate = LocalDate.of(year, month + 1, day)
                binding.btnAnchorDate.text = anchorDate.format(dateFormatter)
                refreshPreview()
            },
            anchorDate.year,
            anchorDate.monthValue - 1,
            anchorDate.dayOfMonth
        ).show()
    }

    private fun refreshPreview() {
        val templateId = selectedTemplateId ?: return
        currentPreviewItems = viewModel.previewTemplateDates(templateId, anchorDate)
        previewAdapter.submitList(currentPreviewItems)
        val hasItems = currentPreviewItems.isNotEmpty()
        binding.tvPreviewHeader.visibility = if (hasItems) View.VISIBLE else View.GONE
        binding.tvPreviewHint.visibility = if (hasItems) View.VISIBLE else View.GONE
        binding.rvPreview.visibility = if (hasItems) View.VISIBLE else View.GONE
        updateConfirmButton()
    }

    private fun onPreviewItemToggled(item: LidmaatDetailPastoralViewModel.PreviewItem) {
        currentPreviewItems = currentPreviewItems.map {
            if (it.stepId == item.stepId) it.copy(isSelected = !it.isSelected) else it
        }
        previewAdapter.submitList(currentPreviewItems)
        updateConfirmButton()
    }

    private fun onPreviewItemDateClick(item: LidmaatDetailPastoralViewModel.PreviewItem) {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val picked = LocalDate.of(year, month + 1, day)
                currentPreviewItems = currentPreviewItems.map {
                    if (it.stepId == item.stepId) it.copy(
                        dueDate = picked,
                        isInPast = picked.isBefore(LocalDate.now())
                    ) else it
                }
                previewAdapter.submitList(currentPreviewItems)
            },
            item.dueDate.year,
            item.dueDate.monthValue - 1,
            item.dueDate.dayOfMonth
        ).show()
    }

    // -------------------------------------------------------------------------
    // Template selection & context fields
    // -------------------------------------------------------------------------

    private fun onTemplateSelected(templateCode: String) {
        // Build the context fields from schema
        formBuilder.buildFor(templateCode)

        // If the template is "NA_STERF", load family members via ViewModel
        if (templateCode == "NA_STERF") {
            val memberGuid = requireArguments().getString(ARG_MEMBER_GUID) ?: return
            val familyHeadGuid = requireArguments().getString(ARG_FAMILY_HEAD_GUID)
            viewModel.loadFamilyMembers(memberGuid, familyHeadGuid)
        } else {
            spinnerController.remove()
        }
    }

    // -------------------------------------------------------------------------
    // Ad‑hoc panel
    // -------------------------------------------------------------------------

    private fun setupAdHocPanel() {
        dueDate = LocalDate.now()
        binding.btnDueDate.text = getString(R.string.datum_vandag)
        binding.btnDueDate.setOnClickListener { showDueDatePicker() }

        binding.switchTimed.setOnCheckedChangeListener { _, checked ->
            isTimedMode = checked
            binding.btnDueTime.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) dueTime = null
        }
        binding.btnDueTime.setOnClickListener { showTimePicker() }

        binding.etTitle.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = updateConfirmButton()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun showDueDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                dueDate = LocalDate.of(year, month + 1, day)
                binding.btnDueDate.text = dueDate.format(dateFormatter)
            },
            dueDate.year,
            dueDate.monthValue - 1,
            dueDate.dayOfMonth
        ).show()
    }

    private fun showTimePicker() {
        val current = dueTime ?: LocalTime.of(9, 0)
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                dueTime = LocalTime.of(hour, minute)
                binding.btnDueTime.text = LocalTime.of(hour, minute)
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            },
            current.hour,
            current.minute,
            true
        ).show()
    }

    // -------------------------------------------------------------------------
    // Confirm button
    // -------------------------------------------------------------------------

    private fun setupConfirmButton() {
        binding.btnBevestig.setOnClickListener {
            when (mode) {
                Mode.TEMPLATE -> {
                    val templateId = selectedTemplateId ?: return@setOnClickListener
                    val templateCode = selectedTemplateCode ?: ""

                    val textValues = formBuilder.getTextValues()
                    val dateValues = formBuilder.getDateValues()
                    val allValues =
                        textValues + dateValues.mapValues { entry -> entry.value.toString() }

                    val context = TemplateContext.build {
                        allValues.forEach { (k, v) -> put(k, v) }
                    }

                    viewModel.createFromTemplate(
                        templateId = templateId,
                        anchorDate = anchorDate,
                        selectedItems = currentPreviewItems,
                        contextJson = if (TemplateContextSchema.hasContext(templateCode))
                            context.toJson()
                        else null
                    )
                    dismiss()
                }

                Mode.ADHOC -> {
                    val title = binding.etTitle.text?.toString()?.trim()
                    if (title.isNullOrBlank()) return@setOnClickListener
                    viewModel.createAdHoc(
                        title = title,
                        note = binding.etNote.text?.toString()?.trim()?.ifBlank { null },
                        dueDate = dueDate,
                        scheduleType = if (isTimedMode) ScheduleType.TIMED else ScheduleType.DATE_ONLY,
                        dueTime = dueTime
                    )
                    dismiss()
                }
            }
        }
    }

    private fun updateConfirmButton() {
        binding.btnBevestig.isEnabled = when (mode) {
            Mode.TEMPLATE -> selectedTemplateId != null && currentPreviewItems.any { it.isSelected }
            Mode.ADHOC -> !binding.etTitle.text.isNullOrBlank()
        }
    }

    // -------------------------------------------------------------------------
    // ViewModel observers
    // -------------------------------------------------------------------------

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.created.collect { count ->
                val msg = resources.getQuantityString(
                    R.plurals.herinnering_created_count, count, count
                )
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.templates.collect { templates ->
                binding.templateProgress.visibility =
                    if (templates.isEmpty()) View.VISIBLE else View.GONE
                templateAdapter.submitList(templates)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        // Observe family members and update the spinner
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.familyMembers.collect { members ->
                if (members.isNotEmpty()) {
                    spinnerController.show(members)
                } else {
                    spinnerController.remove()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Companion
    // -------------------------------------------------------------------------

    companion object {
        const val TAG = "StelHerinneringBottomSheet"
        private const val ARG_MEMBER_GUID = "arg_member_guid"
        private const val ARG_FAMILY_HEAD_GUID = "arg_family_head_guid"

        fun newInstance(memberGuid: String?, familyHeadGuid: String? = null) =
            StelHerinneringBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEMBER_GUID, memberGuid)
                    putString(ARG_FAMILY_HEAD_GUID, familyHeadGuid)
                }
            }
    }
}