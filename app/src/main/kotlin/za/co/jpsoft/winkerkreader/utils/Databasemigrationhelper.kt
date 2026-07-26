package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase
import za.co.jpsoft.winkerkreader.utils.DatabaseMigrationHelper.forceMigrate
import za.co.jpsoft.winkerkreader.utils.DatabaseMigrationHelper.migrate
import java.io.File

/**
 * Handles SQLite schema migration and Room verification for an incoming
 * Winkerk database file before it replaces the live copy.
 *
 * Extracted from LaaiDatabasisActivity. All methods are pure IO — no UI
 * interactions, no binding references, no Activity dependencies.
 *
 * Typical call sequence (from DatabaseImportController.processTempFile):
 *
 *   val ok = DatabaseMigrationHelper.migrateAndVerify(context, tempFile)
 *   if (!ok) { onError("..."); return false }
 *   // replace active DB file
 */
object DatabaseMigrationHelper {

    private const val TAG = "DatabaseMigrationHelper"

    // ── Orchestration ─────────────────────────────────────────────────────────

    /**
     * Runs [migrate] (or [forceMigrate] if normal migration fails) and then
     * verifies the result with Room. All work runs on [Dispatchers.IO].
     *
     * @return true when the file is safe to swap into the databases directory.
     */
    suspend fun migrateAndVerify(context: Context, dbFile: File): Boolean {
        if (!dbFile.exists()) return false

        val migrated = withContext(Dispatchers.IO) {
            var ok = migrate(dbFile)
            if (!ok) {
                if (BuildConfig.DEBUG) Log.w(
                    TAG, "Normal migration failed, attempting forced migration"
                )
                ok = forceMigrate(dbFile)
            }
            ok
        }
        if (!migrated) {
            if (BuildConfig.DEBUG) Log.e(TAG, "All migration attempts failed")
            return false
        }

        return withContext(Dispatchers.IO) {
            verifyWithRoom(context, dbFile)
        }
    }

    // ── Migration ─────────────────────────────────────────────────────────────

    /**
     * Normal migration: applies VARCHAR→TEXT conversion only when the
     * database version is below 4. Stamps the result as version 4.
     *
     * Was [LaaiDatabasisActivity.migrateDownloadedDatabase].
     */
    fun migrate(dbFile: File): Boolean {
        if (!dbFile.exists()) {
            if (BuildConfig.DEBUG) Log.e(TAG, "DB file does not exist: ${dbFile.absolutePath}")
            return false
        }
        return try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                if (db.version >= 4) {
                    if (BuildConfig.DEBUG) Log.i(
                        TAG, "DB already at v${db.version}, skipping migration"
                    )
                    return@use
                }
                listOf("Members", "Argief", "Datum").forEach { table ->
                    migrateTableVarcharToText(db, table)
                }
                db.execSQL("PRAGMA user_version = 4")
                if (BuildConfig.DEBUG) Log.i(TAG, "Migration successful on ${dbFile.name}")
            }
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Migration failed on ${dbFile.name}", e)
            false
        }
    }

    /**
     * Force migration: applies VARCHAR→TEXT conversion regardless of the
     * current version and stamps version 4. Used as a fallback when
     * [migrate] cannot open the file (e.g. corrupt version field).
     *
     * Was [LaaiDatabasisActivity.forceMigrateDatabase].
     */
    fun forceMigrate(dbFile: File): Boolean {
        return try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                listOf("Members", "Argief", "Datum").forEach { table ->
                    migrateTableVarcharToText(db, table)
                }
                db.execSQL("PRAGMA user_version = 4")
                if (BuildConfig.DEBUG) Log.i(
                    TAG, "Forced migration successful on ${dbFile.name}"
                )
            }
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Forced migration failed on ${dbFile.name}", e)
            false
        }
    }

    // ── Room verification ─────────────────────────────────────────────────────

    /**
     * Opens [dbFile] through a temporary Room instance and runs a lightweight
     * member count query to confirm Room accepts the schema.
     *
     * IMPORTANT: only manages the local temp instance ("Winkerk.db.new").
     * Never calls [WinkerkDatabase.closeInstance] — the global singleton
     * is managed by the caller ([DatabaseImportController.processTempFile]).
     *
     * Must be called on a background thread (IO dispatcher).
     *
     * Was [LaaiDatabasisActivity.verifyRoomDatabaseOnFile].
     */
    fun verifyWithRoom(context: Context, dbFile: File): Boolean {
        var localDb: WinkerkDatabase? = null
        return try {
            localDb = Room.databaseBuilder(
                context.applicationContext,
                WinkerkDatabase::class.java,
                dbFile.name   // "Winkerk.db.new" — separate from global singleton
            ).build()
            val count = localDb.memberDao().getCount()
            if (BuildConfig.DEBUG) Log.d(
                TAG, "Room verification passed on ${dbFile.name}, count=$count"
            )
            true
        } catch (e: IllegalStateException) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Room schema mismatch on ${dbFile.name}", e)
            false
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Room verification failed on ${dbFile.name}", e)
            false
        } finally {
            try {
                localDb?.close()
            } catch (_: Exception) {
            }
        }
    }

    // ── Schema helper ─────────────────────────────────────────────────────────

    /**
     * Recreates [tableName] with all columns typed as TEXT (dropping VARCHAR),
     * preserving all existing data. The primary key column `_id` retains its
     * INTEGER PRIMARY KEY AUTOINCREMENT type.
     *
     * Uses the standard SQLite rename trick:
     *   CREATE TABLE _new → INSERT SELECT → DROP old → RENAME _new.
     *
     * Was [LaaiDatabasisActivity.migrateTableVarcharToText].
     */
    fun migrateTableVarcharToText(db: SQLiteDatabase, tableName: String) {
        // Bail early if the table doesn't exist
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { if (!it.moveToFirst()) return }

        val oldColumns = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info('$tableName')", null).use { cursor ->
            while (cursor.moveToNext()) {
                oldColumns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        if (oldColumns.isEmpty()) return

        val withNotNull = tableName == "Members"
        val newColumnDefs = buildList {
            add(
                if (withNotNull) "[_id] INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"
                else "[_id] INTEGER PRIMARY KEY AUTOINCREMENT"
            )
            oldColumns.filter { it != "_id" }.forEach { add("[$it] TEXT") }
        }

        val temp = "${tableName}_upgrade_new"
        db.execSQL("DROP TABLE IF EXISTS $temp")
        db.execSQL("CREATE TABLE $temp (${newColumnDefs.joinToString(", ")})")

        val columnsToInsert = if (oldColumns.contains("_id")) oldColumns
        else oldColumns.filter { it != "_id" }
        val cols = columnsToInsert.joinToString(", ") { "[$it]" }
        db.execSQL("INSERT INTO $temp ($cols) SELECT $cols FROM $tableName")

        db.execSQL("DROP TABLE $tableName")
        db.execSQL("ALTER TABLE $temp RENAME TO $tableName")
    }
}