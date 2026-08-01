package za.co.jpsoft.winkerkreader.ui.controllers

import android.util.Log
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.MainViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs

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

        if (newSort == currentSort) return

        currentSort = newSort
        memberListPrefs.defLayout = newSort
        mainViewModel.setSortOrder(newSort)
        viewModel.updateSortOrder(newSort)

        onMenuInvalidated()

        if (isBirthdaySort(newSort)) scrollToBirthday()

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
        viewModel.refresh()
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
        viewModel.refresh()
    }

    // ── Birthday scroll API ───────────────────────────────────────────────────

    fun prefetchBirthdayScrollIfNeeded() {
        if (!isBirthdaySort(viewModel.sortOrder)) return
        if (hasPendingBirthdayScroll) return

        lifecycleScope.launch {
            val offset = viewModel.getBirthdayOffset(viewModel.sortOrder)
            if (offset <= 0) return@launch

            hasPendingBirthdayScroll = true
            memberListAdapter.loadStateFlow.collect { loadStates ->
                if (loadStates.refresh is LoadState.NotLoading) {
                    val itemCount = memberListAdapter.itemCount
                    if (itemCount > 0) {
                        val scrollPos = if (offset < itemCount) offset else itemCount - 1
                        recyclerView.post {
                            (recyclerView.layoutManager as? LinearLayoutManager)
                                ?.scrollToPositionWithOffset(scrollPos, 0)
                        }
                        hasPendingBirthdayScroll = false
                    }
                }
            }
        }
    }

    fun recomputeBirthdayOffset() {
        val sort = memberListPrefs.defLayout
        if (!isBirthdaySort(sort)) return
        lifecycleScope.launch {
            val offset = viewModel.getBirthdayOffset(sort)
            hasPendingBirthdayScroll = offset > 0
        }
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

    private fun scrollToBirthday() {
        lifecycleScope.launch {
            try {
                val offset = viewModel.getBirthdayOffset(viewModel.sortOrder)
                memberListAdapter.loadStateFlow.collect { loadStates ->
                    if (loadStates.refresh is LoadState.NotLoading) {
                        val itemCount = memberListAdapter.itemCount
                        if (itemCount > 0 && offset >= 0) {
                            val scrollPos = if (offset < itemCount) offset else itemCount - 1
                            recyclerView.post {
                                (recyclerView.layoutManager as? LinearLayoutManager)
                                    ?.scrollToPositionWithOffset(scrollPos, 0)
                            }
                        }
                        return@collect
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(tag, "SortOrderController: birthday scroll failed", e)
            }
        }
    }

    private fun isBirthdaySort(sort: String) =
        sort == "VERJAAR" || sort == "VERJAARSDAG"
}