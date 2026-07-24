package za.co.jpsoft.winkerkreader.ui.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.ActivityPermissionsBinding
import za.co.jpsoft.winkerkreader.databinding.ItemPermissionBinding
import za.co.jpsoft.winkerkreader.utils.BatteryOptimizationHelper
import za.co.jpsoft.winkerkreader.utils.BatteryOptimizationHelper.showBatteryOptimizationDialog
import za.co.jpsoft.winkerkreader.utils.MainNavigationController
import za.co.jpsoft.winkerkreader.utils.PermissionManager
import za.co.jpsoft.winkerkreader.utils.PermissionRationaleHelper

class PermissionsActivity : BaseActivity() {
    // ✅ Only ONE declaration - keep this one
    private val navigationController by lazy { MainNavigationController(this) }
    private val permissionManager by lazy { PermissionManager(this) }
    private lateinit var binding: ActivityPermissionsBinding
    private lateinit var adapter: PermissionsAdapter
    private lateinit var permissionsList: List<PermissionItem>

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

    // Request code for individual runtime permission requests
    private val REQUEST_CODE_RUNTIME = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "App Permissions"
            setDisplayHomeAsUpEnabled(true)
        }

        binding.recyclerViewPermissions.layoutManager = LinearLayoutManager(this)

        initializePermissionsList()
        adapter = PermissionsAdapter(permissionsList)
        binding.recyclerViewPermissions.adapter = adapter

        binding.btnRequestAllPermissions.setOnClickListener {
            requestAllPermissions()
        }

        // Setup "Check on start" checkbox
        binding.permissionCheck.apply {
            // Set initial state from PermissionManager
            isChecked = permissionManager.isCheckOnStartEnabled()

            setOnClickListener {
                permissionManager.setCheckOnStart(isChecked)
                // Optional: show a toast to confirm
                Toast.makeText(
                    this@PermissionsActivity,
                    if (isChecked) "Check on start enabled" else "Check on start disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        updateBatteryStatus()
        binding.tvBatteryStatus.setOnClickListener {
            showBatteryOptimizationDialog(this)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerViewPermissions) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }
    }

    fun updateBatteryStatus() {
        val isIgnoring = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)
        binding.tvBatteryStatus.text = if (isIgnoring) {
            "🔋 ${getString(R.string.battery_optimization_disabled)}"
        } else {
            "🪫 ${getString(R.string.battery_optimization_enabled)}"
        }
    }

    private fun initializePermissionsList() {
        permissionsList = buildList {
            // Exact Alarm permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(
                    PermissionItem(
                        "Alarms",
                        "Maak dit moontlik dat app jou kan herinner op sekere tye",
                        null,
                        PermissionType.EXACT_ALARM
                    )
                )
            }

            // Notification permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionItem(
                        "Notifications",
                        "Wys Kennisgewings",
                        Manifest.permission.POST_NOTIFICATIONS,
                        PermissionType.RUNTIME
                    )
                )
            }

            // Notification Policy Access
            add(
                PermissionItem(
                    "Do Not Disturb Access",
                    "Laat app toe om beleid te lees",
                    null,
                    PermissionType.NOTIFICATION_POLICY
                )
            )

            // Phone permissions
            add(
                PermissionItem(
                    "Phone State",
                    "Laat app toe om inkomende nommer op te soek teen gemeente data",
                    Manifest.permission.READ_PHONE_STATE,
                    PermissionType.RUNTIME
                )
            )
            add(
                PermissionItem(
                    "Call Log",
                    "Laat app toe om nommer op te soek teen gemeente data",
                    Manifest.permission.READ_CALL_LOG,
                    PermissionType.RUNTIME
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionItem(
                        "Phone Numbers",
                        "Laat app toe om inkomende nommer op te soek teen gemeente data",
                        Manifest.permission.READ_PHONE_NUMBERS,
                        PermissionType.RUNTIME
                    )
                )
            }

            // SMS permissions
            add(
                PermissionItem(
                    "Send SMS",
                    "Laat app toe om SMS te stuur",
                    Manifest.permission.SEND_SMS,
                    PermissionType.RUNTIME
                )
            )
            add(
                PermissionItem(
                    "Read SMS",
                    "Laat app toe om SMS'e te lees",
                    Manifest.permission.READ_SMS,
                    PermissionType.RUNTIME
                )
            )

            // Contacts permissions
            add(
                PermissionItem(
                    "Read Contacts",
                    "Laat app toe om jou foon se kontakte te lees",
                    Manifest.permission.READ_CONTACTS,
                    PermissionType.RUNTIME
                )
            )
            add(
                PermissionItem(
                    "Write Contacts",
                    "Laat app toe om kontak by te voeg op jou foon",
                    Manifest.permission.WRITE_CONTACTS,
                    PermissionType.RUNTIME
                )
            )

            // Calendar permissions
            add(
                PermissionItem(
                    "Read Calendar",
                    "Laat app toe om kalender te lees",
                    Manifest.permission.READ_CALENDAR,
                    PermissionType.RUNTIME
                )
            )
            add(
                PermissionItem(
                    "Write Calendar",
                    "Laat app toe om veranderinge aan jou kalender te maak",
                    Manifest.permission.WRITE_CALENDAR,
                    PermissionType.RUNTIME
                )
            )

            // System overlay permission
            add(
                PermissionItem(
                    "Display over other apps",
                    "Toestemming om bo oor ander apps te wys",
                    null,
                    PermissionType.OVERLAY
                )
            )

            // Notification Listener
            add(
                PermissionItem(
                    "Notification Access",
                    "Luister na kennisgewings (vir VOIP oproepe bv. Whatsapp)",
                    null,
                    PermissionType.NOTIFICATION_LISTENER
                )
            )
        }
    }

    private fun requestAllPermissions() {
        val permissionsToRequest = permissionsList
            .filter { it.type == PermissionType.RUNTIME && !it.isGranted }
            .mapNotNull { it.permission }

        if (permissionsToRequest.isNotEmpty()) {
            runtimePermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Toast.makeText(this, "Alle toestemmings is reeds gegee", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestSpecialPermission(item: PermissionItem) {
        when (item.type) {
            PermissionType.RUNTIME -> {
                item.permission?.let {
                    PermissionRationaleHelper.requestWithRationale(
                        this,
                        arrayOf(it),
                        REQUEST_CODE_RUNTIME,
                        getRationaleTitle(it),
                        getRationaleMessage(it)
                    )
                }
            }

            // ✅ These now use the single navigationController instance
            PermissionType.OVERLAY -> {
                if (!Settings.canDrawOverlays(this)) {
                    navigationController.navigateToOverlaySettings()
                } else {
                    Toast.makeText(this, "Toestemming reeds gegee", Toast.LENGTH_SHORT).show()
                }
            }

            PermissionType.EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    navigationController.navigateToExactAlarmSettings()
                }
            }

            PermissionType.NOTIFICATION_POLICY -> {
                navigationController.navigateToNotificationPolicySettings()
            }

            PermissionType.NOTIFICATION_LISTENER -> {
                navigationController.navigateToNotificationListenerSettings()
            }
        }
    }

    // ❌ REMOVE this duplicate declaration - it's already at the top of the class
    // private val navigationController by lazy { MainNavigationController(this) }

    // ❌ REMOVE these duplicate methods - they're already defined above
    // private fun requestOverlayPermission() { ... }
    // private fun requestExactAlarmPermission() { ... }
    // private fun requestNotificationPolicyAccess() { ... }
    // private fun requestNotificationListenerAccess() { ... }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        updateBatteryStatus()
    }

    private fun refreshPermissions() {
        permissionsList.forEach { it.updateStatus(this) }
        adapter.notifyDataSetChanged()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ------------------------------------------------------------------------
    // Rationale title & message helpers
    // ------------------------------------------------------------------------

    private fun getRationaleTitle(permission: String): Int {
        return when (permission) {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_NUMBERS -> R.string.rationale_phone_title

            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS -> R.string.rationale_contacts_title

            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS -> R.string.rationale_sms_title

            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR -> R.string.rationale_calendar_title

            Manifest.permission.POST_NOTIFICATIONS -> R.string.rationale_notifications_title
            Manifest.permission.SCHEDULE_EXACT_ALARM -> R.string.rationale_exact_alarm_title
            else -> R.string.rationale_generic_title
        }
    }

    private fun getRationaleMessage(permission: String): Int {
        return when (permission) {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_NUMBERS -> R.string.rationale_phone_message

            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS -> R.string.rationale_contacts_message

            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS -> R.string.rationale_sms_message

            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR -> R.string.rationale_calendar_message

            Manifest.permission.POST_NOTIFICATIONS -> R.string.rationale_notifications_message
            Manifest.permission.SCHEDULE_EXACT_ALARM -> R.string.rationale_exact_alarm_message
            else -> R.string.rationale_generic_message
        }
    }

    // ------------------------------------------------------------------------
    // Inner classes
    // ------------------------------------------------------------------------

    enum class PermissionType {
        RUNTIME, OVERLAY, EXACT_ALARM, NOTIFICATION_POLICY, NOTIFICATION_LISTENER
    }

    inner class PermissionItem(
        val name: String,
        val description: String,
        val permission: String? = null,
        val type: PermissionType
    ) {
        var isGranted: Boolean = false
            private set

        init {
            updateStatus(this@PermissionsActivity)
        }

        fun updateStatus(activity: PermissionsActivity) {
            isGranted = when (type) {
                PermissionType.RUNTIME -> {
                    permission != null && ContextCompat.checkSelfPermission(
                        activity,
                        permission
                    ) == PackageManager.PERMISSION_GRANTED
                }

                PermissionType.OVERLAY -> Settings.canDrawOverlays(activity)
                PermissionType.EXACT_ALARM -> {
                    // Check if exact alarm permission is granted
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val alarmManager =
                            getSystemService(ALARM_SERVICE) as android.app.AlarmManager
                        alarmManager.canScheduleExactAlarms()
                    } else true
                }

                PermissionType.NOTIFICATION_POLICY -> {
                    val manager =
                        activity.getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.isNotificationPolicyAccessGranted
                }

                PermissionType.NOTIFICATION_LISTENER -> {
                    NotificationManagerCompat.getEnabledListenerPackages(activity)
                        .contains(activity.packageName)
                }
            }
        }
    }

    inner class PermissionsAdapter(private val items: List<PermissionItem>) :
        RecyclerView.Adapter<PermissionsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPermissionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val itemBinding: ItemPermissionBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: PermissionItem) {
                itemBinding.tvPermissionName.text = item.name
                itemBinding.tvPermissionDescription.text = item.description

                if (item.isGranted) {
                    itemBinding.ivPermissionStatus.setImageResource(android.R.drawable.checkbox_on_background)
                    itemBinding.ivPermissionStatus.setColorFilter(
                        ContextCompat.getColor(
                            this@PermissionsActivity,
                            android.R.color.holo_green_dark
                        )
                    )
                    itemBinding.btnRequestPermission.isEnabled = false
                    itemBinding.btnRequestPermission.setText(R.string.permission_granted)
                } else {
                    itemBinding.ivPermissionStatus.setImageResource(android.R.drawable.ic_delete)
                    itemBinding.ivPermissionStatus.setColorFilter(
                        ContextCompat.getColor(
                            this@PermissionsActivity,
                            android.R.color.holo_red_dark
                        )
                    )
                    itemBinding.btnRequestPermission.isEnabled = true
                    itemBinding.btnRequestPermission.setText(R.string.permission_request)
                }

                itemBinding.btnRequestPermission.setOnClickListener {
                    if (!item.isGranted) {
                        requestSpecialPermission(item)
                    }
                }

                itemBinding.root.setOnClickListener {
                    if (!item.isGranted) {
                        requestSpecialPermission(item)
                    } else {
                        Toast.makeText(
                            this@PermissionsActivity,
                            "${item.name} reeds toestemming ontvang.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}