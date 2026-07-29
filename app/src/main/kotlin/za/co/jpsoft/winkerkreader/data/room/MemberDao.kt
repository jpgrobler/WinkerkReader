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

    // ── New method for photo sync ──────────────────────────────
    @Query("SELECT MemberGUID FROM Members WHERE MemberGUID IS NOT NULL AND MemberGUID != ''")
    fun getAllMemberGuids(): List<String>

    // ── Existing detail‑screen queries ────────────────────────

    @Query("SELECT * FROM Members WHERE MemberGUID = :guid AND Rekordstatus = :recordStatus LIMIT 1")
    fun getByGuid(guid: String, recordStatus: String): MemberEntity?

    @Query("SELECT * FROM Members WHERE _id = :id AND Rekordstatus = :recordStatus LIMIT 1")
    fun getByIdAndStatus(id: Long, recordStatus: String): MemberEntity?

    @Query(
        """
        SELECT * FROM Members
        WHERE FamilyHeadGUID = :familyHeadGuid
          AND Rekordstatus = :recordStatus
        ORDER BY Gesinsrol ASC
    """
    )
    fun getFamilyMembersEntities(familyHeadGuid: String, recordStatus: String): List<MemberEntity>

    @Query("SELECT Gemeente FROM Members WHERE MemberGUID = :guid LIMIT 1")
    fun getCongregationByGuid(guid: String): String?
}