package za.co.jpsoft.winkerkreader

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_TAG

class MenuItemHandler(
    private val activity: AppCompatActivity,
    private val settings: SettingsManager
) {
    private lateinit var permissionManager: PermissionManager

    fun handleMenuItem(item: MenuItem): Boolean {
        // Initialize permission manager (needed for permission dialogs)
        permissionManager = PermissionManager(activity)

        val sortOrderView = activity.findViewById<TextView>(R.id.sortorder)

        return when (item.itemId) {
            R.id.select_options -> handleSelectOptions()
            R.id.tagged -> handleTagged(sortOrderView)
            R.id.sort_van -> handleSortVan(sortOrderView)
            R.id.sort_wyk -> handleSortWyk(sortOrderView)
            R.id.sort_ouderdom -> handleSortOuderdom(sortOrderView)
            R.id.verjaar -> handleVerjaar(sortOrderView)
            R.id.sort_adres -> handleSortAdres(sortOrderView)
            R.id.sort_gesin -> handleSortGesin(sortOrderView)
            R.id.registreer -> handleRegistreer()
            R.id.laai -> handleLaai()
            R.id.sms_verjaar -> handleSmsVerjaar()
            R.id.filter_options -> handleFilterOptions()
            R.id.deselect -> handleDeselect()
            R.id.opdateerApp -> handleUpdateApp()
            R.id.uitleg -> handleUitleg()
            R.id.argief -> handleArgief()
            R.id.action_view_call_log -> handleViewCallLog()
            R.id.menu_permissions -> handlePermissions()
            R.id.menu_permission_settings -> {
                showPermissionSettingsDialog()
                true
            }
            else -> false
        }
    }

    fun handlePermissions(): Boolean {
        val intent = Intent(activity, PermissionsActivity::class.java)
        activity.startActivity(intent)
        return true
    }

    private fun handleViewCallLog(): Boolean {
        val intent = Intent(activity, CallLogActivity::class.java)
        activity.startActivity(intent)
        return true
    }

    private fun handleSelectOptions(): Boolean {
        val intent = Intent(activity, MyActivity_Settings::class.java)
        activity.startActivity(intent)
        return true
    }

    private fun handleTagged(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER.apply { "VAN" }
        winkerkEntry.SOEKLIST = false
        (activity as MainActivity2).observeDataset()
        return true
    }

    private fun handleSortVan(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER.apply { "VAN" }
        winkerkEntry.SOEKLIST = false
        (activity as MainActivity2).observeDataset()
        return true
    }

    private fun handleSortWyk(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER.apply { "WYK" }
        winkerkEntry.SOEKLIST = false
        (activity as MainActivity2).observeDataset()
        return true
    }

    private fun handleSortOuderdom(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER.apply { "OUDERDOM" }
        winkerkEntry.SOEKLIST = false
        (activity as MainActivity2).observeDataset()
        return true
    }

    private fun handleVerjaar(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER.apply { "VERJAAR" }
        winkerkEntry.SOEKLIST = false
        (activity as MainActivity2).observeDataset()
        return true
    }

    private fun handleSortAdres(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER.apply { "ADRES" }
        winkerkEntry.SOEKLIST = false
        (activity as MainActivity2).observeDataset()
        return true
    }

    private fun handleSortGesin(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER.apply { "GESINNE" }
        winkerkEntry.SOEKLIST = false
        (activity as MainActivity2).observeDataset()
        return true
    }

    private fun handleRegistreer(): Boolean {
        val intent = Intent(activity, registreer::class.java)
        activity.startActivity(intent)
        return true
    }

    private fun checkAndRequestStoragePermissions(): Boolean {
        if (PermissionHelper.arePermissionsGranted(activity, PermissionHelper.STORAGE_PERMISSIONS)) {
            return true
        } else {
            if (PermissionHelper.shouldShowRationaleForPermissions(activity, PermissionHelper.STORAGE_PERMISSIONS)) {
                showStoragePermissionRationale()
            } else {
                PermissionHelper.requestPermissionGroup(
                    activity,
                    PermissionHelper.STORAGE_PERMISSIONS,
                    PermissionHelper.REQUEST_CODE_STORAGE
                )
            }
        }
        return PermissionHelper.arePermissionsGranted(activity, PermissionHelper.STORAGE_PERMISSIONS)
    }

    private fun showStoragePermissionRationale() {
        AlertDialog.Builder(activity)
            .setTitle("Storage Permission Required")
            .setMessage("This app needs storage access to read and save files. Please grant the permission.")
            .setPositiveButton("Grant") { _, _ ->
                PermissionHelper.requestPermissionGroup(
                    activity,
                    PermissionHelper.STORAGE_PERMISSIONS,
                    PermissionHelper.REQUEST_CODE_STORAGE
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleLaai(): Boolean {
        settings.fromMenu = true
        val intent = Intent(activity, Laaidatabasis::class.java)
        activity.startActivity(intent)
        activity.finish()
        return true
    }

    private fun handleSmsVerjaar(): Boolean {
        val intent = Intent(activity, VerjaarSMS2::class.java)
        settings.fromMenu = true
        activity.startActivity(intent)
        return true
    }

    private fun handleFilterOptions(): Boolean {
        return try {
            val filterHandler = FilterHandler(activity)
            filterHandler.showFilterDialog()
            true
        } catch (e: Exception) {
            Toast.makeText(activity, "Error opening filter options", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun handleDeselect(): Boolean {
        val values = ContentValues().apply {
            put(LIDMATE_TAG, 0)
        }
        activity.contentResolver.update(WinkerkContract.winkerkEntry.CONTENT_URI, values, null, null)
        return true
    }

    private fun handleUpdateApp(): Boolean {
        val intent = Intent(activity, UpdateActivity::class.java)
        activity.startActivity(intent)
        return true
    }

    private fun handleUitleg(): Boolean {
        val intent = Intent(activity, UitlegActivity::class.java)
        activity.startActivity(intent)
        return true
    }

    private fun handleArgief(): Boolean {
        val intent = Intent(activity, argief_List::class.java)
        activity.startActivity(intent)
        return true
    }

    private fun checkStoragePermission(): Boolean {
        val permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return if (permission != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, "Storage permission required", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(activity, STORAGE_PERMISSIONS, REQUEST_EXTERNAL_STORAGE)
            false
        } else {
            true
        }
    }

    private fun showPermissionSettingsDialog() {
        val currentSetting = permissionManager.isCheckOnStartEnabled()

        AlertDialog.Builder(activity)
            .setTitle("Permission Check Settings")
            .setMessage("Check permissions on app start: ${if (currentSetting) "Enabled" else "Disabled"}")
            .setPositiveButton(if (currentSetting) "Disable" else "Enable") { _, _ ->
                permissionManager.setCheckOnStart(!currentSetting)
                Toast.makeText(
                    activity,
                    "Permission check ${if (!currentSetting) "enabled" else "disabled"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNeutralButton("Manage Permissions") { _, _ ->
                val intent = Intent(activity, PermissionsActivity::class.java)
                activity.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private const val REQUEST_EXTERNAL_STORAGE = 1
        private val STORAGE_PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}