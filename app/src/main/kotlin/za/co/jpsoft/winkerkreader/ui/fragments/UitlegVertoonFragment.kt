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
import androidx.fragment.app.Fragment
import yuku.ambilwarna.AmbilWarnaDialog
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.FragmentUitlegVertoonBinding
import za.co.jpsoft.winkerkreader.utils.SettingsManager

class UitlegVertoonFragment : Fragment() {

    private var _binding: FragmentUitlegVertoonBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

    private var isInitializing = true
    private var isDirty = false

    // Aanvanklike waardes
    private var initialCheckboxes = mutableMapOf<Int, Boolean>()
    private var initialLayout: String = "GESINNE"
    private var initialColors = mutableListOf(-1, -1, -1)

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

        // Wag totdat die spinner adapter gelaai is
        Handler(Looper.getMainLooper()).postDelayed({
            updateSaveButtonState()
        }, 300)
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

        // Colors
        initialColors[0] = settingsManager.gemeenteKleur
        initialColors[1] = settingsManager.gemeente2Kleur
        initialColors[2] = settingsManager.gemeente3Kleur
        updateTextViewBackground(binding.gem1, initialColors[0])
        updateTextViewBackground(binding.gem2, initialColors[1])
        updateTextViewBackground(binding.gem3, initialColors[2])
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

        // Colors
        if (getCurrentGemColor(1) != initialColors[0]) return true
        if (getCurrentGemColor(2) != initialColors[1]) return true
        if (getCurrentGemColor(3) != initialColors[2]) return true

        return false
    }

    private fun getCurrentGemColor(index: Int): Int {
        return when (index) {
            1 -> (binding.gem1.background as? android.graphics.drawable.ColorDrawable)?.color ?: -1
            2 -> (binding.gem2.background as? android.graphics.drawable.ColorDrawable)?.color ?: -1
            3 -> (binding.gem3.background as? android.graphics.drawable.ColorDrawable)?.color ?: -1
            else -> -1
        }
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
        initialColors[0] = getCurrentGemColor(1)
        initialColors[1] = getCurrentGemColor(2)
        initialColors[2] = getCurrentGemColor(3)
        settingsManager.gemeenteKleur = initialColors[0]
        settingsManager.gemeente2Kleur = initialColors[1]
        settingsManager.gemeente3Kleur = initialColors[2]
        isDirty = false
        updateSaveButtonState()
        Toast.makeText(requireContext(), "Kleure gestoor", Toast.LENGTH_SHORT).show()
    }

    private fun openColorPickerDialog(view: View, gemeenteIndex: Int) {
        val currentColor = when (gemeenteIndex) {
            1 -> settingsManager.gemeenteKleur
            2 -> settingsManager.gemeente2Kleur
            3 -> settingsManager.gemeente3Kleur
            else -> -1
        }
        val dialog = AmbilWarnaDialog(
            requireContext(),
            currentColor,
            object : AmbilWarnaDialog.OnAmbilWarnaListener {
                override fun onCancel(dialog: AmbilWarnaDialog) {}
                override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                    handleColorSelected(view, gemeenteIndex, color)
                }
            }
        )
        dialog.show()
    }

    private fun handleColorSelected(view: View, gemeenteIndex: Int, color: Int) {
        if (view is TextView) {
            updateTextViewBackground(view, color)
        } else {
            view.setBackgroundColor(color)
        }
        onUserChanged()
    }

    private fun updateTextViewBackground(textView: TextView, color: Int) {
        if (color != -1 && color != 0) {
            textView.background = android.graphics.drawable.ColorDrawable(color)
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            textView.setTextColor(if (darkness >= 0.5) Color.WHITE else Color.BLACK)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}