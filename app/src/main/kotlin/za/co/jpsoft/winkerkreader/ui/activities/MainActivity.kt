package za.co.jpsoft.winkerkreader.ui.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.members.repository.ChurchInfoRepository
import za.co.jpsoft.winkerkreader.data.members.setup.DatabaseInitializer
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding
import za.co.jpsoft.winkerkreader.services.CallMonitoringService
import za.co.jpsoft.winkerkreader.ui.bottomsheets.FilterBottomSheet
import za.co.jpsoft.winkerkreader.ui.controllers.MainActivityInitializer
import za.co.jpsoft.winkerkreader.ui.controllers.MainSearchFilterCoordinator
import za.co.jpsoft.winkerkreader.ui.controllers.SortOrderController
import za.co.jpsoft.winkerkreader.ui.viewmodels.MainViewModel
import za.co.jpsoft.winkerkreader.utils.CallLogImporter
import za.co.jpsoft.winkerkreader.utils.PastoralNotificationHelper
import za.co.jpsoft.winkerkreader.utils.messaging.WhatsAppContactLoader
import za.co.jpsoft.winkerkreader.utils.permissions.PermissionManager
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs
import za.co.jpsoft.winkerkreader.utils.prefs.BackupPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.BirthdaySmsPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.PastoralPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.QuickActionPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.SyncPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.TasksPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.WidgetPrefs
import za.co.jpsoft.winkerkreader.utils.ui.MainNavigationController
import za.co.jpsoft.winkerkreader.utils.ui.MenuItemHandler
import za.co.jpsoft.winkerkreader.utils.work.BatteryOptimizationHelper
import za.co.jpsoft.winkerkreader.utils.work.WorkScheduler

@AndroidEntryPoint
class MainActivity : AuthBaseActivity() {

    // ─── Injected Preferences ──────────────────────────────────────────────
    @Inject
    lateinit var memberListPrefs: MemberListPrefs
    @Inject
    lateinit var widgetPrefs: WidgetPrefs
    @Inject
    lateinit var congregationPrefs: CongregationPrefs
    @Inject
    lateinit var callMonitorPrefs: CallMonitorPrefs
    @Inject
    lateinit var backupPrefs: BackupPrefs
    @Inject
    lateinit var appearancePrefs: AppearancePrefs
    @Inject
    lateinit var quickActionPrefs: QuickActionPrefs
    @Inject
    lateinit var pastoralPrefs: PastoralPrefs
    @Inject
    lateinit var birthdaySmsPrefs: BirthdaySmsPrefs
    @Inject
    lateinit var syncPrefs: SyncPrefs

    // REMOVED: @Inject lateinit var securityPrefs: SecurityPrefs   // now inherited from AuthBaseActivity
    @Inject
    lateinit var tasksPrefs: TasksPrefs
    @Inject
    lateinit var databaseInitializer: DatabaseInitializer
    @Inject
    lateinit var workScheduler: WorkScheduler
    @Inject
    lateinit var callLogImporter: CallLogImporter
    @Inject
    lateinit var navigationController: MainNavigationController

    @Inject
    lateinit var churchInfoRepo: ChurchInfoRepository

    // ─── View Binding ──────────────────────────────────────────────────────
    lateinit var binding: ActivityMainBinding

    // ─── ViewModels ────────────────────────────────────────────────────────
    private val mainViewModel: MainViewModel by viewModels()

    // ─── Controller ────────────────────────────────────────────────────────
    private lateinit var initializer: MainActivityInitializer

    val searchFilterCoordinator: MainSearchFilterCoordinator
        get() = initializer.searchFilterCoordinator

    val sortController: SortOrderController
        get() = initializer.sortController

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ─── Use injected prefs ──────────────────────────────────────────
        val dailyEnabled = backupPrefs.dailyBackupEnabled
        val exportToDownloads = backupPrefs.backupExportToDownloads
//        if (dailyEnabled) {
//            PastoralBackupWorker.schedule(this, exportToDownloads)
//        } else {
//            PastoralBackupWorker.cancel(this)
//        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.lidmaatList) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        // ─── Pass injected dependencies to initi
        initializer = MainActivityInitializer(
            activity = this,
            savedInstanceState = savedInstanceState,
            memberListPrefs = memberListPrefs,
            callMonitorPrefs = callMonitorPrefs,
            congregationPrefs = congregationPrefs,
            mainViewModel = mainViewModel,
            quickActionPrefs = quickActionPrefs,
            appearancePrefs = appearancePrefs,
            workScheduler = workScheduler,
            syncPrefs = syncPrefs,
            birthdaySmsPrefs = birthdaySmsPrefs,
            databaseInitializer = databaseInitializer,
            callLogImporter = callLogImporter,
            navigationController = navigationController,
            backupPrefs = backupPrefs,
            churchInfoRepo = churchInfoRepo
        )
        initializer.setupPreAuth()

        lifecycleScope.launch {
            // Use the adapter from the initializer
            val adapter = initializer.adapter as? PagingDataAdapter<*, *>
            if (adapter != null) {
                initializer.viewModel.scrollToPosition.collect { position ->
                    // Wait for the refresh load to complete (data is ready)
                    adapter.loadStateFlow
                        .filter { it.refresh is LoadState.NotLoading }
                        .first()
                    delay(50)
                    binding.lidmaatList.post {
                        (binding.lidmaatList.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(position, 0)
                        binding.lidmaatList.scrollToPosition(position)
                    }
                }
            } else {
                // Fallback (should not happen because adapter is initialized early)
                initializer.viewModel.scrollToPosition.collect { position ->
                    binding.lidmaatList.postDelayed({
                        (binding.lidmaatList.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(position, 0)
                        binding.lidmaatList.scrollToPosition(position)
                    }, 500)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (callMonitorPrefs.callLogEnabled) {
            BatteryOptimizationHelper.showBatteryOptimizationDialog(this)
        }
    }

    override fun onResumeAfterAuth() {
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
            activity = this,
            viewModel = initializer.viewModel,
            navigationController = navigationController,
            memberListPrefs = memberListPrefs,
            onSortOrderChanged = { sortOrder -> initializer.updateSortOrder(sortOrder) },
            onBirthdaySortSelected = { initializer.updateSortOrder("VERJAAR") },
            swipeActionHandler = initializer.swipeActionHandler
        ).handleMenuItem(item) || super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (!::initializer.isInitialized || !initializer.isReady) return false
        return super.onPrepareOptionsMenu(menu)
    }

    // ─── Helper methods ──────────────────────────────────────────────────────

    fun startMonitoringServiceIfEnabled() {
        if (callMonitorPrefs.autoStartEnabled && !CallMonitoringService.isServiceRunning(this)) {
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

    fun setupPermissions() {
        checkOverlayPermission()
        createNotificationChannel()
        PastoralNotificationHelper.ensureChannel(this)
    }

    fun ensureServicesAreRunning() {
        if (callMonitorPrefs.autoStartEnabled && !CallMonitoringService.isServiceRunning(this)) {
            startMonitoringServiceIfEnabled()
        }
    }

    fun findSearchView(): SearchView? {
        return if (::initializer.isInitialized) {
            initializer.menuController.findSearchView()
        } else null
    }

    // ─── Private helpers ────────────────────────────────────────────────────
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