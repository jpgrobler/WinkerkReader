package za.co.jpsoft.winkerkreader;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import za.co.jpsoft.winkerkreader.data.WinkerkContract;
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

import org.joda.time.DateTime;
import org.joda.time.MonthDay;
import org.joda.time.Years;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import static za.co.jpsoft.winkerkreader.Utils.parseDate;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.*;

/**
 * Enhanced Widget Service with improved error handling, performance, and thread safety.
 * Maintains full compatibility with original layouts and functionality.
 */
public class ListViewWidgetService extends RemoteViewsService {
    private static final String TAG = "ListViewWidgetService";

    @Override
    public RemoteViewsService.RemoteViewsFactory onGetViewFactory(Intent intent) {
        Log.d(TAG, "Creating RemoteViewsFactory");

        try {
            WinkerkContract.winkerkEntry.id = Settings.Secure.getString(
                    getApplicationContext().getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
        } catch (Exception e) {
            Log.e(TAG, "Error getting device ID", e);
        }

        return new EnhancedLoremViewsFactory(getApplicationContext(), intent);
    }
}

/**
 * Enhanced factory that maintains your original data structure and logic
 * while adding modern error handling and thread safety
 */
class EnhancedLoremViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private static final String TAG = "EnhancedLoremFactory";
    private static final int LOOK_AHEAD_DAYS = 15;

    // Thread safety
    private final ReentrantLock dataLock = new ReentrantLock();

    // Context and widget info
    private final Context context;
    private final int appWidgetId;

    // Your original data structure - keeping exactly the same
    private ArrayList<String> records = new ArrayList<>();
    private ArrayList<String> records2 = new ArrayList<>();  // day
    private ArrayList<String> records3 = new ArrayList<>();  // month
    private ArrayList<String> records4 = new ArrayList<>();  // reason
    private ArrayList<String> records5 = new ArrayList<>();  // gemeente

    // Database components
    private SQLiteAssetHelper databaseHelper;
    private SQLiteDatabase database;

    // Widget settings (cached to avoid repeated SharedPreferences reads)
    private boolean widgetSettingsLoaded = false;
    private boolean WIDGET_DOOP = true;
    private boolean WIDGET_BELYDENIS = true;
    private boolean WIDGET_HUWELIK = true;
    private boolean WIDGET_STERF = true;

    public EnhancedLoremViewsFactory(Context context, Intent intent) {
        this.context = context;
        this.appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );

        Log.d(TAG, "Created factory for widget " + appWidgetId);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        try {
            initializeDatabase();
            loadWidgetSettings(); // Load once at creation
            updateView();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            addErrorItem("Failed to initialize");
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        dataLock.lock();
        try {
            closeDatabase();
            clearAllData();
        } finally {
            dataLock.unlock();
        }
    }

    @Override
    public int getCount() {
        dataLock.lock();
        try {
            return records.size();
        } finally {
            dataLock.unlock();
        }
    }

    @Override
    public RemoteViews getViewAt(int position) {
        dataLock.lock();
        try {
            if (position < 0 || position >= records.size()) {
                return createErrorRow("Invalid position");
            }

            return createViewForPosition(position);

        } catch (Exception e) {
            Log.e(TAG, "Error creating view for position " + position, e);
            return createErrorRow("Error: " + e.getMessage());
        } finally {
            dataLock.unlock();
        }
    }

    /**
     * FIXED: Your enhanced getViewAt logic with proper background color handling
     *
     * KEY FIXES:
     * 1. Removed loadWidgetSettings() call - now cached at onCreate/onDataSetChanged
     * 2. Changed filtering logic - now filters during data load, not during view creation
     * 3. Fixed background color sequence - set default AFTER checking gemeente
     * 4. Simplified view creation logic
     */
    // In C:/Pieter Folders/WinkerkReader/Winkerk10Reader 2024/app/src/main/java/com/example/android/winkerkreader/ListViewWidgetService.java

// ... inside the EnhancedLoremViewsFactory class ...

    /**
     * FIXED: Your enhanced getViewAt logic with proper background color handling
     * <p>
     * KEY FIXES:
     * 1. Logic for determining the background color has been refined for clarity.
     * 2. The background color is determined first and then set once, preventing potential state issues.
     * 3. Assumes that constants like GEMEENTE_NAAM and GEMEENTE_KLEUR are correctly loaded.
     */
    private RemoteViews createViewForPosition(int position) {
        // Create row using your original layout
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.row);

        DateTime currentDate = DateTime.now();

        // Get data for this position from your data lists
        String name = records.get(position);
        String day = records2.get(position);
        String month = records3.get(position);
        String gemeente = records5.get(position);

        // 1. DETERMINE BACKGROUND COLOR FIRST
        // Start with a default background color.
        int backgroundColor = Color.WHITE;

        // Override the default color based on the 'gemeente' field.
        // This uses the static fields you've likely loaded from SharedPreferences.
        if (!TextUtils.isEmpty(gemeente)) {
            if (gemeente.equals(GEMEENTE_NAAM)) {
                backgroundColor = GEMEENTE_KLEUR;
            } else if (gemeente.equals(GEMEENTE2_NAAM)) {
                backgroundColor = GEMEENTE2_KLEUR;
            } else if (gemeente.equals(GEMEENTE3_NAAM)) {
                backgroundColor = GEMEENTE3_KLEUR;
            }
        }

        // 2. SET THE BACKGROUND COLOR ONCE
        row.setInt(android.R.id.text1, "setBackgroundColor", backgroundColor);

        // --- The rest of your text formatting logic remains the same ---

        // Create display text
        String displayText = day + "/" + month + " " + name;
        Spannable wordtoSpan = new SpannableString(displayText);
        Integer textLength = wordtoSpan.length();

        // Apply your text formatting (spans for color, size, etc.)
        wordtoSpan.setSpan(new RelativeSizeSpan(0.6f), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Handle repeated date graying
        if (position > 0 && day.equals(records2.get(position - 1))) {
            wordtoSpan.setSpan(new ForegroundColorSpan(Color.LTGRAY), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            wordtoSpan.setSpan(new ForegroundColorSpan(Color.BLACK), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Main text color
        wordtoSpan.setSpan(new ForegroundColorSpan(Color.argb(200, 50, 50, 50)), 6, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Today's date highlighting
        if (day.equals(currentDate.toString().substring(8, 10))) {
            wordtoSpan.setSpan(new ForegroundColorSpan(Color.BLUE), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            wordtoSpan.setSpan(new ForegroundColorSpan(Color.argb(255, 0, 0, 220)), 6, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Handle parentheses formatting
        Integer startPos = textLength - 7;
        if (startPos > -1) {
            Integer parenthesesPos = wordtoSpan.toString().indexOf("(", startPos);
            if (parenthesesPos > 0) {
                wordtoSpan.setSpan(new RelativeSizeSpan(0.8f), parenthesesPos, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        // Set the final formatted text
        row.setTextViewText(android.R.id.text1, wordtoSpan);

        // Set up click intent (your original approach)
        Intent fillInIntent = new Intent();
        Bundle extras = new Bundle();
        extras.putString(WinkerkReaderWidgetProvider.EXTRA_WORD, name);
        fillInIntent.putExtras(extras);
        row.setOnClickFillInIntent(android.R.id.text1, fillInIntent);

        return row;
    }


//    private RemoteViews createViewForPosition(int position) {
//        // Create row using your original layout
//        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.row);
//
//        DateTime currentDate = DateTime.now();
//
//        // Get data for this position
//        String name = records.get(position);
//        String day = records2.get(position);
//        String month = records3.get(position);
//        String reason = records4.get(position);
//        String gemeente = records5.get(position);
//
//        // Create display text exactly as your original code
//        String displayText = day + "/" + month + " " + name;
//        Spannable wordtoSpan = new SpannableString(displayText);
//        Integer textLength = wordtoSpan.length();
//
//        // Apply your original text formatting
//        wordtoSpan.setSpan(new ForegroundColorSpan(Color.BLACK), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//        wordtoSpan.setSpan(new RelativeSizeSpan(0.6f), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//        // Handle repeated date graying (your original logic)
//        if (position > 0) {
//            if (!day.equals(records2.get(position - 1))) {
//                wordtoSpan.setSpan(new ForegroundColorSpan(Color.BLACK), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            } else {
//                wordtoSpan.setSpan(new ForegroundColorSpan(Color.LTGRAY), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            }
//        }
//
//        // Main text color (your original)
//        wordtoSpan.setSpan(new ForegroundColorSpan(Color.argb(200, 50, 50, 50)), 6, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//        // Today's date highlighting (your original logic)
//        if (day.equals(currentDate.toString().substring(8, 10))) {
//            wordtoSpan.setSpan(new ForegroundColorSpan(Color.BLUE), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            wordtoSpan.setSpan(new ForegroundColorSpan(Color.argb(255, 0, 0, 220)), 6, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//        }
//
//        // Handle parentheses formatting (your original logic)
//        Integer startPos = textLength - 7;
//        if (startPos > -1) {
//            Integer parenthesesPos = wordtoSpan.toString().indexOf("(", startPos);
//            if (parenthesesPos > 0) {
//                wordtoSpan.setSpan(new RelativeSizeSpan(0.8f), parenthesesPos, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            }
//        }
//
//        // Set the formatted text
//        row.setTextViewText(android.R.id.text1, wordtoSpan);
//
//        // CRITICAL FIX: Apply background colors in correct order
//        // First set default, then override with gemeente colors
//        int backgroundColor = Color.WHITE; // Default
//
//        // Check gemeente and override if match
//        if (!TextUtils.isEmpty(gemeente)) {
//            if (gemeente.equals(GEMEENTE_NAAM)) {
//                backgroundColor = GEMEENTE_KLEUR;
//            } else if (gemeente.equals(GEMEENTE2_NAAM)) {
//                backgroundColor = GEMEENTE2_KLEUR;
//            } else if (gemeente.equals(GEMEENTE3_NAAM)) {
//                backgroundColor = GEMEENTE3_KLEUR;
//            }
//        }
//
//        // Set the final background color once
//        row.setInt(android.R.id.text1, "setBackgroundColor", backgroundColor);
//
//        // Set up click intent (your original approach)
//        Intent fillInIntent = new Intent();
//        Bundle extras = new Bundle();
//        extras.putString(WinkerkReaderWidgetProvider.EXTRA_WORD, name);
//        fillInIntent.putExtras(extras);
//        row.setOnClickFillInIntent(android.R.id.text1, fillInIntent);
//
//        return row;
//    }

    /**
     * Check if item should be displayed based on widget settings
     */
    private boolean shouldDisplayItem(String reason) {
        if (TextUtils.isEmpty(reason)) return true;

        switch (reason) {
            case "Verjaar":
                return true; // Always show birthdays
            case "Doop":
                return WIDGET_DOOP;
            case "Huwelik":
                return WIDGET_HUWELIK;
            case "Belydenis":
                return WIDGET_BELYDENIS;
            case "Oorlede":
                return WIDGET_STERF;
            default:
                return true;
        }
    }

    @Override
    public RemoteViews getLoadingView() {
        return null; // Use default loading view
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public void onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged - refreshing data");

        dataLock.lock();
        try {
            // Reload settings in case they changed
            loadWidgetSettings();
            updateView();
        } catch (Exception e) {
            Log.e(TAG, "Error in onDataSetChanged", e);
        } finally {
            dataLock.unlock();
        }
    }

    /**
     * Enhanced version of your original updateView method
     */
    private void updateView() {
        Log.d(TAG, "Starting data update");

        // Clear existing data
        clearAllData();

        // Initialize/check database
        if (database == null || !database.isOpen()) {
            initializeDatabase();
            if (database == null) {
                addErrorItem("Database unavailable");
                return;
            }
        }

        try {
            // Build and execute your enhanced query
            String query = buildEnhancedQuery();
            Log.d(TAG, "Executing query");

            Cursor cursor = database.rawQuery(query, null);

            if (cursor != null) {
                processQueryResults(cursor);
                cursor.close();
                Log.d(TAG, "Query processed successfully, found " + records.size() + " items");
            } else {
                addErrorItem("No data available");
            }

        } catch (SQLiteException e) {
            Log.e(TAG, "Database query failed", e);
            addErrorItem("Database error");
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error loading data", e);
            addErrorItem("Error loading data");
        }
    }

    /**
     * Enhanced version of your original query with better error handling
     */
    private String buildEnhancedQuery() {
        DateTime today = DateTime.now();
        DateTime futureDate = today.plusDays(LOOK_AHEAD_DAYS);

        int currentDay = today.getDayOfMonth();
        int currentMonth = today.getMonthOfYear();
        int futureDay = futureDate.getDayOfMonth();
        int futureMonth = futureDate.getMonthOfYear();

        return "SELECT * FROM (" +
                // Birthdays
                "SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Verjaar' AS Rede, " +
                "substr(Members.Geboortedatum,1,2) AS Day, substr(Members.Geboortedatum,4,2) AS Month, " +
                "Members.Geboortedatum as Datum FROM Members " +
                "WHERE Members.Rekordstatus = \"0\" AND Members.Geboortedatum IS NOT NULL AND LENGTH(Members.Geboortedatum) >= 10 " +
                "AND ((CAST(substr(Geboortedatum, 1, 2) AS INTEGER) >= " + currentDay + " AND CAST(substr(Geboortedatum, 4, 2) AS INTEGER) = " + currentMonth + ") " +
                "OR (CAST(substr(Geboortedatum, 1, 2) AS INTEGER) <= " + futureDay + " AND CAST(substr(Geboortedatum, 4, 2) AS INTEGER) = " + futureMonth + ")) " +

                "UNION ALL " +

                // Baptisms
                "SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Doop' AS Rede, " +
                "substr(Members.[Doop date],1,2) AS Day, substr(Members.[Doop date],4,2) AS Month, " +
                "Members.[Doop date] as Datum FROM Members " +
                "WHERE Members.Rekordstatus = \"0\" AND Members.[Doop date] IS NOT NULL AND LENGTH(Members.[Doop date]) >= 10 " +
                "AND ((CAST(substr(Members.[Doop date], 1, 2) AS INTEGER) >= " + currentDay + " AND CAST(substr(Members.[Doop date], 4, 2) AS INTEGER) = " + currentMonth + ") " +
                "OR (CAST(substr(Members.[Doop date], 1, 2) AS INTEGER) <= " + futureDay + " AND CAST(substr(Members.[Doop date], 4, 2) AS INTEGER) = " + futureMonth + ")) " +

                "UNION ALL " +

                // Marriages
                "SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Huwelik' AS Rede, " +
                "substr(Members.[Huwelik date],1,2) AS Day, substr(Members.[Huwelik date],4,2) AS Month, " +
                "Members.[Huwelik date] as Datum FROM Members " +
                "WHERE Members.Rekordstatus = \"0\" AND Members.[Huwelik date] IS NOT NULL AND LENGTH(Members.[Huwelik date]) >= 10 " +
                "AND ((CAST(substr(Members.[Huwelik date], 1, 2) AS INTEGER) >= " + currentDay + " AND CAST(substr(Members.[Huwelik date], 4, 2) AS INTEGER) = " + currentMonth + ") " +
                "OR (CAST(substr(Members.[Huwelik date], 1, 2) AS INTEGER) <= " + futureDay + " AND CAST(substr(Members.[Huwelik date], 4, 2) AS INTEGER) = " + futureMonth + ")) " +

                "UNION ALL " +

                // Confessions
                "SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Belydenis' AS Rede, " +
                "substr(Members.[Belydenisaflegging Date],1,2) AS Day, substr(Members.[Belydenisaflegging Date],4,2) AS Month, " +
                "Members.[Belydenisaflegging Date] as Datum FROM Members " +
                "WHERE Members.[Belydenisaflegging Date] IS NOT NULL AND LENGTH(Members.[Belydenisaflegging Date]) >= 10 " +
                "AND ((CAST(substr(Members.[Belydenisaflegging Date], 1, 2) AS INTEGER) >= " + currentDay + " AND CAST(substr(Members.[Belydenisaflegging Date], 4, 2) AS INTEGER) = " + currentMonth + ") " +
                "OR (CAST(substr(Members.[Belydenisaflegging Date], 1, 2) AS INTEGER) <= " + futureDay + " AND CAST(substr(Members.[Belydenisaflegging Date], 4, 2) AS INTEGER) = " + futureMonth + ")) " +

                "UNION ALL " +

                // Deaths (with 2-year limit)
                "SELECT Argief.Name AS Noemnaam, Argief.Surname as Van, Argief.Gemeente, 'Oorlede' AS Rede, " +
                "substr(Argief.[DepartureDate],1,2) AS Day, substr(Argief.[DepartureDate],4,2) AS Month, " +
                "Argief.[DepartureDate] AS Datum FROM Argief " +
                "WHERE Argief.Reason = 'Oorlede' AND Argief.[DepartureDate] IS NOT NULL AND LENGTH(Argief.[DepartureDate]) >= 10 " +
                "AND ((CAST(SUBSTR(Argief.[DepartureDate], 1, 2) AS INTEGER) >= " + currentDay + " AND CAST(SUBSTR(Argief.[DepartureDate], 4, 2) AS INTEGER) = " + currentMonth + ") " +
                "OR (CAST(SUBSTR(Argief.[DepartureDate], 1, 2) AS INTEGER) <= " + futureDay + " AND CAST(SUBSTR(Argief.[DepartureDate], 4, 2) AS INTEGER) = " + futureMonth + ")) " +
                "AND (strftime('%Y', 'now') - CAST(SUBSTR(Argief.[DepartureDate], 7, 4) AS INTEGER)) <= 2 " +

                ") ORDER BY Month ASC, Day ASC, Van ASC, Noemnaam ASC";
    }

    /**
     * Enhanced version of your original query result processing
     */
    private void processQueryResults(Cursor cursor) {
        if (cursor == null || cursor.getCount() == 0) {
            addErrorItem("No upcoming events");
            return;
        }

        DateTime today = DateTime.now();
        cursor.moveToFirst();

        while (!cursor.isAfterLast()) {
            try {
                processCurrentRow(cursor, today);
            } catch (Exception e) {
                Log.e(TAG, "Error processing cursor row", e);
            }
            cursor.moveToNext();
        }

        // If no valid items were added, add a message
        if (records.isEmpty()) {
            addErrorItem("No events in date range");
        }
    }

    /**
     * FIXED: Process a single cursor row with filtering at data load time
     */
    private void processCurrentRow(Cursor cursor, DateTime today) {
        try {
            // Get column indices safely
            int firstNameIndex = cursor.getColumnIndex(LIDMATE_NOEMNAAM);
            int lastNameIndex = cursor.getColumnIndex(LIDMATE_VAN);
            int gemeenteIndex = cursor.getColumnIndex(LIDMATE_GEMEENTE);
            int reasonIndex = cursor.getColumnIndex("Rede");
            int dateIndex = cursor.getColumnIndex("Datum");

            // Validate indices
            if (firstNameIndex < 0 || reasonIndex < 0 || dateIndex < 0) {
                Log.w(TAG, "Missing required column indices in cursor");
                return;
            }

            // Extract data safely
            String firstName = cursor.getString(firstNameIndex);
            String lastName = lastNameIndex >= 0 ? cursor.getString(lastNameIndex) : "";
            String gemeente = gemeenteIndex >= 0 ? cursor.getString(gemeenteIndex) : "";
            String reason = cursor.getString(reasonIndex);
            String dateString = cursor.getString(dateIndex);

            // Validate required data
            if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(dateString) || dateString.length() < 10) {
                return;
            }

            // CRITICAL: Filter by widget settings HERE during data loading
            if (!shouldDisplayItem(reason)) {
                return; // Skip this item entirely - don't add to data structure
            }

            // Parse date safely
            DateTime eventDate;
            try {
                eventDate = parseDate(dateString.substring(0, 10));
                if (eventDate == null) {
                    Log.w(TAG, "Failed to parse date: " + dateString);
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing date: " + dateString, e);
                return;
            }

            // Calculate years since event
            String ageDisplay = "";
            try {
                Years yearsSince = Years.yearsBetween(eventDate, today);
                ageDisplay = getAgeDisplayText(reason, yearsSince.getYears());
            } catch (Exception e) {
                Log.w(TAG, "Error calculating age", e);
                ageDisplay = "(?)";
            }

            // Check if event should be shown (future dates only, like your original logic)
            MonthDay eventMonthDay = MonthDay.fromDateFields(eventDate.toDate());
            MonthDay todayMonthDay = MonthDay.fromDateFields(today.toDate());

            boolean shouldShow = eventMonthDay.isAfter(todayMonthDay) || eventMonthDay.isEqual(todayMonthDay);

            if (shouldShow) {
                // Build display name
                String fullName = (firstName + " " + lastName).trim();
                String displayText = fullName + " " + ageDisplay;

                // Add to your original data structure
                records.add(displayText);
                records2.add(dateString.substring(0, 2));  // day
                records3.add(dateString.substring(3, 5));  // month
                records4.add(reason);
                records5.add(gemeente != null ? gemeente : "");

                Log.d(TAG, "Added item: " + displayText + " (" + reason + ") - gemeente: " + gemeente);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing row", e);
        }
    }

    /**
     * Get age display text with emoji (your original logic enhanced)
     */
    private String getAgeDisplayText(String reason, int years) {
        switch (reason) {
            case "Verjaar":
                return "(" + years + " 🎂)";
            case "Doop":
                return "(" + years + " 💧)";
            case "Huwelik":
                return "(" + years + " 💍)";
            case "Belydenis":
                return "(" + years + " ⛪)";
            case "Oorlede":
                return "(" + years + " 🪦)";
            default:
                return "(" + years + ")";
        }
    }

    /**
     * Initialize database connection with proper error handling
     */
    private void initializeDatabase() {
        try {
            if (databaseHelper == null) {
                databaseHelper = new SQLiteAssetHelper(context, WINKERK_DB, null, 1);
            }

            if (database == null || !database.isOpen()) {
                database = databaseHelper.getReadableDatabase();
                Log.d(TAG, "Database connection established");
            }

        } catch (SQLiteException e) {
            Log.e(TAG, "Database initialization failed", e);
            database = null;
        }
    }

    /**
     * Close database connections properly
     */
    private void closeDatabase() {
        try {
            if (database != null && database.isOpen()) {
                database.close();
                database = null;
                Log.d(TAG, "Database connection closed");
            }

            if (databaseHelper != null) {
                databaseHelper.close();
                databaseHelper = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing database", e);
        }
    }

    /**
     * FIXED: Load widget settings from SharedPreferences with caching
     */
    private void loadWidgetSettings() {
        try {
            SharedPreferences settings = context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE);
            WIDGET_DOOP = settings.getBoolean("Widget_Doop", true);
            WIDGET_BELYDENIS = settings.getBoolean("Widget_Belydenis", true);
            WIDGET_HUWELIK = settings.getBoolean("Widget_Huwelik", true);
            WIDGET_STERF = settings.getBoolean("Widget_Sterwe", true);
            widgetSettingsLoaded = true;

            Log.d(TAG, "Widget settings loaded - Doop: " + WIDGET_DOOP +
                    ", Belydenis: " + WIDGET_BELYDENIS +
                    ", Huwelik: " + WIDGET_HUWELIK +
                    ", Sterf: " + WIDGET_STERF);
        } catch (Exception e) {
            Log.e(TAG, "Error loading widget settings", e);
            // Use defaults if loading fails
        }
    }

    /**
     * Clear all data arrays
     */
    private void clearAllData() {
        records.clear();
        records2.clear();
        records3.clear();
        records4.clear();
        records5.clear();
    }

    /**
     * Add an error item to display when things go wrong
     */
    private void addErrorItem(String message) {
        DateTime now = DateTime.now();
        records.add(message);
        records2.add(String.format("%02d", now.getDayOfMonth()));
        records3.add(String.format("%02d", now.getMonthOfYear()));
        records4.add("Error");
        records5.add("");

        Log.w(TAG, "Added error item: " + message);
    }

    /**
     * Create an error row view
     */
    private RemoteViews createErrorRow(String errorMessage) {
        RemoteViews errorRow = new RemoteViews(context.getPackageName(), R.layout.row);
        errorRow.setTextViewText(android.R.id.text1, "Error: " + errorMessage);
        errorRow.setInt(android.R.id.text1, "setBackgroundColor", Color.RED);
        return errorRow;
    }
}
//    private RemoteViews createErrorRow(String errorMessage) {
//        RemoteViews errorRow = new RemoteViews(context.getPackageName(), R.layout.row);
//        errorRow.setTextViewText(android.R.id.text1, errorMessage);
//        errorRow.setInt(android.R.id.text1, "setBackgroundColor", Color.WHITE);
//        return errorRow;
//    }
//}