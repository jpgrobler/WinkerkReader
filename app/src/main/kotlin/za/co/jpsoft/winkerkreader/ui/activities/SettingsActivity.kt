package za.co.jpsoft.winkerkreader.ui.activities

import za.co.jpsoft.winkerkreader.utils.SearchCheckBoxPreferences
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox

import android.app.ListActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.R
/**
 * Created by Pieter Grobler on 06/09/2017.
 */
class SettingsActivity : ListActivity() {

    private lateinit var settingList: ArrayList<SearchCheckBox>
    private val prefsManager by lazy { SearchCheckBoxPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sub_menu)
        title = "Soek in velde:"

        // Load saved list or create new one
        settingList = prefsManager.getSearchCheckBoxList()

        if (settingList.isEmpty()) {
            settingList = createDefaultSearchList()
            prefsManager.saveSearchCheckBoxList(settingList)
        }

        findViewById<Button>(R.id.run_filter).setOnClickListener {
            prefsManager.saveSearchCheckBoxList(settingList)
            finish()
        }

        findViewById<Button>(R.id.cancel_filter).setOnClickListener {
            prefsManager.saveSearchCheckBoxList(settingList)
            finish()
        }

        listAdapter = SettingsAdapter(this, R.layout.sub_menu_item, settingList)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(MainActivity.SEARCH_CHECK_BOX, settingList)
    }

    override fun finish() {
        super.finish()
    }

    private fun createDefaultSearchList(): ArrayList<SearchCheckBox> {
        return arrayListOf(
            SearchCheckBox(winkerkEntry.LIDMATE_VAN, "", "Van", true),
            SearchCheckBox(winkerkEntry.LIDMATE_NOEMNAAM, "", "Noemnaam", true),
            SearchCheckBox(winkerkEntry.LIDMATE_VOORNAME, "", "Voorname", true),
            SearchCheckBox(winkerkEntry.LIDMATE_WYK, "", "Wyk", true),
            SearchCheckBox(winkerkEntry.LIDMATE_SELFOON, "", "Selfoon", true),
            SearchCheckBox(winkerkEntry.ADRESSE_LANDLYN, "", "Landlyn", true),
            SearchCheckBox(winkerkEntry.LIDMATE_NOOIENSVAN, "", "Nooiensvan", true),
            SearchCheckBox(winkerkEntry.LIDMATE_BEROEP, "", "Beroep", true),
            SearchCheckBox(winkerkEntry.LIDMATE_EPOS, "", "Epos", true),
            SearchCheckBox(winkerkEntry.LIDMATE_STRAATADRES, "", "Adres", true)
        )
    }
}

/**
 * Adapter for displaying search checkbox options.
 */
class SettingsAdapter(
    context: ListActivity,
    private val itemResourceId: Int,
    options: List<SearchCheckBox>
) : ArrayAdapter<SearchCheckBox>(context, itemResourceId, options) {

    private val inflater = LayoutInflater.from(context)

    private class ViewHolder(val title: TextView, val checkBox: CheckBox)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val rowView: View
        val viewHolder: ViewHolder

        if (convertView == null) {
            rowView = inflater.inflate(itemResourceId, parent, false)
            val title = rowView.findViewById<TextView>(R.id.option_title)
            val checkBox = rowView.findViewById<CheckBox>(R.id.option_checkbox)
            viewHolder = ViewHolder(title, checkBox)
            rowView.tag = viewHolder
        } else {
            rowView = convertView
            viewHolder = rowView.tag as ViewHolder
        }

        // Position is guaranteed to be valid in ArrayAdapter
        val item = getItem(position)!!

        viewHolder.title.text = item.description
        viewHolder.checkBox.isChecked = item.isChecked

        // Click listener toggles the checkbox state and updates the model
        viewHolder.checkBox.setOnClickListener {
            item.isChecked = !item.isChecked
            viewHolder.checkBox.isChecked = item.isChecked
        }

        return rowView
    }
}