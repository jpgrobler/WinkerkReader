package za.co.jpsoft.winkerkreader.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase
import za.co.jpsoft.winkerkreader.utils.prefs.SyncPrefs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseInitializer @Inject constructor(
    private val syncPrefs: SyncPrefs
) {

    private val TAG = "DatabaseInitializer"
    private val CURRENT_SCHEMA_VERSION = 1   // bump this when schema changes

    interface ProgressListener {
        fun onProgressUpdate(progress: Int)
        fun onInitializationComplete(success: Boolean)
    }

    fun initializeDatabase(context: Context, listener: ProgressListener? = null) {
        // ── If already fully initialized, just check if migration is needed ──
        if (syncPrefs.isDatabaseInitialized) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Database already initialized")

            // Only run schema repair if we haven't already applied it
            if (syncPrefs.databaseSchemaVersion < CURRENT_SCHEMA_VERSION) {
                // Idempotent – repairs only if needed, does nothing otherwise
                migrateIfNeeded(context)
                // Mark that we've handled this schema version
                syncPrefs.databaseSchemaVersion = CURRENT_SCHEMA_VERSION
            }

            listener?.onInitializationComplete(true)
            return
        }

        // ── First launch – initialize Room and run repair ──
        if (BuildConfig.DEBUG) Log.d(TAG, "First launch - initializing database via Room")

        try {
            // 1. Migrate if needed (this also handles the case where the existing DB has old schema)
            migrateIfNeeded(context)

            // 2. Open Room to create tables if they don't exist
            val db = WinkerkDatabase.getInstance(context)
            db.openHelper.writableDatabase

            // 3. Mark as fully initialized
            syncPrefs.isDatabaseInitialized = true
            syncPrefs.databaseSchemaVersion = CURRENT_SCHEMA_VERSION

            if (BuildConfig.DEBUG) Log.d(TAG, "Database initialized successfully")
            listener?.onInitializationComplete(true)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Critical error during database initialization", e)
            listener?.onInitializationComplete(false)
        }
    }

    /**
     * Opens the .db file directly with plain SQLiteDatabase (bypassing Room's
     * schema validator) and checks whether any column in Members is still
     * VARCHAR. If so, recreates all three tables with TEXT columns.
     *
     * This method is idempotent – it checks whether repair is needed before doing anything.
     * Returns true if a repair was performed, false if no repair was needed.
     */
    private fun migrateIfNeeded(context: Context): Boolean {
        val dbFile = context.getDatabasePath(WinkerkContract.winkerkEntry.WINKERK_DB)
        if (!dbFile.exists()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Database file does not exist yet – skipping repair")
            return false
        }

        return try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                if (!needsRepair(db)) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Schema OK — no repair needed")
                    return false
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Schema repair needed — migrating")
                listOf("Members", "Argief", "Datum").forEach { migrateTable(db, it) }
                // Set Room version to 1 so it does not run any migration
                db.execSQL("PRAGMA user_version = 1")
                if (BuildConfig.DEBUG) Log.d(TAG, "Migration complete, user_version set to 1")
                true
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "migrateIfNeeded failed", e)
            false
        }
    }

    /**
     * Returns true if the Members table has a VARCHAR column OR is missing _id.
     */
    private fun needsRepair(db: SQLiteDatabase): Boolean {
        var hasVarchar = false
        var hasId = false
        db.rawQuery("PRAGMA table_info('Members')", null).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                if (name == "_id") hasId = true
                if (type.uppercase().startsWith("VARCHAR")) hasVarchar = true
            }
        }
        return hasVarchar || !hasId
    }

    /**
     * Recreates [tableName] with TEXT columns for all non‑_id columns, and ensures
     * _id exists as INTEGER PRIMARY KEY AUTOINCREMENT. For [tableName] == "Members",
     * _id is created with NOT NULL to satisfy Room's @PrimaryKey(autoGenerate = true)
     * on a non‑nullable field. For Argief and Datum, _id is nullable (as per their entities).
     *
     * If the old table does NOT have an _id column, it is omitted from the INSERT,
     * allowing the new AUTOINCREMENT to generate _id values automatically.
     */
    private fun migrateTable(db: SQLiteDatabase, tableName: String) {
        // Check if table exists
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { if (!it.moveToFirst()) return }

        // Read existing columns from the old table
        val oldColumns = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info('$tableName')", null).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                oldColumns.add(name)
            }
        }

        if (oldColumns.isEmpty()) return

        val withNotNull = tableName == "Members"

        // Build new table definition: _id first, then all other columns as TEXT
        val newColumnDefs = mutableListOf<String>()
        newColumnDefs.add(
            if (withNotNull) "[_id] INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"
            else "[_id] INTEGER PRIMARY KEY AUTOINCREMENT"
        )
        val otherColumns = oldColumns.filter { it != "_id" }
        otherColumns.forEach { colName ->
            newColumnDefs.add("[$colName] TEXT")
        }

        val temp = "${tableName}_upgrade_new"
        val createTableColumns = newColumnDefs.joinToString(", ")
        db.execSQL("DROP TABLE IF EXISTS $temp")
        db.execSQL("CREATE TABLE $temp ($createTableColumns)")

        // Prepare INSERT – handle presence/absence of _id
        val columnsToInsert = if (oldColumns.contains("_id")) {
            // Copy _id as well
            oldColumns
        } else {
            // _id does NOT exist – we will not include it in the INSERT,
            // so the new table auto‑generates _id values.
            otherColumns
        }

        val insertColumns = columnsToInsert.joinToString(", ") { "[$it]" }
        val selectColumns = columnsToInsert.joinToString(", ") { "[$it]" }

        val insertSql = "INSERT INTO $temp ($insertColumns) SELECT $selectColumns FROM $tableName"
        db.execSQL(insertSql)

        db.execSQL("DROP TABLE $tableName")
        db.execSQL("ALTER TABLE $temp RENAME TO $tableName")
    }
}