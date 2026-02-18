package za.co.jpsoft.winkerkreader;

import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import za.co.jpsoft.winkerkreader.data.FilterBox;
import za.co.jpsoft.winkerkreader.data.WinkerkContract;

import java.util.ArrayList;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.*;

/**
 * Handles filter dialog operations
 */
public class FilterHandler {
    private final AppCompatActivity activity;
    private ArrayList<FilterBox> filterList;

    public FilterHandler(AppCompatActivity activity) {
        this.activity = activity;
        this.filterList = new ArrayList<>();
    }

    public void showFilterDialog() {
        LinearLayout mainLayout = activity.findViewById(R.id.main_main);
        LinearLayout filterLayout = activity.findViewById(R.id.main_filter);

        if (mainLayout == null || filterLayout == null) {
            // Filter layout not found in current activity layout
            return;
        }

        mainLayout.setVisibility(View.GONE);
        filterLayout.setVisibility(View.VISIBLE);

        setupFilterControls();
    }

    private void setupFilterControls() {
        // Setup active/inactive buttons
        Button activeButton = activity.findViewById(R.id.filter_aktief);
        Button inactiveButton = activity.findViewById(R.id.filter_onaktief);

        if (activeButton != null) {
            activeButton.setOnClickListener(v -> {
                RECORDSTATUS = "0";
                activeButton.setBackgroundResource(R.drawable.aktief0);
                if (inactiveButton != null) {
                    inactiveButton.setBackgroundResource(R.drawable.onaktief);
                }
                SELECTION_LIDMAAT_WHERE = "";
            });
        }

        if (inactiveButton != null) {
            inactiveButton.setOnClickListener(v -> {
                RECORDSTATUS = "2";
                inactiveButton.setBackgroundResource(R.drawable.onaktief2);
                if (activeButton != null) {
                    activeButton.setBackgroundResource(R.drawable.aktief);
                }
                SELECTION_LIDMAAT_WHERE = "";
            });
        }

        // Setup run filter button
        Button runButton = activity.findViewById(R.id.run_filter2);
        if (runButton != null) {
            runButton.setOnClickListener(v -> applyFilters());
        }

        // Setup cancel button
        Button cancelButton = activity.findViewById(R.id.cancel_filter2);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> closeFilterDialog());
        }
    }

    private void applyFilters() {
        filterList = new ArrayList<>();

        // Collect filters from UI controls
        addFilterFromUI("Van", R.id.filter_van, R.id.filter_van_opsies, R.id.filter_van_check);
        addFilterFromUI("Noemnaam", R.id.filter_noemnaam, R.id.filter_noemnaam_opsies, R.id.filter_noemnaam_check);
        addFilterFromUI("Nooiensvan", R.id.filter_nooiensvan, R.id.filter_nooiensvan_opsies, R.id.filter_nooiensvan_check);
        addFilterFromUI("Ouderdom", R.id.filter_ouderdom1, R.id.filter_ouderdom2, R.id.filter_ouderdom_opsies, R.id.filter_ouderdom_check);
        addFilterFromUI("Wyk", R.id.filter_wyk, R.id.filter_wyk_opsies, R.id.filter_wyk_check);

        // Add special filters
        addSpecialFilter("Geslag", R.id.filter_geslag_opsies, R.id.filter_geslag_check);
        addSpecialFilter("Huwelikstatus", R.id.filter_huwelikstatus_opsies, R.id.filter_huwelikstatus_check);
        addSpecialFilter("Lidmaatskap", R.id.filter_lidmaatsakp_opsies, R.id.filter_lidmaatsakapstatus_check);

        // Add checkbox only filters
        addCheckboxFilter("Selfoon", R.id.filter_selfoon);
        addCheckboxFilter("Landlyn", R.id.filter_landlyn);
        addCheckboxFilter("E-pos", R.id.filter_epos);
        addCheckboxFilter("Gesinshoof", R.id.filter_gesinshoof);

        // Close dialog and apply filters
        closeFilterDialog();

        // Apply the filters to the main activity
        MainActivity2 mainActivity = (MainActivity2) activity;
        mainActivity.applyFilterList(filterList);

        WinkerkContract.winkerkEntry.SORTORDER = "Filter";
        DEFLAYOUT = "FILTER_DATA";
        mainActivity.observeDataset();
    }

    private void addFilterFromUI(String fieldName, int editTextId, int spinnerId, int checkBoxId) {
        EditText editText = activity.findViewById(editTextId);
        Spinner spinner = activity.findViewById(spinnerId);
        CheckBox checkBox = activity.findViewById(checkBoxId);

        if (editText != null && spinner != null && checkBox != null) {
            filterList.add(new FilterBox(
                    fieldName,
                    editText.getText().toString(),
                    "",
                    spinner.getSelectedItem() != null ? spinner.getSelectedItem().toString() : "",
                    checkBox.isChecked()
            ));
        }
    }

    private void addFilterFromUI(String fieldName, int editText1Id, int editText2Id, int spinnerId, int checkBoxId) {
        EditText editText1 = activity.findViewById(editText1Id);
        EditText editText2 = activity.findViewById(editText2Id);
        Spinner spinner = activity.findViewById(spinnerId);
        CheckBox checkBox = activity.findViewById(checkBoxId);

        if (editText1 != null && editText2 != null && spinner != null && checkBox != null) {
            filterList.add(new FilterBox(
                    fieldName,
                    editText1.getText().toString(),
                    editText2.getText().toString(),
                    spinner.getSelectedItem() != null ? spinner.getSelectedItem().toString() : "",
                    checkBox.isChecked()
            ));
        }
    }

    private void addSpecialFilter(String fieldName, int spinnerId, int checkBoxId) {
        Spinner spinner = activity.findViewById(spinnerId);
        CheckBox checkBox = activity.findViewById(checkBoxId);

        if (spinner != null && checkBox != null) {
            filterList.add(new FilterBox(
                    fieldName,
                    "",
                    "",
                    spinner.getSelectedItem() != null ? spinner.getSelectedItem().toString() : "",
                    checkBox.isChecked()
            ));
        }
    }

    private void addCheckboxFilter(String fieldName, int checkBoxId) {
        CheckBox checkBox = activity.findViewById(checkBoxId);

        if (checkBox != null) {
            filterList.add(new FilterBox(
                    fieldName,
                    "",
                    "",
                    "",
                    checkBox.isChecked()
            ));
        }
    }

    private void closeFilterDialog() {
        LinearLayout mainLayout = activity.findViewById(R.id.main_main);
        LinearLayout filterLayout = activity.findViewById(R.id.main_filter);

        if (mainLayout != null && filterLayout != null) {
            mainLayout.setVisibility(View.VISIBLE);
            filterLayout.setVisibility(View.GONE);
        }
    }

    public ArrayList<FilterBox> getFilterList() {
        return filterList;
    }
}