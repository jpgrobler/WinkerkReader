package za.co.jpsoft.winkerkreader.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.ui.activities.LidmaatDetailActivity
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel

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
                Log.e(TAG, "Error handling member action: $actionId", e)
                Toast.makeText(activity, "Error performing action", Toast.LENGTH_SHORT).show()
                false
            }
}

