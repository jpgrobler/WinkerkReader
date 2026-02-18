package za.co.jpsoft.winkerkreader;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

import static za.co.jpsoft.winkerkreader.MainActivity2.SEARCH_CHECK_BOX;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.ADRESSE_LANDLYN;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_BEROEP;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_EPOS;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_NOOIENSVAN;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_SELFOON;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_STRAATADRES;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_VAN;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_VOORNAME;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_WYK;

/**
 * Created by Pieter Grobler on 06/09/2017.
 */

public class MyActivity_Settings extends ListActivity {

    private ArrayList< SearchCheckBox > settingList;
    private ArrayList<SearchCheckBox> createDefaultSearchList() {
        ArrayList<SearchCheckBox> defaultList = new ArrayList<>();
        defaultList.add(new SearchCheckBox(LIDMATE_VAN,"","Van",true));
        defaultList.add(new SearchCheckBox(LIDMATE_NOEMNAAM,"","Noemnaam",true));
        defaultList.add(new SearchCheckBox(LIDMATE_VOORNAME,"","Voorname",true));
        defaultList.add(new SearchCheckBox(LIDMATE_WYK,"","Wyk",true));
        defaultList.add(new SearchCheckBox(LIDMATE_SELFOON,"","Selfoon",true));
        defaultList.add(new SearchCheckBox(ADRESSE_LANDLYN,"","Landlyn",true));
        defaultList.add(new SearchCheckBox(LIDMATE_NOOIENSVAN,"","Nooiensvan",true));
        defaultList.add(new SearchCheckBox(LIDMATE_BEROEP,"","Beroep",true));
        defaultList.add(new SearchCheckBox(LIDMATE_EPOS,"","Epos",true));
        defaultList.add(new SearchCheckBox(LIDMATE_STRAATADRES,"","Adres",true));
        return defaultList;
    }

    @Override
    public void onCreate ( Bundle savedInstanceState ) {
        super.onCreate(savedInstanceState);
        setContentView ( R.layout.sub_menu );
        setTitle ( "Soek in velde:" );
//        SearchCheckBoxPreferences prefsManager = new SearchCheckBoxPreferences(this);
//        if (savedInstanceState != null && savedInstanceState.containsKey(SEARCH_CHECK_BOX)) {
//            settingList = (ArrayList<SearchCheckBox>) savedInstanceState.getSerializable(SEARCH_CHECK_BOX);
//        } else if (getIntent() != null && getIntent().hasExtra(SEARCH_CHECK_BOX)) {
//            try {
//                settingList = (ArrayList<SearchCheckBox>) getIntent().getSerializableExtra(SEARCH_CHECK_BOX);
//            } catch (ClassCastException e) {
//                settingList = createDefaultSearchCheckboxes();
//            }
//        } else {
//            settingList = createDefaultSearchCheckboxes();
//        }
        SearchCheckBoxPreferences prefsManager = new SearchCheckBoxPreferences(this);

        // Load saved list or create new one
        settingList = prefsManager.getSearchCheckBoxList();

        if (settingList.isEmpty()) {
            // Initialize with default values if no saved data exists
            settingList = createDefaultSearchList();
            prefsManager.saveSearchCheckBoxList(settingList);
        }
        Button run = findViewById(R.id.run_filter);
        run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Simply return the current state
//                Intent resultIntent = new Intent();
//                resultIntent.putExtra(SEARCH_CHECK_BOX, settingList);
//                setResult(RESULT_OK, resultIntent);
                prefsManager.saveSearchCheckBoxList(settingList);
                finish();
            }
        });

        Button close = findViewById(R.id.cancel_filter);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Intent resultIntent = new Intent();
//                resultIntent.putExtra(SEARCH_CHECK_BOX, settingList);
//                setResult ( RESULT_OK ,resultIntent );
                prefsManager.saveSearchCheckBoxList(settingList);
                  finish();
            }
        });

        setListAdapter ( new MyActivity_Settings_Adapter ( this , R.layout.sub_menu_item , settingList ) );
    }

    protected void onSaveInstanceState ( Bundle outState ) {
        super.onSaveInstanceState ( outState );
        outState.putSerializable ( SEARCH_CHECK_BOX , settingList );
    }

    @Override
    public void finish () {
        //setResult ( RESULT_OK , new Intent().putExtra ( SEARCH_CHECK_BOX , settingList ) );
        super.finish ();
    }

}

class MyActivity_Settings_Adapter extends ArrayAdapter < SearchCheckBox > {

    private final LayoutInflater layoutInflater;
    private final int itemResourceId;

    // Holder pattern (used instead of findViewById for better performance)
    static class ViewHolder {
        public CheckBox checkBox;
        public TextView title;

    }

    // Constructor
    public MyActivity_Settings_Adapter ( ListActivity context, int itemResourceId , List< SearchCheckBox > options ) {
        super ( context , itemResourceId , options );
        layoutInflater = context.getLayoutInflater ();
        this.itemResourceId = itemResourceId;
    }

    // Method called by the list view every time to display a row
    @Override
    public View getView (final int position , View convertView , ViewGroup parent ) {
        // Declare and initialize the row view
        View rowView = convertView;
        // Declare the row view holder
        ViewHolder viewHolder;
        // Check if an inflated view is provided
        if ( rowView == null ) {
            // A new view must be inflated
            rowView = layoutInflater.inflate ( itemResourceId , null );
            // Declare and initialize a view holder
            viewHolder = new ViewHolder ();
            // Retrieve a reference to the row title
            viewHolder.title = rowView.findViewById ( R.id.option_title );
            // Retrieve a reference to the row check box
            viewHolder.checkBox = rowView.findViewById ( R.id.option_checkbox );
            // Store the view holder as tag
            rowView.setTag ( viewHolder );
        } // End if
        else
            // An inflated view is already provided, retrieve the stored view holder
            viewHolder = (ViewHolder) rowView.getTag ();

        // Set the option title
        viewHolder.title.setText ( getItem ( position ).getDescription () );
        // Set the option check box state
        viewHolder.checkBox.setChecked ( getItem ( position ).isChecked () );
        // Assign a click listener to the checkbox
        viewHolder.checkBox.setOnClickListener( new View.OnClickListener() {

            public void onClick ( View checkBox ) {
                // Retrieve the stored view holder
                ViewHolder viewHolder = (ViewHolder) ((View) checkBox.getParent()).getTag();
                // Update the option state
                getItem ( position ).setChecked ( ! getItem ( position ).isChecked () );
                // Display the new option state
                viewHolder.checkBox.setChecked ( getItem ( position ).isChecked () );

            }
        });


        return rowView;
    } // End of getView

}
