package za.co.jpsoft.winkerkreader;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import za.co.jpsoft.winkerkreader.data.WinkerkContract;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.*;
import java.io.IOException;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by Pieter Grobler on 21/08/2017.
 */

public class registreer extends AppCompatActivity {
    @Override
    public void onBackPressed() {
        finish();
        super.onBackPressed();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registreer);

        initializeUI();
        populateUserData();
        setupClickListeners();
    }

    private void initializeUI() {
        // Hide progress bars initially
        ProgressBar progressBar = findViewById(R.id.reg_indeterminateBar);
        ProgressBar progressBar2 = findViewById(R.id.reg_indeterminateBar2);
        progressBar.setVisibility(View.GONE);
        progressBar2.setVisibility(View.GONE);

        // Set device ID
        WinkerkContract.winkerkEntry.id = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID);

        // Set about text
        TextView aboutView = findViewById(R.id.reg_about);
        aboutView.setText(getAboutText());

        // Display device ID
        EditText idView = findViewById(R.id.WinkerkReaderID);
        idView.setText(WinkerkContract.winkerkEntry.id);
    }

    private String getAboutText() {
        return "WinkerkReader is geskryf deur Pieter Grobler \n" +
                "Kontak no 082 293 2795 / jpgrobler@gmail.com \n" +
                "Donasies is welkom\n" +
                "Capitec Spaar Rek no 1542201649 \n" +
                "Die program is nie 'n produk van INFOKERK nie \n" +
                "Die program wysig geen WINKERK data nie!";
    }

    private void populateUserData() {
        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);

        // Populate user fields with saved data
        setTextIfNotEmpty(R.id.reg_naam, settings.getString("Naam", ""));
        setTextIfNotEmpty(R.id.reg_van, settings.getString("Van", ""));
        setTextIfNotEmpty(R.id.reg_epos, settings.getString("E-Pos", ""));
        setTextIfNotEmpty(R.id.reg_selno, settings.getString("Selfoon", ""));

        // Populate gemeente fields if available
        if (!WinkerkContract.winkerkEntry.GEMEENTE_NAAM.equals("Onbekend")) {
            EditText gemNaamView = findViewById(R.id.reg_gemeente);
            EditText gemEposView = findViewById(R.id.reg_gemeente_epos);
            gemNaamView.setText(WinkerkContract.winkerkEntry.GEMEENTE_NAAM);
            gemEposView.setText(WinkerkContract.winkerkEntry.GEMEENTE_EPOS);
        }
    }

    private void setTextIfNotEmpty(int viewId, String text) {
        if (!text.isEmpty()) {
            EditText editText = findViewById(viewId);
            editText.setText(text);
        }
    }

    private void setupClickListeners() {
        ImageView regOpdateer = findViewById(R.id.reg_opdateer);
        ImageView regkode = findViewById(R.id.reg);
        ImageView eposVirKode = findViewById(R.id.reg_stuur_epos);
        ImageView smsVirKode = findViewById(R.id.reg_stuur_sms);

        regOpdateer.setOnClickListener(this::handleUpdateClick);
        regkode.setOnClickListener(this::handleRegistrationClick);
        eposVirKode.setOnClickListener(this::handleEmailClick);
        smsVirKode.setOnClickListener(this::handleSmsClick);
    }

    private void handleUpdateClick(View view) {
        UserData userData = collectUserData();
        saveUserData(userData);
        Toast.makeText(this, "Inligting gestoor", Toast.LENGTH_LONG).show();
    }

    private void handleRegistrationClick(View view) {
        EditText kodeView = findViewById(R.id.reg_kode);
        String regKode = kodeView.getText().toString().trim();

        if (regKode.isEmpty()) {
            Toast.makeText(this, "Voer asseblief 'n registrasie kode in", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            UserData userData = collectUserData();
            saveUserData(userData);

            if (Installation.write(regKode, this)) {
                Toast.makeText(this, "Kode gestoor\nDankie vir registrasie", Toast.LENGTH_LONG).show();
                restartApplication();
            } else {
                showRegistrationError();
            }
        } catch (IOException e) {
            e.printStackTrace();
            showRegistrationError();
        }
    }

    private void handleEmailClick(View view) {
        UserData userData = collectUserData();
        saveUserData(userData);
        sendRegistrationEmail(userData);
    }

    private void handleSmsClick(View view) {
        UserData userData = collectUserData();
        saveUserData(userData);
        sendRegistrationSms(userData);
    }

    private UserData collectUserData() {
        EditText naamView = findViewById(R.id.reg_naam);
        EditText vanView = findViewById(R.id.reg_van);
        EditText eposView = findViewById(R.id.reg_epos);
        EditText selNoView = findViewById(R.id.reg_selno);
        EditText gemNaamView = findViewById(R.id.reg_gemeente);
        EditText gemEposView = findViewById(R.id.reg_gemeente_epos);

        UserData userData = new UserData();
        userData.naam = naamView.getText().toString().trim();
        userData.van = vanView.getText().toString().trim();
        userData.epos = eposView.getText().toString().trim();
        userData.selNo = selNoView.getText().toString().trim();
        userData.gemNaam = gemNaamView.getText().toString().trim();
        userData.gemEpos = gemEposView.getText().toString().trim();

        return userData;
    }

    private void saveUserData(UserData userData) {
        // Update global gemeente data if changed
        WinkerkContract.winkerkEntry.GEMEENTE_EPOS = userData.gemEpos;
        if (!userData.gemNaam.isEmpty() &&
                !WinkerkContract.winkerkEntry.GEMEENTE_NAAM.equals(userData.gemNaam)) {
            WinkerkContract.winkerkEntry.GEMEENTE_NAAM = userData.gemNaam;
        }

        // Save to SharedPreferences
        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("Naam", userData.naam);
        editor.putString("Van", userData.van);
        editor.putString("E-Pos", userData.epos);
        editor.putString("Selfoon", userData.selNo);
        editor.putString("Gemeente", userData.gemNaam);
        editor.putString("Gemeente_Epos", userData.gemEpos);
        editor.apply();
    }

    private void showRegistrationError() {
        TextView regstatus = findViewById(R.id.reg_reg);
        regstatus.setText(getString(R.string.ongereg));
        regstatus.setSelected(true);

        ProgressBar progressBar = findViewById(R.id.reg_indeterminateBar);
        ProgressBar progressBar2 = findViewById(R.id.reg_indeterminateBar2);
        progressBar.setVisibility(View.GONE);
        progressBar2.setVisibility(View.GONE);
    }

    private void restartApplication() {
        Intent restartIntent = new Intent(this, MainActivity2.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                123456,
                restartIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent);
        Runtime.getRuntime().exit(0);
    }

    private void sendRegistrationEmail(UserData userData) {
        String body = buildRegistrationMessage(userData);
        String emailAddress = "jpgrobler@gmail.com";

        Intent selectorIntent = new Intent(Intent.ACTION_SENDTO);
        selectorIntent.setData(Uri.parse("mailto:"));

        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailAddress});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Registrasie kode vir WinkerkReader Toep asb");
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);
        emailIntent.setSelector(selectorIntent);

        try {
            startActivity(emailIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Kan nie e-pos app oopmaak nie", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendRegistrationSms(UserData userData) {
        String body = buildRegistrationMessage(userData);

        Uri smsUri = Uri.parse("smsto:+27822932795");
        Intent smsIntent = new Intent(Intent.ACTION_SENDTO, smsUri);
        smsIntent.putExtra("sms_body", body);

        try {
            startActivity(smsIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Kan nie SMS app oopmaak nie", Toast.LENGTH_SHORT).show();
        }
    }

    private String buildRegistrationMessage(UserData userData) {
        return "Kode vir WinkerkReader asb:\n" +
                userData.naam + " " + userData.van + "\n" +
                userData.selNo + "\n" +
                userData.epos + "\n" +
                WinkerkContract.winkerkEntry.GEMEENTE_NAAM + "\n" +
                WinkerkContract.winkerkEntry.id;
    }

    // Helper class to hold user data
    private static class UserData {
        String naam = "";
        String van = "";
        String epos = "";
        String selNo = "";
        String gemNaam = "";
        String gemEpos = "";
    }
}

