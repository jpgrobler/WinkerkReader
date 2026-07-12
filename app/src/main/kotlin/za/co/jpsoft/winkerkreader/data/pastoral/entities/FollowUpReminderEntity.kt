package za.co.jpsoft.winkerkreader.data.pastoral.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "follow_up_reminders",
    indices = [
        Index("memberGuid"),
        Index("dueDateUtc"),
        Index("status"),
        Index("calendarEventId")
    ]
)
data class FollowUpReminderEntity(
    @PrimaryKey val reminderId: String,
    val memberGuid: String,
    val familyHeadGuid: String?,
    val templateId: String?,
    val templateStepId: String?,
    val anchorDateUtc: Long?,
    val symbol: String? = null,
    val title: String,
    val note: String?,
    val contextJson: String? = null,
    val scheduleType: String,
    val dueDateUtc: Long,
    val dueEndUtc: Long?,
    val status: String,
    val completedAtUtc: Long?,
    val snoozedUntilUtc: Long?,
    val lastNotifiedDateUtc: Long?,
    val calendarEventId: Long?,
    val calendarSynced: Boolean = false,
    val googleTaskId: String? = null,
    val googleTaskSynced: Boolean = false,
    val memberDisplayNameCache: String?,
    val memberSurname: String? = null,
    val memberGivenName: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
