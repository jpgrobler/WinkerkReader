package za.co.jpsoft.winkerkreader.data.pastoral.model

import androidx.room.Embedded
import androidx.room.Relation
import za.co.jpsoft.winkerkreader.data.pastoral.entities.ReminderTemplateEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity

data class TemplateWithSteps(
    @Embedded val template: ReminderTemplateEntity,
    @Relation(
        parentColumn = "templateId",
        entityColumn = "templateId",
        entity = TemplateStepEntity::class
    )
    val steps: List<TemplateStepEntity>
)
