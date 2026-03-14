package za.co.jpsoft.winkerkreader

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
import android.content.ClipData
import android.content.ClipboardManager
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry

class MemberActionHandler(
    private val activity: AppCompatActivity,
    private val cursor: Cursor
) {
    companion object {
        private const val TAG = "MemberActionHandler"
    }

    fun handleAction(actionId: Int): Boolean = try {
        when (actionId) {
            R.id.kyk_lidmaat_detail -> openMemberDetail()
            R.id.bel_selfoon -> callCellPhone()
            R.id.bel_landlyn -> callLandline()
            R.id.stuur_sms -> sendSms()
            R.id.stuur_whatsapp -> sendWhatsApp1()
            R.id.stuur_whatsapp2 -> sendWhatsApp2()
            R.id.stuur_whatsapp3 -> sendWhatsApp3()
            R.id.stuur_epos -> sendEmail()
            R.id.kopieer -> {
                MemberUtils.copyToClipboard(activity, cursor)
                Toast.makeText(activity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.nota -> {
                MemberUtils.createCalendarNote(activity, cursor)
                true
            }
            R.id.copy_to_contacts -> {
                MemberUtils.copyToContacts(activity, cursor)
                true
            }
            else -> false
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error handling member action: $actionId", e)
        Toast.makeText(activity, "Error performing action", Toast.LENGTH_SHORT).show()
        false
    }

    private fun openMemberDetail(): Boolean {
        return try {
            val memberId = cursor.getIntOrDefault("_id", -1)
            if (memberId == -1) return false

            val intent = Intent(activity, lidmaat_detail_Activity::class.java).apply {
                data = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, memberId.toLong())
            }
            winkerkEntry.LIDMAATID = memberId
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening member detail", e)
            false
        }
    }

    private fun callCellPhone(): Boolean {
        val phone = getFormattedPhoneNumber(winkerkEntry.LIDMATE_SELFOON)
        return makeCall(phone)
    }

    private fun callLandline(): Boolean {
        val phone = getFormattedPhoneNumber(winkerkEntry.LIDMATE_LANDLYN)
        return makeCall(phone)
    }

    private fun makeCall(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrEmpty()) return false
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error making call", e)
            false
        }
    }

    private fun sendSms(): Boolean {
        val phone = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_SELFOON)
        if (phone.isEmpty()) return false

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("sms:$phone")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    type = "vnd.android-dir/mms-sms"
                }
            }
            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(intent)
                true
            } else {
                Log.e(TAG, "No SMS app available")
                Toast.makeText(activity, "No SMS app found", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
            Toast.makeText(activity, "Failed to open SMS app", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun sendWhatsApp1(): Boolean {
        val phone = getFormattedPhoneNumber(winkerkEntry.LIDMATE_SELFOON)
        if (phone.isNullOrEmpty()) return false

        return try {
            val uri = Uri.parse("smsto: $phone")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                `package` = "com.whatsapp"
            }
            activity.startActivity(Intent.createChooser(intent, ""))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WhatsApp message (method 1)", e)
            Toast.makeText(activity, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun sendWhatsApp2(): Boolean {
        val phone = getFormattedPhoneNumber(winkerkEntry.LIDMATE_SELFOON)
        if (phone.isNullOrEmpty()) return false

        return try {
            val url = "https://api.whatsapp.com/send?phone=$phone"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                `package` = "com.whatsapp"
            }
            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(intent)
                true
            } else {
                Toast.makeText(activity, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WhatsApp message (method 2)", e)
            Toast.makeText(activity, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun sendWhatsApp3(): Boolean {
        val phone = getFormattedPhoneNumber(winkerkEntry.LIDMATE_SELFOON)
        if (phone.isNullOrEmpty()) return false

        return try {
            val intent = Intent("android.intent.action.MAIN").apply {
                action = Intent.ACTION_SEND
                `package` = "com.whatsapp"
                type = "text/plain"
                putExtra("jid", "${phone}@s.whatsapp.net")
            }
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WhatsApp message (method 3)", e)
            Toast.makeText(activity, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun sendEmail(): Boolean {
        val email = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_EPOS)
        if (email.isEmpty()) return false

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("mailto:$email")
            }
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email", e)
            false
        }
    }

    private fun copyToClipboard(): Boolean {
        return try {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false.also { Toast.makeText(activity, "Clipboard not available", Toast.LENGTH_SHORT).show() }

            val clipData = buildClipboardData()
            val clip = ClipData.newPlainText("Member Info", clipData)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(activity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard", e)
            false
        }
    }

    private fun buildClipboardData(): String {
        val builder = StringBuilder()

        fun add(label: String, value: String?) {
            if (!value.isNullOrEmpty()) {
                builder.append("\r\n").append(label).append(": ").append(value)
            }
        }

        add("Naam", cursor.getStringOrNull(winkerkEntry.LIDMATE_NOEMNAAM))
        add("Van", cursor.getStringOrNull(winkerkEntry.LIDMATE_VAN))
        add("Selfoon", getFormattedPhoneNumber(winkerkEntry.LIDMATE_SELFOON))
        add("Landlyn", getFormattedPhoneNumber(winkerkEntry.LIDMATE_LANDLYN))
        add("Epos", cursor.getStringOrNull(winkerkEntry.LIDMATE_EPOS))
        add("Adres", cursor.getStringOrNull(winkerkEntry.LIDMATE_STRAATADRES))

        return builder.toString().replace("\r\n\r\n", "\r\n")
    }

    private fun createCalendarNote(): Boolean {
        return try {
            val intent = Intent().apply {
                type = "vnd.android.cursor.item/event"
                putExtra("beginTime", System.currentTimeMillis())
                putExtra("endTime", System.currentTimeMillis() + DateUtils.HOUR_IN_MILLIS)
                putExtra("title", "${cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)} ${cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN)}")
                putExtra("description", buildClipboardData())
                action = Intent.ACTION_EDIT
            }
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating calendar note", e)
            false
        }
    }

    private fun copyToContacts(): Boolean {
        return try {
            val name = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)
            val surname = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN)
            val cellPhone = getFormattedPhoneNumber(winkerkEntry.LIDMATE_SELFOON)
            val landline = getFormattedPhoneNumber(winkerkEntry.LIDMATE_LANDLYN)
            val email = cursor.getStringOrNull(winkerkEntry.LIDMATE_EPOS)
            val address = cursor.getStringOrNull(winkerkEntry.LIDMATE_STRAATADRES)
            val birthday = cursor.getStringOrNull(winkerkEntry.LIDMATE_GEBOORTEDATUM)

            val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE

                putExtra(ContactsContract.Intents.Insert.NAME, "$name, $surname")
                putExtra(ContactsContract.Intents.Insert.PHONE, cellPhone)
                putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, landline)
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_HOME)
                putExtra(ContactsContract.Intents.Insert.EMAIL, email)

                if (!address.isNullOrEmpty()) {
                    putExtra(ContactsContract.Intents.Insert.POSTAL, address.replace("\r\n", ", "))
                }

                if (!birthday.isNullOrEmpty() && birthday.length >= 10) {
                    val data = ArrayList<ContentValues>().apply {
                        add(ContentValues().apply {
                            put(ContactsContract.CommonDataKinds.Event.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                            put(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
                            put(ContactsContract.CommonDataKinds.Event.START_DATE, birthday.substring(0, 10))
                        })
                        add(ContentValues().apply {
                            put(ContactsContract.CommonDataKinds.Nickname.MIMETYPE, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
                            put(ContactsContract.CommonDataKinds.Nickname.TYPE, ContactsContract.CommonDataKinds.Nickname.TYPE_SHORT_NAME)
                            put(ContactsContract.CommonDataKinds.Nickname.NAME, name)
                        })
                    }
                    putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data)
                }
            }

            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to contacts", e)
            false
        }
    }

    private fun getString(columnName: String): String? = cursor.getStringOrNull(columnName)

    private fun getFormattedPhoneNumber(columnName: String): String? {
        val phone = cursor.getStringOrNull(columnName)
        return if (!phone.isNullOrEmpty()) fixphonenumber(phone) else null
    }
}