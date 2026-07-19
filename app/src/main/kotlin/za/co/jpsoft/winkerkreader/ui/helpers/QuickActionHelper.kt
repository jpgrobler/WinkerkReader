package za.co.jpsoft.winkerkreader.ui.helpers

import android.graphics.PorterDuff
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
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
 */
class QuickActionHelper(
    private val activity: AppCompatActivity,
    private val settingsManager: SettingsManager
) {

    private var quickActionsPopup: PopupWindow? = null
    var expandCallback: ((View, MemberItem) -> Unit)? = null

    /**
     * Show the horizontal quick action popup anchored to the given view.
     * @param message Optional personalized message for SMS/WhatsApp actions.
     */
    fun showQuickActions(anchor: View, item: MemberItem, message: String? = null) {
        dismiss()

        val inflater = LayoutInflater.from(activity)
        val popupView = inflater.inflate(R.layout.popup_quick_actions, null)
        val container = popupView.findViewById<LinearLayout>(R.id.quick_actions_container)

        val hasPhone = item.cellphone.isNotEmpty()

        val dividerColor = MaterialColors.getColor(
            activity,
            com.google.android.material.R.attr.colorOutlineVariant,
            0
        )

        fun addActionButton(
            iconText: String? = null,
            iconDrawableRes: Int? = null,
            label: String,
            onClick: () -> Unit,
            iconColor: Int? = null
        ) {
            if (container.childCount > 0) {
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
            val button = createActionButton(iconText, iconDrawableRes, label, onClick, iconColor)
            container.addView(button)
        }

        // 1. SMS – green icon (no icon colour, just the emoji)
        if (hasPhone) {
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
        // In showQuickActions, determine the WhatsApp method:
        val whatsappMethod = when {
            settingsManager.whatsapp1 -> 1
            settingsManager.whatsapp2 -> 2
            settingsManager.whatsapp3 -> 3
            else -> 0 // disabled
        }

        // Then when adding the WhatsApp button:
        if (hasPhone && whatsappMethod != 0) {
            val formattedPhone = Utils.fixphonenumber(item.cellphone) ?: item.cellphone
            if (ContactRepository.isWhatsAppContact(formattedPhone)) {
                addActionButton(
                    iconDrawableRes = R.drawable.whatsapp,
                    label = "WhatsApp",
                    onClick = {
                        // Pass the method number
                        handleQuickAction(R.id.stuur_whatsapp, item, message, whatsappMethod)
                        dismiss()
                    }
                )
            }
        }

        // 3. Call – default color
        if (hasPhone) {
            addActionButton(
                iconText = "📱",
                label = "Bel",
                onClick = {
                    handleQuickAction(R.id.bel_selfoon, item)
                    dismiss()
                }
            )
        }

        // 4. Note – always visible
        addActionButton(
            iconText = "📝",
            label = "Nota",
            onClick = {
                handleQuickAction(R.id.voeg_nota_by, item)
                dismiss()
            }
        )

        // 5. Reminder – drawable with primary colour
        addActionButton(
            iconDrawableRes = R.drawable.ic_bediening,
            label = "Herinner",
            onClick = {
                handleQuickAction(R.id.stel_herinnering, item)
                dismiss()
            }
        )

        // 6. Expand – always visible
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
        val popupHeight = popupView.measuredHeight.takeIf { it > 0 } ?: 10

        val yOffset = if (location[1] + anchor.height + popupHeight > screenHeight) {
            -anchor.height - popupHeight
        } else {
            0
        }

        popup.showAsDropDown(anchor, 0, yOffset)
        quickActionsPopup = popup
    }

    private fun createActionButton(
        iconText: String? = null,
        iconDrawableRes: Int? = null,
        label: String,
        onClick: () -> Unit,
        iconColor: Int? = null
    ): View {
        val buttonView = LayoutInflater.from(activity)
            .inflate(R.layout.quick_action_button, null)

        val iconImageView = buttonView.findViewById<ImageView>(R.id.icon_image)
        val iconTextView = buttonView.findViewById<TextView>(R.id.icon_text)
        val labelView = buttonView.findViewById<TextView>(R.id.action_label)

        // Reset visibility
        iconImageView.visibility = View.GONE
        iconTextView.visibility = View.GONE

        if (iconDrawableRes != null) {
            val drawable = ContextCompat.getDrawable(activity, iconDrawableRes)?.mutate()
            if (drawable != null) {
                if (iconColor != null) {
                    drawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
                }
                iconImageView.setImageDrawable(drawable)
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

    // ─── Handle quick action clicks ──────────────────────────────────────────

    private fun handleQuickAction(
        actionId: Int,
        item: MemberItem,
        message: String? = null,
        whatsappMethod: Int = 1  // default to method 1
    ) {
        when (actionId) {
            R.id.stuur_sms -> MemberUtils.sendSms(activity, item.cellphone, message)
            R.id.stuur_whatsapp -> MemberUtils.sendWhatsApp(
                activity,
                item.cellphone,
                whatsappMethod,
                message
            )

            R.id.bel_selfoon -> MemberUtils.callPhone(activity, item.cellphone)
            R.id.voeg_nota_by -> openVoegNotaBy(item)
            R.id.stel_herinnering -> openStelHerinnering(item)
        }
    }

    // ─── Open sheets ──────────────────────────────────────────────────────────

    private fun openVoegNotaBy(item: MemberItem) {
        val guid = item.guid?.takeIf { it.isNotBlank() } ?: run {
            Log.w("QuickActionHelper", "openVoegNotaBy: guid is null/blank")
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

    private fun openStelHerinnering(item: MemberItem) {
        val guid = item.guid?.takeIf { it.isNotBlank() } ?: run {
            Log.w("QuickActionHelper", "openStelHerinnering: guid is null/blank")
            return
        }
        StelHerinneringBottomSheet.newInstance(
            memberGuid = guid,
            familyHeadGuid = item.familyHead
        ).show(activity.supportFragmentManager, StelHerinneringBottomSheet.TAG)
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    fun dismiss() {
        quickActionsPopup?.dismiss()
        quickActionsPopup = null
    }
}