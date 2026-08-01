package za.co.jpsoft.winkerkreader.utils

import android.content.ContentValues
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_TAG
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs

class MenuItemHandler(
    private val activity: AppCompatActivity,
    private val viewModel: MemberViewModel,
    private val navigationController: MainNavigationController,
    private val memberListPrefs: MemberListPrefs,
    private val onSortOrderChanged: (String) -> Unit
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
                memberListPrefs.fromMenu = true   // ← fixed
                navigationController.navigateToLaaiDatabasis(extras = null)
                activity.finish()
                true
            }
            R.id.sms_verjaar -> {
                memberListPrefs.fromMenu = true   // ← fixed
                navigationController.navigateToVerjaarSms()
                true
            }
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

    fun handleAktiefRadioGroup(): Boolean = true

    private fun handleTagged(): Boolean {
        memberListPrefs.defLayout = "VAN"   // ← fixed
        viewModel.soekList = false
        onSortOrderChanged("VAN")
        return true
    }

    private fun handleSortVan(): Boolean {
        memberListPrefs.defLayout = "VAN"   // ← fixed
        viewModel.soekList = false
        onSortOrderChanged("VAN")
        return true
    }

    private fun handleSortWyk(): Boolean {
        memberListPrefs.defLayout = "WYK"   // ← fixed
        viewModel.soekList = false
        onSortOrderChanged("WYK")
        return true
    }

    private fun handleSortOuderdom(): Boolean {
        memberListPrefs.defLayout = "OUDERDOM"   // ← fixed
        viewModel.soekList = false
        onSortOrderChanged("OUDERDOM")
        return true
    }

    private fun handleVerjaar(): Boolean {
        memberListPrefs.defLayout = "VERJAAR"   // ← fixed
        viewModel.soekList = false
        onSortOrderChanged("VERJAAR")
        return true
    }

    private fun handleSortAdres(): Boolean {
        memberListPrefs.defLayout = "ADRES"   // ← fixed
        viewModel.soekList = false
        onSortOrderChanged("ADRES")
        return true
    }

    private fun handleSortGesin(): Boolean {
        memberListPrefs.defLayout = "GESINNE"   // ← fixed
        viewModel.soekList = false
        onSortOrderChanged("GESINNE")
        return true
    }

    private fun handleDeselect(): Boolean {
        val values = ContentValues().apply {
            put(LIDMATE_TAG, 0)
        }
        activity.contentResolver.update(
            WinkerkContract.winkerkEntry.CONTENT_URI,
            values,
            null,
            null
        )
        return true
    }
}