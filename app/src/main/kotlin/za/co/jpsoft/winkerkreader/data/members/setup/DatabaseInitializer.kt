package za.co.jpsoft.winkerkreader.data.members.setup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import jakarta.inject.Inject
import jakarta.inject.Singleton
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract
import za.co.jpsoft.winkerkreader.data.members.repository.ChurchInfoRepository
import za.co.jpsoft.winkerkreader.utils.prefs.SyncPrefs
import za.co.jpsoft.winkerkreader.utils.work.WorkScheduler
import java.io.File
import java.io.FileOutputStream

@Singleton
class DatabaseInitializer @Inject constructor(
    private val syncPrefs: SyncPrefs,
    private val workScheduler: WorkScheduler,
    private val churchInfoRepository: ChurchInfoRepository
) {

    private val TAG = "DatabaseInitializer"
    private val CURRENT_SCHEMA_VERSION = 1   // bump this when schema changes
    private val ASSET_NAME = "databases/WinkerkReader.sqlite"

    interface ProgressListener {
        fun onProgressUpdate(progress: Int)
        fun onInitializationComplete(success: Boolean)
    }

    fun initializeDatabase(context: Context, listener: ProgressListener? = null) {
        if (syncPrefs.isDatabaseInitialized) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Database already initialized")
            if (syncPrefs.databaseSchemaVersion < CURRENT_SCHEMA_VERSION) {
                migrateIfNeeded(context)
                syncPrefs.databaseSchemaVersion = CURRENT_SCHEMA_VERSION
            }
            listener?.onInitializationComplete(true)
            return
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "First launch - initializing database via Room")

        try {
            // 1. ★ Copy the asset **even if the file exists**, but only if it's empty.
            //    This handles the case where the ContentProvider already created an empty DB.
            val didSeedFromAsset = seedFromAssetIfNeeded(context, force = true)

            // 2. Migrate (VARCHAR → TEXT, set user_version = 4)
            migrateIfNeeded(context)

            // 3. Open Room to validate schema
            val db = WinkerkDatabase.getInstance(context)
            db.openHelper.writableDatabase

            // 👈 4. CRITICAL FIX: Load the church info (gemeentes) immediately after seeding!
            // Since initializeDatabase runs on a background thread (IO), we can run this blocking or via runBlocking if needed,
            // or let ChurchInfoRepository execute its internal withContext(Dispatchers.IO).
            // Since loadChurchInfo is a suspend function, we can call it if we wrap it, or make a blocking version,
            // or invoke it via runBlocking:
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                churchInfoRepository.loadChurchInfo()
            }

            // 5. Mark as fully initialized
            syncPrefs.isDatabaseInitialized = true
            syncPrefs.databaseSchemaVersion = CURRENT_SCHEMA_VERSION

            // 6. Force the ContentProvider to reload the database
            context.contentResolver.call(
                WinkerkContract.winkerkEntry.CONTENT_URI,
                "reloadDatabase",
                null,
                null
            )

            // ★ Pastoral setup — only when we actually just loaded the demo member DB.
            // Both steps run in ONE runBlocking, in explicit order, since
            // PastoralDatabase.getInstance() no longer seeds itself implicitly:
            // templates must exist before the demo seeder can reference them.
            if (didSeedFromAsset) {
                workScheduler.schedulePastoralDemoSeed()
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "Database initialized successfully")
            listener?.onInitializationComplete(true)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Critical error during database initialization", e)
            listener?.onInitializationComplete(false)
        }
    }

    /**
     * Copies the asset to the database directory.
     * @param force If true, overwrites the file if it exists but is empty (Members count == 0).
     */
    private fun seedFromAssetIfNeeded(context: Context, force: Boolean = false): Boolean {
        val dbFile = context.getDatabasePath(WinkerkContract.winkerkEntry.WINKERK_DB)
        if (dbFile.exists()) {
            if (force) {
                if (isDatabaseEmpty(dbFile)) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "Existing DB is empty – overwriting with asset"
                    )
                    context.contentResolver.call(
                        WinkerkContract.winkerkEntry.CONTENT_URI,
                        "closeDatabase",
                        null,
                        null
                    )
                    dbFile.delete()
                    //Thread.sleep(50)
                } else {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Existing DB has data – keeping it")
                    return false
                }
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "DB file already exists – skipping asset seed")
                return false
            }
        }

        return try {
            context.assets.open(ASSET_NAME).use { input ->
                dbFile.parentFile?.mkdirs()
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Asset '$ASSET_NAME' copied to ${dbFile.absolutePath}"
            )
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(
                TAG,
                "Asset seed skipped (no asset or copy failed): ${e.message}"
            )
            false
        }
    }

    private fun isDatabaseEmpty(dbFile: File): Boolean {
        return try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM Members", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) == 0 else true
                }
            }
        } catch (e: Exception) {
            true
        }
    }

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
                db.execSQL("PRAGMA user_version = 4")
                if (BuildConfig.DEBUG) Log.d(TAG, "Migration complete, user_version set to 4")
                true
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "migrateIfNeeded failed", e)
            false
        }
    }

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

    private fun migrateTable(db: SQLiteDatabase, tableName: String) {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { if (!it.moveToFirst()) return }

        val oldColumns = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info('$tableName')", null).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                oldColumns.add(name)
            }
        }

        if (oldColumns.isEmpty()) return

        val withNotNull = tableName == "Members"

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

        val columnsToInsert = if (oldColumns.contains("_id")) {
            oldColumns
        } else {
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