package za.co.jpsoft.winkerkreader;
import java.io.*;
import java.net.Socket;
import android.Manifest;
import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.LoaderManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.TextView;
import android.graphics.PorterDuff;
import za.co.jpsoft.winkerkreader.data.SpinnerAdapter;
import za.co.jpsoft.winkerkreader.data.WinkerkContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;


import static android.content.Intent.ACTION_GET_CONTENT;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.DATA_DATUM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_DB;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WkrDir;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WINKERK_DB;

/**
 * Created by Pieter Grobler on 23/08/2017.
 */

public class Laaidatabasis extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "Laaidatabasis";
    private String SERVER_IP;
    private int SERVER_PORT = 49514;
    private static final int FILE_DATA_PORT = 49514;  // Port for file data
    private static final int ACK_PORT = 49515;        // Port for acknowledgment
    private static final int CHECKSUM_PORT = 49516;   // Port for checksum

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    private static final String DB_NAME = WINKERK_DB;
    private static String DB_PATH = "";
    private Context context;


    private long myDownloadReference;
    private BroadcastReceiver recieverDownloadComplete;

    private final String MEDIA_PATH = Environment.getExternalStorageDirectory()
            .getPath() + "/";
    private final String MEDIA_PATH2 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .getPath() + "/";

    private final ArrayList<HashMap<String, String>> fileList = new ArrayList<>();
    private Boolean delete = false;
    public static final int PICKFILE_RESULT_CODE = 1;
    private TextView laai_boodskap;

    TimePickerDialog picker;
    EditText eText;
    Boolean AutoDL = false;

    ReceiveFileTask receiveFileTaskUSB = new ReceiveFileTask();
    ReceiveFileTask receiveFileTaskWiFi = new ReceiveFileTask();


    private final String[] weeksdagArray = {"Sondag", "Maandag", "Dinsdag", "Woensdag", "Donderdag", "Vrydag", "Saterdag"};
    private String data;
    private boolean FlagCancelledUSB;
    private boolean FlagCancelledWiFi;

    private String substringBetween(String str, String open, String close) {
        if (str == null || open == null || close == null) {
            return null;
        }
        int start = str.indexOf(open);
        if (start != -1) {
            int end = str.indexOf(close, start + open.length());
            if (end != -1) {
                return str.substring(start + open.length(), end);
            }
        }
        return null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        //unregisterReceiver(recieverDownloadComplete);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(recieverDownloadComplete);
        } catch (Exception e) {
            Log.e("WinkerkReader Laaidatabasis", "Error: " + e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.laaidatabasis);

        // Initialize shared preferences
        final SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        initializeSettings(settings);

        // Request permissions early
        requestPermissionsIfNeeded();

        // Initialize UI components
        initializeTimePickerUI(settings);
        initializeSpinnerUI(settings);
        initializeCheckboxUI(settings);
        initializeButtons();
        initializeProgressBars();
        initializeDataInfo(settings);

        // Handle file scanning
        scanForDatabaseFiles();

        // Setup file list UI
        setupFileListUI();

        // Handle intent extras for automatic database updates
        handleIntentExtras();

        // Handle automatic download if conditions are met
        handleAutomaticDownload(settings);
    }

    private void initializeSettings(SharedPreferences settings) {
        AutoDL = settings.getBoolean("AUTO_DL", false);
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - Request notification permission if using notifications
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 787);
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 786);
        }
    }

    private void initializeTimePickerUI(SharedPreferences settings) {
        String hour = settings.getString("DL-HOUR", "12");
        String minute = settings.getString("DL-MINUTE", "00");

        eText = findViewById(R.id.tydText);
        eText.setInputType(InputType.TYPE_NULL);
        eText.setText(String.format("%s:%s", hour, minute));
        eText.setOnClickListener(this::showTimePicker);

        EditText ipAddress = findViewById(R.id.server_ip);
        ipAddress.setText(settings.getString("IP", ""));
    }

    private void showTimePicker(View v) {
        final Calendar calendar = Calendar.getInstance();
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int currentMinutes = calendar.get(Calendar.MINUTE);

        picker = new TimePickerDialog(this, (timePicker, selectedHour, selectedMinute) -> {
            String timeText = String.format("%d:%d", selectedHour, selectedMinute);
            eText.setText(timeText);
            saveTimeSettings(selectedHour, selectedMinute);
            Toast.makeText(this, "Tyd opgedateer", Toast.LENGTH_SHORT).show();
        }, currentHour, currentMinutes, true);

        picker.show();
    }

    private void saveTimeSettings(int hour, int minute) {
        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("DL-HOUR", String.valueOf(hour));
        editor.putString("DL-MINUTE", String.valueOf(minute));
        editor.putBoolean("DL-TIMEUPDATE", true);
        editor.putBoolean("AUTO_DL", true);
        editor.apply();
    }

    private void initializeSpinnerUI(SharedPreferences settings) {
        Integer day = settings.getInt("DL-DAY", 6);
        Spinner weeksDag = findViewById(R.id.weeksdag);
        SpinnerAdapter weeksdagStatusAdapter = new SpinnerAdapter(this, null, weeksdagArray);
        weeksDag.setAdapter(weeksdagStatusAdapter);
        weeksDag.setSelection(day - 1);
        weeksDag.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveDaySelection(position + 1);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void saveDaySelection(int day) {
        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("DL-DAY", day);
        editor.apply();
    }

    private void initializeCheckboxUI(SharedPreferences settings) {
        final CheckBox autoDL = findViewById(R.id.alDropBox);
        autoDL.setChecked(AutoDL);
        autoDL.setOnClickListener(v -> handleAutoDLToggle(autoDL, settings));
    }

    private void handleAutoDLToggle(CheckBox autoDL, SharedPreferences settings) {
        AutoDL = autoDL.isChecked();
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("AUTO_DL", AutoDL);
        editor.apply();

        if (AutoDL) {
            setupAlarmForDownload(settings);
        } else {
            cancelAlarmForDownload();
        }
    }

    private void setupAlarmForDownload(SharedPreferences settings) {
        String hour = settings.getString("DL-HOUR", "08");
        String minute = settings.getString("DL-MINUTE", "00");

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hour));
        calendar.set(Calendar.MINUTE, Integer.parseInt(minute));
        calendar.set(Calendar.SECOND, 0);

        Calendar now = Calendar.getInstance();

        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("DL-TIMEUPDATE", true);
        editor.putBoolean("FROM_MENU", false);
        editor.apply();

        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction("DropBoxDownLoad");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);

        long triggerTime = calendar.getTimeInMillis() <= now.getTimeInMillis()
                ? calendar.getTimeInMillis() + AlarmManager.INTERVAL_DAY * 7
                : calendar.getTimeInMillis();

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerTime, AlarmManager.INTERVAL_DAY, pendingIntent);
    }

    private void cancelAlarmForDownload() {
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction("DropBoxDownLoad");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }

    private void initializeButtons() {
        Button setTimeButton = findViewById(R.id.button1);
        setTimeButton.setOnClickListener(this::handleSetTimeClick);

        Button dropboxButton = findViewById(R.id.dbLinkButton);
        dropboxButton.setOnClickListener(this::handleDropboxDownload);

        Button loadButton = findViewById(R.id.laai_laai);
        loadButton.setOnClickListener(this::handleLoadDatabase);

        Button pickFileButton = findViewById(R.id.laai_picker);
        pickFileButton.setOnClickListener(this::handlePickFile);

        Button networkButton = findViewById(R.id.laai_socket);
        networkButton.setOnClickListener(this::handleNetworkTransfer);

        Button usbButton = findViewById(R.id.laai_USB);
        usbButton.setOnClickListener(this::handleUSBTransfer);
    }

    private void handleSetTimeClick(View view) {
        EditText hourEdit = findViewById(R.id.time_hour);
        EditText minuteEdit = findViewById(R.id.time_minute);

        String hour = hourEdit.getText().toString();
        String minute = minuteEdit.getText().toString();

        if (hour.length() < 2) hour = "0" + hour;
        if (minute.length() < 2) minute = "0" + minute;

        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("DL-HOUR", hour);
        editor.putString("DL-MINUTE", minute);
        editor.putBoolean("DL-TIMEUPDATE", true);
        editor.apply();

        Toast.makeText(this, "Tyd opgedateer", Toast.LENGTH_SHORT).show();

        setupAlarmAndNavigateToMain(hour, minute);
    }

    private void setupAlarmAndNavigateToMain(String hour, String minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hour));
        calendar.set(Calendar.MINUTE, Integer.parseInt(minute));
        calendar.set(Calendar.SECOND, 0);

        Calendar now = Calendar.getInstance();

        Intent alarmIntent = new Intent(this, AlarmReceiver.class);
        alarmIntent.setAction("DropBoxDownLoad");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);

        long triggerTime = calendar.getTimeInMillis() <= now.getTimeInMillis()
                ? calendar.getTimeInMillis() + AlarmManager.INTERVAL_DAY * 7
                : calendar.getTimeInMillis();

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerTime, AlarmManager.INTERVAL_DAY * 7, pendingIntent);

        navigateToMainActivity();
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity2.class);
        intent.putExtra("SENDER_CLASS_NAME", "WysVerjaar");
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        WinkerkContract.winkerkEntry.SORTORDER = "VERJAAR";
        WinkerkContract.winkerkEntry.SOEKLIST = false;
        startActivity(intent);
        finish();
    }

    private void handleDropboxDownload(View view) {
        Button dropboxButton = (Button) view;
        dropboxButton.setBackgroundColor(Color.GREEN);

        EditText dbLinkView = findViewById(R.id.db_link);
        String downloadUrl = processDownloadUrl(dbLinkView.getText().toString());

        downloadFromDropBoxUrl(downloadUrl);

        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("DropBox", downloadUrl);
        editor.apply();

        updateUIForDownload();
    }

    private String processDownloadUrl(String url) {
        if (url.contains("www.dropbox.com")) {
            return url.replace("dl=0", "dl=1");
        } else if (url.contains("1drv.ms")) {
            return conv(url);
        } else if (url.contains("drive.google.com")) {
            return conv2(url);
        } else if (url.contains("sharepoint.com")) {
            return conv3(url);
        }
        return url;
    }

    private void updateUIForDownload() {
        TextView laai_boodskap = findViewById(R.id.laai_boodskap);
        laai_boodskap.setText("WKR - Databasis word nou van Dropbox afgelaai\nMoenie die skerm toemaak nie!!");

        findViewById(R.id.dbLinkButton).setVisibility(View.INVISIBLE);
        findViewById(R.id.laai_local).setVisibility(View.GONE);
    }

    private void handleLoadDatabase(View view) {
        RadioGroup radioGroup = findViewById(R.id.laai_filelist);
        int radioButtonID = radioGroup.getCheckedRadioButtonId();

        if (radioButtonID == -1) {
            Toast.makeText(this, "Kies asseblief 'n databasis", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton deleteButton = findViewById(R.id.laai_wisuit);
        delete = deleteButton.isChecked();

        String filePath = fileList.get(radioButtonID).get("Path");
        if (LaaiNuweData(filePath)) {
            Toast.makeText(this, "Suksesvol", Toast.LENGTH_SHORT).show();
            resetGemeenteSettings();
            restartApplication();
        } else {
            Toast.makeText(this, "Onsuksesvol", Toast.LENGTH_SHORT).show();
            navigateToMainActivity();
        }

        deleteButton.setChecked(false);
        radioGroup.clearCheck();
    }

    private void resetGemeenteSettings() {
        WinkerkContract.winkerkEntry.GEMEENTE_NAAM = "";
        WinkerkContract.winkerkEntry.GEMEENTE_EPOS = "";

        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("Gemeente", "");
        editor.putString("Gemeente_Epos", "");
        editor.putString("DATA_DATUM", "");
        editor.apply();
    }

    private void restartApplication() {
        Intent restartIntent = new Intent(this, MainActivity2.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 123456, restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 50, pendingIntent);
        Runtime.getRuntime().exit(0);
    }

    private void handlePickFile(View view) {
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("*/*");
        startActivityForResult(Intent.createChooser(chooseFile, "Kies die databasis"), PICKFILE_RESULT_CODE);

        Button pickButton = (Button) view;
        pickButton.setBackgroundColor(Color.GREEN);
    }

    private void handleNetworkTransfer(View view) {
        Button networkButton = (Button) view;
        EditText ipAddress = findViewById(R.id.server_ip);
        TextView laai_boodskap = findViewById(R.id.laai_boodskap);

        if (FlagCancelledWiFi) {
            // Cancel operation
            networkButton.getBackground().clearColorFilter();
            receiveFileTaskWiFi.cancel();
            laai_boodskap.setText("Aflaai gekanselleer");
            FlagCancelledWiFi = false;
        } else {
            // Start operation
            String ipText = ipAddress.getText().toString();
            if (ipText != null && !ipText.isEmpty() && checkIPv4(ipText)) {
                networkButton.getBackground().setColorFilter(Color.YELLOW, PorterDuff.Mode.DARKEN);
                saveIPAddress(ipText);
                SERVER_IP = ipText;
                SERVER_PORT = 49514;
                startWiFiFileTransfer();
                FlagCancelledWiFi = true;
            } else {
                laai_boodskap.setText("Voer geldige IP adres in asb");
            }
        }
    }

    private void handleUSBTransfer(View view) {
        Button usbButton = (Button) view;
        EditText ipAddress = findViewById(R.id.server_ip);
        TextView laai_boodskap = findViewById(R.id.laai_boodskap);

        if (FlagCancelledUSB) {
            // Cancel operation
            usbButton.getBackground().clearColorFilter();
            ipAddress.setText("");
            receiveFileTaskUSB.cancel();
            laai_boodskap.setText("Aflaai gekanselleer");
            FlagCancelledUSB = false;
        } else {
            // Start operation
            usbButton.getBackground().setColorFilter(Color.YELLOW, PorterDuff.Mode.DARKEN);
            ipAddress.setText("127.0.0.1");
            SERVER_IP = "localhost";
            SERVER_PORT = 49514;
            startUSBFileTransfer();
            FlagCancelledUSB = true;
        }
    }

    private void saveIPAddress(String ipAddress) {
        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("IP", ipAddress);
        editor.apply();
    }

    private void startWiFiFileTransfer() {
        try {
            receiveFileTaskWiFi.cancel();
            receiveFileTaskWiFi = new ReceiveFileTask();
            receiveFileTaskWiFi.execute();
        } catch (Exception e) {
            Log.e(TAG, "Laai Databasis startWifiFileTransfer", e);
        }
    }

    private void startUSBFileTransfer() {
        try {
            receiveFileTaskUSB.cancel();
            receiveFileTaskUSB = new ReceiveFileTask();
            receiveFileTaskUSB.execute();
        } catch (Exception e) {
            Log.e(TAG, "Laai Databasis startUsbFileTransfer", e);
        }
    }

    private void initializeProgressBars() {
        ProgressBar progressBar = findViewById(R.id.laai_indeterminateBar);
        ProgressBar progressBar2 = findViewById(R.id.laai_indeterminateBar2);
        progressBar.setVisibility(View.GONE);
        progressBar2.setVisibility(View.GONE);
    }

    private void initializeDataInfo(SharedPreferences settings) {
        TextView dataDate = findViewById(R.id.datadate);
        String dateText = "Huidige Data: " + DATA_DATUM;
        dataDate.setText(dateText);

        EditText dbLinkView = findViewById(R.id.db_link);
        String dropBoxUrl = settings.getString("DropBox", "");
        if (!dropBoxUrl.isEmpty()) {
            dbLinkView.setText(dropBoxUrl);
        }
    }

    private void scanForDatabaseFiles() {
        try {
            getFileList(MEDIA_PATH);
            getFileList(Environment.getExternalStorageDirectory() + "/WinkerkReader/");
            getFileList(MEDIA_PATH2);
        } catch (Exception e) {
            Log.e("WinkerkReader Laaidatabasis", "Error scanning files: " + e);
        }

        // Backup current database
        backupCurrentDatabase();
    }

    private void backupCurrentDatabase() {
        try {
            File dataDir = new File(getApplicationInfo().dataDir, "/databases/");
            File currentDB = new File(dataDir, INFO_DB);
            File backupDB = new File(WkrDir, INFO_DB);

            if (backupDB.exists()) {
                backupDB.delete();
            }

            if (currentDB.exists()) {
                try (FileInputStream fis = new FileInputStream(currentDB);
                     FileOutputStream fos = new FileOutputStream(backupDB);
                     FileChannel src = fis.getChannel();
                     FileChannel dst = fos.getChannel()) {
                    src.transferTo(0, src.size(), dst);
                }
                MediaScannerConnection.scanFile(this, new String[]{backupDB.getAbsolutePath()}, null, null);
            }
        } catch (Exception e) {
            Log.e("WinkerkReader Laaidatabasis", "Error backing up database: " + e);
        }
    }

    private void setupFileListUI() {
        RadioGroup fileList = findViewById(R.id.laai_filelist);
        Button loadButton = findViewById(R.id.laai_laai);

        if (this.fileList.size() == 0) {
            loadButton.setVisibility(View.GONE);
            return;
        }

        loadButton.setVisibility(View.VISIBLE);

        for (int i = 0; i < this.fileList.size(); i++) {
            addFileRadioButton(fileList, i);
        }
    }

    private void addFileRadioButton(RadioGroup fileList, int index) {
        File file = new File(this.fileList.get(index).get("Path"));
        String size = String.valueOf((int) (file.length() / 1024 / 1024));
        String additionalData = getFileAdditionalData(this.fileList.get(index).get("Path"));

        RadioButton radioButton = new RadioButton(this);
        String buttonText = String.format("%s\n%s Mb%s",
                this.fileList.get(index).get("Path"), size, additionalData);
        radioButton.setText(buttonText);
        radioButton.setId(index);
        radioButton.setBackground(getResources().getDrawable(R.drawable.border2));
        radioButton.setLayoutParams(new LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT));

        fileList.addView(radioButton);
    }

    private String getFileAdditionalData(String filePath) {
        String data = "";
        try (SQLiteDatabase sqlite = SQLiteDatabase.openDatabase(filePath, null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
             Cursor cursor = sqlite.rawQuery(
                     "SELECT MyCongregationInfo.Name, MyCongregationInfo.Email, Denominations.Abbreviation " +
                             "FROM MyCongregationInfo " +
                             "JOIN Congregations ON (MyCongregationInfo.CongregationGUID = Congregations.CongregationGUID) " +
                             "JOIN Denominations ON (quote(MyCongregationInfo.DenominationGUID) = quote(Denominations.DenominationGUID))",
                     null)) {

            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                int abbrevIndex = cursor.getColumnIndex("Abbreviation");
                int nameIndex = cursor.getColumnIndex("GemeenteNaam");

                if (abbrevIndex >= 0 && nameIndex >= 0) {
                    String abbreviation = cursor.getString(abbrevIndex);
                    String gemeenteNaam = cursor.getString(nameIndex);
                    data = String.format("\nGemeente: %s %s", abbreviation, gemeenteNaam);
                }
            }
        } catch (Exception e) {
            Log.e("WinkerkReader Laaidatabasis", "Error reading database info: " + e);
        }
        return data;
    }

    private void handleIntentExtras() {
        Intent intentMain = getIntent();
        if (intentMain.getExtras() == null) return;

        String extra = intentMain.getStringExtra("DataBase_Update");
        if (extra == null || extra.isEmpty()) return;

        processAutomaticDatabaseUpdate(extra);
    }

    private void processAutomaticDatabaseUpdate(String filePath) {
        Toast.makeText(this, "WKR - Databasislaai", Toast.LENGTH_SHORT).show();

        File file = new File(filePath);
        long fileSizeKB = file.length() / 1024;
        long fileSizeMB = fileSizeKB / 1024;

        Toast.makeText(this, String.format("WKR - DROPBOX Databasis %d KB", fileSizeKB), Toast.LENGTH_LONG).show();

        if (fileSizeMB >= 1) {
            Toast.makeText(this, "WKR - Probeer Dropbox databasis laai", Toast.LENGTH_LONG).show();

            if (LaaiNuweData(filePath)) {
                Toast.makeText(this, "WKR - Dropbox Databasis gelaai", Toast.LENGTH_LONG).show();
                restartApplication();
            } else {
                Toast.makeText(this, "WKR - Dropbox Databasis laai was onsuksesvol", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "WKR - Dropbox Databasis te klein", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void handleAutomaticDownload(SharedPreferences settings) {
        boolean fromMenu = settings.getBoolean("FROM_MENU", false);
        EditText dbLinkView = findViewById(R.id.db_link);

        if (!fromMenu && AutoDL && !dbLinkView.getText().toString().equals(getString(R.string.dbLink))) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("FROM_MENU", false);
            editor.apply();

            Button dropboxButton = findViewById(R.id.dbLinkButton);
            dropboxButton.performClick();
        }
    }


    /**
     * Function to read all winkerk_droid.db files and store the details in
     * ArrayList
     */
    private ArrayList<HashMap<String, String>> getFileList(String searchpath) {
        System.out.println(searchpath);
        if (searchpath != null) {
            File home = new File(searchpath);
            File[] listFiles = home.listFiles();
            if (listFiles != null) {
                for (File file : listFiles) {
                    System.out.println(file.getAbsolutePath());
                    if (!file.isDirectory()) {
                        //   scanDirectory(file);
                        //} else {
                        addFileToList(file);
                    }
                }
            }
        }
        // return file list array
        return fileList;
    }

    private void scanDirectory(File directory) {
        if (directory != null) {
            File[] listFiles = directory.listFiles();
            if (listFiles != null) {
                for (File file : listFiles) {
                    if (file.isDirectory()) {
                        scanDirectory(file);
                    } else {
                        addFileToList(file);
                    }

                }
            }
        }
    }

    private void addFileToList(File mfile) {
        HashMap<String, String> fileMap = new HashMap<>();
        fileMap.put("Title", mfile.getName());
        fileMap.put("Path", mfile.getPath());
        // Adding each file to mfileList
        //fileList.add(fileMap);
        String mPattern = WINKERK_DB;//"WinkerkReader.sqlite";
        if (mfile.getName().equals(mPattern)) {
            //HashMap<String, String> fileMap = new HashMap<>();
            fileMap.put("Title", mfile.getName());
            fileMap.put("Path", mfile.getPath());
            //             Adding each file to mfileList
            fileList.add(fileMap);
        }
    }

    // procedure to write file to disk
    private static void writeExtractedFileToDisk(InputStream in, OutputStream outs) throws IOException {
        byte[] buffer = new byte[1024];

        int length;
        while ((length = in.read(buffer)) > 0) {
            outs.write(buffer, 0, length);
        }
        outs.flush();
        outs.close();
        in.close();
    }

    private Boolean LaaiNuweData(String nfile) {
        // Update program database with new external one
        // located in folder ROOT /WinkerkReader/
        context = this;
        if (Build.VERSION.SDK_INT >= 17)
            DB_PATH = context.getApplicationInfo().dataDir + "/databases/";
        else
            DB_PATH = "/data/data" + context.getPackageName() + "/databases/";
        this.context = context;

        Boolean result = false;
        File d = new File(nfile);//Environment.getExternalStorageDirectory() + nFile);
        //   if (d.exists()) {
        if (checkPermission()) {
            InputStream is = null;
            try {
                is = new FileInputStream(d);
                result = true;
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Laai Nuwe Data Input stream failed", e);
                result = false;
            }
            if (result) {
                OutputStream dest;
                eiedata:
                {
                    try {
                        dest = new FileOutputStream(DB_PATH + "/" + DB_NAME);
                    } catch (FileNotFoundException e) { // make dir if there is none
                        File mediaStorageDir = new File(this.getApplicationInfo().dataDir, "/databases/");
                        mediaStorageDir.mkdirs();
                        try {
                            dest = Files.newOutputStream(Paths.get(DB_PATH + "/" + DB_NAME));
                        } catch (IOException ss) {
                            Log.e(TAG, "Laai Nuwe Data Output stream failed", ss);
                            break eiedata;
                        }
                    }
                    try { // write new database to internal program storage
                        writeExtractedFileToDisk(is, dest);
                        result = true;
                    } catch (IOException e) {
                        Log.e(TAG, "Write ExtractedFileToDisk failed", e);
                        result = false;
                        break eiedata;
                    }
                    if (delete) {
                        try {
                            // delete the original file deleteFile(Environment.getExternalStorageDirectory()+ "/winkerk_droid.db");/
                            String absolutePathToFile = d.getAbsolutePath();
                            d.delete();
                            MediaScannerConnection.scanFile(this, new String[]{absolutePathToFile}, null, null);
                            if (d.exists()) {
                                d.getCanonicalFile().delete();
                                if (d.exists()) {
                                    getApplicationContext().deleteFile(d.getName());
                                }
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "File Delete failed", e);
                        }
                    }

                    try {
                        File data = new File(this.getApplicationInfo().dataDir, "/databases/");
                        String currentDBPath = "/" + INFO_DB;
                        String backupDBPath = "/" + INFO_DB;
                        File currentDB = new File(data, currentDBPath);
                        File backupDB = new File(WkrDir, backupDBPath);

                        if (backupDB.exists()) {
                            backupDB.getCanonicalFile().delete();
                            if (backupDB.exists()) {
                                getApplicationContext().deleteFile(backupDB.getName());
                            }
                        }
                        if (currentDB.exists()) {
                            FileChannel src = new FileInputStream(currentDB).getChannel();
                            FileChannel dst = new FileOutputStream(backupDB).getChannel();
                            src.transferTo(0, src.size(), dst);
                            src.close();
                            dst.close();
                        }

                        MediaScannerConnection.scanFile(this, new String[]{backupDB.getAbsolutePath()}, null, null);

                    } catch (Exception e) {
                        Log.e("WinkerkReader Laaidatabasis", "Error: " + e);
                    }

                }
            } // END copy if have permission
        }
        return result;
    }

    private boolean checkPermission() { // write permissions
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (result == PackageManager.PERMISSION_GRANTED) {
            result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            return result == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }


    public void downloadFromDropBoxUrl(String url) {

        //verfying if the downloadmanager is available first.
        context = this;
        if (Build.VERSION.SDK_INT >= 17)
            DB_PATH = context.getApplicationInfo().dataDir + "/databases/";
        else
            DB_PATH = "/data/data" + context.getPackageName() + "/databases/";
        if (isDownloadManagerAvailable(getApplication())) {

            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            recieverDownloadComplete = new BroadcastReceiver() {


                @Override
                public void onReceive(Context context, Intent intent) {
                    DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    long reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (myDownloadReference == reference) {
                        final TextView laai_boodskap = findViewById(R.id.laai_boodskap);
                        //Uri downloadFileLocalUri = manager.getUriForDownloadedFile(reference);
                        DownloadManager.Query query = new DownloadManager.Query();
                        query.setFilterById(reference);
                        Cursor cursor = manager.query(query);

                        cursor.moveToFirst();
                        int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int status = cursor.getInt(columnIndex);
                        int columnReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                        int reason = cursor.getInt(columnReason);
                        int fileNameIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        Uri downloadFileLocalUri2 = manager.getUriForDownloadedFile(reference);


                        String savedFilePath = null;
                        String downloadFileLocalUri = cursor.getString(fileNameIndex).replace("file://", "");
                        if (downloadFileLocalUri != null) {
                            File mFile = new File(downloadFileLocalUri);
                            savedFilePath = mFile.getAbsolutePath();

                            switch (status) {
                                case DownloadManager.STATUS_SUCCESSFUL:
                                    Button LaaiDropBoxButton = (Button) findViewById(R.id.dbLinkButton);
                                    LaaiDropBoxButton.setVisibility(View.VISIBLE);

                                    InputStream in = null;
                                    OutputStream out = null;
                                    try {

                                        in = getContentResolver().openInputStream(downloadFileLocalUri2);
                                        // open the output-file:
                                        out = Files.newOutputStream(new File(DB_PATH + "/" + DB_NAME).toPath());
                                        // copy the content:
                                        byte[] buffer = new byte[1024];
                                        int len;
                                        while ((len = in.read(buffer)) != -1) {
                                            out.write(buffer, 0, len);
                                        }
                                        // Contents are copied!
                                    } catch (IOException e) {
                                        Log.e(TAG, "Write outbuffer failed", e);
                                    } finally {
                                        if (in != null) {
                                            try {
                                                in.close();
                                            } catch (IOException e) {
                                                Log.e(TAG, "Read failed", e);
                                            }
                                        }
                                        if (out != null) {
                                            try {
                                                out.close();
                                            } catch (IOException e) {
                                                Log.e(TAG, "Out failed", e);
                                            }
                                        }
                                        Intent mStartActivity = new Intent(getApplicationContext(), MainActivity2.class);
                                        int mPendingIntentId = 123456;
                                        PendingIntent mPendingIntent = PendingIntent.getActivity(getApplicationContext(), mPendingIntentId, mStartActivity, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);//FLAG_CANCEL_CURRENT);
                                        AlarmManager mgr = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                                        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 50, mPendingIntent);
                                        Runtime.getRuntime().exit(0);
                                    }

                                    Intent chooseFile = new Intent(ACTION_GET_CONTENT);
                                    chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
                                    chooseFile.setType("*/*");
                                    startActivityForResult(Intent.createChooser(chooseFile, "Kies die databasis"),
                                            PICKFILE_RESULT_CODE);
                                    break;
                                case DownloadManager.STATUS_FAILED:
                                    Toast.makeText(Laaidatabasis.this, "FAILED: " + reason, Toast.LENGTH_LONG).show();
                                    break;
                                case DownloadManager.STATUS_PAUSED:
                                    Toast.makeText(Laaidatabasis.this, "PAUSED: " + reason, Toast.LENGTH_LONG).show();
                                    break;
                                case DownloadManager.STATUS_PENDING:
                                    Toast.makeText(Laaidatabasis.this, "PENDING! ", Toast.LENGTH_LONG).show();
                                    break;
                                case DownloadManager.STATUS_RUNNING:
                                    Toast.makeText(Laaidatabasis.this, "RUNNING! ", Toast.LENGTH_LONG).show();
                                    break;
                            }
                        }
                    }
                }

                private String formatDateAsString(Date date) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    return sdf.format(date);
                }
            };
            registerReceiver(recieverDownloadComplete, intentFilter, RECEIVER_EXPORTED);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setDescription("WinkerkReader Database Download");
            request.setTitle(WINKERK_DB);
            request.setMimeType("application/vnd.sqlite3");
            request.setVisibleInDownloadsUi(true);
// in order for this if to run, you must use the android 3.2 to compile your app
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            }
            File dest = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "//" + DB_NAME);

            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, DB_NAME);



// get download service and enqueue file

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            myDownloadReference = manager.enqueue(request);

        }
    }

    private Date convertOLETIMEToDate(long oleTimeValue) {
        // OLE Automation date starts from 1899-12-30
        long baseTime = -2209161600000L; // milliseconds

        // Convert OLE Automation date to milliseconds since Epoch
        double daysSinceBase = oleTimeValue;
        long milliseconds = (long) ((daysSinceBase * 86400000) + baseTime);

        return new Date(milliseconds);
    }

    private String conv2(String text) {
        String sharingUrl = text;//"https://1drv.ms/u/s!AkijuZglD51udjvlclZWSNf_2wo";
        //String utf =  sharingUrl.getBytes("utf-8");
        String encodedUrl = text;
        encodedUrl = encodedUrl.replace("/view?usp=sharing", "");
        encodedUrl = encodedUrl.replace("/file/d/", "/uc?export=download&id=");

        String resultUrl = encodedUrl;
        return resultUrl;
    }

    private String conv3(String text) {
        int lastIndex = text.lastIndexOf("?");
        if (lastIndex < 0)
            return text;
        text = text.substring(0, lastIndex) + "?download=1";
        return text;
    }


    private String conv(String text) {
        String sharingUrl = text;//"https://1drv.ms/u/s!AkijuZglD51udjvlclZWSNf_2wo";

        //String utf =  sharingUrl.getBytes("utf-8");
        byte[] bytes = sharingUrl.getBytes();
        String base64Value = Base64.encodeToString(bytes, Base64.DEFAULT); // System.Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes(sharingUrl));
        String encodedUrl = "u!" + base64Value.trim();
        encodedUrl = encodedUrl.replaceAll("[" + "=" + "]+$", "");//trimEnd("=");
        encodedUrl = encodedUrl.replace('/', '_');
        encodedUrl = encodedUrl.replace('/', '_');
        encodedUrl = encodedUrl.replace('+', '-');

        String resultUrl = "https://api.onedrive.com/v1.0/shares/" + encodedUrl + "/root/content";
        //String.format("https://api.onedrive.com/v1.0/shares/{0}/root/content", encodedUrl);
        return resultUrl;
    }

    public String trimEnd(String value) {
        int len = value.length();
        int st = 0;
        while ((st < len) && value.charAt(len - 1) == ' ') {
            len--;
        }
        return value.substring(0, len);
    }

    public static boolean isDownloadManagerAvailable(Context context) {

        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        context = this;
        if (Build.VERSION.SDK_INT >= 17)
            DB_PATH = context.getApplicationInfo().dataDir + "/databases/";
        else
            DB_PATH = "/data/data" + context.getPackageName() + "/databases/";
        this.context = context;

        if (requestCode == PICKFILE_RESULT_CODE) {
            if (resultCode == -1) {
                Uri fileUri = data.getData();
                String filePath;
                if (fileUri != null) {
                    filePath = fileUri.getPath();
                    assert filePath != null;
                    File source = new File(filePath);
                    InputStream in = null;
                    OutputStream out = null;
                    try {
                        // open the user-picked file for reading:
                        in = getContentResolver().openInputStream(fileUri);
                        // open the output-file:
                        out = Files.newOutputStream(new File(DB_PATH + "/" + DB_NAME).toPath());
                        // copy the content:
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                        // Contents are copied!
                    } catch (IOException e) {
                        Log.e(TAG, "Write buffer out failed", e);
                    } finally {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (IOException e) {
                                Log.e(TAG, "REad buffer failed", e);
                            }
                        }
                        if (out != null) {
                            try {
                                out.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Write failed", e);
                            }
                        }
                    }
                    Intent mStartActivity = new Intent(getApplicationContext(), MainActivity2.class);
                    int mPendingIntentId = 123456;
                    PendingIntent mPendingIntent = PendingIntent.getActivity(getApplicationContext(), mPendingIntentId, mStartActivity, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);//FLAG_CANCEL_CURRENT);
                    AlarmManager mgr = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                    mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 50, mPendingIntent);
                    Runtime.getRuntime().exit(0);
                }

            }

        } else {
            throw new IllegalStateException("Unexpected value: " + requestCode);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    public static boolean checkIPv4(String s)
    {
        // Regex for digit from 0 to 255
        String reg0To255 = "(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])";
        // regex 0 To 255 followed by a dot, 4 times repeat
        // validation an IP address.
        String regex = reg0To255 + "\\." + reg0To255 + "\\." + reg0To255 + "\\." + reg0To255;
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(s);
        return m.matches();
    }



    private class ReceiveFileTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();
        private final Handler mainHandler;
        private volatile boolean isCancelled = false;


        public ReceiveFileTask() {
            mainHandler = new Handler(Looper.getMainLooper());
        }

        public void execute() {
            this.onPreExecute();
            this.executorService.submit(() -> {
                try {
                    this.doInBackground();
                } finally {
                    this.onPostExecute();
                    this.executorService.shutdown();
                }

            });
        }

        public void cancel() {
            this.isCancelled = true;
            this.executorService.shutdownNow();
            this.onCancelled();
        }

        protected void onCancelled() {
            Log.d(Laaidatabasis.class.getCanonicalName(), "ReceiveFileTask USB / WIFI was canceled");
        }

        protected void onPostExecute() {
            this.mainHandler.post(() -> {
                TextView laai_boodskap = findViewById(R.id.laai_boodskap);
                laai_boodskap.setText("Klaar");
            });
        }

        protected void onPreExecute() {
            this.mainHandler.post(() -> {
                TextView laai_boodskap = findViewById(R.id.laai_boodskap);
                laai_boodskap.setText("Begin");
            });
        }

        private void doInBackground() {
            AtomicInteger retryAttempts = new AtomicInteger(5);
            int retryInterval = 2000;
            boolean connected = false;
            Socket socket = null;
            Socket ackSocket = null;
            Socket checksumSocket = null;

            while(!connected && retryAttempts.get() > 0 && !this.isCancelled) {
                try {
                    socket = new Socket(SERVER_IP, SERVER_PORT);
                    ackSocket = new Socket(SERVER_IP, SERVER_PORT + 1);
                    checksumSocket = new Socket(SERVER_IP, SERVER_PORT + 2);
                    connected = true;
                } catch (IOException var11) {
                    int attemptsRemaining = retryAttempts.decrementAndGet();
                    this.mainHandler.post(() -> {
                        TextView laai_boodskap = findViewById(R.id.laai_boodskap);
                        laai_boodskap.setText("Waiting for server... Attempts remaining: " + attemptsRemaining);
                    });

                    try {
                        Thread.sleep((long)retryInterval);
                    } catch (InterruptedException var10) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (connected) {
                this.mainHandler.post(() -> {
                    TextView laai_boodskap = findViewById(R.id.laai_boodskap);
                    laai_boodskap.setText("Server connected. Starting download...");
                });
                this.startFileTransfer(socket, ackSocket, checksumSocket);
            } else {
                this.mainHandler.post(() -> {
                    TextView laai_boodskap = findViewById(R.id.laai_boodskap);
                    laai_boodskap.setText("Unable to connect to the server after retries.");
                });
            }

        }

        private void startFileTransfer(Socket socket, Socket ackSocket, Socket checksumSocket) {
            byte[] buffer = new byte[8192];
            InputStream inputStream = null;
            OutputStream outputStream = null;
            BufferedOutputStream bufferedOutputStream = null;
            BufferedWriter ackWriter = null;
            BufferedReader checksumReader = null;

            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                String fileName = "WinkerReader.sqlite";
                String filePath = getExternalFilesDir((String)null) + "/" + fileName;

                bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(getExternalFilesDir((String)null) + "/" + fileName));
                ackWriter = new BufferedWriter(new OutputStreamWriter(ackSocket.getOutputStream()));
                checksumReader = new BufferedReader(new InputStreamReader(checksumSocket.getInputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String fileSizeString = reader.readLine();
                long fileSize = Long.parseLong(fileSizeString);
                ackWriter.write("ACK\n");
                ackWriter.flush();
                String bufferSizeString = reader.readLine();
                int bufferSize = Integer.parseInt(bufferSizeString);
                ackWriter.write("ACK\n");
                ackWriter.flush();
                buffer = new byte[bufferSize];
                long totalBytesReceived = 0L;
                int chunks = 0;
                ackWriter.write("ACK\n");
                ackWriter.flush();

                while(totalBytesReceived < fileSize) {
                    int totalBytesRead = 0;
                    int chunkSize = buffer.length;

                    while(totalBytesRead < chunkSize) {
                        int bytesRead = inputStream.read(buffer, totalBytesRead, chunkSize - totalBytesRead);
                        if (bytesRead == -1) {
                            throw new IOException("Connection closed prematurely.");
                        }

                        totalBytesRead += bytesRead;
                        if (totalBytesReceived + (long)totalBytesRead == fileSize) {
                            break;
                        }
                    }

                    ackWriter.write("ACK\n");
                    ackWriter.flush();
                    ++chunks;
                    Log.e("FileTransfer", "Chunk #" + chunks);
                    Log.e("FileTransfer", "Chunk size " + totalBytesRead);
                    String checksumString = checksumReader.readLine();
                    ackWriter.write("ACK\n");
                    ackWriter.flush();
                    String chunkChecksum = this.calculateChecksum(buffer, 0, totalBytesRead);
                    if (!chunkChecksum.equals(checksumString)) {
                        Log.e("FileTransfer", "Checksum mismatch for chunk. Aborting.");
                        ackWriter.write("ERROR\n");
                        ackWriter.flush();
                    } else {
                        Log.e("FileTransfer", "Checksum valid.");
                        ackWriter.write("ACK\n");
                        ackWriter.flush();
                        totalBytesReceived += (long)totalBytesRead;
                        bufferedOutputStream.write(buffer, 0, totalBytesRead);
                        bufferedOutputStream.flush();
                    }

                    float progress = (float)totalBytesReceived / (float)fileSize * 100.0F;
                    runOnUiThread(() -> {
                        TextView laai_boodskap = findViewById(R.id.laai_boodskap);
                        laai_boodskap.setText("Received: " + (int)progress + "%");
                    });
                }

                Log.d("FileTransfer", "File transfer complete. File saved to: " + getExternalFilesDir((String)null) + "/downloaded_file");
                runOnUiThread(() -> {
                    TextView laai_boodskap = findViewById(R.id.laai_boodskap);
                    laai_boodskap.setText("File transfer complete.");
                });
                this.processDownloadedFile(filePath, fileName);
            } catch (IOException var35) {
                var35.printStackTrace();
                runOnUiThread(() -> {
                    TextView laai_boodskap = findViewById(R.id.laai_boodskap);
                    laai_boodskap.setText("Error receiving file.");
                });
            } finally {
                try {
                    if (bufferedOutputStream != null) {
                        bufferedOutputStream.close();
                    }

                    if (inputStream != null) {
                        inputStream.close();
                    }

                    if (outputStream != null) {
                        outputStream.close();
                    }

                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }

                    if (checksumReader != null) {
                        checksumReader.close();
                    }

                    if (ackWriter != null) {
                        ackWriter.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error closing file transfer streams", e);
                }

            }

        }

        private void processDownloadedFile(String filePath, String fileName) {
            Intent intent = new Intent(Laaidatabasis.this, MainActivity2.class);
            intent.putExtra("SENDER_CLASS_NAME", "Laaidatabasis");
            RadioButton deleteButton = findViewById(R.id.laai_wisuit);
            boolean delete = deleteButton.isChecked();
            if (LaaiNuweData(filePath)) {
                // Successfully loaded new data
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        getApplicationContext(),
                        123456,
                        new Intent(getApplicationContext(), MainActivity2.class),
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
                );

                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 150, pendingIntent);

                Runtime.getRuntime().exit(0); // Restart app
            } else {
                Toast.makeText(Laaidatabasis.this, "File loading failed.", Toast.LENGTH_SHORT).show();
                startActivity(intent);
                finish();
            }

            deleteButton.setChecked(false);
        }

        private String calculateChecksum(byte[] data, int offset, int length) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                // Update the digest with the specific portion of the array
                digest.update(data, offset, length);
                // Compute the final hash
                byte[] hashBytes = digest.digest();
                // Convert hash bytes to a hexadecimal string
                StringBuilder hexString = new StringBuilder();
                for (byte b : hashBytes) {
                    hexString.append(String.format("%02x", b));
                }
                return hexString.toString();
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "SHA-256 algorithm not available for checksum", e);
                return null;
            }
        }
    }
}
