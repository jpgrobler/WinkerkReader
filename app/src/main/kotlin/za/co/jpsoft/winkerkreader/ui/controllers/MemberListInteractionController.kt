package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.ContentUris
import android.content.ContentValues
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.bottomsheets.StelHerinneringBottomSheet
import za.co.jpsoft.winkerkreader.ui.bottomsheets.VoegNotaByBottomSheet
import za.co.jpsoft.winkerkreader.ui.helpers.QuickActionHelper
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.MemberActionHandler
import za.co.jpsoft.winkerkreader.utils.SettingsManager

class MemberListInteractionController(
    private val activity: AppCompatActivity,
    private val tag: String,
    private val settingsManager: SettingsManager,
    private val viewModel: MemberViewModel,
    private val memberListAdapter: MemberListAdapter,
    private val observeDataset: () -> Unit
) {

    // ─── Quick Action Helper ──────────────────────────────────────────────────
    private val quickActionHelper = QuickActionHelper(activity, settingsManager)

    init {
        // Set the expand callback to show the full menu
        quickActionHelper.expandCallback = { anchor, item ->
            showFullPopupMenu(anchor, item)
        }
    }

    // ─── Long‑press: toggle tag ──────────────────────────────────────────────
    fun onMemberLongClick(item: MemberItem): Boolean {
        val newTag = if (item.tag == 0) 1 else 0
        val values = ContentValues().apply {
            put(winkerkEntry.LIDMATE_TAG, newTag)
        }
        val memberUri = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, item.id)
        val rowsAffected = activity.contentResolver.update(
            memberUri,
            values,
            "${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ?",
            arrayOf(item.id.toString())
        )
        if (rowsAffected == 1) {
            viewModel.refresh()
        }
        return rowsAffected == 1
    }

    // ─── Click: show quick‑action popup ──────────────────────────────────────
    fun showMemberPopupMenu(anchor: View, item: MemberItem) {
        quickActionHelper.showQuickActions(anchor, item)
    }

    // ─── Full menu (original expanded menu) ──────────────────────────────────
    private fun showFullPopupMenu(anchor: View, item: MemberItem) {
        val popup = PopupMenu(activity, anchor)
        popup.menuInflater.inflate(R.menu.lidmaatlist_menu, popup.menu)
        configureFullMenu(popup.menu, item)

        popup.setOnMenuItemClickListener { menuItem ->
            handlePopupMenuClick(menuItem, menuItem.itemId, item)
        }
        popup.show()
    }

    // ─── Configure full menu ──────────────────────────────────────────────────
    private fun configureFullMenu(menu: Menu, item: MemberItem) {
        menu.findItem(R.id.kyk_lidmaat_detail)?.title =
            "ℹ️ ${item.name} ${item.surname}"

        if (item.cellphone.isEmpty()) {
            safeRemoveMenuItem(menu, R.id.submenu_bel, R.id.bel_selfoon)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_sms)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp2)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp3)
        } else {
            menu.findItem(R.id.bel_selfoon)?.title = "📱 ${item.cellphone}"
            menu.findItem(R.id.stuur_sms)?.title = "💬 ${item.cellphone}"
        }

        if (item.landline.isEmpty()) {
            safeRemoveMenuItem(menu, R.id.submenu_bel, R.id.bel_landlyn)
        } else {
            menu.findItem(R.id.bel_landlyn)?.title = "☎️ ${item.landline}"
        }

        if (item.email.isEmpty()) {
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_epos)
        }

        if (!settingsManager.whatsapp1) {
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp)
        }
        if (!settingsManager.whatsapp2) {
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp2)
        }
        if (!settingsManager.whatsapp3) {
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp3)
        }

        val hasGuid = !item.guid.isNullOrBlank()
        menu.findItem(R.id.voeg_nota_by)?.isVisible = hasGuid
        menu.findItem(R.id.stel_herinnering)?.isVisible = hasGuid
    }

    // ─── Handle full menu clicks ─────────────────────────────────────────────
    private fun handlePopupMenuClick(
        menuItem: MenuItem,
        actionId: Int,
        item: MemberItem
    ): Boolean {
        return when (actionId) {
            R.id.voeg_nota_by -> {
                openVoegNotaBy(item); true
            }

            R.id.stel_herinnering -> {
                openStelHerinnering(item); true
            }
            else -> MemberActionHandler(activity, item, viewModel).handleAction(actionId)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private fun safeRemoveMenuItem(menu: Menu, submenuId: Int, itemId: Int) {
        val submenu = menu.findItem(submenuId)
        if (submenu?.hasSubMenu() == true) {
            submenu.subMenu?.removeItem(itemId)
        }
    }

    private fun openVoegNotaBy(item: MemberItem) {
        val guid = item.guid?.takeIf { it.isNotBlank() } ?: return
        VoegNotaByBottomSheet.newInstance(
            memberGuid = guid,
            familyHeadGuid = item.familyHead,
            memberDisplayName = "${item.name} ${item.surname}".trim(),
            memberSurname = item.surname.ifBlank { null },
            memberGivenName = item.name.ifBlank { null }
        ).show(activity.supportFragmentManager, VoegNotaByBottomSheet.TAG)
    }

    private fun openStelHerinnering(item: MemberItem) {
        val guid = item.guid?.takeIf { it.isNotBlank() } ?: return
        StelHerinneringBottomSheet.newInstance(
            memberGuid = guid,
            familyHeadGuid = item.familyHead
        ).show(activity.supportFragmentManager, StelHerinneringBottomSheet.TAG)
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────
    fun dismissQuickActions() {
        quickActionHelper.dismiss()
    }
}