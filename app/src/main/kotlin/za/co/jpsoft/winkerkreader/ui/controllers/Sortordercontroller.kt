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
import za.co.jpsoft.winkerkreader.utils.SettingsManager

/**
 * Manages the member list sort order and birthday auto-scroll.
 *
 * Extracted from MainActivity. Owns:
 *  - [currentSort] — the active sort key (was `currentSortOrder` on MainActivity)
 *  - Sort cycling via swipe left/right (was inline `when` blocks in swipeGestureController)
 *  - Birthday scroll in two modes:
 *      [scrollToBirthday]           — immediate, called after sort change
 *      [prefetchBirthdayScrollIfNeeded] — deferred prefetch called from observeDataset()
 *
 * ── Wiring in MainActivity ────────────────────────────────────────────────────
 *
 *  sortController = SortOrderController(
 *      tag              = TAG,
 *      viewModel        = viewModel,
 *      mainViewModel    = mainViewModel,
 *      memberListAdapter = memberListAdapter,
 *      settings         = settingsManager,
 *      lifecycleScope   = lifecycleScope,
 *      recyclerView     = binding.lidmaatList,
 *      sortLabel        = binding.sortorder,
 *      onMenuInvalidated = ::invalidateOptionsMenu
 *  )
 *
 *  // swipeGestureController constructor (replaces inline when blocks):
 *  onSwipeLeft  = { sortController.cycleForward() }
 *  onSwipeRight = { sortController.cycleBack() }
 *
 *  // menuController constructor:
 *  onSortChanged = sortController::update
 *
 *  // observeDataset(), replace lines 659 and 670-690:
 *  sortController.refreshLabel()
 *  sortController.prefetchBirthdayScrollIfNeeded()
 *
 *  // clearFilter(): replace lines 1025-1041:
 *  sortController.update(restoreSort)
 *  viewModel.refresh()                 ← keep: clearFilter owns the refresh call
 *  sortController.recomputeBirthdayOffset()
 *
 *  // cancelFilter(): replace line 1059:
 *  sortController.update(restoreSort)
 *
 *  // loadInitialData(): replace updateSortOrder(defLayout):
 *  sortController.update(defLayout)
 *
 *  // onResumeAfterAuth() / syncSortOrderWithSettings() calls:
 *  sortController.syncWithSettings()
 *
 *  // restoreListScrollIfNeeded() guard (was `pendingBirthdayOffset != null`):
 *  if (sortController.hasPendingBirthdayScroll) return
 */
class SortOrderController(
    private val tag: String,
    private val viewModel: MemberViewModel,
    private val mainViewModel: MainViewModel,
    private val memberListAdapter: MemberListAdapter,
    private val settings: SettingsManager,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val recyclerView: RecyclerView,
    private val sortLabel: TextView,
    private val onMenuInvalidated: () -> Unit
) {
    // ── State ─────────────────────────────────────────────────────────────────

    /** The active sort key. Was `currentSortOrder` on MainActivity. */
    var currentSort: String = ""
        private set

    /**
     * True while a birthday scroll position has been fetched but the adapter
     * hasn't finished loading yet. Used by restoreListScrollIfNeeded() to
     * avoid stomping the pending scroll with a saved-scroll restore.
     *
     * Was `pendingBirthdayOffset != null` check in MainActivity.
     */
    var hasPendingBirthdayScroll: Boolean = false
        private set

    // ── Sort order API ────────────────────────────────────────────────────────

    /**
     * Changes the active sort order to [newSort], persists it, updates the
     * adapter, and triggers a birthday scroll when relevant.
     *
     * Was `updateSortOrder()` in MainActivity.
     * Note: does NOT call viewModel.refresh() — callers that need a full data
     * reload (clearFilter, swipe) do so themselves.
     */
    fun update(newSort: String) {
        if (BuildConfig.DEBUG) Log.d(tag,
            "SortOrderController.update: new=$newSort current=$currentSort")

        memberListAdapter.updateState(
            listView = settings.listView,
            soekList = viewModel.soekList,
            soek = viewModel.soek,
            recordStatus = viewModel.recordStatus,
            sortOrder = newSort,
            useCongregationIndicator = settings.useCongregationIndicator
        )
        sortLabel.text = iconFor(newSort)

        if (newSort == currentSort) return   // label + adapter updated; nothing else needed

        currentSort = newSort
        settings.defLayout = newSort
        mainViewModel.setSortOrder(newSort)
        viewModel.updateSortOrder(newSort)

        onMenuInvalidated()

        if (isBirthdaySort(newSort)) scrollToBirthday()

        if (BuildConfig.DEBUG) Log.d(tag, "SortOrderController.update complete")
    }

    /**
     * Re-applies the sort stored in settings, but only when the app is fully
     * initialised. Safe to call speculatively; guards itself.
     *
     * Was `syncSortOrderWithSettings()`.
     */
    fun syncWithSettings(isReady: Boolean) {
        if (!isReady) return
        update(settings.defLayout)
    }

    /**
     * Cycle forward through the sort order (swipe-left gesture).
     * Includes viewModel.refresh() because a swipe always requires a full reload.
     *
     * Was the `onSwipeLeft` lambda in swipeGestureController.
     */
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

    /**
     * Cycle backward through the sort order (swipe-right gesture).
     * Includes viewModel.refresh() because a swipe always requires a full reload.
     *
     * Was the `onSwipeRight` lambda in swipeGestureController.
     */
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

    /**
     * Pre-fetches the birthday scroll offset and scrolls once the adapter
     * finishes loading. Called from observeDataset() each time the dataset
     * refreshes under a birthday sort.
     *
     * Was the `if (isBirthdaySort && pendingBirthdayOffset == null)` block
     * inside observeDataset() (lines 670-690).
     */
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

    /**
     * Re-fetches the birthday offset into [hasPendingBirthdayScroll] state
     * so the next scroll-restoration check is aware a birthday scroll is
     * pending. Called from clearFilter() and cancelFilter().
     *
     * Was `recomputeBirthdayOffset()` in MainActivity.
     */
    fun recomputeBirthdayOffset() {
        val sort = settings.defLayout
        if (!isBirthdaySort(sort)) return
        lifecycleScope.launch {
            val offset = viewModel.getBirthdayOffset(sort)
            hasPendingBirthdayScroll = offset > 0
        }
    }

    // ── Label helper (public for observeDataset) ──────────────────────────────

    /**
     * Updates [sortLabel] to match the current viewModel sort order.
     * Call from observeDataset() to keep the icon in sync after a data refresh.
     *
     * Replaces: `binding.sortorder.text = getSortIcon(viewModel.sortOrder)` (line 659)
     */
    fun refreshLabel() {
        sortLabel.text = iconFor(viewModel.sortOrder)
    }

    /** Returns the emoji icon string for [sortOrder]. */
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

    /**
     * Immediately scrolls to today's birthday position once the adapter
     * reports NotLoading. Called internally by [update] when sort is VERJAAR.
     *
     * Was `scrollToCurrentBirthday()` in MainActivity.
     */
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