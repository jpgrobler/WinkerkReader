package za.co.jpsoft.winkerkreader.data.pastoral.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PastoralReminderRepositoryTest {

    private val zoneId: ZoneId = ZoneId.of("Africa/Johannesburg")

    @Test
    fun operasieTemplate_expandsNegativeAndPositiveOffsets() {
        val anchorDate = LocalDate.of(2026, 3, 15)
        val offsets = listOf(-1, 0, 1, 3, 7, 14)

        val dueDates = offsets.map { offset ->
            PastoralReminderDates.expandDueDate(anchorDate, operasieStep(offset))
        }

        assertEquals(
            listOf(
                anchorDate.minusDays(1),
                anchorDate,
                anchorDate.plusDays(1),
                anchorDate.plusDays(3),
                anchorDate.plusDays(7),
                anchorDate.plusDays(14)
            ),
            dueDates
        )
    }

    @Test
    fun naSterfTemplate_expandsFiveOffsets() {
        val anchorDate = LocalDate.of(2026, 1, 10)
        val offsets = listOf(3, 14, 30, 90, 365)

        val dueDates = offsets.map { offset ->
            PastoralReminderDates.expandDueDate(anchorDate, templateStep(offset))
        }

        assertEquals(
            listOf(
                anchorDate.plusDays(3),
                anchorDate.plusDays(14),
                anchorDate.plusDays(30),
                anchorDate.plusDays(90),
                anchorDate.plusDays(365)
            ),
            dueDates
        )
    }

    @Test
    fun dateOnlyReminder_usesStartOfDayInZone() {
        val dueDate = LocalDate.of(2026, 6, 12)
        val dueDateUtc = PastoralReminderDates.toDueDateUtc(
            dueDate = dueDate,
            scheduleType = ScheduleType.DATE_ONLY,
            dueTime = null,
            defaultHour = 8,
            defaultMinute = 0,
            zoneId = zoneId
        )

        val instant = Instant.ofEpochMilli(dueDateUtc).atZone(zoneId)
        assertEquals(0, instant.hour)
        assertEquals(0, instant.minute)
        assertEquals(dueDate, instant.toLocalDate())
    }

    @Test
    fun timedReminder_usesSuppliedTime() {
        val dueDate = LocalDate.of(2026, 5, 20)
        val dueDateUtc = PastoralReminderDates.toDueDateUtc(
            dueDate = dueDate,
            scheduleType = ScheduleType.TIMED,
            dueTime = LocalTime.of(14, 30),
            defaultHour = 8,
            defaultMinute = 0,
            zoneId = zoneId
        )

        val instant = Instant.ofEpochMilli(dueDateUtc).atZone(zoneId)
        assertEquals(14, instant.hour)
        assertEquals(30, instant.minute)
        assertEquals(dueDate, instant.toLocalDate())
    }

    @Test
    fun expandDueDate_appliesMonthOffset() {
        val anchorDate = LocalDate.of(2026, 1, 31)
        val step = templateStep(offsetDays = 0, offsetMonths = 1)

        assertEquals(LocalDate.of(2026, 2, 28), PastoralReminderDates.expandDueDate(anchorDate, step))
    }

    private fun operasieStep(offsetDays: Int): TemplateStepEntity {
        return templateStep(offsetDays = offsetDays)
    }

    private fun templateStep(
        offsetDays: Int,
        offsetMonths: Int = 0
    ): TemplateStepEntity {
        return TemplateStepEntity(
            stepId = "test-step",
            templateId = "sys-TEST",
            stepOrder = 1,
            offsetDays = offsetDays,
            offsetMonths = offsetMonths,
            defaultTitleAf = "Test",
            defaultNoteAf = null,
            scheduleType = ScheduleType.DATE_ONLY.name
        )
    }


}
