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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.model.VandagAllesItem
import za.co.jpsoft.winkerkreader.data.pastoral.model.VandagAllesSection
import za.co.jpsoft.winkerkreader.databinding.FragmentBedieningAllesBinding
import za.co.jpsoft.winkerkreader.ui.activities.LidmaatDetailActivity
import za.co.jpsoft.winkerkreader.ui.adapters.BedieningAllesMultiViewAdapter
import za.co.jpsoft.winkerkreader.ui.bottomsheets.StelHerinneringBottomSheet
import za.co.jpsoft.winkerkreader.ui.bottomsheets.VoegNotaByBottomSheet
import za.co.jpsoft.winkerkreader.ui.viewmodels.BedieningViewModel
import za.co.jpsoft.winkerkreader.utils.VandagAllesDisplayItem

@AndroidEntryPoint
class BedieningAllesFragment : Fragment() {

    private var _binding: FragmentBedieningAllesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BedieningViewModel by activityViewModels()
    private lateinit var adapter: BedieningAllesMultiViewAdapter

    private var isVandagOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isVandagOnly = arguments?.getBoolean(ARG_VANDAG_ONLY, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBedieningAllesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        setupRecyclerView()
        setupFilterChips()
        observeViewModel()
    }

    private fun setupAdapter() {
        adapter = BedieningAllesMultiViewAdapter(
            onCallMember = { guid, phone -> viewModel.callMember(guid, phone) },
            onSendSms = { guid, phone -> viewModel.sendSms(guid, phone) },
            onWhatsApp = { celebration ->
                celebration.cellphone?.let { phone ->
                    viewModel.sendWhatsApp(
                        memberGuid = celebration.memberGuid,
                        phoneNumber = phone,
                        eventType = celebration.eventType,
                        memberName = celebration.name
                    )
                } ?: run {
                    viewModel.emitError("Geen selfoonnommer vir WhatsApp")
                }
            },
            onAddNote = { guid, name -> viewModel.addNote(guid, name) },
            onSetReminder = { guid -> viewModel.setReminder(guid) },
            onOpenMember = { guid -> navigateToMemberDetail(guid) },
            onComplete = { reminderId -> viewModel.completeReminder(reminderId) },
            onSnooze = { reminderId -> showSnoozeDialog(reminderId) },
            onDelete = { reminderId -> viewModel.deleteReminder(reminderId) },
            onDeleteSeries = { reminderId -> viewModel.deleteSeries(reminderId) },
            onAddCalendar = { reminderId -> viewModel.addToCalendar(reminderId) },
            onAddGoogleTask = { reminderId -> viewModel.syncReminderToGoogleTasks(reminderId) }
        )
    }

    private fun setupRecyclerView() {
        binding.rvBedieningAlles.adapter = adapter
        binding.rvBedieningAlles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBedieningAlles.setHasFixedSize(false)
    }

    private fun setupFilterChips() {
        val chipGroup = binding.filterChipGroup
        if (!isVandagOnly) {
            chipGroup.visibility = View.GONE
            return
        }
        chipGroup.visibility = View.VISIBLE

        val chipFilterMap = mapOf(
            R.id.filter_agterstallig to BedieningViewModel.Filter.AGTERSTALLIG,
            R.id.filter_vandag to BedieningViewModel.Filter.VANDAG,
            R.id.filter_hierdie_week to BedieningViewModel.Filter.HIERDIE_WEEK,
            R.id.filter_als to BedieningViewModel.Filter.ALS
        )

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chipId = checkedIds.first()
                val filter = chipFilterMap[chipId] ?: BedieningViewModel.Filter.VANDAG
                viewModel.setFilter(filter)
            }
        }

        // Update chip selection when the filter changes from the ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeFilter.collect { filter ->
                val targetId = chipFilterMap.entries.find { it.value == filter }?.key
                if (targetId != null && chipGroup.checkedChipId != targetId) {
                    chipGroup.check(targetId)
                }
            }
        }
    }

    private fun observeViewModel() {
        if (isVandagOnly) {
            // Tab 2: use the ViewModel's filtered displayItems (reminders only)
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.displayItems.collect { reminders ->
                    val displayItems =
                        reminders.map { VandagAllesDisplayItem.Reminder(VandagAllesItem.Reminder(it)) }
                    adapter.submitList(displayItems)
                    updateEmptyState(displayItems.isEmpty())
                }
            }
        } else {
            // Tab 1: full "Alles" view with celebrations and reminders (due today/overdue)
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.allesItems.collect { sections ->
                    val displayItems = flattenSections(sections)
                    adapter.submitList(displayItems)
                    updateEmptyState(displayItems.isEmpty())
                }
            }
        }

        // Shared loading/error handling (unchanged)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadingState.collect { state ->
                handleLoadingState(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.navigationEvent.collect { event ->
                handleNavigationEvent(event)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errorEvent.collect { error ->
                showErrorSnackbar(error)
            }
        }
    }

    private fun flattenSections(sections: List<VandagAllesSection>): List<VandagAllesDisplayItem> {
        val items = mutableListOf<VandagAllesDisplayItem>()
        sections.forEach { section ->
            val headerType = when (section) {
                is VandagAllesSection.Celebrations -> VandagAllesDisplayItem.Header.SectionType.CELEBRATIONS
                is VandagAllesSection.Overdue -> VandagAllesDisplayItem.Header.SectionType.OVERDUE
                is VandagAllesSection.DueToday -> VandagAllesDisplayItem.Header.SectionType.DUE_TODAY
            }
            items.add(VandagAllesDisplayItem.Header(section.title, headerType))
            when (section) {
                is VandagAllesSection.Celebrations -> {
                    section.items.forEach {
                        if (it is VandagAllesItem.Celebration) items.add(
                            VandagAllesDisplayItem.Celebration(it)
                        )
                    }
                }

                is VandagAllesSection.Overdue, is VandagAllesSection.DueToday -> {
                    section.items.forEach {
                        if (it is VandagAllesItem.Reminder) items.add(
                            VandagAllesDisplayItem.Reminder(it)
                        )
                    }
                }
            }
        }
        return items
    }

    private fun handleLoadingState(state: BedieningViewModel.LoadingState) {
        when (state) {
            is BedieningViewModel.LoadingState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.rvBedieningAlles.visibility = View.GONE
                binding.layoutEmptyAlles.visibility = View.GONE
                binding.layoutErrorAlles.visibility = View.GONE
            }

            is BedieningViewModel.LoadingState.Success -> {
                binding.progressBar.visibility = View.GONE
                binding.rvBedieningAlles.visibility = View.VISIBLE
                binding.layoutErrorAlles.visibility = View.GONE
            }

            is BedieningViewModel.LoadingState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.rvBedieningAlles.visibility = View.GONE
                binding.layoutErrorAlles.visibility = View.VISIBLE
                binding.tvErrorMessage.text = state.message
            }

            is BedieningViewModel.LoadingState.Idle -> {}
        }
    }

    private fun handleNavigationEvent(event: BedieningViewModel.NavigationEvent) {
        when (event) {
            is BedieningViewModel.NavigationEvent.Call -> {
                try {
                    startActivity(event.intent)
                } catch (e: Exception) {
                    showErrorSnackbar("Kon nie bel nie: ${e.message}")
                }
            }

            is BedieningViewModel.NavigationEvent.SendSms -> {
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("smsto:${event.phoneNumber}")
                }
                try {
                    startActivity(smsIntent)
                } catch (e: Exception) {
                    showErrorSnackbar("Kon nie SMS laai nie: ${e.message}")
                }
            }

            is BedieningViewModel.NavigationEvent.OpenNoteDialog -> {
                VoegNotaByBottomSheet.newInstance(
                    event.memberGuid,
                    memberDisplayName = event.memberName
                )
                    .show(childFragmentManager, VoegNotaByBottomSheet.TAG)
            }

            is BedieningViewModel.NavigationEvent.OpenReminderDialog -> {
                StelHerinneringBottomSheet.newInstance(event.memberGuid)
                    .show(childFragmentManager, StelHerinneringBottomSheet.TAG)
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmptyAlles.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvBedieningAlles.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun navigateToMemberDetail(memberGuid: String) {
        if (memberGuid.isBlank()) {
            Snackbar.make(binding.root, "Lidmaat-inligting ontbreek", Snackbar.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(requireContext(), LidmaatDetailActivity::class.java).apply {
            putExtra(LidmaatDetailActivity.EXTRA_MEMBER_GUID, memberGuid)
        }
        startActivity(intent)
    }

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

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        binding.rvBedieningAlles.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_VANDAG_ONLY = "arg_vandag_only"

        fun newInstance(isVandagOnly: Boolean): BedieningAllesFragment {
            return BedieningAllesFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_VANDAG_ONLY, isVandagOnly)
                }
            }
        }
    }
}