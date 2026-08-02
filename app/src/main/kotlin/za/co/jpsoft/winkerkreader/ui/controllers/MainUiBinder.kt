package za.co.jpsoft.winkerkreader.ui.controllers

import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.repository.ContactRepository
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel

/**
 * Binds UI components to ViewModel and other data sources.
 * Owns all observer subscriptions that update the main activity's views.
 */
class MainUiBinder(
    private val binding: ActivityMainBinding,
    private val viewModel: MemberViewModel,
    private val adapter: MemberListAdapter,
    private val lifecycleOwner: LifecycleOwner,
    private val sortController: SortOrderController
) {

    private val lifecycleScope: LifecycleCoroutineScope = lifecycleOwner.lifecycleScope

    fun setupObservers() {
        // 1. Search text LiveData → update search banner
        viewModel.getTextLiveData().observe(lifecycleOwner) { searchText ->
            binding.searchText.text = searchText
            binding.searchItemBlock.visibility =
                if (searchText.isEmpty()) View.GONE else View.VISIBLE
        }

        // 2. Verjaar flag (currently only logs; kept for consistency)
        viewModel.getVerjaarFlag().observe(lifecycleOwner) { showBirthday ->
            if (BuildConfig.DEBUG) Log.d("MainUiBinder", "verjaarFlag: $showBirthday")
        }

        // 3. Pending reminder GUIDs → update adapter (for the bell icon)
        viewModel.memberGuidsWithPendingReminders.observe(lifecycleOwner) { guids ->
            if (BuildConfig.DEBUG) Log.d("MainUiBinder", "Updating pending GUIDs: $guids")
            adapter.updatePendingReminderGuids(guids)
        }

        // 4. Total member count (Flow) → update the count label
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totalCount.collect { count ->
                    binding.totalCount.text = "($count)"
                }
            }
        }

        // 5. Paging data flow → submit to adapter
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pagingDataFlowWithRefresh
                    .catch { e ->
                        if (BuildConfig.DEBUG) Log.e("MainUiBinder", "Paging flow error", e)
                    }
                    .collectLatest { pagingData ->
                        adapter.submitData(lifecycleOwner.lifecycle, pagingData)
                    }
            }
        }

        // 6. Load state → show/hide progress bar and log errors
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                adapter.loadStateFlow.collect { loadStates ->
                    val isLoading = loadStates.refresh is LoadState.Loading
                    binding.indeterminateBar.visibility =
                        if (isLoading) View.VISIBLE else View.GONE

                    if (loadStates.refresh is LoadState.Error) {
                        val error = (loadStates.refresh as LoadState.Error).error
                        if (BuildConfig.DEBUG) Log.e("MainUiBinder", "Load error", error)
                    }
                }
            }
        }

        // 7. Contact repository updates → rebind visible items (for WhatsApp icon)
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ContactRepository.contactsUpdateFlow.collect {
                    adapter.rebindVisibleItems(binding.lidmaatList)
                }
            }
        }

        // 8. Scroll restoration: refresh sort label when adapter finishes loading
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                adapter.loadStateFlow.collect { loadStates ->
                    if (loadStates.refresh is LoadState.NotLoading) {
                        sortController.refreshLabel()
                    }
                }
            }
        }
    }
}