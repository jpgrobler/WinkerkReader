package za.co.jpsoft.winkerkreader.ui.activities

import za.co.jpsoft.winkerkreader.WinkerkReader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.databinding.ActivityPermissionsBinding
import za.co.jpsoft.winkerkreader.databinding.ItemPermissionBinding

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding
    private lateinit var adapter: PermissionsAdapter
    private lateinit var permissionsList: List<PermissionItem>

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshPermissions()
    }

    private val notificationPolicyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshPermissions()
    }

    private val runtimePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        refreshPermissions()
    }

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
    }

    private fun initializePermissionsList() {
        permissionsList = buildList {
            // Exact Alarm permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(PermissionItem(
                    "Alarms",
                    "Maak dit moontlik dat app jou kan herinner op sekere tye",
                    null,
                    PermissionType.EXACT_ALARM
                ))
            }

            // Notification permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionItem(
                    "Notifications",
                    "Wys Kennisgewings",
                    Manifest.permission.POST_NOTIFICATIONS,
                    PermissionType.RUNTIME
                ))
            }

            // Notification Policy Access
            add(PermissionItem(
                "Do Not Disturb Access",
                "Laat app toe om beleid te lees",
                null,
                PermissionType.NOTIFICATION_POLICY
            ))

            // Phone permissions
            add(PermissionItem(
                "Phone State",
                "Laat app toe om inkomende nommer op te soek teen gemeente data",
                Manifest.permission.READ_PHONE_STATE,
                PermissionType.RUNTIME
            ))
            add(PermissionItem(
                "Call Log",
                "Laat app toe om nommer op te soek teen gemeente data",
                Manifest.permission.READ_CALL_LOG,
                PermissionType.RUNTIME
            ))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionItem(
                    "Phone Numbers",
                    "Laat app toe om inkomende nommer op te soek teen gemeente data",
                    Manifest.permission.READ_PHONE_NUMBERS,
                    PermissionType.RUNTIME
                ))
            }

            // SMS permissions
            add(PermissionItem(
                "Send SMS",
                "Laat app toe om SMS te stuur",
                Manifest.permission.SEND_SMS,
                PermissionType.RUNTIME
            ))
            add(PermissionItem(
                "Read SMS",
                "Laat app toe om SMS'e te lees",
                Manifest.permission.READ_SMS,
                PermissionType.RUNTIME
            ))

            // Contacts permissions
            add(PermissionItem(
                "Read Contacts",
                "Laat app toe om jou foon se kontakte te lees",
                Manifest.permission.READ_CONTACTS,
                PermissionType.RUNTIME
            ))
            add(PermissionItem(
                "Write Contacts",
                "Laat app toe om kontak by te voeg op jou foon",
                Manifest.permission.WRITE_CONTACTS,
                PermissionType.RUNTIME
            ))

            // Calendar permissions
            add(PermissionItem(
                "Read Calendar",
                "Laat app toe om kalender te lees",
                Manifest.permission.READ_CALENDAR,
                PermissionType.RUNTIME
            ))
            add(PermissionItem(
                "Write Calendar",
                "Laat app toe om veranderinge aan jou kalender te maak",
                Manifest.permission.WRITE_CALENDAR,
                PermissionType.RUNTIME
            ))

            // System overlay permission
            add(PermissionItem(
                "Display over other apps",
                "Toestemming om bo oor ander apps te wys",
                null,
                PermissionType.OVERLAY
            ))

            // Notification Listener
            add(PermissionItem(
                "Notification Access",
                "Luister na kennisgewings (vir VOIP oproepe bv. Whatsapp)",
                null,
                PermissionType.NOTIFICATION_LISTENER
            ))
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
            PermissionType.OVERLAY -> requestOverlayPermission()
            PermissionType.EXACT_ALARM -> requestExactAlarmPermission()
            PermissionType.NOTIFICATION_POLICY -> requestNotificationPolicyAccess()
            PermissionType.NOTIFICATION_LISTENER -> requestNotificationListenerAccess()
            PermissionType.RUNTIME -> {
                item.permission?.let {
                    runtimePermissionLauncher.launch(arrayOf(it))
                }
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Toestemming reeds gegee", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun requestNotificationPolicyAccess() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            notificationPolicyLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Toestemming reeds gegee", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationListenerAccess() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
        AlertDialog.Builder(this)
            .setTitle("Enable Notification Access")
            .setMessage("Gee asb vir WinkerkReader Notification Access om VOIP oproepe te monitor.")
            .setPositiveButton("OK", null)
            .show()
    }


    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        permissionsList.forEach { it.updateStatus(this) }
        adapter.notifyDataSetChanged()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    // Inner classes
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
                    permission != null && ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
                }
                PermissionType.OVERLAY -> Settings.canDrawOverlays(activity)
                PermissionType.EXACT_ALARM -> true // assume granted for simplicity
                PermissionType.NOTIFICATION_POLICY -> {
                    val manager = activity.getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
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
                    itemBinding.ivPermissionStatus.setColorFilter(ContextCompat.getColor(this@PermissionsActivity, android.R.color.holo_green_dark))
                    itemBinding.btnRequestPermission.isEnabled = false
                    itemBinding.btnRequestPermission.text = "Granted"
                } else {
                    itemBinding.ivPermissionStatus.setImageResource(android.R.drawable.ic_delete)
                    itemBinding.ivPermissionStatus.setColorFilter(ContextCompat.getColor(this@PermissionsActivity, android.R.color.holo_red_dark))
                    itemBinding.btnRequestPermission.isEnabled = true
                    itemBinding.btnRequestPermission.text = "Request"
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