package za.co.jpsoft.winkerkreader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.text.format.DateUtils
import android.util.Log
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry

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
     * @param cursor  A cursor positioned at the desired member row.
     */
    fun copyToClipboard(context: Context, cursor: Cursor) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Log.e(TAG, "Clipboard service not available")
            return
        }

        val clipData = buildClipboardText(cursor)
        val clip = ClipData.newPlainText("Member Info", clipData)
        clipboard.setPrimaryClip(clip)
    }

    /**
     * Creates a calendar note (event) for the member. The event starts now
     * and lasts one hour, with the member's details in the description.
     *
     * @param context The context used to start the calendar activity.
     * @param cursor  A cursor positioned at the desired member row.
     */
    fun createCalendarNote(context: Context, cursor: Cursor) {
        val name = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)
        val surname = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN)
        val description = buildClipboardText(cursor)

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
     * @param cursor  A cursor positioned at the desired member row.
     */
    fun copyToContacts(context: Context, cursor: Cursor) {
        val name = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)
        val surname = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN)
        val cellPhone = cursor.getStringOrNull(winkerkEntry.LIDMATE_SELFOON)?.let { Utils.fixphonenumber(it) }
        val landline = cursor.getStringOrNull(winkerkEntry.LIDMATE_LANDLYN)?.let { Utils.fixphonenumber(it) }
        val email = cursor.getStringOrNull(winkerkEntry.LIDMATE_EPOS)
        val address = cursor.getStringOrNull(winkerkEntry.LIDMATE_STRAATADRES)
        val birthday = cursor.getStringOrNull(winkerkEntry.LIDMATE_GEBOORTEDATUM)

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

    // -------------------------------------------------------------------------
    // Private helper
    // -------------------------------------------------------------------------

    private fun buildClipboardText(cursor: Cursor): String {
        val builder = StringBuilder()

        fun add(label: String, value: String?) {
            if (!value.isNullOrEmpty()) {
                if (builder.isNotEmpty()) builder.append("\r\n")
                builder.append("$label: $value")
            }
        }

        add("Naam", cursor.getStringOrNull(winkerkEntry.LIDMATE_NOEMNAAM))
        add("Van", cursor.getStringOrNull(winkerkEntry.LIDMATE_VAN))
        add("Selfoon", cursor.getStringOrNull(winkerkEntry.LIDMATE_SELFOON)?.let { Utils.fixphonenumber(it) })
        add("Landlyn", cursor.getStringOrNull(winkerkEntry.LIDMATE_LANDLYN)?.let { Utils.fixphonenumber(it) })
        add("Epos", cursor.getStringOrNull(winkerkEntry.LIDMATE_EPOS))
        add("Adres", cursor.getStringOrNull(winkerkEntry.LIDMATE_STRAATADRES))

        return builder.toString()
    }
}