package za.co.jpsoft.winkerkreader.ui.activities

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import java.util.concurrent.Executors
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.services.CallMonitoringService
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.BatteryOptimizationHelper
import za.co.jpsoft.winkerkreader.utils.DeviceIdManager
import za.co.jpsoft.winkerkreader.utils.MenuItemHandler
import za.co.jpsoft.winkerkreader.utils.NavigationHandler
import za.co.jpsoft.winkerkreader.utils.PermissionHelper
import za.co.jpsoft.winkerkreader.utils.PermissionManager
import za.co.jpsoft.winkerkreader.utils.SearchCheckBoxPreferences
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.WhatsAppContactLoader
import za.co.jpsoft.winkerkreader.utils.WorkManagerHelper
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var memberListAdapter: MemberListAdapter

    // Data
    private lateinit var viewModel: MemberViewModel
    private lateinit var settingsManager: SettingsManager
    private lateinit var gestureDetector: GestureDetector
    private lateinit var backgroundExecutor: java.util.concurrent.ExecutorService

    // State
    private var searchList: ArrayList<SearchCheckBox> = arrayListOf()
    private lateinit var searchFilterCoordinator: MainSearchFilterCoordinator

    private lateinit var permissionManager: PermissionManager
    private lateinit var permissionDialogManager: PermissionDialogManager
    private lateinit var menuController: MainMenuController
    private lateinit var startupCoordinator: MainStartupCoordinator
    private lateinit var listInteractionController: MemberListInteractionController
    private lateinit var activityResultCoordinator: ActivityResultCoordinator
    private lateinit var mainDataLoader: MainDataLoader

    companion object {
        private const val TAG = "Winkerk_MainActivity"
        const val CHANNEL_ID = "winkerkReaderServiceChannel"
        const val SEARCH_CHECK_BOX = "SEARCH_CHECK_BOX"
        const val FILTER_CHECK_BOX = "FILTER_CHECK_BOX"
    }

    // Activity result launchers are registered in ActivityResultCoordinator
    // (field initialisation — must happen before onCreate)
    // NOTE: activityResultCoordinator is lateinit and assigned in onCreate because
    // its callbacks reference searchFilterCoordinator which is also lateinit.
    // The launchers themselves are registered lazily inside the coordinator constructor
    // which is called at the top of onCreate before setContentView.

    private fun handleResultCancelled() {
        searchFilterCoordinator.handleResultCancelled()
    }

    private fun resetAllFiltersAndSearch() {
        searchFilterCoordinator.resetAllFiltersAndSearch()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager.getInstance(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViews()
        permissionManager = PermissionManager(this)
        permissionDialogManager = PermissionDialogManager(this, permissionManager)
        backgroundExecutor = Executors.newSingleThreadExecutor()
        gestureDetector = GestureDetector(this, SwipeGestureDetector())

        // Must be created before startupCoordinator so launchers are registered
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
        mainDataLoader = MainDataLoader(
            context = this,
            binding = binding,
            settingsManager = settingsManager,
            executor = backgroundExecutor
        )

        startupCoordinator =
            MainStartupCoordinator(
                tag = TAG,
                context = this,
                lifecycleScope = lifecycleScope,
                settingsManager = settingsManager,
                permissionManager = permissionManager,
                binding = binding,
                actions = object : StartupActions {
                    override fun checkAndRequestPermissions() = this@MainActivity.checkAndRequestPermissions()
                    override fun startMonitoringServiceIfEnabled() = this@MainActivity.startMonitoringServiceIfEnabled()
                    override fun setupViewModel() = this@MainActivity.setupViewModel()
                    override fun setupPermissions() = this@MainActivity.setupPermissions()
                    override fun initializeData() = this@MainActivity.initializeData(null)
                    override fun setupEventHandlers() = this@MainActivity.setupEventHandlers()
                    override fun setupAlarms() = this@MainActivity.setupAlarms()
                    override fun loadInitialData() = this@MainActivity.loadInitialData()
                    override fun ensureServicesAreRunning() = this@MainActivity.ensureServicesAreRunning()
                    override fun isNotificationAccessEnabled() = this@MainActivity.isNotificationAccessEnabled()
                    override fun openNotificationSettings() = this@MainActivity.openNotificationSettings()
                    override fun showToast(message: String) {
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        startupCoordinator.runOnCreate()
        onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        finish()
                    }
                }
        )

        ViewCompat.setOnApplyWindowInsetsListener(binding.lidmaatList) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        // If call logging is enabled, check for battery optimizations to ensure reliability.
        if (settingsManager.callLogEnabled) {
            BatteryOptimizationHelper.showBatteryOptimizationDialog(this)
        }
    }

    override fun onResume() {
        super.onResume()
        startupCoordinator.runOnResume()
    }

    private fun ensureServicesAreRunning() {
        if (settingsManager.callMonitorEnabled && !CallMonitoringService.isServiceRunning()) {
            Log.d(TAG, "CallMonitoring service was killed, restarting...")
            startMonitoringServiceIfEnabled()
        }
    }

    private fun openNotificationSettings() {
        Toast.makeText(this, "Please enable notification access for this app", Toast.LENGTH_LONG)
                .show()
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val notificationEnabled =
                Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return notificationEnabled != null && notificationEnabled.contains(packageName)
    }

    private fun startMonitoringServiceIfEnabled() {
        if (settingsManager.callMonitorEnabled && !CallMonitoringService.isServiceRunning()) {
            try {
                val serviceIntent = Intent(this, CallMonitoringService::class.java)
                startForegroundService(serviceIntent)
                Log.d(TAG, "Call monitoring service started successfully")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception - check permissions", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start call monitoring service", e)
            }
        } else {
            Log.d(TAG, "Call monitoring service already running or disabled")
        }
    }

    private fun checkAndRequestPermissions() {
        permissionDialogManager.checkAndShowIfNeeded()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            PermissionHelper.getSystemAlertWindowPermissionIntent(this)?.let {
                activityResultCoordinator.overlayPermissionLauncher.launch(it)
            }
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel =
                NotificationChannel(CHANNEL_ID, "Oproep", NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(serviceChannel)
    }

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

        binding.lidmaatList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = memberListAdapter
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[MemberViewModel::class.java]
        searchFilterCoordinator =
            MainSearchFilterCoordinator(
                tag = TAG,
                context = this,
                viewModel = viewModel,
                settingsManager = settingsManager,
                binding = binding,
                memberListAdapter = memberListAdapter,
                findSearchView = ::findSearchView,
                hideFilterPanel = {
                    if (binding.mainFilter.visibility == View.VISIBLE) {
                        binding.mainFilter.visibility = View.GONE
                    }
                }
            )
        menuController =
            MainMenuController(
                activity = this,
                tag = TAG,
                viewModel = viewModel,
                searchFilterCoordinator = searchFilterCoordinator,
                observeDataset = ::observeDataset
            )
        listInteractionController =
            MemberListInteractionController(
                activity = this,
                tag = TAG,
                settingsManager = settingsManager,
                viewModel = viewModel,
                memberListAdapter = memberListAdapter,
                observeDataset = ::observeDataset
            )

        viewModel.getRowCount().observe(this) { count -> binding.mainCount.text = "[$count]" }
        viewModel.getTextLiveData().observe(this) { searchText ->
            binding.searchText.text = searchText
            binding.searchItemBlock.visibility = if (searchText.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.getVerjaarFLag().observe(this) { showBirthday ->
            // Flag fires after VERJAAR list is committed; scroll is handled in the
            // submitList callback below, so nothing extra needed here.
            Log.d(TAG, "verjaarFlag: $showBirthday")
        }

        // One observer for ALL sort orders — no cursor leaks, no 9-way dispatch
        viewModel.getMemberList().observe(this) { items ->
            val isVerjaar = settingsManager.defLayout == "VERJAAR"

            // Sync adapter state before submitting list
            memberListAdapter.updateState(
                    listView = settingsManager.listView,
                    soekList = viewModel.soekList,
                    soek = viewModel.soek,
                    recordStatus = viewModel.recordStatus,
                    sortOrder = viewModel.sortOrder
            )

            memberListAdapter.submitList(items) {
                // submitList callback fires on the main thread once DiffUtil has
                // committed changes — safe place to auto-scroll
                if (isVerjaar && items.isNotEmpty()) {
                    BirthdayScrollHelper.scrollToNextBirthday(
                        binding.lidmaatList,
                        items,
                        backgroundExecutor
                    )
                }
            }
        }
    }

    private fun setupPermissions() {
        PermissionHelper.requestAllPermissions(this, PermissionHelper.REQUEST_CODE_ALL_PERMISSIONS)
        checkOverlayPermission()
        createNotificationChannel()
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
        viewModel.sortOrder = settingsManager.defLayout
        viewModel.soekList = false
    }

    private fun setupVersionInfo() {
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            supportActionBar?.apply {
                title = "WinkerkReader"          // main title
                subtitle = "v$versionName"       // smaller text below title
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get package info", e)
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
            val savedSearchList =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                    ) {
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
            Log.w(TAG, "Failed to restore search list", e)
        }
    }

    private fun setupEventHandlers() {
        setupSearchCloseHandler()
        setupSortOrderClickHandler()
        setupChurchNameClickHandler()
        // Note: list item click/long-click are handled via MemberListAdapter lambdas
        // set up in initializeComponents()
    }

    private fun setupSearchCloseHandler() {
        binding.mainSearchTextClose.setOnClickListener {
            resetAllFiltersAndSearch()
        }
    }

    // Helper method to find the SearchView
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

    fun setFilterRestoreState(savedSortOrder: String) {
        searchFilterCoordinator.originalLayoutBeforeFilter = savedSortOrder
        searchFilterCoordinator.originalLayoutBeforeSearch = ""
    }

    fun clearFilterRestoreState() {
        searchFilterCoordinator.originalLayoutBeforeFilter = ""
    }

    fun clearAppliedFilterList() {
        searchFilterCoordinator.filterList = null
    }

    private fun setupAlarms() {
        setupAutoDownloadWork()
        setupReminderWork()
        setupWidgetRefreshWork()
    }

    private fun setupAutoDownloadWork() {
        if (settingsManager.autoDl || settingsManager.dlTimeUpdate) {
            val hour = settingsManager.dlHour.toInt()
            val minute = settingsManager.dlMinute.toInt()
            val day = settingsManager.dlDay

            WorkManagerHelper.scheduleDropboxDownload(this, hour, minute, day)
            settingsManager.dlTimeUpdate = false
            settingsManager.fromMenu = false
        }
    }

    private fun setupReminderWork() {
        if (settingsManager.herinner || settingsManager.smsTimeUpdate) {
            val hour = settingsManager.smsHour.toInt()
            val minute = settingsManager.smsMinute.toInt()

            WorkManagerHelper.scheduleBirthdayReminder(this, hour, minute)
            settingsManager.smsTimeUpdate = false
            settingsManager.fromMenu = false
        }
    }

    private fun setupWidgetRefreshWork() {
        WorkManagerHelper.scheduleWidgetRefresh(this)
    }

    private fun loadInitialData() {
        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }

        binding.searchItemBlock.visibility = View.GONE
        binding.sortorder.text = viewModel.sortOrder
        binding.sortorder.tag = viewModel.sortOrder
        binding.mainCount.text = "[0]"

        mainDataLoader.load {
            // Runs on main thread after church header is applied
            searchFilterCoordinator.refresh()
            WhatsAppContactLoader.loadWhatsAppContactsAtomic(this, lifecycleScope)
        }
    }

    /**
     * Public entry point used by [MainMenuController] and other coordinators
     * to trigger a data refresh. Delegates to [MainSearchFilterCoordinator.refresh].
     */
    fun observeDataset() {
        searchFilterCoordinator.refresh()
    }

    // -------------------------------------------------------------------------
    // Options menu, touch & lifecycle
    // -------------------------------------------------------------------------
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return menuController.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return MenuItemHandler(this, settingsManager, viewModel).handleMenuItem(item) ||
                super.onOptionsItemSelected(item)
    }

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

    override fun onDestroy() {
        if (::menuController.isInitialized) {
            menuController.clearCallbacks()
        }
        WhatsAppContactLoader.reset()
        if (::backgroundExecutor.isInitialized) {
            backgroundExecutor.shutdown()
        }
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Swipe gesture detector (unchanged)
    // -------------------------------------------------------------------------

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
                Log.e(TAG, "Error on gestures", e)
            }
            return false
        }
    }

    private fun onLeftSwipe() {
        NavigationHandler.handleLeftSwipe(this, binding.sortorder, viewModel)
    }

    private fun onRightSwipe() {
        NavigationHandler.handleRightSwipe(this, binding.sortorder, viewModel)
    }
}
