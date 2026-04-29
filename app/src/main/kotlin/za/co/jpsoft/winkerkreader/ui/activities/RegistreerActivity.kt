package za.co.jpsoft.winkerkreader.ui.activities

import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.WinkerkReader
import za.co.jpsoft.winkerkreader.R
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageView

import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

import androidx.core.content.edit
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO

/**
 * Created by Pieter Grobler on 21/08/2017.
 */
class RegistreerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.registreer)

        initializeUI()
        populateUserData()
        setupClickListeners()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun initializeUI() {

        // Get device ID using privacy-compliant manager
        val deviceId = za.co.jpsoft.winkerkreader.utils.DeviceIdManager.getDeviceId(this)

        // Set about text
        findViewById<TextView>(R.id.reg_about).text = getAboutText()

    }

    private fun getAboutText(): String {
        return "WinkerkReader is geskryf deur Pieter Grobler \n" +
                "Kontak no 082 293 2795 / jpgrobler@gmail.com \n" +
                "Donasies is welkom\n" +
                "Capitec Spaar Rek no 1542201649 \n" +
                "Die program is nie 'n produk van INFOKERK nie \n" +
                "Die program wysig geen WINKERK data nie!"
    }

    private fun populateUserData() {
        val settings = getSharedPreferences(PREFS_USER_INFO, 0)

        // Populate user fields with saved data
        setTextIfNotEmpty(R.id.reg_naam, settings.getString("Naam", ""))
        setTextIfNotEmpty(R.id.reg_van, settings.getString("Van", ""))
        setTextIfNotEmpty(R.id.reg_epos, settings.getString("E-Pos", ""))
        setTextIfNotEmpty(R.id.reg_selno, settings.getString("Selfoon", ""))

        // Populate gemeente fields if available
        val settingsManager = SettingsManager.getInstance(this)
        if (settingsManager.gemeenteNaam != "Onbekend") {
            findViewById<EditText>(R.id.reg_gemeente).setText(settingsManager.gemeenteNaam)
            findViewById<EditText>(R.id.reg_gemeente_epos).setText(settingsManager.gemeenteEpos)
        }
    }

    private fun setTextIfNotEmpty(viewId: Int, text: String?) {
        if (!text.isNullOrEmpty()) {
            findViewById<EditText>(viewId).setText(text)
        }
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.reg_opdateer).setOnClickListener(::handleUpdateClick)
    }

    private fun handleUpdateClick(view: View) {
        val userData = collectUserData()
        saveUserData(userData)
        Toast.makeText(this, "Inligting gestoor", Toast.LENGTH_LONG).show()
    }

    private fun collectUserData(): UserData {
        return UserData(
            naam = findViewById<EditText>(R.id.reg_naam).text.toString().trim(),
            van = findViewById<EditText>(R.id.reg_van).text.toString().trim(),
            epos = findViewById<EditText>(R.id.reg_epos).text.toString().trim(),
            selNo = findViewById<EditText>(R.id.reg_selno).text.toString().trim(),
            gemNaam = findViewById<EditText>(R.id.reg_gemeente).text.toString().trim(),
            gemEpos = findViewById<EditText>(R.id.reg_gemeente_epos).text.toString().trim()
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

    // Helper class to hold user data (static nested)
    class UserData(
        var naam: String = "",
        var van: String = "",
        var epos: String = "",
        var selNo: String = "",
        var gemNaam: String = "",
        var gemEpos: String = ""
    )
}