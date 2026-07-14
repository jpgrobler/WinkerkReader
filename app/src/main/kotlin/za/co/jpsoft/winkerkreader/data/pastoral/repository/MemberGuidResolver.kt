package za.co.jpsoft.winkerkreader.data.pastoral.repository

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.pastoral.model.MemberDisplay
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.utils.getStringOrEmpty

fun interface MemberGuidResolver {
    fun resolve(memberGuid: String): MemberDisplay?
}

class CongregationMemberGuidResolver(context: Context) : MemberGuidResolver {

    private val appContext = context.applicationContext

    override fun resolve(memberGuid: String): MemberDisplay? {
        if (memberGuid.isBlank()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "resolve: memberGuid is blank")
            return null
        }

        // Clean the GUID
        val cleanedGuid = memberGuid
            .trim()
            .replace("\"", "")
            .replace("'", "")
            .replace("`", "")
            .replace("\\s".toRegex(), "")

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "resolve: original GUID = '$memberGuid'")
            Log.d(TAG, "resolve: cleaned GUID = '$cleanedGuid'")
        }

        if (cleanedGuid.isBlank()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "resolve: GUID is blank after cleaning")
            return null
        }

        // Try active members first
        lookupActiveMember(cleanedGuid)?.let {
            if (BuildConfig.DEBUG) Log.d(TAG, "resolve: found active member: ${it.displayName}")
            return it
        }

        // Then try archived members
        lookupArchivedMember(cleanedGuid)?.let {
            if (BuildConfig.DEBUG) Log.d(TAG, "resolve: found archived member: ${it.displayName}")
            return it
        }

        if (BuildConfig.DEBUG) Log.w(TAG, "resolve: member not found for GUID: '$cleanedGuid'")
        return null
    }

    private fun lookupActiveMember(memberGuid: String): MemberDisplay? {
        if (BuildConfig.DEBUG) Log.d(TAG, "lookupActiveMember: searching for GUID: '$memberGuid'")

        return try {
            val db = WinkerkDatabase.getInstance(appContext)

            // ✅ Use Room's queryRaw with SimpleSQLiteQuery
            val cursor = db.memberDao().queryRaw(
                SimpleSQLiteQuery(
                    "SELECT * FROM Members WHERE MemberGUID = ?",
                    arrayOf(memberGuid)
                )
            )

            cursor.use { cursor ->
                if (!cursor.moveToFirst()) {
                    if (BuildConfig.DEBUG) Log.w(
                        TAG,
                        "lookupActiveMember: no cursor data for GUID: '$memberGuid'"
                    )
                    return null
                }
                extractMemberFromCursor(cursor)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(
                TAG,
                "lookupActiveMember: error for GUID: '$memberGuid'",
                e
            )
            null
        }
    }

    private fun extractMemberFromCursor(cursor: android.database.Cursor): MemberDisplay {
        val noemnaam = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)
        val van = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN)
        val recordStatus = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_REKORDSTATUS)
        val guid = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATGUID)
        val cellphone = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_SELFOON)
        val familyHeadGuid = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GESINSHOOFGUID)
        val photoPath = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_PICTUREPATH)

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "extractMemberFromCursor: name = '$noemnaam', surname = '$van', guid = '$guid'"
            )
        }

        return MemberDisplay(
            guid = guid,
            displayName = formatDisplayName(noemnaam, van),
            surname = van.ifBlank { null },
            givenName = noemnaam.ifBlank { null },
            cellphone = formatPhone(cellphone),
            familyHeadGuid = familyHeadGuid.ifBlank { null },
            photoPath = photoPath.ifBlank { null },
            isArchived = recordStatus == "2"
        )
    }

    private fun lookupArchivedMember(memberGuid: String): MemberDisplay? {
        if (BuildConfig.DEBUG) Log.d(TAG, "lookupArchivedMember: searching for GUID: '$memberGuid'")

        return try {
            val db = WinkerkDatabase.getInstance(appContext)

            // ✅ Use Room's queryRaw with SimpleSQLiteQuery
            val cursor = db.argiefDao().queryRaw(
                SimpleSQLiteQuery(
                    "SELECT * FROM Argief WHERE MemberGUID = ?",
                    arrayOf(memberGuid)
                )
            )

            cursor.use { cursor ->
                if (!cursor.moveToFirst()) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "lookupArchivedMember: no cursor data")
                    return null
                }
                val surname = cursor.getStringOrEmpty(winkerkEntry.argief_Surname)
                val name = cursor.getStringOrEmpty(winkerkEntry.argief_Name)

                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "lookupArchivedMember: found - name: '$name', surname: '$surname'"
                )

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
            if (BuildConfig.DEBUG) Log.e(
                TAG,
                "lookupArchivedMember: error for GUID: '$memberGuid'",
                e
            )
            null
        }
    }

    fun lookupAnyMember(memberGuid: String): MemberDisplay? {
        if (memberGuid.isBlank()) return null
        val cleaned = memberGuid.trim()
        return lookupActiveMember(cleaned) ?: lookupArchivedMember(cleaned)
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