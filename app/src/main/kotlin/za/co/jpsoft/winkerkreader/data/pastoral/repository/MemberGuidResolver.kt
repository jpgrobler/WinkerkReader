package za.co.jpsoft.winkerkreader.data.pastoral.repository

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract
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
 */
class CongregationMemberGuidResolver(context: Context) : MemberGuidResolver {

    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    override fun resolve(memberGuid: String): MemberDisplay? {
        if (memberGuid.isBlank()) {
            return null
        }
        val safeGuid = sanitizeGuid(memberGuid)

        lookupActiveMember(safeGuid)?.let { return it }
        return lookupArchivedMember(safeGuid)
    }

    private fun lookupActiveMember(memberGuid: String): MemberDisplay? {
        val selection = """
        ${winkerkEntry.SELECTION_LIDMAAT_INFO} FROM ${winkerkEntry.SELECTION_LIDMAAT_FROM}
        WHERE quote(${WinkerkContract.col(winkerkEntry.LIDMATE_LIDMAATGUID)}) = quote('$memberGuid')
        LIMIT 1
    """.trimIndent()

        return try {
            contentResolver.query(winkerkEntry.CONTENT_URI, null, selection, null, null)
                ?.use { cursor ->
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

    private fun lookupArchivedMember(memberGuid: String): MemberDisplay? {
        val selection = """
        SELECT Argief._rowid_ AS _id, *
        FROM Argief
        WHERE quote(${WinkerkContract.col(winkerkEntry.argief_MemberGUID)}) = quote('$memberGuid')
        LIMIT 1
    """.trimIndent()

        return try {
            contentResolver.query(winkerkEntry.ARGIEF_URI, null, selection, null, null)
                ?.use { cursor ->
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

    private fun sanitizeGuid(guid: String): String {
        require(!guid.contains('\'')) { "Invalid memberGuid" }
        return guid.trim()
    }

    companion object {
        private const val TAG = "MemberGuidResolver"
    }
}
