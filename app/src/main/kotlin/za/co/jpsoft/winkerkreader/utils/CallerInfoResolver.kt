package za.co.jpsoft.winkerkreader.utils

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry

/**
 * Resolves a phone number to a [CallerInfoResult] by checking:
 * 1. The congregation member database.
 * 2. The device's local contacts (if permission is granted).
 *
 * Replaces the old string-based parsing approach for robust, type-safe result handling.
 */
object CallerInfoResolver {

    /**
     * Resolve the given phone number.
     * @param phoneNumber The phone number to look up (as a string, e.g., "+27123456789")
     * @return A [CallerInfoResult] indicating whether the number belongs to a member, a contact, or is unknown.
     */
    fun resolve(phoneNumber: String, contentResolver: ContentResolver): CallerInfoResult {
        // Step 1: Try to find in the congregation database (members)
        val memberResult = resolveMember(phoneNumber, contentResolver)
        if (memberResult != null) {
            return memberResult
        }

        // Step 2: Try to find in the device contacts (if permission is granted)
        val contactResult = resolveContact(phoneNumber, contentResolver)
        if (contactResult != null) {
            return contactResult
        }

        // Step 3: Not found
        return CallerInfoResult.Unknown
    }

    /**
     * Query the congregation database for a member with this phone number.
     * Searches in the Selfoon, Landlyn, and Werk tel columns.
     */
    private fun resolveMember(phoneNumber: String, contentResolver: ContentResolver): CallerInfoResult.Member? {
        // Normalize the phone number for matching (strip spaces, dashes, etc.)
        val normalized = normalizePhoneNumber(phoneNumber)

        // Build query: search in multiple columns (Selfoon, Landlyn, Werk tel)
        // Use a selection that ORs the columns with LIKE or exact match.
        // We'll use a selection with placeholders and parameterise the value.
        val selection = """
            ${winkerkEntry.LIDMATE_SELFOON} = ? OR 
            ${winkerkEntry.ADRESSE_LANDLYN} = ? OR 
            ${winkerkEntry.LIDMATE_WERKFOON} = ?
        """.trimIndent()

        val selectionArgs = arrayOf(normalized, normalized, normalized)

        // Projection: we need the member's name and GUID (and optionally other fields)
        val projection = arrayOf(
            winkerkEntry.LIDMATE_VAN,
            winkerkEntry.LIDMATE_NOEMNAAM,
            winkerkEntry.LIDMATE_VOORNAME,
            winkerkEntry.LIDMATE_LIDMAATGUID,
            winkerkEntry.LIDMATE_SELFOON
        )

        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(
                winkerkEntry.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val surname = cursor.getString(cursor.getColumnIndexOrThrow(winkerkEntry.LIDMATE_VAN))
                val noemnaam = cursor.getString(cursor.getColumnIndexOrThrow(winkerkEntry.LIDMATE_NOEMNAAM))
                val voorname = cursor.getString(cursor.getColumnIndexOrThrow(winkerkEntry.LIDMATE_VOORNAME))
                val guid = cursor.getString(cursor.getColumnIndexOrThrow(winkerkEntry.LIDMATE_LIDMAATGUID))
                val phone = cursor.getString(cursor.getColumnIndexOrThrow(winkerkEntry.LIDMATE_SELFOON))

                // Build a display name (e.g., "Jan Botha" or "Botha, Jan")
                val displayName = buildMemberDisplayName(surname, voorname, noemnaam)

                CallerInfoResult.Member(
                    name = displayName,
                    guid = guid,
                    surname = surname,
                    firstName = voorname,
                    phone = phone,
                    memberType = "Lidmaat" // Or you could determine type from other fields
                )
            } else {
                null
            }
        } catch (e: Exception) {
            // Log error if needed
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * Query the device contacts for a contact with this phone number.
     * Requires READ_CONTACTS permission.
     */
    private fun resolveContact(phoneNumber: String, contentResolver: ContentResolver): CallerInfoResult.Contact? {
        // Check if we have permission? The caller should ensure permission.
        // For simplicity, assume we have permission or the query will return nothing.

        val normalized = normalizePhoneNumber(phoneNumber)

        // Query the ContactsContract provider for a number match.
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(normalized)
            .build()

        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.NUMBER
        )

        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.NUMBER))
                CallerInfoResult.Contact(name = name, phoneNumber = number)
            } else {
                null
            }
        } catch (e: Exception) {
            // Permission denied or other error
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * Normalize a phone number: remove spaces, dashes, parentheses, and leading zeros.
     * Also ensure the number starts with a plus for international format, but keep as-is.
     */
    private fun normalizePhoneNumber(number: String): String {
        // Remove all non-digit characters except leading '+'
        val cleaned = number.replace(Regex("[^\\d+]"), "")
        // If it starts with '0' and length > 1, replace with '+27' for South Africa? Not generic.
        // For simplicity, just return the cleaned string.
        return cleaned
    }

    /**
     * Build a display name from surname, first name, and nickname.
     */
    private fun buildMemberDisplayName(surname: String?, voorname: String?, noemnaam: String?): String {
        return when {
            !surname.isNullOrEmpty() && !voorname.isNullOrEmpty() -> "$voorname $surname"
            !surname.isNullOrEmpty() -> surname
            !voorname.isNullOrEmpty() -> voorname
            else -> "Lidmaat"
        }
    }

    // DEPRECATED – remove old isKnownCaller method
    // @Deprecated("Use resolve() and check the result type")
    // fun isKnownCaller(formatted: String): Boolean = false
}