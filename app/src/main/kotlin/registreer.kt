// registreer.kt
package za.co.jpsoft.winkerkreader


import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageView

import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import androidx.core.content.edit


/**
 * Created by Pieter Grobler on 21/08/2017.
 */
class registreer : AppCompatActivity() {

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

        // Set device ID
        winkerkEntry.id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

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
        if (winkerkEntry.GEMEENTE_NAAM != "Onbekend") {
            findViewById<EditText>(R.id.reg_gemeente).setText(winkerkEntry.GEMEENTE_NAAM)
            findViewById<EditText>(R.id.reg_gemeente_epos).setText(winkerkEntry.GEMEENTE_EPOS)
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
        winkerkEntry.GEMEENTE_EPOS = userData.gemEpos
        if (userData.gemNaam.isNotEmpty() && winkerkEntry.GEMEENTE_NAAM != userData.gemNaam) {
            winkerkEntry.GEMEENTE_NAAM = userData.gemNaam
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