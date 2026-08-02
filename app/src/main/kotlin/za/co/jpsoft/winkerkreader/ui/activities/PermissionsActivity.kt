package za.co.jpsoft.winkerkreader.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.ActivityPermissionsBinding
import za.co.jpsoft.winkerkreader.ui.adapters.PermissionsAdapter
import za.co.jpsoft.winkerkreader.utils.permissions.PermissionDefinitions
import za.co.jpsoft.winkerkreader.utils.permissions.PermissionItem
import za.co.jpsoft.winkerkreader.utils.permissions.PermissionManager
import za.co.jpsoft.winkerkreader.utils.permissions.PermissionRationaleHelper
import za.co.jpsoft.winkerkreader.utils.permissions.PermissionType
import za.co.jpsoft.winkerkreader.utils.ui.MainNavigationController
import za.co.jpsoft.winkerkreader.utils.work.BatteryOptimizationHelper
import za.co.jpsoft.winkerkreader.utils.work.BatteryOptimizationHelper.showBatteryOptimizationDialog

class PermissionsActivity : BaseActivity() {

    private val navigationController by lazy { MainNavigationController(this) }
    private val permissionManager by lazy { PermissionManager(this) }
    private lateinit var binding: ActivityPermissionsBinding
    private lateinit var adapter: PermissionsAdapter
    private lateinit var permissionsList: List<PermissionItem>

    private val REQUEST_CODE_RUNTIME = 100

    // ── ActivityResultLaunchers (must stay in Activity) ───────────────────────

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPermissions()
        }

    private val notificationPolicyLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPermissions()
        }

    private val runtimePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            refreshPermissions()
        }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "App Permissions"
            setDisplayHomeAsUpEnabled(true)
        }

        // Build the permission list — replaces initializePermissionsList()
        permissionsList = PermissionDefinitions.build(this)

        adapter = PermissionsAdapter(permissionsList) { item ->
            requestSpecialPermission(item)
        }
        binding.recyclerViewPermissions.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPermissions.adapter = adapter

        binding.btnRequestAllPermissions.setOnClickListener { requestAllPermissions() }

        binding.permissionCheck.apply {
            isChecked = permissionManager.isCheckOnStartEnabled()
            setOnClickListener {
                permissionManager.setCheckOnStart(isChecked)
                Toast.makeText(
                    this@PermissionsActivity,
                    if (isChecked) "Check on start enabled" else "Check on start disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        updateBatteryStatus()
        binding.tvBatteryStatus.setOnClickListener { showBatteryOptimizationDialog(this) }

        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerViewPermissions) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        updateBatteryStatus()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ── Permission requests ───────────────────────────────────────────────────

    private fun requestAllPermissions() {
        val toRequest = permissionsList
            .filter { it.type == PermissionType.RUNTIME && !it.isGranted }
            .mapNotNull { it.permission }
        if (toRequest.isNotEmpty()) {
            runtimePermissionLauncher.launch(toRequest.toTypedArray())
        } else {
            Toast.makeText(this, "Alle toestemmings is reeds gegee", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestSpecialPermission(item: PermissionItem) {
        when (item.type) {
            PermissionType.RUNTIME -> {
                item.permission?.let { perm ->
                    val (title, message) = PermissionRationaleHelper.getRationaleResIds(perm)
                    PermissionRationaleHelper.requestWithRationale(
                        this, arrayOf(perm), REQUEST_CODE_RUNTIME, title, message
                    )
                }
            }

            PermissionType.OVERLAY ->
                if (!android.provider.Settings.canDrawOverlays(this))
                    navigationController.navigateToOverlaySettings()
                else Toast.makeText(this, "Toestemming reeds gegee", Toast.LENGTH_SHORT).show()

            PermissionType.EXACT_ALARM ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                    navigationController.navigateToExactAlarmSettings()

            PermissionType.NOTIFICATION_POLICY ->
                navigationController.navigateToNotificationPolicySettings()

            PermissionType.NOTIFICATION_LISTENER ->
                navigationController.navigateToNotificationListenerSettings()
        }
    }

    private fun refreshPermissions() {
        permissionsList.forEach { it.updateStatus(this) }
        adapter.notifyDataSetChanged()
    }

    fun updateBatteryStatus() {
        val isIgnoring = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)
        binding.tvBatteryStatus.text = if (isIgnoring)
            "🔋 ${getString(R.string.battery_optimization_disabled)}"
        else
            "🪫 ${getString(R.string.battery_optimization_enabled)}"
    }
}