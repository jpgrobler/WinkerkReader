package za.co.jpsoft.winkerkreader.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import java.util.concurrent.ConcurrentHashMap

class WinkerkDbHelper private constructor(context: Context, dbName: String) :
    SQLiteAssetHelper(context, dbName, null, WinkerkContract.DATABASE_VERSION) {

    private val tag = "WinkerkDbHelper"
    private var isOpen = false

    init {
        // Don't force WAL off during initialization - this causes locks
        // Let SQLite handle the journal mode
        setForcedUpgrade()
    }

    // REMOVED onCreate() - it's final in SQLiteAssetHelper and can't be overridden

    override fun onOpen(db: SQLiteDatabase) {
        if (BuildConfig.DEBUG) Log.d(tag, "onOpen for database: $databaseName, path: ${db.path}")

        // Don't disable WAL here - it causes locks during migration
        // Let SQLite manage its own journal mode
        super.onOpen(db)

        try {
            // Enable foreign keys for better data integrity
            db.execSQL("PRAGMA foreign_keys=ON;")

            // Check if we need to run migrations
            ensureColumnsExist(db)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(tag, "Error in onOpen", e)
        }

        isOpen = true
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (BuildConfig.DEBUG) Log.d(tag, "onUpgrade from $oldVersion to $newVersion")
        // Handle upgrades if needed
    }

    override fun close() {
        if (BuildConfig.DEBUG) Log.d(tag, "Closing database: $databaseName")
        isOpen = false
        super.close()
        instances.remove(databaseName)
    }

    /**
     * Ensure required columns exist in the database. This runs every time the database is opened.
     */
    private fun ensureColumnsExist(db: SQLiteDatabase) {
        // Only run for main database
        if (databaseName != WinkerkContract.winkerkEntry.WINKERK_DB) return

        try {
            // Check and add TAG column to Members table
            if (!isColumnExists(db, "Members", WinkerkContract.winkerkEntry.LIDMATE_TAG)) {
                try {
                    db.execSQL("ALTER TABLE Members ADD COLUMN ${WinkerkContract.winkerkEntry.LIDMATE_TAG} BIT")
                    if (BuildConfig.DEBUG) Log.d(
                        tag,
                        "Added ${WinkerkContract.winkerkEntry.LIDMATE_TAG} column"
                    )
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(tag, "Failed to add TAG column", e)
                }
            }

            // Check and add _id column to Datum table
            if (!isColumnExists(db, "Datum", "_id")) {
                try {
                    db.execSQL("ALTER TABLE Datum ADD COLUMN _id INTEGER PRIMARY KEY AUTOINCREMENT")
                    if (BuildConfig.DEBUG) Log.d(tag, "Added _id column to Datum table")
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(tag, "Failed to add _id column", e)
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(tag, "Error ensuring columns exist", e)
        }
    }

    /** Check if a column exists in a given table. */
    private fun isColumnExists(db: SQLiteDatabase, tableName: String, columnName: String): Boolean {
        return try {
            db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    if (columnName.equals(name, ignoreCase = true)) {
                        return@use true
                    }
                }
                false
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(tag, "Error checking column existence", e)
            false
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, WinkerkDbHelper>()

        /**
         * Get a singleton instance for the given database name.
         * Uses application context to avoid leaks.
         */
        @JvmStatic
        fun getInstance(context: Context, dbName: String): WinkerkDbHelper {
            // Quick path: check existing instance without locking
            instances[dbName]?.let { return it }

            // Synchronize creation to avoid race conditions
            return synchronized(instances) {
                // Double-check after acquiring lock
                instances[dbName] ?: WinkerkDbHelper(context.applicationContext, dbName).also {
                    instances[dbName] = it
                    if (BuildConfig.DEBUG) Log.d(
                        "WinkerkDbHelper",
                        "Created new instance for: $dbName"
                    )
                }
            }
        }

        /** Close a specific database instance and remove it from the map. */
        @JvmStatic
        fun closeInstance(dbName: String) {
            synchronized(instances) {
                instances.remove(dbName)?.close()
            }
            if (BuildConfig.DEBUG) Log.d("WinkerkDbHelper", "Closed helper for: $dbName")
        }

        /** Close all database instances. */
        @JvmStatic
        fun closeAllInstances() {
            synchronized(instances) {
                instances.values.forEach { it.close() }
                instances.clear()
            }
            if (BuildConfig.DEBUG) Log.d("WinkerkDbHelper", "Closed all database instances")
        }

        fun setDatabaseDate(context: Context) {
            try {
                val db =
                    getInstance(context, WinkerkContract.winkerkEntry.WINKERK_DB).readableDatabase
                val settingsManager = SettingsManager.getInstance(context)
                db.rawQuery("SELECT * FROM Datum", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dateIdx = cursor.getColumnIndex("DataDatum")
                        settingsManager.dataDatum =
                            if (dateIdx != -1) cursor.getString(dateIdx) ?: "" else ""
                    } else {
                        settingsManager.dataDatum = ""
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("WinkerkDbHelper", "Error setting database date", e)
            }
        }
    }
}