package za.co.jpsoft.winkerkreader.data.pastoral

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.pastoral.dao.FollowUpReminderDao
import za.co.jpsoft.winkerkreader.data.pastoral.dao.PastoralMetaDao
import za.co.jpsoft.winkerkreader.data.pastoral.dao.PastoralNoteDao
import za.co.jpsoft.winkerkreader.data.pastoral.dao.ReminderTemplateDao
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.PastoralMetaEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.PastoralNoteEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.ReminderTemplateEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity

@Database(
    entities = [
        FollowUpReminderEntity::class,
        ReminderTemplateEntity::class,
        TemplateStepEntity::class,
        PastoralMetaEntity::class,
        PastoralNoteEntity::class,
    ],
    // Must always match PastoralDatabaseBackup.CURRENT_PASTORAL_SCHEMA_VERSION.
    // Update both together when adding a new migration.
    version = 6,
    exportSchema = true
)
abstract class PastoralDatabase : RoomDatabase() {

    abstract fun followUpReminderDao(): FollowUpReminderDao
    abstract fun reminderTemplateDao(): ReminderTemplateDao
    abstract fun pastoralMetaDao(): PastoralMetaDao
    abstract fun pastoralNoteDao(): PastoralNoteDao

    companion object {
        private const val TAG = "PastoralDatabase"

        @Volatile
        private var instance: PastoralDatabase? = null

        /**
         * Returns the singleton instance. Does NOT seed — seeding is an explicit,
         * structured step (see [PastoralDatabaseInitializer.seedIfEmpty]), called
         * once from the app's startup flow, so callers can rely on ordering
         * relative to other database work instead of racing a hidden background task.
         */
        fun getInstance(context: Context): PastoralDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }

        fun closeInstance() {
            synchronized(this) {
                instance?.let { db ->
                    if (db.isOpen) db.close()
                }
                instance = null
                if (BuildConfig.DEBUG) Log.d(TAG, "Pastoral database instance closed")
            }
        }

        // ── Migrations (oldest first) ──────────────────────────────────────────

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE follow_up_reminders ADD COLUMN contextJson TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE follow_up_reminders ADD COLUMN googleTaskId TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE follow_up_reminders ADD COLUMN googleTaskSynced INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reminder_templates ADD COLUMN symbol TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE follow_up_reminders ADD COLUMN symbol TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE follow_up_reminders ADD COLUMN memberSurname TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE follow_up_reminders ADD COLUMN memberGivenName TEXT DEFAULT NULL")
            }
        }

        /**
         * v5 → v6: Adds the pastoral_notes table introduced with the Bedieningsnotas feature.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pastoral_notes (
                        noteId                  TEXT NOT NULL PRIMARY KEY,
                        memberGuid              TEXT NOT NULL,
                        familyHeadGuid          TEXT,
                        memberSurname           TEXT,
                        memberGivenName         TEXT,
                        memberDisplayNameCache  TEXT,
                        noteDateUtc             INTEGER NOT NULL,
                        category                TEXT NOT NULL,
                        noteText                TEXT NOT NULL,
                        isConfidential          INTEGER NOT NULL DEFAULT 0,
                        linkedReminderId        TEXT,
                        createdAt               INTEGER NOT NULL,
                        updatedAt               INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pastoral_notes_memberGuid ON pastoral_notes(memberGuid)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pastoral_notes_noteDateUtc ON pastoral_notes(noteDateUtc)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pastoral_notes_category ON pastoral_notes(category)"
                )
            }
        }

        private fun buildDatabase(context: Context): PastoralDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PastoralDatabase::class.java,
                winkerkEntry.PASTORAL_DB
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6
                )
                .build()
        }
    }
}