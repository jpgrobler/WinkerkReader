package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.bottomsheets.FilterBottomSheet
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.ui.helpers.MemberListScrollHelper
import za.co.jpsoft.winkerkreader.ui.viewmodels.MainViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.*
import za.co.jpsoft.winkerkreader.workers.PastoralBackupWorker

/**
 * Coordinates the initialisation and wiring of all MainActivity components.
 * Handles both pre‑authentication setup (controllers, adapters, ViewModels) and
 * post‑authentication finalisation (observers, background services, scroll state).
 */
class MainActivityInitializer(
    private val activity: MainActivity,
    private val savedInstanceState: Bundle?
) {

    // ─── Dependencies ──────────────────────────────────────────────────────────

    private val lifecycleScope = activity.lifecycleScope
    private val binding: ActivityMainBinding = activity.binding
    private val settingsManager = SettingsManager.getInstance(activity)
    private val permissionManager = PermissionManager(activity)
    private val navigationController = MainNavigationController(activity)
    private val workScheduler = WorkScheduler(activity, settingsManager)

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

    // ─── State ────────────────────────────────────────────────────────────────

    private var initState: InitState = InitState.AwaitingAuth
    private var savedListScroll: MemberListScrollHelper.ScrollState? = null
    private var searchList: ArrayList<SearchCheckBox> = arrayListOf()
    private var scrollRestored = false

    // ─── Initialisation entry point ─────────────────────────────────────────

    /**
     * Call this from MainActivity.onCreate() to set up everything that does not
     * depend on the app‑lock authentication (controllers, ViewModels, adapters).
     * The [onAuthenticated] callback will be invoked after the user passes the
     * biometric/PIN check, and will complete the finalisation.
     */
    fun setupPreAuth() {
        // 1. Initialise ViewModel and adapter (must happen before any controller that uses them)
        setupViewModelAndAdapter()

        // 2. Controllers that are needed before auth (chip, sort, search, menu, list interaction, swipe)
        setupChipController()
        setupSortController()
        setupSearchFilterCoordinator()
        setupMenuController()
        setupListInteractionController()
        setupSwipeGestureController()

        // 3. Result coordinator (uses activity’s registerForActivityResult)
        setupActivityResultCoordinator()

        // 4. Work scheduler and backup worker
        setupWorkScheduler()

        // 5. Now guard the rest with auth
        activity.appAuthGuard.guardIfNeeded(
            onAuthenticated = { setupPostAuth() }
        )
    }

    // ─── Post‑authentication finalisation ───────────────────────────────────

    private fun setupPostAuth() {
        // 1. Observers (UI bindings)
        setupUiBinder()

        // 2. Pastoral badge controller
        setupPastoralBadgeController()

        // 3. Startup coordinator (runs onCreate tasks)
        setupStartupCoordinator()
        startupCoordinator.runOnCreate()

        // 4. Back‑press handler
        setupBackPressHandler()

        // 5. Restore saved instance state (search list, scroll position)
        restoreInstanceState()

        // 6. Setup scroll restoration observer (listens to adapter load state)
        setupScrollRestorationObserver()

        // 7. Sync sort order with settings
        sortController.syncWithSettings(false)

        // 8. Load initial data (search/filter state, WhatsApp contacts)
        loadInitialData()

        // 9. Mark as ready
        initState = InitState.Ready
        activity.invalidateOptionsMenu()
    }

    // ─── Private setup methods ──────────────────────────────────────────────

    private fun setupViewModelAndAdapter() {
        val initialCongregations = listOfNotNull(
            settingsManager.congregation.gemeenteNaam.takeIf { it.isNotBlank() },
            settingsManager.congregation.gemeente2Naam.takeIf { it.isNotBlank() },
            settingsManager.congregation.gemeente3Naam.takeIf { it.isNotBlank() }
        ).toSet()

        val savedStateHandle = SavedStateHandle()
        viewModel = ViewModelProvider(
            activity,
            MemberViewModel.MemberViewModelFactory(
                activity.application,
                savedStateHandle,
                initialCongregations
            )
        ).get(MemberViewModel::class.java)

        adapter = MemberListAdapter(
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
            setHasFixedSize(false)
            itemAnimator = null
        }
        adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.ALLOW
    }

    private fun setupChipController() {
        chipController = CongregationChipController(
            context = activity,
            chipGroup = binding.congregationChipGroup,
            loadingBar = binding.indeterminateBar,
            settings = settingsManager,
            onFilterChanged = { selected ->
                viewModel.setCongregationFilter(selected)
                viewModel.refresh()
                searchFilterCoordinator.updateSummaryView()
            }
        )
        chipController.setup()
    }

    private fun setupSortController() {
        sortController = SortOrderController(
            tag = "MainActivity",
            viewModel = viewModel,
            mainViewModel = activity.mainViewModel,
            memberListAdapter = adapter,
            settings = settingsManager,
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
            settingsManager = settingsManager,
            binding = binding,
            memberListAdapter = adapter,
            findSearchView = { activity.findSearchView() },
            hideFilterPanel = {},
            onUpdateSortOrder = { sortController.update(it) },
            onRecomputeBirthdayOffset = { sortController.recomputeBirthdayOffset() },
            selectAllChips = { chipController.selectAll() },
            deselectChips = { chipController.deselectAll() }
        ).apply {
            onFilterRestored = { sortController.recomputeBirthdayOffset() }
            onFilterCancelled = {
                activity.currentFocus?.let { view ->
                    hideKeyboard()
                }
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
            settingsManager = settingsManager,
            viewModel = viewModel,
            memberListAdapter = adapter
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

        // Dismiss quick actions when scrolling
        binding.lidmaatList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    listInteractionController.dismissQuickActions()
                }
            }
        })
    }

    private fun setupActivityResultCoordinator() {
        // The ActivityResultCoordinator is already created inside MainActivity
        // because it requires registerForActivityResult before onCreate.
        // We keep it there; it's fine.
    }

    private fun setupWorkScheduler() {
        val dailyEnabled = settingsManager.backup.dailyBackupEnabled
        val exportToDownloads = settingsManager.backup.backupExportToDownloads
        if (dailyEnabled) {
            PastoralBackupWorker.schedule(activity, exportToDownloads)
        } else {
            PastoralBackupWorker.cancel(activity)
        }
        workScheduler.scheduleAll()
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
        pastoralBadgeController = PastoralReminderBadgeController(
            activity = activity,
            pastoralDb = PastoralDatabase.getInstance(activity),
            memberViewModel = viewModel,
            mainViewModel = activity.mainViewModel
        )
        pastoralBadgeController.setup()
    }

    private fun setupStartupCoordinator() {
        startupCoordinator = MainStartupCoordinator(
            context = activity,
            lifecycleScope = lifecycleScope,
            settingsManager = settingsManager,
            permissionManager = permissionManager,
            binding = binding,
            navigationController = navigationController,
            actions = object : StartupActions {
                override fun checkAndRequestPermissions() {
                    permissionManager.requestPhonePermissions(activity)
                    permissionManager.requestContactsPermissions(activity)
                    permissionManager.requestSmsPermissions(activity)
                    permissionManager.requestCalendarPermissions(activity)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionManager.requestNotificationPermissions(activity)
                    }
                }

                override fun startMonitoringServiceIfEnabled() {
                    activity.startMonitoringServiceIfEnabled()
                }

                override fun setupViewModel() { /* already done */
                }

                override fun setupPermissions() {
                    activity.setupPermissions()
                }

                override fun initializeData() {
                    activity.initializeData(savedInstanceState)
                }

                override fun setupEventHandlers() {
                    activity.setupEventHandlers()
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
        )
    }

    private fun setupBackPressHandler() {
        backPressHandler = BackPressHandler(
            activity = activity,
            mainViewModel = activity.mainViewModel,
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
            adapter.loadStateFlow.collect { loadStates ->
                if (loadStates.refresh is LoadState.NotLoading && savedListScroll != null) {
                    restoreListScrollIfNeeded()
                }
            }
        }
    }

    private fun restoreListScrollIfNeeded() {
        if (sortController.hasPendingBirthdayScroll) return
        val state = savedListScroll ?: return
        if (adapter.itemCount == 0) return
        MemberListScrollHelper.restoreScrollState(binding.lidmaatList, state, adapter)
        savedListScroll = null
        scrollRestored = true
    }

    private fun loadInitialData() {
        if (true) {
            if (BuildConfig.DEBUG) Log.d(
                "MainActivityInit",
                "loadInitialData: already started, skipping"
            )
            return
        }

        if (::adapter.isInitialized && adapter.itemCount > 0) {
            savedListScroll = MemberListScrollHelper.saveScrollState(binding.lidmaatList, adapter)
        }

        activity.initializeData(savedInstanceState)
        hideKeyboard()
        binding.searchItemBlock.visibility = View.GONE
        searchFilterCoordinator.refresh()
        WhatsAppContactLoader.loadWhatsAppContactsAtomic(activity, lifecycleScope)

        val defLayout = settingsManager.memberList.defLayout
        sortController.update(defLayout)

        initState = InitState.Ready
    }

    fun updateSortOrder(sortOrder: String) {
        sortController.update(sortOrder)
    }

    private fun onAdapterStateChanged() {
        sortController.refreshLabel()
        adapter.updateState(
            listView = settingsManager.memberList.listView,
            soekList = viewModel.soekList,
            soek = viewModel.soek,
            recordStatus = viewModel.recordStatus,
            sortOrder = viewModel.sortOrder,
            useCongregationIndicator = settingsManager.congregation.useCongregationIndicator
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

    /** Returns true when the app is fully initialised and ready for user interaction. */
    val isReady: Boolean get() = initState == InitState.Ready

    /** Called from MainActivity.onResumeAfterAuth() to refresh badges and sync state. */
    fun onResumeAfterAuth() {
        if (initState != InitState.AwaitingAuth) {
            startupCoordinator.runOnResume()
            pastoralBadgeController.refresh()
            searchFilterCoordinator.updateSummaryView()
            sortController.syncWithSettings(initState == InitState.Ready)
            viewModel.refresh()
            loadInitialData()
            binding.lidmaatList.post { restoreListScrollIfNeeded() }
        }
    }

    /** Called from MainActivity.onPause() to save scroll state. */
    fun onPause() {
        if (initState == InitState.Ready) {
            savedListScroll = MemberListScrollHelper.saveScrollState(binding.lidmaatList, adapter)
        }
    }

    /** Called from MainActivity.onDestroy() for cleanup. */
    fun onDestroy() {
        menuController.clearCallbacks()
        listInteractionController.dismissQuickActions()
        WhatsAppContactLoader.reset()
    }

    // ─── Inner state enum (mirrors MainActivity.InitState) ─────────────────

    private sealed class InitState {
        object AwaitingAuth : InitState()
        object LoadingData : InitState()
        object Ready : InitState()
    }
}