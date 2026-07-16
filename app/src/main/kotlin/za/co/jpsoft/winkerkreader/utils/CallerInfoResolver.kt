package za.co.jpsoft.winkerkreader.utils

import android.content.ContentResolver
import android.provider.ContactsContract
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry

object CallerInfoResolver {

    private const val TAG = "CallerInfoResolver"

    fun resolve(phoneNumber: String, contentResolver: ContentResolver): CallerInfoResult {
        if (BuildConfig.DEBUG) Log.d(TAG, "Resolving phone number: $phoneNumber")

        if (phoneNumber.isEmpty() || phoneNumber == "Unknown Number" || phoneNumber == "null") {
            if (BuildConfig.DEBUG) Log.d(TAG, "Invalid phone number, skipping resolve")
            return CallerInfoResult.Unknown
        }

        val normalized = normalizePhoneNumber(phoneNumber)
        if (normalized.isEmpty() || normalized == "+") {
            if (BuildConfig.DEBUG) Log.d(TAG, "Number empty after normalization")
            return CallerInfoResult.Unknown
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Normalized number: $normalized")

        val memberResult = resolveMember(normalized, contentResolver)
        if (memberResult != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Found member: ${memberResult.name}")
            return memberResult
        }

        val contactResult = resolveContact(normalized, contentResolver)
        if (contactResult != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Found contact: ${contactResult.name}")
            return contactResult
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "No match found for: $phoneNumber")
        return CallerInfoResult.Unknown
    }

    private fun resolveMember(
        phoneNumber: String,
        contentResolver: ContentResolver
    ): CallerInfoResult.Member? {
        try {
            val formats = buildList {
                add(phoneNumber)                                        // +27810000008

                val digitsOnly = phoneNumber.replace(Regex("[^0-9]"), "")
                if (digitsOnly.isNotEmpty()) {
                    add(digitsOnly)                                     // 27810000008

                    // Strip SA country code to get local subscriber number
                    val local = if (digitsOnly.startsWith("27") && digitsOnly.length > 2)
                        digitsOnly.substring(2)                         // 810000008
                    else
                        digitsOnly

                    add(local)                                          // 810000008
                    add("0$local")                                      // 0810000008
                }
            }.distinct()

            if (BuildConfig.DEBUG) Log.d(TAG, "Trying formats: $formats")
            if (formats.isEmpty()) return null

            val conditions = mutableListOf<String>()
            val args = mutableListOf<String>()

            for (format in formats) {
                if (format.isNotEmpty()) {
                    conditions.add("[${winkerkEntry.LIDMATE_SELFOON}] LIKE ?")
                    args.add("%$format%")
                    conditions.add("[${winkerkEntry.LIDMATE_LANDLYN}] LIKE ?")
                    args.add("%$format%")
                    conditions.add("[${winkerkEntry.LIDMATE_WERKFOON}] LIKE ?")
                    args.add("%$format%")
                }
            }

            if (conditions.isEmpty()) return null

            // Build a complete SELECT statement because the provider expects a full SQL query.
            val whereClause = conditions.joinToString(" OR ")
            val fullQuery = "SELECT * FROM Members WHERE $whereClause"

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Full query: $fullQuery")
                Log.d(TAG, "Args: ${args.joinToString()}")
            }

            val projection = arrayOf(
                winkerkEntry.LIDMATE_VAN,
                winkerkEntry.LIDMATE_NOEMNAAM,
                winkerkEntry.LIDMATE_VOORNAME,
                winkerkEntry.LIDMATE_LIDMAATGUID,
                winkerkEntry.LIDMATE_SELFOON,
                winkerkEntry.LIDMATE_GEMEENTE
            )

            val cursor = contentResolver.query(
                winkerkEntry.CONTENT_URI,
                projection,
                fullQuery,
                args.toTypedArray(),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val surname =
                        it.getString(it.getColumnIndexOrThrow(winkerkEntry.LIDMATE_VAN)) ?: ""
                    val noemnaam =
                        it.getString(it.getColumnIndexOrThrow(winkerkEntry.LIDMATE_NOEMNAAM)) ?: ""
                    val voorname =
                        it.getString(it.getColumnIndexOrThrow(winkerkEntry.LIDMATE_VOORNAME)) ?: ""
                    val guid =
                        it.getString(it.getColumnIndexOrThrow(winkerkEntry.LIDMATE_LIDMAATGUID))
                            ?: ""
                    val phone =
                        it.getString(it.getColumnIndexOrThrow(winkerkEntry.LIDMATE_SELFOON)) ?: ""
                    val gemeente =
                        it.getString(it.getColumnIndexOrThrow(winkerkEntry.LIDMATE_GEMEENTE)) ?: ""

                    val displayName = buildMemberDisplayName(noemnaam, surname)

                    return CallerInfoResult.Member(
                        name = displayName,
                        guid = guid,
                        surname = surname,
                        firstName = noemnaam,
                        phone = phone,
                        memberType = "Lidmaat",
                        gemeente = gemeente
                    )
                }
            }
            return null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error resolving member", e)
            return null
        }
    }

    private fun resolveContact(
        phoneNumber: String,
        contentResolver: ContentResolver
    ): CallerInfoResult.Contact? {
        try {
            // Build URI correctly with the phone number
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(phoneNumber)
                .build()

            if (BuildConfig.DEBUG) Log.d(TAG, "Contact lookup URI: $uri")

            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.NUMBER
            )

            val cursor = contentResolver.query(uri, projection, null, null, null)

            cursor?.use {
                if (it.moveToFirst()) {
                    val name =
                        it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                            ?: ""
                    val number =
                        it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.NUMBER))
                            ?: ""
                    if (name.isNotEmpty()) {
                        return CallerInfoResult.Contact(name = name, phoneNumber = number)
                    }
                }
            }
            return null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error resolving contact", e)
            return null
        }
    }

    private fun normalizePhoneNumber(number: String): String {
        var cleaned = number.replace(Regex("[\\s\\-()\\.]"), "")
        if (!cleaned.startsWith("+")) {
            cleaned = cleaned.replace(Regex("^0+"), "")
        }
        return cleaned
    }

    private fun buildMemberDisplayName(noemnaam: String, surname: String): String {
        return when {
            noemnaam.isNotEmpty() && surname.isNotEmpty() -> "$noemnaam $surname"
            noemnaam.isNotEmpty() -> noemnaam
            surname.isNotEmpty() -> surname
            else -> "Lidmaat"
        }
    }
}