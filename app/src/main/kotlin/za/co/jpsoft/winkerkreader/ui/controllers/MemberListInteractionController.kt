package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.bottomsheets.StelHerinneringBottomSheet
import za.co.jpsoft.winkerkreader.ui.bottomsheets.VoegNotaByBottomSheet
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.MemberActionHandler
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.forceShowIcons

class MemberListInteractionController(
    private val activity: AppCompatActivity,
    private val tag: String,
    private val settingsManager: SettingsManager,
    private val viewModel: MemberViewModel,
    private val memberListAdapter: MemberListAdapter,
    private val observeDataset: () -> Unit
) {
    companion object {
        private const val UNIMPLEMENTED_GROUP_ACTION_LOG =
            "Group menu action tapped; not implemented yet"
    }

    fun onMemberLongClick(item: MemberItem): Boolean {
        val values =
            ContentValues().apply { put(winkerkEntry.LIDMATE_TAG, if (item.tag == 0) 1 else 0) }
        val memberUri = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, item.id)
        val rowsAffected =
            activity.contentResolver.update(
                memberUri,
                values,
                "${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ =?",
                arrayOf(item.id.toString())
            )
        if (rowsAffected == 1) viewModel.refresh()
        return rowsAffected == 1
    }

    fun showMemberPopupMenu(anchor: View, item: MemberItem) {
        val popup = PopupMenu(activity, anchor)
        popup.menuInflater.inflate(R.menu.lidmaatlist_menu, popup.menu)
        popup.forceShowIcons()
        configurePopupMenuFromItem(popup, item)

        popup.setOnMenuItemClickListener { menuItem ->
            handlePopupMenuClick(
                menuItem,
                menuItem.itemId,
                item
            )
        }
        popup.show()
    }

//    fun showGroupFunctionMenu(anchor: View) {
//        val popup = PopupMenu(activity, anchor)
//        popup.menuInflater.inflate(R.menu.groepfunksie_menu, popup.menu)
//        popup.forceShowIcons()
//        val items = memberListAdapter.getCurrentItems()
//        if (items.isEmpty()) return
//        val totalCount = items.size
//        val selectedCount = items.count { it.tag == 1 }
//
//        applyGroupMenuCounts(popup.menu, totalCount, selectedCount)
//
//        popup.show()
//        popup.setOnMenuItemClickListener {
//            if (BuildConfig.DEBUG) Log.d(tag, UNIMPLEMENTED_GROUP_ACTION_LOG)
//            true
//        }
//    }

    // ── Menu configuration ─────────────────────────────────────────────────────

    private fun configurePopupMenuFromItem(popup: PopupMenu, item: MemberItem) {
        val menu = popup.menu
        var mt = menu.findItem(R.id.kyk_lidmaat_detail);
        var spanString = SpannableString("ℹ\uFE0F ${item.name} ${item.surname}")
        spanString.setSpan(StyleSpan(Typeface.BOLD), 0, spanString.length, 0)
        mt.title = spanString

        mt = menu.findItem(R.id.submenu_bel)
        spanString = SpannableString("\uD83D\uDCDE ${item.name}")
        // spanString.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, spanString.length, 0)
        mt.title = spanString

        mt = menu.findItem(R.id.submenu_teks)
        spanString = SpannableString("✏\uFE0F ${item.name}") // Teks
        //spanString.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, spanString.length, 0)
        mt.title = spanString

        mt = menu.findItem(R.id.submenu_ander)
        spanString = SpannableString(item.name)
        //  spanString.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, spanString.length, 0)
        mt.title = spanString

        if (item.cellphone.isEmpty()) {
            safeRemoveMenuItem(menu, R.id.submenu_bel, R.id.bel_selfoon)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_sms)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp2)
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp3)
        } else {
            menu.findItem(R.id.bel_selfoon)?.title = "\uD83D\uDCF2 ${item.cellphone}" // Skakel
            menu.findItem(R.id.stuur_sms)?.title = "\uD83D\uDCAC ${item.cellphone}" // SMS na
        }

        if (item.landline.isEmpty()) {
            safeRemoveMenuItem(menu, R.id.submenu_bel, R.id.bel_landlyn)
        } else {
            menu.findItem(R.id.bel_landlyn)?.title = "\uD83D\uDCDE ${item.landline}" //Skakel
        }

        if (item.email.isEmpty()) {
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_epos)
        }

        if (!settingsManager.whatsapp1) safeRemoveMenuItem(
            menu,
            R.id.submenu_teks,
            R.id.stuur_whatsapp
        )
        if (!settingsManager.whatsapp2) safeRemoveMenuItem(
            menu,
            R.id.submenu_teks,
            R.id.stuur_whatsapp2
        )
        if (!settingsManager.whatsapp3) safeRemoveMenuItem(
            menu,
            R.id.submenu_teks,
            R.id.stuur_whatsapp3
        )

        // ── Bediening items — versteek as GUID ontbreek ────────────────────────
        val hasGuid = !item.guid.isNullOrBlank()
        menu.findItem(R.id.voeg_nota_by)?.isVisible = hasGuid
        menu.findItem(R.id.stel_herinnering)?.isVisible = hasGuid
    }

    // ── Click handling ─────────────────────────────────────────────────────────

    private fun handlePopupMenuClick(menuItem: MenuItem, actionId: Int, item: MemberItem): Boolean {
        if (BuildConfig.DEBUG) Log.d(
            tag,
            "handlePopupMenuClick: actionId=$actionId, item.id=${item.id}"
        )

        return when (actionId) {
            R.id.submenu_bel -> {
                var spanString = SpannableString(item.name)//menuItem.title)
                spanString.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, spanString.length, 0)
                menuItem.title = spanString
                true
            }

            R.id.submenu_teks -> {
                var spanString = SpannableString(item.name)//menuItem.title)
                spanString.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, spanString.length, 0)
                menuItem.title = spanString
                true
            }

            R.id.submenu_ander -> {
                var spanString = SpannableString(item.name)//menuItem.title)
                spanString.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, spanString.length, 0)
                menuItem.title = spanString
                true
            }
            // ── Bedieningsnota ─────────────────────────────────────────────────
            R.id.voeg_nota_by -> {
                openVoegNotaBy(item)
                true
            }

            // ── Herinnering ────────────────────────────────────────────────────
            R.id.stel_herinnering -> {
                openStelHerinnering(item)
                true
            }

            // ── Alle ander aksies gaan deur die bestaande MemberActionHandler ──
            else -> MemberActionHandler(activity, item, viewModel).handleAction(actionId)
        }
    }

    // ── Bediening helpers ──────────────────────────────────────────────────────

    /**
     * Opens [VoegNotaByBottomSheet] for [item].
     * Silently no-ops if [MemberItem.guid] is blank — the menu item is already
     * hidden in that case via [configurePopupMenuFromItem].
     */
    private fun openVoegNotaBy(item: MemberItem) {
        val guid = item.guid?.takeIf { it.isNotBlank() } ?: run {
            if (BuildConfig.DEBUG) Log.w(tag, "openVoegNotaBy: guid is null/blank for ${item.name}")
            return
        }
        VoegNotaByBottomSheet.newInstance(
            memberGuid = guid,
            familyHeadGuid = item.familyHead,
            memberDisplayName = "${item.name} ${item.surname}".trim(),
            memberSurname = item.surname.ifBlank { null },
            memberGivenName = item.name.ifBlank { null }
        ).show(activity.supportFragmentManager, VoegNotaByBottomSheet.TAG)
    }

    /**
     * Opens [StelHerinneringBottomSheet] for [item].
     * Silently no-ops if [MemberItem.guid] is blank.
     */
    private fun openStelHerinnering(item: MemberItem) {
        val guid = item.guid?.takeIf { it.isNotBlank() } ?: run {
            if (BuildConfig.DEBUG) Log.w(
                tag,
                "openStelHerinnering: guid is null/blank for ${item.name}"
            )
            return
        }
        StelHerinneringBottomSheet.newInstance(
            memberGuid = guid,
            familyHeadGuid = item.familyHead
        ).show(activity.supportFragmentManager, StelHerinneringBottomSheet.TAG)
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun safeRemoveMenuItem(menu: Menu, submenuId: Int, itemId: Int) {
        val submenu = menu.findItem(submenuId)
        if (submenu?.hasSubMenu() == true) {
            submenu.subMenu?.removeItem(itemId)
        }
    }

//    private fun applyGroupMenuCounts(menu: Menu, totalCount: Int, selectedCount: Int) {
//        menu.findItem(R.id.sms_groep).title = "Almal ($totalCount)"
//        menu.findItem(R.id.sms_selected).title = "Geselekteerdes ($selectedCount)"
//        menu.findItem(R.id.almal_in_groep).title = "Almal ($totalCount)"
//        menu.findItem(R.id.selected_in_groep).title = "Geselekteerdes ($selectedCount)"
//    }
}