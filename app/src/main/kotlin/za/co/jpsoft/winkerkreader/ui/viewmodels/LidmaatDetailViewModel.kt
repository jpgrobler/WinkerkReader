package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.app.Application
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.FamilyMemberItem
import za.co.jpsoft.winkerkreader.data.models.MemberDetailItem
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.utils.Utils.parseDate
import za.co.jpsoft.winkerkreader.utils.getIntOrDefault
import za.co.jpsoft.winkerkreader.utils.getStringOrEmpty
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class LidmaatDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val _memberDetail = MutableLiveData<MemberDetailItem?>()
    val memberDetail: LiveData<MemberDetailItem?> = _memberDetail

    private val _familyMembers = MutableLiveData<List<FamilyMemberItem>>(emptyList())
    val familyMembers: LiveData<List<FamilyMemberItem>> = _familyMembers

    private val contentResolver = application.contentResolver
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    // ------------------------------------------------------------------------
    // Public entry points – launch coroutines
    // ------------------------------------------------------------------------

    fun loadMemberByGuid(memberGuid: String, recordStatus: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { queryMemberByGuid(memberGuid, recordStatus) }
            _memberDetail.postValue(result)
            _isLoading.value = false
        }
    }

    fun loadMember(memberUri: Uri, recordStatus: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                queryMemberByUri(memberUri, recordStatus)
            }
            _memberDetail.postValue(result)
            _isLoading.value = false
        }
    }

    fun loadFamily(familyHeadGuid: String, recordStatus: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                queryFamilyMembers(familyHeadGuid, recordStatus)
            }
            _familyMembers.postValue(result)
            _isLoading.value = false
        }
    }

    // ------------------------------------------------------------------------
    // Background query methods (all run on IO)
    // ------------------------------------------------------------------------

    private fun queryMemberByGuid(memberGuid: String, recordStatus: String): MemberDetailItem? {
        // Use parameterized query with ?
        val query = """
            SELECT *, _rowid_ as _id 
            FROM ${winkerkEntry.LIDMATE_TABLE_NAME}
            WHERE ${winkerkEntry.LIDMATE_LIDMAATGUID} = ?
              AND ${winkerkEntry.LIDMATE_REKORDSTATUS} = ?
        """.trimIndent()
        val cursor = contentResolver.query(
            winkerkEntry.CONTENT_URI,
            null,
            query,
            arrayOf(memberGuid, recordStatus),
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) extractMemberDetail(it) else null
        }
    }

    private fun queryMemberByUri(memberUri: Uri, recordStatus: String): MemberDetailItem? {
        val selection = """
            SELECT _rowid_ AS _id, * 
            FROM ${winkerkEntry.SELECTION_LIDMAAT_FROM}
            WHERE (${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_REKORDSTATUS} = ?)
              AND (${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ?)
        """.trimIndent()
        val selectionArgs = arrayOf(recordStatus, memberUri.lastPathSegment)
        val cursor = contentResolver.query(
            memberUri,
            null,
            selection,
            selectionArgs,
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) extractMemberDetail(it) else null
        }
    }

    private fun queryFamilyMembers(
        familyHeadGuid: String,
        recordStatus: String
    ): List<FamilyMemberItem> {
        val selection = """
            SELECT _rowid_ AS _id,
                   ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_VAN},
                   ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_NOEMNAAM},
                   ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_GEBOORTEDATUM},
                   ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_PICTUREPATH},
                   ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_LIDMAATGUID}
            FROM ${winkerkEntry.LIDMATE_TABLE_NAME}
            WHERE (${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_REKORDSTATUS} = ?)
              AND (${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_GESINSHOOFGUID} = ?)
            ORDER BY Gesinsrol ASC
        """.trimIndent()
        val selectionArgs = arrayOf(recordStatus, familyHeadGuid)
        val uri = ContentUris.withAppendedId(winkerkEntry.CONTENT_GESIN_URI, 0L)
        val cursor = contentResolver.query(uri, null, selection, selectionArgs, null)
        return cursor?.use {
            buildList {
                while (it.moveToNext()) {
                    add(extractFamilyMember(it))
                }
            }
        } ?: emptyList()
    }

    // ------------------------------------------------------------------------
    // Cursor → Model conversion (unchanged logic)
    // ------------------------------------------------------------------------

    private fun extractMemberDetail(cursor: Cursor): MemberDetailItem {
        var bDayRaw = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEBOORTEDATUM)
        var ageYears = -1L
        var bDay = bDayRaw
        if (bDay.length >= 10) {
            bDay = bDay.substring(0, 10)
            try {
                parseDate(bDay)?.let {
                    ageYears = ChronoUnit.YEARS.between(it, LocalDate.now())
                }
            } catch (_: Exception) {
            }
        }

        val huwelikDatum = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_HUWELIKSDATUM)
        var huwelikYears = -1L
        if (huwelikDatum.isNotEmpty()) {
            try {
                parseDate(huwelikDatum)?.let {
                    huwelikYears = ChronoUnit.YEARS.between(it, LocalDate.now())
                }
            } catch (_: Exception) {
            }
        }

        return MemberDetailItem(
            id = cursor.getIntOrDefault("_id", 0),
            guid = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATGUID),
            familyHeadGuid = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GESINSHOOFGUID),
            name = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM),
            surname = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN),
            fullNames = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VOORNAME),
            maidenName = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOOIENSVAN),
            cellphone = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_SELFOON)
                .let { if (it.isNotBlank()) fixphonenumber(it) ?: "" else "" },
            landline = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LANDLYN)
                .let { if (it.isNotBlank()) fixphonenumber(it) ?: "" else "" },
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
            marriageStatus = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_HUWELIKSTATUS)
                .ifEmpty { "Ongetroud" },
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

    private fun extractFamilyMember(cursor: Cursor): FamilyMemberItem {
        var bDay = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEBOORTEDATUM)
        var age = -1L
        if (bDay.isNotEmpty() && bDay.length >= 10) {
            bDay = bDay.substring(0, 10)
            try {
                parseDate(bDay)?.let {
                    age = ChronoUnit.YEARS.between(it, LocalDate.now())
                }
            } catch (_: Exception) {
            }
        }

        return FamilyMemberItem(
            id = cursor.getIntOrDefault("_id", 0),
            name = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM),
            surname = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN),
            birthday = bDay,
            age = age,
            picturePath = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_PICTUREPATH),
            guid = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATGUID)
        )
    }
}