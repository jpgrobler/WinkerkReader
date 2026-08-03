package za.co.jpsoft.winkerkreader.ui.controllers

import android.util.Log
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.helpers.BirthdayScrollHelper
import za.co.jpsoft.winkerkreader.ui.viewmodels.MainViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs
import java.time.LocalDate

/**
 * Manages the member list sort order and birthday auto-scroll.
 */
class SortOrderController(
    private val tag: String,
    private val viewModel: MemberViewModel,
    private val mainViewModel: MainViewModel,
    private val memberListAdapter: MemberListAdapter,
    private val memberListPrefs: MemberListPrefs,
    private val congregationPrefs: CongregationPrefs,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val recyclerView: RecyclerView,
    private val sortLabel: TextView,
    private val onMenuInvalidated: () -> Unit
) {
    // ── State ─────────────────────────────────────────────────────────────────

    var currentSort: String = ""
        private set

    var hasPendingBirthdayScroll: Boolean = false
        private set

    private var pendingBirthdayAnchor = false

    // ── Sort order API ────────────────────────────────────────────────────────

    fun update(newSort: String) {
        if (BuildConfig.DEBUG) Log.d(
            tag,
            "SortOrderController.update: new=$newSort current=$currentSort"
        )

        memberListAdapter.updateState(
            listView = memberListPrefs.listView,
            soekList = viewModel.soekList,
            soek = viewModel.soek,
            recordStatus = viewModel.recordStatus,
            sortOrder = newSort,
            useCongregationIndicator = congregationPrefs.useCongregationIndicator
        )
        sortLabel.text = iconFor(newSort)

        if (newSort == currentSort) {
            if (isBirthdaySort(newSort)) {
                requestBirthdayAnchor(newSort)
                viewModel.switchToBirthdaySort()
            }
            return
        }

        currentSort = newSort
        memberListPrefs.defLayout = newSort
        mainViewModel.setSortOrder(newSort)

        if (isBirthdaySort(newSort)) {
            requestBirthdayAnchor(newSort)
            viewModel.switchToBirthdaySort()
        } else {
            viewModel.updateSortOrder(newSort)
        }

        onMenuInvalidated()

        if (BuildConfig.DEBUG) Log.d(tag, "SortOrderController.update complete")
    }

    fun syncWithSettings(isReady: Boolean) {
        if (!isReady) return
        update(memberListPrefs.defLayout)
    }

    fun cycleForward() {
        val next = when (viewModel.sortOrder) {
            "HUWELIK"   -> "VAN"
            "VAN"       -> "GESINNE"
            "GESINNE"   -> "WYK"
            "WYK"       -> "OUDERDOM"
            "OUDERDOM"  -> "ADRES"
            "ADRES"     -> "VERJAAR"
            "VERJAAR"   -> "HUWELIK"
            else        -> "VAN"
        }
        update(next)
    }

    fun cycleBack() {
        val next = when (viewModel.sortOrder) {
            "HUWELIK"  -> "VERJAAR"
            "VERJAAR"  -> "ADRES"
            "ADRES"    -> "OUDERDOM"
            "OUDERDOM" -> "WYK"
            "WYK"      -> "GESINNE"
            "GESINNE"  -> "VAN"
            "VAN"      -> "HUWELIK"
            else       -> "VAN"
        }
        update(next)
    }

    // ── Birthday scroll API ───────────────────────────────────────────────────

    /** Call before a birthday reload; [anchorBirthdayListIfPending] scrolls after load. */
    fun requestBirthdayAnchor(forSort: String = viewModel.sortOrder) {
        if (!isBirthdaySort(forSort)) return
        pendingBirthdayAnchor = true
        hasPendingBirthdayScroll = true
    }

    /**
     * After paging refresh, scroll to today or the nearest upcoming birthday in
     * the loaded adapter window. Survives Paging prepending earlier pages.
     */
    fun anchorBirthdayListIfPending() {
        if (!pendingBirthdayAnchor) return
        if (!isBirthdaySort(viewModel.sortOrder)) {
            pendingBirthdayAnchor = false
            hasPendingBirthdayScroll = false
            return
        }

        lifecycleScope.launch {
            try {
                // Wait for refresh and any backward prefetch — prepended pages shift
                // adapter indices and would undo a scroll done too early.
                memberListAdapter.loadStateFlow
                    .filter {
                        it.refresh is LoadState.NotLoading &&
                                it.prepend !is LoadState.Loading
                    }
                    .first()

                val items = memberListAdapter.snapshot().items
                if (items.isEmpty()) return@launch

                val today = LocalDate.now()
                val month = "%02d".format(today.monthValue)
                val day = "%02d".format(today.dayOfMonth)
                val targetPos = BirthdayScrollHelper.findNextBirthdayPosition(items, month, day)
                if (targetPos >= 0) {
                    recyclerView.post {
                        (recyclerView.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(targetPos, 0)
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(tag, "Birthday anchor scroll failed", e)
            } finally {
                pendingBirthdayAnchor = false
                hasPendingBirthdayScroll = false
            }
        }
    }

    fun prefetchBirthdayScrollIfNeeded() {
        if (!isBirthdaySort(viewModel.sortOrder)) return
        if (hasPendingBirthdayScroll) return
        lifecycleScope.launch {
            val offset = viewModel.getBirthdayOffset(viewModel.sortOrder)
            hasPendingBirthdayScroll = offset > 0
        }
    }

    fun recomputeBirthdayOffset() {
        if (!isBirthdaySort(viewModel.sortOrder)) return
        requestBirthdayAnchor(viewModel.sortOrder)
        viewModel.switchToBirthdaySort()
    }

    fun refreshLabel() {
        sortLabel.text = iconFor(viewModel.sortOrder)
    }

    fun iconFor(sortOrder: String): String = when (sortOrder) {
        "VAN"      -> "⇵🔤"
        "GESINNE"  -> "⇵👨‍👩‍👧‍👦"
        "WYK"      -> "⇵🏘️"
        "OUDERDOM" -> "⇵📅"
        "VERJAAR"  -> "⇵🎂"
        "ADRES"    -> "⇵📌"
        "HUWELIK"  -> "⇵💍"
        else       -> "⇵📋"
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun isBirthdaySort(sort: String) =
        sort == "VERJAAR" || sort == "VERJAARSDAG"
}