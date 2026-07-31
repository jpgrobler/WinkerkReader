package za.co.jpsoft.winkerkreader.utils

import za.co.jpsoft.winkerkreader.data.models.PendingReminderUiItem
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContext
import za.co.jpsoft.winkerkreader.utils.Utils.toLocalDateSafe
import java.time.LocalDate
import java.time.ZoneId

object ReminderUiMapper {

    private val zoneId = ZoneId.systemDefault()

    fun toUiItem(entity: FollowUpReminderEntity): PendingReminderUiItem {
        val dueDate = entity.dueDateUtc.toLocalDateSafe() ?: LocalDate.now()
        val today = LocalDate.now(zoneId)

        val contextSuffix = TemplateContext.from(entity.contextJson).let { ctx ->
            ctx.getString("hospital")
                ?: ctx.getString("deceasedName")
                ?: ctx.getString("illness")
                ?: ctx.getString("traumaType")
        }

        return PendingReminderUiItem(
            reminderId = entity.reminderId,
            title = entity.title,
            symbol = entity.symbol,
            contextSuffix = contextSuffix,
            dueDate = dueDate,
            isOverdue = dueDate.isBefore(today)
        )
    }
}