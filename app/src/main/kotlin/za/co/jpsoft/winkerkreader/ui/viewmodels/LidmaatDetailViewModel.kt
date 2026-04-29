package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.FamilyMemberItem
import za.co.jpsoft.winkerkreader.data.models.MemberDetailItem
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.utils.Utils.parseDate
import za.co.jpsoft.winkerkreader.utils.getIntOrDefault
import za.co.jpsoft.winkerkreader.utils.getStringOrEmpty
import za.co.jpsoft.winkerkreader.utils.getStringOrNull
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class LidmaatDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val _memberDetail = MutableLiveData<MemberDetailItem?>()
    val memberDetail: LiveData<MemberDetailItem?> = _memberDetail

    private val _familyMembers = MutableLiveData<List<FamilyMemberItem>>()
    val familyMembers: LiveData<List<FamilyMemberItem>> = _familyMembers

    private val contentResolver = application.contentResolver

    fun loadMember(memberUri: Uri, recordStatus: String) {
        val selection = """
            SELECT _rowid_ AS _id, * 
            FROM ${winkerkEntry.SELECTION_LIDMAAT_FROM}
            WHERE (${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_REKORDSTATUS} = $recordStatus)
            AND (${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ${memberUri.lastPathSegment})
        """.trimIndent()

        try {
            contentResolver.query(memberUri, null, selection, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val item = extractMemberDetail(cursor)
                    _memberDetail.postValue(item)
                }
            }
        } catch (e: Exception) {
            Log.e("LidmaatDetailVM", "Error loading member detail", e)
            _memberDetail.postValue(null)
        }
    }

    private fun extractMemberDetail(cursor: android.database.Cursor): MemberDetailItem {
        val bDayRaw = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEBOORTEDATUM)
        var ageYears = -1L
        var bDay = bDayRaw
        if (bDay.length >= 10) {
            bDay = bDay.substring(0, 10)
            try {
                parseDate(bDay)?.let {
                    ageYears = ChronoUnit.YEARS.between(it, LocalDate.now())
                }
            } catch (_: Exception) {}
        }

        val huwelikDatum = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_HUWELIKSDATUM)
        var huwelikYears = -1L
        if (huwelikDatum.isNotEmpty()) {
            try {
                parseDate(huwelikDatum)?.let {
                    huwelikYears = ChronoUnit.YEARS.between(it, LocalDate.now())
                }
            } catch (_: Exception) {}
        }

        return MemberDetailItem(
            id = cursor.getIntOrDefault("_id", 0),
            guid = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATGUID),
            familyHeadGuid = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GESINSHOOFGUID),
            name = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM),
            surname = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN),
            fullNames = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VOORNAME),
            maidenName = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOOIENSVAN),
            cellphone = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_SELFOON).let { if (it.isNotBlank()) fixphonenumber(it) ?: "" else "" },
            landline = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LANDLYN).let { if (it.isNotBlank()) fixphonenumber(it) ?: "" else "" },
            ward = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_WYK),
            birthday = bDay,
            age = ageYears,
            streetAddress = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_STRAATADRES)
                .replace("\r\n", ", ").replace("\r", ", ").replace("\n", ", ")
                .replace(", , ", ", ").replace(",  ,", ", "),
            postalAddress = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_POSADRES)
                .replace("\r\n", ", ").replace("\r", ", ").replace("\n", ", ")
                .replace(", , ", ", "),
            email = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_EPOS),
            profession = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_BEROEP),
            employer = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_WERKGEWER),
            gender = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GESLAG),
            marriageStatus = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_HUWELIKSTATUS).ifEmpty { "Ongetroud" },
            memberStatus = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATSTATUS),
            certificateStatus = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_BEWYSSTATUS),
            baptismDate = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_DOOPDATUM),
            baptismDs = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_DOOPDS),
            confessionDate = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_BELYDENISDATUM),
            confessionDs = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_BELYDENISDS),
            marriageDate = huwelikDatum,
            marriageYears = huwelikYears,
            gemeente = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEMEENTE)
        )
    }
//deathDate = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_STERFDATUM)
    fun loadFamily(familyHeadGuid: String, recordStatus: String) {
        val selection = """
            SELECT _rowid_ AS _id, ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_VAN},
                   ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_NOEMNAAM},
                   ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_GEBOORTEDATUM},
                   ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_PICTUREPATH}
            FROM ${winkerkEntry.LIDMATE_TABLE_NAME}
            WHERE (${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_REKORDSTATUS} = $recordStatus)
            AND (members.FamilyHeadGUID = "$familyHeadGuid")
            ORDER BY Gesinsrol ASC
        """.trimIndent()
        
        val uri = ContentUris.withAppendedId(winkerkEntry.CONTENT_GESIN_URI, 0L)
        try {
            contentResolver.query(uri, null, selection, null, null)?.use { cursor ->
                val list = mutableListOf<FamilyMemberItem>()
                while (cursor.moveToNext()) {
                    list.add(extractFamilyMember(cursor))
                }
                _familyMembers.postValue(list)
            }
        } catch (e: Exception) {
            Log.e("LidmaatDetailVM", "Error loading family", e)
            _familyMembers.postValue(emptyList())
        }
    }

    private fun extractFamilyMember(cursor: android.database.Cursor): FamilyMemberItem {
        var bDay = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEBOORTEDATUM)
        var age = -1L
        if (bDay.isNotEmpty() && bDay.length >= 10) {
            bDay = bDay.substring(0, 10)
            try {
                parseDate(bDay)?.let {
                    age = ChronoUnit.YEARS.between(it, LocalDate.now())
                }
            } catch (_: Exception) {}
        }

        return FamilyMemberItem(
            id = cursor.getIntOrDefault("_id", 0),
            name = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM),
            surname = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN),
            birthday = bDay,
            age = age,
            picturePath = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_PICTUREPATH)
        )
    }
}