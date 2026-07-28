// CallerNameResolver.kt
package za.co.jpsoft.winkerkreader.utils

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig

object CallerNameResolver {

    private const val TAG = "CallerNameResolver"

    /**
     * Resolves a phone number to the best available display name.
     * Tries:
     * 1. App's member database (via CallerInfoResolver)
     * 2. System contacts (via PhoneLookup)
     *
     * @param number The phone number to resolve (can be null or blank)
     * @param contentResolver The ContentResolver to query contacts
     * @return The best display name, or null if none found
     */
    fun resolve(number: String?, context: Context): String? {
        if (number.isNullOrBlank() || number == "Unknown Number" || number == "null") {
            if (BuildConfig.DEBUG) Log.d(TAG, "Invalid number, skipping resolution")
            return null
        }

        val result = CallerInfoResolver.resolve(number, context)
        return when (result) {
            is CallerInfoResult.Member -> result.name
            is CallerInfoResult.Contact -> result.name
            is CallerInfoResult.MultipleMembers -> result.members.joinToString(", ") { it.name }
            CallerInfoResult.Unknown -> null
        }
    }

    private fun resolveFromSystemContacts(
        number: String,
        contentResolver: ContentResolver
    ): String? {
        try {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(number)
                .build()

            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Found in system contacts: $name")
                        return name
                    }
                }
            }
        } catch (e: SecurityException) {
            if (BuildConfig.DEBUG) Log.w(
                TAG,
                "Missing READ_CONTACTS permission, skipping system lookup"
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error querying system contacts", e)
        }
        return null
    }
}