package za.co.jpsoft.winkerkreader.utils

import android.content.ContentValues
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_TAG
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel

class MenuItemHandler(
    private val activity: AppCompatActivity,
    private val settings: SettingsManager,
    private val viewModel: MemberViewModel,
    private val navigationController: MainNavigationController
) {
    private lateinit var permissionManager: PermissionManager

    fun handleMenuItem(item: MenuItem): Boolean {
        permissionManager = PermissionManager(activity)

        val sortOrderView = activity.findViewById<TextView>(R.id.sortorder)

        return when (item.itemId) {
            R.id.aktief_radio_group -> handleAktiefRadioGroup()
            R.id.tagged -> handleTagged(sortOrderView)
            R.id.sort_van -> handleSortVan(sortOrderView)
            R.id.sort_wyk -> handleSortWyk(sortOrderView)
            R.id.sort_ouderdom -> handleSortOuderdom(sortOrderView)
            R.id.verjaar -> handleVerjaar(sortOrderView)
            R.id.sort_adres -> handleSortAdres(sortOrderView)
            R.id.sort_gesin -> handleSortGesin(sortOrderView)
            R.id.RegistreerActivity -> {
                navigationController.navigateToRegistreer()
                true
            }
            R.id.laai -> {
                settings.fromMenu = true
                navigationController.navigateToLaaiDatabasis()
                activity.finish()
                true
            }
            R.id.sms_verjaar -> {
                settings.fromMenu = true
                navigationController.navigateToSmsVerjaar()
                true
            }
            R.id.filter_options -> handleFilterOptions()
            R.id.deselect -> handleDeselect()
            R.id.uitleg -> {
                navigationController.navigateToUitleg()
                true
            }
            R.id.argief -> {
                navigationController.navigateToArgief()
                true
            }
            R.id.action_view_call_log -> {
                navigationController.navigateToCallLog()
                true
            }
            R.id.menu_permissions -> {
                navigationController.navigateToPermissions()
                true
            }
            R.id.menu_permission_settings -> {
                showPermissionSettingsDialog()
                true
            }
            R.id.menu_battery_optimization -> {
                BatteryOptimizationHelper.showBatteryOptimizationDialog(activity)
                true
            }
            R.id.action_bediening -> {
                navigationController.navigateToBediening()
                true
            }
            R.id.menu_sorteer_titel -> {
                val spanString = SpannableString(item.title)
                spanString.setSpan(StyleSpan(Typeface.BOLD), 0, spanString.length, 0)
                item.title = spanString
                true
            }
            R.id.menu_andmin_titel -> {
                val spanString = SpannableString(item.title)
                spanString.setSpan(StyleSpan(Typeface.BOLD), 0, spanString.length, 0)
                item.title = spanString
                true
            }
            else -> false
        }
    }

    fun handleAktiefRadioGroup(): Boolean {
        return true
    }

    // Die volgende metodes bly onveranderd, want hulle gebruik nie `startActivity` nie.
    private fun handleTagged(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout = "VAN"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("VAN")//viewModel.refresh()//(activity as MainActivity).observeDataset()
        return true
    }

    private fun handleSortVan(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout = "VAN"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("VAN")//viewModel.refresh()  //(activity as MainActivity).observeDataset()
        return true
    }

    private fun handleSortWyk(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout = "WYK"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("WYK")//viewModel.refresh()//(activity as MainActivity).observeDataset()
        return true
    }

    private fun handleSortOuderdom(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout = "OUDERDOM"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("OUDERDOM")//viewModel.refresh()//(activity as MainActivity).observeDataset()
        return true
    }

    private fun handleVerjaar(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout = "VERJAAR"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("VERJAAR")//viewModel.refresh()//(activity as MainActivity).observeDataset()
        return true
    }

    private fun handleSortAdres(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout = "ADRES"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("ADRES")//viewModel.refresh()//(activity as MainActivity).observeDataset()
        return true
    }

    private fun handleSortGesin(sortOrderView: TextView): Boolean {
        sortOrderView.background = null
        settings.defLayout = "GESINNE"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("GESINNE")//viewModel.refresh()//(activity as MainActivity).observeDataset()
        return true
    }

    private fun handleFilterOptions(): Boolean {
        return try {
            val filterHandler = FilterHandler(activity as MainActivity, viewModel)
            filterHandler.showFilterDialog()
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("MenuItemHandler", "Filter error", e)
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
                navigationController.navigateToPermissions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}