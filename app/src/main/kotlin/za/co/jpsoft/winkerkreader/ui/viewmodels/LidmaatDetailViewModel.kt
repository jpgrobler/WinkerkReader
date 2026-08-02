package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.data.members.dao.MemberDao
import za.co.jpsoft.winkerkreader.data.members.models.FamilyMemberItem
import za.co.jpsoft.winkerkreader.data.members.models.MemberDetailItem
import za.co.jpsoft.winkerkreader.data.pastoral.repository.FamilyMemberRepository
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.utils.Utils.parseDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@HiltViewModel
class LidmaatDetailViewModel @Inject constructor(
    private val familyRepo: FamilyMemberRepository,
    private val memberDao: MemberDao   // already injected – use directly
) : ViewModel() {

    // REMOVED: memberDao = WinkerkDatabase.getInstance(application).memberDao() // ← DELETE THIS LINE

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
            val members = withContext(Dispatchers.IO) {
                familyRepo.getFamilyMembers(
                    memberGuid = "",
                    familyHeadGuid = familyHeadGuid,
                    recordStatus = recordStatus
                )
            }
            val items = members.map { domain ->
                entityToFamilyMemberItem(domain)
            }
            _familyMembers.postValue(items)
        }
    }

    // Helper to convert domain → FamilyMemberItem
    private fun entityToFamilyMemberItem(domain: za.co.jpsoft.winkerkreader.data.pastoral.model.FamilyMember): FamilyMemberItem {
        val birthday = domain.birthday
        val age = if (birthday.isNotEmpty()) {
            try {
                parseDate(birthday)?.let {
                    ChronoUnit.YEARS.between(it, LocalDate.now())
                } ?: -1
            } catch (_: Exception) {
                -1
            }
        } else -1

        return FamilyMemberItem(
            id = 0,   // domain doesn't have DB id; activity uses it only for click navigation
            name = domain.displayName.split(' ').firstOrNull() ?: "",
            surname = domain.displayName.split(' ').drop(1).joinToString(" "),
            birthday = birthday,
            age = age,
            picturePath = "", // will be loaded by PhotoHelper using guid
            guid = domain.guid
        )
    }

    // -------------------------------------------------------------------------
    // Entity → model conversion  (identical field logic to the old cursor path)
    // -------------------------------------------------------------------------

    private fun entityToDetail(entity: za.co.jpsoft.winkerkreader.data.members.entities.MemberEntity): MemberDetailItem {
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
}