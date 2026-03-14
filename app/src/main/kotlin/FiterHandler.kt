package za.co.jpsoft.winkerkreader

import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.data.FilterBox
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import java.util.ArrayList

/**
 * Handles filter dialog operations
 */
class FilterHandler(private val activity: AppCompatActivity) {

    private var filterList: MutableList<FilterBox> = mutableListOf()

    fun showFilterDialog() {
        val mainLayout = activity.findViewById<LinearLayout>(R.id.main_main)
        val filterLayout = activity.findViewById<LinearLayout>(R.id.main_filter)

        if (mainLayout == null || filterLayout == null) {
            // Filter layout not found in current activity layout
            return
        }

        mainLayout.visibility = View.GONE
        filterLayout.visibility = View.VISIBLE

        setupFilterControls()
    }

    private fun setupFilterControls() {
        // Setup active/inactive buttons
        val activeButton = activity.findViewById<Button>(R.id.filter_aktief)
        val inactiveButton = activity.findViewById<Button>(R.id.filter_onaktief)

        activeButton?.setOnClickListener {
            winkerkEntry.RECORDSTATUS = "0"
            activeButton.setBackgroundResource(R.drawable.aktief0)
            inactiveButton?.setBackgroundResource(R.drawable.onaktief)
            winkerkEntry.SELECTION_LIDMAAT_WHERE = ""
        }

        inactiveButton?.setOnClickListener {
            winkerkEntry.RECORDSTATUS = "2"
            inactiveButton.setBackgroundResource(R.drawable.onaktief2)
            activeButton?.setBackgroundResource(R.drawable.aktief)
            winkerkEntry.SELECTION_LIDMAAT_WHERE = ""
        }

        // Setup run filter button
        activity.findViewById<Button>(R.id.run_filter2)?.setOnClickListener {
            applyFilters()
        }

        // Setup cancel button
        activity.findViewById<Button>(R.id.cancel_filter2)?.setOnClickListener {
            closeFilterDialog()
        }
    }

    private fun applyFilters() {
        filterList = mutableListOf()

        // Collect filters from UI controls
        addFilterFromUI("Van", R.id.filter_van, R.id.filter_van_opsies, R.id.filter_van_check)
        addFilterFromUI("Noemnaam", R.id.filter_noemnaam, R.id.filter_noemnaam_opsies, R.id.filter_noemnaam_check)
        addFilterFromUI("Nooiensvan", R.id.filter_nooiensvan, R.id.filter_nooiensvan_opsies, R.id.filter_nooiensvan_check)
        addFilterFromUI("Ouderdom", R.id.filter_ouderdom1, R.id.filter_ouderdom2, R.id.filter_ouderdom_opsies, R.id.filter_ouderdom_check)
        addFilterFromUI("Wyk", R.id.filter_wyk, R.id.filter_wyk_opsies, R.id.filter_wyk_check)

        // Add special filters
        addSpecialFilter("Geslag", R.id.filter_geslag_opsies, R.id.filter_geslag_check)
        addSpecialFilter("Huwelikstatus", R.id.filter_huwelikstatus_opsies, R.id.filter_huwelikstatus_check)
        addSpecialFilter("Lidmaatskap", R.id.filter_lidmaatsakp_opsies, R.id.filter_lidmaatsakapstatus_check)

        // Add checkbox only filters
        addCheckboxFilter("Selfoon", R.id.filter_selfoon)
        addCheckboxFilter("Landlyn", R.id.filter_landlyn)
        addCheckboxFilter("E-pos", R.id.filter_epos)
        addCheckboxFilter("Gesinshoof", R.id.filter_gesinshoof)

        // Close dialog and apply filters
        closeFilterDialog()

        // Apply the filters to the main activity – convert to ArrayList for Java compatibility
        val mainActivity = activity as MainActivity2
        mainActivity.applyFilterList(ArrayList(filterList))

        winkerkEntry.SORTORDER = "Filter"
        winkerkEntry.DEFLAYOUT = "FILTER_DATA"
        mainActivity.observeDataset()
    }

    private fun addFilterFromUI(fieldName: String, editTextId: Int, spinnerId: Int, checkBoxId: Int) {
        val editText = activity.findViewById<EditText>(editTextId)
        val spinner = activity.findViewById<Spinner>(spinnerId)
        val checkBox = activity.findViewById<CheckBox>(checkBoxId)

        if (editText != null && spinner != null && checkBox != null) {
            filterList.add(
                FilterBox(
                    fieldName,
                    editText.text.toString(),
                    "",
                    spinner.selectedItem?.toString() ?: "",
                    checkBox.isChecked
                )
            )
        }
    }

    private fun addFilterFromUI(fieldName: String, editText1Id: Int, editText2Id: Int, spinnerId: Int, checkBoxId: Int) {
        val editText1 = activity.findViewById<EditText>(editText1Id)
        val editText2 = activity.findViewById<EditText>(editText2Id)
        val spinner = activity.findViewById<Spinner>(spinnerId)
        val checkBox = activity.findViewById<CheckBox>(checkBoxId)

        if (editText1 != null && editText2 != null && spinner != null && checkBox != null) {
            filterList.add(
                FilterBox(
                    fieldName,
                    editText1.text.toString(),
                    editText2.text.toString(),
                    spinner.selectedItem?.toString() ?: "",
                    checkBox.isChecked
                )
            )
        }
    }

    private fun addSpecialFilter(fieldName: String, spinnerId: Int, checkBoxId: Int) {
        val spinner = activity.findViewById<Spinner>(spinnerId)
        val checkBox = activity.findViewById<CheckBox>(checkBoxId)

        if (spinner != null && checkBox != null) {
            filterList.add(
                FilterBox(
                    fieldName,
                    "",
                    "",
                    spinner.selectedItem?.toString() ?: "",
                    checkBox.isChecked
                )
            )
        }
    }

    private fun addCheckboxFilter(fieldName: String, checkBoxId: Int) {
        val checkBox = activity.findViewById<CheckBox>(checkBoxId)

        if (checkBox != null) {
            filterList.add(
                FilterBox(
                    fieldName,
                    "",
                    "",
                    "",
                    checkBox.isChecked
                )
            )
        }
    }

    private fun closeFilterDialog() {
        val mainLayout = activity.findViewById<LinearLayout>(R.id.main_main)
        val filterLayout = activity.findViewById<LinearLayout>(R.id.main_filter)

        mainLayout?.visibility = View.VISIBLE
        filterLayout?.visibility = View.GONE
    }

    fun getFilterList(): MutableList<FilterBox> = filterList
}