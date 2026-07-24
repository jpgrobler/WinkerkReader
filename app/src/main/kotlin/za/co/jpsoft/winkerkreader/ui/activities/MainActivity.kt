package za.co.jpsoft.winkerkreader.ui.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.repositories.ContactRepository
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding
import za.co.jpsoft.winkerkreader.services.CallMonitoringService
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.bottomsheets.FilterBottomSheet
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.ui.controllers.ActivityResultCoordinator
import za.co.jpsoft.winkerkreader.ui.controllers.MainMenuController
import za.co.jpsoft.winkerkreader.ui.controllers.MainSearchFilterCoordinator
import za.co.jpsoft.winkerkreader.ui.controllers.MainStartupCoordinator
import za.co.jpsoft.winkerkreader.ui.controllers.MainSwipeGestureController
import za.co.jpsoft.winkerkreader.ui.controllers.MemberListInteractionController
import za.co.jpsoft.winkerkreader.ui.controllers.PastoralReminderBadgeController
import za.co.jpsoft.winkerkreader.ui.controllers.StartupActions
import za.co.jpsoft.winkerkreader.ui.helpers.MemberListScrollHelper
import za.co.jpsoft.winkerkreader.ui.viewmodels.MainViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.BackPressHandler
import za.co.jpsoft.winkerkreader.utils.BatteryOptimizationHelper
import za.co.jpsoft.winkerkreader.utils.MainNavigationController
import za.co.jpsoft.winkerkreader.utils.MenuItemHandler
import za.co.jpsoft.winkerkreader.utils.PastoralNotificationHelper
import za.co.jpsoft.winkerkreader.utils.PermissionManager
import za.co.jpsoft.winkerkreader.utils.SearchCheckBoxPreferences
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.WhatsAppContactLoader
import za.co.jpsoft.winkerkreader.utils.WorkScheduler
import za.co.jpsoft.winkerkreader.workers.PastoralBackupWorker

/**
 * The main container Activity of the application. Displays the search interface,
 * congregation filter chips, and the paginated list of congregation members.
 */
class MainActivity : BaseActivity() {

    // ─── View Binding & Adapters ─────────────────────────────────────────────

    /** View binding containing layout elements. */
    lateinit var binding: ActivityMainBinding

    /** Adapter for rendering the paginated list of member records. */
    lateinit var memberListAdapter: MemberListAdapter


    // ─── Coordinators & Managers ─────────────────────────────────────────────

    /** Controls navigation flows to sub-activities. */
    private lateinit var navigationController: MainNavigationController

    /** Main shared configuration settings manager. */
    lateinit var settingsManager: SettingsManager

    /** Coordinates search parameters and filters between UI and ViewModel. */
    lateinit var searchFilterCoordinator: MainSearchFilterCoordinator

    /** Coordinates startup tasks, initialization sequence, and background services. */
    private lateinit var startupCoordinator: MainStartupCoordinator

    /** Manages application permissions requested from the user. */
    private lateinit var permissionManager: PermissionManager

    /** Schedules periodic background tasks (e.g. notifications and database syncing). */
    private lateinit var workScheduler: WorkScheduler

    /** Manages options menu states and creation. */
    private lateinit var menuController: MainMenuController

    /** Handles interaction clicks and quick-actions menu triggers on list rows. */
    private lateinit var listInteractionController: MemberListInteractionController

    /** Processes on-activity-result callbacks for child views or settings. */
    private lateinit var activityResultCoordinator: ActivityResultCoordinator

    /** Coordinates and handles physical device back button presses. */
    private lateinit var backPressHandler: BackPressHandler

    /** Controls pastoral reminders visual badge counts in the menu. */
    private lateinit var pastoralBadgeController: PastoralReminderBadgeController

    /** Intercepts and detects horizontal swipe gestures to change list sorting. */
    private lateinit var swipeGestureController: MainSwipeGestureController


    // ─── ViewModels ──────────────────────────────────────────────────────────

    /** Viewmodel supplying members data and pagination flows. */
    private lateinit var viewModel: MemberViewModel

    /** State-holder viewmodel that survives configuration changes. */
    val mainViewModel: MainViewModel by viewModels(
        factoryProducer = { SavedStateViewModelFactory(application, this, intent?.extras) }
    )


    // ─── Observers & States ──────────────────────────────────────────────────

    /** Observer listening to periodic DB backup worker progress. */
    private var workInfoObserver: Observer<WorkInfo?> = Observer { }

    /** Currently active LiveData monitoring Room/WorkManager database tasks. */
    private var currentWorkInfoLiveData: LiveData<WorkInfo?>? = null

    /** List of search fields currently checked for queries. */
    private var searchList: ArrayList<SearchCheckBox> = arrayListOf()

    /** Temporarily holds list scroll state during configuration changes. */
    private var savedListScroll: MemberListScrollHelper.ScrollState? = null

    /** Current sort order identifier (e.g. VAN, WYK, etc.). */
    private var currentSortOrder: String = ""

    /** Offset position where the next upcoming birthday member resides in the sorted list. */
    private var pendingBirthdayOffset: Int? = null

    /** Status checking if basic application components have completed initialization. */
    private var isAppInitialized = false

    /** Status checking if initial network/local database load has finished. */
    private var hasCompletedInitialLoad = false

    /** Flag indicating that initial data loading process has begun. */
    private var initialLoadStarted = false

    /** Flag indicating that initial data loading process has fully completed. */
    private var initialLoadComplete = false

    /** List of congregations registered in the configuration settings. */
    private lateinit var initialCongregations: Set<String>

    /** Flag representing if initial load has finished and configurations synced. */
    private var initialLoadDone = false


    companion object {
        private const val TAG = "Winkerk_MainActivity"
        const val CHANNEL_ID = "winkerkReaderServiceChannel"
        const val SEARCH_CHECK_BOX = "SEARCH_CHECK_BOX"
        const val FILTER_CHECK_BOX = "FILTER_CHECK_BOX"
    }


    // ─── Android Lifecycle Callbacks ─────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Basic configuration and settings managers setup
        settingsManager = SettingsManager.getInstance(this)
        initialCongregations = listOfNotNull(
            settingsManager.gemeenteNaam.takeIf { it.isNotBlank() },
            settingsManager.gemeente2Naam.takeIf { it.isNotBlank() },
            settingsManager.gemeente3Naam.takeIf { it.isNotBlank() }
        ).toSet()

        val dailyEnabled = settingsManager.dailyBackupEnabled
        val exportToDownloads = settingsManager.backupExportToDownloads
        if (dailyEnabled) {
            PastoralBackupWorker.schedule(this, exportToDownloads)
        } else {
            PastoralBackupWorker.cancel(this)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionManager = PermissionManager(this)

        workScheduler = WorkScheduler(this, settingsManager)
        workScheduler.scheduleAll()

        navigationController = MainNavigationController(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.lidmaatList) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        // 2. Setup ViewModel and data source controllers
        val savedStateHandle = SavedStateHandle()
        viewModel = ViewModelProvider(
            this,
            MemberViewModel.MemberViewModelFactory(
                application,
                savedStateHandle,
                initialCongregations
            )
        ).get(MemberViewModel::class.java)

        memberListAdapter = MemberListAdapter(
            onItemClick = { view, item, _ ->
                if (::listInteractionController.isInitialized) {
                    listInteractionController.showMemberPopupMenu(view, item)
                }
            },
            onItemLongClick = { item, _ ->
                if (::listInteractionController.isInitialized) {
                    listInteractionController.onMemberLongClick(item)
                } else {
                    false
                }
            }
        )

        binding.lidmaatList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = memberListAdapter
            setHasFixedSize(false)
            itemAnimator = null
        }
        memberListAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.ALLOW

        searchFilterCoordinator = MainSearchFilterCoordinator(
            tag = TAG,
            context = this,
            viewModel = viewModel,
            settingsManager = settingsManager,
            binding = binding,
            memberListAdapter = memberListAdapter,
            findSearchView = ::findSearchView,
            hideFilterPanel = {}
        )

        menuController = MainMenuController(
            activity = this,
            tag = TAG,
            viewModel = viewModel,
            searchFilterCoordinator = searchFilterCoordinator,
            observeDataset = ::observeDataset,
            navigationController = navigationController,
            onSortChanged = ::updateSortOrder
        )

        listInteractionController = MemberListInteractionController(
            activity = this,
            tag = TAG,
            settingsManager = settingsManager,
            viewModel = viewModel,
            memberListAdapter = memberListAdapter,
            observeDataset = ::observeDataset
        )

        swipeGestureController = MainSwipeGestureController(
            activity = this,
            onSwipeLeft = {
                when (viewModel.sortOrder) {
                    "HUWELIK" -> updateSortOrder("VAN")
                    "VAN" -> updateSortOrder("GESINNE")
                    "GESINNE" -> updateSortOrder("WYK")
                    "WYK" -> updateSortOrder("OUDERDOM")
                    "OUDERDOM" -> updateSortOrder("ADRES")
                    "ADRES" -> updateSortOrder("VERJAAR")
                    "VERJAAR" -> updateSortOrder("HUWELIK")
                    else -> updateSortOrder("VAN")
                }
                viewModel.refresh()
            },
            onSwipeRight = {
                when (viewModel.sortOrder) {
                    "HUWELIK" -> updateSortOrder("VERJAAR")
                    "VERJAAR" -> updateSortOrder("ADRES")
                    "ADRES" -> updateSortOrder("OUDERDOM")
                    "OUDERDOM" -> updateSortOrder("WYK")
                    "WYK" -> updateSortOrder("GESINNE")
                    "GESINNE" -> updateSortOrder("VAN")
                    "VAN" -> updateSortOrder("HUWELIK")
                    else -> updateSortOrder("VAN")
                }
                viewModel.refresh()
            }
        )

        binding.lidmaatList.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                swipeGestureController.handleTouchEvent(e)
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

        activityResultCoordinator = ActivityResultCoordinator(
            activity = this,
            searchCheckBoxKey = SEARCH_CHECK_BOX,
            filterBoxKey = FILTER_CHECK_BOX,
            onSearchResult = { list ->
                searchList = list
                viewModel.setSearchList(searchList)
            },
            onFilterResult = { list ->
                searchFilterCoordinator.applyFilterResult(list, viewModel.sortOrder)
            },
            onCancelled = ::handleResultCancelled
        )

        // 3. UI details and chip setup
        setupFilterChips()

        // 4. Load remaining application state behind AuthGuard
        appAuthGuard.guardIfNeeded(
            onAuthenticated = {
                loadDataAndFinalize(savedInstanceState)
            }
        )

        binding.lidmaatList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    listInteractionController.dismissQuickActions()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (settingsManager.callLogEnabled) {
            BatteryOptimizationHelper.showBatteryOptimizationDialog(this)
        }
    }

    override fun onPause() {
        if (isAppInitialized) {
            savedListScroll =
                MemberListScrollHelper.saveScrollState(binding.lidmaatList, memberListAdapter)
        }
        super.onPause()
    }

    override fun onDestroy() {
        currentWorkInfoLiveData?.removeObserver(workInfoObserver)
        if (::menuController.isInitialized) {
            menuController.clearCallbacks()
        }
        WhatsAppContactLoader.reset()
        if (::listInteractionController.isInitialized) {
            listInteractionController.dismissQuickActions()
        }
        super.onDestroy()
    }


    // ─── Authentication Guarded Resume Housekeeping ─────────────────────────

    override fun onResumeAfterAuth() {
        if (isAppInitialized) {
            startupCoordinator.runOnResume()
            pastoralBadgeController.refresh()
            updateFilterSummary()

            val currentSort = settingsManager.defLayout
            if (currentSort == "VERJAAR" || currentSort == "VERJAARSDAG") {
                scrollToCurrentBirthday()
            }

            if (!initialLoadDone) {
                initialLoadDone = true
                loadInitialData()
            }

            syncSortOrderWithSettings()
            binding.lidmaatList.post { restoreListScrollIfNeeded() }
        }
    }

    /**
     * Initializes core observers, background triggers, and registers event loops after successful auth.
     */
    private fun loadDataAndFinalize(savedInstanceState: Bundle?) {
        setupObservers()
        setupViewModelObservers()

        initialLoadDone = false
        initialLoadStarted = false

        pastoralBadgeController = PastoralReminderBadgeController(
            activity = this,
            pastoralDb = PastoralDatabase.getInstance(this),
            memberViewModel = viewModel,
            mainViewModel = mainViewModel
        )
        pastoralBadgeController.setup()

        startupCoordinator = MainStartupCoordinator(
            context = this,
            lifecycleScope = lifecycleScope,
            settingsManager = settingsManager,
            permissionManager = permissionManager,
            binding = binding,
            navigationController = navigationController,
            actions = object : StartupActions {
                override fun checkAndRequestPermissions() {
                    permissionManager.requestPhonePermissions(this@MainActivity)
                    permissionManager.requestContactsPermissions(this@MainActivity)
                    permissionManager.requestSmsPermissions(this@MainActivity)
                    permissionManager.requestCalendarPermissions(this@MainActivity)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionManager.requestNotificationPermissions(this@MainActivity)
                    }
                }

                override fun startMonitoringServiceIfEnabled() {
                    this@MainActivity.startMonitoringServiceIfEnabled()
                }

                override fun setupViewModel() {}

                override fun setupPermissions() {
                    this@MainActivity.setupPermissions()
                }

                override fun initializeData() {
                    this@MainActivity.initializeData(null)
                }

                override fun setupEventHandlers() {
                    this@MainActivity.setupEventHandlers()
                }

                override fun setupAlarms() {
                    workScheduler.scheduleAll()
                }

                override fun loadInitialData() {
                    this@MainActivity.loadInitialData()
                }

                override fun ensureServicesAreRunning() {
                    this@MainActivity.ensureServicesAreRunning()
                }

                override fun openNotificationSettings() {
                    navigationController.navigateToNotificationListenerSettings()
                }

                override fun showToast(message: String) {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
        startupCoordinator.runOnCreate()

        backPressHandler = BackPressHandler(
            activity = this,
            mainViewModel = mainViewModel,
            onCancelFilter = { cancelFilter() },
            onFinish = { finish() }
        )
        backPressHandler.register()
        savedInstanceState?.let { restoreInstanceState(it) }

        syncSortOrderWithSettings()
        setupBirthdayScrollHandling()
        setupScrollRestorationObserver()

        invalidateOptionsMenu()
        isAppInitialized = true
    }


    // ─── Options Menu Callbacks ──────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return if (isAppInitialized) {
            menuController.onCreateOptionsMenu(menu)
        } else {
            false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!isAppInitialized) {
            return super.onOptionsItemSelected(item)
        }

        if (item.itemId == R.id.filter_options) {
            FilterBottomSheet().show(supportFragmentManager, "filter")
            return true
        }

        return MenuItemHandler(this, settingsManager, viewModel, navigationController)
            .handleMenuItem(item) || super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (!isAppInitialized) return false

        val bedieningItem = menu.findItem(R.id.action_bediening)
        if (bedieningItem != null) {
            val count = pastoralBadgeController.badgeCount
            val title = if (count > 0) {
                getString(R.string.mainmenu_bediening_badge, count)
            } else {
                getString(R.string.mainmenu_bediening)
            }
            bedieningItem.title = title
        }

        return super.onPrepareOptionsMenu(menu)
    }


    // ─── Data Observers & Bindings ───────────────────────────────────────────

    /**
     * Connects LiveData elements and flows inside the ViewModels directly to UI elements.
     */
    private fun setupObservers() {
        viewModel.getTextLiveData().observe(this) { searchText ->
            binding.searchText.text = searchText
            binding.searchItemBlock.visibility =
                if (searchText.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.getVerjaarFLag().observe(this) { showBirthday ->
            if (BuildConfig.DEBUG) Log.d(TAG, "verjaarFlag: $showBirthday")
        }

        viewModel.memberGuidsWithPendingReminders.observe(this) { guids ->
            if (BuildConfig.DEBUG) Log.d(TAG, "🔄 Observer received ${guids.size} GUIDs: $guids")
            memberListAdapter.updatePendingReminderGuids(guids)
            restoreListScrollIfNeeded()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totalCount.collect { count ->
                    val txt = "($count)"
                    binding.totalCount.text = txt
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (BuildConfig.DEBUG) Log.d(TAG, "📊 Starting paging data collection")

                viewModel.pagingDataFlowWithRefresh
                    .catch { e ->
                        if (BuildConfig.DEBUG) Log.e(TAG, "Paging flow error", e)
                    }
                    .collectLatest { pagingData ->
                        if (binding.lidmaatList.adapter == null) {
                            binding.lidmaatList.adapter = memberListAdapter
                        }
                        memberListAdapter.submitData(lifecycle, pagingData)
                    }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                memberListAdapter.loadStateFlow.collect { loadStates ->
                    val isLoading = loadStates.refresh is LoadState.Loading
                    binding.indeterminateBar.visibility = if (isLoading) View.VISIBLE else View.GONE

                    if (loadStates.refresh is LoadState.Error) {
                        if (BuildConfig.DEBUG) Log.e(
                            TAG,
                            "Load error: ${(loadStates.refresh as LoadState.Error).error.message}"
                        )
                    }
                }
            }
        }
    }

    /** Observes repository and intent modifications to refresh items dynamically. */
    private fun setupViewModelObservers() {
        lifecycleScope.launch {
            ContactRepository.contactsUpdateFlow.collect {
                memberListAdapter.rebindVisibleItems(binding.lidmaatList)
            }
        }
    }

    /**
     * Triggered to notify adapter state modifications and display search overlays.
     */
    fun observeDataset() {
        if (BuildConfig.DEBUG) Log.d(TAG, "observeDataset called")

        val filterList = viewModel.getCurrentFilterList()
        val hasFilter = filterList != null && filterList.any { it.checked }
        val hasSearch = viewModel.soekList && viewModel.soek.isNotEmpty()
        val isBirthdaySort =
            viewModel.sortOrder == "VERJAAR" || viewModel.sortOrder == "VERJAARSDAG"

        when {
            hasFilter -> updateFilterSummary()
            hasSearch -> {
                binding.searchItemBlock.visibility = View.VISIBLE
                binding.searchText.text = viewModel.soek
                binding.mainSearchTextClose.visibility = View.VISIBLE
                binding.mainSearchTextClose.setOnClickListener {
                    searchFilterCoordinator.onSearchClosed()
                }
            }
            else -> {
                binding.searchItemBlock.visibility = View.GONE
                binding.searchText.text = ""
                binding.mainSearchTextClose.visibility = View.GONE
            }
        }

        binding.sortorder.text = getSortIcon(viewModel.sortOrder)

        memberListAdapter.updateState(
            listView = settingsManager.listView,
            soekList = viewModel.soekList,
            soek = viewModel.soek,
            recordStatus = viewModel.recordStatus,
            sortOrder = viewModel.sortOrder,
            useCongregationIndicator = settingsManager.useCongregationIndicator
        )

        if (isBirthdaySort && pendingBirthdayOffset == null) {
            lifecycleScope.launch {
                val offset = viewModel.getBirthdayOffset(viewModel.sortOrder)
                if (offset > 0) {
                    pendingBirthdayOffset = offset
                    memberListAdapter.loadStateFlow.collect { loadStates ->
                        if (loadStates.refresh is LoadState.NotLoading) {
                            val itemCount = memberListAdapter.itemCount
                            if (itemCount > 0) {
                                val scrollPos = if (offset < itemCount) offset else itemCount - 1
                                binding.lidmaatList.post {
                                    (binding.lidmaatList.layoutManager as? LinearLayoutManager)
                                        ?.scrollToPositionWithOffset(scrollPos, 0)
                                }
                                pendingBirthdayOffset = null
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── Initialization & Setup Helpers ──────────────────────────────────────
    /** Performs the initial queries load after app authorization check. */
    private fun loadInitialData() {
        if (initialLoadStarted) {
            if (BuildConfig.DEBUG) Log.d(TAG, "loadInitialData: already started, skipping")
            return
        }
        initialLoadStarted = true

        if (::memberListAdapter.isInitialized && memberListAdapter.itemCount > 0) {
            savedListScroll =
                MemberListScrollHelper.saveScrollState(binding.lidmaatList, memberListAdapter)
        }
        initializeData(null)
        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
        binding.searchItemBlock.visibility = View.GONE
        searchFilterCoordinator.refresh()
        WhatsAppContactLoader.loadWhatsAppContactsAtomic(this, lifecycleScope)

        hasCompletedInitialLoad = true

        val defLayout = settingsManager.defLayout
        updateSortOrder(defLayout)

        if (defLayout == "VERJAAR" || defLayout == "VERJAARSDAG") {
            binding.lidmaatList.postDelayed({
                scrollToCurrentBirthday()
            }, 500)
        }
        initialLoadComplete = true
    }

    /** Prepares default local configurations for filters and search fields. */
    private fun initializeData(savedInstanceState: Bundle?) {
        setupVersionInfo()
        initializeSearchAndFilterLists()
        viewModel.setSearchList(searchList)
        savedInstanceState?.let { restoreInstanceState(it) }
        if (settingsManager.defLayout.isEmpty()) {
            settingsManager.defLayout = "GESINNE"
        }
        viewModel.soekList = false
    }

    /** Populates the title bar with current build version metadata. */
    private fun setupVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName

            supportActionBar?.apply {
                val title = SpannableString("WinkerkReader")
                title.setSpan(
                    android.text.style.RelativeSizeSpan(0.75f),
                    0,
                    title.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                this.title = title
                subtitle = "v$versionName"
            }
        } catch (e: PackageManager.NameNotFoundException) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to get package info", e)
            supportActionBar?.title = "WinkerkReader"
        }
    }

    /** Creates standard default list items configurations for search dialogs. */
    private fun createDefaultSearchList(): ArrayList<SearchCheckBox> {
        return arrayListOf(
            SearchCheckBox(winkerkEntry.LIDMATE_VAN, "", "Van", true),
            SearchCheckBox(winkerkEntry.LIDMATE_NOEMNAAM, "", "Noemnaam", true),
            SearchCheckBox(winkerkEntry.LIDMATE_VOORNAME, "", "Voorname", true),
            SearchCheckBox(winkerkEntry.LIDMATE_WYK, "", "Wyk", true),
            SearchCheckBox(winkerkEntry.LIDMATE_SELFOON, "", "Selfoon", true),
            SearchCheckBox(winkerkEntry.ADRESSE_LANDLYN, "", "Landlyn", true),
            SearchCheckBox(winkerkEntry.LIDMATE_NOOIENSVAN, "", "Nooiensvan", true),
            SearchCheckBox(winkerkEntry.LIDMATE_BEROEP, "", "Beroep", true),
            SearchCheckBox(winkerkEntry.LIDMATE_EPOS, "", "Epos", true),
            SearchCheckBox(winkerkEntry.LIDMATE_STRAATADRES, "", "Adres", true)
        )
    }

    /** Restores checked search criteria from shared preferences. */
    private fun initializeSearchAndFilterLists() {
        val prefsManager = SearchCheckBoxPreferences(this)
        searchList = prefsManager.getSearchCheckBoxList()
        if (searchList.isEmpty()) {
            searchList = createDefaultSearchList()
            prefsManager.saveSearchCheckBoxList(searchList)
        }
    }

    private fun setupEventHandlers() {
        setupSearchCloseHandler()
        binding.sortorder.setOnClickListener {}
    }

    private fun setupSearchCloseHandler() {
        binding.mainSearchTextClose.setOnClickListener {
            searchFilterCoordinator.resetAllFiltersAndSearch()
        }
    }

    private fun setupPermissions() {
        checkOverlayPermission()
        createNotificationChannel()
        PastoralNotificationHelper.ensureChannel(this)
    }

    private fun createNotificationChannel() {
        val serviceChannel =
            NotificationChannel(CHANNEL_ID, "Oproep", NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(serviceChannel)
    }


    // ─── Filter Operations ───────────────────────────────────────────────────

    /**
     * Initializes congregation select chips dynamically from layout properties.
     */
    private fun setupFilterChips() {
        val group = binding.congregationChipGroup
        val settings = SettingsManager.getInstance(this)

        group.removeAllViews()
        group.isSingleSelection = false
        group.isSelectionRequired = false

        val congregations = listOfNotNull(
            settings.gemeenteNaam.takeIf { it.isNotBlank() },
            settings.gemeente2Naam.takeIf { it.isNotBlank() },
            settings.gemeente3Naam.takeIf { it.isNotBlank() }
        )

        congregations.forEach { name ->
            val color = when (name) {
                settings.gemeenteNaam -> settings.gemeenteKleur
                settings.gemeente2Naam -> settings.gemeente2Kleur
                settings.gemeente3Naam -> settings.gemeente3Kleur
                else -> ContextCompat.getColor(this, R.color.md_theme_primary)
            }

            val chip = Chip(this).apply {
                text = name
                isCheckable = true
                textSize = 12f
                isChecked = true

                setChipBackgroundColor(
                    android.content.res.ColorStateList.valueOf(color)
                )
                val textColor = if (isColorDark(color)) Color.WHITE else Color.BLACK
                setTextColor(textColor)
                chipStrokeWidth = 2f
                chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    if (isColorDark(color)) Color.BLUE else Color.BLUE
                )

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        setChipBackgroundColor(
                            android.content.res.ColorStateList.valueOf(color)
                        )
                        val textColor = if (isColorDark(color)) Color.WHITE else Color.BLACK
                        setTextColor(textColor)
                        chipStrokeWidth = 2f
                        chipStrokeColor = android.content.res.ColorStateList.valueOf(
                            if (isColorDark(color)) Color.WHITE else Color.BLACK
                        )
                    } else {
                        setChipBackgroundColor(
                            android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(
                                    this@MainActivity,
                                    R.color.md_theme_surfaceVariant
                                )
                            )
                        )
                        setTextColor(
                            ContextCompat.getColor(
                                this@MainActivity,
                                R.color.md_theme_onSurface
                            )
                        )
                        chipStrokeWidth = 0f
                    }
                    applyCongregationFilter()
                }
            }
            group.addView(chip)
        }

        binding.indeterminateBar.post {
            binding.indeterminateBar.visibility = View.GONE
        }
    }

    /** Resolves which congregation chips are actively checked. */
    private fun getSelectedCongregations(): Set<String> {
        val group = binding.congregationChipGroup
        val selected = mutableSetOf<String>()
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip
            if (chip?.isChecked == true) {
                selected.add(chip.text.toString())
            }
        }
        return selected
    }

    /** Applies checked chip tags directly as ViewModel query limitations. */
    private fun applyCongregationFilter() {
        val selected = getSelectedCongregations()
        viewModel.setCongregationFilter(selected)
        viewModel.refresh()
    }

    /**
     * Builds and displays the text string summarizing active filters on the search panel.
     */
    fun updateFilterSummary() {
        val filterList = viewModel.getCurrentFilterList()
        if (filterList != null && filterList.any { it.checked }) {
            val summary = buildFilterSummary(filterList)
            binding.searchText.text = summary
            binding.searchItemBlock.visibility = View.VISIBLE
            binding.mainSearchTextClose.visibility = View.VISIBLE
            binding.mainSearchTextClose.setOnClickListener {
                clearFilter()
            }
        } else {
            binding.searchItemBlock.visibility = View.GONE
            binding.searchText.text = ""
        }
    }

    /** Formats active database selection structures as UI summary strings. */
    private fun buildFilterSummary(filterList: ArrayList<FilterBox>): String {
        val parts = mutableListOf<String>()

        val status = when (viewModel.recordStatus) {
            "0" -> "Aktief"
            "2" -> "Onaktief"
            "*" -> "Almal"
            else -> "Aktief"
        }
        parts.add("Status: $status")

        filterList.filter { it.checked }.forEach { filter ->
            when {
                filter.title == "Selfoon" -> parts.add("Met Selfoon")
                filter.title == "Landlyn" -> parts.add("Met Landlyn")
                filter.title == "E-pos" -> parts.add("Met E-pos")
                filter.title == "Gesinshoof" -> parts.add("Gesinshoofde")

                filter.title == "Geslag" -> {
                    val value = when (filter.text3) {
                        "manlik" -> "Manlik"
                        "vroulik" -> "Vroulik"
                        else -> filter.text3
                    }
                    parts.add("Geslag: $value")
                }

                filter.title == "Huwelikstatus" -> parts.add("Huwelik: ${filter.text3}")
                filter.title == "Lidmaatskap" -> parts.add("Lidmaatskap: ${filter.text3}")

                filter.title == "Ouderdom" -> {
                    when (filter.text3) {
                        "gelyk" -> parts.add("Ouderdom: ${filter.text1}")
                        "kleiner as" -> parts.add("Ouderdom < ${filter.text1}")
                        "groter as" -> parts.add("Ouderdom > ${filter.text1}")
                        "tussen" -> parts.add("Ouderdom: ${filter.text1}-${filter.text2}")
                    }
                }

                filter.text3 == "leeg" -> parts.add("${filter.title} is leeg")
                filter.text1.isNotEmpty() -> {
                    when (filter.text3) {
                        "gelyk aan" -> parts.add("${filter.title}: ${filter.text1}")
                        "nie gelyk aan" -> parts.add("${filter.title} ≠ ${filter.text1}")
                        "begin met" -> parts.add("${filter.title} begin met ${filter.text1}")
                        "eindig met" -> parts.add("${filter.title} eindig met ${filter.text1}")
                    }
                }
            }
        }

        return parts.joinToString(" • ")
    }

    /** Clears active search parameters and restores original sorting structure. */
    private fun clearFilter() {
        if (BuildConfig.DEBUG) Log.d(TAG, "clearFilter called")

        val restoreSort = if (searchFilterCoordinator.originalLayoutBeforeFilter.isNotEmpty()) {
            searchFilterCoordinator.originalLayoutBeforeFilter
        } else {
            "VAN"
        }

        viewModel.clearFilters()
        viewModel.recordStatus = "0"

        searchFilterCoordinator.originalLayoutBeforeFilter = ""
        searchFilterCoordinator.filterList = null

        binding.searchItemBlock.visibility = View.GONE
        binding.searchText.text = ""
        binding.mainSearchTextClose.visibility = View.GONE
        viewModel.clearFilterSummary()

        currentSortOrder = restoreSort
        settingsManager.defLayout = restoreSort
        mainViewModel.setSortOrder(restoreSort)

        viewModel.sortOrder = restoreSort
        viewModel.updateSortOrder(restoreSort)

        memberListAdapter.updateState(
            listView = settingsManager.listView,
            soekList = viewModel.soekList,
            soek = viewModel.soek,
            recordStatus = viewModel.recordStatus,
            sortOrder = restoreSort,
            useCongregationIndicator = settingsManager.useCongregationIndicator
        )

        binding.sortorder.text = getSortIcon(restoreSort)
        viewModel.refresh()
        recomputeBirthdayOffset()
    }

    /** Cancels the current filter criteria, reverting sorting and hiding the text summary. */
    fun cancelFilter() {
        if (BuildConfig.DEBUG) Log.d(TAG, "cancelFilter called")
        recomputeBirthdayOffset()
        val restoreSort = if (searchFilterCoordinator.originalLayoutBeforeFilter.isNotEmpty()) {
            searchFilterCoordinator.originalLayoutBeforeFilter
        } else {
            "VAN"
        }

        clearAppliedFilterList()
        viewModel.soekList = false

        updateSortOrder(restoreSort)
        searchFilterCoordinator.originalLayoutBeforeFilter = ""
        mainViewModel.setSavedSortOrderBeforeFilter(null)

        binding.mainMain.visibility = View.VISIBLE
        mainViewModel.setFilterVisible(false)

        binding.searchText.text = ""
        binding.searchItemBlock.visibility = View.GONE
        viewModel.clearFilterSummary()

        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }

        resetChipSelection()
    }

    private fun resetChipSelection() {
        val congGroup = binding.congregationChipGroup
        for (i in 0 until congGroup.childCount) {
            (congGroup.getChildAt(i) as? Chip)?.isChecked = false
        }
        applyCongregationFilter()
    }

    fun clearAppliedFilterList() {
        searchFilterCoordinator.filterList = null
    }


    // ─── Sorting & Scroll Control ────────────────────────────────────────────

    /** Synchronizes current visual sort orders matching settings options. */
    private fun syncSortOrderWithSettings() {
        if (!hasCompletedInitialLoad) {
            return
        }
        val currentDefLayout = settingsManager.defLayout
        updateSortOrder(currentDefLayout)
    }

    fun updateSortOrder(newSort: String) {
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "updateSortOrder: newSort=$newSort, currentSort=$currentSortOrder"
        )

        if (newSort == currentSortOrder) {
            memberListAdapter.updateState(
                listView = settingsManager.listView,
                soekList = viewModel.soekList,
                soek = viewModel.soek,
                recordStatus = viewModel.recordStatus,
                sortOrder = newSort,
                useCongregationIndicator = settingsManager.useCongregationIndicator
            )
            binding.sortorder.text = getSortIcon(newSort)
            return
        }

        currentSortOrder = newSort
        settingsManager.defLayout = newSort
        mainViewModel.setSortOrder(newSort)

        viewModel.updateSortOrder(newSort)

        memberListAdapter.updateState(
            listView = settingsManager.listView,
            soekList = viewModel.soekList,
            soek = viewModel.soek,
            recordStatus = viewModel.recordStatus,
            sortOrder = newSort,
            useCongregationIndicator = settingsManager.useCongregationIndicator
        )

        binding.sortorder.text = getSortIcon(newSort)
        invalidateOptionsMenu()

        if (newSort == "VERJAAR" || newSort == "VERJAARSDAG") {
            scrollToCurrentBirthday()
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "updateSortOrder completed")
    }


    /** Launches a coroutine to retrieve upcoming birthdays offset index and scroll to it. */
    private fun scrollToCurrentBirthday() {
        lifecycleScope.launch {
            try {
                val offset = viewModel.getBirthdayOffset(viewModel.sortOrder)
                memberListAdapter.loadStateFlow.collect { loadStates ->
                    if (loadStates.refresh is LoadState.NotLoading) {
                        val itemCount = memberListAdapter.itemCount
                        if (itemCount > 0 && offset >= 0) {
                            val scrollPos = if (offset < itemCount) offset else itemCount - 1
                            binding.lidmaatList.post {
                                (binding.lidmaatList.layoutManager as? LinearLayoutManager)
                                    ?.scrollToPositionWithOffset(scrollPos, 0)
                            }
                        }
                        return@collect
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to scroll to birthday", e)
            }
        }
    }

    fun recomputeBirthdayOffset() {
        val currentSort = settingsManager.defLayout
        if (currentSort == "VERJAAR" || currentSort == "VERJAARSDAG") {
            lifecycleScope.launch {
                pendingBirthdayOffset = viewModel.getBirthdayOffset(currentSort)
            }
        }
    }

    private fun setupBirthdayScrollHandling() {
        if (BuildConfig.DEBUG) Log.d(TAG, "setupBirthdayScrollHandling initialized")
    }


    // ─── Service Controls ────────────────────────────────────────────────────

    /** Launches standard foreground Telephony broadcast listening services. */
    private fun startMonitoringServiceIfEnabled() {
        if (settingsManager.autoStartEnabled && !CallMonitoringService.isServiceRunning(this)) {
            try {
                val serviceIntent = Intent(this, CallMonitoringService::class.java)
                startForegroundService(serviceIntent)
            } catch (e: SecurityException) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Security exception - check permissions", e)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to start call monitoring service", e)
            }
        }
    }

    private fun checkOverlayPermission() {
        permissionManager.requestOverlayPermissionWithRationale(this)
    }

    private fun ensureServicesAreRunning() {
        if (settingsManager.autoStartEnabled && !CallMonitoringService.isServiceRunning(this)) {
            startMonitoringServiceIfEnabled()
        }
    }


    // ─── Touch Gestures & Inputs ─────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (isAppInitialized) {
            swipeGestureController.onTouchEvent(event) || super.onTouchEvent(event)
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (isAppInitialized) {
            val chipView = binding.chipScrollView
            if (isTouchInsideView(event, chipView)) {
                return super.dispatchTouchEvent(event)
            }
            swipeGestureController.handleTouchEvent(event)
        }
        return super.dispatchTouchEvent(event)
    }

    private fun isTouchInsideView(event: MotionEvent, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = event.rawX
        val y = event.rawY
        return x >= location[0] && x <= location[0] + view.width &&
                y >= location[1] && y <= location[1] + view.height
    }


    // ─── State Restoration ───────────────────────────────────────────────────

    private fun restoreInstanceState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        try {
            val savedSearchList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelableArrayList(
                    SEARCH_CHECK_BOX,
                    SearchCheckBox::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getParcelableArrayList<SearchCheckBox>(SEARCH_CHECK_BOX)
            }
            if (savedSearchList != null) {
                searchList = savedSearchList
                SearchCheckBoxPreferences(this).saveSearchCheckBoxList(searchList)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to restore search list", e)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(SEARCH_CHECK_BOX, searchList)
        (binding.lidmaatList.layoutManager as? LinearLayoutManager)?.let { lm ->
            outState.putInt("scroll_position", lm.findFirstVisibleItemPosition())
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val position = savedInstanceState.getInt("scroll_position", -1)
        if (position != -1) {
            binding.lidmaatList.post {
                binding.lidmaatList.scrollToPosition(position)
            }
        }
    }

    private fun restoreListScrollIfNeeded() {
        if (pendingBirthdayOffset != null) return
        val state = savedListScroll ?: return
        if (memberListAdapter.itemCount == 0) return
        MemberListScrollHelper.restoreScrollState(binding.lidmaatList, state, memberListAdapter)
        savedListScroll = null
        scrollRestored = true
    }

    private var scrollRestored = false

    private fun setupScrollRestorationObserver() {
        lifecycleScope.launch {
            memberListAdapter.loadStateFlow.collect { loadStates ->
                if (loadStates.refresh is LoadState.NotLoading && savedListScroll != null) {
                    restoreListScrollIfNeeded()
                }
            }
        }
    }


    // ─── Helper & Utility functions ──────────────────────────────────────────

    private fun isColorDark(color: Int): Boolean {
        val darkness =
            1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    private fun getSortIcon(sortOrder: String): String = when (sortOrder) {
        "VAN" -> "⇵🔤"
        "GESINNE" -> "⇵👨‍👩‍👧‍👦"
        "WYK" -> "⇵🏘️"
        "OUDERDOM" -> "⇵📅"
        "VERJAAR" -> "⇵🎂"
        "ADRES" -> "⇵📌"
        "HUWELIK" -> "⇵💍"
        else -> "⇵📋"
    }

    private fun findSearchView(): SearchView? {
        return if (::menuController.isInitialized) menuController.findSearchView() else null
    }

    private fun handleResultCancelled() {
        if (::searchFilterCoordinator.isInitialized) {
            searchFilterCoordinator.handleResultCancelled()
        }
    }
}