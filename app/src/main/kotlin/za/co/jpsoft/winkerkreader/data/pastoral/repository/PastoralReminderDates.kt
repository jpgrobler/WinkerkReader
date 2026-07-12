package za.co.jpsoft.winkerkreader.data.pastoral.repository

import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

internal object PastoralReminderDates {

    fun expandDueDate(anchorDate: LocalDate, step: TemplateStepEntity): LocalDate {
        return anchorDate
            .plusMonths(step.offsetMonths.toLong())
            .plusDays(step.offsetDays.toLong())
    }

    fun toDueDateUtc(
        dueDate: LocalDate,
        scheduleType: ScheduleType,
        dueTime: LocalTime?,
        defaultHour: Int?,
        defaultMinute: Int?,
        zoneId: ZoneId
    ): Long {
        return when (scheduleType) {
            ScheduleType.DATE_ONLY -> {
                // All-day events: store UTC midnight of the target date
                dueDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }

            ScheduleType.TIMED -> {
                val time = dueTime ?: LocalTime.of(
                    defaultHour ?: 8,
                    defaultMinute ?: 0
                )
                LocalDateTime.of(dueDate, time).atZone(zoneId).toInstant().toEpochMilli()
            }
        }
    }

    fun anchorDateUtc(anchorDate: LocalDate, zoneId: ZoneId): Long {
        return anchorDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}
