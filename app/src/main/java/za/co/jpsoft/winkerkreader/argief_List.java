package za.co.jpsoft.winkerkreader;

import android.app.LoaderManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
//import android.support.v4.widget.CursorAdapter;
//import android.support.v7.app.AppCompatActivity;
//import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import za.co.jpsoft.winkerkreader.data.WinkerkContract;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static za.co.jpsoft.winkerkreader.R.layout.argief;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.ARGIEF_LOADER;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.ARGIEF_URI;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.ARGIEF_SOEK_LOADER;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.KEUSE;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.CursorAdapter;

/**
 * Created by Pieter Grobler on 23/01/2018.
 */

public class argief_List extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {
    private String argief_ID = "";
    private ListView argiefListView;
    private argiefLysAdapter mCursorAdapter;
    private Menu mMenu;

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        mCursorAdapter.swapCursor(null);
        getLoaderManager().destroyLoader(ARGIEF_LOADER);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(argief);

        argiefListView = (ListView) findViewById(R.id.argief_list);
        getLoaderManager().initLoader(ARGIEF_LOADER, null, this);
        mCursorAdapter = new argiefLysAdapter(this, null);

        argiefListView.setAdapter(mCursorAdapter);// Set database to listview
        argiefListView.setFastScrollEnabled(true);
        argiefListView.setClickable(true);
        RadioButton sortvan = (RadioButton) findViewById(R.id.argief_sort_van);
        RadioButton sortdatum = (RadioButton) findViewById(R.id.argief_sort_datum);
        RadioButton sortrede = (RadioButton) findViewById(R.id.argief_sort_rede);
        switch (KEUSE) {
            case "Van":
                sortvan.setChecked(true);
                break;
            case "Rede":
                sortrede.setChecked(true);
                break;
            case "Datum":
                sortdatum.setChecked(true);
                break;
            default:
                sortvan.setChecked(true);
                KEUSE = "Van";
                break;
        }

        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.argief_sort);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.argief_sort_van) {
                    KEUSE = "Van";
                } else if (checkedId == R.id.argief_sort_datum) {
                    KEUSE = "Datum";
                } else if (checkedId == R.id.argief_sort_rede) {
                    KEUSE = "Rede";
                } else {
                    KEUSE = "Van";
                }
                SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
                settings.edit()
                        .putBoolean("FROM_MENU", true)
                        .apply();

// Refresh current activity instead of starting new instance
                recreate(); // or call a refresh method
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {// Options menu (three dots, top right)
        this.mMenu = menu;
        if (menu.getClass().getSimpleName().equals("MenuBuilder")) {
            try {
                Method m = menu.getClass().getDeclaredMethod(
                        "setOptionalIconsVisible", Boolean.TYPE);
                m.setAccessible(true);
                m.invoke(menu, true);
            } catch (NoSuchMethodException e) {
                //Log.e(TAG, "onMenuOpened", e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.argiefmenu, menu);

        MenuItem searchItem = menu.findItem(R.id.argief_action_search);
        SearchView searchView = (SearchView) menu.findItem(R.id.argief_action_search).getActionView();//MenuItemCompat.getActionView(searchItem);
        searchItem.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM
                | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        searchView.setSubmitButtonEnabled(true);

        if (searchItem != null) {
            searchView = (SearchView) menu.findItem(R.id.argief_action_search).getActionView();//MenuItemCompat.getActionView(searchItem);
            searchView.setOnCloseListener(new SearchView.OnCloseListener() {
                @Override
                public boolean onClose() {
                    Loader<?> loa;

                    return false;

                }
            });
            searchView.setOnSearchClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //some operation
                }

            });

            EditText searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            searchPlate.setHint("Soek");
            View searchPlateView = searchView.findViewById(androidx.appcompat.R.id.search_plate);
            searchPlateView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));


            SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

            // use this method for search process
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    // use this method when query submitted
                    //Toast.makeText(MainActivity2.this, query, Toast.LENGTH_SHORT).show();\
                    WinkerkContract.winkerkEntry.SOEK = query;
                    Loader<?> loa;
                    try {
                        loa = getLoaderManager().getLoader(ARGIEF_SOEK_LOADER);
                        Log.d("WinkerkReader", "LOADER SOEK");

                    } catch (Exception e) {
                        loa = null;
                    }
                    if (loa == null) {
                        getLoaderManager().initLoader(ARGIEF_SOEK_LOADER, null, argief_List.this);
                        Log.d("WinkerkReader", "LOADER SOEK");
                    } else {
                        Log.d("WinkerkReader", "LOADER SOEK");
                        getLoaderManager().restartLoader(ARGIEF_SOEK_LOADER, null, argief_List.this);//??????
                    }
                    //} else {
                    //    Toast.makeText(getApplicationContext(), "Registreer app asb.", Toast.LENGTH_SHORT).show();
                    //}
                    //getLoaderManager().initLoader(SOEK_LOADER, null, MainActivity2.this);
                    if (mMenu != null) {
                        (mMenu.findItem(R.id.argief_action_search)).collapseActionView();
                    }
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    // use this method for auto complete search process\

                    //       winkerkEntry.SOEK = newText;

                    return true;
                }

            });

        }
        return true;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        String selection = "";
        String[] projection = {""};

        switch (id) {
            case ARGIEF_SOEK_LOADER:
                String where = " WHERE (Surname LIKE '%" + WinkerkContract.winkerkEntry.SOEK + "%') OR " +
                        " (Name LIKE '%" + WinkerkContract.winkerkEntry.SOEK + "%') ";

                selection = "Select Argief._rowid_ as _id, * from Argief" + where;
                //SELECTION_MEELEWING_LYS_FROM;._rowid_ as _id,
                return new CursorLoader(this,   // Parent activity context
                        ARGIEF_URI,         // Query the content URI for the current pet
                        projection,             // Columns to include in the resulting Cursor
                        selection,                   // No selection clause
                        null,                   // No selection arguments
                        null);
            case ARGIEF_LOADER:
                selection = "Select Argief._rowid_ as _id, * from Argief";
                if (KEUSE == "Van")
                    selection = selection + " ORDER BY Surname, Name, DepartureDate ";
                if (KEUSE == "Rede")
                    selection = selection + " ORDER BY Reason, Surname, Name, DepartureDate ";
                if (KEUSE == "Datum")
                    selection = selection + " ORDER BY substr(DepartureDate,7,4), substr(DepartureDate,4,2), substr(DepartureDate,1,2), Surname, Name";
                //SELECTION_MEELEWING_LYS_FROM;._rowid_ as _id,
                return new CursorLoader(this,   // Parent activity context
                        ARGIEF_URI,         // Query the content URI for the current pet
                        projection,             // Columns to include in the resulting Cursor
                        selection,                   // No selection clause
                        null,                   // No selection arguments
                        null);
            default:
                return null;
        } // End Switch
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        argiefListView = (ListView) findViewById(R.id.argief_list);
        // Proceed with moving to the first row of the cursor and reading data from it
        // (This should be the only row in the cursor)
        if ((cursor != null) && (cursor.getCount() > 0)) {
            cursor.moveToFirst();
        }
        argiefListView.setVisibility(View.VISIBLE);
        argiefListView.setAdapter(null);
        argiefListView.setAdapter(mCursorAdapter);

        if ((cursor != null) && (cursor.getCount() > 0)) {
            argiefListView.setAdapter(null);
            argiefListView.setAdapter(mCursorAdapter);
            mCursorAdapter.swapCursor(cursor);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

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
}


//===========================================================================================================================================================================

            class argiefLysAdapter extends CursorAdapter {
                public static class ViewHolder {
                    public final TextView GroepNaamView;

                    public final TextView GroepCountView;

                    public ViewHolder(View view) {
                        GroepNaamView = view.findViewById(R.id.GroepNaam);
                        GroepCountView = view.findViewById(R.id.GroepCount);
                    }
                }

                public static class ViewHolder2 {
                    public final TextView redeTextView;
                    public final TextView nameTextView;
                    public final TextView geboortedatumTextView;
                    public final TextView vanTextView;
                    public final TextView bestemmingTextView;
                    public final TextView vertrekTextview;
                    public final TextView separatorView;


                    public ViewHolder2(View view) {
                        nameTextView = view.findViewById(R.id.argief_name);
                        vanTextView = view.findViewById(R.id.argief_van);
                        geboortedatumTextView = view.findViewById(R.id.argief_geboortedatum);
                        redeTextView = view.findViewById(R.id.argief_rede);
                        bestemmingTextView = view.findViewById(R.id.argief_bestemming);
                        vertrekTextview = view.findViewById(R.id.argief_vertrekdatum);
                        separatorView = view.findViewById(R.id.argief_list_separator);
                    }
                }

                public argiefLysAdapter(Context context, Cursor cursor) {
                    super(context, cursor, 0 /* flags */);
                }

                @Override
                public Cursor swapCursor(Cursor newCursor) {
                    Cursor old = super.swapCursor(newCursor);
                    return old;
                }

                @Override
                public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
                    if ((cursor == null) || (cursor.getCount() < 1)) {
                        return null;
                    }
                    View view;
                    view = LayoutInflater.from(context).inflate(R.layout.argief_item, viewGroup, false);//R.layout.list_item_groeplede, viewGroup,false);
                    ViewHolder2 viewHolder = new ViewHolder2(view);
                    view.setTag(viewHolder);
                    return view;

                }

                @Override
                public void bindView(View view, Context context, Cursor cursor) {
                    if ((cursor == null) || (cursor.getCount() < 1)) {
                        return;
                    }

                    ViewHolder2 viewHolder2 = (ViewHolder2) view.getTag();

                    int naamColumnIndex = cursor.getColumnIndex("Name");
                    int vanColumnIndex = cursor.getColumnIndex("Surname");
                    int redeColumnIndex = cursor.getColumnIndex("Reason");
                    int bestemmingColumnIndex = cursor.getColumnIndex("DepartureTo");
                    int geboorteColumnIndex = cursor.getColumnIndex("DateOfBirth");
                    int vertrekDatumColumnIndex = cursor.getColumnIndex("DepartureDate");

                    String lidNaam = "";
                    String lidVan = "";
                    String lidGeboortedatum = "";
                    String vertrekDatum = "";
                    String rede = "";
                    String bestemming = "";

                    lidNaam = cursor.getString(naamColumnIndex);
                    if (!cursor.isNull(vanColumnIndex))
                        lidVan = cursor.getString(vanColumnIndex);
                    if (!cursor.isNull(geboorteColumnIndex))
                        lidGeboortedatum = cursor.getString(geboorteColumnIndex);
                    if (!cursor.isNull(redeColumnIndex))
                        rede = cursor.getString(redeColumnIndex);
                    if (!cursor.isNull(bestemmingColumnIndex))
                        bestemming = cursor.getString(bestemmingColumnIndex);
                    if (!cursor.isNull(vertrekDatumColumnIndex))
                        vertrekDatum = cursor.getString(vertrekDatumColumnIndex);
                    viewHolder2.nameTextView.setText(lidNaam);
                    viewHolder2.vanTextView.setText(lidVan);
                    viewHolder2.geboortedatumTextView.setText(lidGeboortedatum);
                    viewHolder2.redeTextView.setText(rede);
                    viewHolder2.bestemmingTextView.setText(bestemming);
                    viewHolder2.vertrekTextview.setText(vertrekDatum);
                    viewHolder2.separatorView.setText(KEUSE);
                    viewHolder2.separatorView.setVisibility(View.GONE);
                    int position = cursor.getPosition();
                    String current ="";
                    String previous = "";
                    if (position == 0) {
                        viewHolder2.separatorView.setVisibility(View.VISIBLE);
                        switch (KEUSE) {
                            case "Van":
                                current = cursor.getString(vanColumnIndex);
                                viewHolder2.separatorView.setText(KEUSE + " " + current);
                                break;
                            case "Rede":
                                current = cursor.getString(redeColumnIndex);
                                viewHolder2.separatorView.setText(KEUSE + " " + current);
                                break;
                            case "Datum":
                                current = cursor.getString(vertrekDatumColumnIndex);
                                viewHolder2.separatorView.setText(KEUSE + " " + current);
                                break;
                        }
                    }
                    else{

                        switch (KEUSE) {
                            case "Van":
                                current = cursor.getString(vanColumnIndex);
                                break;
                            case "Rede":
                                current = cursor.getString(redeColumnIndex);
                                break;
                            case "Datum":
                                current = cursor.getString(vertrekDatumColumnIndex);
                                break;
                        }
                        cursor.moveToPosition(position - 1);

                        switch (KEUSE) {
                            case "Van":
                                previous = cursor.getString(vanColumnIndex);//cursor.getColumnIndex(winkerkEntry.LIDMATE_WYK));
                                if (previous != null && current != null) {
                                    if (!previous.equals(current)) {
                                        viewHolder2.separatorView.setVisibility(View.VISIBLE);
                                        viewHolder2.separatorView.setText(KEUSE + " " + current);
                                    }
                                }
                                break;
                            case "Rede":
                                previous = cursor.getString(redeColumnIndex);//cursor.getColumnIndex(winkerkEntry.LIDMATE_WYK));
                                if (previous != null && current != null) {
                                    if (!previous.equals(current)) {
                                        viewHolder2.separatorView.setVisibility(View.VISIBLE);
                                        viewHolder2.separatorView.setText(KEUSE + " " + current);
                                    }
                                }
                                break;
                            case "Datum":
                                previous = cursor.getString(vertrekDatumColumnIndex);//cursor.getColumnIndex(winkerkEntry.LIDMATE_WYK));
                                if (previous != null && current != null) {
                                    if (!previous.equals(current)) {
                                        viewHolder2.separatorView.setVisibility(View.VISIBLE);
                                        viewHolder2.separatorView.setText(KEUSE + " " + current);
                                    }
                                }
                                break;
                        }
                    }
                }
            }