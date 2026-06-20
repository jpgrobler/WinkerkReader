package za.co.jpsoft.winkerkreader.ui.activities

import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import za.co.jpsoft.winkerkreader.services.CallMonitoringService

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
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
import za.co.jpsoft.winkerkreader.databinding.UitlegBinding

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

    private lateinit var binding: UitlegBinding
    // Calendar components
    private var availableCalendars: List<CalendarInfo> = emptyList()
    private var selectedCalendarId: Long = -1
    private var calendarManager: CalendarManager? = null

    // Settings manager
    private lateinit var settingsManager: SettingsManager

    // Google Tasks
//    private var googleTaskLists: List<com.google.api.services.tasks.model.TaskList> = emptyList()
//    private var googleTasksManager: GoogleTasksManager? = null
//    private var selectedTaskListId: String? = null
    // Color picker
    private val defaultColor = 0

    // Progress dialog
    private var progressDialog: AlertDialog? = null
    private var pastoralCalendars: List<CalendarInfo> = emptyList()
    private var selectedPastoralCalendarId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = UitlegBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Initialize settings manager with error handling
            if (!initializeSettingsManager()) {
                showErrorAndFinish("Failed to initialize settings. Please restart the app.")
                return
            }

            initializeComponents()

            if (!loadSavedPreferences()) {
                showErrorDialog("Warning", "Some settings could not be loaded. Default values will be used.", false)
            }
            setupGoogleTasksUI()
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
            binding.uitlegLogOproepe.isChecked = settingsManager.callLogEnabled
            binding.uitlegLogVOIP.isChecked = settingsManager.voipLogEnabled
            Log.d(TAG, "Loaded call logging settings - Telephone: ${binding.uitlegLogOproepe.isChecked}, VOIP: ${binding.uitlegLogVOIP.isChecked}")
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
            settingsManager = SettingsManager.getInstance(this)
            Log.d(TAG, "SettingsManager initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SettingsManager", e)
            false
        }
    }

    private fun initializeComponents() {
        try {
            if (!settingsManager.gemeenteNaam.isNullOrEmpty()) {
                binding.gem1.setText(settingsManager.gemeenteNaam)
            }
            if (!settingsManager.gemeente2Naam.isNullOrEmpty()) {
                binding.gem2.setText(settingsManager.gemeente2Naam)
            }
            if (!settingsManager.gemeente3Naam.isNullOrEmpty()) {
                binding.gem3.setText(settingsManager.gemeente3Naam)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing components", e)
            throw RuntimeException("Failed to initialize UI components", e)
        }
    }

    private fun validateRequiredComponents() {
        // Handled by ViewBinding - if binding is initialized, views are present
        Log.d(TAG, "Components handled by ViewBinding")
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
            binding.uitlegFoto.isChecked = settingsManager.isListFoto
            binding.uitlegEpos.isChecked = settingsManager.isListEpos
            binding.uitlegWhatsap.isChecked = settingsManager.isListWhatsapp
            binding.uitlegVerjaarsdag.isChecked = settingsManager.isListVerjaarBlok
            binding.uitlegOuderdom.isChecked = settingsManager.isListOuderdom
            binding.uitlegHuweliksdag.isChecked = settingsManager.isListHuwelikBlok
            binding.uitlegWyk.isChecked = settingsManager.isListWyk
            binding.uitlegSelfoon.isChecked = settingsManager.isListSelfoon
            binding.uitlegTelefoon.isChecked = settingsManager.isListTelefoon
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting display preferences", e)
            false
        }
    }

    private fun setFunctionPreferences(): Boolean {
        return try {
            binding.uitlegMonitorOproepe.isChecked = settingsManager.callMonitorEnabled
            binding.uitlegLogOproepe.isChecked = settingsManager.callLogEnabled
            binding.uitlegHtml.isChecked = settingsManager.eposHtml
            binding.uitlegW1.isChecked = settingsManager.whatsapp1
            binding.uitlegW2.isChecked = settingsManager.whatsapp2
            binding.uitlegW3.isChecked = settingsManager.whatsapp3
            binding.autoStartSwitch.isChecked = settingsManager.autoStartEnabled
            binding.pastoralCalendarAutoSync.isChecked = settingsManager.pastoralCalendarSyncEnabled
            binding.autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
                saveAutoStartSetting(isChecked)
                settingsManager.autoStartEnabled = isChecked
                if (isChecked) {
                    startMonitoringService()
                } else {
                    stopMonitoringService()
                }
            }
            binding.pastoralCalendarAutoSync.setOnCheckedChangeListener { _, isChecked ->
                settingsManager.pastoralCalendarSyncEnabled = isChecked
                Log.d(TAG, "Pastoral sync setting saved immediately: $isChecked")
            }
            binding.appsScriptLink.setText(settingsManager.tasksScriptUrl ?: "")
            binding.appsScriptKey.setText(settingsManager.tasksScriptSecret ?: "")
            // Set spinner selection with validation
            val defaultLayout = settingsManager.defLayout
            setSpinnerSelection(binding.layoutOpsies, defaultLayout)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting function preferences", e)
            false
        }
    }

    private fun setWidgetPreferences(): Boolean {
        return try {
            binding.widgetDoopSelect.isChecked = settingsManager.widgetDoop
            binding.widgetBelydenisSelect.isChecked = settingsManager.widgetBelydenis
            binding.widgetHuwelikSelect.isChecked = settingsManager.widgetHuwelik
            binding.widgetSterf.isChecked = settingsManager.widgetSterf
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting widget preferences", e)
            false
        }
    }


    private fun setColorPreferences(): Boolean {
        return try {
            // Force update backgrounds with proper context
            updateTextViewBackground(binding.gem1, settingsManager.gemeenteKleur)
            updateTextViewBackground(binding.gem2, settingsManager.gemeente2Kleur)
            updateTextViewBackground(binding.gem3, settingsManager.gemeente3Kleur)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting color preferences", e)
            false
        }
    }

    // Add this helper function
    private fun updateTextViewBackground(textView: TextView, color: Int) {
        try {
            if (color != -1 && color != 0) {
                // Clear any existing background tint
                textView.background = null

                // Create a new ColorDrawable
                val drawable = android.graphics.drawable.ColorDrawable(color)

                // For Android 14+, ensure the drawable is mutable
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    drawable.setTintList(null) // Remove any tint
                }

                textView.background = drawable

                // Also set the text color for better contrast
                if (isColorDark(color)) {
                    textView.setTextColor(android.graphics.Color.WHITE)
                } else {
                    textView.setTextColor(android.graphics.Color.BLACK)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating background color", e)
        }
    }

    // Helper to determine if color is dark
    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * android.graphics.Color.red(color) +
                0.587 * android.graphics.Color.green(color) +
                0.114 * android.graphics.Color.blue(color)) / 255
        return darkness >= 0.5
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
            binding.btnCopyScript.setOnClickListener {
                copyScriptToClipboard()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up click listeners", e)
            showErrorDialog("Error", "Some buttons may not work properly. Please restart the app.", false)
        }
    }

    private fun setupSaveButtonListeners() {
        binding.uitlegStoor.setOnClickListener { saveDisplaySettings() }
        binding.funksoieStoor.setOnClickListener { saveFunctionSettings() }
        binding.saveWidget.setOnClickListener { saveWidgetSettings() }
        binding.saveColor.setOnClickListener { saveColorSettings() }
        binding.tasksSave.setOnClickListener { saveGoogleTasksSettings()
        }
    }

    private fun setupColorPickerListeners() {
        binding.gem1.setOnClickListener { openColorPickerDialog(it, 1) }
        binding.gem2.setOnClickListener { openColorPickerDialog(it, 2) }
        binding.gem3.setOnClickListener { openColorPickerDialog(it, 3) }
    }

    private fun saveDisplaySettings() {
        try {
            showProgressDialog("Saving display settings...")

            // Update settings properties
            settingsManager.isListFoto = binding.uitlegFoto.isChecked
            settingsManager.isListEpos = binding.uitlegEpos.isChecked
            settingsManager.isListWhatsapp = binding.uitlegWhatsap.isChecked
            settingsManager.isListVerjaarBlok = binding.uitlegVerjaarsdag.isChecked
            settingsManager.isListOuderdom = binding.uitlegOuderdom.isChecked
            settingsManager.isListHuwelikBlok = binding.uitlegHuweliksdag.isChecked
            settingsManager.isListWyk = binding.uitlegWyk.isChecked
            settingsManager.isListSelfoon = binding.uitlegSelfoon.isChecked
            settingsManager.isListTelefoon = binding.uitlegTelefoon.isChecked

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
            val selectedLayout = binding.layoutOpsies.selectedItem?.toString() ?: ""

            // Save settings
            settingsManager.callMonitorEnabled = binding.uitlegMonitorOproepe.isChecked
            settingsManager.callLogEnabled = binding.uitlegLogOproepe.isChecked
            settingsManager.voipLogEnabled = binding.uitlegLogVOIP.isChecked
            settingsManager.defLayout = selectedLayout
            settingsManager.whatsapp1 = binding.uitlegW1.isChecked
            settingsManager.whatsapp2 = binding.uitlegW2.isChecked
            settingsManager.whatsapp3 = binding.uitlegW3.isChecked
            settingsManager.eposHtml = binding.uitlegHtml.isChecked
            settingsManager.selectedCalendarId = calendarId

            hideProgressDialog()
            showSuccessToast("Function settings saved successfully")
            if (binding.uitlegLogVOIP.isChecked) {
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

            settingsManager.widgetDoop = binding.widgetDoopSelect.isChecked
            settingsManager.widgetBelydenis = binding.widgetBelydenisSelect.isChecked
            settingsManager.widgetHuwelik = binding.widgetHuwelikSelect.isChecked
            settingsManager.widgetSterf = binding.widgetSterf.isChecked

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

            // Instead of just view.setBackgroundColor(color), use the same helper
            if (view is TextView) {
                updateTextViewBackground(view, color)
            } else {
                view.setBackgroundColor(color)
            }

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
        selectedPastoralCalendarId = settingsManager.getPastoralCalendarId() ?: -1L
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
            setupPastoralCalendarSpinner()
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
            binding.calendarSpinner.adapter = adapter
            binding.calendarSpinner.isEnabled = false
            Log.w(TAG, "Calendar spinner disabled: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up empty calendar spinner", e)
        }
    }

    private fun setupCalendarSpinner() {
        try {
            binding.calendarSpinner.isEnabled = true

            val calendarNames = availableCalendars.map {
                "${it.displayName} (${it.accountName})"
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, calendarNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.calendarSpinner.adapter = adapter

            selectSavedCalendar()

            binding.calendarSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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
                        binding.calendarSpinner.setSelection(index)
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

    private fun setupGoogleTasksUI() {
        val mode = settingsManager.googleTasksMode()
        when (mode) {
            SettingsManager.GoogleTasksMode.OFF -> binding.tasksModeOff.isChecked = true
            SettingsManager.GoogleTasksMode.API -> binding.tasksModeApi.isChecked = true
            SettingsManager.GoogleTasksMode.SHARE -> binding.tasksModeShare.isChecked = true
        }



        // Show/hide sign-in and spinner based on mode
        val isApi = mode == SettingsManager.GoogleTasksMode.API

    }

    private fun saveGoogleTasksSettings() {
        val selectedId = binding.tasksModeGroup.checkedRadioButtonId
        val mode = when (selectedId) {
            R.id.tasks_mode_api -> SettingsManager.GoogleTasksMode.API
            R.id.tasks_mode_share -> SettingsManager.GoogleTasksMode.SHARE
            else -> SettingsManager.GoogleTasksMode.OFF
        }
        settingsManager.setGoogleTasksMode(mode)
        settingsManager.pastoralCalendarSyncEnabled = binding.pastoralCalendarAutoSync.isChecked
        settingsManager.setPastoralCalendarId(
            if (selectedPastoralCalendarId != -1L) selectedPastoralCalendarId else null
        )

        val scriptUrl = binding.appsScriptLink.text?.toString()?.trim()
        val scriptSecret = binding.appsScriptKey.text?.toString()?.trim()
        settingsManager.tasksScriptUrl = scriptUrl?.ifBlank { null }
        settingsManager.tasksScriptSecret = scriptSecret?.ifBlank { null }

        Toast.makeText(this, "Bedienings instellings gestoor", Toast.LENGTH_SHORT).show()
        finish() // optional – close settings after saving
    }

    private fun setupPastoralCalendarSpinner() {
        if (availableCalendars.isEmpty()) {
            setupEmptyPastoralSpinner("No calendars found")
            return
        }
        pastoralCalendars = availableCalendars
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            availableCalendars.map { "${it.displayName} (${it.accountName})" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.pastoralCalendarSpinner.adapter = adapter
        binding.pastoralCalendarSpinner.isEnabled = true

        // Select saved calendar
        val savedId = settingsManager.getPastoralCalendarId()
        if (savedId != null) {
            val index = availableCalendars.indexOfFirst { it.id == savedId }
            if (index >= 0) binding.pastoralCalendarSpinner.setSelection(index)
        }

        binding.pastoralCalendarSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position < availableCalendars.size) {
                    selectedPastoralCalendarId = availableCalendars[position].id
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupEmptyPastoralSpinner(message: String) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf(message))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.pastoralCalendarSpinner.adapter = adapter
        binding.pastoralCalendarSpinner.isEnabled = false
    }

    private fun copyScriptToClipboard() {
        val scriptCode = """
/**
 * WinkerkReader Pastoral Reminders — Google Tasks Bridge
 * Deploy as: Web App | Execute as: Me | Who has access: Anyone
 *
 * Setup:
 * 1. Project Settings → Script Properties → add SECRET = (choose your own key)
 * 2. Services (+) → Add "Tasks API"
 * 3. Deploy → New deployment → Web App
 * 4. Copy the /exec URL into WinkerkReader Settings → Apps Script-skakel
 * 5. Enter the same SECRET into Settings → Geheime kode
 */

const WKR_MARKER = '[WKR] ';   // Prefix added to task titles for identification

function doGet(e) {
  var secret = PropertiesService.getScriptProperties().getProperty('SECRET');
  if (!e.parameter || e.parameter.secret !== secret) {
    console.log('Invalid secret provided');
    return respond('UNAUTHORIZED');
  }

  var action = e.parameter.action;
  console.log('Action: ' + action);

  try {
    if (action === 'add')      return handleAdd(e);
    if (action === 'delete')   return handleDelete(e);
    if (action === 'complete') return handleComplete(e);
    return respond('ERROR:unknown_action');
  } catch (err) {
    console.error('Error in doGet: ' + err.message);
    return respond('ERROR:' + err.message);
  }
}

function handleAdd(e) {
  var title = e.parameter.title || 'Herinnering';
  var notes = e.parameter.notes || '';
  var due   = e.parameter.due;
  var fullTitle = WKR_MARKER + title;
  console.log('Adding task: ' + fullTitle);

  var taskListId = getDefaultTaskListId();
  var task = { title: fullTitle, notes: notes };
  if (due) task.due = due + 'T00:00:00.000Z';

  var created = Tasks.Tasks.insert(task, taskListId);
  console.log('Task created with ID: ' + created.id);
  return respond('OK:' + created.id);
}

function handleDelete(e) {
  var taskId = e.parameter.taskId;
  if (!taskId) return respond('ERROR:no_taskId');
  if (!taskBelongsToUs(taskId)) return respond('ERROR:not_our_task');

  console.log('Deleting task: ' + taskId);
  var taskListId = getDefaultTaskListId();
  Tasks.Tasks.remove(taskListId, taskId);
  return respond('DELETED');
}

function handleComplete(e) {
  var taskId = e.parameter.taskId;
  if (!taskId) return respond('ERROR:no_taskId');
  if (!taskBelongsToUs(taskId)) return respond('ERROR:not_our_task');

  console.log('Completing task: ' + taskId);
  var taskListId = getDefaultTaskListId();
  var task = Tasks.Tasks.get(taskListId, taskId);
  task.status    = 'completed';
  task.completed = new Date().toISOString();
  Tasks.Tasks.update(task, taskListId, taskId);
  return respond('COMPLETED');
}

var CACHED_TASK_LIST_ID = null;
function getDefaultTaskListId() {
  if (CACHED_TASK_LIST_ID) return CACHED_TASK_LIST_ID;
  var lists = Tasks.Tasklists.list();
  CACHED_TASK_LIST_ID = lists.items[0].id;
  return CACHED_TASK_LIST_ID;
}

function respond(text) {
  return ContentService.createTextOutput(text).setMimeType(ContentService.MimeType.TEXT);
}

function taskBelongsToUs(taskId) {
  try {
    var taskListId = getDefaultTaskListId();
    var task = Tasks.Tasks.get(taskListId, taskId);
    return task.title && task.title.startsWith(WKR_MARKER);
  } catch (e) {
    console.log('Error fetching task ' + taskId + ': ' + e.message);
    return false;
  }
}
    """.trimIndent()

        val instructions = """
📋 INSTALLASIE-INSTRUKSIES:

1. Gaan na script.google.com en skep 'n nuwe projek.
2. Vee die standaardkode uit en plak die skrip hierbo in.
3. Klik op "Project Settings" (rat-ikoon) en voeg 'n script property by:
   - Eienskap: SECRET
   - Waarde: Kies 'n geheim (bv. 'MyGeheim123') – onthou dit!
4. Klik op "Services" (+), voeg "Tasks API" by en aktiveer dit.
5. Klik op "Deploy" → "New deployment" → "Web app".
6. Stel "Execute as" op "Me" en "Who has access" op "Anyone".
7. Klik "Deploy" en kopieer die /exec URL.
8. Plak die URL in die "Apps Script-skakel" veld hierbo.
9. Plak dieselfde SECRET in die "Geheime kode" veld hierbo.
10. Stoor die instellings.

Die skrip is nou gereed!
    """.trimIndent()

        val fullText = "$scriptCode\n\n$instructions"

        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText("WinkerkReader Script", fullText)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, "Skrip en instruksies is na knipbord gekopieer", Toast.LENGTH_LONG).show()
    }
}