package za.co.jpsoft.winkerkreader.data.members.dao

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteQuery
import za.co.jpsoft.winkerkreader.data.members.entities.MemberEntity
import za.co.jpsoft.winkerkreader.data.members.setup.WinkerkDatabase

/**
 * MemberDao that always resolves against the current [WinkerkDatabase] companion
 * instance. Required because Hilt @Singleton bindings would otherwise keep a DAO
 * attached to a RoomDatabase that was closed during a database import/swap.
 */
class LiveMemberDao(
    private val appContext: Context
) : MemberDao {

    private fun current(): MemberDao =
        WinkerkDatabase.getInstance(appContext).memberDao()

    override fun queryRaw(query: SupportSQLiteQuery): Cursor =
        current().queryRaw(query)

    override fun getFamilyMembers(familyHeadGuid: String, recordStatus: String): Cursor =
        current().getFamilyMembers(familyHeadGuid, recordStatus)

    override fun getById(id: Long): MemberEntity? =
        current().getById(id)

    override fun insert(member: MemberEntity) =
        current().insert(member)

    override fun update(member: MemberEntity) =
        current().update(member)

    override fun updateTag(id: Long, tag: String) =
        current().updateTag(id, tag)

    override fun getMembersRaw(query: SupportSQLiteQuery): List<MemberEntity> =
        current().getMembersRaw(query)

    override fun countRaw(query: SupportSQLiteQuery): Int =
        current().countRaw(query)

    override fun getCount(): Int =
        current().getCount()

    override fun getAllMemberGuids(): List<String> =
        current().getAllMemberGuids()

    override fun getByGuid(guid: String, recordStatus: String): MemberEntity? =
        current().getByGuid(guid, recordStatus)

    override fun getByIdAndStatus(id: Long, recordStatus: String): MemberEntity? =
        current().getByIdAndStatus(id, recordStatus)

    override fun getFamilyMembersEntities(
        familyHeadGuid: String,
        recordStatus: String
    ): List<MemberEntity> =
        current().getFamilyMembersEntities(familyHeadGuid, recordStatus)

    override fun getCongregationByGuid(guid: String): String? =
        current().getCongregationByGuid(guid)
}
