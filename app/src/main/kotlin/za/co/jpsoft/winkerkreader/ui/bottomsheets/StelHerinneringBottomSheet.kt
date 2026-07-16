package za.co.jpsoft.winkerkreader.ui.bottomsheets

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContext
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContextSchema
import za.co.jpsoft.winkerkreader.data.pastoral.repository.CongregationMemberGuidResolver
import za.co.jpsoft.winkerkreader.databinding.BottomSheetStelHerinneringBinding
import za.co.jpsoft.winkerkreader.ui.adapters.ReminderPreviewAdapter
import za.co.jpsoft.winkerkreader.ui.adapters.TemplatePickerAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModelFactory
import za.co.jpsoft.winkerkreader.utils.getStringOrEmpty
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.google.android.material.R as materialR

class StelHerinneringBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetStelHerinneringBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LidmaatDetailPastoralViewModel by viewModels {
        LidmaatDetailPastoralViewModelFactory(
            context = requireContext(),
            memberGuid = requireArguments().getString(ARG_MEMBER_GUID) ?: ""
        )
    }
    private val dateButtonLabels = mutableMapOf<String, String>()
    private var mode = Mode.TEMPLATE
    private var selectedTemplateId: String? = null
    private var anchorDate: LocalDate = LocalDate.now()
    private var dueDate: LocalDate = LocalDate.now()
    private var dueTime: LocalTime? = null
    private var isTimedMode = false
    private var familyLabel: TextView? = null

    private lateinit var templateAdapter: TemplatePickerAdapter
    private lateinit var previewAdapter: ReminderPreviewAdapter

    // Current preview list, including per-reminder selection state and any
    // manual date edits. Rebuilt from scratch whenever the template or the
    // anchor date changes (see refreshPreview()) — any per-item edits made
    // before that point are intentionally reset, since the anchor date is
    // what the whole preview is derived from.
    private var currentPreviewItems: List<LidmaatDetailPastoralViewModel.PreviewItem> = emptyList()

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    private val contextValues = mutableMapOf<String, String>()
    private val contextDateValues = mutableMapOf<String, LocalDate>()
    private var selectedTemplateCode: String? = null

    // Family member data
    private data class FamilyMember(
        val guid: String,
        val displayName: String,
        val birthday: String
    )

    private var familyMembers: List<FamilyMember> = emptyList()
    private var familySpinner: Spinner? = null

    enum class Mode { TEMPLATE, ADHOC }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetStelHerinneringBinding.inflate(inflater, container, false)

        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupModeTabs()
        setupTemplatePanel()
        setupAdHocPanel()
        setupConfirmButton()
        observeViewModel()

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
            contextValues.clear()
            contextDateValues.clear()
            removeFamilySpinner()
            if (selected != null) {
                onTemplateSelected(selected.template.code)
                refreshPreview()
            } else {
                currentPreviewItems = emptyList()
                previewAdapter.submitList(emptyList())
                binding.layoutContextFields.removeAllViews()
                binding.layoutContextFields.visibility = View.GONE
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

    /** Dot tapped — toggle whether this reminder will be created. */
    private fun onPreviewItemToggled(item: LidmaatDetailPastoralViewModel.PreviewItem) {
        currentPreviewItems = currentPreviewItems.map {
            if (it.stepId == item.stepId) it.copy(isSelected = !it.isSelected) else it
        }
        previewAdapter.submitList(currentPreviewItems)
        updateConfirmButton()
    }

    /** Row tapped (outside the dot) — let the user change this reminder's date before it's created. */
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
    // Family member loading and spinner
    // -------------------------------------------------------------------------

    private fun removeFamilySpinner() {
        familyLabel?.let { (it.parent as? ViewGroup)?.removeView(it) }
        familySpinner?.let { (it.parent as? ViewGroup)?.removeView(it) }
        familyLabel = null
        familySpinner = null
        familyMembers = emptyList()
    }

    private fun loadFamilyMembers(memberGuid: String, familyHeadGuid: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val members = fetchFamilyMembers(memberGuid, familyHeadGuid)
            withContext(Dispatchers.Main) {
                if (members.isNotEmpty()) {
                    familyMembers = members
                    addFamilySpinner(members)  // pass the list
                    if (BuildConfig.DEBUG) Log.d(TAG, "Family members loaded: ${members.size}")
                } else {
                    if (BuildConfig.DEBUG) Log.d(TAG, "No family members found")
                    removeFamilySpinner()
                }
            }
        }
    }

    private fun fetchFamilyMembers(
        memberGuid: String,
        familyHeadGuid: String?
    ): List<FamilyMember> {
        // Bepaal die effektiewe gesinshoof-GUID
        val effectiveFamilyHeadGuid = if (!familyHeadGuid.isNullOrBlank()) {
            familyHeadGuid
        } else {
            val resolver = CongregationMemberGuidResolver(requireContext())
            val member = resolver.resolve(memberGuid) ?: return emptyList()
            member.familyHeadGuid ?: member.guid
        }
        if (effectiveFamilyHeadGuid.isBlank()) return emptyList()

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Family head GUID: $effectiveFamilyHeadGuid")
        }

        // Bou 'n volledige SQL-query (soos in MemberViewModel)
        val query = """
        SELECT ${winkerkEntry.LIDMATE_LIDMAATGUID}, 
               ${winkerkEntry.LIDMATE_NOEMNAAM}, 
               ${winkerkEntry.LIDMATE_VAN}, 
               ${winkerkEntry.LIDMATE_GEBOORTEDATUM}
        FROM ${winkerkEntry.LIDMATE_TABLE_NAME}
        WHERE ${winkerkEntry.LIDMATE_GESINSHOOFGUID} = ? 
          AND ${winkerkEntry.LIDMATE_LIDMAATGUID} != ?
    """.trimIndent()

        val args = arrayOf(effectiveFamilyHeadGuid, memberGuid)

        val cursor = requireContext().contentResolver.query(
            winkerkEntry.CONTENT_URI,
            null, // projection is null omdat ons die kolomme in die query spesifiseer
            query,
            args,
            null
        )

        val result = mutableListOf<FamilyMember>()
        cursor?.use {
            while (it.moveToNext()) {
                val guid = it.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATGUID)
                val name = it.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)
                val surname = it.getStringOrEmpty(winkerkEntry.LIDMATE_VAN)
                val birthday = it.getStringOrEmpty(winkerkEntry.LIDMATE_GEBOORTEDATUM)
                val displayName = if (name.isNotEmpty() || surname.isNotEmpty()) {
                    listOf(name, surname).filter { it.isNotEmpty() }.joinToString(" ")
                } else {
                    "Onbekend"
                }
                result.add(FamilyMember(guid, displayName, birthday))
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Family member: $displayName, birthday: $birthday")
                }
            }
        }
        return result
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun addFamilySpinner(members: List<FamilyMember>) {
        // Remove any existing spinner and label
        removeFamilySpinner()

        binding.layoutContextFields.visibility = View.VISIBLE

        // --- Label ---
        val label = TextView(requireContext()).apply {
            text = "Kies gesinslid of tik naam van oorledene"
            setTextAppearance(android.R.style.TextAppearance_Small)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
        }
        // Insert at position 0
        binding.layoutContextFields.addView(label, 0)
        familyLabel = label

        // --- Spinner ---
        val displayItems = mutableListOf("Kies gesinslid")
        displayItems.addAll(members.map { "${it.displayName} (${it.birthday})" })

        val spinner = Spinner(requireContext())
        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            displayItems
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                if (view is TextView) {
                    view.setTextColor(
                        MaterialColors.getColor(
                            view.context,
                            com.google.android.material.R.attr.colorOnSurface,
                            Color.BLACK
                        )
                    )
                    view.textSize = 16f
                }
                return view
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = super.getDropDownView(position, convertView, parent)
                if (view is TextView) {
                    view.setTextColor(
                        MaterialColors.getColor(
                            view.context,
                            com.google.android.material.R.attr.colorOnSurface,
                            Color.BLACK
                        )
                    )
                    view.textSize = 16f
                }
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position == 0) {
                    // Placeholder selected → clear everything
                    findEditTextByTag("deceasedName")?.setText("")
                    findEditTextByTag("deceasedDob")?.setText("")
                    // Reset the date button to its label (if it exists)
                    findDateButtonByTag("deceasedDob")?.let { btn ->
                        btn.text = dateButtonLabels["deceasedDob"] ?: "Geboorte datum"
                    }
                    contextValues["deceasedName"] = ""
                    contextValues["deceasedDob"] = ""
                    contextDateValues.remove("deceasedDob")
                    return
                }

                val member = members[position - 1]

                // Update the name field (always a text field)
                findEditTextByTag("deceasedName")?.setText(member.displayName)
                contextValues["deceasedName"] = member.displayName

                // Update the birth date: if there is a date button, update its text and contextDateValues
                val dateButton = findDateButtonByTag("deceasedDob")
                if (dateButton != null) {
                    // Parse the birthday string (assume format yyyy-MM-dd)
                    try {
                        val date =
                            LocalDate.parse(member.birthday, DateTimeFormatter.ISO_LOCAL_DATE)
                        contextDateValues["deceasedDob"] = date
                        // Update button text
                        val label = dateButtonLabels["deceasedDob"] ?: "Geboorte datum"
                        dateButton.text = "$label: ${date.format(dateFormatter)}"
                    } catch (e: Exception) {
                        // If parsing fails, fallback to storing as string in contextValues
                        contextValues["deceasedDob"] = member.birthday
                        // Optionally show the raw string on the button
                        val label = dateButtonLabels["deceasedDob"] ?: "Geboorte datum"
                        dateButton.text = "$label: ${member.birthday}"
                    }
                } else {
                    // No date button – update the edit text (if any) and contextValues
                    findEditTextByTag("deceasedDob")?.setText(member.birthday)
                    contextValues["deceasedDob"] = member.birthday
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Default to placeholder
        spinner.setSelection(0, false)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpToPx(8) }
        spinner.layoutParams = params
        spinner.visibility = View.VISIBLE
        // Tint the existing spinner drawable rather than replacing it with a flat colour.
        // Using backgroundTintList preserves the dropdown arrow and respects the M3 theme.
        spinner.backgroundTintList = ColorStateList.valueOf(
            MaterialColors.getColor(
                spinner.context,
                com.google.android.material.R.attr.colorSurfaceVariant,
                Color.LTGRAY
            )
        )

        // Insert at position 1 (after the label)
        binding.layoutContextFields.addView(spinner, 1)
        familySpinner = spinner

        binding.layoutContextFields.requestLayout()
        binding.layoutContextFields.invalidate()
    }

    private fun findEditTextByTag(tag: String): TextInputEditText? {
        for (i in 0 until binding.layoutContextFields.childCount) {
            val child = binding.layoutContextFields.getChildAt(i)
            if (child is TextInputLayout) {
                val editText = child.editText
                if (editText is TextInputEditText && editText.tag == tag) {
                    return editText
                }
            }
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Ad-hoc panel
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
    // Template selection
    // -------------------------------------------------------------------------

    private fun onTemplateSelected(templateCode: String) {
        binding.btnAnchorDate.hint = TemplateContextSchema.anchorDateLabel(templateCode)

        // Clear everything
        binding.layoutContextFields.removeAllViews()
        dateButtonLabels.clear()
        familyLabel = null
        familySpinner = null
        familyMembers = emptyList()

        val fields = TemplateContextSchema.fieldsFor(templateCode)
        binding.layoutContextFields.visibility = if (fields.isEmpty()) View.GONE else View.VISIBLE

        // 1. Create all schema‑defined fields (they will be added in order)
        fields.forEach { field ->
            when (field) {
                is TemplateContextSchema.Field.Text -> {
                    val til = TextInputLayout(requireContext()).apply {
                        hint = field.labelAfr
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dpToPx(12) }
                    }
                    val et = TextInputEditText(requireContext()).apply {
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun afterTextChanged(s: android.text.Editable?) {
                                contextValues[field.key] = s?.toString() ?: ""
                                updateConfirmButton()
                            }

                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int
                            ) {
                            }

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int
                            ) {
                            }
                        })
                        tag = field.key
                    }
                    til.addView(et)
                    binding.layoutContextFields.addView(til)
                }

                is TemplateContextSchema.Field.DateField -> {
                    val btn = MaterialButton(
                        requireContext(),
                        null,
                        materialR.attr.materialButtonOutlinedStyle   // ✅ uses the attribute
                    ).apply {
                        text = field.labelAfr
                        tag = field.key
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dpToPx(12) }
                        setOnClickListener {
                            val existing = contextDateValues[field.key] ?: LocalDate.now()
                            DatePickerDialog(
                                requireContext(),
                                { _, year, month, day ->
                                    val picked = LocalDate.of(year, month + 1, day)
                                    contextDateValues[field.key] = picked
                                    text = "${field.labelAfr}: ${picked.format(dateFormatter)}"
                                },
                                existing.year,
                                existing.monthValue - 1,
                                existing.dayOfMonth
                            ).show()
                        }
                    }
                    dateButtonLabels[field.key] = field.labelAfr
                    binding.layoutContextFields.addView(btn)
                }
            }
        }

        // 2. If this is the NA_STERF template, load family members.
        //    The spinner will be inserted at the very front (positions 0 & 1)
        //    so it appears above all schema fields.
        if (templateCode == "NA_STERF") {
            val memberGuid = requireArguments().getString(ARG_MEMBER_GUID) ?: return
            val familyHeadGuid = requireArguments().getString(ARG_FAMILY_HEAD_GUID)
            loadFamilyMembers(memberGuid, familyHeadGuid)
        }
    }

    private fun findDateButtonByTag(tag: String): MaterialButton? {
        for (i in 0 until binding.layoutContextFields.childCount) {
            val child = binding.layoutContextFields.getChildAt(i)
            if (child is MaterialButton && child.tag == tag) {
                return child
            }
        }
        return null
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

                    val context = TemplateContext.build {
                        contextValues.forEach { (k, v) -> put(k, v) }
                        contextDateValues.forEach { (k, v) -> put(k, v) }
                    }
                    if (BuildConfig.DEBUG) Log.d(
                        "PastoralRepo",
                        "contextJson = ${context.toJson()}"
                    )
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
    }

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
