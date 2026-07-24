package za.co.jpsoft.winkerkreader.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.FragmentBedieningVandagBinding
import za.co.jpsoft.winkerkreader.ui.activities.LidmaatDetailActivity
import za.co.jpsoft.winkerkreader.ui.adapters.BedieningReminderAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.BedieningViewModel
import za.co.jpsoft.winkerkreader.utils.SettingsManager

class BedieningVandagFragment : Fragment() {

    private var _binding: FragmentBedieningVandagBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BedieningViewModel by activityViewModels()
    private val settingsManager: SettingsManager by lazy {
        SettingsManager.getInstance(
            requireContext()
        )
    }

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
        ViewCompat.setOnApplyWindowInsetsListener(binding.rvBedieningReminders) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }
    }

    override fun onDestroyView() {
        // Release the adapter to break references to the fragment
        binding.rvBedieningReminders.adapter = null
        super.onDestroyView()
        _binding = null
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private fun setupAdapter() {
        adapter = BedieningReminderAdapter(
            onVoltooi = { reminderId -> viewModel.completeReminder(reminderId) },
            onSnooze = { reminderId -> showSnoozeDialog(reminderId) },
            onAddCalendar = { reminderId -> viewModel.addToCalendar(reminderId) },
            onOpenMember = { memberGuid -> openMemberDetail(memberGuid) },
            onAddGoogleTask = { reminderId -> viewModel.syncReminderToGoogleTasks(reminderId) },
            onDelete = { reminderId -> showDeleteConfirmationDialog(reminderId) },
            onDeleteSeries = { reminderId -> showDeleteSeriesConfirmationDialog(reminderId) }
        )

        binding.rvBedieningReminders.apply {
            adapter = this@BedieningVandagFragment.adapter
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
        binding.chipHierdieWeek.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.setFilter(BedieningViewModel.Filter.HIERDIE_WEEK)
        }
        binding.chipAls.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.setFilter(BedieningViewModel.Filter.ALS)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.displayItems.collect { items ->
                if (BuildConfig.DEBUG) Log.d(
                    "BedieningFragment",
                    "displayItems emitted: ${items.size} items"
                )
                adapter.submitList(items)
                adapter.notifyDataSetChanged()
                if (items.isEmpty()) {
                    binding.layoutEmptyState.visibility = View.VISIBLE
                    binding.rvBedieningReminders.visibility = View.GONE
                } else {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.rvBedieningReminders.visibility = View.VISIBLE
                    binding.rvBedieningReminders.post {
                        adapter.notifyDataSetChanged()
                        binding.rvBedieningReminders.requestLayout()
                    }
                }
                if (BuildConfig.DEBUG) Log.d(
                    "BedieningFragment",
                    "RecyclerView visibility set to ${binding.rvBedieningReminders.visibility}"
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.overdueCount.collect { count ->
                binding.chipAgterstallig.text = if (count > 0)
                    getString(R.string.bediening_filter_agterstallig_count, count)
                else
                    getString(R.string.bediening_filter_agterstallig)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scrollToReminderId.collect { reminderId ->
                val position = adapter.positionOf(reminderId)
                if (position >= 0) {
                    binding.rvBedieningReminders.smoothScrollToPosition(position)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private fun showSnoozeDialog(reminderId: String) {
        val options = arrayOf(
            getString(R.string.snooze_more),
            getString(R.string.snooze_drie_dae),
            getString(R.string.snooze_een_week)
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
        val intent = Intent(requireContext(), LidmaatDetailActivity::class.java).apply {
            putExtra(LidmaatDetailActivity.EXTRA_MEMBER_GUID, memberGuid)
        }
        startActivity(intent)
    }

    private fun showDeleteConfirmationDialog(reminderId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.herinnering_verwyder_titel)
            .setMessage(R.string.herinnering_verwyder_boodskap)
            .setPositiveButton(R.string.pastoral_import_ja) { _, _ ->
                viewModel.deleteReminder(reminderId)
            }
            .setNegativeButton(R.string.pastoral_import_nee, null)
            .show()
    }

    private fun showDeleteSeriesConfirmationDialog(reminderId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.herinnering_verwyder_reeks_titel)  // you may need to add this string
            .setMessage(R.string.herinnering_verwyder_reeks_boodskap) // and this string
            .setPositiveButton(R.string.pastoral_import_ja) { _, _ ->
                viewModel.deleteSeries(reminderId)
            }
            .setNegativeButton(R.string.pastoral_import_nee, null)
            .show()
    }
}