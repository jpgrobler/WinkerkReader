package za.co.jpsoft.winkerkreader;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import yuku.ambilwarna.AmbilWarnaDialog;

/**
 * Enhanced UitlegActivity with comprehensive error handling, validation, and user feedback.
 * Provides robust settings management with graceful error recovery.
 */
public class UitlegActivity extends AppCompatActivity {
    private static final String TAG = "UitlegActivity";
    private CheckBox uitlegLogOproepe;
    private CheckBox uitlegLogVoip;
    // Permission request codes
    public static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    public static final int PHONE_PERMISSIONS_REQUEST = 101;
    public static final int CALENDAR_PERMISSIONS_REQUEST = 102;

    // Required permissions
    public static final String[] REQUIRED_PHONE_PERMISSIONS = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_NUMBERS
    };

    public static final String[] CALENDAR_PERMISSIONS = {
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
    };

    // UI Components - Display Settings
    private CheckBox fotoCheck, eposCheck, whatsappCheck, verjaarsdagCheck;
    private CheckBox ouderdomCheck, huweliksdatumCheck, wykCheck, selfoonCheck, telefoonCheck;

    // UI Components - Function Settings
    private CheckBox autoStartSwitch, oproepMonitor, oproepLog, whatsapp1, whatsapp2, whatsapp3, eposHtml;
    private Spinner defUitleg;

    // UI Components - Widget Settings
    private CheckBox showDoop, showBelydenis, showHuwelik, showSterwe;

    // UI Components - Color Settings
    private TextView gem1, gem2, gem3;

    // Calendar components
    private Spinner calendarSpinner;
    private List<CalendarInfo> availableCalendars = new ArrayList<>();
    private long selectedCalendarId = -1;
    private CalendarManager calendarManager;

    // Settings manager
    private SettingsManager settingsManager;

    // Color picker
    private int defaultColor = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.uitleg);

            // Initialize settings manager with error handling
            if (!initializeSettingsManager()) {
                showErrorAndFinish("Failed to initialize settings. Please restart the app.");
                return;
            }

            initializeComponents();

            if (!loadSavedPreferences()) {
                showErrorDialog("Warning", "Some settings could not be loaded. Default values will be used.", false);
            }

            setupClickListeners();
            initializeCalendarManager();
            loadCalendars();
            loadCallLoggingSettings();
        } catch (Exception e) {
            Log.e(TAG, "Critical error in onCreate", e);
            showErrorAndFinish("Critical error starting settings. Please contact support.");
        }
    }
    private void loadCallLoggingSettings() {
        try {
            // Load telephone call logging setting
            boolean telephoneLogEnabled = settingsManager.isTelephoneLogEnabled();
            oproepLog.setChecked(telephoneLogEnabled);

            // Load VOIP call logging setting
            boolean voipLogEnabled = settingsManager.isVoipLogEnabled();
            uitlegLogVoip.setChecked(voipLogEnabled);

            Log.d(TAG, "Loaded call logging settings - Telephone: " + telephoneLogEnabled +
                    ", VOIP: " + voipLogEnabled);
        } catch (Exception e) {
            Log.e(TAG, "Error loading call logging settings", e);
            Toast.makeText(this, "Error loading settings", Toast.LENGTH_SHORT).show();
        }
    }

    // ADD THIS: Helper method to show info toast
    private void showInfoToast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing info toast", e);
        }
    }

    /**
     * Initialize settings manager with error handling
     */
    private boolean initializeSettingsManager() {
        try {
            settingsManager = SettingsManager.getInstance(this);
            if (settingsManager == null) {
                Log.e(TAG, "SettingsManager returned null");
                return false;
            }
            Log.d(TAG, "SettingsManager initialized successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SettingsManager", e);
            return false;
        }
    }

    /**
     * Initialize all UI components with error handling
     */
    private void initializeComponents() {
        try {
            // Display settings checkboxes
            fotoCheck = findViewById(R.id.uitleg_foto);
            eposCheck = findViewById(R.id.uitleg_epos);
            whatsappCheck = findViewById(R.id.uitleg_whatsap);
            verjaarsdagCheck = findViewById(R.id.uitleg_verjaarsdag);
            ouderdomCheck = findViewById(R.id.uitleg_ouderdom);
            huweliksdatumCheck = findViewById(R.id.uitleg_Huweliksdag);
            wykCheck = findViewById(R.id.uitleg_wyk);
            selfoonCheck = findViewById(R.id.uitleg_selfoon);
            telefoonCheck = findViewById(R.id.uitleg_telefoon);

            // Function settings components
            autoStartSwitch = findViewById(R.id.autoStartSwitch);
            oproepMonitor = findViewById(R.id.uitleg_Monitor_Oproepe);
            oproepLog = findViewById(R.id.uitleg_Log_Oproepe);
            defUitleg = findViewById(R.id.layoutOpsies);
            whatsapp1 = findViewById(R.id.uitleg_w1);
            whatsapp2 = findViewById(R.id.uitleg_w2);
            whatsapp3 = findViewById(R.id.uitleg_w3);
            eposHtml = findViewById(R.id.uitleg_html);
            uitlegLogVoip = findViewById(R.id.uitleg_Log_VOIP);

            // Widget settings checkboxes
            showDoop = findViewById(R.id.widget_Doop_select);
            showBelydenis = findViewById(R.id.widget_Belydenis_select);
            showHuwelik = findViewById(R.id.widget_Huwelik_select);
            showSterwe = findViewById(R.id.widget_Sterf);

            // Color components
            gem1 = findViewById(R.id.gem1);
            gem2 = findViewById(R.id.gem2);
            gem3 = findViewById(R.id.gem3);

            // Calendar component
            calendarSpinner = findViewById(R.id.calendarSpinner);

            // Validate that all critical components were found
            validateRequiredComponents();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing components", e);
            throw new RuntimeException("Failed to initialize UI components", e);
        }
    }

    /**
     * Validate that all required UI components were found
     */
    private void validateRequiredComponents() {
        List<String> missingComponents = new ArrayList<>();

        if (fotoCheck == null) missingComponents.add("fotoCheck");
        if (eposCheck == null) missingComponents.add("eposCheck");
        if (whatsappCheck == null) missingComponents.add("whatsappCheck");
        if (gem1 == null) missingComponents.add("gem1");
        if (gem2 == null) missingComponents.add("gem2");
        if (gem3 == null) missingComponents.add("gem3");
        if (defUitleg == null) missingComponents.add("defUitleg");

        if (!missingComponents.isEmpty()) {
            String missing = String.join(", ", missingComponents);
            Log.e(TAG, "Missing required components: " + missing);
            throw new RuntimeException("Missing required UI components: " + missing);
        }

        Log.d(TAG, "All required components initialized successfully");
    }

    /**
     * Load saved preferences and set UI component states with error handling
     */
    private boolean loadSavedPreferences() {
        boolean allSuccessful = true;

        try {
            // Load and set display preferences
            if (!setDisplayPreferences()) {
                Log.w(TAG, "Failed to load some display preferences");
                allSuccessful = false;
            }

            // Load and set function preferences
            if (!setFunctionPreferences()) {
                Log.w(TAG, "Failed to load some function preferences");
                allSuccessful = false;
            }

            // Load and set widget preferences
            if (!setWidgetPreferences()) {
                Log.w(TAG, "Failed to load some widget preferences");
                allSuccessful = false;
            }

            // Load and set color preferences
            if (!setColorPreferences()) {
                Log.w(TAG, "Failed to load some color preferences");
                allSuccessful = false;
            }

            return allSuccessful;

        } catch (Exception e) {
            Log.e(TAG, "Critical error loading preferences", e);
            return false;
        }
    }

    private boolean setDisplayPreferences() {
        try {
            fotoCheck.setChecked(settingsManager.isListFotoEnabled());
            eposCheck.setChecked(settingsManager.isListEposEnabled());
            whatsappCheck.setChecked(settingsManager.isListWhatsappEnabled());
            verjaarsdagCheck.setChecked(settingsManager.isListVerjaarblokEnabled());
            ouderdomCheck.setChecked(settingsManager.isListOuderdomEnabled());
            huweliksdatumCheck.setChecked(settingsManager.isListHuwelikblokEnabled());
            wykCheck.setChecked(settingsManager.isListWykEnabled());
            selfoonCheck.setChecked(settingsManager.isListSelfoonEnabled());
            telefoonCheck.setChecked(settingsManager.isListTelefoonEnabled());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error setting display preferences", e);
            return false;
        }
    }
    private void saveAutoStartSetting(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE);
        prefs.edit().putBoolean("auto_start_enabled", enabled).apply();
    }
    private boolean setFunctionPreferences() {
        try {
            oproepMonitor.setChecked(settingsManager.isOproepMonitorEnabled());
            oproepLog.setChecked(settingsManager.isOproepLogEnabled());
            eposHtml.setChecked(settingsManager.isEposHtmlEnabled());
            whatsapp1.setChecked(settingsManager.isWhatsapp1Enabled());
            whatsapp2.setChecked(settingsManager.isWhatsapp2Enabled());
            whatsapp3.setChecked(settingsManager.isWhatsapp3Enabled());
            autoStartSwitch.setChecked(settingsManager.isAutoStartEnabled());
            autoStartSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    saveAutoStartSetting(isChecked);
                    settingsManager.setAutostart(isChecked);
                    if (isChecked) {
                        startMonitoringService();
                    } else {
                        stopMonitoringService();
                    }
                }
            });
            // Set spinner selection with validation
            String defaultLayout = settingsManager.getDefaultLayout();
            if (SettingsManager.isValidLayoutName(defaultLayout)) {
                setSpinnerSelection(defUitleg, defaultLayout);
            } else {
                Log.w(TAG, "Invalid default layout, using first item");
                if (defUitleg.getCount() > 0) {
                    defUitleg.setSelection(0);
                }
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error setting function preferences", e);
            return false;
        }
    }

    private boolean setWidgetPreferences() {
        try {
            showDoop.setChecked(settingsManager.isWidgetDoopEnabled());
            showBelydenis.setChecked(settingsManager.isWidgetBelydenisEnabled());
            showHuwelik.setChecked(settingsManager.isWidgetHuwelikEnabled());
            showSterwe.setChecked(settingsManager.isWidgetSterfEnabled());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error setting widget preferences", e);
            return false;
        }
    }

    private boolean setColorPreferences() {
        try {
            int color1 = settingsManager.getGemeenteKleur();
            int color2 = settingsManager.getGemeente2Kleur();
            int color3 = settingsManager.getGemeente3Kleur();

            gem1.setBackgroundColor(color1);
            gem2.setBackgroundColor(color2);
            gem3.setBackgroundColor(color3);

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error setting color preferences", e);
            return false;
        }
    }

    /**
     * Helper method to set spinner selection safely
     */
    private void setSpinnerSelection(Spinner spinner, String value) {
        try {
            for (int position = 0; position < spinner.getCount(); position++) {
                Object item = spinner.getItemAtPosition(position);
                if (item != null && item.toString().equals(value)) {
                    spinner.setSelection(position);
                    return;
                }
            }
            Log.w(TAG, "Value not found in spinner: " + value);
        } catch (Exception e) {
            Log.e(TAG, "Error setting spinner selection", e);
        }
    }

    /**
     * Setup click listeners for all interactive components
     */
    private void setupClickListeners() {
        try {
            setupSaveButtonListeners();
            setupColorPickerListeners();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up click listeners", e);
            showErrorDialog("Error", "Some buttons may not work properly. Please restart the app.", false);
        }
    }

    private void setupSaveButtonListeners() {
        findViewById(R.id.uitleg_stoor).setOnClickListener(v -> saveDisplaySettings());
        findViewById(R.id.funksoie_Stoor).setOnClickListener(v -> saveFunctionSettings());
        findViewById(R.id.save_widget).setOnClickListener(v -> saveWidgetSettings());
        findViewById(R.id.saveColor).setOnClickListener(v -> saveColorSettings());
    }

    private void setupColorPickerListeners() {
        gem1.setOnClickListener(v -> openColorPickerDialog(v, 1));
        gem2.setOnClickListener(v -> openColorPickerDialog(v, 2));
        gem3.setOnClickListener(v -> openColorPickerDialog(v, 3));
    }

    /**
     * Save display settings with comprehensive error handling and validation
     */
    private void saveDisplaySettings() {
        try {
            // Show loading indicator
            showProgressDialog("Saving display settings...");

            // Create settings object
            SettingsManager.DisplaySettings settings = new SettingsManager.DisplaySettings(
                    fotoCheck.isChecked(),
                    eposCheck.isChecked(),
                    whatsappCheck.isChecked(),
                    verjaarsdagCheck.isChecked(),
                    ouderdomCheck.isChecked(),
                    huweliksdatumCheck.isChecked(),
                    wykCheck.isChecked(),
                    selfoonCheck.isChecked(),
                    telefoonCheck.isChecked()
            );

            // Validate settings before saving
            if (!settings.isValid()) {
                hideProgressDialog();
                showErrorDialog("Validation Error",
                        "At least one display option must be enabled. Please select at least one option.", false);
                return;
            }

            // Save settings
            SettingsManager.OperationResult result = settingsManager.saveDisplaySettings(settings);
            hideProgressDialog();

            if (result.isSuccess()) {
                showSuccessToast("Display settings saved successfully");
                //finish();
            } else {
                Log.e(TAG, "Failed to save display settings: " + result.getMessage());
                showErrorDialog("Save Error",
                        "Failed to save display settings: " + result.getMessage(), true);
            }

        } catch (Exception e) {
            hideProgressDialog();
            Log.e(TAG, "Critical error saving display settings", e);
            showErrorDialog("Critical Error",
                    "An unexpected error occurred while saving. Please try again.", true);
        }
    }

    /**
     * Save function settings with validation
     */
    private void saveFunctionSettings() {
        try {
            showProgressDialog("Saving function settings...");

            // Validate calendar selection
            long calendarId = selectedCalendarId;
            if (calendarId != -1 && !SettingsManager.isValidCalendarId(calendarId)) {
                hideProgressDialog();
                showErrorDialog("Invalid Calendar",
                        "Selected calendar is invalid. Please choose a different calendar.", false);
                return;
            }

            // Validate layout selection
            String selectedLayout = defUitleg.getSelectedItem() != null ?
                    defUitleg.getSelectedItem().toString() : "";
            if (!SettingsManager.isValidLayoutName(selectedLayout)) {
                hideProgressDialog();
                showErrorDialog("Invalid Layout",
                        "Selected layout is invalid. Please choose a different layout.", false);
                return;
            }

            // Save individual settings
            boolean allSuccessful = true;
            StringBuilder errors = new StringBuilder();

            if (!settingsManager.setOproepMonitorEnabled(oproepMonitor.isChecked())) {
                allSuccessful = false;
                errors.append("Call monitor setting, ");
            }

            if (!settingsManager.setOproepLogEnabled(oproepLog.isChecked())) {
                allSuccessful = false;
                errors.append("Call Log setting, ");
            }

            // ADD THIS: Save VOIP logging setting
            if (!settingsManager.setVoipLogEnabled(uitlegLogVoip.isChecked())) {
                allSuccessful = false;
                errors.append("VOIP Log setting, ");
            }

            if (!settingsManager.setDefaultLayout(selectedLayout)) {
                allSuccessful = false;
                errors.append("Default layout, ");
            }

            if (!settingsManager.setWhatsapp1Enabled(whatsapp1.isChecked())) {
                allSuccessful = false;
                errors.append("WhatsApp 1, ");
            }

            if (!settingsManager.setWhatsapp2Enabled(whatsapp2.isChecked())) {
                allSuccessful = false;
                errors.append("WhatsApp 2, ");
            }

            if (!settingsManager.setWhatsapp3Enabled(whatsapp3.isChecked())) {
                allSuccessful = false;
                errors.append("WhatsApp 3, ");
            }

            if (!settingsManager.setEposHtmlEnabled(eposHtml.isChecked())) {
                allSuccessful = false;
                errors.append("Email HTML, ");
            }

            if (!settingsManager.setSelectedCalendarId(calendarId)) {
                allSuccessful = false;
                errors.append("Calendar selection, ");
            }

            hideProgressDialog();

            if (allSuccessful) {
                showSuccessToast("Function settings saved successfully");

                // ADD THIS: Notify user about VOIP service changes if needed
                if (uitlegLogVoip.isChecked()) {
                    showInfoToast("VOIP call logging is now enabled");
                } else {
                    showInfoToast("VOIP call logging is now disabled");
                }
                //finish();
            } else {
                String errorList = errors.toString();
                if (errorList.endsWith(", ")) {
                    errorList = errorList.substring(0, errorList.length() - 2);
                }
                showErrorDialog("Partial Save Error",
                        "Failed to save some settings: " + errorList + ". Other settings were saved successfully.", false);
            }

        } catch (Exception e) {
            hideProgressDialog();
            Log.e(TAG, "Critical error saving function settings", e);
            showErrorDialog("Critical Error",
                    "An unexpected error occurred while saving function settings.", true);
        }
    }

    /**
     * Save widget settings with validation
     */
    private void saveWidgetSettings() {
        try {
            showProgressDialog("Saving widget settings...");

            boolean allSuccessful = true;
            StringBuilder errors = new StringBuilder();

            if (!settingsManager.setWidgetDoopEnabled(showDoop.isChecked())) {
                allSuccessful = false;
                errors.append("Doop widget, ");
            }

            if (!settingsManager.setWidgetBelydenisEnabled(showBelydenis.isChecked())) {
                allSuccessful = false;
                errors.append("Belydenis widget, ");
            }

            if (!settingsManager.setWidgetHuwelikEnabled(showHuwelik.isChecked())) {
                allSuccessful = false;
                errors.append("Huwelik widget, ");
            }

            if (!settingsManager.setWidgetSterfEnabled(showSterwe.isChecked())) {
                allSuccessful = false;
                errors.append("Sterf widget, ");
            }

            hideProgressDialog();

            if (allSuccessful) {
                showSuccessToast("Widget settings saved successfully");
                //finish();
            } else {
                String errorList = errors.toString();
                if (errorList.endsWith(", ")) {
                    errorList = errorList.substring(0, errorList.length() - 2);
                }
                showErrorDialog("Partial Save Error",
                        "Failed to save some widget settings: " + errorList, false);
            }

        } catch (Exception e) {
            hideProgressDialog();
            Log.e(TAG, "Critical error saving widget settings", e);
            showErrorDialog("Critical Error",
                    "An unexpected error occurred while saving widget settings.", true);
        }
    }

    /**
     * Save color settings with validation
     */
    private void saveColorSettings() {
        try {
            showProgressDialog("Saving color settings...");

            boolean allSuccessful = true;
            StringBuilder errors = new StringBuilder();

            int color1 = settingsManager.getGemeenteKleur();
            int color2 = settingsManager.getGemeente2Kleur();
            int color3 = settingsManager.getGemeente3Kleur();

            // Colors should already be validated when they were set, but double-check
            if (!SettingsManager.isValidColor(color1) || !settingsManager.setGemeenteKleur(color1)) {
                allSuccessful = false;
                errors.append("Gemeente 1 color, ");
            }

            if (!SettingsManager.isValidColor(color2) || !settingsManager.setGemeente2Kleur(color2)) {
                allSuccessful = false;
                errors.append("Gemeente 2 color, ");
            }

            if (!SettingsManager.isValidColor(color3) || !settingsManager.setGemeente3Kleur(color3)) {
                allSuccessful = false;
                errors.append("Gemeente 3 color, ");
            }

            hideProgressDialog();

            if (allSuccessful) {
                showSuccessToast("Color settings saved successfully");
                //finish();
            } else {
                String errorList = errors.toString();
                if (errorList.endsWith(", ")) {
                    errorList = errorList.substring(0, errorList.length() - 2);
                }
                showErrorDialog("Save Error",
                        "Failed to save some colors: " + errorList, false);
            }

        } catch (Exception e) {
            hideProgressDialog();
            Log.e(TAG, "Critical error saving color settings", e);
            showErrorDialog("Critical Error",
                    "An unexpected error occurred while saving color settings.", true);
        }
    }
    /**
     * Start CallLogging
     */
    private void startMonitoringService() {
        Intent serviceIntent = new Intent(this, CallMonitoringService.class);
        startForegroundService(serviceIntent);
    }
    private void stopMonitoringService() {
        Intent serviceIntent = new Intent(this, CallMonitoringService.class);
        stopService(serviceIntent);
    }

    /**
     * Open color picker dialog with error handling
     */
    private void openColorPickerDialog(View view, int gemeenteIndex) {
        try {
            if (gemeenteIndex < 1 || gemeenteIndex > 3) {
                Log.e(TAG, "Invalid gemeente index: " + gemeenteIndex);
                showErrorToast("Invalid color selection");
                return;
            }

            int currentColor = getCurrentGemeenteColor(gemeenteIndex);

            AmbilWarnaDialog colorPickerDialog = new AmbilWarnaDialog(
                    this,
                    currentColor,
                    new AmbilWarnaDialog.OnAmbilWarnaListener() {
                        @Override
                        public void onCancel(AmbilWarnaDialog dialog) {
                            Log.d(TAG, "Color picker cancelled");
                        }

                        @Override
                        public void onOk(AmbilWarnaDialog dialog, int color) {
                            handleColorSelected(view, gemeenteIndex, color);
                        }
                    }
            );
            colorPickerDialog.show();

        } catch (Exception e) {
            Log.e(TAG, "Error opening color picker", e);
            showErrorToast("Failed to open color picker");
        }
    }

    /**
     * Handle color selection with validation
     */
    private void handleColorSelected(View view, int gemeenteIndex, int color) {
        try {
            if (!SettingsManager.isValidColor(color)) {
                Log.w(TAG, "Invalid color selected: " + Integer.toHexString(color));
                showErrorToast("Invalid color selected");
                return;
            }

            boolean success = updateGemeenteColor(gemeenteIndex, color);

            if (success) {
                view.setBackgroundColor(color);
                Log.d(TAG, "Color updated successfully for gemeente " + gemeenteIndex +
                        ": " + Integer.toHexString(color));
            } else {
                showErrorToast("Failed to update color");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling color selection", e);
            showErrorToast("Error updating color");
        }
    }

    /**
     * Get current color for specified gemeente
     */
    private int getCurrentGemeenteColor(int gemeenteIndex) {
        switch (gemeenteIndex) {
            case 1: return settingsManager.getGemeenteKleur();
            case 2: return settingsManager.getGemeente2Kleur();
            case 3: return settingsManager.getGemeente3Kleur();
            default: return defaultColor;
        }
    }

    /**
     * Update the appropriate gemeente color with validation
     */
    private boolean updateGemeenteColor(int gemeenteIndex, int color) {
        switch (gemeenteIndex) {
            case 1:
                return settingsManager.setGemeenteKleur(color);
            case 2:
                return settingsManager.setGemeente2Kleur(color);
            case 3:
                return settingsManager.setGemeente3Kleur(color);
            default:
                Log.w(TAG, "Invalid gemeente index: " + gemeenteIndex);
                return false;
        }
    }

    /**
     * Initialize calendar manager with error handling
     */
    private void initializeCalendarManager() {
        try {
            calendarManager = new CalendarManager(this);
            selectedCalendarId = settingsManager.getSelectedCalendarId();
            Log.d(TAG, "Calendar manager initialized with ID: " + selectedCalendarId);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing calendar manager", e);
            showErrorToast("Calendar features may not work properly");
        }
    }

    /**
     * Load available calendars with comprehensive error handling
     */
    private void loadCalendars() {
        try {
            if (!hasCalendarPermissions()) {
                Log.w(TAG, "Calendar permissions not granted");
                setupEmptyCalendarSpinner("Calendar permissions required");
                return;
            }

            if (calendarManager == null) {
                Log.e(TAG, "Calendar manager not initialized");
                setupEmptyCalendarSpinner("Calendar service unavailable");
                return;
            }

            availableCalendars = calendarManager.getAvailableCalendars();

            if (availableCalendars == null) {
                Log.e(TAG, "getAvailableCalendars returned null");
                setupEmptyCalendarSpinner("Error loading calendars");
                return;
            }

            if (availableCalendars.isEmpty()) {
                setupEmptyCalendarSpinner("No calendars found - Add Google account");
                return;
            }

            setupCalendarSpinner();
            Log.d(TAG, "Loaded " + availableCalendars.size() + " calendars successfully");

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception loading calendars", e);
            setupEmptyCalendarSpinner("Calendar permissions required");
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error loading calendars", e);
            setupEmptyCalendarSpinner("Error loading calendars");
        }
    }

    // UI Helper Methods

    private AlertDialog progressDialog;

    private void showProgressDialog(String message) {
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            progressDialog = new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setCancelable(false)
                    .create();
            progressDialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing progress dialog", e);
        }
    }

    private void hideProgressDialog() {
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding progress dialog", e);
        }
    }

    private void showErrorDialog(String title, String message, boolean finishOnDismiss) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", (dialog, which) -> {
                        if (finishOnDismiss) {
                            finish();
                        }
                    });

            if (finishOnDismiss) {
                builder.setCancelable(false);
            }

            builder.create().show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing error dialog", e);
            // Fallback to toast
            showErrorToast(message);
        }
    }

    private void showErrorAndFinish(String message) {
        showErrorDialog("Error", message, true);
    }

    private void showSuccessToast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing success toast", e);
        }
    }

    private void showErrorToast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing error toast", e);
        }
    }

    // Calendar Helper Methods (existing methods with improved error handling)

    private boolean hasCalendarPermissions() {
        for (String permission : CALENDAR_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void setupEmptyCalendarSpinner(String message) {
        try {
            List<String> emptyMessage = new ArrayList<>();
            emptyMessage.add(message);

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    emptyMessage
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            calendarSpinner.setAdapter(adapter);
            calendarSpinner.setEnabled(false);

            Log.w(TAG, "Calendar spinner disabled: " + message);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up empty calendar spinner", e);
        }
    }

    private void setupCalendarSpinner() {
        try {
            calendarSpinner.setEnabled(true);

            List<String> calendarNames = new ArrayList<>();
            for (CalendarInfo calendar : availableCalendars) {
                String displayName = calendar.getDisplayName() + " (" + calendar.getAccountName() + ")";
                calendarNames.add(displayName);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    calendarNames
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            calendarSpinner.setAdapter(adapter);

            // Select previously chosen calendar
            selectSavedCalendar();

            calendarSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    try {
                        if (!availableCalendars.isEmpty() && position < availableCalendars.size()) {
                            selectedCalendarId = availableCalendars.get(position).getId();
                            Log.d(TAG, "Calendar selected: " + selectedCalendarId);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling calendar selection", e);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // No action needed
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error setting up calendar spinner", e);
            setupEmptyCalendarSpinner("Error setting up calendars");
        }
    }

    private void selectSavedCalendar() {
        try {
            if (selectedCalendarId != -1 && SettingsManager.isValidCalendarId(selectedCalendarId)) {
                for (int i = 0; i < availableCalendars.size(); i++) {
                    if (availableCalendars.get(i).getId() == selectedCalendarId) {
                        calendarSpinner.setSelection(i);
                        Log.d(TAG, "Previously saved calendar selected at position: " + i);
                        return;
                    }
                }
                Log.w(TAG, "Previously saved calendar ID not found: " + selectedCalendarId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error selecting saved calendar", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideProgressDialog();
    }
}