// registreer.kt
package za.co.jpsoft.winkerkreader

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import java.io.IOException

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
        // Hide progress bars initially
        val progressBar = findViewById<ProgressBar>(R.id.reg_indeterminateBar)
        val progressBar2 = findViewById<ProgressBar>(R.id.reg_indeterminateBar2)
        progressBar.visibility = View.GONE
        progressBar2.visibility = View.GONE

        // Set device ID
        winkerkEntry.id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // Set about text
        findViewById<TextView>(R.id.reg_about).text = getAboutText()

        // Display device ID
        findViewById<EditText>(R.id.WinkerkReaderID).setText(winkerkEntry.id)
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
        findViewById<ImageView>(R.id.reg).setOnClickListener(::handleRegistrationClick)
        findViewById<ImageView>(R.id.reg_stuur_epos).setOnClickListener(::handleEmailClick)
        findViewById<ImageView>(R.id.reg_stuur_sms).setOnClickListener(::handleSmsClick)
    }

    private fun handleUpdateClick(view: View) {
        val userData = collectUserData()
        saveUserData(userData)
        Toast.makeText(this, "Inligting gestoor", Toast.LENGTH_LONG).show()
    }

    private fun handleRegistrationClick(view: View) {
        val kodeView = findViewById<EditText>(R.id.reg_kode)
        val regKode = kodeView.text.toString().trim()

        if (regKode.isEmpty()) {
            Toast.makeText(this, "Voer asseblief 'n registrasie kode in", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val userData = collectUserData()
            saveUserData(userData)

            if (Installation.write(regKode, this)) {
                Toast.makeText(this, "Kode gestoor\nDankie vir registrasie", Toast.LENGTH_LONG).show()
                restartApplication()
            } else {
                showRegistrationError()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            showRegistrationError()
        }
    }

    private fun handleEmailClick(view: View) {
        val userData = collectUserData()
        saveUserData(userData)
        sendRegistrationEmail(userData)
    }

    private fun handleSmsClick(view: View) {
        val userData = collectUserData()
        saveUserData(userData)
        sendRegistrationSms(userData)
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
        settings.edit()
            .putString("Naam", userData.naam)
            .putString("Van", userData.van)
            .putString("E-Pos", userData.epos)
            .putString("Selfoon", userData.selNo)
            .putString("Gemeente", userData.gemNaam)
            .putString("Gemeente_Epos", userData.gemEpos)
            .apply()
    }

    private fun showRegistrationError() {
        findViewById<TextView>(R.id.reg_reg).apply {
            text = getString(R.string.ongereg)
            isSelected = true
        }
        findViewById<ProgressBar>(R.id.reg_indeterminateBar).visibility = View.GONE
        findViewById<ProgressBar>(R.id.reg_indeterminateBar2).visibility = View.GONE
    }

    private fun restartApplication() {
        val restartIntent = Intent(this, MainActivity2::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            123456,
            restartIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent)
        Runtime.getRuntime().exit(0)
    }

    private fun sendRegistrationEmail(userData: UserData) {
        val body = buildRegistrationMessage(userData)
        val emailAddress = "jpgrobler@gmail.com"

        val selectorIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
        }

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
            putExtra(Intent.EXTRA_SUBJECT, "Registrasie kode vir WinkerkReader Toep asb")
            putExtra(Intent.EXTRA_TEXT, body)
            selector = selectorIntent
        }

        try {
            startActivity(emailIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Kan nie e-pos app oopmaak nie", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendRegistrationSms(userData: UserData) {
        val body = buildRegistrationMessage(userData)

        val smsUri = Uri.parse("smsto:+27822932795")
        val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
            putExtra("sms_body", body)
        }

        try {
            startActivity(smsIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Kan nie SMS app oopmaak nie", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildRegistrationMessage(userData: UserData): String {
        return "Kode vir WinkerkReader asb:\n" +
                "${userData.naam} ${userData.van}\n" +
                "${userData.selNo}\n" +
                "${userData.epos}\n" +
                "${winkerkEntry.GEMEENTE_NAAM}\n" +
                winkerkEntry.id
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