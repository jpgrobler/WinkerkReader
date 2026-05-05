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
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.databinding.SubMenuBinding
import za.co.jpsoft.winkerkreader.databinding.SubMenuItemBinding

/**
 * Created by Pieter Grobler on 06/09/2017.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: SubMenuBinding

    private lateinit var settingList: ArrayList<SearchCheckBox>
    private val prefsManager by lazy { SearchCheckBoxPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SubMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Soek in velde:"

        // Load saved list from preferences
        settingList = prefsManager.getSearchCheckBoxList()

        if (settingList.isEmpty()) {
            // No saved data – create fresh default list
            settingList = createDefaultSearchList()
            prefsManager.saveSearchCheckBoxList(settingList)
        } else {
            // ... (Repaired logic remains the same)
            val defaultList = createDefaultSearchList()
            val defaultMap = defaultList.associateBy { it.columnName }
            var needsSave = false

            for (savedItem in settingList) {
                if (savedItem.columnName.isBlank() || savedItem.description.isBlank()) {
                    needsSave = true
                    break
                }
            }

            if (needsSave) {
                val repairedList = ArrayList<SearchCheckBox>()
                for (savedItem in settingList) {
                    val defaultItem = defaultMap[savedItem.columnName]
                    if (defaultItem != null && savedItem.columnName.isNotBlank()) {
                        repairedList.add(
                            SearchCheckBox(
                                columnName = defaultItem.columnName,
                                columnValue = savedItem.columnValue.ifBlank { defaultItem.columnValue },
                                description = defaultItem.description,
                                isChecked = savedItem.isChecked
                            )
                        )
                    } else {
                        val fallback = defaultMap.values.find { it.columnName == savedItem.columnName }
                        if (fallback != null) {
                            repairedList.add(
                                SearchCheckBox(
                                    columnName = fallback.columnName,
                                    columnValue = fallback.columnValue,
                                    description = fallback.description,
                                    isChecked = savedItem.isChecked
                                )
                            )
                        }
                    }
                }
                for (defaultItem in defaultList) {
                    if (repairedList.none { it.columnName == defaultItem.columnName }) {
                        repairedList.add(defaultItem)
                    }
                }
                settingList = repairedList
                prefsManager.saveSearchCheckBoxList(settingList)
            }
        }

        binding.runFilter.setOnClickListener {
            prefsManager.saveSearchCheckBoxList(settingList)
            finish()
        }

        binding.cancelFilter.setOnClickListener {
            prefsManager.saveSearchCheckBoxList(settingList)
            finish()
        }

        binding.list.adapter = SettingsAdapter(this, settingList)
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
            SearchCheckBox(WinkerkContract.winkerkEntry.LIDMATE_VAN, "", "Van", true),
            SearchCheckBox(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM, "", "Noemnaam", true),
            SearchCheckBox(WinkerkContract.winkerkEntry.LIDMATE_VOORNAME, "", "Voorname", true),
            SearchCheckBox(WinkerkContract.winkerkEntry.LIDMATE_WYK, "", "Wyk", true),
            SearchCheckBox(WinkerkContract.winkerkEntry.LIDMATE_SELFOON, "", "Selfoon", true),
            SearchCheckBox(WinkerkContract.winkerkEntry.ADRESSE_LANDLYN, "", "Landlyn", true),
            SearchCheckBox(WinkerkContract.winkerkEntry.LIDMATE_NOOIENSVAN, "", "Nooiensvan", true),
            SearchCheckBox(WinkerkContract.winkerkEntry.LIDMATE_BEROEP, "", "Beroep", true),
            SearchCheckBox(WinkerkContract.winkerkEntry.LIDMATE_EPOS, "", "Epos", true),
            SearchCheckBox(WinkerkContract.winkerkEntry.LIDMATE_STRAATADRES, "", "Adres", true)
        )
    }
}

/**
 * Adapter for displaying search checkbox options.
 */
class SettingsAdapter(
    context: android.content.Context,
    options: List<SearchCheckBox>
) : ArrayAdapter<SearchCheckBox>(context, R.layout.sub_menu_item, options) {

    private val inflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding: SubMenuItemBinding
        val rowView: View

        if (convertView == null) {
            binding = SubMenuItemBinding.inflate(inflater, parent, false)
            rowView = binding.root
            rowView.tag = binding
        } else {
            rowView = convertView
            binding = rowView.tag as SubMenuItemBinding
        }

        val item = getItem(position)!!

        binding.optionTitle.text = item.description
        binding.optionCheckbox.isChecked = item.isChecked

        binding.optionCheckbox.setOnClickListener {
            item.isChecked = binding.optionCheckbox.isChecked
        }

        return rowView
    }
}