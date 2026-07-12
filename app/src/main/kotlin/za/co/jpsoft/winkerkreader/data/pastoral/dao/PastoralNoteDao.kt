package za.co.jpsoft.winkerkreader.data.pastoral.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import za.co.jpsoft.winkerkreader.data.pastoral.entities.PastoralNoteEntity

@Dao
interface PastoralNoteDao {

    // ── Observe (Flow) ────────────────────────────────────────────────────────

    @Query(
        """
        SELECT * FROM pastoral_notes
        WHERE memberGuid = :memberGuid
        ORDER BY noteDateUtc DESC
    """
    )
    fun observeForMember(memberGuid: String): Flow<List<PastoralNoteEntity>>

    @Query(
        """
        SELECT * FROM pastoral_notes
        ORDER BY noteDateUtc DESC
    """
    )
    fun observeAll(): Flow<List<PastoralNoteEntity>>

    // ── One-shot queries ──────────────────────────────────────────────────────

    @Query(
        """
        SELECT * FROM pastoral_notes
        WHERE memberGuid = :memberGuid
        ORDER BY noteDateUtc DESC
    """
    )
    suspend fun getForMember(memberGuid: String): List<PastoralNoteEntity>

    @Query("SELECT * FROM pastoral_notes WHERE noteId = :noteId LIMIT 1")
    suspend fun getById(noteId: String): PastoralNoteEntity?

    @Query("SELECT COUNT(*) FROM pastoral_notes WHERE memberGuid = :memberGuid")
    suspend fun countForMember(memberGuid: String): Int

    // ── Recent notes across all members (for "Laaste aktiwiteit" overview) ───

    @Query(
        """
        SELECT * FROM pastoral_notes
        ORDER BY noteDateUtc DESC
        LIMIT :limit
    """
    )
    suspend fun getRecent(limit: Int = 20): List<PastoralNoteEntity>

    // ── Write ─────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: PastoralNoteEntity)

    @Update
    suspend fun update(note: PastoralNoteEntity)

    @Query("DELETE FROM pastoral_notes WHERE noteId = :noteId")
    suspend fun deleteById(noteId: String)

    // ── Link to reminder (set after reminder is created alongside the note) ───

    @Query(
        """
        UPDATE pastoral_notes
        SET linkedReminderId = :reminderId,
            updatedAt        = :now
        WHERE noteId = :noteId
    """
    )
    suspend fun linkToReminder(noteId: String, reminderId: String, now: Long)
}