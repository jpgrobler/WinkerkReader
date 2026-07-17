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

        // Keep the original cleaned number (spaces/punctuation removed, but leading zeros preserved)
        val cleanedOriginal = phoneNumber.replace(Regex("[\\s\\-()\\.]"), "")

        val normalized = normalizePhoneNumber(phoneNumber)
        if (normalized.isEmpty() || normalized == "+") {
            if (BuildConfig.DEBUG) Log.d(TAG, "Number empty after normalization")
            return CallerInfoResult.Unknown
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Normalized number: $normalized")

        // Member lookup returns a list of all matching members
        val memberList = resolveMember(normalized, contentResolver)
        if (memberList != null) {
            return when (memberList.size) {
                1 -> {
                    val member = memberList.first()
                    if (BuildConfig.DEBUG) Log.d(TAG, "Found single member: ${member.name}")
                    member
                }

                else -> {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Found ${memberList.size} members")
                    CallerInfoResult.MultipleMembers(memberList)
                }
            }
        }

        // Contact lookup now tries both the original cleaned number and the normalized one
        val contactResult = resolveContact(cleanedOriginal, normalized, contentResolver)
        if (contactResult != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Found contact: ${contactResult.name}")
            return contactResult
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "No match found for: $phoneNumber")
        return CallerInfoResult.Unknown
    }

    /**
     * Searches all member phone columns for any format of the given number.
     * Returns a list of all matching members, or null if none found.
     */
    private fun resolveMember(
        phoneNumber: String,
        contentResolver: ContentResolver
    ): List<CallerInfoResult.Member>? {
        try {
            val formats = buildList {
                add(phoneNumber) // e.g., +27810000008

                val digitsOnly = phoneNumber.replace(Regex("[^0-9]"), "")
                if (digitsOnly.isNotEmpty()) {
                    add(digitsOnly) // 27810000008

                    // Determine local subscriber number (strip leading 27 if present)
                    val localSubscriber =
                        if (digitsOnly.startsWith("27") && digitsOnly.length > 2) {
                            digitsOnly.substring(2)
                        } else {
                        digitsOnly
                        }
                    add(localSubscriber) // 810000008
                    add("0$localSubscriber") // 0810000008

                    // If the original number does NOT start with '+', add the international SA format
                    if (!phoneNumber.startsWith("+")) {
                        val withCountryCode = "+27$localSubscriber"
                        add(withCountryCode) // +27810000008
                    }
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

            val members = mutableListOf<CallerInfoResult.Member>()
            cursor?.use {
                while (it.moveToNext()) {
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
                    members.add(
                        CallerInfoResult.Member(
                            name = displayName,
                            guid = guid,
                            surname = surname,
                            firstName = noemnaam,
                            phone = phone,
                            memberType = "Lidmaat",
                            gemeente = gemeente
                        )
                    )
                }
            }
            return if (members.isNotEmpty()) members else null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error resolving member", e)
            return null
        }
    }

    /**
     * Looks up the phone number in the device's Contacts.
     * Tries both the original cleaned number (with leading zeros) and the normalized version.
     */
    private fun resolveContact(
        cleanedOriginal: String,
        normalized: String,
        contentResolver: ContentResolver
    ): CallerInfoResult.Contact? {
        try {
            val candidates = mutableListOf<String>()
            if (cleanedOriginal.isNotEmpty()) {
                candidates.add(cleanedOriginal)
            }
            if (normalized.isNotEmpty() && normalized != cleanedOriginal) {
                candidates.add(normalized)
            }

            for (candidate in candidates.distinct()) {
                val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                    .appendPath(candidate)
                    .build()

                if (BuildConfig.DEBUG) Log.d(TAG, "Contact lookup URI: $uri")

                val projection = arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.NUMBER
                )

                val cursor = contentResolver.query(uri, projection, null, null, null)

                cursor?.use {
                    if (it.moveToFirst()) {
                        val name = it.getString(
                            it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        ) ?: ""
                        val number = it.getString(
                            it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.NUMBER)
                        ) ?: ""
                        if (name.isNotEmpty()) {
                            return CallerInfoResult.Contact(name = name, phoneNumber = number)
                        }
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