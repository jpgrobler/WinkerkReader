package za.co.jpsoft.winkerkreader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import za.co.jpsoft.winkerkreader.data.FilterBox;

import java.util.ArrayList;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.RECORDSTATUS;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by Pieter Grobler on 06/09/2017.
 */

public class MyFilter extends AppCompatActivity {

    private ArrayList<FilterBox> filterList;

    @Override
    public void onBackPressed() {
        finish();
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
    public void onCreate ( Bundle savedInstanceState ) {
        super.onCreate(savedInstanceState);
        setContentView ( R.layout.filterscreen );
        final Button aktiefB = (Button) findViewById(R.id.filter_aktief);
        final Button onaktiefB = (Button) findViewById(R.id.filter_onaktief);
        Button run = (Button) findViewById(R.id.run_filter2);
        aktiefB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!aktiefB.getBackground().equals(R.drawable.aktief0)){
                    RECORDSTATUS = "0";
                    aktiefB.setBackgroundResource(R.drawable.aktief0);
                    onaktiefB.setBackgroundResource(R.drawable.onaktief);
                }
                else {
                    aktiefB.setBackgroundResource(R.drawable.aktief);
                    onaktiefB.setBackgroundResource(R.drawable.onaktief2);
                    RECORDSTATUS = "2";
                }
            }
        });
        onaktiefB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!onaktiefB.getBackground().equals(R.drawable.onaktief2)){
                    RECORDSTATUS = "2";
                    onaktiefB.setBackgroundResource(R.drawable.onaktief2);
                    aktiefB.setBackgroundResource(R.drawable.aktief);
                }
                else {
                    RECORDSTATUS = "0";
                    onaktiefB.setBackgroundResource(R.drawable.onaktief);
                    aktiefB.setBackgroundResource(R.drawable.aktief0);
                }
            }
        });
        run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                filterList = new ArrayList<>();
                filterList.add(new FilterBox("Van",
                        ((EditText) findViewById(R.id.filter_van)).getText().toString().trim(),
                        "",
                        ((Spinner) findViewById(R.id.filter_van_opsies)).getSelectedItem().toString(),
                        ((CheckBox) findViewById(R.id.filter_van_check)).isChecked()));
                filterList.add(new FilterBox("Noemnaam",
                        ((EditText) findViewById(R.id.filter_noemnaam)).getText().toString().trim(),
                        "",
                        ((Spinner) findViewById(R.id.filter_noemnaam_opsies)).getSelectedItem().toString(),
                        ((CheckBox) findViewById(R.id.filter_noemnaam_check)).isChecked()));
                filterList.add(new FilterBox("Ouderdom",
                        ((EditText) findViewById(R.id.filter_ouderdom1)).getText().toString().trim(),
                        ((EditText) findViewById(R.id.filter_ouderdom2)).getText().toString().trim(),
                        ((Spinner) findViewById(R.id.filter_ouderdom_opsies)).getSelectedItem().toString(),
                        ((CheckBox) findViewById(R.id.filter_ouderdom_check)).isChecked()));
                filterList.add(new FilterBox("Wyk",
                        ((EditText) findViewById(R.id.filter_wyk)).getText().toString().trim(),
                        "",
                        ((Spinner) findViewById(R.id.filter_wyk_opsies)).getSelectedItem().toString(),
                        ((CheckBox) findViewById(R.id.filter_wyk_check)).isChecked()));
                filterList.add(new FilterBox("Geslag",
                        "",
                        "",
                        ((Spinner) findViewById(R.id.filter_geslag_opsies)).getSelectedItem().toString(),
                        ((CheckBox) findViewById(R.id.filter_geslag_check)).isChecked()));
                filterList.add(new FilterBox("Huwelikstatus",
                        "",
                        "",
                        ((Spinner) findViewById(R.id.filter_huwelikstatus_opsies)).getSelectedItem().toString(),
                        ((CheckBox) findViewById(R.id.filter_huwelikstatus_check)).isChecked()));
                filterList.add(new FilterBox("Lidmaatskap",
                        "",
                        "",
                        ((Spinner) findViewById(R.id.filter_lidmaatsakp_opsies)).getSelectedItem().toString(),
                        ((CheckBox) findViewById(R.id.filter_lidmaatsakapstatus_check)).isChecked()));
                filterList.add(new FilterBox("Selfoon",
                        "",
                        "",
                        "",
                        ((CheckBox) findViewById(R.id.filter_selfoon)).isChecked()));
                filterList.add(new FilterBox("Landlyn",
                        "",
                        "",
                        "",
                        ((CheckBox) findViewById(R.id.filter_landlyn)).isChecked()));
                filterList.add(new FilterBox("E-pos",
                        "",
                        "",
                        "",
                        ((CheckBox) findViewById(R.id.filter_epos)).isChecked()));
                filterList.add(new FilterBox("Gesinshoof",
                        "",
                        "",
                        "",
                        ((CheckBox) findViewById(R.id.filter_gesinshoof)).isChecked()));

                setResult ( RESULT_OK , new Intent().putExtra ( MainActivity2.FILTER_CHECK_BOX , filterList ) );

                    InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);


                finish();
            }
        });
        Button close = (Button) findViewById(R.id.cancel_filter2);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(getCurrentFocus()!=null) {
                    InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                }
                setResult ( Activity.RESULT_CANCELED);
                finish();
            }
        });
        close.requestFocus();
//        setListAdapter ( new MyFilter_Adapter ( this , R.layout.filter_sub_menu_item , filterList ) );

    }

    protected void onSaveInstanceState ( Bundle outState ) {
        super.onSaveInstanceState ( outState );
    }

    @Override
    public void finish () {
        //setResult ( RESULT_OK , new Intent().putExtra ( MainActivity2.FILTER_CHECK_BOX , filterList ) );
        if(getCurrentFocus()!=null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        super.finish ();
    }

}

