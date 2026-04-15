package za.co.jpsoft.winkerkreader.ui.activities

import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import za.co.jpsoft.winkerkreader.services.CallMonitoringService

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import yuku.ambilwarna.AmbilWarnaDialog
import za.co.jpsoft.winkerkreader.data.models.CalendarInfo
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO

class UitlegActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "UitlegActivity"

        const val NOTIFICATION_PERMISSION_REQUEST = 100
        const val PHONE_PERMISSIONS_REQUEST = 101
        const val CALENDAR_PERMISSIONS_REQUEST = 102

        val REQUIRED_PHONE_PERMISSIONS = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_NUMBERS
        )

        val CALENDAR_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
    }

    // UI Components - Display Settings
    private lateinit var fotoCheck: CheckBox
    private lateinit var eposCheck: CheckBox
    private lateinit var whatsappCheck: CheckBox
    private lateinit var verjaarsdagCheck: CheckBox
    private lateinit var ouderdomCheck: CheckBox
    private lateinit var huweliksdatumCheck: CheckBox
    private lateinit var wykCheck: CheckBox
    private lateinit var selfoonCheck: CheckBox
    private lateinit var telefoonCheck: CheckBox

    // UI Components - Function Settings
    private lateinit var autoStartSwitch: CheckBox
    private lateinit var oproepMonitor: CheckBox
    private lateinit var oproepLog: CheckBox
    private lateinit var whatsapp1: CheckBox
    private lateinit var whatsapp2: CheckBox
    private lateinit var whatsapp3: CheckBox
    private lateinit var eposHtml: CheckBox
    private lateinit var uitlegLogVoip: CheckBox
    private lateinit var defUitleg: Spinner

    // UI Components - Widget Settings
    private lateinit var showDoop: CheckBox
    private lateinit var showBelydenis: CheckBox
    private lateinit var showHuwelik: CheckBox
    private lateinit var showSterwe: CheckBox

    // UI Components - Color Settings
    private lateinit var gem1: TextView
    private lateinit var gem2: TextView
    private lateinit var gem3: TextView

    // Calendar components
    private lateinit var calendarSpinner: Spinner
    private var availableCalendars: List<CalendarInfo> = emptyList()
    private var selectedCalendarId: Long = -1
    private var calendarManager: CalendarManager? = null

    // Settings manager
    private lateinit var settingsManager: SettingsManager

    // Color picker
    private val defaultColor = 0

    // Progress dialog
    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.uitleg)

            // Initialize settings manager with error handling
            if (!initializeSettingsManager()) {
                showErrorAndFinish("Failed to initialize settings. Please restart the app.")
                return
            }

            initializeComponents()

            if (!loadSavedPreferences()) {
                showErrorDialog("Warning", "Some settings could not be loaded. Default values will be used.", false)
            }

            setupClickListeners()
            initializeCalendarManager()
            loadCalendars()
            loadCallLoggingSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in onCreate", e)
            showErrorAndFinish("Critical error starting settings. Please contact support.")
        }
    }

    private fun loadCallLoggingSettings() {
        try {
            oproepLog.isChecked = settingsManager.callLogEnabled
            uitlegLogVoip.isChecked = settingsManager.voipLogEnabled
            Log.d(TAG, "Loaded call logging settings - Telephone: ${oproepLog.isChecked}, VOIP: ${uitlegLogVoip.isChecked}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading call logging settings", e)
            Toast.makeText(this, "Error loading settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInfoToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing info toast", e)
        }
    }

    private fun initializeSettingsManager(): Boolean {
        return try {
            settingsManager = SettingsManager(this)
            Log.d(TAG, "SettingsManager initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SettingsManager", e)
            false
        }
    }

    private fun initializeComponents() {
        try {
            fotoCheck = findViewById(R.id.uitleg_foto)
            eposCheck = findViewById(R.id.uitleg_epos)
            whatsappCheck = findViewById(R.id.uitleg_whatsap)
            verjaarsdagCheck = findViewById(R.id.uitleg_verjaarsdag)
            ouderdomCheck = findViewById(R.id.uitleg_ouderdom)
            huweliksdatumCheck = findViewById(R.id.uitleg_Huweliksdag)
            wykCheck = findViewById(R.id.uitleg_wyk)
            selfoonCheck = findViewById(R.id.uitleg_selfoon)
            telefoonCheck = findViewById(R.id.uitleg_telefoon)

            autoStartSwitch = findViewById(R.id.autoStartSwitch)
            oproepMonitor = findViewById(R.id.uitleg_Monitor_Oproepe)
            oproepLog = findViewById(R.id.uitleg_Log_Oproepe)
            defUitleg = findViewById(R.id.layoutOpsies)
            whatsapp1 = findViewById(R.id.uitleg_w1)
            whatsapp2 = findViewById(R.id.uitleg_w2)
            whatsapp3 = findViewById(R.id.uitleg_w3)
            eposHtml = findViewById(R.id.uitleg_html)
            uitlegLogVoip = findViewById(R.id.uitleg_Log_VOIP)

            showDoop = findViewById(R.id.widget_Doop_select)
            showBelydenis = findViewById(R.id.widget_Belydenis_select)
            showHuwelik = findViewById(R.id.widget_Huwelik_select)
            showSterwe = findViewById(R.id.widget_Sterf)

            gem1 = findViewById(R.id.gem1)
            if (!za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeenteNaam.isNullOrEmpty()) {
                gem1.setText(za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeenteNaam)
            }
            gem2 = findViewById(R.id.gem2)
            if (!za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente2Naam.isNullOrEmpty()) {
                gem2.setText(za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente2Naam)
            }
            gem3 = findViewById(R.id.gem3)
            if (!za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente3Naam.isNullOrEmpty()) {
                gem3.setText(za.co.jpsoft.winkerkreader.utils.SettingsManager(this).gemeente3Naam)
            }
            calendarSpinner = findViewById(R.id.calendarSpinner)

            validateRequiredComponents()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing components", e)
            throw RuntimeException("Failed to initialize UI components", e)
        }
    }

    private fun validateRequiredComponents() {
        val missing = mutableListOf<String>()
        if (!::fotoCheck.isInitialized) missing.add("fotoCheck")
        if (!::eposCheck.isInitialized) missing.add("eposCheck")
        if (!::whatsappCheck.isInitialized) missing.add("whatsappCheck")
        if (!::gem1.isInitialized) missing.add("gem1")
        if (!::gem2.isInitialized) missing.add("gem2")
        if (!::gem3.isInitialized) missing.add("gem3")
        if (!::defUitleg.isInitialized) missing.add("defUitleg")

        if (missing.isNotEmpty()) {
            val missingStr = missing.joinToString(", ")
            Log.e(TAG, "Missing required components: $missingStr")
            throw RuntimeException("Missing required UI components: $missingStr")
        }
        Log.d(TAG, "All required components initialized successfully")
    }

    private fun loadSavedPreferences(): Boolean {
        var allSuccessful = true
        try {
            if (!setDisplayPreferences()) allSuccessful = false
            if (!setFunctionPreferences()) allSuccessful = false
            if (!setWidgetPreferences()) allSuccessful = false
            if (!setColorPreferences()) allSuccessful = false
        } catch (e: Exception) {
            Log.e(TAG, "Critical error loading preferences", e)
            return false
        }
        return allSuccessful
    }

    private fun setDisplayPreferences(): Boolean {
        return try {
            fotoCheck.isChecked = settingsManager.isListFoto
            eposCheck.isChecked = settingsManager.isListEpos
            whatsappCheck.isChecked = settingsManager.isListWhatsapp
            verjaarsdagCheck.isChecked = settingsManager.isListVerjaarBlok
            ouderdomCheck.isChecked = settingsManager.isListOuderdom
            huweliksdatumCheck.isChecked = settingsManager.isListHuwelikBlok
            wykCheck.isChecked = settingsManager.isListWyk
            selfoonCheck.isChecked = settingsManager.isListSelfoon
            telefoonCheck.isChecked = settingsManager.isListTelefoon
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting display preferences", e)
            false
        }
    }

    private fun setFunctionPreferences(): Boolean {
        return try {
            oproepMonitor.isChecked = settingsManager.callMonitorEnabled
            oproepLog.isChecked = settingsManager.callLogEnabled
            eposHtml.isChecked = settingsManager.eposHtml
            whatsapp1.isChecked = settingsManager.whatsapp1
            whatsapp2.isChecked = settingsManager.whatsapp2
            whatsapp3.isChecked = settingsManager.whatsapp3
            autoStartSwitch.isChecked = settingsManager.autoStartEnabled

            autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
                saveAutoStartSetting(isChecked)
                settingsManager.autoStartEnabled = isChecked
                if (isChecked) {
                    startMonitoringService()
                } else {
                    stopMonitoringService()
                }
            }

            // Set spinner selection with validation
            val defaultLayout = settingsManager.defLayout
            setSpinnerSelection(defUitleg, defaultLayout)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting function preferences", e)
            false
        }
    }

    private fun setWidgetPreferences(): Boolean {
        return try {
            showDoop.isChecked = settingsManager.widgetDoop
            showBelydenis.isChecked = settingsManager.widgetBelydenis
            showHuwelik.isChecked = settingsManager.widgetHuwelik
            showSterwe.isChecked = settingsManager.widgetSterf
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting widget preferences", e)
            false
        }
    }

    private fun setColorPreferences(): Boolean {
        return try {
            gem1.setBackgroundColor(settingsManager.gemeenteKleur)
            gem2.setBackgroundColor(settingsManager.gemeente2Kleur)
            gem3.setBackgroundColor(settingsManager.gemeente3Kleur)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting color preferences", e)
            false
        }
    }

    private fun setSpinnerSelection(spinner: Spinner, value: String) {
        try {
            for (position in 0 until spinner.count) {
                val item = spinner.getItemAtPosition(position)
                if (item?.toString() == value) {
                    spinner.setSelection(position)
                    return
                }
            }
            Log.w(TAG, "Value not found in spinner: $value")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting spinner selection", e)
        }
    }

    private fun setupClickListeners() {
        try {
            setupSaveButtonListeners()
            setupColorPickerListeners()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up click listeners", e)
            showErrorDialog("Error", "Some buttons may not work properly. Please restart the app.", false)
        }
    }

    private fun setupSaveButtonListeners() {
        findViewById<View>(R.id.uitleg_stoor).setOnClickListener { saveDisplaySettings() }
        findViewById<View>(R.id.funksoie_Stoor).setOnClickListener { saveFunctionSettings() }
        findViewById<View>(R.id.save_widget).setOnClickListener { saveWidgetSettings() }
        findViewById<View>(R.id.saveColor).setOnClickListener { saveColorSettings() }
    }

    private fun setupColorPickerListeners() {
        gem1.setOnClickListener { openColorPickerDialog(it, 1) }
        gem2.setOnClickListener { openColorPickerDialog(it, 2) }
        gem3.setOnClickListener { openColorPickerDialog(it, 3) }
    }

    private fun saveDisplaySettings() {
        try {
            showProgressDialog("Saving display settings...")

            // Update settings properties
            settingsManager.isListFoto = fotoCheck.isChecked
            settingsManager.isListEpos = eposCheck.isChecked
            settingsManager.isListWhatsapp = whatsappCheck.isChecked
            settingsManager.isListVerjaarBlok = verjaarsdagCheck.isChecked
            settingsManager.isListOuderdom = ouderdomCheck.isChecked
            settingsManager.isListHuwelikBlok = huweliksdatumCheck.isChecked
            settingsManager.isListWyk = wykCheck.isChecked
            settingsManager.isListSelfoon = selfoonCheck.isChecked
            settingsManager.isListTelefoon = telefoonCheck.isChecked

            hideProgressDialog()
            showSuccessToast("Display settings saved successfully")
        } catch (e: Exception) {
            hideProgressDialog()
            Log.e(TAG, "Critical error saving display settings", e)
            showErrorDialog("Critical Error", "An unexpected error occurred while saving. Please try again.", true)
        }
    }

    private fun saveFunctionSettings() {
        try {
            showProgressDialog("Saving function settings...")

            val calendarId = selectedCalendarId
            val selectedLayout = defUitleg.selectedItem?.toString() ?: ""

            // Save settings
            settingsManager.callMonitorEnabled = oproepMonitor.isChecked
            settingsManager.callLogEnabled = oproepLog.isChecked
            settingsManager.voipLogEnabled = uitlegLogVoip.isChecked
            settingsManager.defLayout = selectedLayout
            settingsManager.whatsapp1 = whatsapp1.isChecked
            settingsManager.whatsapp2 = whatsapp2.isChecked
            settingsManager.whatsapp3 = whatsapp3.isChecked
            settingsManager.eposHtml = eposHtml.isChecked
            settingsManager.selectedCalendarId = calendarId

            hideProgressDialog()
            showSuccessToast("Function settings saved successfully")
            if (uitlegLogVoip.isChecked) {
                showInfoToast("VOIP call logging is now enabled")
            } else {
                showInfoToast("VOIP call logging is now disabled")
            }
        } catch (e: Exception) {
            hideProgressDialog()
            Log.e(TAG, "Critical error saving function settings", e)
            showErrorDialog("Critical Error", "An unexpected error occurred while saving function settings.", true)
        }
    }

    private fun saveWidgetSettings() {
        try {
            showProgressDialog("Saving widget settings...")

            settingsManager.widgetDoop = showDoop.isChecked
            settingsManager.widgetBelydenis = showBelydenis.isChecked
            settingsManager.widgetHuwelik = showHuwelik.isChecked
            settingsManager.widgetSterf = showSterwe.isChecked

            hideProgressDialog()
            showSuccessToast("Widget settings saved successfully")
        } catch (e: Exception) {
            hideProgressDialog()
            Log.e(TAG, "Critical error saving widget settings", e)
            showErrorDialog("Critical Error", "An unexpected error occurred while saving widget settings.", true)
        }
    }

    private fun saveColorSettings() {
        try {
            showProgressDialog("Saving color settings...")

            // Colors are already stored via color picker, but we can trigger a save if needed.
            // The color picker already updates the settings via handleColorSelected.
            // So this method might just show a confirmation.
            hideProgressDialog()
            showSuccessToast("Color settings saved successfully")
        } catch (e: Exception) {
            hideProgressDialog()
            Log.e(TAG, "Critical error saving color settings", e)
            showErrorDialog("Critical Error", "An unexpected error occurred while saving color settings.", true)
        }
    }

    private fun saveAutoStartSetting(enabled: Boolean) {
        val prefs = getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_start_enabled", enabled).apply()
    }

    private fun startMonitoringService() {
        val intent = Intent(this, CallMonitoringService::class.java)
        startForegroundService(intent)
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, CallMonitoringService::class.java)
        stopService(intent)
    }

    private fun openColorPickerDialog(view: View, gemeenteIndex: Int) {
        try {
            if (gemeenteIndex !in 1..3) {
                Log.e(TAG, "Invalid gemeente index: $gemeenteIndex")
                showErrorToast("Invalid color selection")
                return
            }

            val currentColor = getCurrentGemeenteColor(gemeenteIndex)

            val colorPickerDialog = AmbilWarnaDialog(
                this,
                currentColor,
                object : AmbilWarnaDialog.OnAmbilWarnaListener {
                    override fun onCancel(dialog: AmbilWarnaDialog) {
                        Log.d(TAG, "Color picker cancelled")
                    }

                    override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                        handleColorSelected(view, gemeenteIndex, color)
                    }
                }
            )
            colorPickerDialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening color picker", e)
            showErrorToast("Failed to open color picker")
        }
    }

    private fun handleColorSelected(view: View, gemeenteIndex: Int, color: Int) {
        try {
            updateGemeenteColor(gemeenteIndex, color)
            view.setBackgroundColor(color)
            Log.d(TAG, "Color updated successfully for gemeente $gemeenteIndex: ${Integer.toHexString(color)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling color selection", e)
            showErrorToast("Error updating color")
        }
    }

    private fun getCurrentGemeenteColor(gemeenteIndex: Int): Int {
        return when (gemeenteIndex) {
            1 -> settingsManager.gemeenteKleur
            2 -> settingsManager.gemeente2Kleur
            3 -> settingsManager.gemeente3Kleur
            else -> defaultColor
        }
    }

    private fun updateGemeenteColor(gemeenteIndex: Int, color: Int) {
        when (gemeenteIndex) {
            1 -> settingsManager.gemeenteKleur = color
            2 -> settingsManager.gemeente2Kleur = color
            3 -> settingsManager.gemeente3Kleur = color
            else -> Log.w(TAG, "Invalid gemeente index: $gemeenteIndex")
        }
    }

    private fun initializeCalendarManager() {
        try {
            calendarManager = CalendarManager(this)
            selectedCalendarId = settingsManager.selectedCalendarId
            Log.d(TAG, "Calendar manager initialized with ID: $selectedCalendarId")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing calendar manager", e)
            showErrorToast("Calendar features may not work properly")
        }
    }

    private fun loadCalendars() {
        try {
            if (!hasCalendarPermissions()) {
                Log.w(TAG, "Calendar permissions not granted")
                setupEmptyCalendarSpinner("Calendar permissions required")
                return
            }

            val manager = calendarManager ?: run {
                Log.e(TAG, "Calendar manager not initialized")
                setupEmptyCalendarSpinner("Calendar service unavailable")
                return
            }

            availableCalendars = manager.getAvailableCalendars() ?: emptyList()

            if (availableCalendars.isEmpty()) {
                setupEmptyCalendarSpinner("No calendars found - Add Google account")
                return
            }

            setupCalendarSpinner()
            Log.d(TAG, "Loaded ${availableCalendars.size} calendars successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception loading calendars", e)
            setupEmptyCalendarSpinner("Calendar permissions required")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading calendars", e)
            setupEmptyCalendarSpinner("Error loading calendars")
        }
    }

    // UI Helper Methods

    private fun showProgressDialog(message: String) {
        try {
            progressDialog?.dismiss()
            progressDialog = AlertDialog.Builder(this)
                .setMessage(message)
                .setCancelable(false)
                .create()
            progressDialog?.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing progress dialog", e)
        }
    }

    private fun hideProgressDialog() {
        try {
            progressDialog?.dismiss()
            progressDialog = null
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding progress dialog", e)
        }
    }

    private fun showErrorDialog(title: String, message: String, finishOnDismiss: Boolean) {
        try {
            val builder = AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK") { _, _ ->
                    if (finishOnDismiss) {
                        finish()
                    }
                }
            if (finishOnDismiss) {
                builder.setCancelable(false)
            }
            builder.create().show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error dialog", e)
            showErrorToast(message)
        }
    }

    private fun showErrorAndFinish(message: String) {
        showErrorDialog("Error", message, true)
    }

    private fun showSuccessToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing success toast", e)
        }
    }

    private fun showErrorToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error toast", e)
        }
    }

    // Calendar Helper Methods

    private fun hasCalendarPermissions(): Boolean {
        return CALENDAR_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun setupEmptyCalendarSpinner(message: String) {
        try {
            val emptyMessage = listOf(message)
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, emptyMessage)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            calendarSpinner.adapter = adapter
            calendarSpinner.isEnabled = false
            Log.w(TAG, "Calendar spinner disabled: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up empty calendar spinner", e)
        }
    }

    private fun setupCalendarSpinner() {
        try {
            calendarSpinner.isEnabled = true

            val calendarNames = availableCalendars.map {
                "${it.displayName} (${it.accountName})"
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, calendarNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            calendarSpinner.adapter = adapter

            selectSavedCalendar()

            calendarSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    try {
                        if (availableCalendars.isNotEmpty() && position < availableCalendars.size) {
                            selectedCalendarId = availableCalendars[position].id
                            Log.d(TAG, "Calendar selected: $selectedCalendarId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling calendar selection", e)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // No action needed
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up calendar spinner", e)
            setupEmptyCalendarSpinner("Error setting up calendars")
        }
    }

    private fun selectSavedCalendar() {
        try {
            if (selectedCalendarId != -1L) {
                for ((index, calendar) in availableCalendars.withIndex()) {
                    if (calendar.id == selectedCalendarId) {
                        calendarSpinner.setSelection(index)
                        Log.d(TAG, "Previously saved calendar selected at position: $index")
                        return
                    }
                }
                Log.w(TAG, "Previously saved calendar ID not found: $selectedCalendarId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting saved calendar", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideProgressDialog()
    }
}