package za.co.jpsoft.winkerkreader.data.pastoral.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.entities.PastoralNoteEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.NoteCategory
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class PastoralNoteRepository(context: Context) {

    private val dao = PastoralDatabase.getInstance(context).pastoralNoteDao()

    // ── Observe ───────────────────────────────────────────────────────────────

    fun observeForMember(memberGuid: String): Flow<List<PastoralNoteEntity>> =
        dao.observeForMember(memberGuid)

    fun observeAll(): Flow<List<PastoralNoteEntity>> =
        dao.observeAll()

    // ── Read ──────────────────────────────────────────────────────────────────
    suspend fun getMemberGuidsWithNotes(): List<String> {
        return dao.getDistinctMemberGuids()
    }
    suspend fun getForMember(memberGuid: String): List<PastoralNoteEntity> =
        dao.getForMember(memberGuid)

    suspend fun getById(noteId: String): PastoralNoteEntity? =
        dao.getById(noteId)

    suspend fun countForMember(memberGuid: String): Int =
        dao.countForMember(memberGuid)

    suspend fun getRecent(limit: Int = 20): List<PastoralNoteEntity> =
        dao.getRecent(limit)

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Creates and persists a new [PastoralNoteEntity].
     * Returns the saved entity so the caller can link it to a reminder if needed.
     */
    suspend fun save(
        memberGuid: String,
        familyHeadGuid: String?,
        memberSurname: String?,
        memberGivenName: String?,
        memberDisplayName: String,
        noteDate: LocalDate,
        category: NoteCategory,
        noteText: String,
        isConfidential: Boolean = false,
        linkedReminderId: String? = null
    ): PastoralNoteEntity {
        val now = System.currentTimeMillis()
        val noteDateUtc = noteDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val entity = PastoralNoteEntity(
            noteId = UUID.randomUUID().toString(),
            memberGuid = memberGuid,
            familyHeadGuid = familyHeadGuid,
            memberSurname = memberSurname,
            memberGivenName = memberGivenName,
            memberDisplayNameCache = memberDisplayName,
            noteDateUtc = noteDateUtc,
            category = category.name,
            noteText = noteText,
            isConfidential = isConfidential,
            linkedReminderId = linkedReminderId,
            createdAt = now,
            updatedAt = now
        )
        dao.insert(entity)
        return entity
    }

    suspend fun update(note: PastoralNoteEntity) {
        dao.update(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(noteId: String) =
        dao.deleteById(noteId)

    /**
     * Called after a [FollowUpReminderEntity] is created alongside a note.
     * Links the two records so the UI can show "🔔 Herinnering gestel" on the note card.
     */
    suspend fun linkToReminder(noteId: String, reminderId: String) =
        dao.linkToReminder(noteId, reminderId, System.currentTimeMillis())
}