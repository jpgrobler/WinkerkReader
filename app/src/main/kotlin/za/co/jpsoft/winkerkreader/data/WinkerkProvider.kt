package za.co.jpsoft.winkerkreader.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase


class WinkerkProvider : ContentProvider() {

    private val tag = "WinkerkProvider"
    private var database: WinkerkDatabase? = null

    private companion object {
        private const val LIDMAAT_LIST = 100
        private const val LIDMAAT_GUID = 101
        private const val GESIN_GUID = 104
        private const val OPROEP = 105
        private const val GEMEENTE_NAAM = 107
        private const val LIDMAAT_OUDERDOM = 108
        private const val ARGIEF_LAAI = 120

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_LIDMATE, LIDMAAT_LIST)
            addURI(WinkerkContract.CONTENT_AUTHORITY, "${WinkerkContract.PATH_LIDMATE}/#", LIDMAAT_GUID)
            addURI(WinkerkContract.CONTENT_AUTHORITY, "${WinkerkContract.PATH_GESIN}/#", GESIN_GUID)
            addURI(WinkerkContract.CONTENT_AUTHORITY, "${WinkerkContract.PATH_FOON}/#", OPROEP)
            addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_GEMEENTE_NAAM, GEMEENTE_NAAM)
            addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_OUDERDOM, LIDMAAT_OUDERDOM)
            addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_ARGIEF, ARGIEF_LAAI)
        }
    }

    override fun onCreate(): Boolean {
        val context = context ?: return false
        database = WinkerkDatabase.getInstance(context)
        if (BuildConfig.DEBUG) Log.v(tag, "onCreate")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val db = database ?: return null
        val match = uriMatcher.match(uri)

        return when (match) {
            GEMEENTE_NAAM -> {
                db.memberDao().queryRaw(
                    SimpleSQLiteQuery(
                        "SELECT DISTINCT Gemeente, [Gemeente epos] FROM Members",
                        emptyArray()
                    )
                )
            }

            LIDMAAT_OUDERDOM -> {
                val query = """
                    SELECT *, 
                           (strftime('%Y', 'now') - strftime('%Y', Geboortedatum)) 
                           - (strftime('%m-%d', 'now') < strftime('%m-%d', Geboortedatum)) AS age
                    FROM Members
                    WHERE Rekordstatus = '0'
                    ORDER BY age DESC
                """.trimIndent()
                db.memberDao().queryRaw(SimpleSQLiteQuery(query))
            }

            ARGIEF_LAAI -> {
                val finalSelection = selection ?: "SELECT * FROM Argief"
                db.argiefDao().queryRaw(SimpleSQLiteQuery(finalSelection, selectionArgs ?: emptyArray()))
            }

            GESIN_GUID -> {
                db.memberDao().queryRaw(SimpleSQLiteQuery(selection ?: "", selectionArgs ?: emptyArray()))
            }

            OPROEP -> {
                db.memberDao().queryRaw(SimpleSQLiteQuery(selection ?: "", selectionArgs ?: emptyArray()))
            }

            LIDMAAT_GUID -> {
                db.memberDao().queryRaw(SimpleSQLiteQuery(selection ?: "", selectionArgs ?: emptyArray()))
            }

            LIDMAAT_LIST -> {
                db.memberDao().queryRaw(SimpleSQLiteQuery(selection ?: "", selectionArgs ?: emptyArray()))
            }

            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
    }

    override fun insert(uri: Uri, contentValues: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        contentValues: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val db = database ?: return 0
        val match = uriMatcher.match(uri)

        return when (match) {
            LIDMAAT_GUID, LIDMAAT_LIST -> {
                if (contentValues == null || contentValues.size() == 0) return 0

                // Build dynamic UPDATE statement
                val setClauses = mutableListOf<String>()
                val args = mutableListOf<String>()
                contentValues.keySet().forEach { key ->
                    setClauses.add("[$key] = ?")
                    args.add(contentValues.getAsString(key))
                }
                // Add the selection args at the end
                selectionArgs?.let { args.addAll(it) }

                val sql = StringBuilder("UPDATE Members SET ")
                    .append(setClauses.joinToString(", "))
                    .append(" WHERE $selection")
                    .toString()

                // Use compileStatement to get the affected row count
                val statement = db.openHelper.writableDatabase.compileStatement(sql)
                args.forEachIndexed { index, value ->
                    statement.bindString(index + 1, value)
                }
                val rows = statement.executeUpdateDelete()
                statement.close()
                rows
            }
            else -> 0
        }
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            LIDMAAT_LIST -> winkerkEntry.CONTENT_LIST_TYPE
            LIDMAAT_GUID -> winkerkEntry.CONTENT_ITEM_TYPE
            GESIN_GUID -> winkerkEntry.CONTENT_GESIN_LIST_TYPE
            OPROEP -> winkerkEntry.CONTENT_FOON_LIST_TYPE
            GEMEENTE_NAAM -> winkerkEntry.CONTENT_GEMEENTE_NAAM_LIST_TYPE
            LIDMAAT_OUDERDOM -> winkerkEntry.LIDMAAT_LOADER_OUDERDOM_LIST_TYPE
            ARGIEF_LAAI -> winkerkEntry.INFO_LOADER_ARGIEF
            else -> throw IllegalStateException("Unknown URI $uri")
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        when (method) {
            "clearTag" -> {
                val db = database ?: return null
                db.openHelper.writableDatabase.execSQL("UPDATE Members SET Tag = 0")
                return null
            }
            "closeDatabase" -> {
                WinkerkDatabase.closeInstance()
                database = null
                return null
            }
            "reloadDatabase" -> {
                if (BuildConfig.DEBUG) Log.d(tag, "reloadDatabase called")
                WinkerkDatabase.closeInstance()
                val ctx = context ?: return Bundle.EMPTY
                database = WinkerkDatabase.getInstance(ctx)
                return Bundle.EMPTY
            }
        }
        return null
    }
}