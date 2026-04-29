package za.co.jpsoft.winkerkreader.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import java.util.concurrent.ConcurrentHashMap

class WinkerkDbHelper private constructor(context: Context, dbName: String) :
        SQLiteAssetHelper(context, dbName, null, WinkerkContract.DATABASE_VERSION) {

    private val tag = "WinkerkDbHelper"

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
     * Ensure required columns exist in the database. This runs every time the database is opened.
     */
    private fun ensureColumnsExist(db: SQLiteDatabase) {
        // Check and add TAG column to Members table (only in main database)
        if (databaseName == WinkerkContract.winkerkEntry.WINKERK_DB) {
            if (!isColumnExists(db, "Members", WinkerkContract.winkerkEntry.LIDMATE_TAG)) {
                try {
                    db.execSQL("ALTER TABLE Members ADD COLUMN ${WinkerkContract.winkerkEntry.LIDMATE_TAG} BIT")
                    Log.d(tag, "Added ${WinkerkContract.winkerkEntry.LIDMATE_TAG} column to Members table")
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
            Log.e(tag, "Error checking column existence", e)
            false
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, WinkerkDbHelper>()

        /**
         * Get a singleton instance for the given database name. Uses application context to avoid
         * leaks.
         */
        @JvmStatic
        fun getInstance(context: Context, dbName: String): WinkerkDbHelper {
            return instances.getOrPut(dbName) {
                WinkerkDbHelper(context.applicationContext, dbName)
            }
        }

        /** Close a specific database instance and remove it from the map. */
        @JvmStatic
        fun closeInstance(dbName: String) {
            Log.d("WinkerkDbHelper", "closeInstance called for: $dbName")
            instances.remove(dbName)?.close()
            Log.d("WinkerkDbHelper", "Closed helper for: $dbName")
        }

        fun setDatabaseDate(context: Context) {
            val db = getInstance(context, WinkerkContract.winkerkEntry.WINKERK_DB).readableDatabase
            val settingsManager = SettingsManager.getInstance(context)
            try {
                db.rawQuery("SELECT * FROM Datum", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dateIdx = cursor.getColumnIndex("DataDatum")
                        settingsManager.dataDatum = if (dateIdx != -1) cursor.getString(dateIdx) ?: "" else ""

                    } else {
                        settingsManager.dataDatum = ""
                    }

                }
            } catch (e: Exception) {
                Log.e("WinkerkDbHelper", "Error setting database date", e)
            }
        }

        fun setChurchInfo(context: Context) {
            val db = getInstance(context, WinkerkContract.winkerkEntry.WINKERK_DB).readableDatabase
            val settingsManager = SettingsManager.getInstance(context)
            try {
                // Table name is usually GemeenteNaam based on PATH_GEMEENTE_NAAM
                db.rawQuery("SELECT DISTINCT Members._rowid_ as _id, Gemeente, [Gemeente epos] FROM Members GROUP BY Gemeente, [Gemeente epos]"
                    , null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex("Gemeente")
                        val emailIdx = cursor.getColumnIndex("Gemeente epos")
                        if (nameIdx != -1 && emailIdx != -1) {
                            settingsManager.gemeenteNaam = cursor.getString(nameIdx) ?: ""
                            settingsManager.gemeenteEpos = cursor.getString(emailIdx) ?: ""
                            if (cursor.moveToNext()) {
                                settingsManager.gemeente2Naam = cursor.getString(nameIdx) ?: ""
                                settingsManager.gemeente2Epos = cursor.getString(emailIdx) ?: ""
                                if (cursor.moveToNext()) {
                                    settingsManager.gemeente3Naam = cursor.getString(nameIdx) ?: ""
                                    settingsManager.gemeente3Epos = cursor.getString(emailIdx) ?: ""
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WinkerkDbHelper", "Error setting church info", e)
            }
        }
    }
}
