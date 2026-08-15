package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.Context
import android.util.Log
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.setup.DatabaseInitializer
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding
import za.co.jpsoft.winkerkreader.utils.AppInitializer
import za.co.jpsoft.winkerkreader.utils.CallLogImporter
import za.co.jpsoft.winkerkreader.utils.permissions.PermissionManager
import za.co.jpsoft.winkerkreader.utils.prefs.BirthdaySmsPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.SyncPrefs
import za.co.jpsoft.winkerkreader.utils.ui.MainNavigationController
import za.co.jpsoft.winkerkreader.utils.work.WorkScheduler

interface StartupActions {
    fun checkAndRequestPermissions()
    fun startMonitoringServiceIfEnabled()
    fun setupViewModel()
    fun setupPermissions()
    fun initializeData()
    fun setupEventHandlers()
    fun setupAlarms()
    fun loadInitialData()
    fun ensureServicesAreRunning()
    fun openNotificationSettings()
    fun showToast(message: String)
}

class MainStartupCoordinator(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val permissionManager: PermissionManager,
    private val binding: ActivityMainBinding,
    private val actions: StartupActions,
    private val navigationController: MainNavigationController,
    private val databaseInitializer: DatabaseInitializer,
    private val syncPrefs: SyncPrefs,
    private val birthdaySmsPrefs: BirthdaySmsPrefs,
    private val memberListPrefs: MemberListPrefs,
    private val workScheduler: WorkScheduler,
    private val callLogImporter: CallLogImporter,
    private val callMonitorPrefs: CallMonitorPrefs   // <-- added
) {

    companion object {
        private const val TAG = "MainStartupCoordinator"
        private const val NOTIFICATION_PERMISSION_MESSAGE =
            "Please enable notification access for this app"
        private const val DB_INIT_FAILED_MESSAGE = "Database initialization failed"
    }

    fun runOnCreate() {
        if (!permissionManager.hasEssentialPermissions()) {
            actions.checkAndRequestPermissions()
        }
        initializeDatabaseIfNeeded()
        actions.startMonitoringServiceIfEnabled()
        actions.setupViewModel()
        actions.setupPermissions()
        actions.setupEventHandlers()
        actions.setupAlarms()
        checkNotificationAccessInBackground()
    }

    fun runOnResume() {
        if (permissionManager.isCheckOnStartEnabled() && !permissionManager.isFirstLaunch()) {
            if (!permissionManager.hasEssentialPermissions()) {
                actions.checkAndRequestPermissions()
            }
        }
        actions.ensureServicesAreRunning()
    }

    private fun initializeDatabaseIfNeeded() {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                setProgressVisible(View.VISIBLE)
            }

            AppInitializer.initialize(
                appContext = context.applicationContext,
                scope = lifecycleScope,
                onProgress = { progress ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        //binding.indeterminateBar.progress = progress
                        //binding.indeterminateBar.isIndeterminate = false
                    }
                },
                onComplete = { success ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        setProgressVisible(View.GONE)
                        if (!success) {
                            actions.showToast(DB_INIT_FAILED_MESSAGE)
                        }
                    }
                },
                onReady = {
                    lifecycleScope.launch(Dispatchers.Main) {
                        actions.loadInitialData()
                    }
                },
                churchInfoRepo = null,   // already loaded at app startup
                databaseInitializer = databaseInitializer,
                workScheduler = workScheduler,
                callLogImporter = callLogImporter,
                autoStartEnabled = callMonitorPrefs.autoStartEnabled   // <-- pass from injected pref
            )
        }
    }

    private fun checkNotificationAccessInBackground() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!permissionManager.isNotificationListenerEnabled()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Notification listener access missing")
                withContext(Dispatchers.Main) {
                    actions.showToast(NOTIFICATION_PERMISSION_MESSAGE)
                    actions.openNotificationSettings()
                }
            }
        }
    }

    private fun setProgressVisible(visibility: Int) {
        //binding.indeterminateBar.visibility = visibility
    }
}