package za.co.jpsoft.winkerkreader.data.pastoral.repository

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.pastoral.model.MemberDisplay
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.utils.getStringOrEmpty

fun interface MemberGuidResolver {
    fun resolve(memberGuid: String): MemberDisplay?
}

/**
 * Resolves [MemberDisplay] from the congregation ContentProvider by [MemberGUID].
 * Falls back to the Argief table when the member is no longer in the active Members list.
 * Uses parameterized queries for security and consistency with the rest of the codebase.
 */
class CongregationMemberGuidResolver(context: Context) : MemberGuidResolver {

    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    override fun resolve(memberGuid: String): MemberDisplay? {
        if (memberGuid.isBlank()) {
            return null
        }
        // No sanitization needed - we use parameterized queries
        val trimmedGuid = memberGuid.trim()

        lookupActiveMember(trimmedGuid)?.let { return it }
        return lookupArchivedMember(trimmedGuid)
    }

    /**
     * Look up an active member using parameterized query.
     */
    private fun lookupActiveMember(memberGuid: String): MemberDisplay? {
        // ✅ Use parameterized query with ? placeholder
        val selection = "${winkerkEntry.LIDMATE_LIDMAATGUID} = ?"
        val selectionArgs = arrayOf(memberGuid)

        return try {
            contentResolver.query(
                winkerkEntry.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val noemnaam = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)
                val van = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN)
                val recordStatus = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_REKORDSTATUS)
                MemberDisplay(
                    guid = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATGUID),
                    displayName = formatDisplayName(noemnaam, van),
                    surname = van.ifBlank { null },
                    givenName = noemnaam.ifBlank { null },
                    cellphone = formatPhone(cursor.getStringOrEmpty(winkerkEntry.LIDMATE_SELFOON)),
                    familyHeadGuid = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GESINSHOOFGUID)
                        .ifBlank { null },
                    photoPath = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_PICTUREPATH)
                        .ifBlank { null },
                    isArchived = recordStatus == "2"
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to resolve active member $memberGuid", e)
            null
        }
    }

    /**
     * Look up an archived member using parameterized query.
     */
    private fun lookupArchivedMember(memberGuid: String): MemberDisplay? {
        // ✅ Use parameterized query with ? placeholder
        val selection = "${winkerkEntry.argief_MemberGUID} = ?"
        val selectionArgs = arrayOf(memberGuid)

        return try {
            contentResolver.query(
                winkerkEntry.ARGIEF_URI,
                null,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val surname = cursor.getStringOrEmpty(winkerkEntry.argief_Surname)
                val name = cursor.getStringOrEmpty(winkerkEntry.argief_Name)
                MemberDisplay(
                    guid = cursor.getStringOrEmpty(winkerkEntry.argief_MemberGUID),
                    displayName = formatDisplayName(name, surname),
                    surname = surname.ifBlank { null },
                    givenName = name.ifBlank { null },
                    cellphone = null,
                    familyHeadGuid = null,
                    photoPath = null,
                    isArchived = true
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to resolve archived member $memberGuid", e)
            null
        }
    }

    /**
     * Look up a member in either active or archived tables.
     * Useful for checking if a member exists anywhere.
     */
    fun lookupAnyMember(memberGuid: String): MemberDisplay? {
        if (memberGuid.isBlank()) return null
        return lookupActiveMember(memberGuid.trim()) ?: lookupArchivedMember(memberGuid.trim())
    }

    private fun formatDisplayName(firstName: String, surname: String): String {
        return listOf(firstName.trim(), surname.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    private fun formatPhone(raw: String): String? {
        if (raw.isBlank()) {
            return null
        }
        return fixphonenumber(raw)?.trim()?.ifBlank { null }
    }

    companion object {
        private const val TAG = "MemberGuidResolver"
    }
}