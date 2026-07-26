package za.co.jpsoft.winkerkreader.ui.helpers

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isNotEmpty
import com.google.android.material.color.MaterialColors
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.data.repositories.ContactRepository
import za.co.jpsoft.winkerkreader.ui.bottomsheets.StelHerinneringBottomSheet
import za.co.jpsoft.winkerkreader.ui.bottomsheets.VoegNotaByBottomSheet
import za.co.jpsoft.winkerkreader.utils.MemberUtils
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.Utils


/**
 * Reusable helper that displays a horizontal quick-action popup for a member.
 * Used in MainActivity, VerjaarSmsActivity, and anywhere else a member list appears.
 *
 * @property activity The parent [AppCompatActivity] used for inflating views and launching bottom sheets.
 * @property settingsManager Manager for checking configured communication channels (e.g., WhatsApp preferences).
 */
class QuickActionHelper(
    private val activity: AppCompatActivity,
    private val settingsManager: SettingsManager
) {

    // ─── Properties ──────────────────────────────────────────────────────────

    /** Reference to the currently active [PopupWindow] instance. */
    private var quickActionsPopup: PopupWindow? = null

    /** Callback invoked when the user selects the "More" (expand) action. */
    var expandCallback: ((View, MemberItem) -> Unit)? = null


    // ─── Public API (Popup Control) ──────────────────────────────────────────

    /**
     * Show the horizontal quick action popup anchored to the given view.
     *
     * @param anchor The anchor [View] where the popup will be displayed.
     * @param item The [MemberItem] data associated with the quick actions.
     * @param message Optional pre-filled personalized message for SMS/WhatsApp actions.
     */
    fun showQuickActions(anchor: View, item: MemberItem, message: String? = null) {

        dismiss()

        val inflater = LayoutInflater.from(activity)

        val popupView = inflater.inflate(R.layout.popup_quick_actions, null) as LinearLayout

        val container = popupView.findViewById<LinearLayout>(R.id.quick_actions_container)

        val hasPhone = item.cellphone.isNotEmpty()

        val dividerColor = MaterialColors.getColor(
            activity,
            com.google.android.material.R.attr.colorOutlineVariant,
            0
        )

        /** Local helper to add action buttons separated by dividers to the popup container. */
        fun addActionButton(
            iconText: String? = null,
            iconDrawableRes: Int? = null,
            label: String,
            onClick: () -> Unit,
            iconColor: Int? = null
        ) {
            if (container.isNotEmpty()) {
                val divider = View(activity)
                divider.layoutParams =
                    LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
                val marginPx = (4 * activity.resources.displayMetrics.density).toInt()
                (divider.layoutParams as LinearLayout.LayoutParams).apply {
                    topMargin = marginPx
                    bottomMargin = marginPx
                }
                divider.setBackgroundColor(dividerColor)
                container.addView(divider)
            }
            val button = createActionButton(
                container,
                iconText,
                iconDrawableRes,
                label,
                onClick,
                iconColor
            )
            container.addView(button)
        }

        // 0. Details
        if (settingsManager.quickActionDetail) {
            addActionButton(
                iconText = "ℹ\uFE0F",
                label = "Detail",
                onClick = {
                    handleQuickAction(R.id.kyk_lidmaat_detail, item, item.recordstatus)
                    dismiss()
                }
            )
        }
        // 1. SMS – green icon
        if (settingsManager.quickActionSms && hasPhone) {
            addActionButton(
                iconText = "💬",
                label = "SMS",
                onClick = {
                    handleQuickAction(R.id.stuur_sms, item, message)
                    dismiss()
                }
            )
        }

        // 2. WhatsApp – drawable with green tint
        val whatsappMethod = when {
            settingsManager.whatsapp1 -> 1
            settingsManager.whatsapp2 -> 2
            settingsManager.whatsapp3 -> 3
            else -> 0 // disabled
        }

        if (settingsManager.quickActionWhatsApp && hasPhone && whatsappMethod != 0) {
            val formattedPhone = Utils.fixphonenumber(item.cellphone) ?: item.cellphone
            if (ContactRepository.isWhatsAppContact(formattedPhone)) {
                addActionButton(
                    iconDrawableRes = R.drawable.whatsapp,
                    label = "WhatsApp",
                    onClick = {
                        handleQuickAction(R.id.stuur_whatsapp, item, message, whatsappMethod)
                        dismiss()
                    }
                )
            }
        }

        // 3. Call – default color
        if (settingsManager.quickActionCall && hasPhone) {
            addActionButton(
                iconText = "📱",
                label = "Bel",
                onClick = {
                    handleQuickAction(R.id.bel_selfoon, item)
                    dismiss()
                }
            )
        }

        // 4. Email (new)
        if (settingsManager.quickActionEmail && item.email.isNotEmpty()) {
            addActionButton(
                iconText = "✉️", label = "E-pos",
                onClick = {
                    handleQuickAction(R.id.stuur_epos, item)
                    dismiss()
                }
            )
        }

        // 5. Landline (new)
        if (settingsManager.quickActionLandline && item.landline.isNotEmpty()) {
            addActionButton(
                iconText = "☎️", label = "Landlyn",
                onClick = {
                    handleQuickAction(R.id.bel_landlyn, item)
                    dismiss()
                }
            )
        }

        // 6. Note
        if (settingsManager.quickActionNote) {
            addActionButton(
                iconText = "📝", label = "Nota",
                onClick = {
                    handleQuickAction(R.id.voeg_nota_by, item)
                    dismiss()
                }
            )
        }

        // 7. Reminder
        if (settingsManager.quickActionReminder) {
            addActionButton(
                iconDrawableRes = R.drawable.ic_bediening,
                label = "Herinner",
                onClick = {
                    handleQuickAction(R.id.stel_herinnering, item)
                    dismiss()
                }
            )
        }

        // 8. Copy to clipboard (new)
        if (settingsManager.quickActionCopy) {
            addActionButton(
                iconText = "📋", label = "Kopieer",
                onClick = {
                    handleQuickAction(R.id.kopieer, item)
                    dismiss()
                }
            )
        }

        // 9. Copy to contacts (new)
        if (settingsManager.quickActionCopyContacts) {
            addActionButton(
                iconText = "👤", label = "Stoor",
                onClick = {
                    handleQuickAction(R.id.copy_to_contacts, item)
                    dismiss()
                }
            )
        }

        // 10. Expand – always visible
        addActionButton(
            iconDrawableRes = R.drawable.ic_chevron_down,
            label = "Meer",
            onClick = {
                dismiss()
                expandCallback?.invoke(anchor, item)
            }
        )

        val popup = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.isOutsideTouchable = true
        popup.elevation = 16f

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val screenHeight = activity.resources.displayMetrics.heightPixels
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = popupView.measuredHeight.takeIf { it > 0 } ?: 10

        val yOffset = if (location[1] + anchor.height + popupHeight > screenHeight) {
            -anchor.height - popupHeight
        } else {
            0
        }

        popup.showAsDropDown(anchor, 0, yOffset)
        quickActionsPopup = popup
    }

    /**
     * Dismisses the active popup window if it is currently visible.
     */
    fun dismiss() {
        quickActionsPopup?.dismiss()
        quickActionsPopup = null
    }


    // ─── Action Handlers ──────────────────────────────────────────────────────

    /**
     * Dispatches action clicks to the appropriate utility helper.
     *
     * @param actionId The menu item resource identifier (e.g., R.id.stuur_sms).
     * @param item The [MemberItem] data target.
     * @param message Optional message payload.
     * @param whatsappMethod WhatsApp API style selection.
     */
    private fun handleQuickAction(
        actionId: Int,
        item: MemberItem,
        message: String? = null,
        whatsappMethod: Int = 1
    ) {
        when (actionId) {
            R.id.kyk_lidmaat_detail ->
                MemberUtils.openMemberDetail(activity, item, item.recordstatus)

            R.id.stuur_sms ->
                MemberUtils.sendSms(activity, item.cellphone, message)

            R.id.stuur_whatsapp ->
                MemberUtils.sendWhatsApp(activity, item.cellphone, whatsappMethod, message)

            R.id.bel_selfoon ->
                MemberUtils.callPhone(activity, item.cellphone)

            R.id.stuur_epos ->                         // ← was missing
                MemberUtils.sendEmail(activity, item.email)

            R.id.bel_landlyn ->                        // ← was missing
                MemberUtils.callPhone(activity, item.landline)

            R.id.kopieer ->                            // ← was missing
                MemberUtils.copyToClipboard(activity, item)

            R.id.copy_to_contacts ->                   // ← was missing
                MemberUtils.saveToContacts(activity, item)

            R.id.voeg_nota_by ->
                openVoegNotaBy(item)

            R.id.stel_herinnering ->
                openStelHerinnering(item)
        }
    }

    /**
     * Launches the "Add Note" bottom sheet for the given member.
     *
     * @param item The target [MemberItem].
     */
    private fun openVoegNotaBy(item: MemberItem) {
        val guid = item.guid.takeIf { it.isNotBlank() } ?: run {
            if (BuildConfig.DEBUG) Log.w("QuickActionHelper", "openVoegNotaBy: guid is null/blank")
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
     * Launches the "Set Reminder" bottom sheet for the given member.
     *
     * @param item The target [MemberItem].
     */
    private fun openStelHerinnering(item: MemberItem) {
        val guid = item.guid.takeIf { it.isNotBlank() } ?: run {
            if (BuildConfig.DEBUG) Log.w(
                "QuickActionHelper",
                "openStelHerinnering: guid is null/blank"
            )
            return
        }
        StelHerinneringBottomSheet.newInstance(
            memberGuid = guid,
            familyHeadGuid = item.familyHead
        ).show(activity.supportFragmentManager, StelHerinneringBottomSheet.TAG)
    }


    // ─── Private UI Helpers ──────────────────────────────────────────────────

    /**
     * Dynamically inflates and configures a quick action button.
     *
     * @param parent The parent [ViewGroup] that will hold the button (used for layout params).
     * @param iconText Optional text/emoji string to render if no drawable is used.
     * @param iconDrawableRes Optional resource ID of drawable icon.
     * @param label Text label displayed below the icon.
     * @param onClick Action closure triggered on click.
     * @param iconColor Optional color override for the icon.
     * @return Prepared action [View].
     */
    private fun createActionButton(
        parent: ViewGroup,
        iconText: String? = null,
        iconDrawableRes: Int? = null,
        label: String,
        onClick: () -> Unit,
        iconColor: Int? = null
    ): View {
        // Inflate with the correct parent, attachToRoot = false
        val buttonView = LayoutInflater.from(activity)
            .inflate(R.layout.quick_action_button, parent, false)

        val iconImageView = buttonView.findViewById<ImageView>(R.id.icon_image)
        val iconTextView = buttonView.findViewById<TextView>(R.id.icon_text)
        val labelView = buttonView.findViewById<TextView>(R.id.action_label)

        iconImageView.visibility = View.GONE
        iconTextView.visibility = View.GONE

        if (iconDrawableRes != null) {
            val drawable = ContextCompat.getDrawable(activity, iconDrawableRes)?.mutate()
            if (drawable != null) {
                // Set drawable first
                iconImageView.setImageDrawable(drawable)
                // Apply color filter using the non-deprecated API
                if (iconColor != null) {
                    iconImageView.colorFilter =
                        PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
                }
                iconImageView.visibility = View.VISIBLE
            }
        } else if (iconText != null) {
            iconTextView.text = iconText
            if (iconColor != null) {
                iconTextView.setTextColor(iconColor)
            } else {
                iconTextView.setTextColor(
                    ContextCompat.getColor(activity, R.color.md_theme_onSurface)
                )
            }
            iconTextView.visibility = View.VISIBLE
        }

        labelView.text = label
        buttonView.setOnClickListener { onClick() }
        buttonView.contentDescription = label

        return buttonView
    }
}