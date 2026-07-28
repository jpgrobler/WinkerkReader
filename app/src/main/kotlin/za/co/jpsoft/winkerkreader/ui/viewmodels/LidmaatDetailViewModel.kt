package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.data.models.FamilyMemberItem
import za.co.jpsoft.winkerkreader.data.models.MemberDetailItem
import za.co.jpsoft.winkerkreader.data.room.MemberEntity
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.utils.Utils.parseDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class LidmaatDetailViewModel(application: Application) : AndroidViewModel(application) {

    // Direct DAO access — no ContentProvider round-trip.
    private val memberDao = WinkerkDatabase.getInstance(application).memberDao()

    private val _memberDetail = MutableLiveData<MemberDetailItem?>()
    val memberDetail: LiveData<MemberDetailItem?> = _memberDetail

    private val _familyMembers = MutableLiveData<List<FamilyMemberItem>>(emptyList())
    val familyMembers: LiveData<List<FamilyMemberItem>> = _familyMembers

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // -------------------------------------------------------------------------
    // Public entry points
    // -------------------------------------------------------------------------

    fun loadMemberByGuid(memberGuid: String, recordStatus: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                memberDao.getByGuid(memberGuid, recordStatus)?.let { entityToDetail(it) }
            }
            _memberDetail.postValue(result)
            _isLoading.postValue(false)
        }
    }

    /**
     * Fallback for callers that supply a content:// URI (legacy navigation).
     * Extracts the row ID from [memberUri]'s last path segment and queries by ID + status.
     */
    fun loadMember(memberUri: Uri, recordStatus: String) {
        val id = memberUri.lastPathSegment?.toLongOrNull() ?: return
        _isLoading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                memberDao.getByIdAndStatus(id, recordStatus)?.let { entityToDetail(it) }
            }
            _memberDetail.postValue(result)
            _isLoading.postValue(false)
        }
    }

    fun loadFamily(familyHeadGuid: String, recordStatus: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                memberDao.getFamilyMembersEntities(familyHeadGuid, recordStatus)
                    .map { entityToFamilyMember(it) }
            }
            _familyMembers.postValue(result)
        }
    }

    // -------------------------------------------------------------------------
    // Entity → model conversion  (identical field logic to the old cursor path)
    // -------------------------------------------------------------------------

    private fun entityToDetail(entity: MemberEntity): MemberDetailItem {
        // Birthday
        val bDayRaw = entity.geboortedatum ?: ""
        val bDay = if (bDayRaw.length >= 10) bDayRaw.substring(0, 10) else bDayRaw
        var ageYears = -1L
        if (bDay.isNotEmpty()) {
            try {
                parseDate(bDay)?.let { ageYears = ChronoUnit.YEARS.between(it, LocalDate.now()) }
            } catch (_: Exception) {
            }
        }

        // Anniversary
        val huwelikDatum = entity.huwelikDate ?: ""
        var huwelikYears = -1L
        if (huwelikDatum.isNotEmpty()) {
            try {
                parseDate(huwelikDatum)?.let {
                    huwelikYears = ChronoUnit.YEARS.between(it, LocalDate.now())
                }
            } catch (_: Exception) {
            }
        }

        // Address normalisation (multi-line → comma-separated)
        fun String?.normaliseAddress(): String =
            (this ?: "")
                .replace("\r\n", ", ")
                .replace("\r", ", ")
                .replace("\n", ", ")
                .replace(", , ", ", ")
                .replace(",  ,", ", ")

        return MemberDetailItem(
            id = entity.id.toInt(),
            guid = entity.memberGUID ?: "",
            familyHeadGuid = entity.familyHeadGUID ?: "",
            name = entity.noemnaam ?: "",
            surname = entity.van ?: "",
            // LIDMATE_VOORNAME maps to column "Naam" → entity.naam
            fullNames = entity.naam ?: "",
            maidenName = entity.nooiensvan ?: "",
            cellphone = (entity.selfoon ?: "").let {
                if (it.isNotBlank()) fixphonenumber(it) ?: "" else ""
            },
            landline = (entity.landlyn ?: "").let {
                if (it.isNotBlank()) fixphonenumber(it) ?: "" else ""
            },
            ward = entity.wyk ?: "",
            birthday = bDay,
            age = ageYears,
            streetAddress = entity.straatadres.normaliseAddress(),
            postalAddress = (entity.posadres ?: "")
                .replace("\r\n", ", ").replace("\r", ", ").replace("\n", ", ")
                .replace(", , ", ", "),
            email = entity.epos ?: "",
            profession = entity.beroep ?: "",
            employer = entity.werkgewer ?: "",
            gender = entity.geslag ?: "",
            // Default to "Ongetroud" when the field is blank, matching old cursor behaviour.
            marriageStatus = (entity.huwelikstatus ?: "").ifEmpty { "Ongetroud" },
            memberStatus = entity.lidmaatstatus ?: "",
            certificateStatus = entity.bewysstatus ?: "",
            // Milestone dates & ministers
            baptismDate = entity.doopDate ?: "",
            baptismDs = entity.doopMinister ?: "",
            confessionDate = entity.belydenisafleggingDate ?: "",
            confessionDs = entity.belydenisafleggingMinister ?: "",
            marriageDate = huwelikDatum,
            marriageYears = huwelikYears,
            gemeente = entity.gemeente ?: ""
        )
    }

    private fun entityToFamilyMember(entity: MemberEntity): FamilyMemberItem {
        val bDayRaw = entity.geboortedatum ?: ""
        val bDay = if (bDayRaw.length >= 10) bDayRaw.substring(0, 10) else bDayRaw
        var age = -1L
        if (bDay.isNotEmpty()) {
            try {
                parseDate(bDay)?.let { age = ChronoUnit.YEARS.between(it, LocalDate.now()) }
            } catch (_: Exception) {
            }
        }

        return FamilyMemberItem(
            id = entity.id.toInt(),
            name = entity.noemnaam ?: "",
            surname = entity.van ?: "",
            birthday = bDay,
            age = age,
            picturePath = entity.fotostoorplek ?: "",
            guid = entity.memberGUID ?: ""
        )
    }
}