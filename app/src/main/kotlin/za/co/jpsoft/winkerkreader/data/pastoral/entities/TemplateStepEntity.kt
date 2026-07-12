package za.co.jpsoft.winkerkreader.data.pastoral.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_steps",
    foreignKeys = [
        ForeignKey(
            entity = ReminderTemplateEntity::class,
            parentColumns = ["templateId"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId")]
)
data class TemplateStepEntity(
    @PrimaryKey val stepId: String,
    val templateId: String,
    val stepOrder: Int,
    val offsetDays: Int,
    val offsetMonths: Int = 0,
    val defaultTitleAf: String,
    val defaultNoteAf: String?,
    val scheduleType: String,
    val defaultHour: Int? = 8,
    val defaultMinute: Int? = 0
)
