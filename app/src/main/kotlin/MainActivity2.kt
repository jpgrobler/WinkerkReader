package za.co.jpsoft.winkerkreader

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
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper
import forceShowIcons
import org.joda.time.DateTime
import za.co.jpsoft.winkerkreader.data.*
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.net.toUri

class MainActivity2 : AppCompatActivity() {

    private val isLoadingWhatsAppContacts = AtomicBoolean(false)
    private val whatsAppContactsLoaded = AtomicBoolean(false)
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    // Views
    private lateinit var cursorAdapter: WinkerkCursorAdapter
    private lateinit var memberListView: ListView
    private lateinit var progressBar: ProgressBar
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
                startActivity(Intent(this@MainActivity2, PermissionsActivity::class.java))
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
                startActivity(Intent(this@MainActivity2, PermissionsActivity::class.java))
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

    private fun initializeComponents() {
        backgroundExecutor = Executors.newSingleThreadExecutor()

        progressBar = findViewById(R.id.indeterminateBar)
        sortOrderView = findViewById(R.id.sortorder)
        memberCountView = findViewById(R.id.main_Count)
        searchTextView = findViewById(R.id.search_text)
        churchNameView = findViewById(R.id.main_gemeentenaam)
        searchItemBlock = findViewById(R.id.search_item_block)
        memberListView = findViewById(R.id.lidmaat_list)

        cursorAdapter = WinkerkCursorAdapter(this, null)
        memberListView.adapter = cursorAdapter
        memberListView.isFastScrollEnabled = true

        gestureDetector = GestureDetector(this, SwipeGestureDetector())

        progressBar.visibility = View.GONE
        progressBar.isIndeterminate = true

        findViewById<ProgressBar>(R.id.main_indeterminateBar2).visibility = View.GONE
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[MemberViewModel::class.java]

        viewModel.getRowCount().observe(this) { count ->
            memberCountView.text = "[$count]"
        }
        viewModel.getTextLiveData().observe(this) { searchText ->
            searchTextView.text = searchText
            searchItemBlock.visibility = if (searchText.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.getVerjaarFLag().observe(this, ::handleBirthdayFlag)
    }

    private fun loadPreferences() {
        winkerkEntry.LIST_FOTO = settingsManager.isListFoto
        winkerkEntry.LIST_VERJAARBLOK = settingsManager.isListVerjaarBlok
        winkerkEntry.LIST_HUWELIKBLOK = settingsManager.isListHuwelikBlok
        winkerkEntry.LIST_WYK = settingsManager.isListWyk
        winkerkEntry.LIST_WHATSAPP = settingsManager.isListWhatsapp
        winkerkEntry.LIST_EPOS = settingsManager.isListEpos
        winkerkEntry.LIST_OUDERDOM = settingsManager.isListOuderdom
        winkerkEntry.LIST_SELFOON = settingsManager.isListSelfoon
        winkerkEntry.LIST_TELEFOON = settingsManager.isListTelefoon
        winkerkEntry.OPROEPMONITOR = settingsManager.callMonitorEnabled
        winkerkEntry.DEFLAYOUT = settingsManager.defLayout
        winkerkEntry.WHATSAPP1 = settingsManager.whatsapp1
        winkerkEntry.WHATSAPP2 = settingsManager.whatsapp2
        winkerkEntry.WHATSAPP3 = settingsManager.whatsapp3
        winkerkEntry.EPOSHTML = settingsManager.eposHtml

        loadColorPreferences()

        winkerkEntry.WIDGET_DOOP = settingsManager.widgetDoop
        winkerkEntry.WIDGET_BELYDENIS = settingsManager.widgetBelydenis
        winkerkEntry.WIDGET_HUWELIK = settingsManager.widgetHuwelik
    }

    private fun loadColorPreferences() {
        winkerkEntry.GEMEENTE_KLEUR = settingsManager.gemeenteKleur
        winkerkEntry.GEMEENTE2_KLEUR = settingsManager.gemeente2Kleur
        winkerkEntry.GEMEENTE3_KLEUR = settingsManager.gemeente3Kleur
    }

    private fun setupPermissions() {
        PermissionHelper.requestAllPermissions(this, PermissionHelper.REQUEST_CODE_ALL_PERMISSIONS)
        PermissionHelper.requestPermissionGroup(this, PermissionHelper.STORAGE_PERMISSIONS, PermissionHelper.REQUEST_CODE_STORAGE)
        checkOverlayPermission()
        createNotificationChannel()
        checkAndRequestStoragePermissions()
    }

    private fun initializeData(savedInstanceState: Bundle?) {
        winkerkEntry.id = DeviceIdManager.getDeviceId(this)
        setupVersionInfo()
        initializeSearchAndFilterLists()
        savedInstanceState?.let { restoreInstanceState(it) }
        if (winkerkEntry.DEFLAYOUT.isEmpty()) {
            winkerkEntry.DEFLAYOUT = "GESINNE"
            currentLayout = "GESINNE"
        }
        winkerkEntry.SORTORDER = winkerkEntry.DEFLAYOUT
        winkerkEntry.SOEKLIST = false
    }

    private fun setupVersionInfo() {
        try {
            val manager = packageManager
            val info = manager.getPackageInfo(packageName, 0)
            val versionName = "v${info.versionName}"
            findViewById<TextView>(R.id.version).text = versionName
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
        setupListViewHandlers()
    }

    private fun setupSearchCloseHandler() {
        findViewById<ImageView>(R.id.main_search_text_close).setOnClickListener {
            winkerkEntry.RECORDSTATUS = "0"
            searchItemBlock.visibility = View.GONE
            winkerkEntry.SOEKLIST = false
            winkerkEntry.DEFLAYOUT = settingsManager.defLayout
            sortOrderView.background = null
            sortOrderView.text = winkerkEntry.DEFLAYOUT
            winkerkEntry.SORTORDER = winkerkEntry.DEFLAYOUT
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

    private fun setupListViewHandlers() {
        memberListView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, id ->
            onMemberLongClick(position, id)
        }
        memberListView.onItemClickListener = AdapterView.OnItemClickListener { _, view, position, id ->
            showMemberPopupMenu(view, position, id)
        }
    }

    private fun onMemberLongClick(position: Int, id: Long): Boolean {
        val cursor = cursorAdapter.getItem(position) as? Cursor ?: return false
        val values = ContentValues().apply {
            val tagIndex = cursor.getColumnIndex(winkerkEntry.LIDMATE_TAG)
            val currentTag = cursor.getInt(tagIndex)
            put(winkerkEntry.LIDMATE_TAG, if (currentTag == 0) 1 else 0)
        }
        val memberUri = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, id)
        val rowsAffected = contentResolver.update(memberUri, values, "${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ =?", arrayOf(id.toString()))
        if (rowsAffected == 1) {
            observeDataset()
        }
        return rowsAffected == 1
    }

    private fun setupAlarms() {
        setupAutoDownloadAlarm()
        setupReminderAlarm()
        setupWidgetRefreshAlarm()
    }

    private fun setupAutoDownloadAlarm() {
        if (settingsManager.autoDl || settingsManager.dlTimeUpdate) {
            val hour = settingsManager.dlHour
            val minute = settingsManager.dlMinute
            val day = settingsManager.dlDay

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour.toInt())
                set(Calendar.MINUTE, minute.toInt())
                set(Calendar.SECOND, 0)
                set(Calendar.DAY_OF_WEEK, day)
            }
            val now = Calendar.getInstance()
            settingsManager.fromMenu = false

            val intent = Intent(this, AlarmReceiver::class.java).apply { action = "DropBoxDownLoad" }
            val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)

            var triggerTime = calendar.timeInMillis
            if (triggerTime <= now.timeInMillis) {
                triggerTime += AlarmManager.INTERVAL_DAY * 7
            }
            scheduleRepeatingAlarm(alarmManager, triggerTime, AlarmManager.INTERVAL_DAY * 7, pendingIntent)
        }
    }

    fun applyFilterList(filterList: ArrayList<FilterBox>) {
        this.filterList = filterList
    }

    private fun setupReminderAlarm() {
        if (settingsManager.herinner || settingsManager.smsTimeUpdate) {
            val hour = settingsManager.smsHour
            val minute = settingsManager.smsMinute

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour.toInt())
                set(Calendar.MINUTE, minute.toInt())
                set(Calendar.SECOND, 0)
            }
            val now = Calendar.getInstance()
            settingsManager.smsTimeUpdate = false
            settingsManager.fromMenu = false

            val intent = Intent(this, AlarmReceiver::class.java).apply { action = "VerjaarSMS" }
            val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            var triggerTime = calendar.timeInMillis
            if (triggerTime <= now.timeInMillis) {
                triggerTime += AlarmManager.INTERVAL_DAY
            }
            scheduleRepeatingAlarm(alarmManager, triggerTime, AlarmManager.INTERVAL_DAY, pendingIntent)
        }
    }

    private fun setupWidgetRefreshAlarm() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val intent = Intent(this, WinkerkReaderWidgetProvider::class.java).apply {
            action = "android.appwidget.action.APPWIDGET_UPDATE"
            val ids = AppWidgetManager.getInstance(this@MainActivity2)
                .getAppWidgetIds(ComponentName(this@MainActivity2, WinkerkReaderWidgetProvider::class.java))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        scheduleRepeatingAlarm(alarmManager, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent)
    }

    private fun scheduleRepeatingAlarm(alarmManager: AlarmManager, triggerTime: Long, interval: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    private fun loadInitialData() {
        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }

        searchItemBlock.visibility = View.GONE
        sortOrderView.text = winkerkEntry.SORTORDER
        sortOrderView.tag = winkerkEntry.SORTORDER
        memberCountView.text = "[0]"

        backgroundExecutor.execute {
            setDatabaseDate()
            setChurchInfo()
            runOnUiThread {
                val churchText = "${winkerkEntry.GEMEENTE_NAAM} ${winkerkEntry.GEMEENTE2_NAAM} ${winkerkEntry.GEMEENTE3_NAAM}".trim()
                churchNameView.text = churchText
                observeDataset()
                loadWhatsAppContactsAtomic()
            }
        }
    }

    fun observeDataset() {
        searchItemBlock.visibility = if (winkerkEntry.SOEKLIST) View.VISIBLE else View.GONE
        sortOrderView.text = winkerkEntry.SORTORDER
        sortOrderView.tag = winkerkEntry.SORTORDER

        when (winkerkEntry.DEFLAYOUT) {
            "SOEK_DATA" -> {
                winkerkEntry.SOEKLIST = true
                viewModel.getSOEK_DATA(this).observe(this) { cursor ->
                    cursorAdapter.swapCursor(cursor)
                }
            }
            "FILTER_DATA" -> {
                winkerkEntry.SOEKLIST = false
                viewModel.getFILTER_DATA(this, filterList ?: arrayListOf()).observe(this) { cursor ->
                    cursorAdapter.swapCursor(cursor)
                }
                searchItemBlock.visibility = View.VISIBLE
            }
            "ADRES" -> {
                viewModel.getLIDMAAT_DATA_ADRES(this).observe(this) { cursor ->
                    cursorAdapter.swapCursor(cursor)
                }
            }
            "GESINNE" -> {
                viewModel.getGESINNE_DATA(this).observe(this) { cursor ->
                    cursorAdapter.swapCursor(cursor)
                }
            }
            "HUWELIK" -> {
                viewModel.getHUWELIK_DATA(this).observe(this) { cursor ->
                    cursorAdapter.swapCursor(cursor)
                }
            }
            "OUDERDOM" -> {
                viewModel.getOUDERDOM_DATA(this).observe(this) { cursor ->
                    cursorAdapter.swapCursor(cursor)
                }
            }
            "VAN" -> {
                viewModel.getLIDMAAT_DATA(this).observe(this) { cursor ->
                    cursorAdapter.swapCursor(cursor)
                }
            }
            "VERJAAR" -> {
                viewModel.getLIDMAAT_DATA_VERJAAR(this).observe(this) { cursor ->
                    cursorAdapter.swapCursor(cursor)
                }
            }
            "WYK" -> {
                viewModel.getLIDMAAT_DATA_WYK(this).observe(this) { cursor ->
                    cursorAdapter.swapCursor(cursor)
                }
            }
        }
    }

    private fun handleBirthdayFlag(showBirthday: Boolean) {
        if (!showBirthday) return
        val data = cursorAdapter.cursor ?: return
        backgroundExecutor.execute {
            try {
                val today = DateTime.now()
                if (data.count == 0) return@execute
                val birthdayIndex = data.getColumnIndex(winkerkEntry.LIDMATE_GEBOORTEDATUM)
                if (birthdayIndex == -1) return@execute
                data.moveToFirst()
                val currentMonth = today.toString().substring(5, 7).trim()
                val currentDay = today.toString().substring(8, 10).trim()
                val targetPosition = findTodaysBirthday(data, birthdayIndex, currentMonth, currentDay)
                if (targetPosition != -1) {
                    runOnUiThread {
                        memberListView.post { memberListView.setSelection(targetPosition) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling birthday flag", e)
            }
        }
    }

    private fun findTodaysBirthday(data: Cursor, columnIndex: Int, targetMonth: String, targetDay: String): Int {
        data.moveToFirst()
        while (!data.isAfterLast) {
            if (!data.isNull(columnIndex) && !data.getString(columnIndex).isNullOrEmpty()) {
                val dateString = data.getString(columnIndex)
                if (dateString.length >= 5) {
                    val month = dateString.substring(3, 5).trim()
                    if (month == targetMonth) break
                }
            }
            data.moveToNext()
        }
        while (!data.isAfterLast) {
            if (!data.isNull(columnIndex) && !data.getString(columnIndex).isNullOrEmpty()) {
                val dateString = data.getString(columnIndex)
                if (dateString.length >= 2) {
                    val day = dateString.substring(0, 2).trim()
                    if (day == targetDay) return data.position
                }
            }
            data.moveToNext()
        }
        return -1
    }

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

    private fun showMemberPopupMenu(view: View, position: Int, id: Long) {
        listItemPosition = position
        listItemId = id
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.lidmaatlist_menu, popup.menu)
        popup.forceShowIcons()
        val cursor = cursorAdapter.getItem(id.toInt()) as? Cursor ?: return
        cursor.moveToPosition(position)
        configurePopupMenu(popup, cursor)
        popup.show()
        popup.setOnMenuItemClickListener { item -> handlePopupMenuClick(item, cursor) }
    }

    private fun configurePopupMenu(popup: PopupMenu, cursor: Cursor) {
        val nameIdx = cursor.getColumnIndex(winkerkEntry.LIDMATE_NOEMNAAM)
        val surnameIdx = cursor.getColumnIndex(winkerkEntry.LIDMATE_VAN)
        val cellIdx = cursor.getColumnIndex(winkerkEntry.LIDMATE_SELFOON)
        val phoneIdx = cursor.getColumnIndex(winkerkEntry.ADRESSE_LANDLYN)
        val emailIdx = cursor.getColumnIndex(winkerkEntry.LIDMATE_EPOS)

        val name = cursor.getString(nameIdx)
        val surname = cursor.getString(surnameIdx)
        val menu = popup.menu

        menu.findItem(R.id.kyk_lidmaat_detail).title = "Detail van $name $surname"
        menu.findItem(R.id.submenu_bel).title = "Skakel $name"
        menu.findItem(R.id.submenu_teks).title = "Teks $name"
        menu.findItem(R.id.submenu_ander).title = name

        if (cursor.isNull(cellIdx)) {
            safeRemoveMenuItem(menu, R.id.submenu_bel, R.id.bel_selfoon)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_sms)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp2)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp3)
        } else {
            val cellNumber = cursor.getString(cellIdx)
            menu.findItem(R.id.bel_selfoon).title = "Skakel $cellNumber"
            menu.findItem(R.id.stuur_sms).title = "SMS na $cellNumber"
        }

        if (cursor.isNull(phoneIdx)) {
            safeRemoveMenuItem(menu, R.id.submenu_bel, R.id.bel_landlyn)
        } else {
            val phoneNumber = cursor.getString(phoneIdx)
            menu.findItem(R.id.bel_landlyn).title = "Skakel $phoneNumber"
        }

        if (cursor.isNull(emailIdx) || cursor.getString(emailIdx).isNullOrEmpty()) {
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_epos)
        }

        if (!winkerkEntry.WHATSAPP1) safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp)
        if (!winkerkEntry.WHATSAPP2) safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp2)
        if (!winkerkEntry.WHATSAPP3) safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp3)
    }

    private fun safeRemoveMenuItem(menu: Menu, submenuId: Int, itemId: Int) {
        val submenu = menu.findItem(submenuId)
        if (submenu?.hasSubMenu() == true) {
            submenu.subMenu?.removeItem(itemId)
        }
    }

    private fun handlePopupMenuClick(item: MenuItem, cursor: Cursor): Boolean {
        return MemberActionHandler(this, cursor).handleAction(item.itemId)
    }

    private fun showGroupFunctionMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.groepfunksie_menu, popup.menu)
        popup.forceShowIcons()
        val cursor = cursorAdapter.cursor ?: return
        if (cursor.count < 1) return
        val counts = calculateMemberCounts(cursor)
        val totalCount = counts[0]
        val selectedCount = counts[1]

        popup.menu.findItem(R.id.sms_groep).title = "Almal ($totalCount)"
        popup.menu.findItem(R.id.sms_selected).title = "Geselekteerdes ($selectedCount)"
        popup.menu.findItem(R.id.almal_in_groep).title = "Almal ($totalCount)"
        popup.menu.findItem(R.id.selected_in_groep).title = "Geselekteerdes ($selectedCount)"

        popup.show()
        popup.setOnMenuItemClickListener { handleGroupMenuClick(it, cursor) }
    }

    private fun calculateMemberCounts(cursor: Cursor): IntArray {
        cursor.moveToFirst()
        val totalCount = cursor.count
        var selectedCount = 0
        val tagIdx = cursor.getColumnIndex(winkerkEntry.LIDMATE_TAG)
        if (tagIdx != -1) {
            repeat(totalCount) {
                if (cursor.getInt(tagIdx) == 1) selectedCount++
                cursor.moveToNext()
            }
        }
        return intArrayOf(totalCount, selectedCount)
    }

    private fun handleGroupMenuClick(item: MenuItem, cursor: Cursor): Boolean {
        if (cursor.count < 1) return false
        // SMS sending not yet implemented – collector collects members but not used yet
        // val collector = GroupMemberCollector(cursor)
        // val smsList = when (item.itemId) {
        //     R.id.sms_groep, R.id.almal_in_groep -> collector.collectAllMembers()
        //     R.id.sms_selected, R.id.selected_in_groep -> collector.collectSelectedMembers()
        //     else -> arrayListOf()
        // }
        // TODO: handle smsList
        return true
    }

    private fun setDatabaseDate() {
        try {
            SQLiteAssetHelper(this, winkerkEntry.WINKERK_DB, null, 1).use { helper ->
                helper.readableDatabase.use { db ->
                    db.rawQuery("SELECT * FROM Datum", null).use { cursor ->
                        if (cursor.moveToFirst()) {
                            val dateIdx = cursor.getColumnIndex("DataDatum")
                            winkerkEntry.DATA_DATUM = if (dateIdx != -1) cursor.getString(dateIdx) ?: "" else ""
                        } else {
                            winkerkEntry.DATA_DATUM = ""
                        }
                    }
                }
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "Error reading database date", e)
            winkerkEntry.DATA_DATUM = ""
        }
    }

    private fun setChurchInfo() {
        val query = "SELECT DISTINCT Members._rowid_ as _id, Gemeente, [Gemeente epos] FROM Members GROUP BY Gemeente, [Gemeente epos]"
        try {
            SQLiteAssetHelper(this, winkerkEntry.WINKERK_DB, null, 1).use { helper ->
                helper.readableDatabase.use { db ->
                    db.rawQuery(query, null).use { cursor ->
                        winkerkEntry.GEMEENTE_NAAM = ""
                        winkerkEntry.GEMEENTE_EPOS = ""
                        winkerkEntry.GEMEENTE2_NAAM = ""
                        winkerkEntry.GEMEENTE2_EPOS = ""
                        winkerkEntry.GEMEENTE3_NAAM = ""
                        winkerkEntry.GEMEENTE3_EPOS = ""

                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex("Gemeente")
                            val emailIdx = cursor.getColumnIndex("Gemeente epos")
                            if (nameIdx != -1 && emailIdx != -1) {
                                winkerkEntry.GEMEENTE_NAAM = cursor.getString(nameIdx) ?: ""
                                winkerkEntry.GEMEENTE_EPOS = cursor.getString(emailIdx) ?: ""
                                if (cursor.moveToNext()) {
                                    winkerkEntry.GEMEENTE2_NAAM = cursor.getString(nameIdx) ?: ""
                                    winkerkEntry.GEMEENTE2_EPOS = cursor.getString(emailIdx) ?: ""
                                    if (cursor.moveToNext()) {
                                        winkerkEntry.GEMEENTE3_NAAM = cursor.getString(nameIdx) ?: ""
                                        winkerkEntry.GEMEENTE3_EPOS = cursor.getString(emailIdx) ?: ""
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
            winkerkEntry.SORTORDER = winkerkEntry.DEFLAYOUT
            winkerkEntry.SOEKLIST = false
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
                searchRunnable = Runnable {
                    performSearch(newText)
                }
                searchHandler.postDelayed(searchRunnable!!, 300)
                return true
            }
        })
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            winkerkEntry.SORTORDER = winkerkEntry.DEFLAYOUT
            winkerkEntry.SOEKLIST = false
            searchItemBlock.visibility = View.GONE
            observeDataset()
        } else {
            searchItemBlock.visibility = View.VISIBLE
            val searchText = if (winkerkEntry.RECORDSTATUS == "2") "Onaktief $query" else query
            searchTextView.text = searchText
            winkerkEntry.SOEK = query.trim()
            winkerkEntry.DEFLAYOUT = "SOEK_DATA"
            winkerkEntry.SOEKLIST = true
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
            winkerkEntry.SORTORDER = winkerkEntry.DEFLAYOUT
            winkerkEntry.SOEKLIST = false
            try {
                cursorAdapter.swapCursor(null)
            } finally {
                observeDataset()
            }
            return
        }
        when (requestCode) {
            SEARCH_LIST_REQUEST -> {
                val extras = data.extras
                if (extras != null) {
                    val list = extras.getParcelableArrayList<SearchCheckBox>(SEARCH_CHECK_BOX)
                    if (list != null) {
                        searchList = list
                    }
                }
            }
            FILTER_LIST_REQUEST -> {
                val extras = data.extras
                if (extras != null) {
                    val list = extras.getParcelableArrayList<FilterBox>(FILTER_CHECK_BOX)
                    if (list != null) {
                        filterList = list
                        winkerkEntry.SORTORDER = "Filter"
                        winkerkEntry.DEFLAYOUT = "FILTER_DATA"
                        winkerkEntry.SOEKLIST = false
                        observeDataset()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        isLoadingWhatsAppContacts.set(false)
        whatsAppContactsLoaded.set(false)
        cursorAdapter.swapCursor(null)
        backgroundExecutor.shutdown()
        super.onDestroy()
    }

    private inner class SwipeGestureDetector : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_MIN_DISTANCE = 120
        private val SWIPE_MAX_OFF_PATH = 200
        private val SWIPE_THRESHOLD_VELOCITY = 200

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            try {
                if (e1 == null) return false
                val diffAbs = Math.abs(e1.y - e2.y)
                val diff = e1.x - e2.x
                if (diffAbs > SWIPE_MAX_OFF_PATH) return false
                when {
                    diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY -> onLeftSwipe()
                    -diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY -> onRightSwipe()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error on gestures", e)
            }
            return false
        }
    }

    private fun onLeftSwipe() {
        NavigationHandler.handleLeftSwipe(this, sortOrderView)
    }

    private fun onRightSwipe() {
        NavigationHandler.handleRightSwipe(this, sortOrderView)
    }


}