package za.co.jpsoft.winkerkreader.utils


import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.ui.activities.LidmaatDetailActivity
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import java.net.URLEncoder

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
            if (BuildConfig.DEBUG) Log.e(TAG, "Clipboard service not available")
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
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to create calendar note", e)
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
        val cellPhone =
            if (item.cellphone.isNotEmpty()) fixphonenumber(item.cellphone) else null
        val landline = if (item.landline.isNotEmpty()) fixphonenumber(item.landline) else null
        val email = item.email
        val address = item.address
        val birthday = item.birthday

        val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE

            // Basic info
            putExtra(ContactsContract.Intents.Insert.NAME, "$name, $surname")

            cellPhone?.let {
                putExtra(ContactsContract.Intents.Insert.PHONE, it)
                putExtra(
                    ContactsContract.Intents.Insert.PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                )
            }

            landline?.let {
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it)
                putExtra(
                    ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                )
            }

            email.let {
                putExtra(ContactsContract.Intents.Insert.EMAIL, it)
            }

            if (address.isNotEmpty()) {
                putExtra(ContactsContract.Intents.Insert.POSTAL, address.replace("\r\n", ", "))
            }

            // Birthday and nickname if available
            if (birthday.isNotEmpty() && birthday.length >= 10) {
                val data = ArrayList<ContentValues>().apply {
                    // Birthday
                    add(ContentValues().apply {
                        put(
                            ContactsContract.CommonDataKinds.Event.MIMETYPE,
                            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE
                        )
                        put(
                            ContactsContract.CommonDataKinds.Event.TYPE,
                            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
                        )
                        put(
                            ContactsContract.CommonDataKinds.Event.START_DATE,
                            birthday.substring(0, 10)
                        )
                    })
                    // Nickname
                    add(ContentValues().apply {
                        put(
                            ContactsContract.CommonDataKinds.Nickname.MIMETYPE,
                            ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE
                        )
                        put(
                            ContactsContract.CommonDataKinds.Nickname.TYPE,
                            ContactsContract.CommonDataKinds.Nickname.TYPE_SHORT_NAME
                        )
                        put(ContactsContract.CommonDataKinds.Nickname.NAME, name)
                    })
                }
                putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data)
            }
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to copy to contacts", e)
        }
    }

    fun callPhone(context: Context, phoneNumber: String?) {
        if (phoneNumber.isNullOrEmpty()) return
        val formatted = fixphonenumber(phoneNumber)
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply { data = "tel:$formatted".toUri() }
            context.startActivity(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error making call", e)
        }
    }

    fun sendSms(context: Context, phoneNumber: String?, message: String? = null) {
        if (phoneNumber.isNullOrEmpty()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = if (message.isNullOrEmpty()) {
                    "sms:$phoneNumber".toUri()
                } else {
                    "sms:$phoneNumber?body=${Uri.encode(message)}".toUri()
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error sending SMS", e)
        }
    }

    // MemberUtils.kt – add these methods

    /**
     * Send a WhatsApp message using the selected method.
     * @param method 1, 2, or 3 (as per settings)
     * @param message Optional pre-filled message
     */
    fun sendWhatsApp(
        context: Context,
        phoneNumber: String?,
        method: Int = 1,
        message: String? = null
    ): Boolean {
        if (phoneNumber.isNullOrEmpty()) return false
        val phone = fixphonenumber(phoneNumber) ?: return false
        return try {
            when (method) {
                1 -> sendWhatsAppMethod1(context, phone, message)
                2 -> sendWhatsAppMethod2(context, phone, message)
                3 -> sendWhatsAppMethod3(context, phone, message)
                else -> false
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "WhatsApp send failed", e)
            Toast.makeText(context, "WhatsApp not installed or error occurred", Toast.LENGTH_SHORT)
                .show()
            false
        }
    }

    private fun sendWhatsAppMethod1(context: Context, phone: String, message: String?): Boolean {
        val uri = "smsto: $phone".toUri()
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            `package` = "com.whatsapp"
            if (!message.isNullOrEmpty()) {
                putExtra("sms_body", message)
                putExtra(Intent.EXTRA_TEXT, message)
            }
        }
        context.startActivity(Intent.createChooser(intent, ""))
        return true
    }

    private fun sendWhatsAppMethod2(context: Context, phone: String, message: String?): Boolean {
        val encoded = if (!message.isNullOrEmpty()) URLEncoder.encode(message, "UTF-8") else ""
        val url =
            "https://api.whatsapp.com/send?phone=$phone${if (encoded.isNotEmpty()) "&text=$encoded" else ""}"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply { `package` = "com.whatsapp" }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return true
        }
        return false
    }

    private fun sendWhatsAppMethod3(context: Context, phone: String, message: String?): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            `package` = "com.whatsapp"
            type = "text/plain"
            if (!message.isNullOrEmpty()) {
                putExtra(Intent.EXTRA_TEXT, message)
            }
            putExtra("jid", "${phone}@s.whatsapp.net")
        }
        context.startActivity(intent)
        return true
    }

    fun sendEmail(context: Context, email: String?) {
        if (email.isNullOrEmpty()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply { data = "mailto:$email".toUri() }
            context.startActivity(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error sending email", e)
        }
    }

    fun openMemberDetail(context: Context, item: MemberItem, recordStatus: String) {
        try {
            if (BuildConfig.DEBUG) Log.d(
                "MemberUtils",
                "Opening detail for ${item.name} ${item.surname}, GUID = ${item.guid}"
            )
            val intent = Intent(context, LidmaatDetailActivity::class.java).apply {
                data = ContentUris.withAppendedId(
                    za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.CONTENT_URI,
                    item.id
                )
                putExtra("RECORD_STATUS", recordStatus)
                putExtra(LidmaatDetailActivity.EXTRA_MEMBER_GUID, item.guid)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error opening member detail", e)
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
        add(
            "Selfoon",
            if (item.cellphone.isNotEmpty()) fixphonenumber(item.cellphone) else null
        )
        add(
            "Landlyn",
            if (item.landline.isNotEmpty()) fixphonenumber(item.landline) else null
        )
        add("Epos", item.email)
        add("Adres", item.address)

        return builder.toString()
    }
}