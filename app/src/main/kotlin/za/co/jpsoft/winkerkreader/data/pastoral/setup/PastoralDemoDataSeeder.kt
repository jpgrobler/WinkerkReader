package za.co.jpsoft.winkerkreader.data.pastoral.setup

import android.content.Context
import android.util.Log
import jakarta.inject.Inject
import jakarta.inject.Singleton
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract
import za.co.jpsoft.winkerkreader.data.members.setup.WinkerkDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.dao.FollowUpReminderDao
import za.co.jpsoft.winkerkreader.data.pastoral.dao.PastoralNoteDao
import za.co.jpsoft.winkerkreader.data.pastoral.dao.ReminderTemplateDao
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.PastoralNoteEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderStatus
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContext
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderDates
import za.co.jpsoft.winkerkreader.utils.prefs.PastoralPrefs
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Seeds the pastoral module (notes + follow-up reminders) with a coherent
 * demo timeline, anchored to the moment the demo database was first loaded.
 *
 * Reminders are created from the built-in system templates (same path as a real
 * [za.co.jpsoft.winkerkreader.data.pastoral.repository.ReminderCrudRepository.createFromTemplate]
 * call): every template step becomes a reminder, with [TemplateContext] fields filled.
 * Calendar / Google Tasks sync is intentionally skipped for demo rows.
 *
 * IMPORTANT: only ever called when DatabaseInitializer confirms the asset
 * (demo) member DB was just copied in — never for a real congregation DB.
 */
@Singleton
class PastoralDemoDataSeeder @Inject constructor(
    private val noteDao: PastoralNoteDao,
    private val reminderDao: FollowUpReminderDao,
    private val templateDao: ReminderTemplateDao,
    private val pastoralPrefs: PastoralPrefs
) {
    private val TAG = "PastoralDemoDataSeeder"
    private val zoneId: ZoneId = ZoneId.systemDefault()

    private data class DemoMember(
        val guid: String,
        val displayName: String,
        val surname: String,
        val givenName: String
    )

    private data class NoteSeed(
        val memberSlot: Int,
        val dayOffset: Int,
        val category: String,
        val text: String,
        val confidential: Boolean = false
    )

    /**
     * One demo series per system template.
     * [anchorOffsetDays] = days relative to "today" for the template anchor date.
     */
    private data class TemplateSeriesSeed(
        val templateCode: String,
        val memberSlot: Int,
        val anchorOffsetDays: Int,
        val context: TemplateContext
    )

    suspend fun seedIfNeeded(context: Context) {
        if (pastoralPrefs.demoDataAnchorUtc != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Demo pastoral data already seeded — skipping")
            return
        }

        val members = resolveDemoMembers(context)
        if (members.size < 3) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Not enough demo members found (${members.size}) — skipping seed")
            }
            return
        }

        val today = LocalDate.now(zoneId)
        val createdAt = today.atTime(8, 0).atZone(zoneId).toInstant().toEpochMilli()
        val seriesSeeds = buildTemplateSeriesSeeds(members, today)

        val notes = buildNoteSeeds().map { it.toEntity(members, today, createdAt) }
        val reminders = seriesSeeds.flatMap { series ->
            expandSeries(series, members, today, createdAt)
        }

        if (reminders.isEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "No template-backed reminders created — templates missing?")
            }
            return
        }

        notes.forEach { noteDao.insert(it) }
        reminderDao.insertAll(reminders)

        pastoralPrefs.demoDataAnchorUtc = createdAt
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Seeded ${notes.size} demo notes, ${reminders.size} template reminders " +
                        "across ${seriesSeeds.size} series, anchor=$createdAt"
            )
        }
    }

    /** Wipes demo pastoral data — call this when the user replaces the demo DB with a real one. */
    suspend fun clearDemoData() {
        val anchor = pastoralPrefs.demoDataAnchorUtc ?: return
        reminderDao.deleteByCreatedAt(anchor)
        noteDao.deleteByCreatedAt(anchor)
        pastoralPrefs.demoDataAnchorUtc = null
        if (BuildConfig.DEBUG) Log.d(TAG, "Cleared demo pastoral data")
    }

    // ─────────────────────────────────────────────────────────────────────

    private fun resolveDemoMembers(context: Context): List<DemoMember> {
        val result = mutableListOf<DemoMember>()
        val db = WinkerkDatabase.getInstance(context)
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT [${WinkerkContract.winkerkEntry.LIDMATE_LIDMAATGUID}], " +
                    "[${WinkerkContract.winkerkEntry.LIDMATE_VOORNAME}], " +
                    "[${WinkerkContract.winkerkEntry.LIDMATE_VAN}] " +
                    "FROM Members ORDER BY [_id] ASC LIMIT 6"
        )
        cursor.use {
            while (it.moveToNext()) {
                val guid = it.getString(0) ?: continue
                val given = it.getString(1) ?: continue
                val surname = it.getString(2) ?: continue
                result += DemoMember(
                    guid = guid,
                    displayName = "$given $surname",
                    surname = surname,
                    givenName = given
                )
            }
        }
        return result
    }

    private suspend fun expandSeries(
        series: TemplateSeriesSeed,
        members: List<DemoMember>,
        today: LocalDate,
        createdAt: Long
    ): List<FollowUpReminderEntity> {
        val template = templateDao.getTemplateByCode(series.templateCode)
        if (template == null) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "System template ${series.templateCode} not found — skipping series")
            }
            return emptyList()
        }
        val steps = templateDao.getStepsForTemplate(template.templateId)
        if (steps.isEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Template ${series.templateCode} has no steps — skipping")
            }
            return emptyList()
        }

        val member = members[series.memberSlot % members.size]
        val anchorDate = today.plusDays(series.anchorOffsetDays.toLong())
        val contextJson = series.context.toJson().takeIf { series.context.values.isNotEmpty() }

        return steps.map { step ->
            buildReminderFromStep(
                member = member,
                templateId = template.templateId,
                templateSymbol = template.symbol,
                step = step,
                anchorDate = anchorDate,
                contextJson = contextJson,
                today = today,
                createdAt = createdAt
            )
        }
    }

    private fun buildReminderFromStep(
        member: DemoMember,
        templateId: String,
        templateSymbol: String?,
        step: TemplateStepEntity,
        anchorDate: LocalDate,
        contextJson: String?,
        today: LocalDate,
        createdAt: Long
    ): FollowUpReminderEntity {
        val dueDate = PastoralReminderDates.expandDueDate(anchorDate, step)
        val scheduleType = ScheduleType.fromStored(step.scheduleType)
        val dueDateUtc = PastoralReminderDates.toDueDateUtc(
            dueDate = dueDate,
            scheduleType = scheduleType,
            dueTime = null,
            defaultHour = step.defaultHour,
            defaultMinute = step.defaultMinute,
            zoneId = zoneId
        )

        // Older past steps → COMPLETED; recent overdue / today / future stay PENDING
        // so the dashboard still shows meaningful overdue + upcoming demo items.
        val isCompleted = dueDate.isBefore(today.minusDays(3))
        val status = if (isCompleted) ReminderStatus.COMPLETED else ReminderStatus.PENDING

        return FollowUpReminderEntity(
            reminderId = UUID.randomUUID().toString(),
            memberGuid = member.guid,
            familyHeadGuid = null,
            templateId = templateId,
            templateStepId = step.stepId,
            symbol = templateSymbol,
            anchorDateUtc = PastoralReminderDates.anchorDateUtc(anchorDate, zoneId),
            title = step.defaultTitleAf,
            note = step.defaultNoteAf,
            contextJson = contextJson,
            scheduleType = scheduleType.name,
            dueDateUtc = dueDateUtc,
            dueEndUtc = null,
            status = status.name,
            completedAtUtc = if (isCompleted) {
                dueDate.atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli()
            } else {
                null
            },
            snoozedUntilUtc = null,
            lastNotifiedDateUtc = null,
            calendarEventId = null,
            calendarSynced = false,
            memberDisplayNameCache = member.displayName,
            memberSurname = member.surname,
            memberGivenName = member.givenName,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    private fun NoteSeed.toEntity(
        members: List<DemoMember>,
        today: LocalDate,
        createdAt: Long
    ): PastoralNoteEntity {
        val m = members[memberSlot % members.size]
        val noteDate = today.plusDays(dayOffset.toLong())
        return PastoralNoteEntity(
            noteId = UUID.randomUUID().toString(),
            memberGuid = m.guid,
            familyHeadGuid = null,
            memberSurname = m.surname,
            memberGivenName = m.givenName,
            memberDisplayNameCache = m.displayName,
            noteDateUtc = noteDate.atTime(8, 0).atZone(zoneId).toInstant().toEpochMilli(),
            category = category,
            noteText = text,
            isConfidential = confidential,
            linkedReminderId = null,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    // ─── Demo narrative: one series per built-in template ─────────────────

    private fun buildTemplateSeriesSeeds(
        members: List<DemoMember>,
        today: LocalDate
    ): List<TemplateSeriesSeed> {
        val m0 = members[0]
        val m1 = members.getOrElse(1) { m0 }
        return listOf(
            // Hospitalisasie — mid-recovery so some steps done, some upcoming
            TemplateSeriesSeed(
                templateCode = "OPERASIE",
                memberSlot = 0,
                anchorOffsetDays = -5,
                context = TemplateContext.build {
                    put("hospital", "Mediclinic Bloemfontein")
                }
            ),
            // Na sterfgeval — ~3 weeks after death
            TemplateSeriesSeed(
                templateCode = "NA_STERF",
                memberSlot = 1,
                anchorOffsetDays = -21,
                context = TemplateContext.build {
                    put("deceasedName", "${m1.givenName.firstOrNull() ?: 'A'}. ${m1.surname}")
                    put("deceasedDob", today.minusYears(78).minusMonths(3))
                    put("deceasedDate", today.minusDays(21))
                }
            ),
            // Algemene opvolg — single step due in a week
            TemplateSeriesSeed(
                templateCode = "ALGEMEEN",
                memberSlot = 2,
                anchorOffsetDays = 0,
                context = TemplateContext()
            ),
            // Nuwe intrekker — joined ~10 days ago
            TemplateSeriesSeed(
                templateCode = "NUWE_LID",
                memberSlot = 3,
                anchorOffsetDays = -10,
                context = TemplateContext()
            ),
            // Siekte
            TemplateSeriesSeed(
                templateCode = "SIEKTE",
                memberSlot = 4,
                anchorOffsetDays = -2,
                context = TemplateContext.build {
                    put("illness", "Longontsteking")
                }
            ),
            // Trauma
            TemplateSeriesSeed(
                templateCode = "TRAUMA",
                memberSlot = 0,
                anchorOffsetDays = -1,
                context = TemplateContext.build {
                    put("traumaType", "Motorongeluk")
                    put("traumaDate", today.minusDays(1))
                }
            )
        )
    }

    private fun buildNoteSeeds() = listOf(
        NoteSeed(
            0, -60, "BESOEK",
            "Hospitaalbesoek na operasie. Herstel gaan goed, familie is dankbaar vir omgee."
        ),
        NoteSeed(
            1, -21, "BESOEK",
            "Huisbesoek gedoen. Bespreek behoefte aan gereelde kontak na verlies van eggenoot."
        ),
        NoteSeed(
            2, -10, "GESPREK",
            "Vertroulike gesprek oor gesinsuitdagings. Sensitief hanteer.",
            confidential = true
        ),
        NoteSeed(
            0, -5, "OPVOLG",
            "Telefoonoproep om te hoor hoe herstel vorder na hospitalisasie."
        ),
        NoteSeed(
            4, -2, "BESOEK",
            "Kort siektebesoek. Lidmaat is bedlêend met longontsteking."
        ),
        NoteSeed(
            0, -1, "GESPREK",
            "Ondersteuning na motorongeluk. Skok en angs — trauma-opvolgreeks begin."
        )
    )
}
