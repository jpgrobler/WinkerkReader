package za.co.jpsoft.winkerkreader.ui.activities

import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.DeviceIdManager
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.WinkerkReader
import za.co.jpsoft.winkerkreader.utils.AppSessionState
import za.co.jpsoft.winkerkreader.utils.SearchCheckBoxPreferences
import za.co.jpsoft.winkerkreader.utils.MenuItemHandler
import za.co.jpsoft.winkerkreader.utils.WorkManagerHelper
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.services.CallMonitoringService
import za.co.jpsoft.winkerkreader.widget.WinkerkReaderWidgetProvider
import za.co.jpsoft.winkerkreader.utils.PermissionHelper
import za.co.jpsoft.winkerkreader.services.receivers.AlarmReceiver
import za.co.jpsoft.winkerkreader.utils.Utils
import za.co.jpsoft.winkerkreader.utils.PermissionManager
import za.co.jpsoft.winkerkreader.utils.MemberActionHandler
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.utils.NavigationHandler
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.utils.forceShowIcons

import android.view.View
import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper
import org.joda.time.DateTime
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.net.toUri
import za.co.jpsoft.winkerkreader.R
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.enableEdgeToEdge



class MainActivity : AppCompatActivity() {

    private val isLoadingWhatsAppContacts = AtomicBoolean(false)
    private val whatsAppContactsLoaded = AtomicBoolean(false)
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    // Views
    private lateinit var memberListAdapter: MemberListAdapter
    private lateinit var memberListView: RecyclerView

    private lateinit var sortOrderView: TextView
    private lateinit var memberCountView: TextView
    private lateinit var searchTextView: TextView
    private lateinit var churchNameView: TextView
    private lateinit var searchItemBlock: View
    private var optionsMenu: Menu? = null

    // Data
    private lateinit var viewModel: MemberViewModel
    private lateinit var settingsManager: SettingsManager
    private lateinit var gestureDetector: GestureDetector
    private lateinit var backgroundExecutor: java.util.concurrent.ExecutorService

    // State
    private var listItemPosition = 0
    private var listItemId: Long = 0
    private var currentLayout = ""
    private var searchList: ArrayList<SearchCheckBox> = arrayListOf()
    private var filterList: ArrayList<FilterBox>? = null

    private lateinit var permissionManager: PermissionManager

    companion object {
        private const val TAG = "Winkerk_MainActivity"
        const val SEARCH_LIST_REQUEST = 16895
        private const val FILTER_LIST_REQUEST = 16896
        const val CHANNEL_ID = "winkerkReaderServiceChannel"
        const val SEARCH_CHECK_BOX = "SEARCH_CHECK_BOX"
        const val FILTER_CHECK_BOX = "FILTER_CHECK_BOX"

        val whatsappContacts = mutableListOf<String>()
    }


    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        // Enable edge-to-edge and allow drawing under the cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        // Use WindowCompat to prevent the system from adding padding
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Make status bar and navigation bar transparent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        settingsManager = SettingsManager(this)
        permissionManager = PermissionManager(this)

        checkAndRequestPermissions()

        setContentView(R.layout.activity_main)
        initializeComponents()

        startMonitoringServiceIfEnabled()

        setupViewModel()
        loadPreferences()
        checkStoragePermissions()
        setupPermissions()
        initializeData(savedInstanceState)
        setupEventHandlers()
        setupAlarms()
        loadInitialData()

        val notificationEnabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!(notificationEnabled != null && notificationEnabled.contains(packageName))) {
            openNotificationSettings()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        val mainView = findViewById<View>(R.id.lidmaat_list)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

    }

    override fun onResume() {
        super.onResume()
        if (permissionManager.isCheckOnStartEnabled() && !permissionManager.isFirstLaunch()) {
            checkAndRequestPermissions()
        }
        ensureServicesAreRunning()
    }

    private fun ensureServicesAreRunning() {
        if (settingsManager.callMonitorEnabled && !CallMonitoringService.isServiceRunning()) {
            Log.d(TAG, "CallMonitoring service was killed, restarting...")
            startMonitoringServiceIfEnabled()
        }
    }

    private fun openNotificationSettings() {
        Toast.makeText(this, "Please enable notification access for this app", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
        if (permissionManager.isCheckOnStartEnabled() && !permissionManager.hasEssentialPermissions()) {
            showPermissionReminderDialog()
        }
    }

    private fun showFirstLaunchPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Welcome to WinkerkReader!")
            .setMessage("This app requires several permissions to function properly.\n\nPlease grant the necessary permissions to continue.")
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
            .setMessage("You have $missingCount missing permission(s).\n\nSome features may not work correctly without these permissions.")
            .setPositiveButton("Grant Now") { _, _ ->
                startActivity(Intent(this@MainActivity, PermissionsActivity::class.java))
            }
            .setNegativeButton("Later", null)
            .setNeutralButton("Don't Ask Again") { _, _ ->
                showDisablePermissionCheckDialog()
            }
            .show()
    }

    private fun showDisablePermissionCheckDialog() {
        AlertDialog.Builder(this)
            .setTitle("Disable Permission Check")
            .setMessage("Are you sure you want to disable the permission check on startup?\n\nYou can re-enable it later from the settings menu.")
            .setPositiveButton("Yes, Disable") { _, _ ->
                permissionManager.setCheckOnStart(false)
                Toast.makeText(this, "Permission check disabled. Enable it from Settings.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkStoragePermissions() {
        if (PermissionHelper.hasStoragePermissions(this)) {
            onStoragePermissionsGranted()
        } else {
            PermissionHelper.requestStoragePermissions(this)
        }
    }

    private fun onStoragePermissionsGranted() {
        Toast.makeText(this, "Storage permissions granted! App is ready to use.", Toast.LENGTH_SHORT).show()
    }

    private fun checkAndRequestStoragePermissions() {
        val notGranted = PermissionHelper.getNotGrantedPermissions(this, PermissionHelper.STORAGE_PERMISSIONS)
        if (notGranted.isEmpty()) {
            onStoragePermissionsGranted()
        } else {
            val missing = StringBuilder("Missing storage permissions:\n")
            for (permission in notGranted) {
                missing.append("• ").append(PermissionHelper.getPermissionDisplayName(permission)).append("\n")
            }
            Log.w("Permissions", missing.toString())
            PermissionHelper.requestPermissionGroup(this, PermissionHelper.STORAGE_PERMISSIONS, PermissionHelper.REQUEST_CODE_STORAGE)
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(CHANNEL_ID, "Oproep", NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(serviceChannel)
    }

    // -------------------------------------------------------------------------
    // Component initialisation — RecyclerView replaces ListView
    // -------------------------------------------------------------------------

    private fun initializeComponents() {
        backgroundExecutor = Executors.newSingleThreadExecutor()

        sortOrderView    = findViewById(R.id.sortorder)
        memberCountView  = findViewById(R.id.main_Count)
        searchTextView   = findViewById(R.id.search_text)
        churchNameView   = findViewById(R.id.main_gemeentenaam)
        searchItemBlock  = findViewById(R.id.search_item_block)
        memberListView   = findViewById(R.id.lidmaat_list)

        // Build adapter with click lambdas — no more AdapterView listeners
        memberListAdapter = MemberListAdapter(
            onItemClick = { view, item, position ->
                showMemberPopupMenu(view, item, position)
            },
            onItemLongClick = { item, position ->
                onMemberLongClick(item, position)
            }
        )

        memberListView.layoutManager = LinearLayoutManager(this)
        memberListView.adapter       = memberListAdapter

        gestureDetector = GestureDetector(this, SwipeGestureDetector())
    }

    // -------------------------------------------------------------------------
    // ViewModel — single observer replaces the previous 9 cursor observers
    // -------------------------------------------------------------------------

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[MemberViewModel::class.java]

        viewModel.getRowCount().observe(this) { count ->
            memberCountView.text = "[$count]"
        }
        viewModel.getTextLiveData().observe(this) { searchText ->
            searchTextView.text = searchText
            searchItemBlock.visibility = if (searchText.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.getVerjaarFLag().observe(this) { showBirthday ->
            // Flag fires after VERJAAR list is committed; scroll is handled in the
            // submitList callback below, so nothing extra needed here.
            Log.d(TAG, "verjaarFlag: $showBirthday")
        }

        // One observer for ALL sort orders — no cursor leaks, no 9-way dispatch
        viewModel.getMemberList().observe(this) { items ->
            val isVerjaar = settingsManager.defLayout == "VERJAAR"
            memberListAdapter.submitList(items) {
                // submitList callback fires on the main thread once DiffUtil has
                // committed changes — safe place to auto-scroll
                if (isVerjaar && items.isNotEmpty()) {
                    scrollToNextBirthday(items)
                }
            }
        }
    }

    private fun loadPreferences() {

        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).isListFoto = settingsManager.isListFoto
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).isListVerjaarBlok = settingsManager.isListVerjaarBlok
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).isListHuwelikBlok = settingsManager.isListHuwelikBlok
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).isListWyk = settingsManager.isListWyk
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).isListWhatsapp = settingsManager.isListWhatsapp
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).isListEpos = settingsManager.isListEpos
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).isListOuderdom = settingsManager.isListOuderdom
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).isListSelfoon = settingsManager.isListSelfoon
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).isListTelefoon = settingsManager.isListTelefoon
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).callMonitorEnabled = settingsManager.callMonitorEnabled
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout = settingsManager.defLayout
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).whatsapp1 = settingsManager.whatsapp1
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).whatsapp2 = settingsManager.whatsapp2
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).whatsapp3 = settingsManager.whatsapp3
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).eposHtml = settingsManager.eposHtml

        loadColorPreferences()

        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).widgetDoop = settingsManager.widgetDoop
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).widgetBelydenis = settingsManager.widgetBelydenis
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).widgetHuwelik = settingsManager.widgetHuwelik
    }

    private fun loadColorPreferences() {
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeenteKleur = settingsManager.gemeenteKleur
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente2Kleur = settingsManager.gemeente2Kleur
        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente3Kleur = settingsManager.gemeente3Kleur
    }

    private fun setupPermissions() {
        PermissionHelper.requestAllPermissions(this, PermissionHelper.REQUEST_CODE_ALL_PERMISSIONS)
        PermissionHelper.requestPermissionGroup(this, PermissionHelper.STORAGE_PERMISSIONS, PermissionHelper.REQUEST_CODE_STORAGE)
        checkOverlayPermission()
        createNotificationChannel()
        checkAndRequestStoragePermissions()
    }

    private fun initializeData(savedInstanceState: Bundle?) {
        AppSessionState.deviceId = DeviceIdManager.getDeviceId(this)
        setupVersionInfo()
        initializeSearchAndFilterLists()
        viewModel.setSearchList(searchList)
        savedInstanceState?.let { restoreInstanceState(it) }
        if (za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout.isEmpty()) {
            za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout = "GESINNE"
            currentLayout = "GESINNE"
        }
        AppSessionState.sortOrder = za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout
        AppSessionState.soekList = false
    }

    private fun setupVersionInfo() {
        try {
            val manager = packageManager
            val info = manager.getPackageInfo(packageName, 0)
            val versionName = "v${info.versionName}"
            //findViewById<TextView>(R.id.version).text = versionName
            supportActionBar?.let { actionBar ->
                // This adds version to the title while keeping radio buttons separate
                actionBar.title = "${getString(R.string.app_name)} ${versionName}"
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get package info", e)
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
        filterList = arrayListOf()
        val prefsManager = SearchCheckBoxPreferences(this)
        searchList = prefsManager.getSearchCheckBoxList()
        if (searchList.isEmpty()) {
            searchList = createDefaultSearchList()
            prefsManager.saveSearchCheckBoxList(searchList)
        }
    }

    private fun restoreInstanceState(savedInstanceState: Bundle) {
        try {
            val savedSearchList = savedInstanceState.getParcelableArrayList<SearchCheckBox>(SEARCH_CHECK_BOX)
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
        findViewById<ImageView>(R.id.main_search_text_close).setOnClickListener {
            AppSessionState.recordStatus = "0"
            searchItemBlock.visibility = View.GONE
            AppSessionState.soekList = false
            za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout = settingsManager.defLayout
            sortOrderView.background = null
            sortOrderView.text = za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout
            AppSessionState.sortOrder = za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout
            observeDataset()
        }
    }

    private fun setupSortOrderClickHandler() {
        sortOrderView.setOnClickListener { v ->
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
        churchNameView.setOnClickListener(::showGroupFunctionMenu)
    }

    // -------------------------------------------------------------------------
    // Long-click — toggle TAG without needing a cursor
    // -------------------------------------------------------------------------

    private fun onMemberLongClick(item: MemberItem, position: Int): Boolean {
        val values = ContentValues().apply {
            put(winkerkEntry.LIDMATE_TAG, if (item.tag == 0) 1 else 0)
        }
        val memberUri = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, item.id)
        val rowsAffected = contentResolver.update(
            memberUri, values,
            "${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ =?",
            arrayOf(item.id.toString())
        )
        if (rowsAffected == 1) observeDataset()
        return rowsAffected == 1
    }

    fun applyFilterList(filterList: ArrayList<FilterBox>) {
        this.filterList = filterList
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

        searchItemBlock.visibility = View.GONE
        sortOrderView.text = AppSessionState.sortOrder
        sortOrderView.tag = AppSessionState.sortOrder
        memberCountView.text = "[0]"

        backgroundExecutor.execute {
            setDatabaseDate()
            setChurchInfo()
            runOnUiThread {
                val churchText = "${za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeenteNaam} ${za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente2Naam} ${za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente3Naam}".trim()
                churchNameView.text = churchText
                observeDataset()
                loadWhatsAppContactsAtomic()
            }
        }
    }

    fun observeDataset() {
        searchItemBlock.visibility = if (AppSessionState.soekList) View.VISIBLE else View.GONE
        sortOrderView.text = AppSessionState.sortOrder
        sortOrderView.tag = AppSessionState.sortOrder

        when (za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout) {
            "SOEK_DATA" -> {
                AppSessionState.soekList = true
                viewModel.loadData(this, "SOEK_DATA")
            }
            "FILTER_DATA" -> {
                AppSessionState.soekList = false
                viewModel.loadData(this, "FILTER_DATA",
                    (filterList ?: arrayListOf()) as ArrayList<FilterBox>?
                )
                searchItemBlock.visibility = View.VISIBLE
            }
            "ADRES"   -> viewModel.loadData(this, "LIDMAAT_DATA_ADRES")
            "GESINNE" -> viewModel.loadData(this, "GESINNE_DATA")
            "HUWELIK" -> viewModel.loadData(this, "HUWELIK_DATA")
            "OUDERDOM"-> viewModel.loadData(this, "OUDERDOM_DATA")
            "VAN"     -> viewModel.loadData(this, "LIDMAAT_DATA")
            "VERJAAR" -> viewModel.loadData(this, "LIDMAAT_DATA_VERJAAR")
            "WYK"     -> viewModel.loadData(this, "LIDMAAT_DATA_WYK")
        }
    }

    // -------------------------------------------------------------------------
    // Birthday auto-scroll — operates on List<MemberItem>, not a Cursor
    // -------------------------------------------------------------------------

    private fun scrollToNextBirthday(items: List<MemberItem>) {
        backgroundExecutor.execute {
            try {
                val today        = DateTime.now()
                val currentMonth = today.toString().substring(5, 7).trim()
                val currentDay   = today.toString().substring(8, 10).trim()
                val targetPosition = findNextBirthdayPosition(items, currentMonth, currentDay)
                if (targetPosition != -1) {
                    runOnUiThread {
                        memberListView.post { memberListView.scrollToPosition(targetPosition) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scrolling to next birthday", e)
            }
        }
    }

    /**
     * Finds the position of the first birthday >= today's month-day,
     * wrapping around to the first birthday of the year if none found after today.
     */
    private fun findNextBirthdayPosition(
        items: List<MemberItem>,
        todayMonth: String,
        todayDay: String
    ): Int {
        val todayMD = todayMonth.toInt() * 100 + todayDay.toInt()
        var firstCandidatePos = -1
        var firstCandidateMD  = Int.MAX_VALUE

        for ((pos, item) in items.withIndex()) {
            val birthday = item.birthday
            if (birthday.length >= 10) {
                try {
                    val month    = birthday.substring(3, 5).trim()
                    val day      = birthday.substring(0, 2).trim()
                    val monthDay = month.toInt() * 100 + day.toInt()
                    if (monthDay >= todayMD) return pos
                    if (monthDay < firstCandidateMD) {
                        firstCandidateMD  = monthDay
                        firstCandidatePos = pos
                    }
                } catch (_: NumberFormatException) { }
            }
        }
        return firstCandidatePos
    }

    // -------------------------------------------------------------------------
    // WhatsApp contacts loading (unchanged)
    // -------------------------------------------------------------------------

    private fun loadWhatsAppContactsAtomic() {
        if (whatsAppContactsLoaded.get() && whatsappContacts.isNotEmpty()) {
            Log.d(TAG, "WhatsApp contacts already loaded, skipping...")
            return
        }
        if (!isLoadingWhatsAppContacts.compareAndSet(false, true)) {
            Log.d(TAG, "WhatsApp contacts are currently being loaded, skipping...")
            return
        }
        backgroundExecutor.execute {
            try {
                val contentResolver = contentResolver
                val projection = arrayOf(
                    ContactsContract.RawContacts._ID,
                    ContactsContract.RawContacts.CONTACT_ID
                )
                val selection = "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?"
                val selectionArgs = arrayOf("com.whatsapp")
                contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    projection, selection, selectionArgs, null
                )?.use { rawCursor ->
                    if (rawCursor.count == 0) {
                        Log.d(TAG, "No WhatsApp contacts found")
                        return@execute
                    }
                    val contactIdIdx = rawCursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
                    if (contactIdIdx == -1) return@execute
                    synchronized(whatsappContacts) { whatsappContacts.clear() }
                    while (rawCursor.moveToNext()) {
                        val contactId = rawCursor.getString(contactIdIdx)
                        if (contactId != null) {
                            loadWhatsAppContactNumber(contentResolver, contactId)
                        }
                    }
                    whatsAppContactsLoaded.set(true)
                    runOnUiThread {
                        if (whatsappContacts.isNotEmpty()) {
                            Toast.makeText(this, "WhatsApp Contacts loaded: ${whatsappContacts.size}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading WhatsApp contacts", e)
                whatsAppContactsLoaded.set(false)
            } finally {
                isLoadingWhatsAppContacts.set(false)
            }
        }
    }

    private fun loadWhatsAppContactNumber(contentResolver: ContentResolver, contactId: String) {
        val phoneProjection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val phoneSelection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val phoneSelectionArgs = arrayOf(contactId)
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            phoneProjection, phoneSelection, phoneSelectionArgs, null
        )?.use { phoneCursor ->
            if (phoneCursor.moveToFirst()) {
                val numberIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIdx != -1) {
                    val number = phoneCursor.getString(numberIdx)
                    if (!number.isNullOrEmpty()) {
                        val formatted = Utils.fixphonenumber(number)
                        if (formatted != null) {
                            synchronized(whatsappContacts) { whatsappContacts.add(formatted) }
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Popup menu — uses MemberItem for configuration; re-queries cursor for actions
    // -------------------------------------------------------------------------

    private fun showMemberPopupMenu(view: View, item: MemberItem, position: Int) {
        listItemPosition = position
        listItemId       = item.id
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.lidmaatlist_menu, popup.menu)
        popup.forceShowIcons()
        configurePopupMenuFromItem(popup, item)
        popup.show()
        popup.setOnMenuItemClickListener { menuItem ->
            handlePopupMenuClick(menuItem.itemId, item)
        }
    }

    /** Configure menu titles and visibility from a [MemberItem] — no cursor needed. */
    private fun configurePopupMenuFromItem(popup: PopupMenu, item: MemberItem) {
        val menu = popup.menu
        menu.findItem(R.id.kyk_lidmaat_detail).title = "Detail van ${item.name} ${item.surname}"
        menu.findItem(R.id.submenu_bel).title        = "Skakel ${item.name}"
        menu.findItem(R.id.submenu_teks).title       = "Teks ${item.name}"
        menu.findItem(R.id.submenu_ander).title      = item.name

        if (item.cellphone.isEmpty()) {
            safeRemoveMenuItem(menu, R.id.submenu_bel,  R.id.bel_selfoon)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_sms)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp2)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp3)
        } else {
            menu.findItem(R.id.bel_selfoon)?.title = "Skakel ${item.cellphone}"
            menu.findItem(R.id.stuur_sms)?.title   = "SMS na ${item.cellphone}"
        }

        if (item.landline.isEmpty()) {
            safeRemoveMenuItem(menu, R.id.submenu_bel, R.id.bel_landlyn)
        } else {
            menu.findItem(R.id.bel_landlyn)?.title = "Skakel ${item.landline}"
        }

        if (item.email.isEmpty()) {
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_epos)
        }

        if (!settingsManager.whatsapp1) safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp)
        if (!settingsManager.whatsapp2) safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp2)
        if (!settingsManager.whatsapp3) safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp3)
    }

    private fun safeRemoveMenuItem(menu: Menu, submenuId: Int, itemId: Int) {
        val submenu = menu.findItem(submenuId)
        if (submenu?.hasSubMenu() == true) {
            submenu.subMenu?.removeItem(itemId)
        }
    }

    /**
     * Re-queries the specific member by ID so [MemberActionHandler] (which still
     * accepts a Cursor) can perform its actions without holding a long-lived cursor.
     */
    private fun handlePopupMenuClick(actionId: Int, item: MemberItem): Boolean {
        Log.e("DEBUG", "=== handlePopupMenuClick START ===")
        Log.e("DEBUG", "actionId: $actionId")
        Log.e("DEBUG", "item.id: ${item.id}")
        Log.e("DEBUG", "item.guid: ${item.guid}")
        Log.e("DEBUG", "item.name: ${item.name}")

        val memberUri = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, item.id)
        Log.e("DEBUG", "memberUri: $memberUri")

        var cursor: Cursor? = null
        return try {
            Log.e("DEBUG", "Before query...")
            cursor = contentResolver.query(memberUri, null, null, null, null)
            Log.e("DEBUG", "After query, cursor = $cursor")

            if (cursor != null) {
                Log.e("DEBUG", "Cursor count: ${cursor.count}")
                Log.e("DEBUG", "Cursor moveToFirst: ${cursor.moveToFirst()}")
            }

            if (cursor != null && cursor.moveToFirst()) {
                Log.e("DEBUG", "Calling MemberActionHandler")
                MemberActionHandler(this, cursor).handleAction(actionId)
            } else {
                Log.w(TAG, "Could not query member for action: id=${item.id}")
                false
            }
        } catch (e: Exception) {
            Log.e("DEBUG", "EXCEPTION CAUGHT!", e)
            Log.e("DEBUG", "Exception message: ${e.message}")
            Log.e("DEBUG", "Exception stack trace:", e)
            false
        } finally {
            cursor?.close()
            Log.e("DEBUG", "=== handlePopupMenuClick END ===")
        }
    }

    // -------------------------------------------------------------------------
    // Group-function menu — uses currentList, no cursor
    // -------------------------------------------------------------------------

    private fun showGroupFunctionMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.groepfunksie_menu, popup.menu)
        popup.forceShowIcons()
        val items = memberListAdapter.currentList
        if (items.isEmpty()) return
        val totalCount    = items.size
        val selectedCount = items.count { it.tag == 1 }

        popup.menu.findItem(R.id.sms_groep).title          = "Almal ($totalCount)"
        popup.menu.findItem(R.id.sms_selected).title       = "Geselekteerdes ($selectedCount)"
        popup.menu.findItem(R.id.almal_in_groep).title     = "Almal ($totalCount)"
        popup.menu.findItem(R.id.selected_in_groep).title  = "Geselekteerdes ($selectedCount)"

        popup.show()
        popup.setOnMenuItemClickListener { handleGroupMenuClick(it) }
    }

    private fun handleGroupMenuClick(item: MenuItem): Boolean {
        // SMS group sending not yet implemented
        return true
    }

    // -------------------------------------------------------------------------
    // Database helpers (unchanged)
    // -------------------------------------------------------------------------

    private fun setDatabaseDate() {
        try {
            SQLiteAssetHelper(this, winkerkEntry.WINKERK_DB, null, 1).use { helper ->
                helper.readableDatabase.use { db ->
                    db.rawQuery("SELECT * FROM Datum", null).use { cursor ->
                        if (cursor.moveToFirst()) {
                            val dateIdx = cursor.getColumnIndex("DataDatum")
                            za.co.jpsoft.winkerkreader.utils.SettingsManager(this).dataDatum = if (dateIdx != -1) cursor.getString(dateIdx) ?: "" else ""
                        } else {
                            za.co.jpsoft.winkerkreader.utils.SettingsManager(this).dataDatum = ""
                        }
                    }
                }
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "Error reading database date", e)
            za.co.jpsoft.winkerkreader.utils.SettingsManager(this).dataDatum = ""
        }
    }

    private fun setChurchInfo() {
        val query = "SELECT DISTINCT Members._rowid_ as _id, Gemeente, [Gemeente epos] FROM Members GROUP BY Gemeente, [Gemeente epos]"
        try {
            SQLiteAssetHelper(this, winkerkEntry.WINKERK_DB, null, 1).use { helper ->
                helper.readableDatabase.use { db ->
                    db.rawQuery(query, null).use { cursor ->
                        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeenteNaam = ""
                        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeenteEpos = ""
                        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente2Naam = ""
                        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente2Epos = ""
                        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente3Naam = ""
                        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente3Epos = ""

                        if (cursor.moveToFirst()) {
                            val nameIdx  = cursor.getColumnIndex("Gemeente")
                            val emailIdx = cursor.getColumnIndex("Gemeente epos")
                            if (nameIdx != -1 && emailIdx != -1) {
                                za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeenteNaam = cursor.getString(nameIdx) ?: ""
                                za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeenteEpos = cursor.getString(emailIdx) ?: ""
                                if (cursor.moveToNext()) {
                                    za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente2Naam = cursor.getString(nameIdx) ?: ""
                                    za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente2Epos = cursor.getString(emailIdx) ?: ""
                                    if (cursor.moveToNext()) {
                                        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente3Naam = cursor.getString(nameIdx) ?: ""
                                        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente3Epos = cursor.getString(emailIdx) ?: ""
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "Error reading church info", e)
        }
    }

    // -------------------------------------------------------------------------
    // Options menu & search (unchanged)
    // -------------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        optionsMenu = menu
        if (menu.javaClass.simpleName == "MenuBuilder") {
            try {
                val method = menu.javaClass.getDeclaredMethod("setOptionalIconsVisible", Boolean::class.java)
                method.isAccessible = true
                method.invoke(menu, true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show menu icons", e)
            }
        }
        menuInflater.inflate(R.menu.menu_main, menu)
        setupSearchView(menu)

        val menuItem = optionsMenu?.findItem(R.id.aktief_radio_group)
        val radioGroup = menuItem?.actionView as RadioGroup
        when (AppSessionState.recordStatus) {
            "0" -> radioGroup.check(R.id.filter_aktief2)
            "2" -> radioGroup.check(R.id.filter_onaktief2)
            "*" -> radioGroup.check(R.id.filter_beide)
            else -> radioGroup.check(R.id.filter_aktief2) // Default to Active
        }
        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            val newStatus = when (checkedId) {
                R.id.filter_aktief2 -> "0"
                R.id.filter_onaktief2 -> "2"
                R.id.filter_beide -> "*"
                else -> "0"
            }

            Log.d("MainActivity", "Radio changed: ${AppSessionState.recordStatus} -> $newStatus")
            AppSessionState.recordStatus = newStatus

            // Clear the cache to force a fresh query
            // You might want to add a clearCache() method in your ViewModel
            viewModel.clearCache()  // We'll add this

            observeDataset()
        }
        return true
    }

    private fun setupSearchView(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search)
        searchItem.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
        val searchView = searchItem.actionView as? SearchView ?: return

        searchView.isSubmitButtonEnabled = false
        searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)?.hint = "Soek"
        searchView.findViewById<View>(androidx.appcompat.R.id.search_plate)?.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.transparent)
        )

        val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))

        searchView.setOnCloseListener {
            AppSessionState.sortOrder = za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout
            AppSessionState.soekList = false
            observeDataset()
            false
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                performSearch(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable { performSearch(newText) }
                searchHandler.postDelayed(searchRunnable!!, 300)
                return true
            }
        })
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            AppSessionState.sortOrder = za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout
            AppSessionState.soekList = false
            searchItemBlock.visibility = View.GONE
            observeDataset()
        } else {
            searchItemBlock.visibility = View.VISIBLE
            val searchText = if (AppSessionState.recordStatus == "2") "Onaktief $query" else query
            searchTextView.text = searchText
            AppSessionState.soek = query.trim()
            za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout = "SOEK_DATA"
            AppSessionState.soekList = true
            observeDataset()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return MenuItemHandler(this, settingsManager).handleMenuItem(item) || super.onOptionsItemSelected(item)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) {
            AppSessionState.sortOrder = SettingsManager(this).defLayout
            AppSessionState.soekList = false
            observeDataset()
            return
        }
        when (requestCode) {
            SEARCH_LIST_REQUEST -> {
                val extras = data.extras
                if (extras != null) {
                    val list = extras.getParcelableArrayList<SearchCheckBox>(SEARCH_CHECK_BOX)
                    if (list != null) {
                        searchList = list
                        viewModel.setSearchList(searchList)
                    }
                }
            }
            FILTER_LIST_REQUEST -> {
                val extras = data.extras
                if (extras != null) {
                    val list = extras.getParcelableArrayList<FilterBox>(FILTER_CHECK_BOX)
                    if (list != null) {
                        filterList = list
                        AppSessionState.sortOrder = "Filter"
                        za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout = "FILTER_DATA"
                        AppSessionState.soekList = false
                        observeDataset()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        isLoadingWhatsAppContacts.set(false)
        whatsAppContactsLoaded.set(false)
        backgroundExecutor.shutdown()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Swipe gesture detector (unchanged)
    // -------------------------------------------------------------------------

    private inner class SwipeGestureDetector : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_MIN_DISTANCE      = 120
        private val SWIPE_MAX_OFF_PATH      = 200
        private val SWIPE_THRESHOLD_VELOCITY = 200

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            try {
                if (e1 == null) return false
                val diffAbs = Math.abs(e1.y - e2.y)
                val diff    = e1.x - e2.x
                if (diffAbs > SWIPE_MAX_OFF_PATH) return false
                when {
                    diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY  -> onLeftSwipe()
                    -diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY -> onRightSwipe()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error on gestures", e)
            }
            return false
        }
    }

    private fun onLeftSwipe()  { NavigationHandler.handleLeftSwipe(this, sortOrderView) }
    private fun onRightSwipe() { NavigationHandler.handleRightSwipe(this, sortOrderView) }
}