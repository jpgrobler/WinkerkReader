package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.repository.ChurchInfoRepository
import za.co.jpsoft.winkerkreader.data.members.setup.DatabaseInitializer
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralNoteRepository
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.ui.helpers.MemberListScrollHelper
import za.co.jpsoft.winkerkreader.ui.helpers.QuickActionHelper
import za.co.jpsoft.winkerkreader.ui.viewmodels.MainViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.CallLogImporter
import za.co.jpsoft.winkerkreader.utils.SearchCheckBoxPreferences
import za.co.jpsoft.winkerkreader.utils.messaging.WhatsAppContactLoader
import za.co.jpsoft.winkerkreader.utils.permissions.PermissionManager
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs
import za.co.jpsoft.winkerkreader.utils.prefs.BackupPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.BirthdaySmsPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.QuickActionPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.SyncPrefs
import za.co.jpsoft.winkerkreader.utils.ui.BackPressHandler
import za.co.jpsoft.winkerkreader.utils.ui.MainNavigationController
import za.co.jpsoft.winkerkreader.utils.ui.SwipeActionHandler
import za.co.jpsoft.winkerkreader.utils.work.WorkScheduler

/**
 * Coordinates the initialisation and wiring of all MainActivity components.
 */
class MainActivityInitializer(
    private val activity: MainActivity,
    private val savedInstanceState: Bundle?,
    private val memberListPrefs: MemberListPrefs,
    private val callMonitorPrefs: CallMonitorPrefs,
    private val congregationPrefs: CongregationPrefs,
    private val mainViewModel: MainViewModel,
    private val quickActionPrefs: QuickActionPrefs,
    private val appearancePrefs: AppearancePrefs,
    private val workScheduler: WorkScheduler,
    private val syncPrefs: SyncPrefs,
    private val birthdaySmsPrefs: BirthdaySmsPrefs,
    private val databaseInitializer: DatabaseInitializer,
    private val callLogImporter: CallLogImporter,
    private val navigationController: MainNavigationController,
    private val backupPrefs: BackupPrefs,
    private val churchInfoRepo: ChurchInfoRepository
) {
    // ─── Dependencies ──────────────────────────────────────────────────────────

    private val lifecycleScope = activity.lifecycleScope
    private val binding: ActivityMainBinding = activity.binding
    private val permissionManager = PermissionManager(activity)

    // ─── Components that will be exposed to the activity ────────────────────

    lateinit var menuController: MainMenuController
        private set
    lateinit var swipeGestureController: MainSwipeGestureController
        private set
    lateinit var viewModel: MemberViewModel
        private set
    lateinit var adapter: MemberListAdapter
        private set

    private lateinit var chipController: CongregationChipController
    lateinit var sortController: SortOrderController
        private set
    lateinit var searchFilterCoordinator: MainSearchFilterCoordinator
        private set

    private lateinit var listInteractionController: MemberListInteractionController
    private lateinit var startupCoordinator: MainStartupCoordinator
    private lateinit var backPressHandler: BackPressHandler
    private lateinit var pastoralBadgeController: PastoralReminderBadgeController
    private lateinit var uiBinder: MainUiBinder
    private lateinit var quickActionHelper: QuickActionHelper

    // ─── State ────────────────────────────────────────────────────────────────

    private var initState: InitState = InitState.AwaitingAuth

    // Replace: private var savedListScroll: MemberListScrollHelper.ScrollState? = null
    private var savedScrollStateWithSort: Pair<MemberListScrollHelper.ScrollState, String>? = null
    private var searchList: ArrayList<SearchCheckBox> = arrayListOf()
    private var scrollRestored = false

    lateinit var swipeActionHandler: SwipeActionHandler
        private set
    // ─── Initialisation entry point ─────────────────────────────────────────

    fun setupPreAuth() {
        setupViewModelAndAdapter()
        setupChipController()
        setupSortController()
        quickActionHelper = QuickActionHelper(activity, quickActionPrefs, appearancePrefs)
        setupSwipeActionHandler()
        setupItemSwipe()
        setupSearchFilterCoordinator()
        setupMenuController()
        setupListInteractionController()
        //setupSwipeGestureController()
        setupActivityResultCoordinator()
        setupWorkScheduler()


        activity.appAuthGuard.guardIfNeeded(
            onAuthenticated = { setupPostAuth() }
        )
    }

    private fun setupItemSwipe() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0,  // drag directions – none
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return

                val item = adapter.snapshot().getOrNull(position) ?: return

                // Reset translation so the view reappears
                viewHolder.itemView.translationX = 0f

                // Rebind after a short delay to let the swipe animation finish
                binding.lidmaatList.postDelayed({
                    adapter.notifyItemChanged(position)
                }, 100)

                when (direction) {
                    ItemTouchHelper.LEFT -> swipeActionHandler.handleSwipeLeft(item)
                    ItemTouchHelper.RIGHT -> swipeActionHandler.handleSwipeRight(item)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.lidmaatList)
    }

    // ─── Post‑authentication finalisation ───────────────────────────────────

    private fun setupPostAuth() {
        setupUiBinder()
        setupPastoralBadgeController()
        setupStartupCoordinator()
        startupCoordinator.runOnCreate()
        setupBackPressHandler()
        restoreInstanceState()
        setupScrollRestorationObserver()
        sortController.syncWithSettings(false)
        //loadInitialData()
        initState = InitState.Ready
        activity.invalidateOptionsMenu()
    }

    // ─── Private setup methods ──────────────────────────────────────────────

    private fun setupViewModelAndAdapter() {
        val initialCongregations = listOfNotNull(
            congregationPrefs.gemeenteNaam.takeIf { it.isNotBlank() },
            congregationPrefs.gemeente2Naam.takeIf { it.isNotBlank() },
            congregationPrefs.gemeente3Naam.takeIf { it.isNotBlank() }
        ).toSet()

        viewModel = ViewModelProvider(activity)[MemberViewModel::class.java]

        adapter = MemberListAdapter(
            memberListPrefs = memberListPrefs,
            congregationPrefs = congregationPrefs,
            onItemClick = { view, item, _ ->
                if (::listInteractionController.isInitialized) {
                    activity.findSearchView()?.clearFocus()
                    hideKeyboard()
                    listInteractionController.showMemberPopupMenu(view, item)
                }
            },
            onItemLongClick = { item, _ ->
                if (::listInteractionController.isInitialized) {
                    listInteractionController.onMemberLongClick(item)
                } else false
            }
        )

        binding.lidmaatList.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = this@MainActivityInitializer.adapter
            setHasFixedSize(true) // 👈 Prevents unnecessary layout recalculations on scroll
            setItemViewCacheSize(20) // 👈 Improves recycling cache hit rates
            itemAnimator = null // 👈 Disables layout animations during scroll to prevent jank
        }
        adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.ALLOW
    }

    private fun setupChipController() {
        chipController = CongregationChipController(
            context = activity,
            chipContainer = binding.chipContainer,
            congregationPrefs = congregationPrefs,
            onFilterChanged = { selected ->
                viewModel.setCongregationFilter(selected)
                viewModel.refresh()
                searchFilterCoordinator.updateSummaryView()
            }
        )
        chipController.setup()
        binding.chipContainer.post {
            if (BuildConfig.DEBUG) Log.d(
                "ChipDebug",
                "post: width=${binding.chipContainer.width}, height=${binding.chipContainer.height}"
            )
            if (BuildConfig.DEBUG) Log.d(
                "ChipDebug",
                "post: visibility=${binding.chipContainer.visibility}, childCount=${binding.chipContainer.childCount}"
            )
        }
        // Also log immediately – this will show 0 children, but that's expected before post runs.
        if (BuildConfig.DEBUG) Log.d(
            "ChipDebug",
            "immediate: childCount=${binding.chipContainer.childCount}"
        )
    }

    private fun setupSortController() {
        sortController = SortOrderController(
            tag = "MainActivity",
            viewModel = viewModel,
            mainViewModel = mainViewModel,
            memberListAdapter = adapter,
            memberListPrefs = memberListPrefs,
            congregationPrefs = congregationPrefs,
            lifecycleScope = lifecycleScope,
            recyclerView = binding.lidmaatList,
            sortLabel = binding.sortorder,
            onMenuInvalidated = { activity.invalidateOptionsMenu() }
        )
    }

    private fun setupSearchFilterCoordinator() {
        searchFilterCoordinator = MainSearchFilterCoordinator(
            tag = "MainActivity",
            viewModel = viewModel,
            memberListAdapter = adapter,
            memberListPrefs = memberListPrefs,        // add
            congregationPrefs = congregationPrefs,    // add
            binding = binding,
            findSearchView = { activity.findSearchView() },
            hideFilterPanel = {},
            onUpdateSortOrder = { sortController.update(it) },
            onRecomputeBirthdayOffset = { sortController.recomputeBirthdayOffset() },
            selectAllChips = { chipController.selectAll() },
            deselectChips = { chipController.deselectAll() }
        ).apply {
            onFilterRestored = { sortController.recomputeBirthdayOffset() }
            onFilterCancelled = {
                activity.currentFocus?.let { hideKeyboard() }
            }
        }
    }

    private fun setupMenuController() {
        menuController = MainMenuController(
            activity = activity,
            tag = "MainActivity",
            viewModel = viewModel,
            searchFilterCoordinator = searchFilterCoordinator,
            onAdapterStateChanged = ::onAdapterStateChanged,
            onFilterDisplayChanged = { searchFilterCoordinator.updateSummaryView() },
            navigationController = navigationController,
            onSortChanged = { sortController.update(it) }
        )
    }

    private fun setupListInteractionController() {
        listInteractionController = MemberListInteractionController(
            activity = activity,
            tag = "MainActivity",
            quickActionPrefs = quickActionPrefs,
            appearancePrefs = appearancePrefs,
            viewModel = viewModel,
            memberListAdapter = adapter,
            quickActionHelper = quickActionHelper
        )
    }

    private fun setupSwipeActionHandler() {
        swipeActionHandler = SwipeActionHandler(
            activity = activity,
            viewModel = viewModel,
            navigationController = navigationController,
            memberListPrefs = memberListPrefs,
            sortOrderController = sortController,
            quickActionHelper = quickActionHelper
        )
    }

    private fun setupSwipeGestureController() {
        swipeGestureController = MainSwipeGestureController(
            activity = activity,
            onSwipeLeft = { sortController.cycleForward() },
            onSwipeRight = { sortController.cycleBack() }
        )

        binding.lidmaatList.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent) = false
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                swipeGestureController.handleTouchEvent(e)
            }
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

        binding.lidmaatList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    listInteractionController.dismissQuickActions()
                }
            }
        })
    }

    private fun setupActivityResultCoordinator() {
        // Already created inside MainActivity – no action needed.
    }

    private fun setupWorkScheduler() {
        val dailyEnabled = backupPrefs.dailyBackupEnabled
        val exportToDownloads = backupPrefs.backupExportToDownloads
        workScheduler.schedulePastoralBackup(dailyEnabled, exportToDownloads)
        // workScheduler.scheduleAll() is still called from startupCoordinator's setupAlarms()
    }

    private fun setupUiBinder() {
        uiBinder = MainUiBinder(
            binding = binding,
            viewModel = viewModel,
            adapter = adapter,
            lifecycleOwner = activity,
            sortController = sortController
        )
        uiBinder.setupObservers()
    }

    private fun setupPastoralBadgeController() {
        val dao = PastoralDatabase.getInstance(activity).followUpReminderDao()
        val noteRepo = PastoralNoteRepository(activity) // or inject via constructor
        pastoralBadgeController = PastoralReminderBadgeController(
            activity = activity,
            followUpReminderDao = dao,
            memberViewModel = viewModel,
            mainViewModel = mainViewModel,
            pastoralNoteRepository = noteRepo
        )
        pastoralBadgeController.setup()
    }

    private fun setupStartupCoordinator() {
        val actions = object : StartupActions {
            override fun checkAndRequestPermissions() {
                permissionManager.requestPhonePermissions(activity)
                permissionManager.requestContactsPermissions(activity)
                //permissionManager.requestSmsPermissions(activity)
                permissionManager.requestCalendarPermissions(activity)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionManager.requestNotificationPermissions(activity)
                }
            }

            override fun startMonitoringServiceIfEnabled() {
                activity.startMonitoringServiceIfEnabled()
            }

            override fun setupViewModel() {
                // Already done
            }

            override fun setupPermissions() {
                activity.setupPermissions()
            }

            override fun initializeData() {
                // Already handled
            }

            override fun setupEventHandlers() {
                // Already handled
            }

            override fun setupAlarms() {
                workScheduler.scheduleAll()
            }

            override fun loadInitialData() {
                this@MainActivityInitializer.loadInitialData()
            }

            override fun ensureServicesAreRunning() {
                activity.ensureServicesAreRunning()
            }

            override fun openNotificationSettings() {
                navigationController.navigateToNotificationListenerSettings()
            }

            override fun showToast(message: String) {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }

        startupCoordinator = MainStartupCoordinator(
            context = activity,
            lifecycleScope = lifecycleScope,
            permissionManager = permissionManager,
            binding = binding,
            actions = actions,
            navigationController = navigationController,
            databaseInitializer = databaseInitializer,
            syncPrefs = syncPrefs,
            birthdaySmsPrefs = birthdaySmsPrefs,
            memberListPrefs = memberListPrefs,
            workScheduler = workScheduler,
            callLogImporter = callLogImporter,
            callMonitorPrefs = callMonitorPrefs
        )
    }

    private fun setupBackPressHandler() {
        backPressHandler = BackPressHandler(
            activity = activity,
            mainViewModel = mainViewModel,
            onCancelFilter = { searchFilterCoordinator.cancelAndRestore() },
            onFinish = { activity.finish() }
        )
        backPressHandler.register()
    }

    private fun restoreInstanceState() {
        savedInstanceState ?: return
        try {
            val savedSearchList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelableArrayList(
                    MainActivity.SEARCH_CHECK_BOX,
                    SearchCheckBox::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getParcelableArrayList<SearchCheckBox>(MainActivity.SEARCH_CHECK_BOX)
            }
            if (savedSearchList != null) {
                searchList = savedSearchList
                SearchCheckBoxPreferences(activity).saveSearchCheckBoxList(searchList)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("MainActivityInit", "Failed to restore search list", e)
        }

        val position = savedInstanceState.getInt("scroll_position", -1)
        if (position != -1) {
            binding.lidmaatList.post {
                binding.lidmaatList.scrollToPosition(position)
            }
        }
    }

    private fun setupScrollRestorationObserver() {
        lifecycleScope.launch {
            adapter.loadStateFlow
                .collect { loadStates ->
                    if (loadStates.refresh is LoadState.NotLoading && savedScrollStateWithSort != null) {
                        restoreListScrollIfNeeded()
                    }
                }
        }
    }

    private fun restoreListScrollIfNeeded() {
        // Skip if birthday sort has a pending explicit scroll
        if (sortController.hasPendingBirthdayScroll) return

        val saved = savedScrollStateWithSort ?: return
        val (savedState, savedSort) = saved

        // Only restore if the sort order hasn't changed
        if (savedSort != viewModel.sortOrder) {
            // Discard stale state
            savedScrollStateWithSort = null
            return
        }

        if (adapter.itemCount == 0) return
        MemberListScrollHelper.restoreScrollState(binding.lidmaatList, savedState, adapter)
        savedScrollStateWithSort = null
        scrollRestored = true
    }

    private fun loadInitialData() {
        if (BuildConfig.DEBUG) Log.d("MainActivityInit", "loadInitialData: started")

        chipController.refresh()        // Reload church info to make sure preferences are populated if they were just seeded\
//        lifecycleScope.launch(Dispatchers.IO) {
//            churchInfoRepo.loadChurchInfo()
//            withContext(Dispatchers.Main) {
//                chipController.setup() // Re-populate chips with newly loaded data
//            }
//        }

        if (::adapter.isInitialized && adapter.itemCount > 0) {
            val scrollState = MemberListScrollHelper.saveScrollState(binding.lidmaatList, adapter)
            savedScrollStateWithSort = scrollState?.let { it to viewModel.sortOrder }
        }

        if (searchList.isNotEmpty()) {
            viewModel.setSearchList(searchList)
        }

        hideKeyboard()
        binding.searchItemBlock.visibility = View.GONE
        searchFilterCoordinator.refresh()
        WhatsAppContactLoader.loadWhatsAppContactsAtomic(activity, lifecycleScope)

        // Only apply default layout if this is the very first load
        if (initState != InitState.Ready) {
            val defLayout = memberListPrefs.defLayout
            when {
                viewModel.sortOrder.isEmpty() -> sortController.update(defLayout)
                viewModel.sortOrder == "VAN" && defLayout != "VAN" -> sortController.update(
                    defLayout
                )

                viewModel.sortOrder == "VERJAAR" || viewModel.sortOrder == "VERJAARSDAG" -> {
                    sortController.requestBirthdayAnchor(viewModel.sortOrder)
                    viewModel.switchToBirthdaySort()
                }
            }
        }

        initState = InitState.Ready
    }

    fun updateSortOrder(sortOrder: String) {
        sortController.update(sortOrder)
    }

    private fun onAdapterStateChanged() {
        sortController.refreshLabel()
        adapter.updateState(
            listView = memberListPrefs.listView,
            soekList = viewModel.soekList,
            soek = viewModel.soek,
            recordStatus = viewModel.recordStatus,
            sortOrder = viewModel.sortOrder,
            useCongregationIndicator = congregationPrefs.useCongregationIndicator
        )
        sortController.prefetchBirthdayScrollIfNeeded()
    }

    private fun hideKeyboard() {
        activity.currentFocus?.let { view ->
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    // ─── Public API for MainActivity ────────────────────────────────────────

    val isReady: Boolean get() = initState == InitState.Ready

    fun onResumeAfterAuth() {
        if (initState != InitState.AwaitingAuth) {
            startupCoordinator.runOnResume()
            pastoralBadgeController.refresh()
            searchFilterCoordinator.updateSummaryView()
            sortController.syncWithSettings(initState == InitState.Ready)
            chipController.refresh()
            viewModel.refresh()
            binding.lidmaatList.post { restoreListScrollIfNeeded() }
        }
    }

    fun onPause() {
        if (initState == InitState.Ready) {
            val scrollState = MemberListScrollHelper.saveScrollState(binding.lidmaatList, adapter)
            savedScrollStateWithSort = scrollState?.let { it to viewModel.sortOrder }
        }
    }

    fun onDestroy() {
        menuController.clearCallbacks()
        listInteractionController.dismissQuickActions()
        WhatsAppContactLoader.reset()
    }


    private sealed class InitState {
        object AwaitingAuth : InitState()
        object LoadingData : InitState()
        object Ready : InitState()
    }
}