# Pastoral CRM — Sprint D: BedieningActivity UI Design

> **Status:** Ready for implementation  
> **Depends on:** Sprints A · B · C  
> **As-built flows:** [`architecture.md`](architecture.md) §8.2 (Vandag dashboard)

---

## Task table

| # | Task | Files |
|---|------|-------|
| D0 | Add `observeDueThisWeek()` to DAO | `FollowUpReminderDao.kt` |
| D1 | Layouts | `activity_bediening.xml`, `fragment_bediening_vandag.xml`, `item_bediening_reminder.xml` |
| D2 | ViewModel + Factory | `BedieningViewModel.kt`, `BedieningViewModelFactory.kt` |
| D3 | Activity + Fragment | `BedieningActivity.kt`, `BedieningVandagFragment.kt` |
| D4 | Adapter | `BedieningReminderAdapter.kt` |
| D5 | Main menu badge | `menu_main.xml`, `MainActivity.kt` |
| D6 | Strings + Manifest | `strings.xml`, `AndroidManifest.xml` |

**Implement in order: D0 → D2 → D1 → D4 → D3 → D5 → D6**

---

## D0 — Add `observeDueThisWeek()` to FollowUpReminderDao

The "Hierdie week" chip needs upcoming reminders (tomorrow through end of day +6).
This is the only DAO change in Sprint D.

```kotlin
// Add to FollowUpReminderDao.kt

/**
 * Reminders due strictly after today through [endOfWeekUtc].
 * Does NOT include today (covered by [observeDueToday]) or overdue.
 * Used by the "Hierdie week" filter chip in BedieningVandagFragment.
 */
@Query(
    """
    SELECT * FROM follow_up_reminders
    WHERE status = :pendingStatus
      AND dueDateUtc > :endOfTodayUtc
      AND dueDateUtc <= :endOfWeekUtc
    ORDER BY dueDateUtc ASC
    """
)
fun observeDueThisWeek(
    endOfTodayUtc: Long,
    endOfWeekUtc: Long,
    pendingStatus: String = ReminderStatus.PENDING.name
): Flow<List<FollowUpReminderEntity>>
```

---

## D2 — BedieningViewModel + Factory

New file: `ui/viewmodels/BedieningViewModel.kt`

The ViewModel is the heart of Sprint D — all other classes are plumbing around it.

```kotlin
package za.co.jpsoft.winkerkreader.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderWithMember
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BedieningViewModel(
    private val repository: PastoralReminderRepository
) : ViewModel() {

    // -------------------------------------------------------------------------
    // Filter state
    // -------------------------------------------------------------------------

    enum class Filter { VANDAG, AGTERSTALLIG, HIERDIE_WEEK }

    private val _activeFilter = MutableStateFlow(Filter.VANDAG)
    val activeFilter: StateFlow<Filter> = _activeFilter.asStateFlow()

    fun setFilter(filter: Filter) {
        _activeFilter.value = filter
    }

    // -------------------------------------------------------------------------
    // Day bounds (computed once per ViewModel lifetime)
    // Sprint A tech debt: reset at midnight — acceptable for Phase 1
    // -------------------------------------------------------------------------

    private val zoneId = ZoneId.systemDefault()
    private val today = LocalDate.now(zoneId)
    private val startOfTodayUtc = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    private val endOfTodayUtc   = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
    private val endOfWeekUtc    = today.plusDays(7).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
    private val nowUtc get()    = System.currentTimeMillis()   // re-evaluated on each access

    // -------------------------------------------------------------------------
    // Dashboard (overdue + today) — from existing repository Flow
    // -------------------------------------------------------------------------

    private val dashboard = repository.observeVandagDashboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // -------------------------------------------------------------------------
    // "Hierdie week" — separate flow from DAO
    // -------------------------------------------------------------------------

    private val hierDieWeekItems: Flow<List<ReminderWithMember>> =
        repository.observeDueThisWeek(endOfTodayUtc, endOfWeekUtc)
            .flowOn(kotlinx.coroutines.Dispatchers.IO)

    // -------------------------------------------------------------------------
    // Items shown in RecyclerView — driven by active filter
    // -------------------------------------------------------------------------

    val displayItems: StateFlow<List<ReminderWithMember>> = combine(
        _activeFilter,
        dashboard,
        hierDieWeekItems
    ) { filter, dash, week ->
        when (filter) {
            Filter.VANDAG        -> dash?.dueToday.orEmpty()
            Filter.AGTERSTALLIG  -> dash?.overdue.orEmpty()
            Filter.HIERDIE_WEEK  -> week
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -------------------------------------------------------------------------
    // Counts for chips and tab badge
    // -------------------------------------------------------------------------

    val todayCount: StateFlow<Int> = dashboard
        .map { it?.todayCount ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val overdueCount: StateFlow<Int> = dashboard
        .map { it?.overdueCount ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Tab badge: overdue + today combined — the number the pastor must action. */
    val tabBadgeCount: StateFlow<Int> = dashboard
        .map { (it?.todayCount ?: 0) + (it?.overdueCount ?: 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val isEmpty: StateFlow<Boolean> = displayItems
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // -------------------------------------------------------------------------
    // Deep-link scrolling — set by Activity when opened from notification
    // -------------------------------------------------------------------------

    private val _scrollToReminderId = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val scrollToReminderId: SharedFlow<String> = _scrollToReminderId.asSharedFlow()

    fun requestScrollTo(reminderId: String) {
        _scrollToReminderId.tryEmit(reminderId)
    }

    // -------------------------------------------------------------------------
    // Actions — delegate to repository on IO dispatcher
    // -------------------------------------------------------------------------

    fun completeReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                repository.completeReminder(reminderId)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie herinnering voltooi nie")
            }
        }
    }

    fun snoozeReminder(reminderId: String, snoozeOption: SnoozeOption) {
        val until = when (snoozeOption) {
            SnoozeOption.TOMORROW   -> LocalDateTime.now().plusDays(1)
            SnoozeOption.THREE_DAYS -> LocalDateTime.now().plusDays(3)
            SnoozeOption.ONE_WEEK   -> LocalDateTime.now().plusDays(7)
        }.withHour(8).withMinute(0).withSecond(0).withNano(0)

        viewModelScope.launch {
            try {
                repository.snoozeReminder(reminderId, until)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie herinnering uitstel nie")
            }
        }
    }

    fun addToCalendar(reminderId: String) {
        viewModelScope.launch {
            try {
                repository.syncToCalendar(reminderId)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie by kalender voeg nie")
            }
        }
    }

    // -------------------------------------------------------------------------
    // One-shot error events for Snackbar
    // -------------------------------------------------------------------------

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    // -------------------------------------------------------------------------
    // Snooze options
    // -------------------------------------------------------------------------

    enum class SnoozeOption { TOMORROW, THREE_DAYS, ONE_WEEK }
}
```

**Add `observeDueThisWeek()` to `PastoralReminderRepository`:**

```kotlin
// Add to PastoralReminderRepository.kt

fun observeDueThisWeek(endOfTodayUtc: Long, endOfWeekUtc: Long): Flow<List<ReminderWithMember>> {
    return reminderDao.observeDueThisWeek(endOfTodayUtc, endOfWeekUtc)
        .map { reminders -> reminders.map { toReminderWithMember(it) } }
        .flowOn(Dispatchers.IO)
}
```

**Factory:**

New file: `ui/viewmodels/BedieningViewModelFactory.kt`

```kotlin
package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository

class BedieningViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BedieningViewModel::class.java)) {
            return BedieningViewModel(
                PastoralReminderRepository.create(context.applicationContext)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
```

---

## D1 — Layouts

### `activity_bediening.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/bediening_appbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/bediening_toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:title="@string/bediening_title" />

        <com.google.android.material.tabs.TabLayout
            android:id="@+id/bediening_tab_layout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:tabGravity="fill"
            app:tabMode="fixed" />

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/bediening_view_pager"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

### `fragment_bediening_vandag.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Filter chips -->
    <HorizontalScrollView
        android:id="@+id/chip_scroll"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:scrollbars="none"
        android:paddingHorizontal="12dp"
        android:paddingTop="8dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <com.google.android.material.chip.ChipGroup
            android:id="@+id/chip_group_filter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            app:singleSelection="true"
            app:selectionRequired="true">

            <com.google.android.material.chip.Chip
                android:id="@+id/chip_vandag"
                style="@style/Widget.Material3.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/bediening_filter_vandag"
                android:checked="true" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chip_agterstallig"
                style="@style/Widget.Material3.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/bediening_filter_agterstallig" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chip_hierdie_week"
                style="@style/Widget.Material3.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/bediening_filter_hierdie_week" />

        </com.google.android.material.chip.ChipGroup>
    </HorizontalScrollView>

    <!-- Reminder list -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_bediening_reminders"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:clipToPadding="false"
        android:paddingBottom="80dp"
        app:layout_constraintTop_toBottomOf="@id/chip_scroll"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- Empty state -->
    <LinearLayout
        android:id="@+id/layout_empty_state"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center"
        android:visibility="gone"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <ImageView
            android:layout_width="64dp"
            android:layout_height="64dp"
            android:src="@drawable/ic_check_circle"
            android:alpha="0.4" />

        <TextView
            android:id="@+id/tv_empty_state"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="@string/bediening_geen_herinnerings"
            android:textAppearance="?attr/textAppearanceBodyMedium"
            android:alpha="0.6" />

    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
```

### `item_bediening_reminder.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="12dp"
    android:layout_marginVertical="4dp"
    app:cardElevation="2dp"
    app:cardCornerRadius="8dp">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="12dp">

        <!-- Member photo -->
        <ImageView
            android:id="@+id/iv_member_photo"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:scaleType="centerCrop"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintStart_toStartOf="parent" />

        <!-- Overdue badge -->
        <TextView
            android:id="@+id/tv_overdue_badge"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/bediening_agterstallig"
            android:textSize="10sp"
            android:textColor="@color/white"
            android:background="@drawable/bg_badge_red"
            android:paddingHorizontal="6dp"
            android:paddingVertical="2dp"
            android:visibility="gone"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintEnd_toEndOf="parent" />

        <!-- Member name -->
        <TextView
            android:id="@+id/tv_member_name"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceTitleSmall"
            android:maxLines="1"
            android:ellipsize="end"
            app:layout_constraintTop_toTopOf="@id/iv_member_photo"
            app:layout_constraintStart_toEndOf="@id/iv_member_photo"
            app:layout_constraintEnd_toStartOf="@id/tv_overdue_badge"
            android:layout_marginStart="12dp"
            android:layout_marginEnd="8dp" />

        <!-- Reminder title -->
        <TextView
            android:id="@+id/tv_reminder_title"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceBodySmall"
            android:maxLines="2"
            android:ellipsize="end"
            app:layout_constraintTop_toBottomOf="@id/tv_member_name"
            app:layout_constraintStart_toEndOf="@id/iv_member_photo"
            app:layout_constraintEnd_toEndOf="parent"
            android:layout_marginStart="12dp"
            android:layout_marginTop="2dp" />

        <!-- Due date -->
        <TextView
            android:id="@+id/tv_due_date"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceBodySmall"
            android:alpha="0.6"
            app:layout_constraintTop_toBottomOf="@id/tv_reminder_title"
            app:layout_constraintStart_toEndOf="@id/iv_member_photo"
            app:layout_constraintEnd_toEndOf="parent"
            android:layout_marginStart="12dp"
            android:layout_marginTop="2dp" />

        <!-- Action divider -->
        <View
            android:id="@+id/action_divider"
            android:layout_width="0dp"
            android:layout_height="1dp"
            android:background="?attr/dividerColor"
            android:layout_marginTop="10dp"
            app:layout_constraintTop_toBottomOf="@id/tv_due_date"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent" />

        <!-- Action row -->
        <LinearLayout
            android:id="@+id/layout_actions"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginTop="4dp"
            app:layout_constraintTop_toBottomOf="@id/action_divider"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintBottom_toBottomOf="parent">

            <ImageButton
                android:id="@+id/btn_bel"
                android:layout_width="0dp"
                android:layout_height="36dp"
                android:layout_weight="1"
                android:src="@drawable/ic_phone"
                android:contentDescription="@string/herinnering_bel"
                style="@style/Widget.Material3.Button.IconButton" />

            <ImageButton
                android:id="@+id/btn_whatsapp"
                android:layout_width="0dp"
                android:layout_height="36dp"
                android:layout_weight="1"
                android:src="@drawable/ic_whatsapp"
                android:contentDescription="@string/herinnering_whatsapp"
                style="@style/Widget.Material3.Button.IconButton" />

            <Button
                android:id="@+id/btn_voltooi"
                android:layout_width="0dp"
                android:layout_height="36dp"
                android:layout_weight="2"
                android:text="@string/herinnering_voltooi"
                style="@style/Widget.Material3.Button.TonalButton" />

            <ImageButton
                android:id="@+id/btn_overflow"
                android:layout_width="36dp"
                android:layout_height="36dp"
                android:src="@drawable/ic_more_vert"
                android:contentDescription="@string/meer_opsies"
                style="@style/Widget.Material3.Button.IconButton" />

        </LinearLayout>

    </androidx.constraintlayout.widget.ConstraintLayout>
</com.google.android.material.card.MaterialCardView>
```

---

## D4 — BedieningReminderAdapter

New file: `ui/adapters/BedieningReminderAdapter.kt`

```kotlin
package za.co.jpsoft.winkerkreader.ui.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderWithMember
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.databinding.ItemBedieningReminderBinding
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class BedieningReminderAdapter(
    private val onVoltooi:      (reminderId: String) -> Unit,
    private val onSnooze:       (reminderId: String) -> Unit,
    private val onAddCalendar:  (reminderId: String) -> Unit,
    private val onOpenMember:   (memberGuid: String) -> Unit
) : ListAdapter<ReminderWithMember, BedieningReminderAdapter.ViewHolder>(DIFF) {

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    inner class ViewHolder(
        private val binding: ItemBedieningReminderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReminderWithMember) {
            val reminder = item.reminder
            val today = java.time.LocalDate.now(ZoneId.systemDefault())
            val dueDate = Instant.ofEpochMilli(reminder.dueDateUtc)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val isOverdue = dueDate.isBefore(today)

            // Member name
            binding.tvMemberName.text = item.displayName

            // Reminder title
            binding.tvReminderTitle.text = reminder.title

            // Due date text
            binding.tvDueDate.text = formatDueDate(reminder)

            // Overdue badge
            binding.tvOverdueBadge.visibility =
                if (isOverdue) View.VISIBLE else View.GONE

            // Photo — Glide with placeholder
            if (!item.photoPath.isNullOrBlank()) {
                Glide.with(binding.ivMemberPhoto)
                    .load(item.photoPath)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(binding.ivMemberPhoto)
            } else {
                binding.ivMemberPhoto.setImageResource(R.drawable.ic_person_placeholder)
            }

            // Bel
            binding.btnBel.isEnabled = !item.cellphone.isNullOrBlank()
            binding.btnBel.setOnClickListener {
                item.cellphone?.let { phone ->
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    it.context.startActivity(intent)
                }
            }

            // WhatsApp
            binding.btnWhatsapp.isEnabled = !item.cellphone.isNullOrBlank()
            binding.btnWhatsapp.setOnClickListener {
                item.cellphone?.let { phone ->
                    val wa = formatWhatsAppNumber(phone)
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/$wa"))
                    it.context.startActivity(intent)
                }
            }

            // Voltooi
            binding.btnVoltooi.setOnClickListener {
                onVoltooi(reminder.reminderId)
            }

            // Overflow menu
            binding.btnOverflow.setOnClickListener { anchor ->
                showOverflowMenu(anchor, item)
            }
        }

        private fun showOverflowMenu(anchor: View, item: ReminderWithMember) {
            val menu = PopupMenu(anchor.context, anchor)
            menu.inflate(R.menu.menu_bediening_reminder_overflow)

            // Hide "Voeg by kalender" if already synced
            menu.menu.findItem(R.id.action_voeg_by_kalender)?.isVisible =
                !item.reminder.calendarSynced

            menu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_uitstel       -> { onSnooze(item.reminder.reminderId); true }
                    R.id.action_voeg_by_kalender -> { onAddCalendar(item.reminder.reminderId); true }
                    R.id.action_maak_lid_oop  -> { onOpenMember(item.reminder.memberGuid); true }
                    else -> false
                }
            }
            menu.show()
        }

        // -------------------------------------------------------------------------
        // Formatting helpers
        // -------------------------------------------------------------------------

        private fun formatDueDate(reminder: za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity): String {
            val zoneId = ZoneId.systemDefault()
            val dueDate = Instant.ofEpochMilli(reminder.dueDateUtc)
                .atZone(zoneId).toLocalDate()
            val today = java.time.LocalDate.now(zoneId)

            val dateStr = when {
                dueDate == today ->
                    binding.root.context.getString(R.string.datum_vandag)
                dueDate == today.minusDays(1) ->
                    binding.root.context.getString(R.string.datum_gister)
                else -> {
                    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                    dueDate.format(formatter)
                }
            }

            val scheduleType = ScheduleType.fromStored(reminder.scheduleType)
            return if (scheduleType == ScheduleType.TIMED) {
                val time = Instant.ofEpochMilli(reminder.dueDateUtc)
                    .atZone(zoneId).toLocalTime()
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                "$dateStr $time"
            } else {
                dateStr
            }
        }

        private fun formatWhatsAppNumber(phone: String): String {
            // Strip spaces, dashes; ensure international format for SA (+27)
            return phone.replace(Regex("[^0-9+]"), "")
                .let {
                    if (it.startsWith("0")) "+27${it.drop(1)}" else it
                }
                .trimStart('+')   // wa.me requires digits only
        }
    }

    // -------------------------------------------------------------------------
    // ListAdapter overrides
    // -------------------------------------------------------------------------

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBedieningReminderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // -------------------------------------------------------------------------
    // Scroll-to helper (used for notification deep link)
    // -------------------------------------------------------------------------

    fun positionOf(reminderId: String): Int {
        return currentList.indexOfFirst { it.reminder.reminderId == reminderId }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ReminderWithMember>() {
            override fun areItemsTheSame(a: ReminderWithMember, b: ReminderWithMember) =
                a.reminder.reminderId == b.reminder.reminderId

            override fun areContentsTheSame(a: ReminderWithMember, b: ReminderWithMember) =
                a == b
        }
    }
}
```

**Add overflow menu XML:** `res/menu/menu_bediening_reminder_overflow.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/action_uitstel"
          android:title="@string/herinnering_uitstel_kies" />
    <item android:id="@+id/action_voeg_by_kalender"
          android:title="@string/herinnering_voeg_by_kalender" />
    <item android:id="@+id/action_maak_lid_oop"
          android:title="@string/herinnering_maak_lid_oop" />
</menu>
```

---

## D3 — BedieningActivity + BedieningVandagFragment

### BedieningActivity

New file: `ui/activities/BedieningActivity.kt`

```kotlin
package za.co.jpsoft.winkerkreader.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.ActivityBedieningBinding
import za.co.jpsoft.winkerkreader.receivers.PastoralReminderActionReceiver
import za.co.jpsoft.winkerkreader.ui.adapters.BedieningPagerAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.BedieningViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.BedieningViewModelFactory

class BedieningActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBedieningBinding

    private val viewModel: BedieningViewModel by viewModels {
        BedieningViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBedieningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.bedieningToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupViewPager()
        setupTabBadge()
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupViewPager() {
        val pagerAdapter = BedieningPagerAdapter(this)
        binding.bedieningViewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.bedieningTabLayout, binding.bedieningViewPager) { tab, position ->
            tab.text = pagerAdapter.tabTitle(position)
        }.attach()
    }

    private fun setupTabBadge() {
        lifecycleScope.launch {
            viewModel.tabBadgeCount.collect { count ->
                val tab = binding.bedieningTabLayout.getTabAt(0) ?: return@collect
                if (count > 0) {
                    tab.orCreateBadge.number = count
                } else {
                    tab.removeBadge()
                }
            }
        }
    }

    /** Handles opening from notification — scrolls to the relevant reminder. */
    private fun handleDeepLink(intent: Intent?) {
        val reminderId = intent
            ?.getStringExtra(PastoralReminderActionReceiver.EXTRA_REMINDER_ID)
            ?: return

        // Navigate to Vandag tab (index 0) and request scroll
        binding.bedieningViewPager.currentItem = 0
        viewModel.requestScrollTo(reminderId)
    }

    companion object {
        /**
         * Launch BedieningActivity, optionally scrolling to a specific reminder.
         */
        fun launch(context: Context, reminderId: String? = null) {
            val intent = Intent(context, BedieningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (reminderId != null) {
                    putExtra(PastoralReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
                }
            }
            context.startActivity(intent)
        }
    }
}
```

**ViewPager adapter:** `ui/adapters/BedieningPagerAdapter.kt`

```kotlin
class BedieningPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 1   // Phase 2 adds Gevalle, Besoeke tabs here

    override fun createFragment(position: Int): Fragment = when (position) {
        0    -> BedieningVandagFragment()
        else -> BedieningVandagFragment()
    }

    fun tabTitle(position: Int) = when (position) {
        0    -> "Vandag"
        else -> ""
    }
}
```

### BedieningVandagFragment

New file: `ui/fragments/BedieningVandagFragment.kt`

```kotlin
package za.co.jpsoft.winkerkreader.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.FragmentBedieningVandagBinding
import za.co.jpsoft.winkerkreader.ui.activities.LidmaatDetailActivity
import za.co.jpsoft.winkerkreader.ui.adapters.BedieningReminderAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.BedieningViewModel

class BedieningVandagFragment : Fragment() {

    private var _binding: FragmentBedieningVandagBinding? = null
    private val binding get() = _binding!!

    // Shared with BedieningActivity — same ViewModel scope
    private val viewModel: BedieningViewModel by activityViewModels()

    private lateinit var adapter: BedieningReminderAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBedieningVandagBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapter()
        setupChips()
        setupObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private fun setupAdapter() {
        adapter = BedieningReminderAdapter(
            onVoltooi     = { reminderId -> viewModel.completeReminder(reminderId) },
            onSnooze      = { reminderId -> showSnoozeDialog(reminderId) },
            onAddCalendar = { reminderId -> viewModel.addToCalendar(reminderId) },
            onOpenMember  = { memberGuid -> openMemberDetail(memberGuid) }
        )

        binding.rvBedieningReminders.apply {
            this.adapter = this@BedieningVandagFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
        }
    }

    private fun setupChips() {
        binding.chipVandag.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.setFilter(BedieningViewModel.Filter.VANDAG)
        }
        binding.chipAgterstallig.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.setFilter(BedieningViewModel.Filter.AGTERSTALLIG)
        }
        binding.chipHierDieWeek.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.setFilter(BedieningViewModel.Filter.HIERDIE_WEEK)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Reminder list
            viewModel.displayItems.collect { items ->
                adapter.submitList(items)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Empty state
            viewModel.isEmpty.collect { empty ->
                binding.layoutEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvBedieningReminders.visibility = if (empty) View.GONE else View.VISIBLE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Chip count badges
            viewModel.overdueCount.collect { count ->
                binding.chipAgterstallig.text = if (count > 0)
                    getString(R.string.bediening_filter_agterstallig_count, count)
                else
                    getString(R.string.bediening_filter_agterstallig)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Scroll to reminder (notification deep link)
            viewModel.scrollToReminderId.collect { reminderId ->
                val position = adapter.positionOf(reminderId)
                if (position >= 0) {
                    binding.rvBedieningReminders.smoothScrollToPosition(position)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Errors
            viewModel.error.collect { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private fun showSnoozeDialog(reminderId: String) {
        val options = arrayOf(
            getString(R.string.snooze_more),        // "Môre (08:00)"
            getString(R.string.snooze_drie_dae),    // "3 dae"
            getString(R.string.snooze_een_week)     // "1 week"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.herinnering_uitstel_kies)
            .setItems(options) { _, which ->
                val option = when (which) {
                    0 -> BedieningViewModel.SnoozeOption.TOMORROW
                    1 -> BedieningViewModel.SnoozeOption.THREE_DAYS
                    else -> BedieningViewModel.SnoozeOption.ONE_WEEK
                }
                viewModel.snoozeReminder(reminderId, option)
            }
            .show()
    }

    private fun openMemberDetail(memberGuid: String) {
        // Reuse existing LidmaatDetailActivity launch pattern
        val intent = Intent(requireContext(), LidmaatDetailActivity::class.java).apply {
            putExtra("MEMBER_GUID", memberGuid)
        }
        startActivity(intent)
    }
}
```

---

## D5 — Main menu badge (MainActivity)

### `menu_main.xml` — add Bediening item

```xml
<!-- Add alongside existing menu items -->
<item
    android:id="@+id/action_bediening"
    android:title="@string/mainmenu_bediening"
    android:icon="@drawable/ic_bediening"
    app:showAsAction="never" />
```

### `MainActivity.kt` — badge count + navigation

**In `onResume()`, add a lightweight badge query:**

```kotlin
override fun onResume() {
    super.onResume()
    // ... existing onResume code ...
    updateBedieningBadge()
}

private fun updateBedieningBadge() {
    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val db = PastoralDatabase.getInstance(applicationContext)
            val zoneId = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val startOfToday = LocalDate.now(zoneId)
                .atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endOfDay = LocalDate.now(zoneId).plusDays(1)
                .atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

            val overdueCount = db.followUpReminderDao().countOverdue(startOfToday)
            // countDueToday needs to be added to the DAO (see below)
            val todayCount   = db.followUpReminderDao().countDueToday(endOfDay, now)
            val total = overdueCount + todayCount

            withContext(Dispatchers.Main) {
                val menuItem = binding.toolbar.menu.findItem(R.id.action_bediening)
                    ?: return@withContext
                if (total > 0) {
                    // Use MaterialBadge or a custom SpannableIcon; simplest: title suffix
                    menuItem.title = getString(R.string.mainmenu_bediening_badge, total)
                } else {
                    menuItem.title = getString(R.string.mainmenu_bediening)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Badge count failed", e)
        }
    }
}
```

**Add `countDueToday()` to `FollowUpReminderDao`:**

```kotlin
@Query(
    """
    SELECT COUNT(*) FROM follow_up_reminders
    WHERE status = :pendingStatus
      AND dueDateUtc <= :endOfDayUtc
      AND (snoozedUntilUtc IS NULL OR snoozedUntilUtc <= :nowUtc)
    """
)
suspend fun countDueToday(
    endOfDayUtc: Long,
    nowUtc: Long,
    pendingStatus: String = ReminderStatus.PENDING.name
): Int
```

**Handle menu item tap in `onOptionsItemSelected()` or `MenuItemHandler`:**

```kotlin
R.id.action_bediening -> {
    BedieningActivity.launch(this)
    true
}
```

**Imports to add to MainActivity:**

```kotlin
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.ui.activities.BedieningActivity
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```

---

## D6 — Strings + Manifest

### `strings.xml` additions

```xml
<!-- Sprint D additions (Sprint C strings already added) -->
<string name="bediening_tab_vandag">Vandag</string>
<string name="bediening_filter_vandag">Vandag</string>
<string name="bediening_filter_agterstallig">Agterstallig</string>
<string name="bediening_filter_agterstallig_count">Agterstallig (%d)</string>
<string name="bediening_filter_hierdie_week">Hierdie week</string>
<string name="mainmenu_bediening">Bediening</string>
<string name="mainmenu_bediening_badge">Bediening (%d)</string>
<string name="herinnering_bel">Bel</string>
<string name="herinnering_whatsapp">WhatsApp</string>
<string name="herinnering_maak_lid_oop">Maak lid oop</string>
<string name="herinnering_uitstel_kies">Stel herinnering uit</string>
<string name="herinnering_voeg_by_kalender">Voeg by kalender</string>
<string name="snooze_more">Môre (08:00)</string>
<string name="snooze_drie_dae">3 dae</string>
<string name="snooze_een_week">1 week</string>
<string name="datum_vandag">Vandag</string>
<string name="datum_gister">Gister</string>
<string name="meer_opsies">Meer opsies</string>
<string name="bediening_geen_herinnerings">Geen herinnerings vir vandag nie</string>
```

### `AndroidManifest.xml`

```xml
<activity
    android:name=".ui.activities.BedieningActivity"
    android:label="@string/bediening_title"
    android:parentActivityName=".ui.activities.MainActivity"
    android:exported="false" />
```

---

## Drawable resources required

| Resource | Used for | Source |
|----------|----------|--------|
| `ic_bediening` | Main menu icon | New — simple cross/church vector |
| `ic_phone` | Bel button | Likely already in project |
| `ic_whatsapp` | WhatsApp button | New — WhatsApp logo vector |
| `ic_more_vert` | Overflow button | Likely already in project |
| `ic_person_placeholder` | Member photo fallback | Likely already in project |
| `bg_badge_red` | Overdue badge background | New — rounded rect shape drawable |
| `ic_notification_pastoral` | Notification small icon (Sprint C) | New |

---

## Sprint D → Sprint E handoff notes

**`LidmaatDetailActivity.openMemberDetail(memberGuid)`** — the extra key `"MEMBER_GUID"` must match whatever `LidmaatDetailActivity` actually reads. Check the existing activity's `intent.getStringExtra()` key and align `BedieningVandagFragment.openMemberDetail()` accordingly.

**"Stel herinnering" entry point (Sprint E)** — Sprint E adds a block to `LidmaatDetailActivity`. The `BedieningActivity` → `LidmaatDetailActivity` path already works from Sprint D. Sprint E adds the reverse: `LidmaatDetailActivity` → `StelHerinneringBottomSheet` → repository.

**Badge uses title suffix approach** as the simplest cross-version technique. If the app uses Material Navigation Drawer or BottomNavigation, replace with `BadgeDrawable` from Material Components for a proper badge bubble.

---

## Decision log

| Decision | Rationale |
|----------|-----------|
| `activityViewModels()` in fragment | Single ViewModel shared between Activity (tab badge) and Fragment (list) — avoids two repo subscriptions |
| `MutableSharedFlow` for scroll event | `StateFlow` would replay the last value every time the fragment is recreated; `SharedFlow` with `extraBufferCapacity=1` is consumed once |
| Snooze dialog instead of inline date picker | Faster for common cases (tomorrow, 3 days, 1 week); covers 90 % of use. Sprint E adds a full date picker in `StelHerinneringBottomSheet` |
| `ListAdapter` + `DiffUtil` | Animates adds/removes correctly when reminders are completed or snoozed mid-session without flashing the whole list |
| `WhatsApp` number stripped to digits only | `wa.me` requires E.164 without leading `+` — SA numbers prefixed `27` |
| ViewPager2 with one tab (Phase 1) | `BedieningPagerAdapter.getItemCount()` returns 1 now; Phase 2 adds `Gevalle` and `Besoeke` tabs by incrementing the count and adding fragments |
| Menu badge via title suffix | Simplest approach compatible with any menu style the existing app uses; replace with `BadgeDrawable` if a Navigation Drawer is present |
