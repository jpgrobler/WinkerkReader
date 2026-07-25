# Pastoral CRM — Sprint E: Member Detail Integration

> **Status:** Ready for implementation
> **Depends on:** Sprints A · B · C · D
> **As-built flows:** [`architecture.md`](architecture.md) §7–§8 (Member detail + pastoral)

---

## Task table

| # | Task | Files |
|---|------|-------|
| E0 | Verify GUID extra key in `LidmaatDetailActivity` | `LidmaatDetailActivity.kt` (read only) |
| E1 | Layout block | Insert into `res/layout/lidmaat_detail.xml` |
| E2 | `StelHerinneringBottomSheet` layout | `res/layout/bottom_sheet_stel_herinnering.xml`, `res/layout/item_template_picker.xml`, `res/layout/item_reminder_preview.xml` |
| E3 | `LidmaatDetailPastoralViewModel` + Factory | `ui/viewmodels/LidmaatDetailPastoralViewModel.kt` |
| E4 | `StelHerinneringBottomSheet` | `ui/bottomsheets/StelHerinneringBottomSheet.kt` |
| E5 | `PendingReminderMiniAdapter` | `ui/adapters/PendingReminderMiniAdapter.kt` |
| E6 | Wire into `LidmaatDetailActivity` | `LidmaatDetailActivity.kt` |
| E7 | Options menu item | `res/menu/menu_lidmaat_detail.xml` |
| E8 | Strings | `res/values/strings.xml` |

**Implement in order: E0 → E3 → E2 → E4 → E5 → E1 → E6 → E7 → E8**

---

## E0 — Verify GUID extra key (before writing any code)

`LidmaatDetailActivity` receives a member GUID via an Intent extra. The key must match exactly what Sprint E passes when opening the BottomSheet. Open `LidmaatDetailActivity.kt` and find:

```kotlin
// Look for something like:
val memberGuid = intent.getStringExtra("MemberGUID")  // or "MEMBER_GUID", or similar
```

Note the key. All Sprint E code uses the constant:

```kotlin
// Add to LidmaatDetailActivity companion object:
const val EXTRA_MEMBER_GUID = "MemberGUID"  // ← replace value with whatever you find
```

All Sprint E files use `LidmaatDetailActivity.EXTRA_MEMBER_GUID` — change the value once and everything stays consistent.

---

## E3 — LidmaatDetailPastoralViewModel + Factory

Designed as a **separate** ViewModel from any existing `LidmaatDetailViewModel`, so Sprint E adds no risk to existing member detail functionality.

New file: `ui/viewmodels/LidmaatDetailPastoralViewModel.kt`

```kotlin
package za.co.jpsoft.winkerkreader.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateWithSteps
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import java.time.LocalDate
import java.time.LocalTime

class LidmaatDetailPastoralViewModel(
    private val repository: PastoralReminderRepository,
    val memberGuid: String
) : ViewModel() {

    // -------------------------------------------------------------------------
    // Pending reminders for this member (drives mini-list)
    // -------------------------------------------------------------------------

    val pendingReminders: StateFlow<List<FollowUpReminderEntity>> =
        repository.observePendingForMember(memberGuid)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> = pendingReminders
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // -------------------------------------------------------------------------
    // Available templates (drives template picker in BottomSheet)
    // -------------------------------------------------------------------------

    val templates: StateFlow<List<TemplateWithSteps>> =
        repository.observeTemplates()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -------------------------------------------------------------------------
    // One-shot events
    // -------------------------------------------------------------------------

    private val _created = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    /** Emits the count of reminders just created — used for Toast confirmation. */
    val created: SharedFlow<Int> = _created.asSharedFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    fun createFromTemplate(templateId: String, anchorDate: LocalDate) {
        viewModelScope.launch {
            try {
                val ids = repository.createFromTemplate(
                    memberGuid  = memberGuid,
                    templateId  = templateId,
                    anchorDate  = anchorDate
                )
                _created.tryEmit(ids.size)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie herinnerings stel nie: ${e.message}")
            }
        }
    }

    fun createAdHoc(
        title: String,
        note: String?,
        dueDate: LocalDate,
        scheduleType: ScheduleType,
        dueTime: LocalTime? = null
    ) {
        viewModelScope.launch {
            try {
                repository.createAdHocReminder(
                    memberGuid   = memberGuid,
                    title        = title,
                    note         = note,
                    dueDate      = dueDate,
                    scheduleType = scheduleType,
                    dueTime      = dueTime
                )
                _created.tryEmit(1)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie herinnering stel nie: ${e.message}")
            }
        }
    }

    fun completeReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                repository.completeReminder(reminderId)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie herinnering voltooi nie")
            }
        }
    }

    /**
     * Previews what dates a template will produce for [anchorDate].
     * Pure computation — no DB access.
     */
    fun previewTemplateDates(templateId: String, anchorDate: LocalDate): List<PreviewItem> {
        val template = templates.value.find { it.template.templateId == templateId }
            ?: return emptyList()
        return template.steps
            .sortedBy { it.stepOrder }
            .map { step ->
                val date = anchorDate
                    .plusMonths(step.offsetMonths.toLong())
                    .plusDays(step.offsetDays.toLong())
                PreviewItem(
                    stepTitle = step.defaultTitleAf,
                    dueDate   = date,
                    isInPast  = date.isBefore(LocalDate.now())
                )
            }
    }

    data class PreviewItem(
        val stepTitle: String,
        val dueDate: LocalDate,
        val isInPast: Boolean
    )
}
```

**Add `observeTemplates()` and `observePendingForMember()` to `PastoralReminderRepository`:**

`observePendingForMember()` already exists (Sprint A). Add `observeTemplates()`:

```kotlin
// Add to PastoralReminderRepository.kt

fun observeTemplates(): Flow<List<TemplateWithSteps>> =
    templateDao.observeTemplatesWithSteps()
        .flowOn(Dispatchers.IO)
```

**Factory:**

New file: `ui/viewmodels/LidmaatDetailPastoralViewModelFactory.kt`

```kotlin
package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository

class LidmaatDetailPastoralViewModelFactory(
    private val context: Context,
    private val memberGuid: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LidmaatDetailPastoralViewModel::class.java)) {
            return LidmaatDetailPastoralViewModel(
                repository  = PastoralReminderRepository.create(context.applicationContext),
                memberGuid  = memberGuid
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
```

---

## E2 — Layouts

### `bottom_sheet_stel_herinnering.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingBottom="24dp">

        <!-- Drag handle -->
        <View
            android:layout_width="32dp"
            android:layout_height="4dp"
            android:layout_gravity="center_horizontal"
            android:layout_marginTop="8dp"
            android:layout_marginBottom="12dp"
            android:background="@drawable/bg_badge_red"
            android:alpha="0.2" />

        <!-- Title -->
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/detail_stel_herinnering"
            android:textAppearance="?attr/textAppearanceTitleMedium"
            android:paddingHorizontal="24dp"
            android:paddingBottom="16dp" />

        <!-- Mode toggle: Sjabloon | Enkel -->
        <com.google.android.material.tabs.TabLayout
            android:id="@+id/tab_mode"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginHorizontal="24dp"
            android:layout_marginBottom="16dp"
            app:tabMode="fixed"
            app:tabGravity="fill"
            style="@style/Widget.Material3.TabLayout.Secondary" />

        <!-- ===== TEMPLATE PANEL ===== -->
        <LinearLayout
            android:id="@+id/panel_template"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:visibility="visible">

            <!-- Template picker -->
            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/rv_templates"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingHorizontal="16dp"
                android:nestedScrollingEnabled="false" />

            <!-- Anchor date -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/herinnering_verwysingsdatum"
                android:textAppearance="?attr/textAppearanceLabelMedium"
                android:paddingHorizontal="24dp"
                android:paddingTop="16dp"
                android:paddingBottom="4dp" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_anchor_date"
                style="@style/Widget.Material3.Button.OutlinedButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginHorizontal="24dp"
                android:text="@string/datum_vandag" />

            <!-- Preview -->
            <TextView
                android:id="@+id/tv_preview_header"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/herinnering_voorskou"
                android:textAppearance="?attr/textAppearanceLabelMedium"
                android:paddingHorizontal="24dp"
                android:paddingTop="16dp"
                android:paddingBottom="4dp"
                android:visibility="gone" />

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/rv_preview"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingHorizontal="16dp"
                android:nestedScrollingEnabled="false"
                android:visibility="gone" />

        </LinearLayout>

        <!-- ===== AD-HOC PANEL ===== -->
        <LinearLayout
            android:id="@+id/panel_adhoc"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingHorizontal="24dp"
            android:visibility="gone">

            <com.google.android.material.textfield.TextInputLayout
                android:id="@+id/til_title"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="@string/herinnering_titel"
                style="@style/Widget.Material3.TextInputLayout.OutlinedBox">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/et_title"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:imeOptions="actionNext"
                    android:inputType="textCapSentences" />

            </com.google.android.material.textfield.TextInputLayout>

            <com.google.android.material.textfield.TextInputLayout
                android:id="@+id/til_note"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="@string/herinnering_nota"
                android:layout_marginTop="8dp"
                style="@style/Widget.Material3.TextInputLayout.OutlinedBox">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/et_note"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="textMultiLine|textCapSentences"
                    android:minLines="2" />

            </com.google.android.material.textfield.TextInputLayout>

            <!-- Due date -->
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_due_date"
                style="@style/Widget.Material3.Button.OutlinedButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:text="@string/datum_vandag" />

            <!-- Timed toggle -->
            <com.google.android.material.materialswitch.MaterialSwitch
                android:id="@+id/switch_timed"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:text="@string/herinnering_spesifieke_tyd" />

            <!-- Time picker row (shown when timed) -->
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_due_time"
                style="@style/Widget.Material3.Button.OutlinedButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="@string/herinnering_kies_tyd"
                android:visibility="gone" />

        </LinearLayout>

        <!-- Confirm button (always visible) -->
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_bevestig"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginHorizontal="24dp"
            android:layout_marginTop="20dp"
            android:text="@string/herinnering_bevestig"
            android:enabled="false" />

    </LinearLayout>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

### `item_template_picker.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="8dp"
    android:layout_marginVertical="4dp"
    app:cardCornerRadius="8dp"
    app:cardElevation="1dp"
    app:checkable="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp">

        <TextView
            android:id="@+id/tv_template_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceTitleSmall" />

        <TextView
            android:id="@+id/tv_template_description"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceBodySmall"
            android:alpha="0.7"
            android:layout_marginTop="2dp" />

        <TextView
            android:id="@+id/tv_template_steps"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceLabelSmall"
            android:alpha="0.5"
            android:layout_marginTop="4dp" />

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

### `item_reminder_preview.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:paddingHorizontal="8dp"
    android:paddingVertical="6dp"
    android:gravity="center_vertical">

    <!-- Dot indicator -->
    <View
        android:id="@+id/view_dot"
        android:layout_width="8dp"
        android:layout_height="8dp"
        android:background="@drawable/bg_badge_red"
        android:layout_marginEnd="12dp" />

    <TextView
        android:id="@+id/tv_preview_title"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textAppearance="?attr/textAppearanceBodySmall" />

    <TextView
        android:id="@+id/tv_preview_date"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textAppearance="?attr/textAppearanceLabelSmall"
        android:alpha="0.6"
        android:layout_marginStart="8dp" />

</LinearLayout>
```

### `item_pending_reminder_mini.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingVertical="6dp"
    android:paddingHorizontal="16dp">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/tv_mini_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceBodySmall"
            android:maxLines="1"
            android:ellipsize="end" />

        <TextView
            android:id="@+id/tv_mini_date"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceLabelSmall"
            android:alpha="0.6" />

    </LinearLayout>

    <!-- Overdue indicator -->
    <TextView
        android:id="@+id/tv_mini_overdue"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/bediening_agterstallig"
        android:textSize="9sp"
        android:textColor="@android:color/white"
        android:background="@drawable/bg_badge_red"
        android:paddingHorizontal="4dp"
        android:paddingVertical="2dp"
        android:visibility="gone"
        android:layout_marginEnd="8dp" />

    <!-- Complete button -->
    <ImageButton
        android:id="@+id/btn_mini_voltooi"
        android:layout_width="32dp"
        android:layout_height="32dp"
        android:src="@drawable/ic_check_circle"
        android:contentDescription="@string/herinnering_voltooi"
        android:background="?attr/selectableItemBackgroundBorderless" />

</LinearLayout>
```

---

## E4 — StelHerinneringBottomSheet

New file: `ui/bottomsheets/StelHerinneringBottomSheet.kt`

```kotlin
package za.co.jpsoft.winkerkreader.ui.bottomsheets

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateWithSteps
import za.co.jpsoft.winkerkreader.databinding.BottomSheetStelHerinneringBinding
import za.co.jpsoft.winkerkreader.ui.adapters.ReminderPreviewAdapter
import za.co.jpsoft.winkerkreader.ui.adapters.TemplatePickerAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class StelHerinneringBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetStelHerinneringBinding? = null
    private val binding get() = _binding!!

    // Shared ViewModel — same instance as LidmaatDetailActivity
    private val viewModel: LidmaatDetailPastoralViewModel by activityViewModels()

    // State
    private var mode = Mode.TEMPLATE
    private var selectedTemplateId: String? = null
    private var anchorDate: LocalDate = LocalDate.now()
    private var dueDate: LocalDate = LocalDate.now()
    private var dueTime: LocalTime? = null
    private var isTimedMode = false

    private lateinit var templateAdapter: TemplatePickerAdapter
    private lateinit var previewAdapter: ReminderPreviewAdapter

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    enum class Mode { TEMPLATE, ADHOC }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetStelHerinneringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupModeTabs()
        setupTemplatePanel()
        setupAdHocPanel()
        setupConfirmButton()
        observeViewModel()
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
                binding.panelAdhoc.visibility =
                    if (mode == Mode.ADHOC) View.VISIBLE else View.GONE
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
        templateAdapter = TemplatePickerAdapter { template ->
            selectedTemplateId = template.template.templateId
            refreshPreview()
            updateConfirmButton()
        }

        binding.rvTemplates.apply {
            adapter = templateAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }

        previewAdapter = ReminderPreviewAdapter()
        binding.rvPreview.apply {
            adapter = previewAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
        }

        // Anchor date picker
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
        val items = viewModel.previewTemplateDates(templateId, anchorDate)
        previewAdapter.submitList(items)
        binding.tvPreviewHeader.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvPreview.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
    }

    // -------------------------------------------------------------------------
    // Ad-hoc panel
    // -------------------------------------------------------------------------

    private fun setupAdHocPanel() {
        // Due date
        dueDate = LocalDate.now()
        binding.btnDueDate.text = getString(R.string.datum_vandag)
        binding.btnDueDate.setOnClickListener { showDueDatePicker() }

        // Timed switch
        binding.switchTimed.setOnCheckedChangeListener { _, checked ->
            isTimedMode = checked
            binding.btnDueTime.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) dueTime = null
        }

        // Time picker
        binding.btnDueTime.setOnClickListener { showTimePicker() }

        // Enable confirm when title not blank
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
                binding.btnDueTime.text =
                    LocalTime.of(hour, minute)
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
            },
            current.hour,
            current.minute,
            true   // 24-hour format
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
                    viewModel.createFromTemplate(templateId, anchorDate)
                    dismiss()
                }
                Mode.ADHOC -> {
                    val title = binding.etTitle.text?.toString()?.trim()
                    if (title.isNullOrBlank()) return@setOnClickListener
                    viewModel.createAdHoc(
                        title        = title,
                        note         = binding.etNote.text?.toString()?.trim()?.ifBlank { null },
                        dueDate      = dueDate,
                        scheduleType = if (isTimedMode) ScheduleType.TIMED else ScheduleType.DATE_ONLY,
                        dueTime      = dueTime
                    )
                    dismiss()
                }
            }
        }
    }

    private fun updateConfirmButton() {
        binding.btnBevestig.isEnabled = when (mode) {
            Mode.TEMPLATE -> selectedTemplateId != null
            Mode.ADHOC    -> !binding.etTitle.text.isNullOrBlank()
        }
    }

    // -------------------------------------------------------------------------
    // ViewModel observers
    // -------------------------------------------------------------------------

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.templates.collect { templates ->
                templateAdapter.submitList(templates)
            }
        }
    }

    companion object {
        const val TAG = "StelHerinneringBottomSheet"

        fun newInstance() = StelHerinneringBottomSheet()
    }
}
```

---

## E5 — Adapters

### `TemplatePickerAdapter`

New file: `ui/adapters/TemplatePickerAdapter.kt`

```kotlin
package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateWithSteps
import za.co.jpsoft.winkerkreader.databinding.ItemTemplatePickerBinding

class TemplatePickerAdapter(
    private val onSelected: (TemplateWithSteps) -> Unit
) : ListAdapter<TemplateWithSteps, TemplatePickerAdapter.ViewHolder>(DIFF) {

    private var selectedId: String? = null

    inner class ViewHolder(
        private val binding: ItemTemplatePickerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateWithSteps) {
            binding.tvTemplateTitle.text = item.template.titleAf
            binding.tvTemplateDescription.text = item.template.descriptionAf
            binding.tvTemplateSteps.text =
                binding.root.context.resources.getQuantityString(
                    R.plurals.herinnering_stap_count,
                    item.steps.size,
                    item.steps.size
                )

            // Highlight selected card
            binding.root.isChecked = item.template.templateId == selectedId

            binding.root.setOnClickListener {
                val previous = selectedId
                selectedId = item.template.templateId
                // Refresh previous and current to update checked state
                currentList.indexOfFirst { it.template.templateId == previous }
                    .takeIf { it >= 0 }?.let { notifyItemChanged(it) }
                currentList.indexOfFirst { it.template.templateId == selectedId }
                    .takeIf { it >= 0 }?.let { notifyItemChanged(it) }
                onSelected(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemTemplatePickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TemplateWithSteps>() {
            override fun areItemsTheSame(a: TemplateWithSteps, b: TemplateWithSteps) =
                a.template.templateId == b.template.templateId
            override fun areContentsTheSame(a: TemplateWithSteps, b: TemplateWithSteps) =
                a == b
        }
    }
}
```

**Add to `res/values/plurals.xml`** (create if it doesn't exist):

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <plurals name="herinnering_stap_count">
        <item quantity="one">%d stap</item>
        <item quantity="other">%d stappe</item>
    </plurals>
    <plurals name="herinnering_created_count">
        <item quantity="one">%d herinnering gestel</item>
        <item quantity="other">%d herinnerings gestel</item>
    </plurals>
</resources>
```

### `ReminderPreviewAdapter`

New file: `ui/adapters/ReminderPreviewAdapter.kt`

```kotlin
package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.ItemReminderPreviewBinding
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReminderPreviewAdapter :
    ListAdapter<LidmaatDetailPastoralViewModel.PreviewItem, ReminderPreviewAdapter.ViewHolder>(DIFF) {

    private val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    inner class ViewHolder(
        private val binding: ItemReminderPreviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LidmaatDetailPastoralViewModel.PreviewItem) {
            binding.tvPreviewTitle.text = item.stepTitle
            binding.tvPreviewDate.text = item.dueDate.format(formatter)

            // Grey out past dates — warn but still allow creation
            val alpha = if (item.isInPast) 0.4f else 1f
            binding.tvPreviewTitle.alpha = alpha
            binding.tvPreviewDate.alpha = alpha
            binding.viewDot.alpha = alpha
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemReminderPreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LidmaatDetailPastoralViewModel.PreviewItem>() {
            override fun areItemsTheSame(
                a: LidmaatDetailPastoralViewModel.PreviewItem,
                b: LidmaatDetailPastoralViewModel.PreviewItem
            ) = a.stepTitle == b.stepTitle && a.dueDate == b.dueDate
            override fun areContentsTheSame(
                a: LidmaatDetailPastoralViewModel.PreviewItem,
                b: LidmaatDetailPastoralViewModel.PreviewItem
            ) = a == b
        }
    }
}
```

### `PendingReminderMiniAdapter`

New file: `ui/adapters/PendingReminderMiniAdapter.kt`

```kotlin
package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.databinding.ItemPendingReminderMiniBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PendingReminderMiniAdapter(
    private val onComplete: (reminderId: String) -> Unit
) : ListAdapter<FollowUpReminderEntity, PendingReminderMiniAdapter.ViewHolder>(DIFF) {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val zoneId = ZoneId.systemDefault()

    inner class ViewHolder(
        private val binding: ItemPendingReminderMiniBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FollowUpReminderEntity) {
            binding.tvMiniTitle.text = item.title

            val dueDate = Instant.ofEpochMilli(item.dueDateUtc)
                .atZone(zoneId).toLocalDate()
            val today = LocalDate.now(zoneId)
            val isOverdue = dueDate.isBefore(today)

            binding.tvMiniDate.text = when {
                dueDate == today         -> binding.root.context.getString(R.string.datum_vandag)
                dueDate == today.minusDays(1) -> binding.root.context.getString(R.string.datum_gister)
                else                     -> dueDate.format(dateFormatter)
            }

            binding.tvMiniOverdue.visibility = if (isOverdue) View.VISIBLE else View.GONE

            binding.btnMiniVoltooi.setOnClickListener {
                onComplete(item.reminderId)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemPendingReminderMiniBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FollowUpReminderEntity>() {
            override fun areItemsTheSame(a: FollowUpReminderEntity, b: FollowUpReminderEntity) =
                a.reminderId == b.reminderId
            override fun areContentsTheSame(a: FollowUpReminderEntity, b: FollowUpReminderEntity) =
                a == b
        }
    }
}
```

---

## E1 — Layout block to insert into `lidmaat_detail.xml`

Find the contact buttons row (the row containing Bel / WhatsApp / Epos buttons) and insert this block **immediately after** it:

```xml
<!-- ===== BEDIENING BLOCK — Sprint E ===== -->
<LinearLayout
    android:id="@+id/detail_bediening_block"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingTop="8dp"
    android:paddingBottom="4dp">

    <!-- Divider -->
    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="?attr/dividerColor"
        android:layout_marginBottom="8dp" />

    <!-- Header row: label + Stel herinnering button -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingHorizontal="16dp">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/detail_bediening"
            android:textAppearance="?attr/textAppearanceTitleSmall" />

        <TextView
            android:id="@+id/detail_herinnering_count"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceLabelSmall"
            android:alpha="0.6"
            android:layout_marginEnd="8dp"
            android:visibility="gone" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/detail_stel_herinnering"
            style="@style/Widget.Material3.Button.TonalButton"
            android:layout_width="wrap_content"
            android:layout_height="32dp"
            android:text="@string/detail_stel_herinnering"
            android:textSize="12sp" />

    </LinearLayout>

    <!-- Pending reminder mini-list -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/detail_pending_reminders"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxHeight="150dp"
        android:nestedScrollingEnabled="false"
        android:paddingTop="4dp"
        android:visibility="gone" />

</LinearLayout>
<!-- ===== END BEDIENING BLOCK ===== -->
```

---

## E6 — Wire into LidmaatDetailActivity

Add to `LidmaatDetailActivity.kt`. These are **additive** changes only — nothing existing is modified.

```kotlin
// 1. Add field declarations
private val pastoralViewModel: LidmaatDetailPastoralViewModel by viewModels {
    LidmaatDetailPastoralViewModelFactory(
        context    = this,
        memberGuid = memberGuid   // ← use whatever field holds the GUID already
    )
}
private lateinit var miniAdapter: PendingReminderMiniAdapter

// 2. In onCreate(), after existing setup:
private fun setupBedieningBlock() {
    miniAdapter = PendingReminderMiniAdapter { reminderId ->
        pastoralViewModel.completeReminder(reminderId)
    }
    binding.detailPendingReminders.apply {
        adapter = miniAdapter
        layoutManager = LinearLayoutManager(this@LidmaatDetailActivity)
        setHasFixedSize(false)
    }

    binding.detailStelHerinnering.setOnClickListener {
        StelHerinneringBottomSheet.newInstance()
            .show(supportFragmentManager, StelHerinneringBottomSheet.TAG)
    }

    // Observe pending reminders
    lifecycleScope.launch {
        pastoralViewModel.pendingReminders.collect { reminders ->
            miniAdapter.submitList(reminders)
            binding.detailPendingReminders.visibility =
                if (reminders.isEmpty()) View.GONE else View.VISIBLE
            binding.detailHerinnering_count.visibility =
                if (reminders.isEmpty()) View.GONE else View.VISIBLE
            binding.detailHerinnering_count.text =
                resources.getQuantityString(
                    R.plurals.herinnering_created_count,
                    reminders.size,
                    reminders.size
                )
        }
    }

    // Toast on creation
    lifecycleScope.launch {
        pastoralViewModel.created.collect { count ->
            val msg = resources.getQuantityString(
                R.plurals.herinnering_created_count, count, count
            )
            Toast.makeText(this@LidmaatDetailActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Error Snackbar
    lifecycleScope.launch {
        pastoralViewModel.error.collect { message ->
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        }
    }
}

// 3. Call from onCreate():
//    setupBedieningBlock()
```

**Imports to add:**

```kotlin
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.ui.adapters.PendingReminderMiniAdapter
import za.co.jpsoft.winkerkreader.ui.bottomsheets.StelHerinneringBottomSheet
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModelFactory
```

---

## E7 — Options menu item

If `LidmaatDetailActivity` already has a menu XML, add to it:

```xml
<item
    android:id="@+id/action_stel_herinnering"
    android:title="@string/detail_stel_herinnering"
    app:showAsAction="never" />
```

Handle in `onOptionsItemSelected()`:

```kotlin
R.id.action_stel_herinnering -> {
    StelHerinneringBottomSheet.newInstance()
        .show(supportFragmentManager, StelHerinneringBottomSheet.TAG)
    true
}
```

---

## E8 — Strings

```xml
<!-- Sprint E additions -->
<string name="detail_bediening">Bediening</string>
<string name="detail_stel_herinnering">Stel herinnering</string>
<string name="herinnering_stel_sjabloon">Sjabloon</string>
<string name="herinnering_enkel">Enkel herinnering</string>
<string name="herinnering_verwysingsdatum">Verwysingsdatum</string>
<string name="herinnering_voorskou">Voorskou</string>
<string name="herinnering_bevestig">Bevestig</string>
<string name="herinnering_titel">Titel</string>
<string name="herinnering_nota">Nota (opsioneel)</string>
<string name="herinnering_spesifieke_tyd">Spesifieke tyd</string>
<string name="herinnering_kies_tyd">Kies tyd</string>
```

---

## Decision log

| Decision | Rationale |
|----------|-----------|
| Separate `LidmaatDetailPastoralViewModel` (not merged into existing ViewModel) | No risk of breaking existing member detail; avoids needing to see full existing ViewModel code; clean separation of concerns |
| `activityViewModels()` in BottomSheet | Shares the same instance as `LidmaatDetailActivity` so `created` / `error` events survive the sheet dismiss and post to the Activity's observers |
| Template preview computed in ViewModel (`previewTemplateDates`) | Pure function, no DB call, runs synchronously on the UI thread — fast enough and keeps the BottomSheet simple |
| Past dates alpha'd in preview but still allowed | A NA_STERF template created a week late should still create the full sequence — suppressing past steps silently would confuse the pastor |
| Mini-list `maxHeight="150dp"` with `nestedScrollingEnabled="false"` | Shows ~3 reminders inline without the member detail scroll fighting the RecyclerView; full list is in BedieningActivity |
| DatePickerDialog + TimePickerDialog (not Material Date/Time Picker) | Existing app uses `DatePickerDialog` pattern consistently; Material pickers require extra dependency and don't match existing UX |

---

## Sprint E → Sprint F handoff

`StelHerinneringBottomSheet` calls `viewModel.createFromTemplate()` which calls `PastoralReminderRepository.createFromTemplate()` which already calls `PastoralDatabaseBackup.backupDebounced()` (Sprint A). Sprint F adds the reverse — importing `wkr_pastoral.db` from PC — so no Sprint E changes are needed for that path.
