package za.co.jpsoft.winkerkreader.ui.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateViewModelFactory
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding
import za.co.jpsoft.winkerkreader.services.CallMonitoringService
import za.co.jpsoft.winkerkreader.ui.bottomsheets.FilterBottomSheet
import za.co.jpsoft.winkerkreader.ui.controllers.MainActivityInitializer
import za.co.jpsoft.winkerkreader.ui.controllers.MainSearchFilterCoordinator
import za.co.jpsoft.winkerkreader.ui.controllers.SortOrderController
import za.co.jpsoft.winkerkreader.ui.viewmodels.MainViewModel
import za.co.jpsoft.winkerkreader.utils.*
import za.co.jpsoft.winkerkreader.workers.PastoralBackupWorker

/**
 * The main container Activity of the application.
 *
 * This class is now a thin coordinator that delegates all heavy lifting to
 * [MainActivityInitializer] and various controllers.
 */
class MainActivity : AuthBaseActivity() {

    // ─── View Binding ─────────────────────────────────────────────────────────

    lateinit var binding: ActivityMainBinding

    // ─── ViewModels ──────────────────────────────────────────────────────────

    val mainViewModel: MainViewModel by viewModels(
        factoryProducer = { SavedStateViewModelFactory(application, this, intent?.extras) }
    )

    // ─── Controllers (initialised by initializer) ──────────────────────────

    private lateinit var initializer: MainActivityInitializer

    // Expose the coordinator to fragments/bottom sheets
    val searchFilterCoordinator: MainSearchFilterCoordinator
        get() = initializer.searchFilterCoordinator

    val sortController: SortOrderController
        get() = initializer.sortController

    // Exposed for use in the initializer and other callbacks
    lateinit var settingsManager: SettingsManager
        private set

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager.getInstance(this)

        // Set up backup worker before inflating
        val dailyEnabled = settingsManager.backup.dailyBackupEnabled
        val exportToDownloads = settingsManager.backup.backupExportToDownloads
        if (dailyEnabled) {
            PastoralBackupWorker.schedule(this, exportToDownloads)
        } else {
            PastoralBackupWorker.cancel(this)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.lidmaatList) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        // Create and run the initializer
        initializer = MainActivityInitializer(this, savedInstanceState)
        initializer.setupPreAuth()
    }

    override fun onStart() {
        super.onStart()
        if (settingsManager.callMonitor.callLogEnabled) {
            BatteryOptimizationHelper.showBatteryOptimizationDialog(this)
        }
    }

    override fun onResumeAfterAuth() {
        // Delegate to initializer (only when fully ready)
        if (::initializer.isInitialized) {
            initializer.onResumeAfterAuth()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::initializer.isInitialized) {
            initializer.onPause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::initializer.isInitialized) {
            initializer.onDestroy()
        }
        WhatsAppContactLoader.reset()
    }

    // ─── Options Menu ────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return if (::initializer.isInitialized && initializer.isReady) {
            initializer.menuController.onCreateOptionsMenu(menu)
        } else {
            false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!::initializer.isInitialized || !initializer.isReady) {
            return super.onOptionsItemSelected(item)
        }

        if (item.itemId == R.id.filter_options) {
            FilterBottomSheet().show(supportFragmentManager, "filter")
            return true
        }

        return MenuItemHandler(
            this,
            settingsManager,
            initializer.viewModel,
            MainNavigationController(this),
            onSortOrderChanged = { sortOrder ->
                // The initializer owns sortController, but we can expose it or let the handler call it.
                // For simplicity, we delegate to the initializer's internal method.
                // We could add a method in initializer to update sort.
                // However, MenuItemHandler is constructed with a callback; we need to call initializer's sortController.update.
                // Let's keep the callback as a lambda that invokes sortController.update.
                // To avoid exposing sortController, we can add a method in initializer.
                // For now, we can retrieve sortController via a getter if needed.
                // Since we don't have a getter, we can store a reference or add a method.
                // Let's add a method in initializer: updateSortOrder(sortOrder)
                initializer.updateSortOrder(sortOrder)
            }
        ).handleMenuItem(item) || super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (!::initializer.isInitialized || !initializer.isReady) return false
        // The badge count is handled by PastoralReminderBadgeController, which updates via invalidateOptionsMenu
        return super.onPrepareOptionsMenu(menu)
    }

    // ─── Touch Events (swipe) ───────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (::initializer.isInitialized && initializer.isReady) {
            initializer.swipeGestureController.onTouchEvent(event) || super.onTouchEvent(event)
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::initializer.isInitialized && initializer.isReady) {
            initializer.swipeGestureController.handleTouchEventIfOutside(
                event,
                binding.chipScrollView
            )
        }
        return super.dispatchTouchEvent(event)
    }

    // ─── Helper methods used by initializer's StartupActions ───────────────

    /**
     * Called from StartupActions to start monitoring services if enabled.
     */
    fun startMonitoringServiceIfEnabled() {
        if (settingsManager.callMonitor.autoStartEnabled && !CallMonitoringService.isServiceRunning(
                this
            )
        ) {
            try {
                val serviceIntent = Intent(this, CallMonitoringService::class.java)
                startForegroundService(serviceIntent)
            } catch (e: SecurityException) {
                if (BuildConfig.DEBUG) Log.e(
                    "MainActivity",
                    "Security exception - check permissions",
                    e
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(
                    "MainActivity",
                    "Failed to start call monitoring service",
                    e
                )
            }
        }
    }

    /**
     * Called from StartupActions to set up permissions (overlay, notification channels).
     */
    fun setupPermissions() {
        checkOverlayPermission()
        createNotificationChannel()
        PastoralNotificationHelper.ensureChannel(this)
    }

    /**
     * Called from StartupActions to initialise data (version info, search/filter lists).
     */
    fun initializeData(savedInstanceState: Bundle?) {
        setupVersionInfo()
        // Search list is already restored from savedInstanceState in initializer
        // We just need to set it on the ViewModel
        // The search list is managed by the initializer; we can access it via a getter if needed.
        // Instead, we let the initializer handle it.
    }

    /**
     * Called from StartupActions to set up event handlers (e.g., touch listeners).
     */
    fun setupEventHandlers() {
        // The initializer already sets up most listeners, but some may remain.
        // We keep this empty; all wiring is done in initializer.
    }

    /**
     * Called from StartupActions to ensure services are running.
     */
    fun ensureServicesAreRunning() {
        if (settingsManager.callMonitor.autoStartEnabled && !CallMonitoringService.isServiceRunning(
                this
            )
        ) {
            startMonitoringServiceIfEnabled()
        }
    }

    /**
     * Returns the current SearchView from the options menu.
     */
    fun findSearchView(): SearchView? {
        return if (::initializer.isInitialized) {
            initializer.menuController.findSearchView()
        } else null
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

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
            if (BuildConfig.DEBUG) Log.e("MainActivity", "Failed to get package info", e)
            supportActionBar?.title = "WinkerkReader"
        }
    }

    private fun checkOverlayPermission() {
        PermissionManager(this).requestOverlayPermissionWithRationale(this)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Oproep",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(serviceChannel)
    }

    // ─── Companion ───────────────────────────────────────────────────────────

    companion object {
        const val CHANNEL_ID = "winkerkReaderServiceChannel"
        const val SEARCH_CHECK_BOX = "SEARCH_CHECK_BOX"
        const val FILTER_CHECK_BOX = "FILTER_CHECK_BOX"
        private const val TAG = "Winkerk_MainActivity"
    }
}