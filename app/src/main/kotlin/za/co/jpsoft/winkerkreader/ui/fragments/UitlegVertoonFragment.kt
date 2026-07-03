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

class UitlegVertoonFragment : Fragment() {

    private var _binding: FragmentUitlegVertoonBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

    private var isInitializing = true
    private var isDirty = false

    // Initial values: [gem1, gem2, gem3, inactive]
    private var initialCheckboxes = mutableMapOf<Int, Boolean>()
    private var initialLayout: String = "GESINNE"
    private val initialColors = mutableListOf(
        Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE
    ) // gem1, gem2, gem3, inactive


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
        isDirty = false

        when (settingsManager.themeMode) {
            SettingsManager.ThemeMode.LIGHT -> binding.themeModeLight.isChecked = true
            SettingsManager.ThemeMode.DARK -> binding.themeModeDark.isChecked = true
            else -> binding.themeModeSystem.isChecked = true
        }

        binding.themeModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.theme_mode_light -> SettingsManager.ThemeMode.LIGHT
                R.id.theme_mode_dark -> SettingsManager.ThemeMode.DARK
                else -> SettingsManager.ThemeMode.SYSTEM
            }
            settingsManager.themeMode = mode
            applyTheme(mode)
            Toast.makeText(requireContext(), "Tema verander. Herbegin die app.", Toast.LENGTH_SHORT).show()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            updateSaveButtonState()
        }, 300)
    }

    private fun applyTheme(mode: SettingsManager.ThemeMode) {
        when (mode) {
            SettingsManager.ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            SettingsManager.ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            SettingsManager.ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun loadPreferences() {
        // Icons
        val checkboxes = listOf(
            binding.uitlegFoto to settingsManager.isListFoto,
            binding.uitlegEpos to settingsManager.isListEpos,
            binding.uitlegWhatsap to settingsManager.isListWhatsapp,
            binding.uitlegVerjaarsdag to settingsManager.isListVerjaarBlok,
            binding.uitlegOuderdom to settingsManager.isListOuderdom,
            binding.uitlegHuweliksdag to settingsManager.isListHuwelikBlok,
            binding.uitlegWyk to settingsManager.isListWyk,
            binding.uitlegSelfoon to settingsManager.isListSelfoon,
            binding.uitlegTelefoon to settingsManager.isListTelefoon
        )
        checkboxes.forEach { (cb, value) ->
            cb.isChecked = value
            initialCheckboxes[cb.id] = value
        }

        // Layout
        initialLayout = settingsManager.defLayout
        for (i in 0 until binding.layoutOpsies.count) {
            val item = binding.layoutOpsies.getItemAtPosition(i).toString()
            if (item.equals(initialLayout, ignoreCase = true)) {
                binding.layoutOpsies.setSelection(i)
                break
            }
        }

        // Colors: gem1, gem2, gem3, inactive
        initialColors[0] = settingsManager.gemeenteKleur
        initialColors[1] = settingsManager.gemeente2Kleur
        initialColors[2] = settingsManager.gemeente3Kleur
        initialColors[3] = settingsManager.inactiveBackgroundColor

        updateTextViewBackground(binding.gem1, initialColors[0])
        updateTextViewBackground(binding.gem2, initialColors[1])
        updateTextViewBackground(binding.gem3, initialColors[2])
        updateTextViewBackground(binding.inactiveColorPreview, initialColors[3])
    }

    private fun setupListeners() {
        // CheckBox listeners
        initialCheckboxes.keys.forEach { id ->
            val cb = binding.root.findViewById<android.widget.CheckBox>(id)
            cb.setOnCheckedChangeListener { _, _ -> onUserChanged() }
        }

        // Layout spinner
        binding.layoutOpsies.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = parent?.getItemAtPosition(position)?.toString() ?: return
                if (selected != initialLayout) onUserChanged()
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

    private fun onUserChanged() {
        if (!isInitializing) {
            isDirty = true
            updateSaveButtonState()
        }
    }

    private fun updateSaveButtonState() {
        val enabled = isDirty || isAnySettingChanged()
        binding.uitlegStoor.isEnabled = enabled
        binding.saveColor.isEnabled = enabled
        binding.uitlegStoor.alpha = if (enabled) 1.0f else 0.4f
        binding.saveColor.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun isAnySettingChanged(): Boolean {
        // Check checkboxes
        initialCheckboxes.forEach { (id, initialValue) ->
            val cb = binding.root.findViewById<android.widget.CheckBox>(id)
            if (cb.isChecked != initialValue) return true
        }

        // Layout
        val currentLayout = binding.layoutOpsies.selectedItem?.toString() ?: "GESINNE"
        if (currentLayout != initialLayout) return true

        // Colors: gem1, gem2, gem3, inactive
        if (getCurrentColor(R.id.gem1) != initialColors[0]) return true
        if (getCurrentColor(R.id.gem2) != initialColors[1]) return true
        if (getCurrentColor(R.id.gem3) != initialColors[2]) return true
        if (getCurrentColor(R.id.inactive_color_preview) != initialColors[3]) return true

        return false
    }

    // Helper to get current color from any TextView's background
    private fun getCurrentColor(viewId: Int): Int {
        val view = binding.root.findViewById<TextView>(viewId) ?: return -1
        return (view.background as? android.graphics.drawable.ColorDrawable)?.color ?: -1
    }

    private fun saveDisplaySettings() {
        initialCheckboxes.forEach { (id, _) ->
            val cb = binding.root.findViewById<android.widget.CheckBox>(id)
            when (id) {
                R.id.uitleg_foto -> settingsManager.isListFoto = cb.isChecked
                R.id.uitleg_epos -> settingsManager.isListEpos = cb.isChecked
                R.id.uitleg_whatsap -> settingsManager.isListWhatsapp = cb.isChecked
                R.id.uitleg_verjaarsdag -> settingsManager.isListVerjaarBlok = cb.isChecked
                R.id.uitleg_ouderdom -> settingsManager.isListOuderdom = cb.isChecked
                R.id.uitleg_Huweliksdag -> settingsManager.isListHuwelikBlok = cb.isChecked
                R.id.uitleg_wyk -> settingsManager.isListWyk = cb.isChecked
                R.id.uitleg_selfoon -> settingsManager.isListSelfoon = cb.isChecked
                R.id.uitleg_telefoon -> settingsManager.isListTelefoon = cb.isChecked
            }
        }
        settingsManager.defLayout = binding.layoutOpsies.selectedItem?.toString() ?: "GESINNE"

        // Update initial values
        initialCheckboxes.forEach { (id, _) ->
            val cb = binding.root.findViewById<android.widget.CheckBox>(id)
            initialCheckboxes[id] = cb.isChecked
        }
        initialLayout = settingsManager.defLayout

        isDirty = false
        updateSaveButtonState()
        Toast.makeText(requireContext(), "Vertoon-instellings gestoor", Toast.LENGTH_SHORT).show()
    }

    private fun saveColorSettings() {
        // Save all four colours
        initialColors[0] = getCurrentColor(R.id.gem1)
        initialColors[1] = getCurrentColor(R.id.gem2)
        initialColors[2] = getCurrentColor(R.id.gem3)
        initialColors[3] = getCurrentColor(R.id.inactive_color_preview)

        settingsManager.gemeenteKleur = initialColors[0]
        settingsManager.gemeente2Kleur = initialColors[1]
        settingsManager.gemeente3Kleur = initialColors[2]
        settingsManager.inactiveBackgroundColor = initialColors[3]

        isDirty = false
        updateSaveButtonState()
        Toast.makeText(requireContext(), "Kleure gestoor", Toast.LENGTH_SHORT).show()
    }

    private fun openColorPickerDialog(view: View, colorIndex: Int) {
        val currentColor = when (colorIndex) {
            1 -> settingsManager.gemeenteKleur
            2 -> settingsManager.gemeente2Kleur
            3 -> settingsManager.gemeente3Kleur
            4 -> settingsManager.inactiveBackgroundColor
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
        onUserChanged()
    }

    private fun updateTextViewBackground(textView: TextView, color: Int) {
        // Only apply if it's NOT the sentinel and NOT transparent
        if (color != Int.MIN_VALUE && color != 0) {
            textView.background = android.graphics.drawable.ColorDrawable(color)
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            textView.setTextColor(if (darkness >= 0.5) Color.WHITE else Color.BLACK)
        } else {
            // Reset to default background
            textView.background = null
            textView.setTextColor(android.R.attr.textColorPrimary)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}