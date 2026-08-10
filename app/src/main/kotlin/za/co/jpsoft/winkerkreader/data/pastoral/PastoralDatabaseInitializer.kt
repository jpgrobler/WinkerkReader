package za.co.jpsoft.winkerkreader.data.pastoral

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabaseInitializer.Companion.seedMutex
import za.co.jpsoft.winkerkreader.data.pastoral.entities.PastoralMetaEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.ReminderTemplateEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.utils.DeviceIdManager

/**
 * Seeds [PastoralDatabase] with pastoral_meta and system reminder templates on first create.
 *
 * Invoked from [PastoralDatabase] via [RoomDatabase.Callback.onCreate] and an idempotent
 * post-build check so existing installs are not re-seeded.
 *
 * Guarded by [seedMutex] because [PastoralDatabase.getInstance] can be reached concurrently
 * from more than one initialization path (DI resolution, demo-data seeding, asset photo
 * copying) — without this guard, two overlapping first-run calls can both pass the
 * "is it empty?" check before either has written anything, causing duplicate/racing writes
 * against a database still mid-creation.
 */
class PastoralDatabaseInitializer(private val context: Context) {

    suspend fun seedIfEmpty(database: PastoralDatabase) {
        seedMutex.withLock {
            // Re-check inside the lock — another caller may have just finished seeding
            // while this one was waiting for the lock.
            if (database.pastoralMetaDao().get() != null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Pastoral DB already seeded — skipping")
                return
            }
            seed(database)
        }
    }

    suspend fun seed(database: PastoralDatabase) {
        val now = System.currentTimeMillis()

        // All-or-nothing: if this is interrupted partway (e.g. process death), a retry
        // won't find a half-seeded pastoral_meta row that would make seedIfEmpty()
        // wrongly think seeding already happened.
        database.withTransaction {
            val templateDao = database.reminderTemplateDao()
            val metaDao = database.pastoralMetaDao()

            metaDao.upsert(
                PastoralMetaEntity(
                    deviceId = DeviceIdManager.getDeviceId(context),
                    congregationName = null,
                    lastBackupUtc = null
                )
            )

            val templates = buildSystemTemplates(now)
            templateDao.insertTemplates(templates.map { it.template })
            templateDao.insertSteps(templates.flatMap { it.steps })

            if (BuildConfig.DEBUG) Log.i(
                TAG,
                "Seeded ${templates.size} pastoral reminder templates"
            )
        }
    }

    companion object {
        private const val TAG = "PastoralDbInitializer"

        /** Serializes first-run seeding across all callers/threads. */
        private val seedMutex = Mutex()

        private const val TEMPLATE_ID_NA_STERF = "sys-NA_STERF"
        private const val TEMPLATE_ID_OPERASIE = "sys-OPERASIE"
        private const val TEMPLATE_ID_ALGEMEEN = "sys-ALGEMEEN"
        private const val TEMPLATE_ID_NUWE_LID = "sys-NUWE_LID"
        private const val TEMPLATE_ID_SIEKTE = "sys-SIEKTE"
        private const val TEMPLATE_ID_TRAUMA = "sys-TRAUMA"

        fun callback(context: Context): RoomDatabase.Callback {
            return object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "Pastoral database created — tables ready for seed"
                    )
                }
            }
        }

        /**
         * Returns the original hardcoded step definitions for [templateCode],
         * or null if [templateCode] is not a system template.
         * Used by [PastoralReminderRepository.resetTemplateToDefault].
         */
        fun originalStepsFor(templateCode: String, now: Long): List<TemplateStepEntity>? {
            return buildSystemTemplates(now)
                .find { it.template.code == templateCode }
                ?.steps
        }

        /**
         * Blocking convenience wrapper for call sites that can't be suspend
         * (e.g. Room's synchronous [RoomDatabase.Callback]). Safe to call from
         * multiple threads — [seedIfEmpty]'s internal mutex serializes them.
         */
        fun seedIfEmptyBlocking(context: Context, database: PastoralDatabase) {
            runBlocking(Dispatchers.IO) {
                PastoralDatabaseInitializer(context.applicationContext).seedIfEmpty(database)
            }
        }

        internal fun buildSystemTemplates(now: Long): List<SeedTemplate> = listOf(
            SeedTemplate(
                template = ReminderTemplateEntity(
                    templateId = TEMPLATE_ID_NA_STERF,
                    code = "NA_STERF",
                    titleAf = "Na sterfgeval",
                    descriptionAf = "Opvolg na 'n sterfgeval",
                    symbol = "† ",
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now
                ),
                steps = listOf(
                    step(TEMPLATE_ID_NA_STERF, 1, 3, "3 dae na sterfgeval"),
                    step(TEMPLATE_ID_NA_STERF, 2, 14, "2 weke na sterfgeval"),
                    step(TEMPLATE_ID_NA_STERF, 3, 30, "1 maand na sterfgeval"),
                    step(TEMPLATE_ID_NA_STERF, 4, 90, "3 maande na sterfgeval"),
                    step(TEMPLATE_ID_NA_STERF, 5, 365, "1 jaar na sterfgeval")
                )
            ),
            SeedTemplate(
                template = ReminderTemplateEntity(
                    templateId = TEMPLATE_ID_OPERASIE,
                    code = "OPERASIE",
                    titleAf = "Hospitalisasie",
                    descriptionAf = "Opvolg voor, op en na 'n hospitalisasie",
                    symbol = "🏥 ",
                    sortOrder = 1,
                    createdAt = now,
                    updatedAt = now
                ),
                steps = listOf(
                    step(TEMPLATE_ID_OPERASIE, 1, -1, "Kontak voor opname"),
                    step(TEMPLATE_ID_OPERASIE, 2, 0, "Hospitalisasiedag"),
                    step(TEMPLATE_ID_OPERASIE, 3, 1, "1 dag na opname"),
                    step(TEMPLATE_ID_OPERASIE, 4, 3, "3 dae na opname"),
                    step(TEMPLATE_ID_OPERASIE, 5, 7, "1 week na opname"),
                    step(TEMPLATE_ID_OPERASIE, 6, 14, "2 weke na opname")
                )
            ),
            SeedTemplate(
                template = ReminderTemplateEntity(
                    templateId = TEMPLATE_ID_ALGEMEEN,
                    code = "ALGEMEEN",
                    titleAf = "Algemene opvolg",
                    descriptionAf = "Algemene opvolg",
                    symbol = "💬 ",
                    sortOrder = 2,
                    createdAt = now,
                    updatedAt = now
                ),
                steps = listOf(
                    step(TEMPLATE_ID_ALGEMEEN, 1, 7, "Algemene opvolg")
                )
            ),
            SeedTemplate(
                template = ReminderTemplateEntity(
                    templateId = TEMPLATE_ID_NUWE_LID,
                    code = "NUWE_LID",
                    titleAf = "Nuwe Intrekker",
                    descriptionAf = "Opvolg van 'n nuwe lidmaat",
                    symbol = "\uD83E\uDD17 ",
                    sortOrder = 3,
                    createdAt = now,
                    updatedAt = now
                ),
                steps = listOf(
                    step(TEMPLATE_ID_NUWE_LID, 1, 7, "1 week na aansluiting"),
                    step(TEMPLATE_ID_NUWE_LID, 2, 30, "1 maand na aansluiting"),
                    step(TEMPLATE_ID_NUWE_LID, 3, 90, "3 maande na aansluiting")
                )
            ),
            SeedTemplate(
                template = ReminderTemplateEntity(
                    templateId = "sys-SIEKTE",
                    code = "SIEKTE",
                    titleAf = "Siekte",
                    descriptionAf = "Opvolg na 'n siekte",
                    symbol = "💊 ",
                    sortOrder = 4,
                    createdAt = now,
                    updatedAt = now
                ),
                steps = listOf(
                    step("sys-SIEKTE", 1, 1, "1 dag na siekte"),
                    step("sys-SIEKTE", 2, 3, "3 dae na siekte"),
                    step("sys-SIEKTE", 3, 7, "1 week na siekte"),
                    step("sys-SIEKTE", 4, 14, "2 weke na siekte")
                )
            ),
            SeedTemplate(
                template = ReminderTemplateEntity(
                    templateId = "sys-TRAUMA",
                    code = "TRAUMA",
                    titleAf = "Trauma",
                    descriptionAf = "Opvolg na 'n traumatiese gebeurtenis (ongeluk, geweld, skok)",
                    symbol = "⚠️ ",
                    sortOrder = 5,
                    createdAt = now,
                    updatedAt = now
                ),
                steps = listOf(
                    step("sys-TRAUMA", 1, 1, "1 dag na trauma"),
                    step("sys-TRAUMA", 2, 3, "3 dae na trauma"),
                    step("sys-TRAUMA", 3, 7, "1 week na trauma"),
                    step("sys-TRAUMA", 4, 14, "2 weke na trauma"),
                    step("sys-TRAUMA", 5, 30, "1 maand na trauma")
                )
            )
        )

        private fun step(
            templateId: String,
            stepOrder: Int,
            offsetDays: Int,
            defaultTitleAf: String
        ): TemplateStepEntity {
            val code = templateId.removePrefix("sys-")
            return TemplateStepEntity(
                stepId = "sys-$code-$stepOrder",
                templateId = templateId,
                stepOrder = stepOrder,
                offsetDays = offsetDays,
                defaultTitleAf = defaultTitleAf,
                defaultNoteAf = null,
                scheduleType = ScheduleType.DATE_ONLY.name
            )
        }
    }

    internal data class SeedTemplate(
        val template: ReminderTemplateEntity,
        val steps: List<TemplateStepEntity>
    )
}