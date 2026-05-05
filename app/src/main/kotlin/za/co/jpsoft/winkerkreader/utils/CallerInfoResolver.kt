// utils/CallerInfoResolver.kt
package za.co.jpsoft.winkerkreader.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract

object CallerInfoResolver {

    /**
     * Returns a display string for the given phone number.
     * Format: "Name (source) - number" if name found, otherwise just the number.
     * Source can be "Lidmaat", "Kontak", or none.
     */
    fun getCallerDisplayInfo(contentResolver: ContentResolver, phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank() || phoneNumber == "Unknown Number") {
            return "Unknown Number"
        }

        // Clean number: keep only digits, take last 9 digits for matching
        val digitsOnly = phoneNumber.filter { it.isDigit() }
        val searchNumber = digitsOnly.takeLast(9)

        // 1. Try app's member database
        val memberName = lookupMemberName(contentResolver, searchNumber)
        if (memberName != null) {
            return "$memberName (Lidmaat) - $phoneNumber"
        }

        // 2. Try Android Contacts
        val contactName = lookupContactName(contentResolver, digitsOnly)
        if (contactName != null) {
            return "$contactName (Kontak) - $phoneNumber"
        }

        // 3. Fallback: just the number
        return phoneNumber
    }

    private fun lookupMemberName(contentResolver: ContentResolver, searchNumber: String): String? {
        if (searchNumber.isEmpty()) return null
        val queryUri = ContentUris.withAppendedId(WinkerkContract.winkerkEntry.CONTENT_FOON_URI, 0)
        val selection = """
            ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_INFO} FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
            WHERE (REPLACE([${WinkerkContract.winkerkEntry.LIDMATE_SELFOON}],' ','') LIKE '%$searchNumber')
               OR (REPLACE([${WinkerkContract.winkerkEntry.LIDMATE_LANDLYN}],' ','') LIKE '%$searchNumber')
               OR (REPLACE([${WinkerkContract.winkerkEntry.LIDMATE_WERKFOON}],' ','') LIKE '%$searchNumber')
        """.trimIndent()
        val cursor = contentResolver.query(queryUri, arrayOf(""), selection, null, null) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val nameIdx = it.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)
            val surnameIdx = it.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_VAN)
            if (nameIdx < 0 || surnameIdx < 0) return null
            val firstName = it.getString(nameIdx) ?: return null
            val surname = it.getString(surnameIdx) ?: return null
            return "$firstName $surname".trim()
        }
    }

    private fun lookupContactName(contentResolver: ContentResolver, phoneNumber: String): String? {
        if (phoneNumber.isEmpty()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        val cursor = contentResolver.query(uri, projection, null, null, null) ?: return null
        cursor.use {
            if (it.moveToFirst()) {
                val name = it.getString(it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
                if (!name.isNullOrBlank()) return name
            }
        }
        return null
    }
}