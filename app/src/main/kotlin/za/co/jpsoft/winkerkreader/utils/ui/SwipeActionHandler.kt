package za.co.jpsoft.winkerkreader.utils.ui

import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import za.co.jpsoft.winkerkreader.data.members.models.MemberItem
import za.co.jpsoft.winkerkreader.data.members.models.SwipeAction
import za.co.jpsoft.winkerkreader.ui.controllers.SortOrderController
import za.co.jpsoft.winkerkreader.ui.helpers.QuickActionHelper
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.MemberUtils
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs

class SwipeActionHandler(
    private val activity: AppCompatActivity,
    private val viewModel: MemberViewModel,
    private val navigationController: MainNavigationController,
    private val memberListPrefs: MemberListPrefs,
    private val sortOrderController: SortOrderController,
    private val quickActionHelper: QuickActionHelper
) {

    // ─── Public swipe entry points ───────────────────────────────────────────

    fun handleSwipeRight(member: MemberItem) =
        handleAction(SwipeAction.fromKey(memberListPrefs.swipeRightAction), member)

    fun handleSwipeLeft(member: MemberItem) =
        handleAction(SwipeAction.fromKey(memberListPrefs.swipeLeftAction), member)

    // ─── Settings dialog ─────────────────────────────────────────────────────

    fun showSwipeSettingsDialog() {
        val builder = MaterialAlertDialogBuilder(activity)
        builder.setTitle("Swiep Aksies")
        builder.setCancelable(false)  // prevent dismissing by back/outside tap
        val dialog = builder.create()

        // Dynamic summary updater
        fun updateSummary() {
            val currentRight = SwipeAction.fromKey(memberListPrefs.swipeRightAction)
            val currentLeft = SwipeAction.fromKey(memberListPrefs.swipeLeftAction)
            dialog.setMessage(
                "Swiep Regs: ${currentRight.labelAfrikaans}\n" +
                        "Swiep Links: ${currentLeft.labelAfrikaans}"
            )
        }
        updateSummary()

        // Regs button → opens picker, does NOT dismiss main dialog
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, "Regs") { _, _ ->
            pickAction("Swiep Regs", memberListPrefs.swipeRightAction) { key ->
                memberListPrefs.swipeRightAction = key
                updateSummary()
            }
        }
        // Links button → opens picker, does NOT dismiss main dialog
        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Links") { _, _ ->
            pickAction("Swiep Links", memberListPrefs.swipeLeftAction) { key ->
                memberListPrefs.swipeLeftAction = key
                updateSummary()
            }
        }
        // Close button – dismisses the main dialog
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Sluit") { _, _ ->
            dialog.dismiss()
        }

        dialog.show()
    }

    // ─── Action picker (single‑choice list) ─────────────────────────────────

    private fun pickAction(title: String, currentKey: String, onPicked: (String) -> Unit) {
        val labels = SwipeAction.labels()
        val all = SwipeAction.all
        val currentIndex = all.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
        var chosenIndex = currentIndex

        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setSingleChoiceItems(labels, currentIndex) { _, which ->
                chosenIndex = which
            }
            .setPositiveButton("OK") { _, _ ->
                if (chosenIndex in all.indices) {
                    onPicked(all[chosenIndex].key)
                }
            }
            .setNegativeButton("Kanselleer", null)
            .show()
    }

    // ─── Action dispatch ─────────────────────────────────────────────────────

    private fun handleAction(action: SwipeAction, member: MemberItem) {
        var whatsappMethod: Int = 1 // TODO: read from preferences if needed
        when (action) {
            SwipeAction.VolgendeSortering -> sortOrderController.cycleForward()

            SwipeAction.Besonderhede ->
                navigationController.navigateToLidmaatDetail(
                    memberGuid = member.guid,
                    recordStatus = member.recordstatus
                )

            SwipeAction.Bel -> {
                val number = member.cellphone.ifEmpty { member.landline }
                if (number.isEmpty()) showNoContact("nommer")
                else navigationController.navigateToPhoneDial(number)
            }

            SwipeAction.WhatsApp -> {
                if (member.cellphone.isEmpty()) showNoContact("selfoon nommer vir WhatsApp")
                else MemberUtils.sendWhatsApp(activity, member.cellphone, whatsappMethod, "")
            }

            SwipeAction.Sms -> {
                val number = member.cellphone.ifEmpty { member.landline }
                if (number.isEmpty()) showNoContact("nommer")
                else navigationController.navigateToSms(number)
            }

            SwipeAction.Epos -> {
                if (member.email.isEmpty()) showNoContact("e-pos adres")
                else navigationController.navigateToEmail(member.email)
            }

            SwipeAction.Nota ->
                quickActionHelper.openVoegNotaBy(member)

            SwipeAction.Herinnering ->   // <-- new
                quickActionHelper.openStelHerinnering(member)

            SwipeAction.Niks -> { /* intentionally empty */
            }
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun showNoContact(detail: String) {
        Snackbar.make(
            activity.window.decorView.rootView,
            "Geen $detail beskikbaar nie",
            Snackbar.LENGTH_SHORT
        ).show()
    }
}