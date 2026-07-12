package za.co.jpsoft.winkerkreader.data.pastoral.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_templates")
data class ReminderTemplateEntity(
    @PrimaryKey val templateId: String,
    val code: String,
    val titleAf: String,
    val symbol: String? = null,
    val descriptionAf: String?,
    val isSystem: Boolean = true,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
