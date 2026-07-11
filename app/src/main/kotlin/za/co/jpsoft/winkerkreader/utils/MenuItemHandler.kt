package za.co.jpsoft.winkerkreader.utils

import android.content.ContentValues
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
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
    fun handleMenuItem(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.aktief_radio_group -> handleAktiefRadioGroup()
            R.id.tagged -> handleTagged()
            R.id.sort_van -> handleSortVan()
            R.id.sort_wyk -> handleSortWyk()
            R.id.sort_ouderdom -> handleSortOuderdom()
            R.id.verjaar -> handleVerjaar()
            R.id.sort_adres -> handleSortAdres()
            R.id.sort_gesin -> handleSortGesin()
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
            //R.id.filter_options -> handleFilterOptions()
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

    private fun handleTagged(): Boolean {
        settings.defLayout = "VAN"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("VAN")
        return true
    }

    private fun handleSortVan(): Boolean {
        settings.defLayout = "VAN"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("VAN")
        return true
    }

    private fun handleSortWyk(): Boolean {
        settings.defLayout = "WYK"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("WYK")
        return true
    }

    private fun handleSortOuderdom(): Boolean {
        settings.defLayout = "OUDERDOM"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("OUDERDOM")
        return true
    }

    private fun handleVerjaar(): Boolean {
        settings.defLayout = "VERJAAR"
        viewModel.soekList = false
        val activity = activity as? MainActivity
        if (activity != null) {
            activity.updateSortOrder("VERJAAR")
            // The scroll will happen automatically in updateSortOrder
        }
        return true
    }

    private fun handleSortAdres(): Boolean {
        settings.defLayout = "ADRES"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("ADRES")
        return true
    }

    private fun handleSortGesin(): Boolean {
        settings.defLayout = "GESINNE"
        viewModel.soekList = false
        (activity as MainActivity).updateSortOrder("GESINNE")
        return true
    }

//    private fun handleFilterOptions(): Boolean {
//        return try {
//            val filterHandler = FilterHandler(activity as MainActivity, viewModel)
//            filterHandler.showFilterDialog()
//            true
//        } catch (e: Exception) {
//            if (BuildConfig.DEBUG) Log.e("MenuItemHandler", "Filter error", e)
//            Toast.makeText(activity, "Error opening filter options", Toast.LENGTH_SHORT).show()
//            false
//        }
//    }

    private fun handleDeselect(): Boolean {
        val values = ContentValues().apply {
            put(LIDMATE_TAG, 0)
        }
        activity.contentResolver.update(WinkerkContract.winkerkEntry.CONTENT_URI, values, null, null)
        return true
    }
}