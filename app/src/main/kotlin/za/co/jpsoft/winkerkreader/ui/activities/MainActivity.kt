package za.co.jpsoft.winkerkreader.ui.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
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
import kotlinx.coroutines.flow.collectLatest
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
import za.co.jpsoft.winkerkreader.utils.PermissionHelper
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var memberListAdapter: MemberListAdapter

    private lateinit var navigationController: MainNavigationController
    private lateinit var viewModel: MemberViewModel
    private lateinit var settingsManager: SettingsManager
    private lateinit var gestureDetector: GestureDetector
    private lateinit var backgroundExecutor: java.util.concurrent.ExecutorService
    private lateinit var workScheduler: WorkScheduler
    private lateinit var searchFilterCoordinator: MainSearchFilterCoordinator

    private lateinit var permissionManager: PermissionManager
    //private lateinit var permissionDialogManager: PermissionDialogManager
    private lateinit var menuController: MainMenuController
    private lateinit var startupCoordinator: MainStartupCoordinator
    private lateinit var listInteractionController: MemberListInteractionController
    private lateinit var activityResultCoordinator: ActivityResultCoordinator
    //private lateinit var mainDataLoader: MainDataLoader
    private lateinit var backPressHandler: BackPressHandler
    private lateinit var authGuard: AppAuthGuard
    private var workInfoObserver: Observer<WorkInfo?> = Observer { }
    private var searchList: ArrayList<SearchCheckBox> = arrayListOf()
    private var bedieningBadgeCount = 0

    val mainViewModel: MainViewModel by viewModels(
        factoryProducer = { SavedStateViewModelFactory(application, this, intent?.extras) }
    )
    private var currentWorkInfoLiveData: LiveData<WorkInfo?>? = null



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
        val prefs = getSharedPreferences("backup_prefs", MODE_PRIVATE)
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

//        mainDataLoader = MainDataLoader(
//            context = this,
//            binding = binding,
//            settingsManager = settingsManager,
//            executor = backgroundExecutor
//        )

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
                    // Delegate to PermissionManager
                    permissionManager.requestAllPermissions(this@MainActivity)
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

//        PastoralBackupWorker.schedule(
//            context           = this,
//            exportToDownloads = prefs.getBoolean(PREF_BACKUP_TO_DOWNLOADS, false)
//        )

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
        checkForNewerBackup()
        setupViewModelObservers()
        loadPendingReminderGuids()
    }
    private fun setupViewModelObservers() {
//        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                mainViewModel.sortOrder.collect { sort ->
//                    if (BuildConfig.DEBUG) Log.d(TAG, "sortOrder collected: $sort")
//                    binding.sortorder.text = sort
//                    binding.sortorder.tag = sort
//                }
//            }
//        }
//        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                mainViewModel.churchName.collect { name ->
//                    binding.mainGemeentenaam.text = name
//                }
//            }
//        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.pendingReminderCount.collect { count ->
                    bedieningBadgeCount = count
                    invalidateOptionsMenu()
                }
            }
        }
    }
//    private fun setupViewModelObservers() {
//        lifecycleScope.launch {
//            mainViewModel.sortOrder.collect { sort ->
//                binding.sortorder.text = sort
//                binding.sortorder.tag = sort
//            }
//        }
//        lifecycleScope.launch {
//            mainViewModel.churchName.collect { name ->
//                binding.mainGemeentenaam.text = name
//            }
//        }
//        lifecycleScope.launch {
//            mainViewModel.pendingReminderCount.collect { count ->
//                bedieningBadgeCount = count
//                invalidateOptionsMenu()
//            }
//        }
//    }

    // ------------------------------------------------------------
    // Filter & cancellation
    // ------------------------------------------------------------

    fun cancelFilter() {
        val savedSort = mainViewModel.savedSortOrderBeforeFilter.value
        if (savedSort != null) {
            mainViewModel.setSortOrder(savedSort)
            mainViewModel.setSavedSortOrderBeforeFilter(null)
            viewModel.sortOrder = savedSort
            settingsManager.defLayout = savedSort
            binding.sortorder.text = savedSort
            binding.sortorder.tag = savedSort
        }
        clearAppliedFilterList()
        viewModel.soekList = false
        binding.mainFilter.visibility = View.GONE
        mainViewModel.setFilterVisible(false)
        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
        // ✅ Force reload with restored mode
        val mode = searchFilterCoordinator.resolveQueryMode(settingsManager.defLayout)
        viewModel.loadData(this, mode)
        viewModel.refresh()
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

    override fun onResume() {
        if (BuildConfig.DEBUG) Log.d(TAG, "onResume called")
        if (::authGuard.isInitialized) {
            authGuard.checkOnResume(
                onAuthenticated = {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Auth callback invoked")
                    startupCoordinator.runOnResume()
                    mainViewModel.refreshPendingReminderCount()
                    loadPendingReminderGuids()
                    loadChurchInfoAndUpdateHeader()
                }
            )
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "authGuard not initialized, running directly")
            startupCoordinator.runOnResume()
            mainViewModel.refreshPendingReminderCount()
            loadPendingReminderGuids()
            loadChurchInfoAndUpdateHeader()
        }
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
        }
    }

    private fun setupViewModel() {
        //viewModel = ViewModelProvider(this)[MemberViewModel::class.java]
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

        viewModel.getRowCount().observe(this) { count ->
            binding.mainCount.text = "[$count]"
        }
        viewModel.getTextLiveData().observe(this) { searchText ->
            binding.searchText.text = searchText
            binding.searchItemBlock.visibility = if (searchText.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.getVerjaarFLag().observe(this) { showBirthday ->
            if (BuildConfig.DEBUG) Log.d(TAG, "verjaarFlag: $showBirthday")
        }

//        viewModel.getMemberList().observe(this) { items ->
//            if (BuildConfig.DEBUG) Log.d(TAG, "Observer received ${items.size} items")
//            val isVerjaar = settingsManager.defLayout == "VERJAAR"
//            memberListAdapter.updateState(
//                listView = settingsManager.listView,
//                soekList = viewModel.soekList,
//                soek = viewModel.soek,
//                recordStatus = viewModel.recordStatus,
//                sortOrder = viewModel.sortOrder
//            )
//            memberListAdapter.submitList(items) {
//                if (isVerjaar && items.isNotEmpty()) {
//                    BirthdayScrollHelper.scrollToNextBirthday(
//                        binding.lidmaatList,
//                        items,
//                        backgroundExecutor
//                    )
//                }
//            }
//        }

        viewModel.memberGuidsWithPendingReminders.observe(this) { guids ->

            Log.d(TAG, "🔄 Observer received ${guids.size} GUIDs: $guids")
            memberListAdapter.updatePendingReminderGuids(guids)
        }
        lifecycleScope.launch {
            viewModel.pagingDataFlowWithRefresh
                .catch { e -> Log.e(TAG, "Paging flow error", e) }
                .collectLatest { pagingData ->
                    memberListAdapter.submitData(pagingData)
                }
        }
        viewModel.getRowCount().observe(this) { count ->
            binding.mainCount.text = "[$count]"
        }
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
            if (::listInteractionController.isInitialized) {
                listInteractionController.showGroupFunctionMenu(view)
            }
        }
    }

    fun applyFilterList(filterList: ArrayList<FilterBox>) {
        searchFilterCoordinator.filterList = filterList
    }

//    private fun loadInitialData() {
//        currentFocus?.let {
//            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
//            imm.hideSoftInputFromWindow(it.windowToken, 0)
//        }
//        binding.searchItemBlock.visibility = View.GONE
//        binding.mainCount.text = "[0]"
//        mainDataLoader.load {
//            searchFilterCoordinator.refresh()
//            WhatsAppContactLoader.loadWhatsAppContactsAtomic(this, lifecycleScope)
//        }
//    }
    private fun loadInitialData() {
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
        viewModel.refresh()
        WhatsAppContactLoader.loadWhatsAppContactsAtomic(this, lifecycleScope)
        loadChurchInfoAndUpdateHeader()
    }

    fun observeDataset() {
        searchFilterCoordinator.refresh()
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

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val bedieningItem = menu.findItem(R.id.action_bediening)
        if (bedieningItem != null) {
            val title = if (bedieningBadgeCount > 0) {
                getString(R.string.mainmenu_bediening_badge, bedieningBadgeCount)
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
        return if (gestureDetector.onTouchEvent(event)) true else super.onTouchEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return if (gestureDetector.onTouchEvent(event)) true else super.dispatchTouchEvent(event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(SEARCH_CHECK_BOX, searchList)
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
        observeDataset()
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
        observeDataset()
    }
//    private fun onLeftSwipe() {
//        NavigationHandler.handleLeftSwipe(this, binding.sortorder, viewModel)
//    }
//
//    private fun onRightSwipe() {
//        NavigationHandler.handleRightSwipe(this, binding.sortorder, viewModel)
//    }

    // ------------------------------------------------------------
    // Pending reminders helpers
    // ------------------------------------------------------------
    private suspend fun getPendingGuids(db: PastoralDatabase): List<String> {
        // First try the DAO
        var guids = db.followUpReminderDao().getDistinctMemberGuidsWithPending()
        if (BuildConfig.DEBUG) Log.d(TAG, "DAO returned ${guids.size} guids: $guids")
        if (guids.isNotEmpty()) return guids

        // Fallback: raw query
        val dbFile = getDatabasePath("wkr_pastoral.db")
        if (BuildConfig.DEBUG) Log.d(TAG, "Pastoral DB file exists? ${dbFile.exists()}, path: ${dbFile.absolutePath}")
        if (!dbFile.exists()) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Pastoral DB file does not exist!")
            return emptyList()
        }

        val sqliteDb = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )

        // First, check if the table exists and has rows
        val countCursor = sqliteDb.rawQuery("SELECT COUNT(*) FROM follow_up_reminders", null)
        countCursor.use {
            if (it.moveToFirst()) {
                val totalRows = it.getInt(0)
                if (BuildConfig.DEBUG) Log.d(TAG, "Total rows in follow_up_reminders: $totalRows")
            }
        }

        // Now query distinct memberGuid with status = 'PENDING' (case-insensitive)
        val cursor = sqliteDb.rawQuery(
            "SELECT DISTINCT memberGuid FROM follow_up_reminders WHERE UPPER(status) = 'PENDING' AND memberGuid IS NOT NULL AND memberGuid != ''",
            null
        )
        val result = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val guid = cursor.getString(0)
            if (!guid.isNullOrBlank()) {
                result.add(guid)
                if (BuildConfig.DEBUG) Log.d(TAG, "Found pending guid: $guid")
            }
        }
        cursor.close()
        sqliteDb.close()
        if (BuildConfig.DEBUG) Log.d(TAG, "Raw query returned ${result.size} guids: $result")
        return result
    }
    private fun loadPendingReminderGuids() {
        if (BuildConfig.DEBUG) Log.d(TAG, "loadPendingReminderGuids called")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = PastoralDatabase.getInstance(applicationContext)
                val pendingReminders = db.followUpReminderDao().getAllPending()
                if (BuildConfig.DEBUG) Log.d(TAG, "Pending reminders count: ${pendingReminders.size}")

                val guids = pendingReminders.mapNotNull { reminder ->

                    var guid = reminder.memberGuid?.takeIf { it.isNotBlank() }
                    if (guid == null) {
                        // Fallback: resolve by name from memberDisplayNameCache
                        val name = reminder.memberDisplayNameCache
                        if (!name.isNullOrBlank()) {
                            guid = resolveMemberGuidByName(name)
                            if (guid != null) {
                                if (BuildConfig.DEBUG) Log.d(TAG, "Resolved GUID '$guid' for name '$name'")
                            }
                        }
                    }
                    guid
                }.distinct()
                if (BuildConfig.DEBUG) Log.d(TAG, "📌 Final pending GUIDs: $guids")

                if (BuildConfig.DEBUG) Log.d(TAG, "📌 Found ${guids.size} distinct member GUIDs with pending reminders: $guids")
                withContext(Dispatchers.Main) {
                    viewModel.updatePendingRemindersSet(guids.toSet())
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load pending reminder guids", e)
            }
        }

    }

    /**
     * Resolves a member GUID by matching the display name (assuming format "FirstName LastName").
     * Returns null if no match is found.
     */
    private suspend fun resolveMemberGuidByName(name: String): String? = withContext(Dispatchers.IO) {
        val parts = name.split(' ')
        val firstName = parts.firstOrNull() ?: ""
        val lastName = parts.drop(1).joinToString(" ")
        if (firstName.isBlank() && lastName.isBlank()) return@withContext null

        val query = """
        SELECT MemberGUID FROM Members 
        WHERE Noemnaam LIKE ? AND Van LIKE ?
        LIMIT 1
    """.trimIndent()
        val selectionArgs = arrayOf("%$firstName%", "%$lastName%")

        try {
            val cursor = contentResolver.query(
                winkerkEntry.CONTENT_URI,
                arrayOf(winkerkEntry.LIDMATE_LIDMAATGUID),
                query,
                selectionArgs,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    return@withContext it.getString(0)
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error resolving member GUID by name", e)
        }
        null
    }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            PermissionHelper.getSystemAlertWindowPermissionIntent(this)?.let {
                activityResultCoordinator.overlayPermissionLauncher.launch(it)
            }
        }
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
    private fun loadChurchInfoAndUpdateHeader() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = WinkerkDatabase.getInstance(applicationContext).openHelper.writableDatabase
            val cursor = db.query(
                "SELECT DISTINCT Gemeente, [Gemeente epos] FROM Members GROUP BY Gemeente, [Gemeente epos]"
            )
            var count = 0
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: ""
                val email = cursor.getString(1) ?: ""
                when (count) {
                    0 -> {
                        settingsManager.gemeenteNaam = name
                        settingsManager.gemeenteEpos = email
                    }
                    1 -> {
                        settingsManager.gemeente2Naam = name
                        settingsManager.gemeente2Epos = email
                    }
                    2 -> {
                        settingsManager.gemeente3Naam = name
                        settingsManager.gemeente3Epos = email
                    }
                }
                count++
            }
            cursor.close()
            withContext(Dispatchers.Main) {
                applyChurchHeader()
            }
        }

    }

    private fun applyChurchHeader() {
        val name1 = settingsManager.gemeenteNaam
        val name2 = settingsManager.gemeente2Naam
        val name3 = settingsManager.gemeente3Naam

        val fullText = buildString {
            append(name1)
            if (name2.isNotEmpty()) {
                append(" ").append(name2)
            }
            if (name3.isNotEmpty()) {
                append(" ").append(name3)
            }
        }

        val spannable = SpannableString(fullText)

        var start = 0

        // Gemeente 1
        if (name1.isNotEmpty()) {
            val end1 = name1.length
            spannable.setSpan(RelativeSizeSpan(0.8f), 0, end1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(BackgroundColorSpan(settingsManager.gemeenteKleur), 0, end1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            start = end1 + 1 // +1 for the space
        }

        // Gemeente 2
        if (name2.isNotEmpty()) {
            val end2 = start + name2.length
            spannable.setSpan(RelativeSizeSpan(0.8f), start, end2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(BackgroundColorSpan(settingsManager.gemeente2Kleur), start, end2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            start = end2 + 1
        }

        // Gemeente 3
        if (name3.isNotEmpty()) {
            val end3 = start + name3.length
            spannable.setSpan(RelativeSizeSpan(0.8f), start, end3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(BackgroundColorSpan(settingsManager.gemeente3Kleur), start, end3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.mainGemeentenaam.text = spannable

        // Set text colour based on the first gemeente’s background (for contrast)
        val firstColor = settingsManager.gemeenteKleur
        if (firstColor != -1) {
            binding.mainGemeentenaam.setTextColor(if (isColorDark(firstColor)) Color.WHITE else Color.BLACK)
        }
    }

    /**
     * Returns true if the luminance of [color] is dark enough to require white text.
     */
    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (
                0.299 * Color.red(color) +
                        0.587 * Color.green(color) +
                        0.114 * Color.blue(color)
                ) / 255
        return darkness >= 0.5
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
                Log.e(TAG, "Failed to query MediaStore for backups", e)
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





    // ------------------------------------------------------------
    // Factory for MainViewModel
    // ------------------------------------------------------------
    /**
     * Single point of truth for changing the sort order.
     * Updates SettingsManager, MemberViewModel, and MainViewModel.
     */
    fun updateSortOrder(newSort: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "updateSortOrder: $newSort")
        settingsManager.defLayout = newSort
        viewModel.sortOrder = newSort
        mainViewModel.setSortOrder(newSort)
        binding.sortorder.text = newSort
        binding.sortorder.tag = newSort

        // ✅ Update adapter's internal sortOrder so separator click logic works
        memberListAdapter.updateState(
            listView = settingsManager.listView,
            soekList = viewModel.soekList,
            soek = viewModel.soek,
            recordStatus = viewModel.recordStatus,
            sortOrder = newSort
        )

        viewModel.refresh()
    }



}