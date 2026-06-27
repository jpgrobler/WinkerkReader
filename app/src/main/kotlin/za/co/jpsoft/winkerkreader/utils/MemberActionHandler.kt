package za.co.jpsoft.winkerkreader.utils

import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.ui.bottomsheets.StelHerinneringBottomSheet
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.BuildConfig

class MemberActionHandler(
    private val activity: AppCompatActivity,
    private val item: MemberItem,
    private val viewModel: MemberViewModel
) {
    companion object {
        private const val TAG = "MemberActionHandler"
    }

    fun handleAction(actionId: Int): Boolean =
            try {
                when (actionId) {
                    R.id.kyk_lidmaat_detail -> {
                        MemberUtils.openMemberDetail(activity, item, viewModel.recordStatus)
                        true
                    }
                    R.id.stel_herinnering -> {
                        StelHerinneringBottomSheet.newInstance(item.guid)
                            .show(
                                activity.supportFragmentManager,
                                StelHerinneringBottomSheet.TAG
                            )
                        true
                    }
                    R.id.bel_selfoon -> {
                        MemberUtils.callPhone(activity, item.cellphone)
                        true
                    }
                    R.id.bel_landlyn -> {
                        MemberUtils.callPhone(activity, item.landline)
                        true
                    }
                    R.id.stuur_sms -> {
                        MemberUtils.sendSms(activity, item.cellphone)
                        true
                    }
                    R.id.stuur_whatsapp -> {
                        MemberUtils.sendWhatsApp(activity, item.cellphone, 1)
                        true
                    }
                    R.id.stuur_whatsapp2 -> {
                        MemberUtils.sendWhatsApp(activity, item.cellphone, 2)
                        true
                    }
                    R.id.stuur_whatsapp3 -> {
                        MemberUtils.sendWhatsApp(activity, item.cellphone, 3)
                        true
                    }
                    R.id.stuur_epos -> {
                        MemberUtils.sendEmail(activity, item.email)
                        true
                    }
                    R.id.kopieer -> {
                        MemberUtils.copyToClipboard(activity, item)
                        Toast.makeText(activity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.nota -> {
                        MemberUtils.createCalendarNote(activity, item)
                        true
                    }
                    R.id.copy_to_contacts -> {
                        MemberUtils.copyToContacts(activity, item)
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error handling member action: $actionId", e)
                Toast.makeText(activity, "Error performing action", Toast.LENGTH_SHORT).show()
                false
            }
}

