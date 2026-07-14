package za.co.jpsoft.winkerkreader.ui.activities

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.snackbar.Snackbar
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.databinding.RegistreerBinding
import za.co.jpsoft.winkerkreader.utils.EdgeToEdgeHelper
import za.co.jpsoft.winkerkreader.utils.SettingsManager

class RegistreerActivity : AppCompatActivity() {

    private lateinit var binding: RegistreerBinding
    private var isDataChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RegistreerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        initializeUI()
        populateUserData()
        setupTextWatchers()
        setupClickListeners()
        setupBackPressedCallback()
    }


    private fun setupWindowInsets() {
        EdgeToEdgeHelper.setupEdgeToEdge(window, isLightStatusBar = true)
    }

    private fun initializeUI() {
        binding.regAbout.text = getAboutText()

        // Set up focus change listeners for better UX
        listOf(
            binding.regNaam,
            binding.regVan,
            binding.regEpos,
            binding.regSelno,
            binding.regGemeente,
            binding.regGemeenteEpos
        ).forEach { editText ->
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    editText.parent?.parent?.requestLayout()
                }
            }
        }
    }

    private fun getAboutText(): String {
        return "WinkerkReader is geskryf deur Pieter Grobler\n" +
                "Kontak no 082 293 2795 / jpgrobler@gmail.com\n" +
                "Donasies is welkom\n" +
                "Capitec Spaar Rek no 1542201649\n" +
                "Die program is nie 'n produk van INFOKERK nie\n" +
                "Die program wysig geen WINKERK data nie!"
    }

    private fun populateUserData() {
        val settings = getSharedPreferences(PREFS_USER_INFO, 0)

        // Populate user fields with saved data
        binding.regNaam.setText(settings.getString("Naam", ""))
        binding.regVan.setText(settings.getString("Van", ""))
        binding.regEpos.setText(settings.getString("E-Pos", ""))
        binding.regSelno.setText(settings.getString("Selfoon", ""))

        // Populate gemeente fields if available
        val settingsManager = SettingsManager.getInstance(this)
        if (settingsManager.gemeenteNaam != "Onbekend") {
            binding.regGemeente.setText(settingsManager.gemeenteNaam)
            binding.regGemeenteEpos.setText(settingsManager.gemeenteEpos)
        }
    }

    private fun setupTextWatchers() {
        val fields = listOf(
            binding.regNaam,
            binding.regVan,
            binding.regEpos,
            binding.regSelno,
            binding.regGemeente,
            binding.regGemeenteEpos
        )

        fields.forEach { editText ->
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    validateField(editText)
                }
            }
        }
    }

    private fun validateField(editText: com.google.android.material.textfield.TextInputEditText) {
        val layout = editText.parent as? com.google.android.material.textfield.TextInputLayout
        val text = editText.text?.toString()?.trim() ?: ""

        when (editText.id) {
            binding.regEpos.id -> {
                if (text.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(text)
                        .matches()
                ) {
                    layout?.error = getString(R.string.invalid_email)
                } else {
                    layout?.error = null
                }
            }

            binding.regGemeenteEpos.id -> {
                if (text.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(text)
                        .matches()
                ) {
                    layout?.error = getString(R.string.invalid_email)
                } else {
                    layout?.error = null
                }
            }

            binding.regSelno.id -> {
                if (text.isNotEmpty() && !text.matches(Regex("^[0-9\\-\\+\\s]+$"))) {
                    layout?.error = getString(R.string.invalid_phone)
                } else {
                    layout?.error = null
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.regOpdateer.setOnClickListener(::handleUpdateClick)
    }

    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isDataChanged) {
                    showDiscardConfirmation()
                } else {
                    finish()
                }
            }
        })
    }

    private fun showDiscardConfirmation() {
        Snackbar.make(
            binding.root,
            R.string.discard_changes,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.discard) {
            finish()
        }.setActionTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnPrimary))
            .show()
    }

    private fun handleUpdateClick(unused: View) {
        val userData = collectUserData()

        // Validate required fields
        if (userData.naam.isEmpty() || userData.van.isEmpty()) {
            Snackbar.make(
                binding.root,
                R.string.required_fields_missing,
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        saveUserData(userData)
        isDataChanged = false

        Snackbar.make(
            binding.root,
            R.string.info_saved,
            Snackbar.LENGTH_LONG
        ).setAnchorView(binding.regOpdateer)
            .show()
    }

    private fun collectUserData(): UserData {
        return UserData(
            naam = binding.regNaam.text.toString().trim(),
            van = binding.regVan.text.toString().trim(),
            epos = binding.regEpos.text.toString().trim(),
            selNo = binding.regSelno.text.toString().trim(),
            gemNaam = binding.regGemeente.text.toString().trim(),
            gemEpos = binding.regGemeenteEpos.text.toString().trim()
        )
    }

    private fun saveUserData(userData: UserData) {
        // Update global gemeente data if changed
        val settingsManager = SettingsManager.getInstance(this)
        settingsManager.gemeenteEpos = userData.gemEpos
        if (userData.gemNaam.isNotEmpty() && settingsManager.gemeenteNaam != userData.gemNaam) {
            settingsManager.gemeenteNaam = userData.gemNaam
        }

        // Save to SharedPreferences
        val settings = getSharedPreferences(PREFS_USER_INFO, 0)
        settings.edit {
            putString("Naam", userData.naam)
            putString("Van", userData.van)
            putString("E-Pos", userData.epos)
            putString("Selfoon", userData.selNo)
            putString("Gemeente", userData.gemNaam)
            putString("Gemeente_Epos", userData.gemEpos)
        }
    }

    private fun getColorFromAttr(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    // Helper class to hold user data
    data class UserData(
        var naam: String = "",
        var van: String = "",
        var epos: String = "",
        var selNo: String = "",
        var gemNaam: String = "",
        var gemEpos: String = ""
    )
}