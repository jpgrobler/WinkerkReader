package za.co.jpsoft.winkerkreader.data.pastoral.repository

import jakarta.inject.Inject
import jakarta.inject.Singleton
import za.co.jpsoft.winkerkreader.data.members.dao.MemberDao
import za.co.jpsoft.winkerkreader.data.pastoral.model.FamilyMember

@Singleton
class FamilyMemberRepository @Inject constructor(
    private val memberDao: MemberDao
) {

    suspend fun getFamilyMembers(
        memberGuid: String,
        familyHeadGuid: String?,
        recordStatus: String = "0"
    ): List<FamilyMember> {
        val headGuid = familyHeadGuid?.takeIf { it.isNotBlank() } ?: memberGuid
        val entities = memberDao.getFamilyMembersEntities(headGuid, recordStatus)
        return entities.map { entity ->
            FamilyMember(
                guid = entity.memberGUID ?: "",
                displayName = listOf(entity.noemnaam, entity.van)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" ")
                    .ifEmpty { "Onbekend" },
                birthday = entity.geboortedatum?.takeIf { it.length >= 10 }
                    ?.substring(0, 10) ?: ""
            )
        }
    }
}