package za.co.jpsoft.winkerkreader.data.pastoral.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pastoral_notes",
    indices = [
        Index("memberGuid"),
        Index("noteDateUtc"),
        Index("category")
    ]
)
data class PastoralNoteEntity(
    @PrimaryKey val noteId: String,          // UUID

    val memberGuid: String,
    val familyHeadGuid: String?,

    // Cached from MemberEntity at save time — same pattern as FollowUpReminderEntity
    val memberSurname: String?,
    val memberGivenName: String?,
    val memberDisplayNameCache: String?,

    val noteDateUtc: Long,                   // When the interaction happened (start-of-day UTC)
    val category: String,                    // NoteCategory enum stored as .name
    val noteText: String,

    val isConfidential: Boolean = false,     // Hides content preview in list

    // Optional link to a reminder that was created alongside this note
    val linkedReminderId: String? = null,

    val createdAt: Long,
    val updatedAt: Long
)