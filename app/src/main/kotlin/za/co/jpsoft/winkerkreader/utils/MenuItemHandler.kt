package za.co.jpsoft.winkerkreader.utils

import za.co.jpsoft.winkerkreader.ui.activities.RegistreerActivity
import za.co.jpsoft.winkerkreader.ui.activities.ArgiefListActivity
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.activities.PermissionsActivity
import za.co.jpsoft.winkerkreader.ui.activities.VerjaarSmsActivity
import za.co.jpsoft.winkerkreader.ui.activities.UitlegActivity
import za.co.jpsoft.winkerkreader.ui.activities.SettingsActivity
import za.co.jpsoft.winkerkreader.ui.activities.LaaiDatabasisActivity
import za.co.jpsoft.winkerkreader.ui.activities.CallLogActivity
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel

import android.content.ContentValues
import android.content.Intent

import android.view.MenuItem
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_TAG

class MenuItemHandler(
    private val activity: AppCompatActivity,
    private val settings: SettingsManager,
    private val viewModel: MemberViewModel
) {
    private lateinit var permissionManager: PermissionManager

    fun handleMenuItem(item: MenuItem): Boolean {
        // Initialize permission manager (needed for permission dialogs)
        permissionManager = PermissionManager(activity)

        val sortOrderView = activity.findViewById<TextView>(R.id.sortorder)

        return when (item.itemId) {
            R.id.aktief_radio_group -> handleAktiefRadioGroup()
            //R.id.select_options -> handleSelectOptions()
            R.id.tagged -> handleTagged(sortOrderView)
            R.id.sort_van -> handleSortVan(sortOrderView)
            R.id.sort_wyk -> handleSortWyk(sortOrderView)
            R.id.sort_ouderdom -> handleSortOuderdom(sortOrderView)
            R.id.verjaar -> handleVerjaar(sortOrderView)
            R.id.sort_adres -> handleSortAdres(sortOrderView)
            R.id.sort_gesin -> handleSortGesin(sortOrderView)
            R.id.RegistreerActivity -> handleRegistreer()
            R.id.laai -> handleLaai()
            R.id.sms_verjaar -> handleSmsVerjaar()
            R.id.filter_options -> handleFilterOptions()
            R.id.deselect -> handleDeselect()
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

    fun handleAktiefRadioGroup(): Boolean {
        return true
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
        val intent = Intent(activity, SettingsActivity::class.java)
        activity.startActivity(intent)
        return true
    }

    private fun handleTagged(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout = "VAN"
        viewModel.sortOrder = "VAN"
        viewModel.soekList = false
        (activity as MainActivity).observeDataset()
        return true
    }

    private fun handleSortVan(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout =  "VAN"
        viewModel.sortOrder = "VAN"
        viewModel.soekList = false
        (activity as MainActivity).observeDataset()
        return true
    }

    private fun handleSortWyk(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout =  "WYK"
        viewModel.sortOrder = "WYK"
        viewModel.soekList = false
        (activity as MainActivity).observeDataset()
        return true
    }

    private fun handleSortOuderdom(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout = "OUDERDOM"
        viewModel.sortOrder = "OUDERDOM"
        viewModel.soekList = false
        (activity as MainActivity).observeDataset()
        return true
    }

    private fun handleVerjaar(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout = "VERJAAR"
        viewModel.sortOrder = "VERJAAR"
        viewModel.soekList = false
        (activity as MainActivity).observeDataset()
        return true
    }

    private fun handleSortAdres(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout = "ADRES"
        viewModel.sortOrder = "ADRES"
        viewModel.soekList = false
        (activity as MainActivity).observeDataset()
        return true
    }

    private fun handleSortGesin(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout = "GESINNE"
        viewModel.sortOrder = "GESINNE"
        viewModel.soekList = false
        (activity as MainActivity).observeDataset()
        return true
    }

    private fun handleRegistreer(): Boolean {
        val intent = Intent(activity, RegistreerActivity::class.java)
        activity.startActivity(intent)
        return true
    }



    private fun handleLaai(): Boolean {
        settings.fromMenu = true
        val intent = Intent(activity, LaaiDatabasisActivity::class.java)
        activity.startActivity(intent)
        activity.finish()
        return true
    }

    private fun handleSmsVerjaar(): Boolean {
        val intent = Intent(activity, VerjaarSmsActivity::class.java)
        settings.fromMenu = true
        activity.startActivity(intent)
        return true
    }

    private fun handleFilterOptions(): Boolean {
        return try {
            val filterHandler = FilterHandler(activity, viewModel)
            filterHandler.showFilterDialog()
            true
        } catch (_: Exception) {
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

    private fun handleUitleg(): Boolean {
        val intent = Intent(activity, UitlegActivity::class.java)
        activity.startActivity(intent)
        return true
    }

    private fun handleArgief(): Boolean {
        val intent = Intent(activity, ArgiefListActivity::class.java)
        activity.startActivity(intent)
        return true
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
}