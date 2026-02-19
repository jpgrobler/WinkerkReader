package za.co.jpsoft.winkerkreader;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.*;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.KEY_LOG_VOIP;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Enhanced SettingsManager with comprehensive data validation and error handling.
 * Provides type-safe, validated access to application settings with proper error recovery.
 */
public class SettingsManager {    private static final String TAG = "SettingsManager";

    
    // Validation Constants
    private static final long MIN_CALENDAR_ID = 0;
    private static final long MAX_CALENDAR_ID = Long.MAX_VALUE;
    private static final int MIN_COLOR_VALUE = Color.TRANSPARENT;
    private static final int MAX_COLOR_VALUE = 0xFFFFFFFF;
    private static final int MAX_LAYOUT_NAME_LENGTH = 50;
    private static final Set<String> VALID_LAYOUTS = new HashSet<>(Arrays.asList(
        "GESINNE", "ADRES", "VERJAARSDAG", "HUWELIK", "OUDERDOM", "VAN", "WYK"
    ));
    
    // Default Values
    private static final boolean DEFAULT_BOOLEAN = false;
    private static final String DEFAULT_LAYOUT = "GESINNE";
    private static final long DEFAULT_CALENDAR_ID = -1;
    private static final int DEFAULT_COLOR = Color.WHITE;
    private static final int DEFAULT_COLOR2 = Color.YELLOW;
    private static final int DEFAULT_COLOR3 = Color.LTGRAY;
    
    private final SharedPreferences userPrefs;
   // private final SharedPreferences callLoggerPrefs;
    private static SettingsManager instance;
    
    private SettingsManager(Context context) {
        userPrefs = context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE);

        
        // Initialize default values if first run
        initializeDefaultsIfNeeded();
    }
    
    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context.getApplicationContext());
        }
        return instance;
    }
    
    // ========== VALIDATION METHODS ==========
    
    /**
     * Validates calendar ID
     * @param calendarId Calendar ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidCalendarId(long calendarId) {
        return calendarId == DEFAULT_CALENDAR_ID || (calendarId >= MIN_CALENDAR_ID && calendarId <= MAX_CALENDAR_ID);
    }
    
    /**
     * Validates color value
     * @param color Color integer to validate
     * @return true if valid color, false otherwise
     */
    public static boolean isValidColor(@ColorInt int color) {
        // Allow any 32-bit color value including transparent
        return true; // Android colors are always valid 32-bit integers
    }
    
    /**
     * Validates layout name
     * @param layoutName Layout name to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidLayoutName(@Nullable String layoutName) {
        if (TextUtils.isEmpty(layoutName)) {
            return false;
        }
        
        if (layoutName.length() > MAX_LAYOUT_NAME_LENGTH) {
            Log.w(TAG, "Layout name too long: " + layoutName.length() + " characters");
            return false;
        }
        
        // Check for potentially harmful characters
        if (layoutName.contains("<") || layoutName.contains(">") || 
            layoutName.contains("\"") || layoutName.contains("'")) {
            Log.w(TAG, "Layout name contains invalid characters: " + layoutName);
            return false;
        }
        
        return VALID_LAYOUTS.contains(layoutName);
    }
    
    /**
     * Sanitizes layout name by removing invalid characters and trimming
     * @param layoutName Raw layout name
     * @return Sanitized layout name or default if invalid
     */
    @NonNull
    private String sanitizeLayoutName(@Nullable String layoutName) {
        if (TextUtils.isEmpty(layoutName)) {
            return DEFAULT_LAYOUT;
        }
        
        String sanitized = layoutName.trim();
        
        // Remove potentially harmful characters
        sanitized = sanitized.replaceAll("[<>\"']", "");
        
        if (sanitized.length() > MAX_LAYOUT_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_LAYOUT_NAME_LENGTH);
        }
        
        // If still invalid after sanitization, return default
        return VALID_LAYOUTS.contains(sanitized) ? sanitized : DEFAULT_LAYOUT;
    }
    
    /**
     * Validates and sanitizes color value
     * @param color Raw color value
     * @return Valid color value
     */
    @ColorInt
    private int sanitizeColor(@ColorInt int color) {
        // For this app, we'll ensure colors are opaque if they're too transparent
        int alpha = Color.alpha(color);
        if (alpha < 50) { // Very transparent colors might not be visible
            Log.w(TAG, "Color too transparent, setting to semi-transparent: " + Integer.toHexString(color));
            return Color.argb(128, Color.red(color), Color.green(color), Color.blue(color));
        }
        return color;
    }
    
    // ========== ERROR HANDLING METHODS ==========
    
    /**
     * Safe method to save boolean preference with error handling
     */
    private boolean saveBooleanPreference(SharedPreferences prefs, String key, boolean value) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(key, value);
            boolean success = editor.commit();  // Use commit() for immediate feedback
            
            if (!success) {
                Log.e(TAG, "Failed to save boolean preference: " + key + " = " + value);
                return false;
            }
            
            Log.d(TAG, "Successfully saved: " + key + " = " + value);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving boolean preference: " + key, e);
            return false;
        }
    }
    
    /**
     * Safe method to save string preference with validation and error handling
     */
    private boolean saveStringPreference(SharedPreferences prefs, String key, String value) {
        try {
            String sanitizedValue = key.equals(KEY_DEFLAYOUT) ? 
                sanitizeLayoutName(value) : (value != null ? value.trim() : "");
            
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(key, sanitizedValue);
            boolean success = editor.commit();
            
            if (!success) {
                Log.e(TAG, "Failed to save string preference: " + key + " = " + sanitizedValue);
                return false;
            }
            
            Log.d(TAG, "Successfully saved: " + key + " = " + sanitizedValue);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving string preference: " + key, e);
            return false;
        }
    }
    
    /**
     * Safe method to save long preference with validation
     */
    private boolean saveLongPreference(SharedPreferences prefs, String key, long value) {
        try {
            if (key.equals(KEY_SELECTED_CALENDAR_ID) && !isValidCalendarId(value)) {
                Log.w(TAG, "Invalid calendar ID: " + value + ", using default");
                value = DEFAULT_CALENDAR_ID;
            }
            
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(key, value);
            boolean success = editor.commit();
            
            if (!success) {
                Log.e(TAG, "Failed to save long preference: " + key + " = " + value);
                return false;
            }
            
            Log.d(TAG, "Successfully saved: " + key + " = " + value);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving long preference: " + key, e);
            return false;
        }
    }
    
    /**
     * Safe method to save int preference (for colors) with validation
     */
    private boolean saveIntPreference(SharedPreferences prefs, String key, @ColorInt int value) {
        try {
            int sanitizedValue = sanitizeColor(value);
            
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(key, sanitizedValue);
            boolean success = editor.commit();
            
            if (!success) {
                Log.e(TAG, "Failed to save int preference: " + key + " = " + Integer.toHexString(sanitizedValue));
                return false;
            }
            
            Log.d(TAG, "Successfully saved color: " + key + " = " + Integer.toHexString(sanitizedValue));
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving int preference: " + key, e);
            return false;
        }
    }

    /**
     * Initialize default values if this is the first run
     * Updated to set most checkboxes to TRUE by default
     */
    private void initializeDefaultsIfNeeded() {
        try {
            boolean hasInitialized = userPrefs.getBoolean("_initialized", false);
            if (!hasInitialized) {
                Log.i(TAG, "First run detected, initializing defaults");

                SharedPreferences.Editor editor = userPrefs.edit();

                // DISPLAY SETTINGS (Uitleg + Icons) - ALL TRUE BY DEFAULT
                editor.putBoolean(KEY_LIST_FOTO, true);           // Foto
                editor.putBoolean(KEY_LIST_EPOS, true);           // Epos
                editor.putBoolean(KEY_LIST_WHATSAPP, true);       // Whatsapp
                editor.putBoolean(KEY_LIST_VERJAARBLOK, true);    // Verjaarsdag
                editor.putBoolean(KEY_LIST_OUDERDOM, true);       // Ouderdom
                editor.putBoolean(KEY_LIST_HUWELIKBLOK, true);    // Huweliksdatum
                editor.putBoolean(KEY_LIST_WYK, true);            // Wyk
                editor.putBoolean(KEY_LIST_SELFOON, true);        // Selfoon
                editor.putBoolean(KEY_LIST_TELEFOON, true);       // Telefoon

                // WIDGET SETTINGS - ALL TRUE BY DEFAULT
                editor.putBoolean(KEY_WIDGET_DOOP, true);         // Doopdatum
                editor.putBoolean(KEY_WIDGET_BELYDENIS, true);    // Belydenisdatum
                editor.putBoolean(KEY_WIDGET_HUWELIK, true);      // Huweliksdatum
                editor.putBoolean(KEY_WIDGET_STERF, true);        // Sterfdatum

                // WHATSAPP SETTINGS - ALL TRUE BY DEFAULT
                editor.putBoolean(KEY_WHATSAPP1, true);           // Whatsapp Metode 1
                editor.putBoolean(KEY_WHATSAPP2, true);           // Whatsapp Metode 2
                editor.putBoolean(KEY_WHATSAPP3, true);           // Whatsapp Metode 3

                // FUNCTION SETTINGS - Keep these FALSE by default for safety
                editor.putBoolean(KEY_AUTOSTART, DEFAULT_BOOLEAN);        // Auto start
                editor.putBoolean(KEY_OPROEPMONITOR, DEFAULT_BOOLEAN);    // Monitor Oproepe
                editor.putBoolean(KEY_OPROEPLOG, DEFAULT_BOOLEAN);        // Log Tel Oproepe
                editor.putBoolean(KEY_LOG_VOIP, DEFAULT_BOOLEAN);         // Log VOIP Oproepe
                editor.putBoolean(KEY_EPOSHTML, DEFAULT_BOOLEAN);         // HTML Epos

                // OTHER SETTINGS
                editor.putString(KEY_DEFLAYOUT, DEFAULT_LAYOUT);
                editor.putInt(KEY_GEMEENTE_KLEUR, DEFAULT_COLOR);
                editor.putInt(KEY_GEMEENTE2_KLEUR, DEFAULT_COLOR2);
                editor.putInt(KEY_GEMEENTE3_KLEUR, DEFAULT_COLOR3);
                editor.putLong(KEY_SELECTED_CALENDAR_ID, DEFAULT_CALENDAR_ID);

                // Mark as initialized
                editor.putBoolean("_initialized", true);

                boolean success = editor.commit();

                if (success) {
                    Log.i(TAG, "Default values initialized successfully");
                    Log.i(TAG, "Display settings: All enabled by default");
                    Log.i(TAG, "Widget settings: All enabled by default");
                    Log.i(TAG, "WhatsApp methods: All enabled by default");
                    Log.i(TAG, "Function settings: All disabled by default (for safety)");
                } else {
                    Log.w(TAG, "Failed to apply default values");
                }
            } else {
                Log.d(TAG, "App already initialized, skipping default value setup");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing default values", e);
        }
    }
    
    // ========== DISPLAY SETTINGS WITH VALIDATION ==========
    
    public boolean isListFotoEnabled() {
        try {
            return userPrefs.getBoolean(KEY_LIST_FOTO, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading LIST_FOTO preference", e);
            return DEFAULT_BOOLEAN;
        }
    }
    
    public boolean setListFotoEnabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_LIST_FOTO, enabled);
    }
    
    public boolean isListEposEnabled() {
        try {
            return userPrefs.getBoolean(KEY_LIST_EPOS, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading LIST_EPOS preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isEposHtmlEnabled() {
        try {
            return userPrefs.getBoolean(KEY_EPOSHTML, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading EPOSHTML preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isListOuderdomEnabled() {
        try {
            return userPrefs.getBoolean(KEY_LIST_OUDERDOM, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading LIST_OUDERDOM preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isListSelfoonEnabled() {
        try {
            return userPrefs.getBoolean(KEY_LIST_SELFOON, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading LIST_SELFOON preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isListHuwelikblokEnabled() {
        try {
            return userPrefs.getBoolean(KEY_LIST_HUWELIKBLOK, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading LIST_HUWELIKBLOK preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isListTelefoonEnabled() {
        try {
            return userPrefs.getBoolean(KEY_LIST_TELEFOON, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading LIST_TELEFOON preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isListVerjaarblokEnabled() {
        try {
            return userPrefs.getBoolean(KEY_LIST_VERJAARBLOK, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading LIST_VERJAARBLOK preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isListWhatsappEnabled() {
        try {
            return userPrefs.getBoolean(KEY_LIST_WHATSAPP, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading LIST_WHATSAPBLOK preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isListWykEnabled() {
        try {
            return userPrefs.getBoolean(KEY_LIST_WYK, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading LIST_WYKBLOK preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isAutoStartEnabled() {
        try {
            return userPrefs.getBoolean(KEY_AUTOSTART, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading OPROEPLOG preference", e);
            return DEFAULT_BOOLEAN;
        }
    }
    public boolean isOproepLogEnabled() {
        try {
            return userPrefs.getBoolean(KEY_OPROEPLOG, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading OPROEPLOG preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isOproepMonitorEnabled() {
        try {
            return userPrefs.getBoolean(KEY_OPROEPMONITOR, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading OPROEPMONITOR preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isWhatsapp1Enabled() {
        try {
            return userPrefs.getBoolean(KEY_WHATSAPP1, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading WHATSAPP1 preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isWhatsapp2Enabled() {
        try {
            return userPrefs.getBoolean(KEY_WHATSAPP2, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading WHATSAPP2 preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isWhatsapp3Enabled() {
        try {
            return userPrefs.getBoolean(KEY_WHATSAPP3, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading WHATSAPP3 preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isWidgetDoopEnabled() {
        try {
            return userPrefs.getBoolean(KEY_WIDGET_DOOP, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading WIDGET_DOOP preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isWidgetBelydenisEnabled() {
        try {
            return userPrefs.getBoolean(KEY_WIDGET_BELYDENIS, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading WIDGET_BELYDENIS preference", e);
            return DEFAULT_BOOLEAN;
        }
    }
    public boolean isWidgetHuwelikEnabled() {
        try {
            return userPrefs.getBoolean(KEY_WIDGET_HUWELIK, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading WIDGET_HUWELIK preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    public boolean isWidgetSterfEnabled() {
        try {
            return userPrefs.getBoolean(KEY_WIDGET_STERF, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading WIDGET_STERF preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    // SET
    public boolean setAutostart(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_AUTOSTART, enabled);
    }
    public boolean setOproepMonitorEnabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_OPROEPMONITOR, enabled);
    }

    public boolean setOproepLogEnabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_OPROEPLOG, enabled);
    }

    public boolean setWhatsapp1Enabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_WHATSAPP1, enabled);
    }
    public boolean setWhatsapp2Enabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_WHATSAPP2, enabled);
    }
    public boolean setWhatsapp3Enabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_WHATSAPP3, enabled);
    }
    public boolean setEposHtmlEnabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_EPOSHTML, enabled);
    }
    public boolean setWidgetDoopEnabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_WIDGET_DOOP, enabled);
    }
    public boolean setWidgetSterfEnabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_WIDGET_STERF, enabled);
    }
    public boolean setWidgetBelydenisEnabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_WIDGET_BELYDENIS, enabled);
    }
    public boolean setWidgetHuwelikEnabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_WIDGET_HUWELIK, enabled);
    }
    /**
     * Check if VOIP call logging is enabled
     * @return true if VOIP calls should be logged, false otherwise
     */
    public boolean isVoipLogEnabled() {
        try {
            return userPrefs.getBoolean(KEY_LOG_VOIP, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading LOG_VOIP preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    /**
     * Enable or disable VOIP call logging
     * @param enabled true to enable VOIP logging, false to disable
     * @return true if successfully saved, false otherwise
     */
    public boolean setVoipLogEnabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_LOG_VOIP, enabled);
    }

    /**
     * Check if telephone call logging is enabled
     * @return true if telephone calls should be logged, false otherwise
     */
    public boolean isTelephoneLogEnabled() {
        try {
            return userPrefs.getBoolean(KEY_OPROEPLOG, DEFAULT_BOOLEAN);
        } catch (Exception e) {
            Log.e(TAG, "Error reading OPROEPLOG (telephone log) preference", e);
            return DEFAULT_BOOLEAN;
        }
    }

    /**
     * Enable or disable telephone call logging
     * @param enabled true to enable telephone logging, false to disable
     * @return true if successfully saved, false otherwise
     */
    public boolean setTelephoneLogEnabled(boolean enabled) {
        return saveBooleanPreference(userPrefs, KEY_OPROEPLOG, enabled);
    }
    // ========== FUNCTION SETTINGS WITH VALIDATION ==========
    
    @NonNull
    public String getDefaultLayout() {
        try {
            String layout = userPrefs.getString(KEY_DEFLAYOUT, DEFAULT_LAYOUT);
            if (!isValidLayoutName(layout)) {
                Log.w(TAG, "Invalid layout found in preferences: " + layout + ", using default");
                return DEFAULT_LAYOUT;
            }
            return layout;
        } catch (Exception e) {
            Log.e(TAG, "Error reading default layout preference", e);
            return DEFAULT_LAYOUT;
        }
    }
    
    public boolean setDefaultLayout(@Nullable String layout) {
        if (!isValidLayoutName(layout)) {
            Log.w(TAG, "Attempted to save invalid layout: " + layout);
            return false;
        }
        return saveStringPreference(userPrefs, KEY_DEFLAYOUT, layout);
    }
    
    public long getSelectedCalendarId() {
        try {
            long calendarId = userPrefs.getLong(KEY_SELECTED_CALENDAR_ID, DEFAULT_CALENDAR_ID);
            if (!isValidCalendarId(calendarId)) {
                Log.w(TAG, "Invalid calendar ID found: " + calendarId + ", using default");
                return DEFAULT_CALENDAR_ID;
            }
            return calendarId;
        } catch (Exception e) {
            Log.e(TAG, "Error reading calendar ID preference", e);
            return DEFAULT_CALENDAR_ID;
        }
    }
    
    public boolean setSelectedCalendarId(long calendarId) {
        if (!isValidCalendarId(calendarId)) {
            Log.w(TAG, "Attempted to save invalid calendar ID: " + calendarId);
            return false;
        }
        return saveLongPreference(userPrefs, KEY_SELECTED_CALENDAR_ID, calendarId);
    }
    
    // ========== COLOR SETTINGS WITH VALIDATION ==========
    
    @ColorInt
    public int getGemeenteKleur() {
        try {
            int color = userPrefs.getInt(KEY_GEMEENTE_KLEUR, DEFAULT_COLOR);
            return sanitizeColor(color);
        } catch (Exception e) {
            Log.e(TAG, "Error reading gemeente color preference", e);
            return DEFAULT_COLOR;
        }
    }
    
    public boolean setGemeenteKleur(@ColorInt int color) {
        if (!isValidColor(color)) {
            Log.w(TAG, "Attempted to save invalid color: " + Integer.toHexString(color));
            return false;
        }
        return saveIntPreference(userPrefs, KEY_GEMEENTE_KLEUR, color);
    }
    
    @ColorInt
    public int getGemeente2Kleur() {
        try {
            int color = userPrefs.getInt(KEY_GEMEENTE2_KLEUR, DEFAULT_COLOR);
            return sanitizeColor(color);
        } catch (Exception e) {
            Log.e(TAG, "Error reading gemeente2 color preference", e);
            return DEFAULT_COLOR;
        }
    }
    
    public boolean setGemeente2Kleur(@ColorInt int color) {
        if (!isValidColor(color)) {
            Log.w(TAG, "Attempted to save invalid color: " + Integer.toHexString(color));
            return false;
        }
        return saveIntPreference(userPrefs, KEY_GEMEENTE2_KLEUR, color);
    }
    
    @ColorInt
    public int getGemeente3Kleur() {
        try {
            int color = userPrefs.getInt(KEY_GEMEENTE3_KLEUR, DEFAULT_COLOR);
            return sanitizeColor(color);
        } catch (Exception e) {
            Log.e(TAG, "Error reading gemeente3 color preference", e);
            return DEFAULT_COLOR;
        }
    }
    
    public boolean setGemeente3Kleur(@ColorInt int color) {
        if (!isValidColor(color)) {
            Log.w(TAG, "Attempted to save invalid color: " + Integer.toHexString(color));
            return false;
        }
        return saveIntPreference(userPrefs, KEY_GEMEENTE3_KLEUR, color);
    }
    
    // ========== BULK OPERATIONS WITH VALIDATION ==========
    
    /**
     * Save all display settings with transaction safety and validation
     * @param settings Display settings to save
     * @return OperationResult with success status and any error messages
     */
    @NonNull
    public OperationResult saveDisplaySettings(@NonNull DisplaySettings settings) {
        if (settings == null) {
            return OperationResult.failure("Settings cannot be null");
        }
        
        try {
            SharedPreferences.Editor editor = userPrefs.edit();
            
            // Validate all settings first
            StringBuilder validationErrors = new StringBuilder();
            
            // For boolean settings, validation is simple (always valid)
            // But we can add business logic validation here
            
            if (validationErrors.length() > 0) {
                return OperationResult.failure("Validation failed: " + validationErrors.toString());
            }
            
            // Apply all changes in one transaction
            editor.putBoolean(KEY_LIST_FOTO, settings.listFoto);
            editor.putBoolean(KEY_LIST_EPOS, settings.listEpos);
            editor.putBoolean(KEY_LIST_WHATSAPP, settings.listWhatsapp);
            editor.putBoolean(KEY_LIST_VERJAARBLOK, settings.listVerjaarblok);
            editor.putBoolean(KEY_LIST_OUDERDOM, settings.listOuderdom);
            editor.putBoolean(KEY_LIST_HUWELIKBLOK, settings.listHuwelikblok);
            editor.putBoolean(KEY_LIST_WYK, settings.listWyk);
            editor.putBoolean(KEY_LIST_SELFOON, settings.listSelfoon);
            editor.putBoolean(KEY_LIST_TELEFOON, settings.listTelefoon);
            
            boolean success = editor.commit();
            
            if (success) {
                Log.i(TAG, "Display settings saved successfully");
                return OperationResult.success("Display settings saved successfully");
            } else {
                Log.e(TAG, "Failed to commit display settings");
                return OperationResult.failure("Failed to save display settings to storage");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving display settings", e);
            return OperationResult.failure("Unexpected error: " + e.getMessage());
        }
    }
    
    // ========== RESULT CLASSES ==========
    
    /**
     * Result class for operations that can fail
     */
    public static class OperationResult {
        private final boolean success;
        private final String message;
        
        private OperationResult(boolean success, String message) {
            this.success = success;
            this.message = message != null ? message : "";
        }
        
        public static OperationResult success(String message) {
            return new OperationResult(true, message);
        }
        
        public static OperationResult failure(String message) {
            return new OperationResult(false, message);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    // ========== DATA CLASSES ==========
    
    public static class DisplaySettings {
        public final boolean listFoto;
        public final boolean listEpos;
        public final boolean listWhatsapp;
        public final boolean listVerjaarblok;
        public final boolean listOuderdom;
        public final boolean listHuwelikblok;
        public final boolean listWyk;
        public final boolean listSelfoon;
        public final boolean listTelefoon;
        
        public DisplaySettings(boolean listFoto, boolean listEpos, boolean listWhatsapp,
                             boolean listVerjaarblok, boolean listOuderdom, boolean listHuwelikblok,
                             boolean listWyk, boolean listSelfoon, boolean listTelefoon) {
            this.listFoto = listFoto;
            this.listEpos = listEpos;
            this.listWhatsapp = listWhatsapp;
            this.listVerjaarblok = listVerjaarblok;
            this.listOuderdom = listOuderdom;
            this.listHuwelikblok = listHuwelikblok;
            this.listWyk = listWyk;
            this.listSelfoon = listSelfoon;
            this.listTelefoon = listTelefoon;
        }
        
        /**
         * Validates that at least one display option is enabled
         */
        public boolean isValid() {
            return listFoto || listEpos || listWhatsapp || listVerjaarblok || 
                   listOuderdom || listHuwelikblok || listWyk || listSelfoon || listTelefoon;
        }
        
        @Override
        public String toString() {
            return "DisplaySettings{" +
                    "listFoto=" + listFoto +
                    ", listEpos=" + listEpos +
                    ", listWhatsapp=" + listWhatsapp +
                    ", listVerjaarblok=" + listVerjaarblok +
                    ", listOuderdom=" + listOuderdom +
                    ", listHuwelikblok=" + listHuwelikblok +
                    ", listWyk=" + listWyk +
                    ", listSelfoon=" + listSelfoon +
                    ", listTelefoon=" + listTelefoon +
                    '}';
        }
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Reset all settings to defaults with confirmation
     * @return OperationResult indicating success or failure
     */
    @NonNull
    public OperationResult resetAllSettings() {
        try {
            Log.i(TAG, "Resetting all settings to defaults");
            
            SharedPreferences.Editor userEditor = userPrefs.edit();
            //SharedPreferences.Editor callEditor = callLoggerPrefs.edit();
            
            // Clear all preferences
            userEditor.clear();

            
            // Apply changes
            boolean userSuccess = userEditor.commit();
            
            if (userSuccess ) {
                // Reinitialize defaults
                initializeDefaultsIfNeeded();
                Log.i(TAG, "All settings reset successfully");
                return OperationResult.success("All settings have been reset to defaults");
            } else {
                Log.e(TAG, "Failed to reset settings");
                return OperationResult.failure("Failed to reset settings");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error resetting settings", e);
            return OperationResult.failure("Unexpected error while resetting: " + e.getMessage());
        }
    }
    
    /**
     * Export current settings as a string (for backup/debugging)
     */
    @NonNull
    public String exportSettings() {
        try {
            StringBuilder export = new StringBuilder();
            export.append("=== WinkerkReader Settings Export ===\n");
            export.append("Timestamp: ").append(System.currentTimeMillis()).append("\n\n");
            
            export.append("Display Settings:\n");
            export.append("LIST_FOTO: ").append(isListFotoEnabled()).append("\n");
            export.append("LIST_EPOS: ").append(isListEposEnabled()).append("\n");
            export.append("Default Layout: ").append(getDefaultLayout()).append("\n");
            export.append("Calendar ID: ").append(getSelectedCalendarId()).append("\n");
            export.append("Gemeente Colors: ")
                  .append(Integer.toHexString(getGemeenteKleur())).append(", ")
                  .append(Integer.toHexString(getGemeente2Kleur())).append(", ")
                  .append(Integer.toHexString(getGemeente3Kleur())).append("\n");
            
            return export.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error exporting settings", e);
            return "Error exporting settings: " + e.getMessage();
        }
    }
}
