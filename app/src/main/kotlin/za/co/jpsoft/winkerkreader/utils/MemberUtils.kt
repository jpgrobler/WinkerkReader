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
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.ui.activities.LidmaatDetailActivity
/**
 * Utility functions for common member actions: copying to clipboard,
 * creating a calendar note, and copying to device contacts.
 */
object MemberUtils {

    private const val TAG = "MemberUtils"

    /**
     * Copies the member's information (name, surname, phone, email, address)
     * to the system clipboard.
     *
     * @param context The context (used to get the clipboard service).
     * @param item    The member item data.
     */
    fun copyToClipboard(context: Context, item: MemberItem) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Log.e(TAG, "Clipboard service not available")
            return
        }

        val clipData = buildClipboardText(item)
        val clip = ClipData.newPlainText("Member Info", clipData)
        clipboard.setPrimaryClip(clip)
    }

    /**
     * Creates a calendar note (event) for the member. The event starts now
     * and lasts one hour, with the member's details in the description.
     *
     * @param context The context used to start the calendar activity.
     * @param item    The member item data.
     */
    fun createCalendarNote(context: Context, item: MemberItem) {
        val name = item.name
        val surname = item.surname
        val description = buildClipboardText(item)

        val intent = Intent().apply {
            type = "vnd.android.cursor.item/event"
            putExtra("beginTime", System.currentTimeMillis())
            putExtra("endTime", System.currentTimeMillis() + DateUtils.HOUR_IN_MILLIS)
            putExtra("title", "$name $surname")
            putExtra("description", description)
            action = Intent.ACTION_EDIT
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create calendar note", e)
        }
    }

    /**
     * Inserts the member as a new contact (or offers to edit an existing one)
     * using the system contacts app. Includes name, phone numbers, email,
     * address, birthday, and nickname.
     *
     * @param context The context used to start the contacts activity.
     * @param item    The member item data.
     */
    fun copyToContacts(context: Context, item: MemberItem) {
        val name = item.name
        val surname = item.surname
        val cellPhone = if (item.cellphone.isNotEmpty()) Utils.fixphonenumber(item.cellphone) else null
        val landline = if (item.landline.isNotEmpty()) Utils.fixphonenumber(item.landline) else null
        val email = item.email
        val address = item.address
        val birthday = item.birthday

        val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE

            // Basic info
            putExtra(ContactsContract.Intents.Insert.NAME, "$name, $surname")

            cellPhone?.let {
                putExtra(ContactsContract.Intents.Insert.PHONE, it)
                putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }

            landline?.let {
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it)
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_HOME)
            }

            email?.let {
                putExtra(ContactsContract.Intents.Insert.EMAIL, it)
            }

            if (!address.isNullOrEmpty()) {
                putExtra(ContactsContract.Intents.Insert.POSTAL, address.replace("\r\n", ", "))
            }

            // Birthday and nickname if available
            if (!birthday.isNullOrEmpty() && birthday.length >= 10) {
                val data = ArrayList<ContentValues>().apply {
                    // Birthday
                    add(ContentValues().apply {
                        put(ContactsContract.CommonDataKinds.Event.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                        put(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
                        put(ContactsContract.CommonDataKinds.Event.START_DATE, birthday.substring(0, 10))
                    })
                    // Nickname
                    add(ContentValues().apply {
                        put(ContactsContract.CommonDataKinds.Nickname.MIMETYPE, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
                        put(ContactsContract.CommonDataKinds.Nickname.TYPE, ContactsContract.CommonDataKinds.Nickname.TYPE_SHORT_NAME)
                        put(ContactsContract.CommonDataKinds.Nickname.NAME, name)
                    })
                }
                putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data)
            }
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to contacts", e)
        }
    }

    fun callPhone(context: Context, phoneNumber: String?) {
        if (phoneNumber.isNullOrEmpty()) return
        val formatted = Utils.fixphonenumber(phoneNumber)
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$formatted") }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error making call", e)
        }
    }

    fun sendSms(context: Context, phoneNumber: String?) {
        if (phoneNumber.isNullOrEmpty()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("sms:$phoneNumber")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    type = "vnd.android-dir/mms-sms"
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
        }
    }

    fun sendWhatsApp(context: Context, phoneNumber: String?, method: Int = 1) {
        if (phoneNumber.isNullOrEmpty()) return
        val phone = Utils.fixphonenumber(phoneNumber)
        try {
            val intent = when (method) {
                1 -> {
                    val uri = Uri.parse("smsto: $phone")
                    Intent(Intent.ACTION_SENDTO, uri).apply { `package` = "com.whatsapp" }
                }
                2 -> {
                    val url = "https://api.whatsapp.com/send?phone=$phone"
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(url)
                        `package` = "com.whatsapp"
                    }
                }
                else -> {
                    Intent("android.intent.action.MAIN").apply {
                        action = Intent.ACTION_SEND
                        `package` = "com.whatsapp"
                        type = "text/plain"
                        putExtra("jid", "${phone}@s.whatsapp.net")
                    }
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WhatsApp (method $method)", e)
            Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendEmail(context: Context, email: String?) {
        if (email.isNullOrEmpty()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("mailto:$email") }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email", e)
        }
    }

    fun openMemberDetail(context: Context, item: MemberItem, recordStatus: String) {
        try {
            val intent = Intent(context, LidmaatDetailActivity::class.java).apply {
                data = ContentUris.withAppendedId(
                    za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.CONTENT_URI,
                    item.id
                )
                putExtra("RECORD_STATUS", recordStatus)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening member detail", e)
        }
    }

    // -------------------------------------------------------------------------
    // Private helper
    // -------------------------------------------------------------------------

    private fun buildClipboardText(item: MemberItem): String {
        val builder = StringBuilder()

        fun add(label: String, value: String?) {
            if (!value.isNullOrEmpty()) {
                if (builder.isNotEmpty()) builder.append("\r\n")
                builder.append("$label: $value")
            }
        }

        add("Naam", item.name)
        add("Van", item.surname)
        add("Selfoon", if (item.cellphone.isNotEmpty()) Utils.fixphonenumber(item.cellphone) else null)
        add("Landlyn", if (item.landline.isNotEmpty()) Utils.fixphonenumber(item.landline) else null)
        add("Epos", item.email)
        add("Adres", item.address)

        return builder.toString()
    }
}