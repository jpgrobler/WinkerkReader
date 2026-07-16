package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.Context
import android.util.Log
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding
import za.co.jpsoft.winkerkreader.utils.AppInitializer
import za.co.jpsoft.winkerkreader.utils.MainNavigationController
import za.co.jpsoft.winkerkreader.utils.PermissionManager
import za.co.jpsoft.winkerkreader.utils.SettingsManager

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

    // fun isNotificationAccessEnabled(): Boolean
    fun openNotificationSettings()
    fun showToast(message: String)
}

class MainStartupCoordinator(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val settingsManager: SettingsManager,
    private val permissionManager: PermissionManager,
    private val binding: ActivityMainBinding,
    private val actions: StartupActions,
    private val navigationController: MainNavigationController
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
                lifecycleScope = lifecycleScope,
                onProgress = { progress ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.indeterminateBar.progress = progress
                        binding.indeterminateBar.isIndeterminate = false
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
                }
            )
        }
    }

    private fun checkNotificationAccessInBackground() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!permissionManager.isNotificationListenerEnabled()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Notification listener access missing")
                withContext(Dispatchers.Main) {
                    actions.showToast(NOTIFICATION_PERMISSION_MESSAGE)
                    actions.openNotificationSettings()  // Now uses navigationController
                }
            }
        }
    }

    private fun setProgressVisible(visibility: Int) {
        binding.indeterminateBar.visibility = visibility
    }
}