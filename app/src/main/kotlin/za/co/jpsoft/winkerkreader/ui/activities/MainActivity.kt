package za.co.jpsoft.winkerkreader.ui.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding
import za.co.jpsoft.winkerkreader.services.CallMonitoringService
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.ui.viewmodels.MainViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.AppAuthGuard
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
import java.util.concurrent.Executors
import androidx.core.view.isVisible
import androidx.paging.LoadState
import za.co.jpsoft.winkerkreader.data.repositories.ContactRepository
import za.co.jpsoft.winkerkreader.ui.helpers.MemberListScrollHelper
import za.co.jpsoft.winkerkreader.ui.controllers.ActivityResultCoordinator
import za.co.jpsoft.winkerkreader.ui.controllers.ChurchHeaderController
import za.co.jpsoft.winkerkreader.ui.controllers.MainMenuController
import za.co.jpsoft.winkerkreader.ui.controllers.MainSearchFilterCoordinator
import za.co.jpsoft.winkerkreader.ui.controllers.MainStartupCoordinator
import za.co.jpsoft.winkerkreader.ui.controllers.MainSwipeGestureController
import za.co.jpsoft.winkerkreader.ui.controllers.MemberListInteractionController
import za.co.jpsoft.winkerkreader.ui.controllers.PastoralReminderBadgeController
import za.co.jpsoft.winkerkreader.ui.controllers.StartupActions
import za.co.jpsoft.winkerkreader.ui.helpers.BirthdayScrollHelper
import java.time.LocalDate


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var memberListAdapter: MemberListAdapter

    private lateinit var navigationController: MainNavigationController
    private lateinit var viewModel: MemberViewModel
    private lateinit var settingsManager: SettingsManager
    private lateinit var gestureDetector: GestureDetector
    private lateinit var backgroundExecutor: java.util.concurrent.ExecutorService
    private lateinit var workScheduler: WorkScheduler
    lateinit var searchFilterCoordinator: MainSearchFilterCoordinator

    private lateinit var permissionManager: PermissionManager
    //private lateinit var permissionDialogManager: PermissionDialogManager
    private lateinit var menuController: MainMenuController
    private lateinit var startupCoordinator: MainStartupCoordinator
    private lateinit var listInteractionController: MemberListInteractionController
    private lateinit var activityResultCoordinator: ActivityResultCoordinator
    //private lateinit var mainDataLoader: MainDataLoader
    private lateinit var backPressHandler: BackPressHandler
    private lateinit var authGuard: AppAuthGuard
    private lateinit var pastoralBadgeController: PastoralReminderBadgeController
    private lateinit var churchHeaderController: ChurchHeaderController
    private lateinit var swipeGestureController: MainSwipeGestureController
    private var workInfoObserver: Observer<WorkInfo?> = Observer { }
    private var searchList: ArrayList<SearchCheckBox> = arrayListOf()
    private var bedieningBadgeCount = 0
    private var savedListScroll: MemberListScrollHelper.ScrollState? = null
    private var originalRecordStatusBeforeFilter: String = "0"
    val mainViewModel: MainViewModel by viewModels(
        factoryProducer = { SavedStateViewModelFactory(application, this, intent?.extras) }
    )
    private var currentWorkInfoLiveData: LiveData<WorkInfo?>? = null
    private var currentSortOrder: String = ""
    private var pendingBirthdayOffset: Int? = null

    companion object {
        private const val TAG = "Winkerk_MainActivity"
        const val CHANNEL_ID = "winkerkReaderServiceChannel"
        const val SEARCH_CHECK_BOX = "SEARCH_CHECK_BOX"
        const val FILTER_CHECK_BOX = "FILTER_CHECK_BOX"
    }

    private fun handleResultCancelled() {
        searchFilterCoordinator.handleResultCancelled()
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager.getInstance(this)
        //val prefs = getSharedPreferences("backup_prefs", MODE_PRIVATE)
        val dailyEnabled = settingsManager.dailyBackupEnabled // or use a dedicated prefs
        val exportToDownloads = settingsManager.backupExportToDownloads
        if (dailyEnabled) {
            PastoralBackupWorker.schedule(this, exportToDownloads)
        } else {
            PastoralBackupWorker.cancel(this)
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        permissionManager = PermissionManager(this)
        initializeViews()

        churchHeaderController = ChurchHeaderController(
            activity = this,
            binding = binding,
            settingsManager = settingsManager,
            database = WinkerkDatabase.getInstance(this)
        )
        swipeGestureController = MainSwipeGestureController(
            activity = this,
            onSwipeLeft = {
                // Same logic as original onLeftSwipe()
                when (viewModel.sortOrder) {
                    "HUWELIK" -> updateSortOrder("VAN")
                    "VAN"     -> updateSortOrder("GESINNE")
                    "GESINNE" -> updateSortOrder("WYK")
                    "WYK"     -> updateSortOrder("OUDERDOM")
                    "OUDERDOM"-> updateSortOrder("ADRES")
                    "ADRES"   -> updateSortOrder("VERJAAR")
                    "VERJAAR" -> updateSortOrder("HUWELIK")
                    else      -> updateSortOrder("VAN")
                }
                viewModel.refresh()
            },
            onSwipeRight = {
                // Same logic as original onRightSwipe()
                when (viewModel.sortOrder) {
                    "HUWELIK" -> updateSortOrder("VERJAAR")
                    "VERJAAR" -> updateSortOrder("ADRES")
                    "ADRES"   -> updateSortOrder("OUDERDOM")
                    "OUDERDOM"-> updateSortOrder("WYK")
                    "WYK"     -> updateSortOrder("GESINNE")
                    "GESINNE" -> updateSortOrder("VAN")
                    "VAN"     -> updateSortOrder("HUWELIK")
                    else      -> updateSortOrder("VAN")
                }
                viewModel.refresh()
            }
        )
        //permissionDialogManager = PermissionDialogManager(this, permissionManager)
        backgroundExecutor = Executors.newSingleThreadExecutor()
        gestureDetector = GestureDetector(this, SwipeGestureDetector())

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

        workScheduler = WorkScheduler(this, settingsManager)
        workScheduler.scheduleAll()

        navigationController = MainNavigationController(this)

        startupCoordinator = MainStartupCoordinator(
            context = this,
            lifecycleScope = lifecycleScope,
            settingsManager = settingsManager,
            permissionManager = permissionManager,
            binding = binding,
            actions = object : StartupActions {
                override fun checkAndRequestPermissions() {
                    // Use the new rationale-enabled methods for critical permission groups
                    permissionManager.requestPhonePermissions(this@MainActivity)
                    permissionManager.requestContactsPermissions(this@MainActivity)
                    permissionManager.requestSmsPermissions(this@MainActivity)
                    permissionManager.requestCalendarPermissions(this@MainActivity)

                    // Notification permission is optional but nice to have on startup (Android 13+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionManager.requestNotificationPermissions(this@MainActivity)
                    }
                }

                override fun startMonitoringServiceIfEnabled() {
                    this@MainActivity.startMonitoringServiceIfEnabled()
                }

                override fun setupViewModel() {
                    this@MainActivity.setupViewModel()
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

                override fun isNotificationAccessEnabled(): Boolean {
                    return permissionManager.isNotificationListenerEnabled()
                }

                override fun openNotificationSettings() {
                    startActivity(permissionManager.getNotificationListenerIntent())
                }

                override fun showToast(message: String) {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
        authGuard = AppAuthGuard(this, settingsManager)
        authGuard.guardIfNeeded(
            onAuthenticated = {
                startupCoordinator.runOnCreate()
            }


        )

        backPressHandler = BackPressHandler(
            activity = this,
            mainViewModel = mainViewModel,
            onCancelFilter = { cancelFilter() },
            onFinish = { finish() }
        )
        backPressHandler.register()

        ViewCompat.setOnApplyWindowInsetsListener(binding.lidmaatList) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
        PastoralDatabase.getInstance(this)
        pastoralBadgeController = PastoralReminderBadgeController(
            activity = this,
            pastoralDb = PastoralDatabase.getInstance(this),
            memberViewModel = viewModel,   // MemberViewModel
            mainViewModel = mainViewModel
        )
        pastoralBadgeController.setup()

        checkForNewerBackup()
        setupViewModelObservers()


    }
    private fun setupViewModelObservers() {
        lifecycleScope.launch {
            ContactRepository.contactsUpdateFlow.collect {
                // Contacts updated – refresh visible rows
                memberListAdapter.rebindVisibleItems(binding.lidmaatList)
            }
        }
//        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                mainViewModel.pendingReminderCount.collect { count ->
//                    bedieningBadgeCount = count
//                    invalidateOptionsMenu()
//                }
//            }
//        }
    }


    // ------------------------------------------------------------
    // Filter & cancellation
    // ------------------------------------------------------------
    fun setOriginalRecordStatusBeforeFilter(status: String) {
        originalRecordStatusBeforeFilter = status
    }

    // MainActivity.kt
    fun cancelFilter() {
        if (BuildConfig.DEBUG) Log.d(TAG, "cancelFilter called")
        recomputeBirthdayOffset()
        // Get the original sort before any filter was applied
        val restoreSort = if (searchFilterCoordinator.originalLayoutBeforeFilter.isNotEmpty()) {
            searchFilterCoordinator.originalLayoutBeforeFilter
        } else {
            "VAN"
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "cancelFilter: restoreSort = $restoreSort")

        // Clear filter state
        clearAppliedFilterList()
        viewModel.soekList = false

        // Restore the sort order
        updateSortOrder(restoreSort)
        if (BuildConfig.DEBUG) Log.d(TAG, "cancelFilter: after updateSortOrder, viewModel.sortOrder = ${viewModel.sortOrder}")
        binding.sortorder.text = restoreSort
        binding.sortorder.tag = restoreSort
        // Reset the coordinator's original sort tracker
        searchFilterCoordinator.originalLayoutBeforeFilter = ""
        mainViewModel.setSavedSortOrderBeforeFilter(null)  // optional, but keep for safety

        // Show main layout
        binding.mainMain.visibility = View.VISIBLE
        binding.mainFilter.visibility = View.GONE
        mainViewModel.setFilterVisible(false)

        // Clear summary bar
        binding.searchText.text = ""
        binding.searchItemBlock.visibility = View.GONE
        viewModel.clearFilterSummary()

        // Hide keyboard
        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "cancelFilter finished")
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
    // Lifecycle
    // ------------------------------------------------------------

    override fun onStart() {
        super.onStart()
        if (settingsManager.callLogEnabled) {
            BatteryOptimizationHelper.showBatteryOptimizationDialog(this)
        }
    }

    override fun onPause() {
        savedListScroll = MemberListScrollHelper.saveScrollState(binding.lidmaatList, memberListAdapter)
        super.onPause()
    }

    override fun onResume() {
        if (BuildConfig.DEBUG) Log.d(TAG, "onResume called")
        if (::authGuard.isInitialized) {
            authGuard.checkOnResume(
                onAuthenticated = {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Auth callback invoked")
                    startupCoordinator.runOnResume()

                    pastoralBadgeController.refresh()

                    churchHeaderController.loadAndApply()   // was loadChurchInfoAndUpdateHeader()

                }
            )
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "authGuard not initialized, running directly")
            startupCoordinator.runOnResume()

            pastoralBadgeController.refresh()

            churchHeaderController.loadAndApply()//loadChurchInfoAndUpdateHeader()

        }
        syncSortOrderWithSettings()
        if ((settingsManager.defLayout == "VERJAAR" || settingsManager.defLayout == "VERJAARSDAG") && pendingBirthdayOffset == null) {
            recomputeBirthdayOffset()
        }
        binding.lidmaatList.post { restoreListScrollIfNeeded() }
        super.onResume()
    }

    override fun onDestroy() {
        currentWorkInfoLiveData?.removeObserver(workInfoObserver)
        if (::menuController.isInitialized) {
            menuController.clearCallbacks()
        }
        WhatsAppContactLoader.reset()
        if (::backgroundExecutor.isInitialized) {
            backgroundExecutor.shutdown()
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------
    // Initialisation helpers
    // ------------------------------------------------------------

    private fun initializeViews() {
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
        // ✅ Preserve scroll position when returning from detail
        memberListAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        binding.lidmaatList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = memberListAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    private fun setupViewModel() {

        viewModel = ViewModelProvider(
            this,
            SavedStateViewModelFactory(application, this, intent?.extras)
        ).get(MemberViewModel::class.java)

        viewModel.initRepository(this)


        searchFilterCoordinator = MainSearchFilterCoordinator(
            tag = TAG,
            context = this,
            viewModel = viewModel,
            settingsManager = settingsManager,
            binding = binding,
            memberListAdapter = memberListAdapter,
            findSearchView = ::findSearchView,
            hideFilterPanel = {
                if (binding.mainFilter.isVisible) {
                    binding.mainFilter.visibility = View.GONE
                }
            }
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

        viewModel.getTextLiveData().observe(this) { searchText ->
            binding.searchText.text = searchText
            binding.searchItemBlock.visibility = if (searchText.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.getVerjaarFLag().observe(this) { showBirthday ->
            if (BuildConfig.DEBUG) Log.d(TAG, "verjaarFlag: $showBirthday")
        }

        viewModel.memberGuidsWithPendingReminders.observe(this) { guids ->
            if (BuildConfig.DEBUG) Log.d(TAG, "🔄 Observer received ${guids.size} GUIDs: $guids")
            memberListAdapter.updatePendingReminderGuids(guids)
            //memberListAdapter.rebindVisibleItems(binding.lidmaatList)
            restoreListScrollIfNeeded()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pagingDataFlowWithRefresh
                    .catch { e -> Log.e(TAG, "Paging flow error", e) }
                    .collect { pagingData ->
                        memberListAdapter.submitData(lifecycle, pagingData)
                    }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totalCount.collect { count ->
                    binding.mainCount.text = "[$count]"
                }
            }
        }
        lifecycleScope.launch {
            memberListAdapter.loadStateFlow.collect { loadStates ->
                val isLoading = loadStates.refresh is LoadState.Loading
                binding.indeterminateBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                if (loadStates.refresh is LoadState.Error) {
                    // show error message
                }
            }
        }
        setupBirthdayScrollHandling()
        setupScrollRestorationObserver()
    }


    private fun setupBirthdayScrollHandling() {
        lifecycleScope.launch {
            // Combine load states with total count so we know when all items are loaded
            memberListAdapter.loadStateFlow.collect { loadStates ->
                val offset = pendingBirthdayOffset ?: return@collect
                // Only proceed when both refresh and append are not loading
                if (loadStates.refresh is LoadState.NotLoading && loadStates.append is LoadState.NotLoading) {
                    val itemCount = memberListAdapter.itemCount
                    val totalCount = viewModel.totalCount.value
                    // We can scroll if we have enough items, or if we've loaded everything
                    if (itemCount > offset || itemCount == totalCount) {
                        val target = if (offset < itemCount) offset else itemCount - 1
                        binding.lidmaatList.post {
                            (binding.lidmaatList.layoutManager as? LinearLayoutManager)
                                ?.scrollToPositionWithOffset(target, 0)
                        }
                        pendingBirthdayOffset = null // clear after successful scroll
                    }
                }
            }
        }
    }

    private fun restoreListScrollIfNeeded() {
        if (pendingBirthdayOffset != null) return   // Prevent overriding birthday scroll
        val state = savedListScroll ?: return
        if (memberListAdapter.itemCount == 0) return
        MemberListScrollHelper.restoreScrollState(binding.lidmaatList, state, memberListAdapter)
        savedListScroll = null
        scrollRestored = true
    }

    private fun setupPermissions() {
        checkOverlayPermission()
        createNotificationChannel()
        PastoralNotificationHelper.ensureChannel(this)
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
        currentSortOrder = settingsManager.defLayout
        viewModel.sortOrder = settingsManager.defLayout
        binding.sortorder.text = settingsManager.defLayout
        binding.sortorder.tag = settingsManager.defLayout

        viewModel.soekList = false
        mainViewModel.setSortOrder(settingsManager.defLayout)
    }

    private fun setupVersionInfo() {
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            supportActionBar?.apply {
                title = "WinkerkReader"
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
                savedInstanceState.getParcelableArrayList(SEARCH_CHECK_BOX, SearchCheckBox::class.java)
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
        setupSortOrderClickHandler()
        setupChurchNameClickHandler()
    }

    private fun setupSearchCloseHandler() {
        binding.mainSearchTextClose.setOnClickListener {
            searchFilterCoordinator.resetAllFiltersAndSearch()
        }
    }

    private fun findSearchView(): SearchView? {
        return if (::menuController.isInitialized) menuController.findSearchView() else null
    }

    private fun setupSortOrderClickHandler() {
        binding.sortorder.setOnClickListener { v ->
            val background = v.background
            if (background is ColorDrawable) {
                v.background = null
                v.setBackgroundColor(Color.WHITE)
            } else {
                v.setBackgroundResource(R.color.selected_view)
            }
        }
    }

    private fun setupChurchNameClickHandler() {
        binding.mainGemeentenaam.setOnClickListener { view ->
//            if (::listInteractionController.isInitialized) {
//               // listInteractionController.showGroupFunctionMenu(view)
//            }
        }
    }

    fun applyFilterList(filterList: ArrayList<FilterBox>) {
        searchFilterCoordinator.filterList = filterList

    }

    private fun loadInitialData() {
        if (::memberListAdapter.isInitialized && memberListAdapter.itemCount > 0) {
            savedListScroll = MemberListScrollHelper.saveScrollState(binding.lidmaatList, memberListAdapter)
        }
        initializeData(null)
        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
        binding.searchItemBlock.visibility = View.GONE
        binding.mainCount.text = "[0]"
        // Die kerknaam word outomaties deur die ViewModel-observer opgedateer.
        // Verfris die lys en laai WhatsApp-kontakte.
        searchFilterCoordinator.refresh()
        WhatsAppContactLoader.loadWhatsAppContactsAtomic(this, lifecycleScope)

        churchHeaderController.loadAndApply() //loadChurchInfoAndUpdateHeader()
    }

    fun observeDataset() {
        // Update the search text display
        binding.searchItemBlock.visibility = if (viewModel.soekList && viewModel.soek.isNotEmpty()) View.VISIBLE else View.GONE
        binding.sortorder.text = viewModel.sortOrder
        binding.sortorder.tag = viewModel.sortOrder
        if (BuildConfig.DEBUG) Log.d(TAG, "observeDataset: setting sortorder text to ${viewModel.sortOrder}")
        //        searchFilterCoordinator.refresh()
    }

    // ------------------------------------------------------------
    // Options menu
    // ------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return menuController.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return MenuItemHandler(this, settingsManager, viewModel, navigationController)
            .handleMenuItem(item) || super.onOptionsItemSelected(item)
    }

    override fun onOptionsMenuClosed(menu: Menu) {
        super.onOptionsMenuClosed(menu)

    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
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
        val sortItem = menu.findItem(R.id.menu_sorteer_titel)
        sortItem?.title = getString(R.string.mainmenu_sorteer)

        val adminItem = menu.findItem(R.id.menu_andmin_titel)
        adminItem?.title = getString(R.string.mainmenu_admin)
        return super.onPrepareOptionsMenu(menu)
    }

    // ------------------------------------------------------------
    // Touch events & gestures
    // ------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return swipeGestureController.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Give the gesture detector first chance; if it consumes, return true.
        // Otherwise, let the superclass handle it.
        return if (swipeGestureController.handleTouchEvent(event)) {
            true
        } else {
            super.dispatchTouchEvent(event)
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
    // ------------------------------------------------------------
    // Swipe gesture detector
    // ------------------------------------------------------------

    private inner class SwipeGestureDetector : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_MIN_DISTANCE = 120
        private val SWIPE_MAX_OFF_PATH = 200
        private val SWIPE_THRESHOLD_VELOCITY = 200

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            try {
                if (e1 == null) return false
                val diffAbs = Math.abs(e1.y - e2.y)
                val diff = e1.x - e2.x
                if (diffAbs > SWIPE_MAX_OFF_PATH) return false
                when {
                    diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY ->
                        onLeftSwipe()
                    -diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY ->
                        onRightSwipe()
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error on gestures", e)
            }
            return false
        }
    }

    // In MainActivity.kt, replace the onLeftSwipe/onRightSwipe methods:

    private fun onLeftSwipe() {
        when (viewModel.sortOrder) {
            "HUWELIK" -> updateSortOrder("VAN")
            "VAN"     -> updateSortOrder("GESINNE")
            "GESINNE" -> updateSortOrder("WYK")
            "WYK"     -> updateSortOrder("OUDERDOM")
            "OUDERDOM"-> updateSortOrder("ADRES")
            "ADRES"   -> updateSortOrder("VERJAAR")
            "VERJAAR" -> updateSortOrder("HUWELIK")
            else      -> updateSortOrder("VAN")
        }
        // Reload data
        viewModel.refresh()//observeDataset()
    }

    private fun onRightSwipe() {
        when (viewModel.sortOrder) {
            "HUWELIK" -> updateSortOrder("VERJAAR")
            "VERJAAR" -> updateSortOrder("ADRES")
            "ADRES"   -> updateSortOrder("OUDERDOM")
            "OUDERDOM"-> updateSortOrder("WYK")
            "WYK"     -> updateSortOrder("GESINNE")
            "GESINNE" -> updateSortOrder("VAN")
            "VAN"     -> updateSortOrder("HUWELIK")
            else      -> updateSortOrder("VAN")
        }
        // Reload data
        viewModel.refresh()//observeDataset()
    }


    // ------------------------------------------------------------
    // Pending reminders helpers
    // ------------------------------------------------------------
//    private suspend fun getPendingGuids(db: PastoralDatabase): List<String> {
//        // First try the DAO
//        var guids = db.followUpReminderDao().getDistinctMemberGuidsWithPending()
//        if (BuildConfig.DEBUG) Log.d(TAG, "DAO returned ${guids.size} guids: $guids")
//        if (guids.isNotEmpty()) return guids
//
//        // Fallback: raw query
//        val dbFile = getDatabasePath("wkr_pastoral.db")
//        if (BuildConfig.DEBUG) Log.d(TAG, "Pastoral DB file exists? ${dbFile.exists()}, path: ${dbFile.absolutePath}")
//        if (!dbFile.exists()) {
//            if (BuildConfig.DEBUG) Log.e(TAG, "Pastoral DB file does not exist!")
//            return emptyList()
//        }
//
//        val sqliteDb = SQLiteDatabase.openDatabase(
//            dbFile.absolutePath,
//            null,
//            SQLiteDatabase.OPEN_READONLY
//        )
//
//        // First, check if the table exists and has rows
//        val countCursor = sqliteDb.rawQuery("SELECT COUNT(*) FROM follow_up_reminders", null)
//        countCursor.use {
//            if (it.moveToFirst()) {
//                val totalRows = it.getInt(0)
//                if (BuildConfig.DEBUG) Log.d(TAG, "Total rows in follow_up_reminders: $totalRows")
//            }
//        }
//
//        // Now query distinct memberGuid with status = 'PENDING' (case-insensitive)
//        val cursor = sqliteDb.rawQuery(
//            "SELECT DISTINCT memberGuid FROM follow_up_reminders WHERE UPPER(status) = 'PENDING' AND memberGuid IS NOT NULL AND memberGuid != ''",
//            null
//        )
//        val result = mutableListOf<String>()
//        while (cursor.moveToNext()) {
//            val guid = cursor.getString(0)
//            if (!guid.isNullOrBlank()) {
//                result.add(guid)
//                if (BuildConfig.DEBUG) Log.d(TAG, "Found pending guid: $guid")
//            }
//        }
//        cursor.close()
//        sqliteDb.close()
//        if (BuildConfig.DEBUG) Log.d(TAG, "Raw query returned ${result.size} guids: $result")
//        return result
//    }
//    private fun loadPendingReminderGuids() {
//        if (BuildConfig.DEBUG) Log.d(TAG, "loadPendingReminderGuids called")
//        lifecycleScope.launch(Dispatchers.IO) {
//            try {
//                val db = PastoralDatabase.getInstance(applicationContext)
//                val pendingReminders = db.followUpReminderDao().getAllPending()
//                if (BuildConfig.DEBUG) Log.d(TAG, "Pending reminders count: ${pendingReminders.size}")
//
//                val guids = pendingReminders.mapNotNull { reminder ->
//
//                    var guid = reminder.memberGuid?.takeIf { it.isNotBlank() }
//                    if (guid == null) {
//                        // Fallback: resolve by name from memberDisplayNameCache
//                        val name = reminder.memberDisplayNameCache
//                        if (!name.isNullOrBlank()) {
//                            guid = resolveMemberGuidByName(name)
//                            if (guid != null) {
//                                if (BuildConfig.DEBUG) Log.d(TAG, "Resolved GUID '$guid' for name '$name'")
//                            }
//                        }
//                    }
//                    guid
//                }.distinct()
//                if (BuildConfig.DEBUG) Log.d(TAG, "📌 Final pending GUIDs: $guids")
//
//                if (BuildConfig.DEBUG) Log.d(TAG, "📌 Found ${guids.size} distinct member GUIDs with pending reminders: $guids")
//                withContext(Dispatchers.Main) {
//                    viewModel.updatePendingRemindersSet(guids.toSet())
//                }
//            } catch (e: Exception) {
//                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load pending reminder guids", e)
//            }
//        }
//
//    }

    /**
     * Resolves a member GUID by matching the display name (assuming format "FirstName LastName").
     * Returns null if no match is found.
     */
//     setupReminderEventBus

        // ------------------------------------------------------------
    // Service & permissions helpers
    // ------------------------------------------------------------

    private fun startMonitoringServiceIfEnabled() {
        if (settingsManager.callMonitorEnabled && !CallMonitoringService.isServiceRunning(this)) {
            try {
                val serviceIntent = Intent(this, CallMonitoringService::class.java)
                startForegroundService(serviceIntent)
                if (BuildConfig.DEBUG) Log.d(TAG, "Call monitoring service started successfully")
            } catch (e: SecurityException) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Security exception - check permissions", e)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to start call monitoring service", e)
            }
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "Call monitoring service already running or disabled")
        }
    }

    private fun checkOverlayPermission() {
        permissionManager.requestOverlayPermissionWithRationale(this)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(CHANNEL_ID, "Oproep", NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(serviceChannel)
    }

    private fun ensureServicesAreRunning() {
        if (settingsManager.callMonitorEnabled && !CallMonitoringService.isServiceRunning(this)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "CallMonitoring service was killed, restarting…")
            startMonitoringServiceIfEnabled()
        }
    }

    private fun openNotificationSettings() {
        Toast.makeText(this, "Please enable notification access for this app", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val notificationEnabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return notificationEnabled != null && notificationEnabled.contains(packageName)
    }

    private fun syncSortOrderWithSettings() {
        val currentDefLayout = settingsManager.defLayout
        if (BuildConfig.DEBUG) Log.d(TAG, "syncSortOrderWithSettings: defLayout = $currentDefLayout")
        updateSortOrder(currentDefLayout)  // This will only refresh if the sort actually changed
    }


    // In MainActivity.onCreate, after the UI is ready
    private fun checkForNewerBackup() {
        lifecycleScope.launch(Dispatchers.IO) {
            val candidate = PastoralDatabaseBackup.findBackupFile(this@MainActivity)
                ?: findDownloadsBackup()
                ?: return@launch

            val liveModified = getDatabasePath(winkerkEntry.PASTORAL_DB).lastModified()
            val backupModified = candidate.lastModified()
            if (backupModified <= liveModified) {
                // Clean up temp file if it's from MediaStore
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
                    startActivity(Intent(this@MainActivity, LaaiDatabasisActivity::class.java)
                        .putExtra(LaaiDatabasisActivity.EXTRA_PROMPT_RESTORE, true))
                }.show()
                // Clean up temp file after showing (or keep for later)
                if (candidate.absolutePath.startsWith(cacheDir.absolutePath)) {
                    candidate.delete()
                }
            }
        }
    }

    /** Scans Downloads/WinkerkReader for any wkr_pastoral_*.db file, newest first. */
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
                    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)

                    // Copy to cache to get a File object with lastModified
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


    private fun scrollToBirthdayIfLoaded() {
        val offset = pendingBirthdayOffset ?: return
        val itemCount = memberListAdapter.itemCount
        if (itemCount > offset) {
            binding.lidmaatList.post {
                (binding.lidmaatList.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(offset, 0)
            }
            pendingBirthdayOffset = null
        }
        // If itemCount <= offset, do nothing – the observer will handle it when more pages load
    }

    /**
     * Recalculates the birthday offset and triggers a scroll if the list is already loaded.
     * Called after filter/search changes while the list is sorted by VERJAAR.
     */
    fun recomputeBirthdayOffset() {
        val currentSort = settingsManager.defLayout
        if (currentSort == "VERJAAR" || currentSort == "VERJAARSDAG") {
            lifecycleScope.launch {
                pendingBirthdayOffset = viewModel.getBirthdayOffset(currentSort)
                scrollToBirthdayIfLoaded()   // fallback if list is already loaded
            }
        }
    }
    // ------------------------------------------------------------
    // Factory for MainViewModel
    // ------------------------------------------------------------
    /**
     * Single point of truth for changing the sort order.
     * Updates SettingsManager, MemberViewModel, and MainViewModel.
     */
    fun updateSortOrder(newSort: String) {
        if (newSort == currentSortOrder) {
            // Still update adapter state (in case filters changed)
            memberListAdapter.updateState(
                listView = settingsManager.listView,
                soekList = viewModel.soekList,
                soek = viewModel.soek,
                recordStatus = viewModel.recordStatus,
                sortOrder = newSort
            )
            return
        }

        // Update the current sort
        currentSortOrder = newSort

        settingsManager.defLayout = newSort
        mainViewModel.setSortOrder(newSort)
        binding.sortorder.text = newSort
        binding.sortorder.tag = newSort

        memberListAdapter.updateState(
            listView = settingsManager.listView,
            soekList = viewModel.soekList,
            soek = viewModel.soek,
            recordStatus = viewModel.recordStatus,
            sortOrder = newSort
        )

        if (newSort == "VERJAAR" || newSort == "VERJAARSDAG") {
            lifecycleScope.launch {
                pendingBirthdayOffset = viewModel.getBirthdayOffset(newSort)
                // Now refresh the list (this will trigger the load state flow)
                viewModel.updateSortOrder(newSort)
            }
        } else {
            pendingBirthdayOffset = null
            viewModel.updateSortOrder(newSort)
        }
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
}