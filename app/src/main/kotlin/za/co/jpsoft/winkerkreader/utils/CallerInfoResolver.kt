// File: utils/CallerInfoResolver.kt
package za.co.jpsoft.winkerkreader.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.data.WinkerkContract

object CallerInfoResolver {

    /**
     * Returns a display string for the given phone number.
     * Format: "Name (source) - number" if name found, otherwise just the number.
     * Source can be "Lidmaat", "Kontak", or none.
     *
     * Now a suspend function – runs on IO dispatcher.
     */
    suspend fun getCallerDisplayInfo(contentResolver: ContentResolver, phoneNumber: String?): String =
        withContext(Dispatchers.IO) {
            if (phoneNumber.isNullOrBlank() || phoneNumber == "Unknown Number") {
                return@withContext "Unknown Number"
            }

            val digitsOnly = phoneNumber.filter { it.isDigit() }
            val searchNumber = digitsOnly.takeLast(9)

            // 1. Try app's member database
            val memberName = lookupMemberName(contentResolver, searchNumber)
            if (memberName != null) {
                return@withContext "$memberName (Lidmaat) - $phoneNumber"
            }

            // 2. Try Android Contacts
            val contactName = lookupContactName(contentResolver, digitsOnly)
            if (contactName != null) {
                return@withContext "$contactName (Kontak) - $phoneNumber"
            }

            // 3. Fallback: just the number
            return@withContext phoneNumber
        }

    private suspend fun lookupMemberName(contentResolver: ContentResolver, searchNumber: String): String? =
        withContext(Dispatchers.IO) {
            if (searchNumber.isEmpty()) return@withContext null
            val queryUri = ContentUris.withAppendedId(WinkerkContract.winkerkEntry.CONTENT_FOON_URI, 0)
            val selection = """
                ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_INFO} FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
                WHERE (REPLACE([${WinkerkContract.winkerkEntry.LIDMATE_SELFOON}],' ','') LIKE '%$searchNumber')
                   OR (REPLACE([${WinkerkContract.winkerkEntry.LIDMATE_LANDLYN}],' ','') LIKE '%$searchNumber')
                   OR (REPLACE([${WinkerkContract.winkerkEntry.LIDMATE_WERKFOON}],' ','') LIKE '%$searchNumber')
            """.trimIndent()
            val cursor = contentResolver.query(queryUri, arrayOf(""), selection, null, null) ?: return@withContext null
            cursor.use {
                if (!it.moveToFirst()) return@withContext null
                val nameIdx = it.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)
                val surnameIdx = it.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_VAN)
                if (nameIdx < 0 || surnameIdx < 0) return@withContext null
                val firstName = it.getString(nameIdx) ?: return@withContext null
                val surname = it.getString(surnameIdx) ?: return@withContext null
                return@withContext "$firstName $surname".trim()
            }
        }

    private suspend fun lookupContactName(contentResolver: ContentResolver, phoneNumber: String): String? =
        withContext(Dispatchers.IO) {
            if (phoneNumber.isEmpty()) return@withContext null
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor = contentResolver.query(uri, projection, null, null, null) ?: return@withContext null
            cursor.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = it.getString(idx)
                        if (!name.isNullOrBlank()) return@withContext name
                    }
                }
                return@withContext null
            }
        }
}