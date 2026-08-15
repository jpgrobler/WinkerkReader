package za.co.jpsoft.winkerkreader.ui.activities

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract
import za.co.jpsoft.winkerkreader.databinding.SubMenuBinding
import za.co.jpsoft.winkerkreader.databinding.SubMenuItemBinding
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.utils.SearchCheckBoxPreferences

class SettingsActivity : AuthBaseActivity() {

    private lateinit var binding: SubMenuBinding
    private lateinit var settingList: ArrayList<SearchCheckBox>
    private val prefsManager by lazy { SearchCheckBoxPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SubMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply navigation bar insets to RecyclerView
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }

        title = "Soek in velde:"

        // Load / repair data (unchanged)
        settingList = prefsManager.getSearchCheckBoxList()
        if (settingList.isEmpty()) {
            settingList = createDefaultSearchList()
            prefsManager.saveSearchCheckBoxList(settingList)
        } else {
            repairDataIfNeeded()
        }

        // Setup RecyclerView
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = SettingsAdapter(settingList)

        // Button listeners
        binding.runFilter.setOnClickListener {
            prefsManager.saveSearchCheckBoxList(settingList)
            finish()
        }
        binding.cancelFilter.setOnClickListener {
            prefsManager.saveSearchCheckBoxList(settingList)
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(MainActivity.SEARCH_CHECK_BOX, settingList)
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

    // Extract repair logic to keep onCreate clean
    private fun repairDataIfNeeded() {
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
}

// RecyclerView adapter replacing ArrayAdapter
class SettingsAdapter(
    private val options: List<SearchCheckBox>
) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = SubMenuItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = options[position]
        with(holder.binding) {
            optionTitle.text = item.description
            optionCheckbox.isChecked = item.isChecked
            optionCheckbox.setOnClickListener {
                item.isChecked = optionCheckbox.isChecked
            }
        }
    }

    override fun getItemCount(): Int = options.size

    class ViewHolder(val binding: SubMenuItemBinding) : RecyclerView.ViewHolder(binding.root)
}