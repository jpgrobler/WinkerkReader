package za.co.jpsoft.winkerkreader.data

import android.content.*
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import android.util.Log
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_ADRES
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_ARGIEF
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_FOON
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_FOTO
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_FOTO_UPDATER
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_GEMEENTE_NAAM
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_GESIN
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_GROEPE
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_GROEPE_LYS
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_LIDMATE
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_MEELEWING
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_MYLPALE
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_WKR_GROEPE
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PATH_WKR_GROEPLEDE
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_DB
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WINKERK_DB

class winkerkProvider : ContentProvider() {

    private val tag = "winkerkProvider"
    private var mDbHelper: winkerk_DB_Helper? = null
    private var mInfoDbHelper: winkerk_DB_Helper? = null

    // UriMatcher codes
    private companion object {
        private const val LIDMAAT_LIST = 100
        private const val LIDMAAT_GUID = 101
        private const val GESIN_GUID = 104
        private const val OPROEP = 105
        private const val MYLPALE_GUID = 106
        private const val GEMEENTE_NAAM = 107
        private const val LIDMAAT_OUDERDOM = 108
        private const val LIDMAAT_ID = 109
        private const val ADRES = 110
        private const val FOTO = 111
        private const val FOTO_UPDATER = 112
        private const val GROEPE_GUID = 113
        private const val GROEPE_LYS = 114
        private const val WKR_GROEPE_LYS = 115
        private const val WKR_GROEPE_ID = 116
        private const val WKR_NGROEP = 117
        private const val WKR_GROEPLEDE = 118
        private const val MEELEWING_LIDMAAT = 119
        private const val ARGIEF_LAAI = 120

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_LIDMATE, LIDMAAT_LIST)
            addURI(WinkerkContract.CONTENT_AUTHORITY, "$PATH_LIDMATE/#", LIDMAAT_GUID)
            addURI(WinkerkContract.CONTENT_AUTHORITY, "$PATH_GESIN/#", GESIN_GUID)
            addURI(WinkerkContract.CONTENT_AUTHORITY, "$PATH_FOON/#", OPROEP)
            addURI(WinkerkContract.CONTENT_AUTHORITY, "$PATH_MYLPALE/#", MYLPALE_GUID)
            addURI(WinkerkContract.CONTENT_AUTHORITY, "$PATH_MEELEWING/#", MEELEWING_LIDMAAT)
            addURI(WinkerkContract.CONTENT_AUTHORITY, "$PATH_GROEPE/#", GROEPE_GUID)
            addURI(WinkerkContract.CONTENT_AUTHORITY, PATH_GROEPE_LYS, GROEPE_LYS)
            addURI(WinkerkContract.CONTENT_AUTHORITY, PATH_GEMEENTE_NAAM, GEMEENTE_NAAM)
            addURI(WinkerkContract.CONTENT_AUTHORITY, "$PATH_ADRES/#", ADRES)
            addURI(WinkerkContract.CONTENT_AUTHORITY, "$PATH_FOTO/#", FOTO)
            addURI(WinkerkContract.CONTENT_AUTHORITY, PATH_FOTO, FOTO)
            addURI(WinkerkContract.CONTENT_AUTHORITY, PATH_FOTO_UPDATER, FOTO_UPDATER)
            addURI(WinkerkContract.CONTENT_AUTHORITY, "$PATH_WKR_GROEPE/#", WKR_GROEPE_ID)
            addURI(WinkerkContract.CONTENT_AUTHORITY, PATH_WKR_GROEPE, WKR_GROEPE_LYS)
            addURI(WinkerkContract.CONTENT_AUTHORITY, PATH_WKR_GROEPLEDE, WKR_GROEPLEDE)
            addURI(WinkerkContract.CONTENT_AUTHORITY, PATH_ARGIEF, ARGIEF_LAAI)
        }
    }

    override fun onCreate(): Boolean {
        val context = context ?: return false
        mDbHelper = winkerk_DB_Helper.getInstance(context, WINKERK_DB)
        mInfoDbHelper = winkerk_DB_Helper.getInstance(context, INFO_DB)
        Log.v(tag, "onCreate")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        var cursor: Cursor? = null
        val match = uriMatcher.match(uri)
        var count = 0

        when (match) {
            GEMEENTE_NAAM -> {
                val db = mDbHelper?.readableDatabase ?: return null
                Log.v(tag, "GEMEENTE_NAAM ${db.isOpen}")
                try {
                    cursor = db.rawQuery(selection ?: "", selectionArgs)
                    count = cursor.count
                } catch (_: Exception) {
                    Log.d(tag, "Gemeente doesn't exist :(((")
                } finally {
                    cursor?.close()
                    cursor = null
                }
            }

            LIDMAAT_LIST, LIDMAAT_GUID, LIDMAAT_OUDERDOM, OPROEP, MYLPALE_GUID,
            GESIN_GUID, GROEPE_GUID, GROEPE_LYS, MEELEWING_LIDMAAT, ARGIEF_LAAI -> {
                val db = mDbHelper?.readableDatabase ?: return null
                Log.v(tag, "Cursor query ${db.isOpen}")

                val finalSelection = when (match) {
                    LIDMAAT_GUID -> {
                        if (winkerkEntry.SORTORDER == "VAN") {
                            "$selection ORDER BY ${winkerkEntry.LIDMATE_VAN} ASC, ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_NOEMNAAM} ASC ;"
                        } else if (winkerkEntry.SORTORDER == "WYK") {
                            "$selection ORDER BY ${winkerkEntry.LIDMATE_WYK} ASC, ${winkerkEntry.LIDMATE_VAN} ASC, ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_NOEMNAAM} ASC ;"
                        } else {
                            selection
                        }
                    }
                    else -> selection
                }

                try {
                    cursor = db.rawQuery(finalSelection ?: "", selectionArgs)
                    count = cursor.count
                } catch (e: SQLException) {
                    Log.e("WinkerkReader", "Query error for match $match >> $e")
                }
            }

            FOTO_UPDATER -> {
                val db = mDbHelper?.readableDatabase ?: return null
                val dbPath = context?.getDatabasePath("wkr_info.db")?.path ?: return null
                try {
                    db.execSQL("ATTACH '$dbPath' as INFO;")
                    cursor = db.rawQuery(selection ?: "", selectionArgs)
                    count = cursor.count
                    db.execSQL("detach database INFO")
                } catch (e: SQLException) {
                    Log.e("WinkerkReader", "FOTO_SYNC >> $e")
                }
            }

            FOTO -> {
                val db = mInfoDbHelper?.readableDatabase ?: return null
                try {
                    cursor = db.rawQuery(selection ?: "", selectionArgs)
                    count = cursor.count
                    Log.v(tag, "Cursor INFO query ${db.isOpen}")
                } catch (e: SQLException) {
                    Log.e("WinkerkReader", "FOTO >> $e")
                }
            }

            else -> throw IllegalArgumentException("Cannot query unknown URI $uri")
        }

        Log.v(tag, "Cursor count $count")
        return if (cursor != null && cursor.count > 0) cursor else null
    }

    override fun insert(uri: Uri, contentValues: ContentValues?): Uri? {
        val match = uriMatcher.match(uri)
        val context = context ?: return null

        return when (match) {
            FOTO -> {
                val db = mInfoDbHelper?.writableDatabase ?: return null
                val id = db.insert(winkerkEntry.INFO_TABLENAME, null, contentValues)
                context.contentResolver.notifyChange(uri, null)
                ContentUris.withAppendedId(uri, id)
            }

            WKR_GROEPE_LYS -> {
                val db = mInfoDbHelper?.writableDatabase ?: return null
                val id = db.insert(winkerkEntry.WKR_GROEPE_TABLENAME, null, contentValues)
                context.contentResolver.notifyChange(uri, null)
                ContentUris.withAppendedId(uri, id)
            }

            WKR_GROEPLEDE -> {
                val db = mInfoDbHelper?.writableDatabase ?: return null
                val groepId = contentValues?.getAsString("GroepID") ?: return null
                val lidmaatGuid = contentValues?.getAsString("LidmaatGUID") ?: return null

                var count = 0
                db.rawQuery(
                    "SELECT * FROM ${winkerkEntry.WKR_LIDMATE2GROEPE_TABLENAME} WHERE (GroepID = ?) AND (LidmaatGUID = ?)",
                    arrayOf(groepId, lidmaatGuid)
                ).use { cursor ->
                    count = cursor.count
                }

                if (count < 1) {
                    val id = db.insert(winkerkEntry.WKR_LIDMATE2GROEPE_TABLENAME, null, contentValues)
                    context.contentResolver.notifyChange(uri, null)
                    ContentUris.withAppendedId(uri, id)
                } else {
                    null
                }
            }

            else -> null
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        when (method) {
            "clearTag" -> {
                val db = mDbHelper?.writableDatabase ?: return null
                val sql = "UPDATE $arg SET ${winkerkEntry.LIDMATE_TAG} = 0"
                try {
                    db.execSQL(sql)
                } catch (e: SQLException) {
                    Log.e("WinkerkReader", "clearTag >> $e")
                }
                return null
            }
            "closeDatabase" -> {
                mDbHelper?.close()
                mDbHelper = null
                mInfoDbHelper?.close()
                mInfoDbHelper = null
            }
            "reloadDatabase" -> {
                mDbHelper = null
                mInfoDbHelper = null
                Log.d(tag, "reloadDatabase called")
                // Close existing helpers and remove them from the map
                mDbHelper?.let { winkerk_DB_Helper.closeInstance(WINKERK_DB) }
                mInfoDbHelper?.let { winkerk_DB_Helper.closeInstance(INFO_DB) }

                // Obtain fresh instances
                val ctx = context ?: return Bundle.EMPTY
                mDbHelper = winkerk_DB_Helper.getInstance(ctx, WINKERK_DB)
                mInfoDbHelper = winkerk_DB_Helper.getInstance(ctx, INFO_DB)
                return Bundle.EMPTY
            }
        }
        return null
    }

    override fun update(
        uri: Uri,
        contentValues: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val match = uriMatcher.match(uri)
        return when (match) {
            LIDMAAT_GUID, LIDMAAT_LIST -> updateLidmaat(uri, contentValues, selection, selectionArgs)
            FOTO -> {
                val guid = contentValues?.get(winkerkEntry.INFO_LIDMAAT_GUID)?.toString()
                if (guid != null) {
                    updateFoto(uri, contentValues, arrayOf(guid))
                } else -1
            }
            ADRES -> updateAdres(uri, contentValues, selection, selectionArgs)
            WKR_GROEPE_LYS -> {
                val db = mInfoDbHelper?.writableDatabase ?: return -1
                db.update(winkerkEntry.WKR_GROEPE_TABLENAME, contentValues, selection, selectionArgs)
            }
            else -> -1
        }
    }

    private fun updateLidmaat(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        if (values == null || values.size() == 0) return 0
        val db = mDbHelper?.writableDatabase ?: return 0
        val rowsUpdated = db.update(winkerkEntry.LIDMATE_TABLE_NAME, values, selection, selectionArgs)
        if (rowsUpdated != 0) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        db.close()
        return rowsUpdated
    }

    private fun updateFoto(uri: Uri, values: ContentValues?, selectionArgs: Array<String>): Int {
        if (values == null || values.size() == 0) return 0
        val db = mInfoDbHelper?.writableDatabase ?: return 0
        val rowsUpdated = db.update(winkerkEntry.INFO_TABLENAME, values, "${winkerkEntry.INFO_LIDMAAT_GUID} =?", selectionArgs)
        if (rowsUpdated == 0) {
            db.insert(winkerkEntry.INFO_TABLENAME, null, values)
        }
        if (rowsUpdated != 0) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        db.close()
        return rowsUpdated
    }

    private fun updateAdres(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        if (values == null || values.size() == 0) return 0
        val db = mDbHelper?.writableDatabase ?: return 0
        selection?.let { db.execSQL(it) }
        val rowsUpdated = 1
        context?.contentResolver?.notifyChange(uri, null)
        db.close()
        return rowsUpdated
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        // Not implemented – keep original stub
        return -1
    }

    override fun getType(uri: Uri): String? {
        return when (val match = uriMatcher.match(uri)) {
            ARGIEF_LAAI -> winkerkEntry.INFO_LOADER_ARGIEF
            LIDMAAT_ID -> winkerkEntry.CONTENT_ITEM_TYPE
            MEELEWING_LIDMAAT -> winkerkEntry.CONTENT_MEELEWING_LIST_TYPE
            LIDMAAT_LIST -> winkerkEntry.CONTENT_LIST_TYPE
            LIDMAAT_GUID -> winkerkEntry.CONTENT_ITEM_TYPE
            GESIN_GUID -> winkerkEntry.CONTENT_GESIN_LIST_TYPE
            OPROEP -> winkerkEntry.CONTENT_FOON_LIST_TYPE
            MYLPALE_GUID -> winkerkEntry.CONTENT_MYLPALE_LIST_TYPE
            GROEPE_GUID -> winkerkEntry.CONTENT_GROEPE_LIST_TYPE
            GROEPE_LYS -> winkerkEntry.CONTENT_GROEPE_LYS_TYPE
            WKR_GROEPE_ID -> winkerkEntry.INFO_LOADER_WKR_GROEPE_TYPE
            WKR_GROEPE_LYS -> winkerkEntry.INFO_LOADER_WKR_GROEPE_LIST_TYPE
            GEMEENTE_NAAM -> winkerkEntry.CONTENT_GEMEENTE_NAAM_LIST_TYPE
            LIDMAAT_OUDERDOM -> winkerkEntry.LIDMAAT_LOADER_OUDERDOM_LIST_TYPE
            ADRES -> winkerkEntry.CONTENT_ADRES_TYPE
            FOTO -> winkerkEntry.INFO_LOADER_FOTO_LIST_TYPE
            else -> throw IllegalStateException("Unknown URI $uri with match $match")
        }
    }
}