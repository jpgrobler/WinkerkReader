package za.co.jpsoft.winkerkreader.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import za.co.jpsoft.winkerkreader.data.WinkerkContract

@Database(
    entities = [MemberEntity::class, ArgiefEntity::class, DatumEntity::class],
    version = 4,   // Bumped to 4 to run the new correction migration
    exportSchema = false
)
abstract class WinkerkDatabase : RoomDatabase() {

    abstract fun memberDao(): MemberDao
    abstract fun argiefDao(): ArgiefDao
    abstract fun datumDao(): DatumDao

    companion object {
        @Volatile
        private var instance: WinkerkDatabase? = null

        fun getInstance(context: Context): WinkerkDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): WinkerkDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                WinkerkDatabase::class.java,
                WinkerkContract.winkerkEntry.WINKERK_DB
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }

        // ===== Migration 1 → 2 (unchanged) =====
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropAllIndexes(db, "Members")
                dropAllIndexes(db, "Argief")
                dropAllIndexes(db, "Datum")

                recreateTable(db, "Members", withNotNull = true)
                recreateTable(db, "Argief", withNotNull = false)
                recreateTable(db, "Datum", withNotNull = false)
            }
        }

        // ===== Migration 2 → 3 (added NOT NULL to all, but incorrectly for Argief/Datum) =====
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropAllIndexes(db, "Members")
                dropAllIndexes(db, "Argief")
                dropAllIndexes(db, "Datum")

                // This was the mistake: added NOT NULL to Argief and Datum too.
                // We'll correct it in MIGRATION_3_4.
                recreateTable(db, "Members", withNotNull = true)
                recreateTable(db, "Argief", withNotNull = true)   // wrong
                recreateTable(db, "Datum", withNotNull = true)    // wrong
            }
        }

        // ===== Migration 3 → 4: correct Argief and Datum to not have NOT NULL =====
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropAllIndexes(db, "Members")
                dropAllIndexes(db, "Argief")
                dropAllIndexes(db, "Datum")

                // Recreate Members with NOT NULL (as before)
                recreateTable(db, "Members", withNotNull = true)
                // Recreate Argief and Datum WITHOUT NOT NULL
                recreateTable(db, "Argief", withNotNull = false)
                recreateTable(db, "Datum", withNotNull = false)
            }
        }

        // -------------------------------------------------------------------------
        // Helper functions
        // -------------------------------------------------------------------------

        private fun dropAllIndexes(db: SupportSQLiteDatabase, tableName: String) {
            db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='$tableName'").use { cursor ->
                while (cursor.moveToNext()) {
                    val indexName = cursor.getString(0)
                    if (indexName != null && !indexName.startsWith("sqlite_")) {
                        db.execSQL("DROP INDEX IF EXISTS $indexName")
                    }
                }
            }
        }

        private fun recreateTable(
            db: SupportSQLiteDatabase,
            tableName: String,
            withNotNull: Boolean
        ) {
            // Get current column names
            val columns = mutableListOf<String>()
            db.query("PRAGMA table_info('$tableName')").use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndex("name"))
                    columns.add(name)
                }
            }

            val newTable = "${tableName}_new"
            val columnDefs = columns.joinToString(", ") { name ->
                if (name == "_id") {
                    if (withNotNull) {
                        "[_id] INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"
                    } else {
                        "[_id] INTEGER PRIMARY KEY AUTOINCREMENT"
                    }
                } else {
                    "[$name] TEXT"
                }
            }
            db.execSQL("CREATE TABLE $newTable ($columnDefs)")

            // Copy data (include all columns)
            val cols = columns.joinToString(", ") { "[$it]" }
            db.execSQL("INSERT INTO $newTable ($cols) SELECT $cols FROM $tableName")

            // Drop old and rename
            db.execSQL("DROP TABLE $tableName")
            db.execSQL("ALTER TABLE $newTable RENAME TO $tableName")
        }

        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}