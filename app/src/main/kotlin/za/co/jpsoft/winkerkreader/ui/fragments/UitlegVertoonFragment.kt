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
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import yuku.ambilwarna.AmbilWarnaDialog
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.FragmentUitlegVertoonBinding
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs.ThemeMode
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.LanguagePrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs

@AndroidEntryPoint
class UitlegVertoonFragment : Fragment() {

    @Inject
    lateinit var memberListPrefs: MemberListPrefs
    @Inject
    lateinit var appearancePrefs: AppearancePrefs
    @Inject
    lateinit var congregationPrefs: CongregationPrefs
    @Inject
    lateinit var languagePrefs: LanguagePrefs

    private var initialLanguageCode: String = "af"

    private var _binding: FragmentUitlegVertoonBinding? = null
    private val binding get() = _binding!!

    private var isInitializing = true
    private var isDisplayDirty = false
    private var isColorsDirty = false
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

        loadPreferences()
        setupListeners()
        isInitializing = false
        isDisplayDirty = false
        isColorsDirty = false

        when (appearancePrefs.themeMode) {
            AppearancePrefs.ThemeMode.LIGHT -> binding.themeModeLight.isChecked = true
            AppearancePrefs.ThemeMode.DARK -> binding.themeModeDark.isChecked = true
            else -> binding.themeModeSystem.isChecked = true
        }

        binding.themeModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.theme_mode_light -> AppearancePrefs.ThemeMode.LIGHT
                R.id.theme_mode_dark -> AppearancePrefs.ThemeMode.DARK
                else -> AppearancePrefs.ThemeMode.SYSTEM
            }
            appearancePrefs.themeMode = mode
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
            ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun loadPreferences() {
        val checkboxes = listOf(
            binding.uitlegFoto to memberListPrefs.isListFoto,
            binding.uitlegEpos to memberListPrefs.isListEpos,
            binding.uitlegWhatsap to memberListPrefs.isListWhatsapp,
            binding.uitlegVerjaarsdag to memberListPrefs.isListVerjaarBlok,
            binding.uitlegOuderdom to memberListPrefs.isListOuderdom,
            binding.uitlegHuweliksdag to memberListPrefs.isListHuwelikBlok,
            binding.uitlegWyk to memberListPrefs.isListWyk,
            binding.uitlegSelfoon to memberListPrefs.isListSelfoon,
            binding.uitlegTelefoon to memberListPrefs.isListTelefoon,
            binding.congregationIndicatorSwitch to congregationPrefs.useCongregationIndicator
        )
        checkboxes.forEach { (cb, value) ->
            cb.isChecked = value
            initialCheckboxes[cb.id] = value
        }
        binding.force60hz.isChecked = appearancePrefs.force60Hz
        initialLayout = memberListPrefs.defLayout
        for (i in 0 until binding.layoutOpsies.count) {
            val item = binding.layoutOpsies.getItemAtPosition(i).toString()
            if (item.equals(initialLayout, ignoreCase = true)) {
                binding.layoutOpsies.setSelection(i)
                break
            }
        }

        initialColors[0] = congregationPrefs.gemeenteKleur
        initialColors[1] = congregationPrefs.gemeente2Kleur
        initialColors[2] = congregationPrefs.gemeente3Kleur
        initialColors[3] = congregationPrefs.inactiveBackgroundColor

        updateTextViewBackground(binding.gem1, initialColors[0])
        updateTextViewBackground(binding.gem2, initialColors[1])
        updateTextViewBackground(binding.gem3, initialColors[2])
        updateTextViewBackground(binding.inactiveColorPreview, initialColors[3])

        if (congregationPrefs.gemeenteNaam.isNotBlank()) {
            binding.gem1.text = congregationPrefs.gemeenteNaam
        }
        if (congregationPrefs.gemeente2Naam.isNotBlank()) {
            binding.gem2.text = congregationPrefs.gemeente2Naam
        }
        if (congregationPrefs.gemeente3Naam.isNotBlank()) {
            binding.gem3.text = congregationPrefs.gemeente3Naam
        }
        initialCongregationIndicator = congregationPrefs.useCongregationIndicator
        binding.congregationIndicatorSwitch.isChecked = initialCongregationIndicator

        initialLanguageCode = languagePrefs.languageCode
        when (initialLanguageCode) {
            "en" -> binding.languageEn.isChecked = true
            else -> binding.languageAf.isChecked = true
        }
    }

    private fun setupListeners() {
        initialCheckboxes.keys.forEach { id ->
            val cb = binding.root.findViewById<android.widget.CheckBox>(id)
            cb.setOnCheckedChangeListener { _, _ -> onDisplayChanged() }
        }

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
        binding.force60hz.setOnCheckedChangeListener { _, isChecked ->
            appearancePrefs.force60Hz = isChecked
            // Herbegin die activity om die nuwe instelling te laai
            activity?.recreate()
        }
        binding.gem1.setOnClickListener { openColorPickerDialog(it, 1) }
        binding.gem2.setOnClickListener { openColorPickerDialog(it, 2) }
        binding.gem3.setOnClickListener { openColorPickerDialog(it, 3) }
        binding.inactiveColorPreview.setOnClickListener { openColorPickerDialog(it, 4) }

        binding.uitlegStoor.setOnClickListener { saveDisplaySettings() }
        binding.saveColor.setOnClickListener { saveColorSettings() }

        binding.saveLanguage.setOnClickListener {
            val newLang = if (binding.languageEn.isChecked) "en" else "af"
            if (newLang != initialLanguageCode) {
                languagePrefs.languageCode = newLang
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Herbegin nodig")
                    .setMessage("Die app moet herbegin word om die nuwe taal toe te pas.")
                    .setPositiveButton("Herbegin nou") { _, _ ->
                        activity?.recreate()
                    }
                    .setNegativeButton("Later", null)
                    .show()
            } else {
                Toast.makeText(requireContext(), "Taal is reeds geselekteer", Toast.LENGTH_SHORT)
                    .show()
            }
        }
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
        val displayCheckboxIds = listOf(
            R.id.uitleg_foto, R.id.uitleg_epos, R.id.uitleg_whatsap,
            R.id.uitleg_verjaarsdag, R.id.uitleg_ouderdom, R.id.uitleg_Huweliksdag,
            R.id.uitleg_wyk, R.id.uitleg_selfoon, R.id.uitleg_telefoon,
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
        initialCheckboxes.forEach { (id, _) ->
            val cb = binding.root.findViewById<android.widget.CheckBox>(id)
            when (id) {
                R.id.uitleg_foto -> memberListPrefs.isListFoto = cb.isChecked
                R.id.uitleg_epos -> memberListPrefs.isListEpos = cb.isChecked
                R.id.uitleg_whatsap -> memberListPrefs.isListWhatsapp = cb.isChecked
                R.id.uitleg_verjaarsdag -> memberListPrefs.isListVerjaarBlok = cb.isChecked
                R.id.uitleg_ouderdom -> memberListPrefs.isListOuderdom = cb.isChecked
                R.id.uitleg_Huweliksdag -> memberListPrefs.isListHuwelikBlok = cb.isChecked
                R.id.uitleg_wyk -> memberListPrefs.isListWyk = cb.isChecked
                R.id.uitleg_selfoon -> memberListPrefs.isListSelfoon = cb.isChecked
                R.id.uitleg_telefoon -> memberListPrefs.isListTelefoon = cb.isChecked
            }
        }
        memberListPrefs.defLayout = binding.layoutOpsies.selectedItem?.toString() ?: "GESINNE"
        congregationPrefs.useCongregationIndicator = binding.congregationIndicatorSwitch.isChecked

        initialCheckboxes.forEach { (id, _) ->
            val cb = binding.root.findViewById<android.widget.CheckBox>(id)
            initialCheckboxes[id] = cb.isChecked
        }
        initialLayout = memberListPrefs.defLayout
        initialCongregationIndicator = congregationPrefs.useCongregationIndicator
        isDisplayDirty = false
        updateSaveButtonState()
        Toast.makeText(requireContext(), "Vertoon-instellings gestoor", Toast.LENGTH_SHORT).show()
    }

    private fun saveColorSettings() {
        initialColors[0] = getCurrentColor(R.id.gem1)
        initialColors[1] = getCurrentColor(R.id.gem2)
        initialColors[2] = getCurrentColor(R.id.gem3)
        initialColors[3] = getCurrentColor(R.id.inactive_color_preview)

        congregationPrefs.gemeenteKleur = initialColors[0]
        congregationPrefs.gemeente2Kleur = initialColors[1]
        congregationPrefs.gemeente3Kleur = initialColors[2]
        congregationPrefs.inactiveBackgroundColor = initialColors[3]

        isColorsDirty = false
        updateSaveButtonState()
        Toast.makeText(requireContext(), "Kleure gestoor", Toast.LENGTH_SHORT).show()
    }

    private fun openColorPickerDialog(view: View, colorIndex: Int) {
        val currentColor = when (colorIndex) {
            1 -> congregationPrefs.gemeenteKleur
            2 -> congregationPrefs.gemeente2Kleur
            3 -> congregationPrefs.gemeente3Kleur
            4 -> congregationPrefs.inactiveBackgroundColor
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