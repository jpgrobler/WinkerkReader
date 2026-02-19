package za.co.jpsoft.winkerkreader;

import android.app.LoaderManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
//import androidx.appcompat.widget;
//import android.support.v7.app.AppCompatActivity;
//import android.support.v7.widget.LinearLayoutCompat;
//import android.support.v7.widget.PopupMenu;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import za.co.jpsoft.winkerkreader.data.SpinnerAdapter;
import za.co.jpsoft.winkerkreader.data.WellBehavedEditText;
import za.co.jpsoft.winkerkreader.data.WinkerkContract;

import org.joda.time.DateTime;
import org.joda.time.Years;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.*;
import static za.co.jpsoft.winkerkreader.R.id.detail_Beroep;
import static za.co.jpsoft.winkerkreader.R.id.detail_BeroepBlock;
import static za.co.jpsoft.winkerkreader.R.id.detail_Werkgewer;
import static za.co.jpsoft.winkerkreader.R.id.detail_WerkgewerBlock;
import static za.co.jpsoft.winkerkreader.R.id.detail_email_icon;
import static za.co.jpsoft.winkerkreader.R.id.detail_epos;
import static za.co.jpsoft.winkerkreader.R.id.detail_eposBlock;
import static za.co.jpsoft.winkerkreader.R.id.detail_geboortedatum;
import static za.co.jpsoft.winkerkreader.R.id.detail_gesinBlock;
import static za.co.jpsoft.winkerkreader.R.id.detail_kontak_foto;
import static za.co.jpsoft.winkerkreader.R.id.detail_landlyn_icon;
import static za.co.jpsoft.winkerkreader.R.id.detail_noemnaam;
import static za.co.jpsoft.winkerkreader.R.id.detail_nooiensvan;
import static za.co.jpsoft.winkerkreader.R.id.detail_nooiensvanBlock;
import static za.co.jpsoft.winkerkreader.R.id.detail_posadres;
import static za.co.jpsoft.winkerkreader.R.id.detail_posadresBlock;
import static za.co.jpsoft.winkerkreader.R.id.detail_selfoon;
import static za.co.jpsoft.winkerkreader.R.id.detail_selfoonBlock;
import static za.co.jpsoft.winkerkreader.R.id.detail_selfoon_icon;
import static za.co.jpsoft.winkerkreader.R.id.detail_sms_icon;
import static za.co.jpsoft.winkerkreader.R.id.detail_straat_icon;
import static za.co.jpsoft.winkerkreader.R.id.detail_straatadres;
import static za.co.jpsoft.winkerkreader.R.id.detail_straatadresBlock;
import static za.co.jpsoft.winkerkreader.R.id.detail_telefoon;
import static za.co.jpsoft.winkerkreader.R.id.detail_telefoonBlock;
import static za.co.jpsoft.winkerkreader.R.id.detail_van;
import static za.co.jpsoft.winkerkreader.R.id.detail_vollename;
import static za.co.jpsoft.winkerkreader.R.id.detail_whatsapp_icon;
import static za.co.jpsoft.winkerkreader.R.id.detail_wyk;
import static za.co.jpsoft.winkerkreader.Utils.fixphonenumber;
import static za.co.jpsoft.winkerkreader.Utils.parseDate;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.CacheDir;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.EPOSHTML;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.FotoDir;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.GEMEENTE2_NAAM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.GEMEENTE3_NAAM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.GEMEENTE_NAAM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.GROEPADRES;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.GROEPNAAM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.GROEPROL;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.GROEP_AANSLUIT;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.GROEP_UITTREE;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_FOTO_PATH;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_GROUP;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_LIDMAAT_GUID;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_LOADER_FOTO_URI;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDS;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_BEROEP;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_BEWYSSTATUS;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_DOOPDS;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_EPOS;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_GEMEENTE;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_GESINSHOOFGUID;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_GESLAG;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSTATUS;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_LANDLYN;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_LIDMAATGUID;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_LIDMAATSTATUS;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_NOOIENSVAN;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_PICTUREPATH;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_POSADRES;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_SELFOON;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_STRAATADRES;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_TABLE_NAME;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_VAN;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_VOORNAME;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_WERKGEWER;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_WYK;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.THUMBSIZE;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WKR_GROEPE_ADRES;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WKR_GROEPE_NAAM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WKR_LIDMATE2GROEPE_GROEPROL;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.widget.TextViewCompat;
//import static za.co.jpsoft.winkerkreader.R.id.van;

/**
 * Created by Pieter Grobler on 24/07/2017.
 */

public class lidmaat_detail_Activity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {


    /**
     * Identifier for the data loader
     */
    private static final int EXISTING_LIDMAAT_LOADER = 0;
    private static final int GESIN_LOADER = 1;
    private static final int MYLPAAL_LOADER = 2;
    private static final int GROEPE_LOADER = 3;
    private static final int WKR_GROEPE_LOADER = 4;
    private static final int INFO_LOADER = 8;
    private static final int MEELEWING_LOADER = 5;
    private static final int GAWES_LOADER = 6;
    private static final int PASSIE_LOADER = 7;
    private static final int SMS_LOADER = 9;

    private static final int CAMERA_REQUEST = 1888;
    private static final int SELECT_PICTURE = 200;
    /**
     * Content URI for chosen lidmaat
     */
    static String mCurrentPicUri = "CURRENT_PIC_URI";
    private Uri mImageUri;
    private Uri mCurrentLidmaatUri;
    private WellBehavedEditText mNameTextView;
    private EditText mNooiensVanTextView;
    private EditText mVanTextView;
    private EditText mVolleNameTextView;
    private EditText mSelfoonTextView;
    private EditText mTelefoonTextView;
    private EditText mWykTextView;
    private EditText mGeboortedatumTextView;
    private EditText mStraatadresTextView;
    private EditText mPosadresTextView;
    private EditText mLidmaatstatusTextView;
    private TextView mGesinTextView;
    private TextView mJAreOudTextView;
    private EditText mEposTextView;
    private EditText mBeroepTextView;
    private EditText mWerkgewerTextView;
    private LinearLayout mBeroepBlock;
    private LinearLayout mWerkgewerBlock;
    private LinearLayout mSelfoonBlock;
    private LinearLayout mTelefoonBlock;
    private LinearLayout mEposBlock;
    private LinearLayout mStraatadresBlock;
    private LinearLayout mPosadresBlock;
    private LinearLayout mNooiensvanBlock;
    private LinearLayout mGesinTextViewBlock;
    private ImageView mSelfooonIcon;
    private ImageView mTelefoonIcon;
    private ImageView mSmsIcon;
    private ImageView mEposIcon;
    private ImageView mWhatsappIcon;
    private ImageView mKontakFoto;
    private Button mWysigButton;
    private int current_id;
    private Spinner mGeslagSpinner;
    private Spinner mHuwelikstatusSpinner;
    private Cursor mCursor;
    private String mStraatAdres;
    private String mPosAdres;
    private String mLidmaatGUID;
    private String LIDMAAT_IN_USE;

    private final String[] huwelikStatusArray = {"Getroud", "Ongetroud", "Geskei", "Weduwee", "Wewenaar", "Onbekend"};
    private final String[] geslagteArray = {"Vroulik", "Manlik"};
    private final int[] geslagPrente = {R.drawable.female, R.drawable.male};
    private String mGeslagB = "";
    private String mHuwelikstatus = "Ongetroud";

//    @Override
//    public void onBackPressed() {
//        for (int lo = 0; lo < 9; lo++) {
//            getLoaderManager().destroyLoader(lo);
//        }
//        finish();
//        super.onBackPressed();
//    }onBackPressed


    @Override
    public void onDestroy(){
        for (int lo = 0; lo < 9; lo++) {
            getLoaderManager().destroyLoader(lo);
        }
        finish();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId()== android.R.id.home) {
                onBackPressed();
                return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lidmaat_detail);
        winkerkEntry.id = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.detail_indeterminateBar);
        ProgressBar progressBar2 = (ProgressBar) findViewById(R.id.detail_indeterminateBar2);
       // TextView regstatus = (TextView) findViewById(R.id.reg_reg);


            progressBar.setVisibility(View.GONE);
            progressBar2.setVisibility(View.GONE);


        //LinearLayout mylpaleBlock = (LinearLayout) findViewById(R.id.detail_mylpaleBlock);
        LinearLayout mylpaleBlock2 = (LinearLayout) findViewById(R.id.detail_mylpaleBlock2);
        mylpaleBlock2.setVisibility(View.GONE);
        LinearLayout groepeBlockm = (LinearLayout) findViewById(R.id.detail_groepBlockm);
        groepeBlockm.setVisibility(View.GONE);
        LinearLayout meelewingBlock = (LinearLayout) findViewById(R.id.detail_meelewingBlock);
        meelewingBlock.setVisibility(View.GONE);
        LinearLayout passieBlock = (LinearLayout) findViewById(R.id.detail_passieBlock);
        passieBlock.setVisibility(View.GONE);
        LinearLayout gawesBlock = (LinearLayout) findViewById(R.id.detail_gawesBlock);
        gawesBlock.setVisibility(View.GONE);

        // Examine the intent that was used to launch this activity,
        // in order to figure out if we're creating a new pet or editing an existing one.
        Intent intent = getIntent();
        mCurrentLidmaatUri = intent.getData();
        //start loading data
        getLoaderManager().initLoader(EXISTING_LIDMAAT_LOADER, null, this);

        TextView gemeenten = (TextView) findViewById(R.id.detail_gemeentenaam);

            gemeenten.setText(winkerkEntry.GEMEENTE_NAAM);

        gemeenten.setSelected(true);

        // Find all relevant views that we will need to read user input from
        mNameTextView = (WellBehavedEditText) findViewById(detail_noemnaam);
        mVanTextView = (WellBehavedEditText) findViewById(detail_van);
        mNooiensVanTextView = (WellBehavedEditText) findViewById(detail_nooiensvan);
        mVolleNameTextView = (WellBehavedEditText) findViewById(detail_vollename);
        mSelfoonTextView = (WellBehavedEditText) findViewById(detail_selfoon);
        mTelefoonTextView = (WellBehavedEditText) findViewById(detail_telefoon);
        mWykTextView = (WellBehavedEditText) findViewById(detail_wyk);
        mGeboortedatumTextView = (WellBehavedEditText) findViewById(detail_geboortedatum);
        mStraatadresTextView = (WellBehavedEditText) findViewById(detail_straatadres);
        mPosadresTextView = (WellBehavedEditText) findViewById(detail_posadres);
        mEposTextView = (WellBehavedEditText) findViewById(detail_epos);
        mBeroepTextView = (WellBehavedEditText) findViewById(detail_Beroep);
        mWerkgewerTextView = (WellBehavedEditText) findViewById(detail_Werkgewer);
        mWysigButton = (Button) findViewById(R.id.buttonWysig);
        mGeslagSpinner = (Spinner) findViewById(R.id.geslag);
        mHuwelikstatusSpinner = (Spinner) findViewById(R.id.huwelikstatus);
        mJAreOudTextView = (TextView) findViewById(R.id.detail_jareoud);
        mLidmaatstatusTextView = (EditText) findViewById(R.id.detail_Lidmaatstatus);

        //Find view blocks ~
        mSelfoonBlock = (LinearLayout) findViewById(detail_selfoonBlock);
        mTelefoonBlock = (LinearLayout) findViewById(detail_telefoonBlock);
        mEposBlock = (LinearLayout) findViewById(detail_eposBlock);
        mStraatadresBlock = (LinearLayout) findViewById(detail_straatadresBlock);
        mPosadresBlock = (LinearLayout) findViewById(detail_posadresBlock);
        mNooiensvanBlock = (LinearLayout) findViewById(detail_nooiensvanBlock);
        mGesinTextViewBlock = (LinearLayout) findViewById(detail_gesinBlock);
        mBeroepBlock = (LinearLayout) findViewById(detail_BeroepBlock);
        mWerkgewerBlock = (LinearLayout) findViewById(detail_WerkgewerBlock);
        mSelfooonIcon = (ImageView) findViewById(detail_selfoon_icon);
        mTelefoonIcon = (ImageView) findViewById(detail_landlyn_icon);
        mEposIcon = (ImageView) findViewById(detail_email_icon);
        mSmsIcon = (ImageView) findViewById(detail_sms_icon);
        mWhatsappIcon = (ImageView) findViewById(detail_whatsapp_icon);
        mKontakFoto = (ImageView) findViewById(detail_kontak_foto);

        mKontakFoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final PopupMenu popuppic = new PopupMenu(lidmaat_detail_Activity.this, view);
                //final Menu m = popup.getMenu();
                MenuInflater inflater = popuppic.getMenuInflater();
                inflater.inflate(R.menu.image_popup, popuppic.getMenu());
                popuppic.getMenu().findItem(R.id.whatsapp_foto).setVisible(false);
                String cell = mSelfoonTextView.getText().toString();
                if (MainActivity2.whatsappContacts.isEmpty()) {
                    if (cell.length() == 10) {
                        if (MainActivity2.whatsappContacts.contains(cell.substring(1, 10))) {
//                            popuppic.getMenu().findItem(R.id.whatsapp_foto).setVisible(true);
                        }
                    }
                    if (cell.length() == 12) {
                        if (MainActivity2.whatsappContacts.contains(cell.substring(3, 12))) {
//                            popuppic.getMenu().findItem(R.id.whatsapp_foto).setVisible(true);
                        }
                    }
                }
                setForceShowIcon(popuppic);
                popuppic.show();
                 //   openImageChooser();
                popuppic.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        int id = item.getItemId();

                        if (id == R.id.kamera_foto) {
                            kamera();
                            return false;
                        } else if (id == R.id.gallery_foto) {
                            openImageChooser();
                            return false;
                        }
                        return true;
                    }
                });
            }
        });

        mNameTextView.setEnabled(false);
        mVanTextView.setEnabled(false);
        mNooiensVanTextView.setEnabled(false);
        mVolleNameTextView.setEnabled(false);
        mSelfoonTextView.setEnabled(false);
        mTelefoonTextView.setEnabled(false);
        mWykTextView.setEnabled(false);
        mGeboortedatumTextView.setEnabled(false);
        mStraatadresTextView.setEnabled(false);
        mPosadresTextView.setEnabled(false);
        mEposTextView.setEnabled(false);
        //mBeroepTextView.setEnabled(false);
        mLidmaatstatusTextView.setEnabled(false);
        mWerkgewerTextView.setEnabled(false);

        mGesinTextViewBlock.setVisibility(View.GONE);

        mWysigButton = (Button) findViewById(R.id.buttonWysig);
        mWysigButton.setFocusable(true);
        mWysigButton.setClickable(true);
        mWysigButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {

                 if (mWysigButton.getText().equals("Wysig")) {
                     mSelfoonBlock.setVisibility(View.VISIBLE);
                     mTelefoonBlock.setVisibility(View.VISIBLE);
                     mStraatadresBlock.setVisibility(View.VISIBLE);
                     mPosadresBlock.setVisibility(View.VISIBLE);
                     mEposBlock.setVisibility(View.VISIBLE);
                     mNooiensvanBlock.setVisibility(View.VISIBLE);
                     mBeroepBlock.setVisibility(View.VISIBLE);
                     mNameTextView.setEnabled(true);
                     mVanTextView.setEnabled(true);
                     mNooiensVanTextView.setEnabled(true);
                     mVolleNameTextView.setEnabled(true);
                     mSelfoonTextView.setEnabled(true);
                     mTelefoonTextView.setEnabled(true);
                     mWykTextView.setEnabled(true);
                     mGeboortedatumTextView.setEnabled(true);
                     mStraatadresTextView.setEnabled(true);
                     mPosadresTextView.setEnabled(true);
                     mEposTextView.setEnabled(true);
                     mBeroepTextView.setEnabled(true);
                     mWerkgewerTextView.setEnabled(true);
                     mHuwelikstatusSpinner.setEnabled(true);
                     mGeslagSpinner.setEnabled(true);
                     mLidmaatstatusTextView.setEnabled(true);
                     mWysigButton.setText(getResources().getString(R.string.stoor));
                     mStraatAdres = mStraatadresTextView.getText().toString();
                     mPosAdres = mPosadresTextView.getText().toString();
                     mWysigButton.setBackgroundColor(Color.RED);
                     showSoftKeyboard(view);
                 } else {
                     mNameTextView.setEnabled(false);
                     mVanTextView.setEnabled(false);
                     mNooiensVanTextView.setEnabled(false);
                     mVolleNameTextView.setEnabled(false);
                     mSelfoonTextView.setEnabled(false);
                     mTelefoonTextView.setEnabled(false);
                     mWykTextView.setEnabled(false);
                     mGeboortedatumTextView.setEnabled(false);
                     mStraatadresTextView.setEnabled(false);
                     mPosadresTextView.setEnabled(false);
                     mEposTextView.setEnabled(false);
                     //mBeroepTextView.setEnabled(false);
                     mWerkgewerTextView.setEnabled(false);
                     mWysigButton.setText(getResources().getString(R.string.wysig));
                     mWysigButton.setBackgroundColor(Color.parseColor("#0A064F"));
                     mHuwelikstatusSpinner.setEnabled(false);
                     mGeslagSpinner.setEnabled(false);
                     mLidmaatstatusTextView.setEnabled(false);
                     wysigLidmaatData(mCursor);
                     hideSoftKeyboard();
                 }


            }
        });
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                for (int lo = 0; lo < 9; lo++) {
                    getLoaderManager().destroyLoader(lo);
                }
                finish();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        // Loading data, setting up query
        String selection = "";
        String[] projection = {""};

        switch (i) {
            case EXISTING_LIDMAAT_LOADER:
                selection = "Select _rowid_ AS _id, * " + //SELECTION_LIDMAAT_DETAIL +
                            " FROM " + SELECTION_LIDMAAT_FROM +

                            " WHERE (" + LIDMATE_TABLE_NAME + "." + LIDMATE_REKORDSTATUS + " = " + winkerkEntry.RECORDSTATUS + ") AND (" + LIDMATE_TABLE_NAME + "." + "_rowid_ = " + ContentUris.parseId(mCurrentLidmaatUri) + ") ";
                LIDMAAT_IN_USE = String.valueOf(ContentUris.parseId(mCurrentLidmaatUri));
                //projection = {selection};
                // This loader will execute the ContentProvider's query method on a background thread
                return new CursorLoader(this,   // Parent activity context
                        mCurrentLidmaatUri,         // Query the content URI for the current pet
                        projection,             // Columns to include in the resulting Cursor
                        selection,                   // No selection clause
                        null,                   // No selection arguments
                        null);                  // Default sort order
            case GESIN_LOADER:
                Uri mGesinUri = ContentUris.withAppendedId(winkerkEntry.CONTENT_GESIN_URI, 0);
                selection = "SELECT _rowid_ AS _id, " + LIDMATE_TABLE_NAME + "." + LIDMATE_VAN + ", " + LIDMATE_TABLE_NAME + "." + LIDMATE_NOEMNAAM + ", "
                                           + LIDMATE_TABLE_NAME + "." + LIDMATE_GEBOORTEDATUM + ", " + LIDMATE_TABLE_NAME + "." + LIDMATE_PICTUREPATH +
                            " FROM " + LIDMATE_TABLE_NAME +
                            " WHERE (" + LIDMATE_TABLE_NAME + "." + LIDMATE_REKORDSTATUS + " = " + winkerkEntry.RECORDSTATUS + ") AND (members.FamilyHeadGUID = \"" + winkerkEntry.GESINNGUID + "\") ORDER BY Gesinsrol ASC;";

                return new CursorLoader(lidmaat_detail_Activity.this,   // Parent activity context
                        mGesinUri,         // Query the content URI for the current pet
                        projection,             // Columns to include in the resulting Cursor
                        selection,                   // No selection clause
                        null,                   // No selection arguments
                        null);                  // Default sort order

            case INFO_LOADER:
                String GUID = bundle.getString("GUID");
                selection = "Select " + INFO_LIDMAAT_GUID + " , " + INFO_FOTO_PATH + " , " + INFO_GROUP + " FROM " + winkerkEntry.INFO_TABLENAME +
                            " WHERE " + INFO_LIDMAAT_GUID + " = \"" + GUID + "\";";
                Uri mFotoUri = ContentUris.withAppendedId(winkerkEntry.INFO_LOADER_FOTO_URI, 1);
                return new CursorLoader(lidmaat_detail_Activity.this,   // Parent activity context
                        mFotoUri,         // Query the content URI for the current pet
                        projection,             // Columns to include in the resulting Cursor
                        selection,                   // No selection clause
                        null,                   // No selection arguments
                        null);                  // Default sort order

            case SMS_LOADER:


            default:
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        //Got data now dispaly it
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("dd/MM/yyyy");
        hideSoftKeyboard();
        if (cursor == null || cursor.getCount() < 1) {
            return;
        }
        // Proceed with moving to the first row of the cursor and reading data from it
        // (This should be the only row in the cursor)
        switch (loader.getId()) {
            case INFO_LOADER:
                if ((cursor.getCount() >0)) {//(cursor != null) &&
                    cursor.moveToFirst();
                    int imagePathIndex = cursor.getColumnIndex(INFO_FOTO_PATH);
                    if (!cursor.isNull(imagePathIndex)) {
                        String path = FotoDir + cursor.getString(imagePathIndex);
                        Bitmap bitmap = BitmapFactory.decodeFile(path);
                        mKontakFoto.setImageBitmap(bitmap);
                        mKontakFoto.setTag("loaded");
                    }
                }
                break;

            case EXISTING_LIDMAAT_LOADER: { // Display lidmaat info
                if ((cursor.getCount() == 0)) { //(cursor == null) ||
                    break;
                }
                mCursor = cursor;
                cursor.moveToFirst();
                ImageView mStraatadresImageView = (ImageView) findViewById(detail_straat_icon);
                int current_idIdx = cursor.getColumnIndex("_id");//cursor.getColumnIndex(LIDMATE_LIDMAATGUID);
                int nameColumnIndex = cursor.getColumnIndex(LIDMATE_NOEMNAAM);
                int vanColumnIndex = cursor.getColumnIndex(LIDMATE_VAN);
                int voornameColumnIndex = cursor.getColumnIndex(LIDMATE_VOORNAME);
                int SelfoonColumnIndex = cursor.getColumnIndex(LIDMATE_SELFOON);
                int TelefoonColumnIndex = cursor.getColumnIndex(LIDMATE_LANDLYN);
                int WykColumnIndex = cursor.getColumnIndex(LIDMATE_WYK);
                int GeboortedatumColumnIndex = cursor.getColumnIndex(LIDMATE_GEBOORTEDATUM);
                int StraatadresColumnIndex = cursor.getColumnIndex(LIDMATE_STRAATADRES);
                int PosadresColumnIndex = cursor.getColumnIndex(LIDMATE_POSADRES);
                int ePosColumnIndex = cursor.getColumnIndex(LIDMATE_EPOS);
                int gesinsHoofColumnIndex = cursor.getColumnIndex(LIDMATE_GESINSHOOFGUID);
                int nooiensVanColumnIndex = cursor.getColumnIndex(LIDMATE_NOOIENSVAN);
                int lidmaatGUIDColumnIndex = cursor.getColumnIndex(LIDMATE_LIDMAATGUID);
                int beroepColumnIndex = cursor.getColumnIndex(LIDMATE_BEROEP);
                int werkgewerColumnIndex = cursor.getColumnIndex(LIDMATE_WERKGEWER);
                int geslagColumnIndex = cursor.getColumnIndex((LIDMATE_GESLAG));
                int huwelikStatusColumnIndex = cursor.getColumnIndex((LIDMATE_HUWELIKSTATUS));
                int lidmaatStatusColumnIndex = cursor.getColumnIndex((LIDMATE_LIDMAATSTATUS));
                int bewysColumnIndex = cursor.getColumnIndex(LIDMATE_BEWYSSTATUS);
                winkerkEntry.LIDMAATGUID = cursor.getString(lidmaatGUIDColumnIndex);

                current_id = cursor.getInt(current_idIdx);
                String mName = "";
                String mVan = "";
                String mNooiensVan = "";
                String mSelfoon = "";
                String mTelefoon = "";
                String mWyk = "";
                String bDay = "";
                String mVoorname = "";
                String mStraatadres = "";
                String mPosadres = "";
                String mEpos = "";
                String mBeroep = "";
                String mWerkgewer = "";
                String mLidmaatstatus = "";
                DateTime bDayDT = DateTime.now();
                Years p = null;

                String mGesinshoofGUID;


                mSelfoonBlock.setVisibility(View.VISIBLE);
                mTelefoonBlock.setVisibility(View.VISIBLE);
                mStraatadresBlock.setVisibility(View.VISIBLE);
                mPosadresBlock.setVisibility(View.VISIBLE);
                mEposBlock.setVisibility(View.VISIBLE);
                mNooiensvanBlock.setVisibility(View.GONE);
                mBeroepBlock.setVisibility(View.GONE);
                mWerkgewerBlock.setVisibility(View.GONE);

                //LOAD DATA FROM CURSOR

                mLidmaatGUID = cursor.getString(lidmaatGUIDColumnIndex);
                // Bundle bundle = new Bundle();
                // bundle.putString("GUID", mLidmaatGUID);
                // getLoaderManager().initLoader(INFO_LOADER, bundle, lidmaat_detail_Activity.this);


                int imagePathIndex = cursor.getColumnIndex(LIDMATE_PICTUREPATH);
                if (!cursor.isNull(imagePathIndex) && !cursor.getString(imagePathIndex).isEmpty() && !cursor.getString(imagePathIndex).isBlank()) {
                    final float scale = this.getResources().getDisplayMetrics().density;
                    int pixels = (int) (200 * scale + 0.5f);
                    mKontakFoto.getLayoutParams().height = pixels;
                    mKontakFoto.getLayoutParams().width = pixels;
                    mKontakFoto.requestLayout();
                    String path = FotoDir + cursor.getString(imagePathIndex);
                    File file = new File(path);
                    if (file.exists()) {
                        Bitmap bitmap = BitmapFactory.decodeFile(path);
                        mKontakFoto.setImageBitmap(bitmap);
                        mKontakFoto.setTag("loaded");
                    } else {
                        mKontakFoto.setImageResource(R.drawable.kontaks);
                        pixels = (int) (50 * scale + 0.5f);
                        mKontakFoto.getLayoutParams().height = pixels;
                        mKontakFoto.getLayoutParams().width = pixels;
                        mKontakFoto.requestLayout();
                        mKontakFoto.setTag("default");
                        path = "";
                        ContentValues values = new ContentValues();
                        values.put(INFO_FOTO_PATH, path);
                        values.put(INFO_LIDMAAT_GUID, mLidmaatGUID);
                        values.put(INFO_GROUP, "");

                        Uri currentLidmaat = ContentUris.withAppendedId(WinkerkContract.winkerkEntry.INFO_LOADER_FOTO_URI, 1);
                        if (values.size() > 0) {
                            if (!mKontakFoto.getTag().equals("default")) {
                                int rowsAffected = getContentResolver().update(INFO_LOADER_FOTO_URI, values, INFO_LIDMAAT_GUID + " =?", null);
                            } else {
                                getContentResolver().insert(INFO_LOADER_FOTO_URI, values);
                            }
                        }
                        values.clear();
                        values.put(LIDMATE_PICTUREPATH, path);
                        //values.put(INFO_LIDMAAT_GUID, mLidmaatGUID);
                        long id = Integer.valueOf(LIDMAAT_IN_USE);
                        currentLidmaat = ContentUris.withAppendedId(WinkerkContract.winkerkEntry.CONTENT_URI, id);
                        try {
                            if (values.size() > 0) {
                                int rowsAffected = getContentResolver().update(currentLidmaat, values, LIDMATE_TABLE_NAME + "._rowid =?", new String[]{Integer.toString((int) id)});

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                } else {
                    final float scale = this.getResources().getDisplayMetrics().density;
                    int pixels = (int) (50 * scale + 0.5f);
                    mKontakFoto.setImageResource(R.drawable.kontaks);
                    mKontakFoto.getLayoutParams().height = pixels;
                    mKontakFoto.getLayoutParams().width = pixels;
                    mKontakFoto.requestLayout();
                    mKontakFoto.setTag("default");
                }
                if (!cursor.isNull(nameColumnIndex)) {
                    mName = cursor.getString(nameColumnIndex);
                }
                if (!cursor.isNull(vanColumnIndex)) {
                    mVan = cursor.getString(vanColumnIndex);
                }
                if (!cursor.isNull(voornameColumnIndex)) {
                    mVoorname = cursor.getString(voornameColumnIndex);
                }
                if (!cursor.isNull(nooiensVanColumnIndex)) {
                    mNooiensVan = cursor.getString(nooiensVanColumnIndex);
                    if (mNooiensVan.length() != 0) {
                        mNooiensvanBlock.setVisibility(View.VISIBLE);
                    }
                }
                if (!cursor.isNull(SelfoonColumnIndex) && !cursor.getString(SelfoonColumnIndex).isEmpty() && !cursor.getString(SelfoonColumnIndex).isBlank()) {
                    mSelfoon = fixphonenumber(cursor.getString(SelfoonColumnIndex));
                } else {
                    mSelfoonBlock.setVisibility(View.GONE);
                }
                if (!cursor.isNull(TelefoonColumnIndex) && !cursor.getString(TelefoonColumnIndex).isEmpty() && !cursor.getString(TelefoonColumnIndex).isBlank()) {
                    mTelefoon = fixphonenumber(cursor.getString(TelefoonColumnIndex));
                } else {
                    mTelefoonBlock.setVisibility(View.GONE);
                    if (!cursor.isNull(WykColumnIndex)) {
                        mTelefoon = cursor.getString(WykColumnIndex);
                    }
                }
                if (!cursor.isNull(GeboortedatumColumnIndex)) {
                    bDay = cursor.getString(GeboortedatumColumnIndex);
                }
                p = Years.years(0);
                if (!bDay.isEmpty() && bDay.length()>=10) {
                        bDay = bDay.substring(0, 10);

                        try {
                            bDayDT = parseDate(bDay);//DateTime.parse(bDay, dateTimeFormatter);
                            p = Years.yearsBetween(bDayDT, DateTime.now());
                        } catch (Exception e) {

                        }

                }
                if (!cursor.isNull(StraatadresColumnIndex) &&! cursor.getString(StraatadresColumnIndex).isEmpty() && !cursor.getString(StraatadresColumnIndex).isBlank()) {
                    mStraatadres = cursor.getString(StraatadresColumnIndex).replaceAll("\r\n", ", ")
                            .replaceAll("\r", ", ")
                            .replaceAll("\n", ", ")
                            .replaceAll(", , ", ", ")
                            .replaceAll(",  ,", ", ");
                    mStraatadresTextView.setText(mStraatadres);
                    mStraatadresBlock.setVisibility(View.VISIBLE);
                    mStraatadresTextView.setVisibility(View.VISIBLE);

                    // Clicking no adress will launch map search
                    mStraatadresImageView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            String straatmap = mStraatadresTextView.getText().toString();
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            String clipdata = mNameTextView.getText().toString() + " " + mVanTextView.getText().toString() +
                                    "\r\n" + straatmap;
                            ClipData clip = ClipData.newPlainText("text", clipdata);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(lidmaat_detail_Activity.this, clipdata, Toast.LENGTH_SHORT).show();

                            straatmap = straatmap.replace("\n", "%20")
                                                 .replace("\t", "%20")
                                                 .replace("\r", "%2C")
                                                 .replace(" ", "%20");
                            String mapuri = "geo:0,0?q=" + straatmap;
                            Intent adres_intent = new Intent(Intent.ACTION_VIEW);
                            adres_intent.setData(Uri.parse(mapuri));
                            startActivity(adres_intent);
                        }
                    });
                } else {
                    mStraatadresBlock.setVisibility(View.GONE);
                }
                if (!cursor.isNull(PosadresColumnIndex) && !cursor.getString(PosadresColumnIndex).isEmpty() && !cursor.getString(PosadresColumnIndex).isBlank() ) {
                    mPosadres = cursor.getString(PosadresColumnIndex)
                                         .replaceAll("\r\n", ", ")
                                         .replaceAll("\r", ", ")
                                         .replaceAll("\n", ", ")
                                         .replaceAll(", , ", ", ");
                    mPosadresTextView.setText(mPosadres);
                    mPosadresBlock.setVisibility(View.VISIBLE);
                    mPosadresTextView.setVisibility(View.VISIBLE);
                } else {
                    mPosadresBlock.setVisibility(View.GONE);
                }
                if (!cursor.isNull(ePosColumnIndex) && !cursor.getString(ePosColumnIndex).isEmpty() && !cursor.getString(ePosColumnIndex).isBlank()) {
                    mEpos = cursor.getString(ePosColumnIndex);
                } else {
                    mEposBlock.setVisibility(View.GONE);
                }
                if (!cursor.isNull(WykColumnIndex)) {
                    mWyk = cursor.getString(WykColumnIndex);
                }
                mLidmaatstatusTextView.setBackgroundColor(Color.WHITE);
                if (!cursor.isNull(lidmaatStatusColumnIndex)) {
                    mLidmaatstatus = cursor.getString(lidmaatStatusColumnIndex);
                    if (!cursor.isNull(bewysColumnIndex)) {
                        switch (cursor.getString(bewysColumnIndex)) {
                            case "Ontvang":
                                mLidmaatstatusTextView.setBackgroundColor(Color.WHITE);
                                break;
                            case "Aangevra":
                                mLidmaatstatusTextView.setBackgroundColor(Color.GREEN);
                                break;
                            case "Nie Aangevra":
                                mLidmaatstatusTextView.setBackgroundColor(Color.CYAN);
                                break;
                        }
                    }
                }
                if (!cursor.isNull(beroepColumnIndex) && !cursor.getString(beroepColumnIndex).isEmpty() && !cursor.getString(beroepColumnIndex).isBlank()) {
                    mBeroep = cursor.getString(beroepColumnIndex);
                    mBeroepBlock.setVisibility(View.VISIBLE);
                } else {
                    mBeroepBlock.setVisibility(View.GONE);
                }
                if (!cursor.isNull(werkgewerColumnIndex) && !cursor.getString(werkgewerColumnIndex).isEmpty() && !cursor.getString(werkgewerColumnIndex).isBlank()) {
                    mWerkgewer = cursor.getString(werkgewerColumnIndex);
                    mWerkgewerBlock.setVisibility(View.VISIBLE);
                } else {
                    mWerkgewerBlock.setVisibility(View.GONE);
                }
                //String mGesin = cursor.getString( detail_vollename):};
                if (!cursor.isNull(geslagColumnIndex)) {
                    mGeslagB = cursor.getString(geslagColumnIndex);
                }
                if (!cursor.isNull(huwelikStatusColumnIndex)) {
                    mHuwelikstatus = cursor.getString(huwelikStatusColumnIndex);
                }
                SpinnerAdapter customAdapter = new SpinnerAdapter(getApplicationContext(), geslagPrente, null);//geslagteArray);
                mGeslagSpinner.setAdapter(customAdapter);
                if (mGeslagB.equals("Manlik"))
                    mGeslagSpinner.setSelection(1);

                SpinnerAdapter huwelikStatusAdapter = new SpinnerAdapter(getApplicationContext(), null, huwelikStatusArray);
                mHuwelikstatusSpinner.setAdapter(huwelikStatusAdapter);
                mHuwelikstatusSpinner.setSelection(0);
                for (int position2 = 0; position2 < huwelikStatusAdapter.getCount(); position2++) {
                    if (huwelikStatusArray[position2].equals(mHuwelikstatus)) {
                        mHuwelikstatusSpinner.setSelection(position2);
                    }
                }

                mHuwelikstatusSpinner.setEnabled(false);
                mGeslagSpinner.setEnabled(false);

                // Update the views on the screen with the values from the database
                mBeroepTextView.setText(mBeroep);
                mWerkgewerTextView.setText(mWerkgewer);
                mNameTextView.setText(mName);
                mVanTextView.setText(mVan);
                mNooiensVanTextView.setText(mNooiensVan);
                mVolleNameTextView.setText(mVoorname);
                mStraatadresTextView.setText(mStraatadres.replaceAll("\r\n", ", "));
                mStraatAdres = mStraatadresTextView.getText().toString();
                mPosadresTextView.setText(mPosadres.replaceAll("\r\n", ", "));
                mPosAdres = mPosadresTextView.getText().toString();
                mSelfoonTextView.setText(mSelfoon);
                mTelefoonTextView.setText(mTelefoon);
                mGeboortedatumTextView.setText(bDay);
                mJAreOudTextView.setText("(" + p.getYears() + ")");
                if (p.getYears() < 0) {
                    mGeboortedatumTextView.setText("?");
                    mJAreOudTextView.setText("(?)");
                }
                mWykTextView.setText(mWyk);
                mEposTextView.setText(mEpos);
                mLidmaatstatusTextView.setText(mLidmaatstatus);

                mSelfooonIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String selfoon = mSelfoonTextView.getText().toString();
                        if ((!selfoon.isEmpty())) { //selfoon != null &&
                            Intent intentSel = new Intent(Intent.ACTION_DIAL);
                            intentSel.setData(Uri.parse("tel:" + selfoon));
                            try {
                                startActivity(intentSel);
                            } catch (Exception e) {
                            }
                        }
                    }
                });

                mTelefoonIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String selfoon = mTelefoonTextView.getText().toString();
                        if ((!selfoon.isEmpty())) { //selfoon != null &&
                            Intent intentBel = new Intent(Intent.ACTION_DIAL);
                            intentBel.setData(Uri.parse("tel:" + selfoon));
                            try {
                                startActivity(intentBel);
                            } catch (Exception e) {
                            }
                        }
                    }
                });

                mWhatsappIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (!mSelfoonTextView.getText().toString().isEmpty()) {
                            if (mSelfoonTextView.getText().toString().length() >= 10) {
                                String celln = fixphonenumber(mSelfoonTextView.getText().toString());// "+27" + mSelfoonTextView.getText().toString().substring(mSelfoonTextView.getText().toString().length() - 10);
                                celln = celln.replaceAll("-", "");
                                celln = celln.replaceAll("\\s", "");
                                try {
                                    Uri uri = Uri.parse("smsto: " + celln);
                                    //Timber.e("smsNumber %s", uri.toString());
                                    Intent i = new Intent(Intent.ACTION_SENDTO, uri);
                                    i.setPackage("com.whatsapp");
                                    startActivity(Intent.createChooser(i, ""));

                                } catch (Exception e) {
                                    Toast.makeText(getApplicationContext(), "WhatsApp not Installed", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                });

                mEposIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String emailUrl = mEposTextView.getText().toString();
                        if (emailUrl != null && (!emailUrl.isEmpty())) {
                            Intent request = new Intent(Intent.ACTION_VIEW);
                            request.setData(Uri.parse("mailto:" + emailUrl));
                            try {
                                startActivity(request);
                            } catch (Exception e) {
                            }
                        }
                    }
                });

                mSmsIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String sms = mSelfoonTextView.getText().toString();
                        if (sms != null && (!sms.isEmpty())) {
                            Intent intentSMS = new Intent(Intent.ACTION_VIEW);
                            intentSMS.setType("vnd.android-dir/mms-sms");
                            intentSMS.putExtra("address", sms);
                            try {
                                startActivity(intentSMS);
                            } catch (Exception e) {
                            }
                        }
                    }
                });

                //getLoaderManager().initLoader(MYLPAAL_LOADER, null, this);

                //getLoaderManager().initLoader(GROEPE_LOADER, null, this);

                //getLoaderManager().initLoader(WKR_GROEPE_LOADER, null, this);

                //getLoaderManager().initLoader(MEELEWING_LOADER, null, this);

                // getLoaderManager().initLoader(GAWES_LOADER, null, this);

                //getLoaderManager().initLoader(PASSIE_LOADER, null, this);

                //getLoaderManager().initLoader(SMS_LOADER, null, this);
                String[] phoneNumber = new String[]{mSelfoon};
                Cursor cursor1 = getContentResolver().query(Uri.parse("content://sms/inbox"), new String[]{"_id", "thread_id", "address", "person", "date", "body", "type"}, "address=?", phoneNumber, null);
                StringBuffer msgData = new StringBuffer();
                if (cursor1.moveToFirst()) {
                    do {
                        for (int idx = 0; idx < cursor1.getColumnCount(); idx++) {
                            msgData.append(" " + cursor1.getColumnName(idx) + ":" + cursor1.getString(idx));
                        }
                    } while (cursor1.moveToNext());
                } else {

                    //  edtmessagebody.setText("no message from this contact"+phoneNumber);
                }

                if (!cursor.isNull(gesinsHoofColumnIndex)) {
                    mGesinshoofGUID = cursor.getString(gesinsHoofColumnIndex);
                    if (mGesinshoofGUID != null) { // start cursor LOAD v res van gesin
                        winkerkEntry.GESINNGUID = mGesinshoofGUID;
                        getLoaderManager().initLoader(GESIN_LOADER, null, this);
                    }
                }
// Laai Mylpale - Doop & Belydenis
                String gBDay = "";
                Years gp = null;

                LinearLayout mylpaleBlock = (LinearLayout) findViewById(R.id.detail_mylpaleBlock);
                LinearLayout mylpaleBlock2 = (LinearLayout) findViewById(R.id.detail_mylpaleBlock2);

                int doopDatumColumnIndex = cursor.getColumnIndex(LIDMATE_DOOPDATUM);
                int belydenisDatumColumnIndex = cursor.getColumnIndex(LIDMATE_BELYDENISDATUM);
                int doopLeraarColumnIndex = cursor.getColumnIndex(LIDMATE_DOOPDS);
                int belydenisLeraarColumnIndex = cursor.getColumnIndex(LIDMATE_BELYDENISDS);
                int huweliksDatumColumnIndex = cursor.getColumnIndex(LIDMATE_HUWELIKSDATUM);
                int huweliksStatusColumnIndex = cursor.getColumnIndex(LIDMATE_HUWELIKSTATUS);
                //DOOP
                TextView doopMylpaal = new TextView(this);
                doopMylpaal.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                        LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                TextViewCompat.setTextAppearance(doopMylpaal, android.R.style.TextAppearance_Medium);

                TextView doopMylpaalLeraar = new TextView(this);
                doopMylpaalLeraar.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                        LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                TextViewCompat.setTextAppearance(doopMylpaalLeraar, android.R.style.TextAppearance_Holo_Small);

                TextView doopMylpaalGemeente = new TextView(this);
                doopMylpaalGemeente.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                        LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                TextViewCompat.setTextAppearance(doopMylpaalGemeente, android.R.style.TextAppearance_Holo_Small);
                String mMylpale = "";

                if ((!cursor.isNull(doopDatumColumnIndex)) && (!cursor.getString(doopDatumColumnIndex).isEmpty())) {
                    mylpaleBlock2.setVisibility(View.VISIBLE);
                    gBDay = cursor.getString(doopDatumColumnIndex);
                    //mylpaleBlock.addView(mMylpaal);
                    mMylpale = "Doop" + "\t\t(" + gBDay + ") ";
                    doopMylpaal.setText(mMylpale);
                    mylpaleBlock.addView(doopMylpaal);
                    if (!cursor.isNull(doopLeraarColumnIndex)) {
                        doopMylpaalLeraar.setText(cursor.getString(doopLeraarColumnIndex));
                        mylpaleBlock.addView(doopMylpaalLeraar);
                    }
                }
                //BELYDENIS
                TextView belyMylpaal = new TextView(this);
                belyMylpaal.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                        LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                TextViewCompat.setTextAppearance(belyMylpaal, android.R.style.TextAppearance_Medium);

                TextView belyMylpaalLeraar = new TextView(this);
                belyMylpaalLeraar.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                        LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                TextViewCompat.setTextAppearance(belyMylpaalLeraar, android.R.style.TextAppearance_Holo_Small);

                TextView belyMylpaalGemeente = new TextView(this);
                doopMylpaalGemeente.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                        LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                TextViewCompat.setTextAppearance(belyMylpaalGemeente, android.R.style.TextAppearance_Holo_Small);

                if (!cursor.isNull(belydenisDatumColumnIndex) && (!cursor.getString(belydenisDatumColumnIndex).isEmpty())) {
                    mylpaleBlock2.setVisibility(View.VISIBLE);
                    mylpaleBlock.setVisibility(View.VISIBLE);
                    gBDay = cursor.getString(belydenisDatumColumnIndex);
                    mMylpale = "Belydenis van geloof" + "\t\t(" + gBDay + ") ";

                    belyMylpaal.setText(mMylpale);
                    mylpaleBlock.addView(belyMylpaal);

                    if (!cursor.isNull(belydenisLeraarColumnIndex)) {
                        belyMylpaalLeraar.setText(cursor.getString(belydenisLeraarColumnIndex));
                        mylpaleBlock.addView(belyMylpaalLeraar);
                    }
                }
                    //mMylpaal.setTextColor(ContextCompat.getColor(R.color.blue));
                //HUWELIK
                TextView trouMylpaal = new TextView(this);
                trouMylpaal.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                        LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                TextViewCompat.setTextAppearance(trouMylpaal, android.R.style.TextAppearance_Medium);

                TextView trouMylpaalLeraar = new TextView(this);
                doopMylpaalLeraar.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                        LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                TextViewCompat.setTextAppearance(trouMylpaalLeraar, android.R.style.TextAppearance_Holo_Small);

                TextView trouMylpaalGemeente = new TextView(this);
                doopMylpaalGemeente.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                        LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                TextViewCompat.setTextAppearance(trouMylpaalGemeente, android.R.style.TextAppearance_Holo_Small);

                if ((!cursor.isNull(huweliksDatumColumnIndex)) && (!cursor.getString(huweliksDatumColumnIndex).isEmpty())) {
                    mylpaleBlock2.setVisibility(View.VISIBLE);
                    mylpaleBlock.setVisibility(View.VISIBLE);
                    gBDay = cursor.getString(huweliksDatumColumnIndex);
                    mMylpale = "Huwelik" + "\t\t(" + gBDay + ") ";

                    if (!gBDay.isEmpty()) {
                        gp = Years.years(0);
                        try {
                            DateTime gBDayDT = parseDate(gBDay);//DateTime.parse(gBDay, dateTimeFormatter);
                            gBDay = gBDay;
                            gp = Years.yearsBetween(gBDayDT, DateTime.now());
                        } catch (Exception e) {

                        }
                        mMylpale = mMylpale + "\t\t(" + cursor.getString(huweliksDatumColumnIndex) + " : " + gp.getYears() + " jaar) ";
                    }
                    trouMylpaal.setText(mMylpale);
                    mylpaleBlock.addView(trouMylpaal);
                }
                }
                //mMylpaal.setTextColor(ContextCompat.getColor(R.color.blue));
                // end of cursor Lidmaat_GUID
                break;

            case GESIN_LOADER: // Load lidmaat's familiy

                    if ((cursor != null) && (cursor.getCount() > 1) && (loader.getId() == GESIN_LOADER)) {
                        int _idIdx = cursor.getColumnIndex("_id");
                        int gNaamIdx = cursor.getColumnIndex(LIDMATE_NOEMNAAM);
                        int gVanIdx = cursor.getColumnIndex(LIDMATE_VAN);
                        int gBDayIdx = cursor.getColumnIndex(LIDMATE_GEBOORTEDATUM);
                        int gphotoIdx = cursor.getColumnIndex(LIDMATE_PICTUREPATH);
                        String gesinString = "";

                        mGesinTextViewBlock.setVisibility(View.VISIBLE);
                        cursor.moveToPosition(-1);
                        String gBDay = "";
                        Years gp = null;
                        String gphotoPath = "";

                        int gesinslede = 0;
                        while (cursor.moveToNext()) { // wys al gesins ;ede
                            if (current_id != cursor.getInt(_idIdx)) {
                                gesinslede = gesinslede + 1;
                                gBDay = "";
                                gp = null;
                                if (!cursor.isNull(gBDayIdx)) {
                                    gBDay = cursor.getString(gBDayIdx);
                                }
                                if (!gBDay.isEmpty()) {
                                    gp = Years.years(0);
                                    try {
                                        DateTime gBDayDT = parseDate(gBDay);//DateTime.parse(gBDay, dateTimeFormatter);
                                        gBDay = gBDay;
                                        gp = Years.yearsBetween(gBDayDT, DateTime.now());
                                    } catch (Exception e) {

                                    }

                                }
                                if (!cursor.isNull(gphotoIdx)) {
                                    gphotoPath = cursor.getString(gphotoIdx);
                                }
                                gesinString = "\n" + cursor.getString(gNaamIdx) +
                                        "\t " + cursor.getString(gVanIdx) +
                                        "\t " + gBDay;
                                if(gp.getYears() < 0) {
                                    gesinString = gesinString + "(?)";}
                                else {
                                    gesinString = gesinString + " (" + gp.getYears() + ")";}


                                // Add a View for every family member
                                TextView gesinsLid = new TextView(this);
                                gesinsLid.setText(gesinString);
                                gesinsLid.setId(gesinslede);
                                gesinsLid.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT,
                                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                                TextViewCompat.setTextAppearance(gesinsLid, android.R.style.TextAppearance_Medium);
                                gesinsLid.setPadding(32, 0, 0, 0);


                                LinearLayout innerLinearLayout = new LinearLayout(this);
                                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

                                innerLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
                                innerLinearLayout.setVerticalGravity(Gravity.CENTER_VERTICAL);
                                FrameLayout fotoframe = new FrameLayout(this);

                                ImageView imageView2 = new ImageView(this);
                                imageView2.setLayoutParams(params);
                                imageView2.setScaleType(ImageView.ScaleType.FIT_XY);
                                imageView2.setImageResource(R.drawable.circle_crop);
                                imageView2.getLayoutParams().height = 256;
                                imageView2.getLayoutParams().width = 256;

                                ImageView imageView = new ImageView(this);
                                imageView.setLayoutParams(params);
                                imageView.setScaleType(ImageView.ScaleType.FIT_XY);
                                if (gphotoPath.isEmpty()) {
                                    imageView.setImageResource(R.drawable.clipboard);
                                } else {
                                    File file = new File(CacheDir + gphotoPath);
                                    if (file.exists()) {
                                        Bitmap bitmap = BitmapFactory.decodeFile(CacheDir + gphotoPath);
                                        imageView.setImageBitmap(bitmap);
                                    } else {
                                        imageView.setImageResource(R.drawable.clipboard);
                                    }
                                }
                                imageView.getLayoutParams().height = 256;
                                imageView.getLayoutParams().width = 256;

                                fotoframe.addView(imageView);
                                fotoframe.addView(imageView2);
                                // fotoframe.getLayoutParams().height = 256;
                                // fotoframe.getLayoutParams().width = 256;

                                innerLinearLayout.addView(fotoframe);
                                innerLinearLayout.addView(gesinsLid);

                                //mGesinTextViewBlock.addView(gesinsLid);
                                mGesinTextViewBlock.addView(innerLinearLayout);


                                gesinsLid.setTag(cursor.getInt(_idIdx));
                                gesinsLid.setOnClickListener(new View.OnClickListener() {
                                     @Override
                                     public void onClick(View view) {
                                         int gId = (int) view.getTag();
                                         Intent intent_gesinslidDetail = new Intent(lidmaat_detail_Activity.this, lidmaat_detail_Activity.class);
                                         winkerkEntry.LIDMAATID = gId;
                                         Uri currentLidmaat = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, gId);
                                         intent_gesinslidDetail.setData(currentLidmaat);
                                         startActivity(intent_gesinslidDetail);
                                         finish();
                                     }// OnClick
                                 } // OnClickListner
                                ); // setOnClickListner

                            }
                        } // While gesin Cursor

                        //cursor.close();
                    } // END GESIN LOADER

                getLoaderManager().destroyLoader(GESIN_LOADER);
                break;

            case MYLPAAL_LOADER: // Display mylpaal info



            case MEELEWING_LOADER: // Display meelewing info
                if ((cursor != null) && (cursor.getCount() > 0) && (loader.getId() == MEELEWING_LOADER)) {
                    LinearLayout meelewingBlock = (LinearLayout) findViewById(R.id.detail_meelewingBlock);
                    meelewingBlock.setVisibility(View.VISIBLE);
                    cursor.moveToPosition(-1);
                    String aktiwiteit = "";

                    int aktiwiteitColumnIndex = cursor.getColumnIndex("Beskrywing");
                    while (cursor.moveToNext()) { // wys al gesins ;ede
                        if (!cursor.isNull(aktiwiteitColumnIndex))
                            aktiwiteit = cursor.getString(aktiwiteitColumnIndex);
                        TextView meeLewingTextView = new TextView(this);
                        meeLewingTextView.setText(aktiwiteit);
                        //meeLewingTextView.setId(gesinslede);
                        meeLewingTextView.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                                LinearLayoutCompat.LayoutParams.WRAP_CONTENT,
                                LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                        TextViewCompat.setTextAppearance(meeLewingTextView, android.R.style.TextAppearance_Medium);
                        meeLewingTextView.setPadding(32, 0, 0, 0);
                        meelewingBlock.addView(meeLewingTextView);
                    } // ENS WHILE
                } // END IF
                break;

            case GROEPE_LOADER: // Display groep info
                if ((cursor != null) && (cursor.getCount() > 0) && (loader.getId() == GROEPE_LOADER)) {
                LinearLayout groepeBlockm = (LinearLayout) findViewById(R.id.detail_groepBlockm);
                groepeBlockm.setVisibility(View.VISIBLE);
                LinearLayout groepeBlock = (LinearLayout) findViewById(R.id.detail_groepBlock);
                    cursor.moveToPosition(-1);
                    String mGroepe = "";

                    int groepNaamColumnIndex = cursor.getColumnIndex(GROEPNAAM);
                    int groepAdresColumnIndex = cursor.getColumnIndex(GROEPADRES);
                    int groepRolColumnIndex = cursor.getColumnIndex(GROEPROL);
                    int groepAansluitColumnIndex = cursor.getColumnIndex(GROEP_AANSLUIT);
                    int groepUittreeColumnIndex = cursor.getColumnIndex(GROEP_UITTREE);

                    String groepNaam = "";
                    String groepAdres = "";
                    String groepRol = "";
                    String groepAansluit = "";
                    String groepUittree = "";

                    TextView mGroepTextView = new TextView(this);
                    mGroepTextView.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                            LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                            LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                    TextViewCompat.setTextAppearance(mGroepTextView, android.R.style.TextAppearance_Large);
                    mGroepTextView.setText("Winkerk Groepe:");
                    groepeBlock.addView(mGroepTextView);

                    while (cursor.moveToNext()) { // wys al gesins ;ede
                        if (!cursor.isNull(groepNaamColumnIndex))
                            groepNaam = cursor.getString(groepNaamColumnIndex);
                        if (!cursor.isNull(groepAdresColumnIndex))
                            groepAdres = cursor.getString(groepAdresColumnIndex);
                        if (!cursor.isNull(groepRolColumnIndex))
                            groepRol = cursor.getString(groepRolColumnIndex);
                        if (!cursor.isNull(groepAansluitColumnIndex))
                            groepAansluit = cursor.getString(groepAansluitColumnIndex);
                        if (!cursor.isNull(groepUittreeColumnIndex))
                            groepUittree = cursor.getString(groepUittreeColumnIndex);
                        TextView mGroepTextView2 = new TextView(this);
                        mGroepTextView2.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                                LinearLayoutCompat.LayoutParams.WRAP_CONTENT));

                        TextViewCompat.setTextAppearance(mGroepTextView2, android.R.style.TextAppearance_Medium);
                        mGroepTextView2.setText(groepNaam + " " + groepRol);
                        groepeBlock.addView(mGroepTextView2);
                    }
                }
                getLoaderManager().destroyLoader(GROEPE_LOADER);
                break;

            case WKR_GROEPE_LOADER:
                if ((cursor != null) && (cursor.getCount() > 0) && (loader.getId() == WKR_GROEPE_LOADER)) {
                    LinearLayout groepeBlock2 = (LinearLayout) findViewById(R.id.detail_groepBlock);
                    cursor.moveToPosition(-1);
                    String mGroepe = "";

                    int groepNaamColumnIndex = cursor.getColumnIndex(WKR_GROEPE_NAAM);
                    int groepAdresColumnIndex = cursor.getColumnIndex(WKR_GROEPE_ADRES);
                    int groepRolColumnIndex = cursor.getColumnIndex(WKR_LIDMATE2GROEPE_GROEPROL);

                    String groepNaam = "";
                    String groepAdres = "";
                    String groepRol = "";
                    String groepAansluit = "";
                    String groepUittree = "";

                    TextView mGroepTextView = new TextView(this);
                    mGroepTextView.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                            LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                            LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                    TextViewCompat.setTextAppearance(mGroepTextView, android.R.style.TextAppearance_Large);
                    mGroepTextView.setText("Eie Groepe:");
                    groepeBlock2.addView(mGroepTextView);

                    while (cursor.moveToNext()) { // wys al gesins ;ede
                        if (!cursor.isNull(groepNaamColumnIndex))
                            groepNaam = cursor.getString(groepNaamColumnIndex);
                        if (!cursor.isNull(groepAdresColumnIndex))
                            groepAdres = cursor.getString(groepAdresColumnIndex);
                        if (!cursor.isNull(groepRolColumnIndex))
                            groepRol = cursor.getString(groepRolColumnIndex);
                        TextView mGroepTextView2 = new TextView(this);
                        mGroepTextView2.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                                LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                        TextViewCompat.setTextAppearance(mGroepTextView2, android.R.style.TextAppearance_Medium);
                        mGroepTextView2.setText(groepNaam + " " + groepRol);
                        groepeBlock2.addView(mGroepTextView2);
                    }
                    getLoaderManager().destroyLoader(WKR_GROEPE_LOADER);
                }
                break;

            case GAWES_LOADER: // Display Gawes info
                if ((cursor != null) && (cursor.getCount() > 0) && (loader.getId() == GAWES_LOADER)) {
                    LinearLayout gawesBlock = (LinearLayout) findViewById(R.id.detail_gawesBlock);
                    gawesBlock.setVisibility(View.VISIBLE);
                    cursor.moveToPosition(-1);
                    String gawe = "";

                    int aktiwiteitColumnIndex = cursor.getColumnIndex("Beskrywing");
                    while (cursor.moveToNext()) { // wys al gesins ;ede
                        if (!cursor.isNull(aktiwiteitColumnIndex))
                            gawe = cursor.getString(aktiwiteitColumnIndex);
                        TextView gawesTextView = new TextView(this);
                        gawesTextView.setText(gawe);
                        //meeLewingTextView.setId(gesinslede);
                        gawesTextView.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                                LinearLayoutCompat.LayoutParams.WRAP_CONTENT,
                                LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                        TextViewCompat.setTextAppearance(gawesTextView, android.R.style.TextAppearance_Medium);
                        gawesTextView.setPadding(32, 0, 0, 0);
                        gawesBlock.addView(gawesTextView);
                    } // ENS WHILE
                } // END IF
                break;

            case PASSIE_LOADER: // Display Gawes info
                if ((cursor != null) && (cursor.getCount() > 0) && (loader.getId() == PASSIE_LOADER)) {
                    LinearLayout passieBlock = (LinearLayout) findViewById(R.id.detail_passieBlock);
                    passieBlock.setVisibility(View.VISIBLE);
                    cursor.moveToPosition(-1);
                    String gawe = "";

                    int aktiwiteitColumnIndex = cursor.getColumnIndex("Beskrywing");
                    while (cursor.moveToNext()) { // wys al gesins ;ede
                        if (!cursor.isNull(aktiwiteitColumnIndex))
                            gawe = cursor.getString(aktiwiteitColumnIndex);
                        TextView passieTextView = new TextView(this);
                        passieTextView.setText(gawe);
                        //meeLewingTextView.setId(gesinslede);
                        passieTextView.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                                LinearLayoutCompat.LayoutParams.WRAP_CONTENT,
                                LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
                        TextViewCompat.setTextAppearance(passieTextView, android.R.style.TextAppearance_Medium);
                        passieTextView.setPadding(32, 0, 0, 0);
                        passieBlock.addView(passieTextView);
                    } // ENS WHILE
                } // END IF
                break;
        }
        hideSoftKeyboard();
    }


    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // If the loader is invalidated, clear out all the data from the input fields.

    }

    private void wysigLidmaatData(Cursor cursor) {
        int current_idIdx = cursor.getColumnIndex("_id");
        int nameColumnIndex = cursor.getColumnIndex(LIDMATE_NOEMNAAM);
        int vanColumnIndex = cursor.getColumnIndex(LIDMATE_VAN);
        int voornameColumnIndex = cursor.getColumnIndex(LIDMATE_VOORNAME);
        int SelfoonColumnIndex = cursor.getColumnIndex(LIDMATE_SELFOON);
        int TelefoonColumnIndex = cursor.getColumnIndex(LIDMATE_LANDLYN);
        int WykColumnIndex = cursor.getColumnIndex(LIDMATE_WYK);
        int GeboortedatumColumnIndex = cursor.getColumnIndex(LIDMATE_GEBOORTEDATUM);
        int StraatadresColumnIndex = cursor.getColumnIndex(LIDMATE_STRAATADRES);
        int PosadresColumnIndex = cursor.getColumnIndex(LIDMATE_POSADRES);
        int ePosColumnIndex = cursor.getColumnIndex(LIDMATE_EPOS);
        int gesinsHoofColumnIndex = cursor.getColumnIndex(LIDMATE_GESINSHOOFGUID);
        int nooiensVanColumnIndex = cursor.getColumnIndex(LIDMATE_NOOIENSVAN);
        int lidmaatGUIDColumnIndex = cursor.getColumnIndex(LIDMATE_LIDMAATGUID);
        int beroepColumnIndex = cursor.getColumnIndex(LIDMATE_BEROEP);
        int werkgewerColumnIndex = cursor.getColumnIndex(LIDMATE_WERKGEWER);
        int geslagColumnIndex = cursor.getColumnIndex(LIDMATE_GESLAG);
        int huwelikStatusColumnIndex = cursor.getColumnIndex(LIDMATE_HUWELIKSTATUS);
        int lidmaatstatusColumnIndex = cursor.getColumnIndex(LIDMATE_LIDMAATSTATUS);
        int adresColumnIndex = cursor.getColumnIndex(LIDMATE_STRAATADRES);
        winkerkEntry.LIDMAATGUID = cursor.getString(lidmaatGUIDColumnIndex);
        //Soek gewysigde data en laai dit!!
        ContentValues values = new ContentValues();
        String valuesAdres = "";
        String emailBoodskap = "<html>";
        String emailText = "";

        String subject = "Opdateer asb Winkerkdata van Lidmaat: " + cursor.getString(voornameColumnIndex) + " " + cursor.getString(vanColumnIndex);
        emailBoodskap = "<p>Wyk: " + cursor.getString(WykColumnIndex) +  "\r\nGeboortedatum: "+ cursor.getString(GeboortedatumColumnIndex)+ "</p>";
        emailText = subject + "\r\nWyk: " + cursor.getString(WykColumnIndex) +  "\r\nGeboortedatum: "+ cursor.getString(GeboortedatumColumnIndex);

        if (!mNameTextView.getText().toString().equals(mCursor.getString(nameColumnIndex))) {
            values.put(LIDMATE_NOEMNAAM, mNameTextView.getText().toString());
            emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_NOEMNAAM + " : <b><font color = red>" + mNameTextView.getText().toString() + "</b><font color = black></p>";
            emailText = emailText + "\r\n" + LIDMATE_NOEMNAAM + ": " + mNameTextView.getText().toString();
        }
        if (!mVanTextView.getText().toString().equals(mCursor.getString(vanColumnIndex))) {
            values.put(LIDMATE_VAN, mVanTextView.getText().toString());
            emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_VAN + " : <b><font color = red>" + mVanTextView.getText().toString() + "</b><font color = black></p>";
            emailText = emailText + "\r\n" + LIDMATE_VAN + " : " + mVanTextView.getText().toString();
        }
        if (!mVolleNameTextView.getText().toString().equals(mCursor.getString(voornameColumnIndex))) {
            values.put(LIDMATE_VOORNAME, mVolleNameTextView.getText().toString());
            emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_VOORNAME + " : <b><font color = red>" + mVolleNameTextView.getText().toString() + "</b><font color = black></p>";
            emailText = emailText + "\r\n" + LIDMATE_VOORNAME + " : " + mVolleNameTextView.getText().toString();
        }
        if ((!mSelfoonTextView.getText().toString().isEmpty())) {
            if ((cursor.isNull(SelfoonColumnIndex)) || (!mSelfoonTextView.getText().toString().equals(mCursor.getString(SelfoonColumnIndex)))) {
                values.put(LIDMATE_SELFOON, mSelfoonTextView.getText().toString());
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_SELFOON + " : <b><font color = red>" + mSelfoonTextView.getText().toString() + "</b><font color = black></p>";
                emailText = emailText + "\r\n" + LIDMATE_SELFOON + " : " + mSelfoonTextView.getText().toString();
            }
        }
        if ((!mTelefoonTextView.getText().toString().isEmpty())) {
            if ((cursor.isNull(TelefoonColumnIndex)) || (!mTelefoonTextView.getText().toString().equals(mCursor.getString(TelefoonColumnIndex)))) {
                valuesAdres = valuesAdres + LIDMATE_LANDLYN + " = \"" + mTelefoonTextView.getText().toString() + "\"";
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_LANDLYN + " : <b><font color = red>" + mTelefoonTextView.getText().toString() + "</b><font color = black></p>";
                emailText = emailText + "\r\n" + LIDMATE_LANDLYN + " : " + mTelefoonTextView.getText().toString();
            }
        }
        if (!mWykTextView.getText().toString().isEmpty()){
            if ((cursor.isNull(WykColumnIndex)) || (!mWykTextView.getText().toString().equals(mCursor.getString(WykColumnIndex)))) {
                values.put(LIDMATE_WYK, mWykTextView.getText().toString());
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_WYK + " : <b><font color = red>" + mWykTextView.getText().toString() + "</b><font color = black></p>";
                emailText = emailText + "\r\n" + LIDMATE_WYK + " : " + mWykTextView.getText().toString();
            }
        }
        if ((!mLidmaatstatusTextView.getText().toString().isEmpty())) {
            if ((cursor.isNull(lidmaatstatusColumnIndex)) || (!mLidmaatstatusTextView.getText().toString().equals(mCursor.getString(lidmaatstatusColumnIndex)))) {
                values.put(LIDMATE_LIDMAATSTATUS, mLidmaatstatusTextView.getText().toString());
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_LIDMAATSTATUS + " : <b><font color = red>" + mLidmaatstatusTextView.getText().toString() + "</b><font color = black></p>";
                emailText = emailText + "\r\n" + LIDMATE_LIDMAATSTATUS + " : " + mLidmaatstatusTextView.getText().toString();
            }
        }
        if (!mGeboortedatumTextView.getText().toString().equals(mCursor.getString(GeboortedatumColumnIndex).substring(0, 10))) {
            //DateTimeFormatter dateForm = DateTimeFormat.forPattern("dd/MM/yyyy");
            //DateTime convertedDate = new DateTime();
            //convertedDate = dateForm.parseDateTime(mGeboortedatumTextView.getText().toString());
            values.put(LIDMATE_GEBOORTEDATUM, mGeboortedatumTextView.getText().toString()); // convertedDate.toString()); 25-5-2025
            emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_GEBOORTEDATUM + " : <b><font color = red>" + mGeboortedatumTextView.getText().toString() + "</b><font color = black></p>";
            emailText = emailText + "\r\n" + LIDMATE_GEBOORTEDATUM + " : " + mGeboortedatumTextView.getText().toString();
        }

        if (!mStraatadresTextView.getText().toString().equals(mStraatAdres)) {
            if ((mStraatadresTextView.getText().toString().isEmpty()) | (mStraatadresTextView.getText().toString().equals("\n"))) {
                if (!valuesAdres.isEmpty()) {
                    valuesAdres = valuesAdres + ", ";
                }
                valuesAdres = valuesAdres + LIDMATE_STRAATADRES + " = \"\"";
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_STRAATADRES + " : <b><font color = red>\"\"</b><font color = black></p>";
                emailText = emailText + "\r\n" + LIDMATE_STRAATADRES + " : ";
                mStraatAdres = mStraatadresTextView.getText().toString();
            } else {
                if (mStraatadresTextView.getText().toString().charAt(0) == '\n') {
                    if (!valuesAdres.isEmpty()) {
                        valuesAdres = valuesAdres + ", ";
                        emailText = emailText + "\r\n" + ", ";
                    }
                    valuesAdres = valuesAdres + LIDMATE_STRAATADRES + " = \"" + mStraatadresTextView.getText().toString().substring(1, mStraatadresTextView.getText().length() - 1) + "\"";
                    emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_STRAATADRES + " : <b><font color = red>" + mStraatadresTextView.getText().toString() + "</b><font color = black></p>";
                    emailText = emailText + "\r\n" + LIDMATE_STRAATADRES + " : " + mStraatadresTextView.getText().toString();
                    mStraatAdres = mStraatadresTextView.getText().toString();
                } else {
                    valuesAdres = valuesAdres + LIDMATE_STRAATADRES + " = \"" + mStraatadresTextView.getText().toString() + "\"";
                    emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_STRAATADRES + " : <b><font color = red>" + mStraatadresTextView.getText().toString() + "</b><font color = black></p>";
                    emailText = emailText + "\r\n" + LIDMATE_STRAATADRES + " : " + mStraatadresTextView.getText().toString();
                    mStraatAdres = mStraatadresTextView.getText().toString();
                }
            }
        }

        if (!mPosadresTextView.getText().toString().equals(mPosAdres)) {
            if ((mPosadresTextView.getText().toString().isEmpty()) | (mPosadresTextView.getText().toString().equals("\n"))) {
                if (!valuesAdres.isEmpty()) {
                    valuesAdres = valuesAdres + ", ";
                }
                valuesAdres = valuesAdres + LIDMATE_POSADRES + " = \"\"";
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_POSADRES + " : <b><font color = red>\"\"</b><font color = black></p>";
                emailText = emailText + LIDMATE_POSADRES + " : ";
            } else if ((mPosadresTextView.getText().toString().charAt(0) == '\n') & (mPosadresTextView.getText().toString().length() > 1)) {
                if (!valuesAdres.isEmpty()) {
                    valuesAdres = valuesAdres + ", ";
                    emailText = emailText + ", ";
                }
                valuesAdres = valuesAdres + LIDMATE_POSADRES + " = \"" + mPosadresTextView.getText().toString().substring(1, mPosadresTextView.getText().length() - 1) + "\"";
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_POSADRES + " : <b><font color = red>" + mPosadresTextView.getText().toString() + "</b><font color = black></p>";
                emailText = emailText + "\r\n" + LIDMATE_POSADRES + " : " + mPosadresTextView.getText().toString();
            } else {
                if (!valuesAdres.isEmpty()) {
                    valuesAdres = valuesAdres + ", ";
                }
                valuesAdres = valuesAdres + LIDMATE_POSADRES + " = \"" + mPosadresTextView.getText().toString() + "\"";
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_POSADRES + " : <b><font color = red>" + mPosadresTextView.getText().toString() + "</b><font color = black></p>";
                emailText = emailText + "\r\n" + LIDMATE_POSADRES + " : " + mPosadresTextView.getText().toString();
            }
        }

        if ((!mEposTextView.getText().toString().isEmpty())){
            if ((cursor.isNull(ePosColumnIndex)) || (!mEposTextView.getText().toString().equals(mCursor.getString(ePosColumnIndex)))){
                values.put(LIDMATE_EPOS, mEposTextView.getText().toString());
                emailBoodskap = emailBoodskap + "<p>" + LIDMATE_EPOS + " : <b><font color = red>" + mEposTextView.getText().toString() + "</b><font color = black></p>";
                emailText = emailText + "\r\n" + LIDMATE_EPOS + " : " + mEposTextView.getText().toString();
            }
        }
        if ((!mNooiensVanTextView.getText().toString().isEmpty())) {
            if ((cursor.isNull(nooiensVanColumnIndex)) || (!mNooiensVanTextView.getText().toString().equals(mCursor.getString(nooiensVanColumnIndex)))) {
                values.put(LIDMATE_NOOIENSVAN, mNooiensVanTextView.getText().toString());
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_NOOIENSVAN + " : <b><font color = red>" + mNooiensVanTextView.getText().toString() + "</b><font color = black></p>";
                emailText = emailText + "\r\n" + LIDMATE_NOOIENSVAN + " : " + mNooiensVanTextView.getText().toString();
            }
        }
        if ((!mBeroepTextView.getText().toString().isEmpty())) {
            if ((cursor.isNull(beroepColumnIndex)) || (!mBeroepTextView.getText().toString().equals(mCursor.getString(beroepColumnIndex)))) {
                values.put(LIDMATE_BEROEP, mBeroepTextView.getText().toString());
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_BEROEP + " : <b><font color = red>" + mBeroepTextView.getText().toString() + "</b><font color = black></p>";
                emailText = emailText + "\r\n" + LIDMATE_BEROEP + " : " + mBeroepTextView.getText().toString();
            }
        }
        if ((!mWerkgewerTextView.getText().toString().isEmpty())) {
            if ((cursor.isNull(beroepColumnIndex)) || (!mWerkgewerTextView.getText().toString().equals(mCursor.getString(werkgewerColumnIndex)))) {
                values.put(LIDMATE_WERKGEWER, mWerkgewerTextView.getText().toString());
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_WERKGEWER + " : <b><font color = red>" + mWerkgewerTextView.getText().toString() + "</b><font color = black></p>";
                emailText = emailText + "\r\n" + LIDMATE_WERKGEWER + " : " + mWerkgewerTextView.getText().toString();
            }
        }
        int huwPos = mHuwelikstatusSpinner.getSelectedItemPosition();
            String mHuwelikstatus = huwelikStatusArray[huwPos];
            if (!mHuwelikstatus.equals(cursor.getString(huwelikStatusColumnIndex))) {
                values.put(LIDMATE_HUWELIKSTATUS, mHuwelikstatus);
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_HUWELIKSTATUS + " : <b><font color = red>" + mHuwelikstatus + "</b><font color = black></p>";
                emailText = emailText + "\r\n" +  LIDMATE_HUWELIKSTATUS + " : " + mHuwelikstatus;
            }

            String mGeslag = geslagteArray[mGeslagSpinner.getSelectedItemPosition()];
            if (!mGeslag.equals(mGeslagB)) {
                values.put(LIDMATE_GESLAG, mGeslag);
                emailBoodskap = emailBoodskap + "\r\n<p>" + LIDMATE_GESLAG + " : <b><font color = red>" + mGeslag + "</b><font color = black></p>";
                emailText = emailText + "\r\n" + LIDMATE_GESLAG + " : " + mGeslag;
            }

        if (!mStraatAdres.isEmpty()) {
            values.put(LIDMATE_STRAATADRES, mStraatAdres);
            //int rowsAffected2 = getContentResolver().update(currentAdres, values, "UPDATE " + ADRESSE_TABLENAME + " SET " + valuesAdres + " WHERE quote(" + ADRESSE_ADRESGUID + ") = \"" + cursor.getString(adresGuidColumnIndex) + "\"", null);
        }

 //   Toast.makeText(getApplicationContext(),values.toString() ,Toast.LENGTH_LONG).show();
    Uri currentLidmaat = ContentUris.withAppendedId(WinkerkContract.winkerkEntry.CONTENT_URI, cursor.getInt(current_idIdx));
    if (values.size() > 0){
        int rowsAffected = getContentResolver().update(currentLidmaat, values, LIDMATE_TABLE_NAME+"._rowid_ =?", new String[]{ Integer.toString(cursor.getInt(current_idIdx)) });}

   // Uri currentAdres = ContentUris.withAppendedId(winkerkEntry.CONTENT_ADRES_URI, 1);
/**    if (!valuesAdres.isEmpty()) {
        String selection = " quote(" + ADRESSE_ADRESGUID + ")=\"?\"";
        String[] arg = new String[]{cursor.getString(adresGuidColumnIndex)};
        if (values.size() == 0) {
            values.put("temp", "1");
        }
        int rowsAffected2 = getContentResolver().update(currentAdres, values, "UPDATE " + ADRESSE_TABLENAME + " SET " + valuesAdres + " WHERE quote(" + ADRESSE_ADRESGUID + ") = \"" + cursor.getString(adresGuidColumnIndex) + "\"", null);
    }
**/
    emailBoodskap = emailBoodskap + "</html>";
    int gemeenteColumnIndex = cursor.getColumnIndex(LIDMATE_GEMEENTE);
        String emailUrl = "";
        if ((gemeenteColumnIndex != -1) && (!cursor.isNull(gemeenteColumnIndex))) {
            String gemeente = cursor.getString(gemeenteColumnIndex);

            if (gemeente.equals(GEMEENTE_NAAM)) {
                emailUrl = winkerkEntry.GEMEENTE_EPOS;
            }
            if (gemeente.equals(GEMEENTE2_NAAM)) {
                emailUrl = winkerkEntry.GEMEENTE2_EPOS;
            }
            if (gemeente.equals(GEMEENTE3_NAAM)) {
                emailUrl = winkerkEntry.GEMEENTE3_EPOS;
            }
        }
    Spanned html;// = Html.fromHtml(emailBoodskap, Html.FROM_HTML_MODE_LEGACY);
    if ((values.size() != 0) | (values.size() > 0)) {
        Intent sendIntent = new Intent(Intent.ACTION_SENDTO);
        sendIntent.setData(Uri.parse("mailto:"));
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            html = Html.fromHtml(subject + "\r\n" + emailBoodskap,Html.FROM_HTML_MODE_LEGACY);
        } else {
            html = Html.fromHtml(subject + "\r\n" + emailBoodskap);
        }


        final SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        EPOSHTML = settings.getBoolean("EposHtml", false);


        if (EPOSHTML) {
            //sendIntent.setType("text/html");
            //sendIntent.putExtra(Intent.EXTRA_HTML_TEXT, html);}
            sendIntent.putExtra(Intent.EXTRA_TEXT,  html);}
            else {
                //sendIntent.setType("text/plain");
                sendIntent.putExtra(Intent.EXTRA_TEXT,  emailText);
                //sendIntent.putExtra(Intent.EXTRA_HTML_TEXT,  "");
            }//


        //sendIntent.setData(Uri.parse("mailto:"+emailUrl));
        sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailUrl});
        try {
            startActivity(sendIntent);
            } catch (Exception e) {
            }
    }
}



    /**
     * Hides the soft keyboard
     */
    private void hideSoftKeyboard() {
        if(getCurrentFocus()!=null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    /**
     * Shows the soft keyboard
     */
    private void showSoftKeyboard(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        view.requestFocus();
        inputMethodManager.showSoftInput(view, 0);
    }

    /* Choose an image from Gallery */
    private void openImageChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        Intent chooser = new Intent(Intent.ACTION_CHOOSER);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);

    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if ((requestCode == SELECT_PICTURE) || (requestCode == CAMERA_REQUEST)) {
                String path = "";
                Uri selectedImageUri;
                // Get the url from data
                if (requestCode == CAMERA_REQUEST) {
                    //Bitmap picture = (Bitmap) data.getExtras().get("data");
                    File imgFile = new File(mCurrentPicUri);
                    mCurrentPicUri = "";
                    selectedImageUri = Uri.fromFile(imgFile);
                    path = imgFile.getPath();

                    if (!path.isEmpty()) {
                        try {
                            Intent CropIntent = new Intent("com.android.camera.action.CROP");
                            //CropIntent.setType("image/*");
                            CropIntent.setDataAndType(selectedImageUri, "image/*");
                            CropIntent.putExtra("crop", "true");
                            CropIntent.putExtra("aspectX", 1);
                            CropIntent.putExtra("aspectY", 1);
                            CropIntent.putExtra("return-data", true);
                            //CropIntent.setAction(Intent.ACTION_GET_CONTENT);
                            startActivityForResult(CropIntent, 147);
                        } catch (ActivityNotFoundException ex) {
                        }
                    }

                } else {
                    selectedImageUri = data.getData();
                    if (null != selectedImageUri) {
                        // Get the path from the Uri
                        path = getPathFromURI(selectedImageUri);

                        // Set the image in ImageView
                        //String realPath;
                        if (path == null) {
                            if (Build.VERSION.SDK_INT < 19)
                                path = RealPathUtil.getRealPathFromURI_API11to18(this, data.getData());

                                // SDK > 19 (Android 4.4)
                            else
                                path = RealPathUtil.getRealPathFromURI_API19(this, data.getData());

                        }
                    }
                    if (!path.isEmpty()) {
                        try {
                            Intent CropIntent = new Intent("com.android.camera.action.CROP");
                            CropIntent.setType("image/*");
                            CropIntent.setData(selectedImageUri);
                            CropIntent.putExtra("crop", "true");
                            CropIntent.putExtra("aspectX", 0);
                            CropIntent.putExtra("aspectY", 0);
                            CropIntent.putExtra("return-data", true);
                            //CropIntent.setAction(Intent.ACTION_GET_CONTENT);
                            startActivityForResult(CropIntent, 147);
                        } catch (ActivityNotFoundException ex) {
                        }
                    }
                }
                Log.i("Winkerkreader", "Image Path : " + path);

                if (!path.isEmpty()) {
                    mKontakFoto.setImageURI(selectedImageUri);
                    final float scale = this.getResources().getDisplayMetrics().density;
                    int pixels = (int) (200 * scale + 0.5f);
                    mKontakFoto.getLayoutParams().height = pixels;
                    mKontakFoto.getLayoutParams().width = pixels;
                    mKontakFoto.requestLayout();


                    path = copyFoto(path, winkerkEntry.LIDMAATGUID);


                    ContentValues values = new ContentValues();
                    values.put(INFO_FOTO_PATH, path);
                    values.put(INFO_LIDMAAT_GUID, mLidmaatGUID);
                    values.put(INFO_GROUP, "");

                    Uri currentLidmaat = ContentUris.withAppendedId(INFO_LOADER_FOTO_URI, 1);
                    if (values.size() > 0) {
                        if (!mKontakFoto.getTag().equals("default")) {
                            int rowsAffected = getContentResolver().update(INFO_LOADER_FOTO_URI, values, INFO_LIDMAAT_GUID + " =?", null);
                        } else {
                            getContentResolver().insert(INFO_LOADER_FOTO_URI, values);
                        }
                    }

                    values.clear();
                    values.put(LIDMATE_PICTUREPATH, path);
                    //values.put(INFO_LIDMAAT_GUID, mLidmaatGUID);
                    long id = Integer.valueOf(LIDMAAT_IN_USE);
                    currentLidmaat = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, id);
                    if (values.size() > 0) {
                        int rowsAffected = getContentResolver().update(currentLidmaat, values, LIDMATE_TABLE_NAME + "._rowid_ =?", new String[]{Integer.toString((int) id)});

                    }
                }
            }
            if (requestCode == 147) {
                // Get the url from data

                Uri selectedImageUri = data.getData();
                String path;
                if (null != selectedImageUri) {
                    // Get the path from the Uri
                    if (selectedImageUri.toString().charAt(0) == 'f') {
                        path = selectedImageUri.toString().substring(8);
                    } else {
                        path = getPathFromURI(selectedImageUri);
                    }

                    // Set the image in ImageView
                    //String realPath;
                    if (path == null) {
                        if (Build.VERSION.SDK_INT < 19)
                            path = RealPathUtil.getRealPathFromURI_API11to18(this, data.getData());

                            // SDK > 19 (Android 4.4)
                        else
                            path = RealPathUtil.getRealPathFromURI_API19(this, data.getData());

                    }
                    Log.i("Winkerkreader", "Image Path : " + path);

                    if (!path.isEmpty()) {

                        if (selectedImageUri.toString().charAt(0) != 'f') {
                            mKontakFoto.setImageURI(selectedImageUri);
                        } else {
                            Bitmap bitmap = BitmapFactory.decodeFile(path);
                            mKontakFoto.setImageBitmap(bitmap);
                        }

                        final float scale = this.getResources().getDisplayMetrics().density;
                        int pixels = (int) (200 * scale + 0.5f);
                        mKontakFoto.getLayoutParams().height = pixels;
                        mKontakFoto.getLayoutParams().width = pixels;
                        mKontakFoto.requestLayout();


                        path = copyFoto(path, winkerkEntry.LIDMAATGUID);


                        ContentValues values = new ContentValues();
                        values.put(INFO_FOTO_PATH, path);
                        values.put(INFO_LIDMAAT_GUID, mLidmaatGUID);
                        values.put(INFO_GROUP, "");

                        Uri currentLidmaat = ContentUris.withAppendedId(INFO_LOADER_FOTO_URI, 1);
                        if (values.size() > 0) {
                            if (!mKontakFoto.getTag().equals("default")) {
                                int rowsAffected = getContentResolver().update(INFO_LOADER_FOTO_URI, values, INFO_LIDMAAT_GUID + " =?", null);
                            } else {
                                getContentResolver().insert(INFO_LOADER_FOTO_URI, values);
                            }
                        }

                        values.clear();
                        values.put(LIDMATE_PICTUREPATH, path);
                        //values.put(INFO_LIDMAAT_GUID, mLidmaatGUID);
                        long id = Integer.valueOf(LIDMAAT_IN_USE);
                        currentLidmaat = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, id);
                        if (values.size() > 0) {
                            int rowsAffected = getContentResolver().update(currentLidmaat, values, LIDMATE_TABLE_NAME + "._rowid_ =?", new String[]{Integer.toString((int) id)});

                        }
                    }
                }
            }
        }
    }

    /* Get the real path from the URI */
    private String getPathFromURI(Uri contentUri) {
        String res = null;
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if ((cursor != null) && (cursor.getCount() > 0)) {
            if (cursor.moveToFirst()) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                res = cursor.getString(column_index);
            }
        }
        cursor.close();
        return res;
    }

private String copyFoto(String path, String GUID) {

    if ( path.isEmpty()) {return "";}
    //Bitmap bitmap = BitmapFactory.decodeFile(path);
    WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    Point size = new Point();
    display.getSize(size);
    int width = THUMBSIZE;//size.x;
    int height = THUMBSIZE;//size.y;
    OutputStream outStream = null;
    Bitmap bitmap;

    File file = new File(CacheDir);
    if(!file.exists()) {
        file.mkdirs();
    }
    file = new File(CacheDir, GUID + ".png");
    if (file.exists()) {
        file.delete();
        file = new File(CacheDir, GUID + ".png");
        Log.e("file exist", file + ", Bitmap= " + GUID);
    }
    try {
        // make a new bitmap from your file
        bitmap = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(path) , width, height);
        outStream = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
        outStream.flush();
        outStream.close();
    } catch (Exception e) {
        e.printStackTrace();
    }
    Log.e("file", "" + file);

    file = new File(FotoDir);
    if(!file.exists()) {
        file.mkdirs();
    }
    file = new File(FotoDir, GUID + ".png");
    if (file.exists()) {
        file.delete();
        file = new File(FotoDir, GUID + ".png");
        Log.e("file exist", file + ", Bitmap= " + GUID);
    }
    try {
        // make a new bitmap from your file
        bitmap = BitmapFactory.decodeFile(path);
        outStream = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.PNG, 75, outStream);
        outStream.flush();
        outStream.close();
    } catch (Exception e) {
        e.printStackTrace();
    }
    Log.e("file", "" + file);
    path = GUID+".png";

    return path;
}

    private static void setForceShowIcon(PopupMenu popupMenu) {
        try {
            Field[] fields = popupMenu.getClass().getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popupMenu);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper
                            .getClass().getName());
                    Method setForceIcons = classPopupHelper.getMethod(
                            "setForceShowIcon", boolean.class);
                    setForceIcons.invoke(menuPopupHelper, true);

                    break;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    public void grabImage(ImageView imageView)
    {
        this.getContentResolver().notifyChange(mImageUri, null);
        ContentResolver cr = this.getContentResolver();
        Bitmap bitmap;
        try
        {
            bitmap = android.provider.MediaStore.Images.Media.getBitmap(cr, mImageUri);
            imageView.setImageBitmap(bitmap);
        }
        catch (Exception e)
        {
            Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show();
        }
    }

    private void kamera(){
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        File photo;
        try
        {
            // place where to store camera taken picture
            photo = this.createTemporaryFile("picture", ".jpg");
            photo.delete();

            mImageUri = Uri.fromFile(photo);
            mCurrentPicUri = mImageUri.getPath();
            Intent intentkamera = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

            intentkamera.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
            //start camera intent
            startActivityForResult(intentkamera, CAMERA_REQUEST);
        }
        catch(Exception e)
        {

            Toast.makeText(this, "Please check SD card! Image shot is impossible!",Toast.LENGTH_LONG).show();

        }
        }

        private File createTemporaryFile(String part, String ext) throws Exception
        {
            File tempDir= Environment.getExternalStorageDirectory();
            tempDir=new File(FotoDir+"/.temp/");
            if(!tempDir.exists())
            {
                tempDir.mkdirs();
            }
            return File.createTempFile(part, ext, tempDir);
        }
    }



