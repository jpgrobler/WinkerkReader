package za.co.jpsoft.winkerkreader.data.room

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface MemberDao {

    @RawQuery(observedEntities = [MemberEntity::class])
    fun queryRaw(query: SupportSQLiteQuery): Cursor

    // Used by WinkerkProvider / legacy Cursor-based callers — kept as-is.
    @Query(
        """
        SELECT * FROM Members
        WHERE FamilyHeadGUID = :familyHeadGuid
          AND Rekordstatus = :recordStatus
        ORDER BY Gesinsrol ASC
    """
    )
    fun getFamilyMembers(familyHeadGuid: String, recordStatus: String): Cursor

    @Query("SELECT * FROM Members WHERE _id = :id")
    fun getById(id: Long): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(member: MemberEntity)

    @Update
    fun update(member: MemberEntity)

    @Query("UPDATE Members SET Tag = :tag WHERE _id = :id")
    fun updateTag(id: Long, tag: String)

    @RawQuery(observedEntities = [MemberEntity::class])
    fun getMembersRaw(query: SupportSQLiteQuery): List<MemberEntity>

    @RawQuery
    fun countRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT COUNT(*) FROM Members")
    fun getCount(): Int

    // ── Detail-screen queries (Room-direct, replaces ContentProvider round-trip) ──

    /**
     * Loads a single member by GUID and record-status.
     * Primary entry point for [LidmaatDetailViewModel.loadMemberByGuid].
     */
    @Query("SELECT * FROM Members WHERE MemberGUID = :guid AND Rekordstatus = :recordStatus LIMIT 1")
    fun getByGuid(guid: String, recordStatus: String): MemberEntity?

    /**
     * Loads a single member by primary-key ID and record-status.
     * Used by [LidmaatDetailViewModel.loadMember] when the caller supplies a content URI.
     */
    @Query("SELECT * FROM Members WHERE _id = :id AND Rekordstatus = :recordStatus LIMIT 1")
    fun getByIdAndStatus(id: Long, recordStatus: String): MemberEntity?

    /**
     * Returns all family members for a given family-head GUID as entities.
     * Replaces the Cursor-returning [getFamilyMembers] in the detail-screen path.
     */
    @Query(
        """
        SELECT * FROM Members
        WHERE FamilyHeadGUID = :familyHeadGuid
          AND Rekordstatus = :recordStatus
        ORDER BY Gesinsrol ASC
    """
    )
    fun getFamilyMembersEntities(familyHeadGuid: String, recordStatus: String): List<MemberEntity>

    // ── Widget queries ────────────────────────────────────────────────────────

    /**
     * Returns only the Gemeente column for a given MemberGUID.
     * Used by [PastoralWidgetRemoteViewsService] to colour the congregation indicator
     * without loading the full entity.
     * No record-status filter — pastoral reminders may target any member.
     */
    @Query("SELECT Gemeente FROM Members WHERE MemberGUID = :guid LIMIT 1")
    fun getCongregationByGuid(guid: String): String?
}