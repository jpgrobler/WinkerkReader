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
    version = 4,
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
                .addMigrations(MIGRATION_1_4)   // Only one migration from 1 to 4
                .build()
        }

        // ─── Column lists for each table (exact match to entity definitions) ───

        private val memberColumns = listOf(
            "_id",
            "Tag",
            "Aankomsdatum",
            "Aansluitmetode",
            "Allergieë",
            "Bedieningstyl",
            "Belydenisaflegging Anniversary Period",
            "Belydenisaflegging Comment",
            "Belydenisaflegging Date",
            "Belydenisaflegging Minister",
            "Belydenisopmerking",
            "Beroep",
            "Beskikbaarheid",
            "Bewysstatus",
            "Datatoestemming",
            "Datum ontvang",
            "Doop Anniversary Period",
            "Doop Comment",
            "Doop Date",
            "Doop Minister",
            "Doopopmerking",
            "Epos",
            "Faks",
            "FamilyHeadGUID",
            "Fotostoorplek",
            "Geboortedatum",
            "Gebruikervlag",
            "Gemeente",
            "Gemeente epos",
            "Gesinshoof",
            "Gesinshoofnaam",
            "Gesinsrol",
            "Geslag",
            "Groepsindikator",
            "Huisdokter",
            "Huisdokter tel",
            "Huwelik Anniversary Period",
            "Huwelik Comment",
            "Huwelik Date",
            "Huwelik Minister",
            "Huwelikstatus",
            "Kommentaar",
            "Kroniese medikasie",
            "Landlyn",
            "Lidmaat nommer",
            "Lidmaatstatus",
            "Mediesefondsafhanklikekode",
            "Mediesefondshooflid",
            "Mediesefondsnaam",
            "Mediesefondsnommer",
            "MemberGUID",
            "Naam",
            "Noemnaam",
            "Noodkontaknommer",
            "Noodkontakpersoon",
            "Nooiensvan",
            "Ouderdom",
            "Posadres",
            "Predekantswyk",
            "Rekordstatus",
            "Selfoon",
            "ShortAddress",
            "Straatadres",
            "Stuur SMS",
            "Stuur epos",
            "Titel",
            "User 1",
            "User 2",
            "User 3",
            "User 4",
            "User 5",
            "User 6",
            "Van",
            "Verjaardag",
            "Voorletters",
            "Vorige gemeente",
            "Werk tel",
            "Werkgewer",
            "Wyk"
        )

        private val argiefColumns = listOf(
            "_id",
            "Tag",
            "ArchiveGUID",
            "Surname",
            "Name",
            "MaidenName",
            "MemberStatus",
            "CertificateStatus",
            "PreviousCongregation",
            "DateReceived",
            "Comment",
            "Reason",
            "ResignationDetail",
            "DepartureTo",
            "DepartureDate",
            "DocCode",
            "Document",
            "OldAddress",
            "NewAddress",
            "DateOfBirth",
            "Gender",
            "MaritalStatus",
            "BaptismDate",
            "BaptismMinister",
            "Father",
            "Mother",
            "ConfessionDate",
            "ConfessionMinister",
            "ConfessionRemark",
            "AcceptanceDate",
            "ArchiveDate",
            "ResignationRemark",
            "User",
            "Gemeente",
            "Gemeente epos"
        )

        private val datumColumns = listOf(
            "_id",
            "DataDatum"
        )

        // ─── Migration 1 → 4 (single step, corrects schema and removes NOT NULL where needed) ───

        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop any existing indexes (they will be recreated by Room later if needed)
                dropAllIndexes(db, "Members")
                dropAllIndexes(db, "Argief")
                dropAllIndexes(db, "Datum")

                // Recreate each table with the exact column list expected by the entities
                recreateTable(db, "Members", memberColumns, withNotNullId = true)
                recreateTable(db, "Argief", argiefColumns, withNotNullId = false)
                recreateTable(db, "Datum", datumColumns, withNotNullId = false)
            }
        }

        // ─── Helper functions ──────────────────────────────────────────────────────

        private fun dropAllIndexes(db: SupportSQLiteDatabase, tableName: String) {
            db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='$tableName'")
                .use { cursor ->
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
            expectedColumns: List<String>,
            withNotNullId: Boolean = true
        ) {
            // Get existing columns from the old table
            val existingColumns = mutableListOf<String>()
            db.query("PRAGMA table_info('$tableName')").use { cursor ->
                while (cursor.moveToNext()) {
                    existingColumns.add(cursor.getString(cursor.getColumnIndex("name")))
                }
            }

            // Build CREATE TABLE with only expected columns
            val newTable = "${tableName}_new"
            val columnDefs = expectedColumns.joinToString(", ") { name ->
                if (name == "_id") {
                    if (withNotNullId) "[_id] INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"
                    else "[_id] INTEGER PRIMARY KEY AUTOINCREMENT"
                } else {
                    "[$name] TEXT"
                }
            }
            db.execSQL("CREATE TABLE $newTable ($columnDefs)")

            // Copy data only for columns that exist in both old and new
            val commonColumns = existingColumns.intersect(expectedColumns.toSet())
            if (commonColumns.isNotEmpty()) {
                val cols = commonColumns.joinToString(", ") { "[$it]" }
                db.execSQL("INSERT INTO $newTable ($cols) SELECT $cols FROM $tableName")
            }

            // Replace old table
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