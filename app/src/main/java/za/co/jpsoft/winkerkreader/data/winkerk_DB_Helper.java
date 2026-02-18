package za.co.jpsoft.winkerkreader.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

/**
 * Created by Pieter Grobler on 22/07/2017.
 */

class winkerk_DB_Helper  extends SQLiteAssetHelper {

    // --Commented out by Inspection (21/09/2017 8:39 PM):public static final String LOG_TAG = winkerk_DB_Helper.class.getSimpleName();


    /**
     * Constructs a new instance of {@link winkerk_DB_Helper}.
     *
     * @param context of the app
     */
    public winkerk_DB_Helper(Context context, String DATABASE_NAME, int DATABASE_VERSION) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
  //      Log.v(LOG_TAG, "winkerk_DB_Helper");
   //     this.mContext = context;
    }//SQLiteAssetHelper(Context context, String name, String storageDirectory, CursorFactory factory, int version)

    @Override
    public synchronized void close() {
        super.close();
    }

    @Override
    public void onOpen(SQLiteDatabase db) {

        db.disableWriteAheadLogging();
        try {
        //    db.execSQL("CREATE TABLE tmp (_id INTEGER PRIMARY KEY AUTOINCREMENT) SELECT * as FROM MEMBERS;");
            //db.execSQL("ALTER TABLE tmp ADD COLUMN _id INTEGER PRIMARY KEY AUTOINCREMENT;");
         //   db.execSQL("INSERT INTO tmp SELECT * FROM MEMBERS;");
        }
        catch (android.database.sqlite.SQLiteException e)
        {

        }
        super.onOpen(db);

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

}