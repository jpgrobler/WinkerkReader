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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkDbHelper
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.services.CallMonitoringService
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.ui.models.MainQueryMode
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.DeviceIdManager
import za.co.jpsoft.winkerkreader.utils.MenuItemHandler
import za.co.jpsoft.winkerkreader.utils.NavigationHandler
import za.co.jpsoft.winkerkreader.utils.PermissionHelper
import za.co.jpsoft.winkerkreader.utils.PermissionManager
import za.co.jpsoft.winkerkreader.utils.SearchCheckBoxPreferences
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.Utils
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
    private lateinit var menuController: MainMenuController
    private lateinit var startupCoordinator: MainStartupCoordinator
    private lateinit var listInteractionController: MemberListInteractionController

    companion object {
        private const val TAG = "Winkerk_MainActivity"
        const val CHANNEL_ID = "winkerkReaderServiceChannel"
        const val SEARCH_CHECK_BOX = "SEARCH_CHECK_BOX"
        const val FILTER_CHECK_BOX = "FILTER_CHECK_BOX"

        val whatsappContacts = mutableListOf<String>()
    }

    private val searchLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.let { data ->
                        val list =
                                if (android.os.Build.VERSION.SDK_INT >=
                                                android.os.Build.VERSION_CODES.TIRAMISU
                                ) {
                                    data.getParcelableArrayListExtra(
                                            SEARCH_CHECK_BOX,
                                            SearchCheckBox::class.java
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    data.getParcelableArrayListExtra<SearchCheckBox>(
                                            SEARCH_CHECK_BOX
                                    )
                                }
                        if (list != null) {
                            searchList = list
                            viewModel.setSearchList(searchList)
                        }
                    }
                } else {
                    handleResultCancelled()
                }
            }

    private val filterLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    val list =
                        if (android.os.Build.VERSION.SDK_INT >=
                            android.os.Build.VERSION_CODES.TIRAMISU
                        ) {
                            data.getParcelableArrayListExtra(
                                FILTER_CHECK_BOX,
                                FilterBox::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            data.getParcelableArrayListExtra<FilterBox>(FILTER_CHECK_BOX)
                        }
                    if (list != null) {
                        searchFilterCoordinator.applyFilterResult(list, viewModel.sortOrder)
                    }
                }
            } else {
                handleResultCancelled()
            }
        }

    private val overlayPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        Log.d(TAG, "Overlay permission granted")
                    }
                }
            }

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
        backgroundExecutor = Executors.newSingleThreadExecutor()
        gestureDetector = GestureDetector(this, SwipeGestureDetector())

        startupCoordinator =
                MainStartupCoordinator(
                        tag = TAG,
                        context = this,
                        lifecycleScope = lifecycleScope,
                        settingsManager = settingsManager,
                        permissionManager = permissionManager,
                        binding = binding,
                        checkAndRequestPermissions = ::checkAndRequestPermissions,
                        startMonitoringServiceIfEnabled = ::startMonitoringServiceIfEnabled,
                        setupViewModel = ::setupViewModel,
                        setupPermissions = ::setupPermissions,
                        initializeData = { initializeData(savedInstanceState) },
                        setupEventHandlers = ::setupEventHandlers,
                        setupAlarms = ::setupAlarms,
                        loadInitialData = ::loadInitialData,
                        ensureServicesAreRunning = ::ensureServicesAreRunning,
                        isNotificationAccessEnabled = ::isNotificationAccessEnabled,
                        openNotificationSettings = ::openNotificationSettings,
                        showToast = { message ->
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
        if (permissionManager.isFirstLaunch()) {
            showFirstLaunchPermissionDialog()
            return
        }
        if (permissionManager.isCheckOnStartEnabled() &&
                        !permissionManager.hasEssentialPermissions()
        ) {
            showPermissionReminderDialog()
        }
    }

    private fun showFirstLaunchPermissionDialog() {
        AlertDialog.Builder(this)
                .setTitle("Welcome to WinkerkReader!")
                .setMessage(
                        "This app requires several permissions to function properly.\n\nPlease grant the necessary permissions to continue."
                )
                .setCancelable(false)
                .setPositiveButton("Grant Permissions") { _, _ ->
                    permissionManager.setFirstLaunchComplete()
                    startActivity(Intent(this@MainActivity, PermissionsActivity::class.java))
                }
                .setNegativeButton("Exit") { _, _ -> finish() }
                .show()
    }

    private fun showPermissionReminderDialog() {
        val missingCount = permissionManager.getMissingPermissionsCount()
        AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage(
                        "You have $missingCount missing permission(s).\n\nSome features may not work correctly without these permissions."
                )
                .setPositiveButton("Grant Now") { _, _ ->
                    startActivity(Intent(this@MainActivity, PermissionsActivity::class.java))
                }
                .setNegativeButton("Later", null)
                .setNeutralButton("Don't Ask Again") { _, _ -> showDisablePermissionCheckDialog() }
                .show()
    }

    private fun showDisablePermissionCheckDialog() {
        AlertDialog.Builder(this)
                .setTitle("Disable Permission Check")
                .setMessage(
                        "Are you sure you want to disable the permission check on startup?\n\nYou can re-enable it later from the settings menu."
                )
                .setPositiveButton("Yes, Disable") { _, _ ->
                    permissionManager.setCheckOnStart(false)
                    Toast.makeText(
                                    this,
                                    "Permission check disabled. Enable it from Settings.",
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
                .setNegativeButton("Cancel", null)
                .show()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            PermissionHelper.getSystemAlertWindowPermissionIntent(this)?.let {
                overlayPermissionLauncher.launch(it)
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
                viewModel = viewModel,
                settingsManager = settingsManager,
                binding = binding,
                memberListAdapter = memberListAdapter,
                findSearchView = ::findSearchView,
                hideFilterPanel = {
                    if (binding.mainFilter.visibility == View.VISIBLE) {
                        binding.mainFilter.visibility = View.GONE
                    }
                },
                observeDataset = ::observeDataset
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
                    scrollToNextBirthday(items)
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

        backgroundExecutor.execute {
            WinkerkDbHelper.setDatabaseDate(this)
            WinkerkDbHelper.setChurchInfo(this)
            runOnUiThread {
                val churchText =
                        "${settingsManager.gemeenteNaam} ${settingsManager.gemeente2Naam} ${settingsManager.gemeente3Naam}".trim()
                binding.mainGemeentenaam.text = churchText
                // Apply the background color properly if set
                if (settingsManager.gemeenteKleur != -1) {
                    binding.mainGemeentenaam.setBackgroundColor(settingsManager.gemeenteKleur)
                    // Ensure text is readable
                    binding.mainGemeentenaam.setTextColor(
                        if (isColorDark(settingsManager.gemeenteKleur))
                            android.graphics.Color.WHITE
                        else
                            android.graphics.Color.BLACK
                    )
                }

                observeDataset()
                WhatsAppContactLoader.loadWhatsAppContactsAtomic(this)
            }
        }
    }
    // Helper to determine if color is dark
    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * android.graphics.Color.red(color) +
                0.587 * android.graphics.Color.green(color) +
                0.114 * android.graphics.Color.blue(color)) / 255
        return darkness >= 0.5
    }
    fun observeDataset() {
        // Only show search block if we're actually searching and have search text
        binding.searchItemBlock.visibility =
                if (viewModel.soekList && viewModel.soek.isNotEmpty()) View.VISIBLE else View.GONE

        // Don't override binding.sortorder.text if we're searching
        if (!viewModel.soekList || viewModel.soek.isEmpty()) {
            binding.sortorder.text = viewModel.sortOrder
            binding.sortorder.tag = viewModel.sortOrder
        }

        when (val mode = resolveQueryMode(settingsManager.defLayout)) {
            MainQueryMode.Search -> {
                // Only do search if there's actual search text
                if (viewModel.soek.isNotEmpty()) {
                    viewModel.soekList = true
                    loadQueryMode(mode)
                } else {
                    // Fall back to default view if search is empty
                    viewModel.soekList = false
                    val fallbackLayout =
                        if (searchFilterCoordinator.originalLayoutBeforeSearch.isNotEmpty()) {
                            searchFilterCoordinator.originalLayoutBeforeSearch
                        } else {
                            settingsManager.defLayout
                        }
                    loadQueryMode(resolveQueryMode(fallbackLayout))
                }
            }
            is MainQueryMode.Filter -> {
                viewModel.soekList = false
                binding.searchItemBlock.visibility = View.VISIBLE
                loadQueryMode(mode)
            }
            else -> loadQueryMode(mode)
        }
    }

    private fun resolveQueryMode(layout: String): MainQueryMode {
        return when (layout) {
            "SOEK_DATA" -> MainQueryMode.Search
            "FILTER_DATA" -> MainQueryMode.Filter(searchFilterCoordinator.filterList ?: arrayListOf())
            "ADRES" -> MainQueryMode.Address
            "GESINNE" -> MainQueryMode.Family
            "HUWELIK" -> MainQueryMode.Wedding
            "OUDERDOM" -> MainQueryMode.Age
            "VAN" -> MainQueryMode.Surname
            "VERJAAR" -> MainQueryMode.Birthday
            "WYK" -> MainQueryMode.Ward
            else -> MainQueryMode.Raw(layout)
        }
    }

    private fun loadQueryMode(mode: MainQueryMode) {
        viewModel.loadData(this, mode)
    }

    // -------------------------------------------------------------------------
    // Birthday auto-scroll — operates on List<MemberItem>, not a Cursor
    // -------------------------------------------------------------------------

    private fun scrollToNextBirthday(items: List<MemberItem>) {
        backgroundExecutor.execute {
            try {
                val today = java.time.LocalDate.now()
                val currentMonth = today.monthValue.toString().padStart(2, '0')
                val currentDay = today.dayOfMonth.toString().padStart(2, '0')
                val targetPosition = findNextBirthdayPosition(items, currentMonth, currentDay)
                if (targetPosition != -1) {
                    runOnUiThread {
                        binding.lidmaatList.post { binding.lidmaatList.scrollToPosition(targetPosition) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scrolling to next birthday", e)
            }
        }
    }

    /**
     * Finds the position of the first birthday >= today's month-day, wrapping around to the first
     * birthday of the year if none found after today.
     */
    private fun findNextBirthdayPosition(
            items: List<MemberItem>,
            todayMonth: String,
            todayDay: String
    ): Int {
        val todayMD = todayMonth.toInt() * 100 + todayDay.toInt()
        var firstCandidatePos = -1
        var firstCandidateMD = Int.MAX_VALUE

        for ((pos, item) in items.withIndex()) {
            val birthday = item.birthday
            if (birthday.length >= 10) {
                try {
                    val month = birthday.substring(3, 5).trim()
                    val day = birthday.substring(0, 2).trim()
                    val monthDay = month.toInt() * 100 + day.toInt()
                    if (monthDay >= todayMD) return pos
                    if (monthDay < firstCandidateMD) {
                        firstCandidateMD = monthDay
                        firstCandidatePos = pos
                    }
                } catch (_: NumberFormatException) {}
            }
        }
        return firstCandidatePos
    }

    // -------------------------------------------------------------------------
    // Database helpers (unchanged)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Options menu & search (unchanged)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Options menu & search (unchanged)
    // -----------------------------------------------------------------------
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
