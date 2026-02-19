package za.co.jpsoft.winkerkreader.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_DB;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_LIDMAAT_GUID;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_TABLENAME;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_TABLE_NAME;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_TAG;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_VAN;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_WYK;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WINKERK_DB;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WKR_GROEPE_TABLENAME;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WKR_LIDMATE2GROEPE_TABLENAME;

/**
 * Created by Pieter Grobler on 22/07/2017.
 */

public class winkerkProvider extends ContentProvider {

// --Commented out by Inspection START (21/09/2017 8:40 PM):
//    /**
//     * Tag for the log messages
//     */
//    public static final String LOG_TAG = winkerkProvider.class.getSimpleName();
// --Commented out by Inspection STOP (21/09/2017 8:40 PM)

    private final String TAG = "winkerkProvider";
    /**
     * URI matcher code for the content URI for the Lidmate table
     */
    private static final int LIDMAAT_LIST = 100;
    /**
     * URI matcher code for the content URI for a single Lidmaat in the Lidmate table
     */
    private static final int LIDMAAT_GUID = 101;
//    private static final int ADRES_GUID = 102;
 //   private static final int KODES_GUID = 103;
    /**
     * URI matcher code for the content URI for a single Lidmaat's gesin (family) in the Lidmate table
     */
    private static final int GESIN_GUID = 104;
    /**
     * URI matcher code for the content URI for looking up incomming call
     */
    private static final int OPROEP = 105;
    private static final int MYLPALE_GUID = 106;
    private static final int GEMEENTE_NAAM =107;
    private static final int LIDMAAT_OUDERDOM =108;
    private static final int LIDMAAT_ID =109;
    private static final int ADRES =110;
    private static final int FOTO =111;
    private static final int FOTO_UPDATER =112;
    private static final int GROEPE_GUID = 113;
    private static final int GROEPE_LYS = 114;
    private static final int WKR_GROEPE_LYS = 115;
    private static final int WKR_GROEPE_ID = 116;
    private static final int WKR_NGROEP = 117;
    private static final int WKR_GROEPLEDE = 118;
    private static final int MEELEWING_LIDMAAT = 119;
    private static final int ARGIEF_LAAI = 120;
    /**
     * UriMatcher object to match a content URI to a corresponding code.
     * The input passed into the constructor represents the code to return for the root URI.
     * It's common to use NO_MATCH as the input for this case.
     */
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    // Static initializer. This is run the first time anything is called from this class.
    static {
        // The calls to addURI() go here, for all of the content URI patterns that the provider
        // should recognize. All paths added to the UriMatcher have a corresponding code to return
        // when a match is found.

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_LIDMATE, LIDMAAT_LIST);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_LIDMATE + "/#", LIDMAAT_GUID);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_GESIN + "/#", GESIN_GUID);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_FOON +"/#", OPROEP);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_MYLPALE +"/#", MYLPALE_GUID);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_MEELEWING +"/#", MEELEWING_LIDMAAT);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_GROEPE +"/#", GROEPE_GUID);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_GROEPE_LYS, GROEPE_LYS);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_GEMEENTE_NAAM, GEMEENTE_NAAM);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_ADRES +"/#", ADRES);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_FOTO +"/#", FOTO);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_FOTO, FOTO);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_FOTO_UPDATER, FOTO_UPDATER);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_WKR_GROEPE +"/#", WKR_GROEPE_ID);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_WKR_GROEPE, WKR_GROEPE_LYS);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_WKR_GROEPLEDE, WKR_GROEPLEDE);

        sUriMatcher.addURI(WinkerkContract.CONTENT_AUTHORITY, WinkerkContract.PATH_ARGIEF, ARGIEF_LAAI);

    }

    /**
     * Database helper object
     */
    private winkerk_DB_Helper mDbHelper;
    private winkerk_DB_Helper mInfoDbHelper;

    public boolean isColumnExists(SQLiteDatabase db, String tableName, String columnName) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    if (columnName.equalsIgnoreCase(name)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking if column exists: " + columnName, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    @Override
    public boolean onCreate() {

            mDbHelper = new winkerk_DB_Helper(getContext(),WINKERK_DB, 1);
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            try {
                // Check if TAG column exists in Members table
                if (!isColumnExists(db, "Members", "TAG")) {
                    db.execSQL("ALTER TABLE Members ADD COLUMN TAG BIT");
                    Log.d(TAG, "Added TAG column to Members table");
                } else {
                    Log.d(TAG, "TAG column already exists in Members table");
                }

                // Check if _id column exists in Datum table
                if (!isColumnExists(db, "Datum", "_id")) {
                    db.execSQL("ALTER TABLE Datum ADD COLUMN _id INTEGER PRIMARY KEY AUTOINCREMENT");
                    Log.d(TAG, "Added _id column to Datum table");
                } else {
                    Log.d(TAG, "_id column already exists in Datum table");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error altering tables", e);
            } finally {
                db.close();
            }
//            SQLiteDatabase db = mDbHelper.getWritableDatabase();
//            try {
//                db.execSQL("ALTER TABLE Members ADD COLUMN TAG BIT");
//            } catch (Exception ee) {
//                Log.d(TAG, "TAG Exists");
//            }
//            try {
//            db.execSQL("ALTER TABLE Datum ADD COLUMN _id INTEGER PRIMARY KEY AUTOINCREMENT");
//                } catch (Exception ee) {
//                    Log.d(TAG, "TAG Exists");
//                }
//            db.close();
            mInfoDbHelper = new winkerk_DB_Helper(getContext(),INFO_DB, 1);

        Log.v(TAG, "onCreate");
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // This cursor will hold the result of the query
        Cursor cursor = null;
        SQLiteDatabase database;

        // Figure out if the URI matcher can match the URI to a specific code
        int match = sUriMatcher.match(uri);
        int g = 0;

        if (match == GEMEENTE_NAAM) {
            database = mDbHelper.getReadableDatabase();
            Log.v(TAG, "GEMEENTE_NAAM " + database.isOpen());
                Cursor c = null;
                boolean tableExists = false;
                try {
                    cursor = database.rawQuery(selection, selectionArgs);
                    g = cursor.getCount();
                }
                catch (Exception e) {
                    Log.d(TAG, "Gemeente doesn't exist :(((");
                }
                finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }

             }
        if ( match == LIDMAAT_LIST || match == LIDMAAT_GUID
                    || match == LIDMAAT_OUDERDOM || match == OPROEP || match == MYLPALE_GUID
                    || match == GESIN_GUID || match == GROEPE_GUID || match == GROEPE_LYS || match == MEELEWING_LIDMAAT || match == ARGIEF_LAAI) {
                // Get readable database
                database = mDbHelper.getReadableDatabase();
                Log.v(TAG, "Cursor query " + database.isOpen());
        switch (match) {
            case ARGIEF_LAAI: // find all Lidmate for listview on MainActivity2
                try {
                    cursor = database.rawQuery(selection, selectionArgs);
                    g = cursor.getCount();
                }catch (SQLException mSQLException) {
                    Log.e("WinkerkReader", "ARGIEF_LIST >>" + mSQLException);
                }
                break;
            case LIDMAAT_LIST: // find all Lidmate for listview on MainActivity2
                try {
                    cursor = database.rawQuery(selection, selectionArgs);
                    g = cursor.getCount();
                }catch (SQLException mSQLException) {
                    Log.e("WinkerkReader", "LIDMAAT_LIST >>" + mSQLException);
                }

                break;

            case LIDMAAT_GUID: // Find single Lidmaat's info
                if (winkerkEntry.SORTORDER.equals("VAN")) {
                    selection = selection + " ORDER BY " + LIDMATE_VAN + " ASC, " + LIDMATE_TABLE_NAME + "." + LIDMATE_NOEMNAAM + " ASC ;";}
                if (winkerkEntry.SORTORDER.equals("WYK")){
                    selection = selection + " ORDER BY " + LIDMATE_WYK + " ASC, " + LIDMATE_VAN + " ASC, " + LIDMATE_TABLE_NAME + "." + LIDMATE_NOEMNAAM + " ASC ;";}
                try {
                    cursor = database.rawQuery(selection, selectionArgs);
                    g = cursor.getCount();
                }catch (SQLException mSQLException) {
                    Log.e("WinkerkReader", "LIDMAAT_GUID >>" + mSQLException);
                }

                break;

            case LIDMAAT_OUDERDOM: // find all Lidmate sorted by age
                try {
                    cursor = database.rawQuery(selection, selectionArgs);
                    g = cursor.getCount();
                }catch (SQLException mSQLException) {
                    Log.e("WinkerkReader", "LIDMAAT_OUDERDOM >>" + mSQLException);
                }
                finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                break;

            case GESIN_GUID: // Find familiymembers
                try {
                    if (cursor != null) {
                        cursor.close();
                    }
                    cursor = database.rawQuery(selection, selectionArgs);
                    g = cursor.getCount();
                    }
                catch (SQLException mSQLException) {
                    Log.e("WinkerkReader", "GESIN_GUID >>" + mSQLException);
                    }

                break;

            case OPROEP: // Find incomming caller
                try {
                    if (cursor != null) {
                        cursor.close();
                    }
                    cursor = database.rawQuery(selection, selectionArgs);
                    g = cursor.getCount();
                    }
                catch (SQLException mSQLException) {
                    Log.e("WinkerkReader", "OPROEP >>" + mSQLException);
                    }

                break;

            case MYLPALE_GUID: // Find aal MYLPALE of lidmaat
                try {
                    if (cursor != null) {
                        cursor.close();
                    }
                    cursor = database.rawQuery(selection, selectionArgs);
                    g = cursor.getCount();
                    }
                catch (SQLException mSQLException) {
                            Log.e("WinkerkReader", "MYLPALE >>" + mSQLException);
                    }

                break;


            case MEELEWING_LIDMAAT: // Find familiymembers
                try {
                    if (cursor != null) {
                        cursor.close();
                    }
                    cursor = database.rawQuery(selection, selectionArgs);
                    g = cursor.getCount();
                    }
                catch (SQLException mSQLException) {
                    Log.e("WinkerkReader", "Meelewing_Lidmaat >>" + mSQLException);
                    }

                break;

            case GROEPE_GUID: // Find familiymembers
                try {
                    if (cursor != null) {
                        cursor.close();
                    }
                    cursor = database.rawQuery(selection, selectionArgs);
                    g = cursor.getCount();
                    }
                catch (SQLException mSQLException) {
                    Log.e("WinkerkReader", "Groepe_GUID >>" + mSQLException);
                    }

                break;
            case GROEPE_LYS: // Find all Winkerk Groups
                try {
                    if (cursor != null) {
                        cursor.close();
                    }
                    cursor = database.rawQuery(selection, selectionArgs);
                    g = cursor.getCount();
                    }
                catch (SQLException mSQLException) {
                    Log.e("WinkerkReader", "Groepe_Lys >>" + mSQLException);
                    }

                break;

            default:
                throw new IllegalArgumentException("Cannot query unknown URI " + uri);
            }
        } else {
            if (match == GEMEENTE_NAAM) {
                database = mDbHelper.getReadableDatabase();
                Log.v(TAG, "Cursor query " + database.isOpen());
            } else {
                if (match == FOTO_UPDATER){
                    //database = mInfoDbHelper.getReadableDatabase();
                    database = mDbHelper.getReadableDatabase();
                    //Cursor cursor = null;
                    try {
                        if (cursor != null) {
                            cursor.close();
                        }
                        database.execSQL(" ATTACH '"+ getContext().getDatabasePath("wkr_info.db").getPath() + "' as INFO; ");
                        cursor = database.rawQuery(selection, selectionArgs);
                        g = cursor.getCount();
                        database.execSQL("detach database INFO");
                        }
                    catch (SQLException mSQLException) {
                        Log.e("WinkerkReader", "FOTO_SYNC >>" + mSQLException);
                        }
                }
            }
                switch (match) {
                    case FOTO:
                        database = mInfoDbHelper.getReadableDatabase();
                        try {
                            if (cursor != null) {
                                cursor.close();
                            }
                            cursor = database.rawQuery(selection, selectionArgs);
                            g = cursor.getCount();
                            Log.v(TAG, "Cursor INFO query " + database.isOpen());
                            }
                        catch (SQLException mSQLException) {
                            Log.e("WinkerkReader", "FOTO >>" + mSQLException);
                            }

                        break;

                    default:
                        break;
                        //throw new IllegalArgumentException("Cannot query unknown URI " + uri);
                }
            }
        // Set notification URI on the Cursor,
        // so we know what content URI the Cursor was created for.
        // If the data at this URI changes, then we know we need to update the Cursor.
        // cursor.setNotificationUri(getContext().getContentResolver(), uri);

        // Return the cursor

        Log.v(TAG, "Cursor count " + g);
        if ((cursor != null) && (cursor.getCount() > 0)) {
        return cursor;}
        else {return null;}
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        int match = sUriMatcher.match(uri);
        Cursor cursor;
        switch (match) {
            case FOTO:
                SQLiteDatabase database = mInfoDbHelper.getWritableDatabase();
                long id = database.insert(INFO_TABLENAME, null,contentValues);
                getContext().getContentResolver().notifyChange(uri, null);

                // Return the new URI with the ID (of the newly inserted row) appended at the end
                return ContentUris.withAppendedId(uri, id);
            case WKR_GROEPE_LYS:
                SQLiteDatabase database2 = mInfoDbHelper.getWritableDatabase();
                long id2 = database2.insert(WKR_GROEPE_TABLENAME, null,contentValues);
                getContext().getContentResolver().notifyChange(uri, null);

                // Return the new URI with the ID (of the newly inserted row) appended at the end
                return ContentUris.withAppendedId(uri, id2);
            case WKR_GROEPLEDE:
                SQLiteDatabase database3 = mInfoDbHelper.getWritableDatabase();
                int g = 0;
                cursor = null;
                try {
                    cursor = database3.rawQuery("SELECT * FROM wkrLidmate2Groepe WHERE (GroepID ='" + contentValues.getAsString("GroepID") + "') AND (LidmaatGUID = \"" + contentValues.getAsString("LidmaatGUID") + "\")",null);
                    g = cursor.getCount();
                    }
                catch (SQLException mSQLException) {
                    Log.e("WinkerkReader", "Groepe_GUID >>" + mSQLException);
                    }
                finally {
                    if (cursor != null) {
                        cursor.close();
                        }
                    }

                if (g < 1) {
                    long id3 = database3.insert(WKR_LIDMATE2GROEPE_TABLENAME, null,contentValues);
                    getContext().getContentResolver().notifyChange(uri, null);

                    // Return the new URI with the ID (of the newly inserted row) appended at the end
                    return ContentUris.withAppendedId(uri, id3);
                }
                else {
                    return null;
                }
            default:
                return null;
        }
    }


    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if(method.equals("clearTag")) {
            SQLiteDatabase database = mDbHelper.getWritableDatabase();
            String sql = "UPDATE " + arg + " SET " + LIDMATE_TAG + " = 0;";
            Cursor cursor = null;
            try {
                database.rawQuery(sql, null);
                }
            catch (SQLException mSQLException) {
                Log.e("WinkerkReader", "Groepe_GUID >>" + mSQLException);
                }
        }
        return null;
    }



    @Override
    public int update(Uri uri, ContentValues contentValues, String selection,
                      String[] selectionArgs) {
        int match = sUriMatcher.match(uri);
        switch (match) {
            case LIDMAAT_GUID: // Find single Lidmaat's info
//                selection = "Lidmate._id =?";
                //selectionArgs = new String[] { String.valueOf(ContentUris.parseId(uri)) };
                return updateLidmaat(uri, contentValues, selection, selectionArgs);
            case LIDMAAT_LIST:
                //selectionArgs = new String[] { String.valueOf(ContentUris.parseId(uri)) };
                return updateLidmaat(uri, contentValues, selection, selectionArgs);
            case FOTO:
//                selection = INFO_LIDMAAT_GUID + " =?";
                selectionArgs = new String[] { (contentValues.get(INFO_LIDMAAT_GUID).toString()) };
                return updateFoto(uri, contentValues, selectionArgs);

            case ADRES:
                return updateAdres(uri,contentValues,selection,selectionArgs);

            case WKR_GROEPE_LYS:
                SQLiteDatabase database = mInfoDbHelper.getWritableDatabase();
                // SQLiteDatabase database2 = mDbHelper.getWritableDatabase();
                return database.update(winkerkEntry.WKR_GROEPE_TABLENAME, contentValues, selection, selectionArgs);

        }
        return -1;
    }

    private int updateLidmaat(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // If there are no values to update, then don't try to update the database
        if (values.size() == 0) {
            return 0;
        }
        // Otherwise, get writeable database to update the data
        SQLiteDatabase database = mDbHelper.getWritableDatabase();
        // Perform the update on the database and get the number of rows affected
        int rowsUpdated = database.update(winkerkEntry.LIDMATE_TABLE_NAME, values, selection, selectionArgs);
        // If 1 or more rows were updated, then notify all listeners that the data at the
        // given URI has changed
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        database.close();
        // Return the number of rows updated
        return rowsUpdated;
    }

    private int updateFoto(Uri uri, ContentValues values, String[] selectionArgs) {
        // If there are no values to update, then don't try to update the database
        if (values.size() == 0) {
            return 0;
        }

        // Otherwise, get writeable database to update the data
        SQLiteDatabase database = mInfoDbHelper.getWritableDatabase();
       // SQLiteDatabase database2 = mDbHelper.getWritableDatabase();
        String selection = INFO_LIDMAAT_GUID + " =?";
        // Perform the update on the database and get the number of rows affected
        int rowsUpdated = database.update(winkerkEntry.INFO_TABLENAME, values, selection, selectionArgs);

        //int rowsUpdated2 = database2.update(winkerkEntry.LIDMATE_TABLE_NAME, values, selection, selectionArgs);
        if (rowsUpdated == 0) {
            database.insert(INFO_TABLENAME, null,values);//winkerkEntry.LIDMATE_TABLE_NAME, values, selection, selectionArgs);
        }
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        database.close();
        // Return the number of rows updated
        return rowsUpdated;
    }

    private int updateAdres(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // If there are no values to update, then don't try to update the database
        if (values.size() == 0) {
            return 0;
        }

        // Otherwise, get writeable database to update the data
        SQLiteDatabase database = mDbHelper.getWritableDatabase();


        // Perform the update on the database and get the number of rows affected
        database.execSQL(selection);//database.update(winkerkEntry.ADRESSE_TABLENAME, values, selection, selectionArgs);
        int rowsUpdated = 1;
        // If 1 or more rows were updated, then notify all listeners that the data at the
        // given URI has changed
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        database.close();
        // Return the number of rows updated
        return rowsUpdated;
    }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int match = sUriMatcher.match(uri);
        String selectiond;

        return -1;
    }

    @Override
    public String getType(Uri uri) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case ARGIEF_LAAI:
                return winkerkEntry.INFO_LOADER_ARGIEF;
            case LIDMAAT_ID:
                return winkerkEntry.CONTENT_ITEM_TYPE;
            case MEELEWING_LIDMAAT:
                return winkerkEntry.CONTENT_MEELEWING_LIST_TYPE;
            case LIDMAAT_LIST:
                return winkerkEntry.CONTENT_LIST_TYPE;
            case LIDMAAT_GUID:
                return winkerkEntry.CONTENT_ITEM_TYPE;
            case GESIN_GUID:
                return winkerkEntry.CONTENT_GESIN_LIST_TYPE;
            case OPROEP:
                return winkerkEntry.CONTENT_FOON_LIST_TYPE;
            case MYLPALE_GUID:
                return winkerkEntry.CONTENT_MYLPALE_LIST_TYPE;
            case GROEPE_GUID:
                return winkerkEntry.CONTENT_GROEPE_LIST_TYPE;
            case GROEPE_LYS:
                return winkerkEntry.CONTENT_GROEPE_LYS_TYPE;
            case WKR_GROEPE_ID:
                return winkerkEntry.INFO_LOADER_WKR_GROEPE_TYPE;
            case WKR_GROEPE_LYS:
                return winkerkEntry.INFO_LOADER_WKR_GROEPE_LIST_TYPE;
            case GEMEENTE_NAAM:
                return winkerkEntry.CONTENT_GEMEENTE_NAAM_LIST_TYPE;
            case LIDMAAT_OUDERDOM:
                return winkerkEntry.LIDMAAT_LOADER_OUDERDOM_LIST_TYPE;
            case ADRES:
                return winkerkEntry.CONTENT_ADRES_TYPE;
            case FOTO:
                return winkerkEntry.INFO_LOADER_FOTO_LIST_TYPE;
            default:
                throw new IllegalStateException("Unknown URI " + uri + " with match " + match);
        }
    }

    public boolean doesFieldExist(String tableName, String fieldName)
    {
        boolean isExist = false;
        SQLiteDatabase db = mInfoDbHelper.getWritableDatabase();
        Cursor res = null;
        try {
            res = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            }
        finally {
            if (res != null) {
                res.close();
                }
            }

        if (res.moveToFirst()) {
            do {
                int value = res.getColumnIndex("name");
                if(value != -1 && res.getString(value).equals(fieldName))
                {
                    isExist = true;
                }
                // Add book to books

            } while (res.moveToNext());
        }

        return isExist;
    }
}

