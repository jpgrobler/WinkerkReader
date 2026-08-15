package za.co.jpsoft.winkerkreader.utils.telephony

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.members.setup.WinkerkDatabase

object CallerInfoResolver {

    private const val TAG = "CallerInfoResolver"

    /**
     * Primary resolution entry point.
     * [context] replaces the former [android.content.ContentResolver] parameter —
     * member lookups now go directly to Room; device-contact lookups still use
     * [Context.contentResolver] internally.
     */
    fun resolve(phoneNumber: String, context: Context): CallerInfoResult {
        if (BuildConfig.DEBUG) Log.d(TAG, "Resolving phone/name: $phoneNumber")

        if (phoneNumber.isEmpty() || phoneNumber == "Unknown Number" || phoneNumber == "null") {
            if (BuildConfig.DEBUG) Log.d(TAG, "Invalid query, skipping resolve")
            return CallerInfoResult.Unknown
        }

        // If the query contains letters, treat it as a display name resolution
        if (phoneNumber.any { it.isLetter() }) {
            return resolveByName(phoneNumber, context)
        }

        // Keep the original cleaned number (spaces/punctuation removed, leading zeros preserved)
        val cleanedOriginal = phoneNumber.replace(Regex("[\\s\\-()\\.]"), "")

        val normalized = normalizePhoneNumber(phoneNumber)
        if (normalized.isEmpty() || normalized == "+") {
            if (BuildConfig.DEBUG) Log.d(TAG, "Number empty after normalization")
            return CallerInfoResult.Unknown
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Normalized number: $normalized")

        // Member lookup — queries Room directly
        val memberList = resolveMember(normalized, context)
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

        // Device contact lookup (ContactsContract — not our DB, ContentResolver stays)
        val contactResult = resolveContact(cleanedOriginal, normalized, context.contentResolver)
        if (contactResult != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Found contact: ${contactResult.name}")
            // Check if this contact name matches a member in our database
            val memberByName = resolveByName(contactResult.name, context)
            if (memberByName is CallerInfoResult.Member ||
                memberByName is CallerInfoResult.MultipleMembers
            ) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Mapped contact name to member: ${contactResult.name}")
                return memberByName
            }
            return contactResult
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "No match found for: $phoneNumber")
        return CallerInfoResult.Unknown
    }

    /**
     * Resolves a member from the database based on their display name
     * (e.g. from a WhatsApp notification).
     * [context] replaces the former [android.content.ContentResolver] parameter.
     */
    fun resolveByName(displayName: String, context: Context): CallerInfoResult {
        val name = displayName.trim()
        if (name.isEmpty() || name == "Unknown Contact" || name == "Unknown") {
            return CallerInfoResult.Unknown
        }

        try {
            // 1. Exact match on concatenated names (Noemnaam + Van) or (Voorname + Van)
            val exactWhere =
                "(${winkerkEntry.LIDMATE_NOEMNAAM} || ' ' || ${winkerkEntry.LIDMATE_VAN}) = ? " +
                        "OR (${winkerkEntry.LIDMATE_VOORNAME} || ' ' || ${winkerkEntry.LIDMATE_VAN}) = ? " +
                        "OR (${winkerkEntry.LIDMATE_NOEMNAAM} = ? AND ${winkerkEntry.LIDMATE_VAN} IS NULL)"
            val exactQuery = "SELECT * FROM Members WHERE $exactWhere"
            val exactArgs = arrayOf(name, name, name)

            val exactMembers = queryMembers(exactQuery, exactArgs, context)
            if (!exactMembers.isNullOrEmpty()) {
                return when (exactMembers.size) {
                    1 -> exactMembers.first()
                    else -> CallerInfoResult.MultipleMembers(exactMembers)
                }
            }

            // 2. Split name: first and last word matching Noemnaam/Van
            val parts = name.split(Regex("\\s+"))
            if (parts.size >= 2) {
                val firstName = parts.first()
                val lastName = parts.last()
                val splitWhere =
                    "(${winkerkEntry.LIDMATE_NOEMNAAM} LIKE ? AND ${winkerkEntry.LIDMATE_VAN} LIKE ?) " +
                            "OR (${winkerkEntry.LIDMATE_VOORNAME} LIKE ? AND ${winkerkEntry.LIDMATE_VAN} LIKE ?)"
                val splitQuery = "SELECT * FROM Members WHERE $splitWhere"
                val splitArgs =
                    arrayOf("%$firstName%", "%$lastName%", "%$firstName%", "%$lastName%")

                val splitMembers = queryMembers(splitQuery, splitArgs, context)
                if (!splitMembers.isNullOrEmpty()) {
                    return when (splitMembers.size) {
                        1 -> splitMembers.first()
                        else -> CallerInfoResult.MultipleMembers(splitMembers)
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error resolving member by name", e)
        }
        return CallerInfoResult.Unknown
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Builds SQLite SQL to strip formatting characters from database phone columns
     * for robust comparison.
     */
    private fun cleanPhoneColumnSql(columnName: String): String {
        return "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE([$columnName], ' ', ''), '-', ''), '(', ''), ')', ''), '+', ''), '.', '')"
    }

    /**
     * Searches all member phone columns for any format of the given number.
     * Returns all matching members, or null if none found.
     */
    private fun resolveMember(
        phoneNumber: String,
        context: Context
    ): List<CallerInfoResult.Member>? {
        try {
            val formats = buildList {
                add(phoneNumber) // e.g. +27810000008

                val digitsOnly = phoneNumber.replace(Regex("[^0-9]"), "")
                if (digitsOnly.isNotEmpty()) {
                    add(digitsOnly) // 27810000008

                    val localSubscriber =
                        if (digitsOnly.startsWith("27") && digitsOnly.length > 2) {
                            digitsOnly.substring(2)
                        } else {
                            digitsOnly
                        }
                    add(localSubscriber)          // 810000008
                    add("0$localSubscriber")       // 0810000008

                    if (!phoneNumber.startsWith("+")) {
                        add("+27$localSubscriber") // +27810000008
                    }
                }
            }.distinct()

            if (BuildConfig.DEBUG) Log.d(TAG, "Trying formats: $formats")
            if (formats.isEmpty()) return null

            val conditions = mutableListOf<String>()
            val args = mutableListOf<String>()

            for (format in formats) {
                val digitsOnlyFormat = format.replace(Regex("[^0-9]"), "")
                if (digitsOnlyFormat.length >= 7) { // guard against short substring matches
                    conditions.add("${cleanPhoneColumnSql(winkerkEntry.LIDMATE_SELFOON)} LIKE ?")
                    args.add("%$digitsOnlyFormat%")
                    conditions.add("${cleanPhoneColumnSql(winkerkEntry.LIDMATE_LANDLYN)} LIKE ?")
                    args.add("%$digitsOnlyFormat%")
                    conditions.add("${cleanPhoneColumnSql(winkerkEntry.LIDMATE_WERKFOON)} LIKE ?")
                    args.add("%$digitsOnlyFormat%")
                }
            }

            if (conditions.isEmpty()) return null

            val whereClause = conditions.joinToString(" OR ")
            val fullQuery = "SELECT * FROM Members WHERE $whereClause"

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Full query: $fullQuery")
                if (BuildConfig.DEBUG) Log.d(TAG, "Args: ${args.joinToString()}")
            }

            return queryMembers(fullQuery, args.toTypedArray(), context)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error resolving member", e)
            return null
        }
    }

    /**
     * Executes a raw SQL query against the Members table via Room's DAO
     * and maps results into [CallerInfoResult.Member] objects.
     *
     * Only the columns needed for caller-ID display are read from each row;
     * the query itself still uses SELECT * so no SQL restructuring is needed.
     */
    private fun queryMembers(
        fullQuery: String,
        args: Array<String>,
        context: Context
    ): List<CallerInfoResult.Member>? {
        val members = mutableListOf<CallerInfoResult.Member>()
        try {
            @Suppress("UNCHECKED_CAST")
            val cursor = WinkerkDatabase.getInstance(context)
                .memberDao()
                .queryRaw(SimpleSQLiteQuery(fullQuery, args as Array<Any>?))

            cursor.use {
                while (it.moveToNext()) {
                    val surname =
                        it.getString(it.getColumnIndexOrThrow(winkerkEntry.LIDMATE_VAN)) ?: ""
                    val noemnaam =
                        it.getString(it.getColumnIndexOrThrow(winkerkEntry.LIDMATE_NOEMNAAM)) ?: ""
                    val guid =
                        it.getString(it.getColumnIndexOrThrow(winkerkEntry.LIDMATE_LIDMAATGUID))
                            ?: ""
                    val phone =
                        it.getString(it.getColumnIndexOrThrow(winkerkEntry.LIDMATE_SELFOON)) ?: ""
                    val gemeente =
                        it.getString(it.getColumnIndexOrThrow(winkerkEntry.LIDMATE_GEMEENTE)) ?: ""
                    val displayName = buildMemberDisplayName(noemnaam, surname)
                    val familyHeadGuid =
                        it.getString(it.getColumnIndexOrThrow(winkerkEntry.LIDMATE_GESINSHOOFGUID))
                            ?: ""
                    members.add(
                        CallerInfoResult.Member(
                            name = displayName,
                            guid = guid,
                            surname = surname,
                            firstName = noemnaam,
                            phone = phone,
                            memberType = "Lidmaat",
                            gemeente = gemeente,
                            familyHeadGuid = familyHeadGuid
                        )
                    )
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Database query failed", e)
        }
        return if (members.isNotEmpty()) members else null
    }

    /**
     * Looks up the phone number in the device's Contacts via [ContactsContract].
     * This uses [android.content.ContentResolver] because it queries Android system
     * data — NOT our database.
     */
    private fun resolveContact(
        cleanedOriginal: String,
        normalized: String,
        contentResolver: ContentResolver
    ): CallerInfoResult.Contact? {
        try {
            val candidates = buildList {
                if (cleanedOriginal.isNotEmpty()) add(cleanedOriginal)
                if (normalized.isNotEmpty() && normalized != cleanedOriginal) add(normalized)
            }.distinct()

            for (candidate in candidates) {
                val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                    .appendPath(candidate)
                    .build()

                if (BuildConfig.DEBUG) Log.d(TAG, "Contact lookup URI: $uri")

                val projection = arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.NUMBER
                )

                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(
                            cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        ) ?: ""
                        val number = cursor.getString(
                            cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.NUMBER)
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