package za.co.jpsoft.winkerkreader.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import android.database.Cursor

@Dao
interface MemberDao {

    @RawQuery(observedEntities = [MemberEntity::class])
    fun queryRaw(query: SupportSQLiteQuery): Cursor

    @Query("""
        SELECT * FROM Members
        WHERE FamilyHeadGUID = :familyHeadGuid
          AND Rekordstatus = :recordStatus
        ORDER BY Gesinsrol ASC
    """)
    fun getFamilyMembers(familyHeadGuid: String, recordStatus: String): Cursor

    @Query("SELECT * FROM Members WHERE _id = :id")
    fun getById(id: Long): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(member: MemberEntity)

    @Update
    fun update(member: MemberEntity)

    @Query("UPDATE Members SET Tag = :tag WHERE _id = :id")
    fun updateTag(id: Long, tag: String)
}