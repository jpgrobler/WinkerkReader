package za.co.jpsoft.winkerkreader.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper
import java.util.concurrent.ConcurrentHashMap

class winkerk_DB_Helper private constructor(
    context: Context,
    dbName: String
) : SQLiteAssetHelper(context, dbName, null, WinkerkContract.DATABASE_VERSION) {

    private val tag = "winkerk_DB_Helper"

    override fun onOpen(db: SQLiteDatabase) {
        Log.d(tag, "onOpen for database: $databaseName, path: ${db.path}")
        db.disableWriteAheadLogging()
        super.onOpen(db)
        ensureColumnsExist(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle upgrades if needed
    }

    override fun close() {
        super.close()
        instances.remove(databaseName)
    }

    /**
     * Ensure required columns exist in the database.
     * This runs every time the database is opened.
     */
    private fun ensureColumnsExist(db: SQLiteDatabase) {
        // Check and add TAG column to Members table (only in main database)
        if (databaseName == WinkerkContract.winkerkEntry.WINKERK_DB) {
            if (!isColumnExists(db, "Members", "TAG")) {
                try {
                    db.execSQL("ALTER TABLE Members ADD COLUMN TAG BIT")
                    Log.d(tag, "Added TAG column to Members table")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to add TAG column", e)
                }
            }

            // Check and add _id column to Datum table (only in main database)
            if (!isColumnExists(db, "Datum", "_id")) {
                try {
                    db.execSQL("ALTER TABLE Datum ADD COLUMN _id INTEGER PRIMARY KEY AUTOINCREMENT")
                    Log.d(tag, "Added _id column to Datum table")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to add _id column", e)
                }
            }
        }

        // Add similar checks for INFO database if needed in the future
    }

    /**
     * Check if a column exists in a given table.
     */
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
            Log.e(tag, "Error checking column existence", e)
            false
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, winkerk_DB_Helper>()

        /**
         * Get a singleton instance for the given database name.
         * Uses application context to avoid leaks.
         */
        @JvmStatic
        fun getInstance(context: Context, dbName: String): winkerk_DB_Helper {
            return instances.getOrPut(dbName) {
                winkerk_DB_Helper(context.applicationContext, dbName)
            }
        }

        /**
         * Close a specific database instance and remove it from the map.
         */
        @JvmStatic
        fun closeInstance(dbName: String) {
            Log.d("winkerk_DB_Helper", "closeInstance called for: $dbName")
            instances.remove(dbName)?.close()
            Log.d("winkerk_DB_Helper", "Closed helper for: $dbName")
        }
    }
}