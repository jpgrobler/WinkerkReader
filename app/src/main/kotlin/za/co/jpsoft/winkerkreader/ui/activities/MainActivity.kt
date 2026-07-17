package za.co.jpsoft.winkerkreader.ui.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import za.co.jpsoft.winkerkreader.utils.DeviceIdManager
import za.co.jpsoft.winkerkreader.utils.MainNavigationController
import za.co.jpsoft.winkerkreader.utils.MenuItemHandler
import za.co.jpsoft.winkerkreader.utils.PastoralDatabaseBackup
import za.co.jpsoft.winkerkreader.utils.PastoralNotificationHelper
import za.co.jpsoft.winkerkreader.utils.PermissionManager
import za.co.jpsoft.winkerkreader.utils.SearchCheckBoxPreferences
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.WhatsAppContactLoader
import za.co.jpsoft.winkerkreader.utils.WorkScheduler
import za.co.jpsoft.winkerkreader.workers.PastoralBackupWorker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity() {

    lateinit var binding: ActivityMainBinding
    lateinit var memberListAdapter: MemberListAdapter

    private lateinit var navigationController: MainNavigationController
    private lateinit var viewModel: MemberViewModel
    lateinit var settingsManager: SettingsManager

    private lateinit var workScheduler: WorkScheduler
    lateinit var searchFilterCoordinator: MainSearchFilterCoordinator

    private lateinit var permissionManager: PermissionManager

    private lateinit var menuController: MainMenuController
    private lateinit var startupCoordinator: MainStartupCoordinator
    private lateinit var listInteractionController: MemberListInteractionController
    private lateinit var activityResultCoordinator: ActivityResultCoordinator

    private lateinit var backPressHandler: BackPressHandler

    private lateinit var pastoralBadgeController: PastoralReminderBadgeController
    private lateinit var swipeGestureController: MainSwipeGestureController
    private var workInfoObserver: Observer<WorkInfo?> = Observer { }
    private var searchList: ArrayList<SearchCheckBox> = arrayListOf()

    private var savedListScroll: MemberListScrollHelper.ScrollState? = null
    private var originalRecordStatusBeforeFilter: String = "0"
    val mainViewModel: MainViewModel by viewModels(
        factoryProducer = { SavedStateViewModelFactory(application, this, intent?.extras) }
    )
    private var currentWorkInfoLiveData: LiveData<WorkInfo?>? = null
    private var currentSortOrder: String = ""
    private var pendingBirthdayOffset: Int? = null
    private var isAppInitialized = false

    private var hasCompletedInitialLoad = false
    private var initialLoadStarted = false
    private var initialLoadComplete = false
    private lateinit var initialCongregations: Set<String>
    private var initialLoadDone = false


    companion object {
        private const val TAG = "Winkerk_MainActivity"
        const val CHANNEL_ID = "winkerkReaderServiceChannel"
        const val SEARCH_CHECK_BOX = "SEARCH_CHECK_BOX"
        const val FILTER_CHECK_BOX = "FILTER_CHECK_BOX"
    }

    private fun handleResultCancelled() {
        if (::searchFilterCoordinator.isInitialized) {
            searchFilterCoordinator.handleResultCancelled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ---------- 1. Basic (ViewModel‑independent) setup ----------
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

        // ---------- 2. Create ViewModel and all controllers early ----------
        // ✅ Create SavedStateHandle for the ViewModel
        val savedStateHandle = SavedStateHandle()

        viewModel = ViewModelProvider(
            this,
            MemberViewModel.MemberViewModelFactory(
                application,
                savedStateHandle,  // ✅ Now savedStateHandle is defined
                initialCongregations
            )
        ).get(MemberViewModel::class.java)

        // Adapter and RecyclerView
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
            // ✅ Remove or set to false if items can change size
            setHasFixedSize(false)
            itemAnimator = null
        }
        //memberListAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        memberListAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.ALLOW
        // Controllers that depend on ViewModel
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
                // Allow swipe controller to handle horizontal swipes
                if (e.action == MotionEvent.ACTION_MOVE) {
                    // Check if it's a horizontal swipe
                    // If yes, return true to intercept
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                // Pass to swipe controller
                swipeGestureController.handleTouchEvent(e)
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
        // ActivityResultCoordinator must be created before onStart
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

        // ---------- 3. Setup filter chips ----------
        setupFilterChips()

        // ---------- 4. Authentication guard ----------
        appAuthGuard.guardIfNeeded(
            onAuthenticated = {
                loadDataAndFinalize(savedInstanceState)
            }
        )
    }

    /**
     * Called after every onResume, but only when the app is authenticated
     * (or when biometric lock is disabled). This is the place for all
     * resume‑time housekeeping.
     */
    override fun onResumeAfterAuth() {
        // This logic was previously inside the checkOnResume callback.
        if (isAppInitialized) {
            startupCoordinator.runOnResume()
            pastoralBadgeController.refresh()
            updateFilterSummary()

            // Check if we need to scroll to birthday
            val currentSort = settingsManager.defLayout
            if (currentSort == "VERJAAR" || currentSort == "VERJAARSDAG") {
                scrollToCurrentBirthday()
            }

            // Load initial data if not already done (but this may be redundant,
            // keep it to be safe)
            if (!initialLoadDone) {
                initialLoadDone = true
                loadInitialData()
            }

            syncSortOrderWithSettings()
            binding.lidmaatList.post { restoreListScrollIfNeeded() }
        }
    }
    /**
     * Called only after successful authentication.
     * Loads data, sets up remaining controllers, and marks the app as initialized.
     */
    private fun loadDataAndFinalize(savedInstanceState: Bundle?) {
        // ---------- IMPORTANT: Set up data observers BEFORE loading data ----------
        // ✅ IMPORTANT: Setup observers BEFORE any data load
        setupObservers()
        setupViewModelObservers()


        // ✅ Set initialLoadDone flag to false (will be set true in loadInitialData)
        initialLoadDone = false
        initialLoadStarted = false

        pastoralBadgeController = PastoralReminderBadgeController(
            activity = this,
            pastoralDb = PastoralDatabase.getInstance(this),
            memberViewModel = viewModel,
            mainViewModel = mainViewModel
        )
        pastoralBadgeController.setup()
        // ---------- Startup coordinator (loads data, sets up permissions, etc.) ----------
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

                override fun setupViewModel() {
                    // Already set up; do nothing
                }

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

//                override fun isNotificationAccessEnabled(): Boolean {
//                    return permissionManager.isNotificationListenerEnabled()
//                }

                override fun openNotificationSettings() {
                    navigationController.navigateToNotificationListenerSettings()
                }

                override fun showToast(message: String) {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
        // This will call loadInitialData() which triggers the first data load.
        startupCoordinator.runOnCreate()
        // Force a refresh to update the count
        //viewModel.refresh()
        // ---------- Back‑press handler ----------
        backPressHandler = BackPressHandler(
            activity = this,
            mainViewModel = mainViewModel,
            onCancelFilter = { cancelFilter() },
            onFinish = { finish() }
        )
        backPressHandler.register()

        // ---------- Check for newer backup ----------
        checkForNewerBackup()

        // ---------- Restore instance state (if any) ----------
        savedInstanceState?.let { restoreInstanceState(it) }

        // ---------- Sync sort, scroll handling (these don't affect initial load) ----------
        syncSortOrderWithSettings()
        setupBirthdayScrollHandling()
        setupScrollRestorationObserver()

        // Force menu to be recreated now that the app is ready
        invalidateOptionsMenu()

        isAppInitialized = true
    }

    /**
     * Sets up all the data observers that bind the ViewModel to the UI.
     * This must be called after viewModel and memberListAdapter are created.
     */
    // MainActivity.kt - setupObservers()
    private fun setupObservers() {
        // LiveData observers (unchanged)
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

        // ✅ Observe total count
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totalCount.collect { count ->
                    binding.totalCount.text = "($count)"
                }
            }
        }

        // ✅ Paging data flow - use collectLatest to handle rapid emissions
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (BuildConfig.DEBUG) Log.d(TAG, "📊 Starting paging data collection")

                viewModel.pagingDataFlowWithRefresh
                    .catch { e ->
                        if (BuildConfig.DEBUG) Log.e(TAG, "Paging flow error", e)
                    }
                    .collectLatest { pagingData ->
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                "📊 Received paging data, adapter itemCount before: ${memberListAdapter.itemCount}"
                            )
                        }

                        // ✅ Ensure adapter is attached
                        if (binding.lidmaatList.adapter == null) {
                            if (BuildConfig.DEBUG) Log.d(TAG, "📊 Adapter is null, reattaching")
                            binding.lidmaatList.adapter = memberListAdapter
                        }

                        // ✅ Submit data - collectLatest ensures previous submissions are cancelled
                        memberListAdapter.submitData(lifecycle, pagingData)

                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                "📊 Submitted paging data, adapter itemCount after: ${memberListAdapter.itemCount}"
                            )
                        }
                    }
            }
        }


        // Load state (progress bar)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                memberListAdapter.loadStateFlow.collect { loadStates ->
                    val isLoading = loadStates.refresh is LoadState.Loading
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "📊 Load state: refresh=${loadStates.refresh}, append=${loadStates.append}, itemCount=${memberListAdapter.itemCount}"
                        )
                    }
                    binding.indeterminateBar.visibility = if (isLoading) View.VISIBLE else View.GONE

                    if (loadStates.refresh is LoadState.NotLoading && memberListAdapter.itemCount > 0) {
                        if (BuildConfig.DEBUG) Log.d(
                            TAG,
                            "📊 Data loaded successfully, count=${memberListAdapter.itemCount}"
                        )
                    }

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

    private fun setupViewModelObservers() {
        lifecycleScope.launch {
            ContactRepository.contactsUpdateFlow.collect {
                memberListAdapter.rebindVisibleItems(binding.lidmaatList)
            }
        }
    }


    private fun loadInitialData() {
        // ✅ Prevent multiple loads
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

        // Sync sort order properly now that database is ready
        val defLayout = settingsManager.defLayout
        updateSortOrder(defLayout, forceRefreshIfUnchanged = true)

        // ✅ If default layout is birthday, scroll to current birthday
        if (defLayout == "VERJAAR" || defLayout == "VERJAARSDAG") {
            // Wait a moment for data to load, then scroll
            binding.lidmaatList.postDelayed({
                scrollToCurrentBirthday()
            }, 500)
        }
        initialLoadComplete = true
    }

    // In MainActivity.kt - update observeDataset

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

        // ✅ FIXED: use memberListAdapter and viewModel.sortOrder
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

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "observeDataset: sort=${viewModel.sortOrder}, hasFilter=$hasFilter, hasSearch=$hasSearch, isBirthdaySort=$isBirthdaySort"
            )
        }
    }


    private fun initializeData(savedInstanceState: Bundle?) {
        val deviceId = DeviceIdManager.getDeviceId(this)

        setupVersionInfo()
        initializeSearchAndFilterLists()
        viewModel.setSearchList(searchList)
        savedInstanceState?.let { restoreInstanceState(it) }
        if (settingsManager.defLayout.isEmpty()) {
            settingsManager.defLayout = "GESINNE"
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "initializeData: defLayout = ${settingsManager.defLayout}")
        }
        // Do not set currentSortOrder, viewModel.sortOrder, or layout UI values here.
        // Doing so would cause updateSortOrder to think the state is already synced and return early,
        // leaving the database query, view model eventType, and adapter layout out of sync on startup.
        viewModel.soekList = false
    }


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

    private fun initializeSearchAndFilterLists() {
        val prefsManager = SearchCheckBoxPreferences(this)
        searchList = prefsManager.getSearchCheckBoxList()
        if (searchList.isEmpty()) {
            searchList = createDefaultSearchList()
            prefsManager.saveSearchCheckBoxList(searchList)
        }
    }

    private fun restoreInstanceState(savedInstanceState: Bundle) {
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

    private fun setupEventHandlers() {
        setupSearchCloseHandler()
        binding.sortorder.setOnClickListener {}
    }

    private fun setupSearchCloseHandler() {
        binding.mainSearchTextClose.setOnClickListener {
            searchFilterCoordinator.resetAllFiltersAndSearch()
        }
    }

    private fun findSearchView(): SearchView? {
        return if (::menuController.isInitialized) menuController.findSearchView() else null
    }

    fun applyFilterList(filterList: ArrayList<FilterBox>) {
        searchFilterCoordinator.filterList = filterList
    }

    private fun setupPermissions() {
        checkOverlayPermission()
        createNotificationChannel()
        PastoralNotificationHelper.ensureChannel(this)
    }

    private fun startMonitoringServiceIfEnabled() {
        if (settingsManager.autoStartEnabled && !CallMonitoringService.isServiceRunning(this)) {
            try {
                val serviceIntent = Intent(this, CallMonitoringService::class.java)
                startForegroundService(serviceIntent)
                if (BuildConfig.DEBUG) Log.d(TAG, "Call monitoring service started successfully")
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

    private fun createNotificationChannel() {
        val serviceChannel =
            NotificationChannel(CHANNEL_ID, "Oproep", NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(serviceChannel)
    }

    private fun ensureServicesAreRunning() {
        if (settingsManager.autoStartEnabled && !CallMonitoringService.isServiceRunning(this)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "CallMonitoring service was killed, restarting…")
            startMonitoringServiceIfEnabled()
        }
    }

    private fun syncSortOrderWithSettings() {
        if (!hasCompletedInitialLoad) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "syncSortOrderWithSettings: skipping, initial load not complete yet"
            )
            return
        }
        val currentDefLayout = settingsManager.defLayout
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "syncSortOrderWithSettings: defLayout = $currentDefLayout"
        )
        updateSortOrder(currentDefLayout, forceRefreshIfUnchanged = true)
    }

    // ------------------------------------------------------------
    // Filter chips setup
    // ------------------------------------------------------------

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

        // ✅ Only call this ONCE, and it's already handled by the ViewModel init
        // The ViewModel is already initialized with initialCongregations
        if (BuildConfig.DEBUG) Log.d(TAG, "🔍 setupFilterChips: congregations = $congregations")
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "🔍 setupFilterChips: initialCongregations = $initialCongregations"
        )
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "🔍 setupFilterChips: viewModel._congregationFilter = ${viewModel.getCurrentCongregations()}"
        )
        binding.indeterminateBar.post {
            binding.indeterminateBar.visibility = View.GONE
        }
    }

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

    private fun applyCongregationFilter() {
        val selected = getSelectedCongregations()
        viewModel.setCongregationFilter(selected)
        //memberListAdapter.forceRefresh()
        viewModel.refresh()
    }

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

    // ------------------------------------------------------------
    // Sort order management
    // ------------------------------------------------------------


    // MainActivity.kt
    // MainActivity.kt
    // MainActivity.kt
    fun updateSortOrder(newSort: String, forceRefreshIfUnchanged: Boolean = true) {
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

    private fun scrollToCurrentBirthday() {
        if (BuildConfig.DEBUG) Log.d(TAG, "scrollToCurrentBirthday called")

        // First, compute the offset
        lifecycleScope.launch {
            try {
                val offset = viewModel.getBirthdayOffset(viewModel.sortOrder)
                if (BuildConfig.DEBUG) Log.d(TAG, "Birthday offset = $offset")

                // Wait for the data to load, then scroll
                memberListAdapter.loadStateFlow.collect { loadStates ->
                    if (loadStates.refresh is LoadState.NotLoading) {
                        val itemCount = memberListAdapter.itemCount
                        if (BuildConfig.DEBUG) Log.d(
                            TAG,
                            "Data loaded, itemCount=$itemCount, offset=$offset"
                        )

                        if (itemCount > 0 && offset >= 0) {
                            val scrollPos = if (offset < itemCount) offset else itemCount - 1
                            if (BuildConfig.DEBUG) Log.d(TAG, "Scrolling to position $scrollPos")

                            binding.lidmaatList.post {
                                (binding.lidmaatList.layoutManager as? LinearLayoutManager)
                                    ?.scrollToPositionWithOffset(scrollPos, 0)
                            }
                        }
                        // Stop collecting after we've scrolled
                        return@collect
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to scroll to birthday", e)
            }
        }
    }
    // ------------------------------------------------------------
    // Lifecycle methods
    // ------------------------------------------------------------

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

        super.onDestroy()
    }

    // ------------------------------------------------------------
    // Options menu
    // ------------------------------------------------------------

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

    // ------------------------------------------------------------
    // Touch events & gestures
    // ------------------------------------------------------------

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
            if (chipView != null && isTouchInsideView(event, chipView)) {
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

    // ------------------------------------------------------------
    // Filter summary and clearing
    // ------------------------------------------------------------

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

    private fun clearFilter() {
        if (BuildConfig.DEBUG) Log.d(TAG, "clearFilter called")

        val restoreSort = if (searchFilterCoordinator.originalLayoutBeforeFilter.isNotEmpty()) {
            searchFilterCoordinator.originalLayoutBeforeFilter
        } else {
            "VAN"
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Restoring sort order to: $restoreSort")

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

        if (BuildConfig.DEBUG) Log.d(TAG, "clearFilter completed, sort order: $restoreSort")
    }

    // ------------------------------------------------------------
    // Birthday and scroll handling
    // ------------------------------------------------------------

    fun recomputeBirthdayOffset() {
        val currentSort = settingsManager.defLayout
        if (currentSort == "VERJAAR" || currentSort == "VERJAARSDAG") {
            lifecycleScope.launch {
                pendingBirthdayOffset = viewModel.getBirthdayOffset(currentSort)
            }
        }
    }

    private fun setupBirthdayScrollHandling() {
        // This is now handled directly in updateSortOrder()
        // Keep this method but make it minimal
        if (BuildConfig.DEBUG) Log.d(TAG, "setupBirthdayScrollHandling initialized")
    }

    private var scrollRestored = false

    private fun restoreListScrollIfNeeded() {
        if (pendingBirthdayOffset != null) return
        val state = savedListScroll ?: return
        if (memberListAdapter.itemCount == 0) return
        MemberListScrollHelper.restoreScrollState(binding.lidmaatList, state, memberListAdapter)
        savedListScroll = null
        scrollRestored = true
    }

    private fun setupScrollRestorationObserver() {
        lifecycleScope.launch {
            memberListAdapter.loadStateFlow.collect { loadStates ->
                if (loadStates.refresh is LoadState.NotLoading && savedListScroll != null) {
                    restoreListScrollIfNeeded()
                }
            }
        }
    }

    // ------------------------------------------------------------
    // Filter & cancellation helpers
    // ------------------------------------------------------------

    fun setOriginalRecordStatusBeforeFilter(status: String) {
        originalRecordStatusBeforeFilter = status
    }

    fun cancelFilter() {
        if (BuildConfig.DEBUG) Log.d(TAG, "cancelFilter called")
        recomputeBirthdayOffset()
        val restoreSort = if (searchFilterCoordinator.originalLayoutBeforeFilter.isNotEmpty()) {
            searchFilterCoordinator.originalLayoutBeforeFilter
        } else {
            "VAN"
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "cancelFilter: restoreSort = $restoreSort")

        clearAppliedFilterList()
        viewModel.soekList = false

        updateSortOrder(restoreSort)
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "cancelFilter: after updateSortOrder, viewModel.sortOrder = ${viewModel.sortOrder}"
        )
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

        if (BuildConfig.DEBUG) Log.d(TAG, "cancelFilter finished")
    }

    private fun resetChipSelection() {
        val congGroup = binding.congregationChipGroup
        for (i in 0 until congGroup.childCount) {
            (congGroup.getChildAt(i) as? Chip)?.isChecked = false
        }
        applyCongregationFilter()
    }

    fun setFilterRestoreState(savedSortOrder: String) {
        mainViewModel.setSavedSortOrderBeforeFilter(savedSortOrder)
    }

    fun clearFilterRestoreState() {
        mainViewModel.setSavedSortOrderBeforeFilter(null)
    }

    fun clearAppliedFilterList() {
        searchFilterCoordinator.filterList = null
    }

    // ------------------------------------------------------------
    // Backup helpers
    // ------------------------------------------------------------

    private fun checkForNewerBackup() {
        lifecycleScope.launch(Dispatchers.IO) {
            val candidate = PastoralDatabaseBackup.findBackupFile(this@MainActivity)
                ?: findDownloadsBackup()
                ?: return@launch

            val liveModified = getDatabasePath(winkerkEntry.PASTORAL_DB).lastModified()
            val backupModified = candidate.lastModified()
            if (backupModified <= liveModified) {
                if (candidate.absolutePath.startsWith(cacheDir.absolutePath)) {
                    candidate.delete()
                }
                return@launch
            }

            val version = PastoralDatabaseBackup.readSchemaVersion(candidate)
            if (version < 1 || version > PastoralDatabaseBackup.CURRENT_PASTORAL_SCHEMA_VERSION) {
                candidate.delete()
                return@launch
            }

            withContext(Dispatchers.Main) {
                val dateStr = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
                    .format(Date(backupModified))
                Snackbar.make(
                    binding.root,
                    "Rugsteun van $dateStr gevind. Wil jy herstel?",
                    Snackbar.LENGTH_LONG
                ).setAction("Herstel") {
                    val extras = Bundle().apply {
                        putBoolean(LaaiDatabasisActivity.EXTRA_PROMPT_RESTORE, true)
                    }
                    navigationController.navigateToLaaiDatabasis(extras)
//                    startActivity(
//                        Intent(this@MainActivity, LaaiDatabasisActivity::class.java)
//                            .putExtra(LaaiDatabasisActivity.EXTRA_PROMPT_RESTORE, true)
//                    )
                }.show()
                if (candidate.absolutePath.startsWith(cacheDir.absolutePath)) {
                    candidate.delete()
                }
            }
        }
    }

    private fun findDownloadsBackup(): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.DATE_MODIFIED,
                MediaStore.Downloads.RELATIVE_PATH
            )
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ? AND " +
                    "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("wkr_pastoral_%.db", "%WinkerkReader%")
            val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

            var cursor: Cursor? = null
            try {
                cursor = contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )
                if (cursor != null && cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(MediaStore.Downloads._ID)
                    val id = cursor.getLong(idIdx)
                    val uri =
                        ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)

                    val tempFile = File(cacheDir, "temp_backup_check.db")
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    return tempFile
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to query MediaStore for backups", e)
            } finally {
                cursor?.close()
            }
            null
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "WinkerkReader"
            )
            dir.listFiles { f -> f.name.startsWith("wkr_pastoral_") && f.name.endsWith(".db") }
                ?.maxByOrNull { it.lastModified() }
        }
    }

}