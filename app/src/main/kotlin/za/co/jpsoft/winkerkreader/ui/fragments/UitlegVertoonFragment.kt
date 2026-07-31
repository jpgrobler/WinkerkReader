package za.co.jpsoft.winkerkreader.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import yuku.ambilwarna.AmbilWarnaDialog
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.FragmentUitlegVertoonBinding
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs.ThemeMode

class UitlegVertoonFragment : Fragment() {

    private var _binding: FragmentUitlegVertoonBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

    private var isInitializing = true
    private var isDisplayDirty = false
    private var isColorsDirty = false

    // Initial values: [gem1, gem2, gem3, inactive]
    private var initialCheckboxes = mutableMapOf<Int, Boolean>()
    private var initialLayout: String = "GESINNE"
    private val initialColors = mutableListOf(
        Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE
    )
    private var initialCongregationIndicator = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUitlegVertoonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager.getInstance(requireContext())

        loadPreferences()
        setupListeners()
        isInitializing = false
        isDisplayDirty = false
        isColorsDirty = false

        // ─── Theme mode – use AppearancePrefs.ThemeMode directly ───
        when (settingsManager.appearance.themeMode) {
            ThemeMode.LIGHT -> binding.themeModeLight.isChecked = true
            ThemeMode.DARK -> binding.themeModeDark.isChecked = true
            else -> binding.themeModeSystem.isChecked = true
        }

        binding.themeModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.theme_mode_light -> ThemeMode.LIGHT
                R.id.theme_mode_dark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            settingsManager.appearance.themeMode = mode
            applyTheme(mode)
            Toast.makeText(requireContext(), "Tema verander. Herbegin die app.", Toast.LENGTH_SHORT)
                .show()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            updateSaveButtonState()
        }, 300)
    }

    private fun applyTheme(mode: ThemeMode) {
        when (mode) {
            ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
            )

            ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES
            )

            ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }
    }

    private fun loadPreferences() {
        // ─── Member list display settings ────────────────────────────────
        val memberList = settingsManager.memberList
        val congregation = settingsManager.congregation

        val checkboxes = listOf(
            binding.uitlegFoto to memberList.isListFoto,
            binding.uitlegEpos to memberList.isListEpos,
            binding.uitlegWhatsap to memberList.isListWhatsapp,
            binding.uitlegVerjaarsdag to memberList.isListVerjaarBlok,
            binding.uitlegOuderdom to memberList.isListOuderdom,
            binding.uitlegHuweliksdag to memberList.isListHuwelikBlok,
            binding.uitlegWyk to memberList.isListWyk,
            binding.uitlegSelfoon to memberList.isListSelfoon,
            binding.uitlegTelefoon to memberList.isListTelefoon,
            binding.congregationIndicatorSwitch to congregation.useCongregationIndicator
        )
        checkboxes.forEach { (cb, value) ->
            cb.isChecked = value
            initialCheckboxes[cb.id] = value
        }

        // Layout
        initialLayout = memberList.defLayout
        for (i in 0 until binding.layoutOpsies.count) {
            val item = binding.layoutOpsies.getItemAtPosition(i).toString()
            if (item.equals(initialLayout, ignoreCase = true)) {
                binding.layoutOpsies.setSelection(i)
                break
            }
        }

        // Colors
        initialColors[0] = congregation.gemeenteKleur
        initialColors[1] = congregation.gemeente2Kleur
        initialColors[2] = congregation.gemeente3Kleur
        initialColors[3] = congregation.inactiveBackgroundColor

        updateTextViewBackground(binding.gem1, initialColors[0])
        updateTextViewBackground(binding.gem2, initialColors[1])
        updateTextViewBackground(binding.gem3, initialColors[2])
        updateTextViewBackground(binding.inactiveColorPreview, initialColors[3])

        // Gemeente names
        if (congregation.gemeenteNaam.isNotBlank()) {
            binding.gem1.text = congregation.gemeenteNaam
        }
        if (congregation.gemeente2Naam.isNotBlank()) {
            binding.gem2.text = congregation.gemeente2Naam
        }
        if (congregation.gemeente3Naam.isNotBlank()) {
            binding.gem3.text = congregation.gemeente3Naam
        }
        initialCongregationIndicator = congregation.useCongregationIndicator
        binding.congregationIndicatorSwitch.isChecked = initialCongregationIndicator
    }

    private fun setupListeners() {
        // CheckBox listeners
        initialCheckboxes.keys.forEach { id ->
            val cb = binding.root.findViewById<android.widget.CheckBox>(id)
            cb.setOnCheckedChangeListener { _, _ -> onDisplayChanged() }
        }

        // Layout spinner
        binding.layoutOpsies.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = parent?.getItemAtPosition(position)?.toString() ?: return
                if (selected != initialLayout) onDisplayChanged()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Color pickers
        binding.gem1.setOnClickListener { openColorPickerDialog(it, 1) }
        binding.gem2.setOnClickListener { openColorPickerDialog(it, 2) }
        binding.gem3.setOnClickListener { openColorPickerDialog(it, 3) }
        binding.inactiveColorPreview.setOnClickListener { openColorPickerDialog(it, 4) }

        // Save buttons
        binding.uitlegStoor.setOnClickListener { saveDisplaySettings() }
        binding.saveColor.setOnClickListener { saveColorSettings() }
    }

    private fun onDisplayChanged() {
        if (!isInitializing) {
            isDisplayDirty = true
            updateSaveButtonState()
        }
    }

    private fun onColorChanged() {
        if (!isInitializing) {
            isColorsDirty = true
            updateSaveButtonState()
        }
    }

    private fun updateSaveButtonState() {
        val displayEnabled = isDisplayDirty || isDisplaySettingChanged()
        val colorsEnabled = isColorsDirty || isColorSettingChanged()

        binding.uitlegStoor.isEnabled = displayEnabled
        binding.uitlegStoor.alpha = if (displayEnabled) 1.0f else 0.4f

        binding.saveColor.isEnabled = colorsEnabled
        binding.saveColor.alpha = if (colorsEnabled) 1.0f else 0.4f
    }

    private fun isDisplaySettingChanged(): Boolean {
        val memberList = settingsManager.memberList
        val congregation = settingsManager.congregation

        val displayCheckboxIds = listOf(
            R.id.uitleg_foto,
            R.id.uitleg_epos,
            R.id.uitleg_whatsap,
            R.id.uitleg_verjaarsdag,
            R.id.uitleg_ouderdom,
            R.id.uitleg_Huweliksdag,
            R.id.uitleg_wyk,
            R.id.uitleg_selfoon,
            R.id.uitleg_telefoon,
            R.id.congregation_indicator_switch
        )
        displayCheckboxIds.forEach { id ->
            val cb = binding.root.findViewById<android.widget.CheckBox>(id)
            if (cb.isChecked != initialCheckboxes[id]) return true
        }

        val currentLayout = binding.layoutOpsies.selectedItem?.toString() ?: "GESINNE"
        if (currentLayout != initialLayout) return true

        return false
    }

    private fun isColorSettingChanged(): Boolean {
        if (getCurrentColor(R.id.gem1) != initialColors[0]) return true
        if (getCurrentColor(R.id.gem2) != initialColors[1]) return true
        if (getCurrentColor(R.id.gem3) != initialColors[2]) return true
        if (getCurrentColor(R.id.inactive_color_preview) != initialColors[3]) return true
        return false
    }

    private fun getCurrentColor(viewId: Int): Int {
        val view = binding.root.findViewById<TextView>(viewId) ?: return -1
        return (view.background as? android.graphics.drawable.ColorDrawable)?.color ?: -1
    }

    private fun saveDisplaySettings() {
        val memberList = settingsManager.memberList
        val congregation = settingsManager.congregation

        initialCheckboxes.forEach { (id, _) ->
            val cb = binding.root.findViewById<android.widget.CheckBox>(id)
            when (id) {
                R.id.uitleg_foto -> memberList.isListFoto = cb.isChecked
                R.id.uitleg_epos -> memberList.isListEpos = cb.isChecked
                R.id.uitleg_whatsap -> memberList.isListWhatsapp = cb.isChecked
                R.id.uitleg_verjaarsdag -> memberList.isListVerjaarBlok = cb.isChecked
                R.id.uitleg_ouderdom -> memberList.isListOuderdom = cb.isChecked
                R.id.uitleg_Huweliksdag -> memberList.isListHuwelikBlok = cb.isChecked
                R.id.uitleg_wyk -> memberList.isListWyk = cb.isChecked
                R.id.uitleg_selfoon -> memberList.isListSelfoon = cb.isChecked
                R.id.uitleg_telefoon -> memberList.isListTelefoon = cb.isChecked
            }
        }
        memberList.defLayout = binding.layoutOpsies.selectedItem?.toString() ?: "GESINNE"
        congregation.useCongregationIndicator = binding.congregationIndicatorSwitch.isChecked

        // Update initial values
        initialCheckboxes.forEach { (id, _) ->
            val cb = binding.root.findViewById<android.widget.CheckBox>(id)
            initialCheckboxes[id] = cb.isChecked
        }
        initialLayout = memberList.defLayout
        initialCongregationIndicator = congregation.useCongregationIndicator
        isDisplayDirty = false
        updateSaveButtonState()
        Toast.makeText(requireContext(), "Vertoon-instellings gestoor", Toast.LENGTH_SHORT).show()
    }

    private fun saveColorSettings() {
        val congregation = settingsManager.congregation

        initialColors[0] = getCurrentColor(R.id.gem1)
        initialColors[1] = getCurrentColor(R.id.gem2)
        initialColors[2] = getCurrentColor(R.id.gem3)
        initialColors[3] = getCurrentColor(R.id.inactive_color_preview)

        congregation.gemeenteKleur = initialColors[0]
        congregation.gemeente2Kleur = initialColors[1]
        congregation.gemeente3Kleur = initialColors[2]
        congregation.inactiveBackgroundColor = initialColors[3]

        isColorsDirty = false
        updateSaveButtonState()
        Toast.makeText(requireContext(), "Kleure gestoor", Toast.LENGTH_SHORT).show()
    }

    private fun openColorPickerDialog(view: View, colorIndex: Int) {
        val congregation = settingsManager.congregation
        val currentColor = when (colorIndex) {
            1 -> congregation.gemeenteKleur
            2 -> congregation.gemeente2Kleur
            3 -> congregation.gemeente3Kleur
            4 -> congregation.inactiveBackgroundColor
            else -> -1
        }
        val dialog = AmbilWarnaDialog(
            requireContext(),
            currentColor,
            object : AmbilWarnaDialog.OnAmbilWarnaListener {
                override fun onCancel(dialog: AmbilWarnaDialog) {}
                override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                    handleColorSelected(view, colorIndex, color)
                }
            }
        )
        dialog.show()
    }

    private fun handleColorSelected(view: View, colorIndex: Int, color: Int) {
        if (view is TextView) {
            updateTextViewBackground(view, color)
        } else {
            view.setBackgroundColor(color)
        }
        onColorChanged()
    }

    private fun updateTextViewBackground(textView: TextView, color: Int) {
        if (color != Int.MIN_VALUE && color != 0) {
            textView.background = android.graphics.drawable.ColorDrawable(color)
            val darkness =
                1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(
                    color
                )) / 255
            textView.setTextColor(if (darkness >= 0.5) Color.WHITE else Color.BLACK)
        } else {
            textView.background = null
            textView.setTextColor(android.R.attr.textColorPrimary)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}