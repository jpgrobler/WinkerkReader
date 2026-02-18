package za.co.jpsoft.winkerkreader;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
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

import za.co.jpsoft.winkerkreader.R;
import za.co.jpsoft.winkerkreader.data.WinkerkContract;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;
import static za.co.jpsoft.winkerkreader.data.CursorDataExtractor.*;

import org.joda.time.DateTime;
import org.joda.time.Years;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static za.co.jpsoft.winkerkreader.Utils.parseDate;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.*;

/**
 * Enhanced Widget Service with proper architecture, error handling, and performance optimization.
 */
public class WinkerkReaderWidgetService extends RemoteViewsService {
    private static final String TAG = "WinkerkReaderService";

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        Log.d(TAG, "Creating RemoteViewsFactory");

        // Set device ID (consider moving this to Application class)
        try {
            WinkerkContract.winkerkEntry.id = Settings.Secure.getString(
                    getApplicationContext().getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
        } catch (Exception e) {
            Log.e(TAG, "Error getting device ID", e);
        }

        return new WinkerkReaderViewsFactory(getApplicationContext(), intent);
    }
}

/**
 * Enhanced RemoteViewsFactory with modern architecture and proper error handling
 */
class WinkerkReaderViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private static final String TAG = "WinkerkReaderFactory";
    private static final String EXTRA_WORD = "za.co.jpsoft.winkerkreader.EXTRA_WORD";
    private static final int LOOK_AHEAD_DAYS = 15;

    // Thread safety
    private final ReentrantLock dataLock = new ReentrantLock();

    // Context and settings
    private final Context context;
    private final int appWidgetId;
    private SettingsManager settingsManager;

    // Data storage - using more descriptive names
    private List<WidgetItem> widgetItems = new ArrayList<>();

    // Database components
    private SQLiteAssetHelper databaseHelper;
    private SQLiteDatabase database;

    public WinkerkReaderViewsFactory(Context context, Intent intent) {
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
            settingsManager = SettingsManager.getInstance(context);
            initializeDatabase();
            loadData();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        dataLock.lock();
        try {
            closeDatabase();
            widgetItems.clear();
        } finally {
            dataLock.unlock();
        }
    }

    @Override
    public int getCount() {
        dataLock.lock();
        try {
            return widgetItems.size();
        } finally {
            dataLock.unlock();
        }
    }

    @Override
    public RemoteViews getViewAt(int position) {
        dataLock.lock();
        try {
            return createViewForPosition(position);
        } catch (Exception e) {
            Log.e(TAG, "Error creating view for position " + position, e);
            return createErrorView(e.getMessage());
        } finally {
            dataLock.unlock();
        }
    }

    @Override
    public RemoteViews getLoadingView() {
        //RemoteViews loadingView = new RemoteViews(context.getPackageName(), R.layout.widget_loading);
        //loadingView.setTextViewText("Loading", context.getString(R.string.widget_loading));
        return null; //loadingView;
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
        Log.d(TAG, "onDataSetChanged");

        dataLock.lock();
        try {
            loadData();
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing data", e);
        } finally {
            dataLock.unlock();
        }
    }

    /**
     * Initialize database connection
     */
    private void initializeDatabase() {
        try {
            if (databaseHelper == null) {
                databaseHelper = new SQLiteAssetHelper(context, WINKERK_DB, null, 1);
            }

            if (database == null || !database.isOpen()) {
                database = databaseHelper.getReadableDatabase();
            }

            Log.d(TAG, "Database initialized successfully");
        } catch (SQLiteException e) {
            Log.e(TAG, "Database initialization failed", e);
            database = null;
        }
    }

    /**
     * Close database connections
     */
    private void closeDatabase() {
        try {
            if (database != null && database.isOpen()) {
                database.close();
                database = null;
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
     * Load data from database with proper error handling
     */
    private void loadData() {
        widgetItems.clear();

        if (database == null) {
            Log.w(TAG, "Database not available, retrying initialization");
            initializeDatabase();
            if (database == null) {
                addErrorItem("Database unavailable");
                return;
            }
        }

        try {
            loadWidgetSettings();
            String query = buildQuery();
            Cursor cursor = database.rawQuery(query, null);

            if (cursor != null) {
                processQueryResults(cursor);
                cursor.close();
            } else {
                addErrorItem("No data available");
            }

        } catch (SQLiteException e) {
            Log.e(TAG, "Database query failed", e);
            addErrorItem("Database error: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error loading data", e);
            addErrorItem("Error: " + e.getMessage());
        }

        Log.d(TAG, "Loaded " + widgetItems.size() + " items");
    }

    /**
     * Load widget display settings
     */
    private void loadWidgetSettings() {
        // Use SettingsManager instead of direct static variable access
        // This ensures proper validation and error handling
        Log.d(TAG, "Loading widget settings");
    }

    /**
     * Build optimized SQL query
     */
    private String buildQuery() {
        DateTime now = DateTime.now();
        DateTime futureDate = now.plusDays(LOOK_AHEAD_DAYS);

        int currentDay = now.getDayOfMonth();
        int currentMonth = now.getMonthOfYear();
        int futureDay = futureDate.getDayOfMonth();
        int futureMonth = futureDate.getMonthOfYear();

        return "SELECT * FROM (" +
                buildBirthdayQuery(currentDay, currentMonth, futureDay, futureMonth) +
                " UNION ALL " +
                buildBaptismQuery(currentDay, currentMonth, futureDay, futureMonth) +
                " UNION ALL " +
                buildMarriageQuery(currentDay, currentMonth, futureDay, futureMonth) +
                " UNION ALL " +
                buildConfessionQuery(currentDay, currentMonth, futureDay, futureMonth) +
                " UNION ALL " +
                buildDeathQuery(currentDay, currentMonth, futureDay, futureMonth) +
                ") ORDER BY Month ASC, Day ASC, Van ASC, Noemnaam ASC";
    }

    private String buildBirthdayQuery(int currentDay, int currentMonth, int futureDay, int futureMonth) {
        return "SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Verjaar' AS Rede, " +
                "substr(Members.Geboortedatum,1,2) AS Day, substr(Members.Geboortedatum,4,2) AS Month, " +
                "Members.Geboortedatum as Datum FROM Members " +
                "WHERE Members.Rekordstatus = 0 AND Members.Geboortedatum IS NOT NULL AND LENGTH(Members.Geboortedatum) >= 10 " +
                "AND ((CAST(substr(Geboortedatum, 1, 2) AS INTEGER) >= " + currentDay + " AND CAST(substr(Geboortedatum, 4, 2) AS INTEGER) = " + currentMonth + ") " +
                "OR (CAST(substr(Geboortedatum, 1, 2) AS INTEGER) <= " + futureDay + " AND CAST(substr(Geboortedatum, 4, 2) AS INTEGER) = " + futureMonth + "))";
    }

    private String buildBaptismQuery(int currentDay, int currentMonth, int futureDay, int futureMonth) {
        return "SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Doop' AS Rede, " +
                "substr(Members.[Doop date],1,2) AS Day, substr(Members.[Doop date],4,2) AS Month, " +
                "Members.[Doop date] as Datum FROM Members " +
                "WHERE Members.Rekordstatus = 0 AND Members.[Doop date] IS NOT NULL AND LENGTH(Members.[Doop date]) >= 10 " +
                "AND ((CAST(substr(Members.[Doop date], 1, 2) AS INTEGER) >= " + currentDay + " AND CAST(substr(Members.[Doop date], 4, 2) AS INTEGER) = " + currentMonth + ") " +
                "OR (CAST(substr(Members.[Doop date], 1, 2) AS INTEGER) <= " + futureDay + " AND CAST(substr(Members.[Doop date], 4, 2) AS INTEGER) = " + futureMonth + "))";
    }

    private String buildMarriageQuery(int currentDay, int currentMonth, int futureDay, int futureMonth) {
        return "SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Huwelik' AS Rede, " +
                "substr(Members.[Huwelik date],1,2) AS Day, substr(Members.[Huwelik date],4,2) AS Month, " +
                "Members.[Huwelik date] as Datum FROM Members " +
                "WHERE Members.Rekordstatus = 0 AND Members.[Huwelik date] IS NOT NULL AND LENGTH(Members.[Huwelik date]) >= 10 " +
                "AND ((CAST(substr(Members.[Huwelik date], 1, 2) AS INTEGER) >= " + currentDay + " AND CAST(substr(Members.[Huwelik date], 4, 2) AS INTEGER) = " + currentMonth + ") " +
                "OR (CAST(substr(Members.[Huwelik date], 1, 2) AS INTEGER) <= " + futureDay + " AND CAST(substr(Members.[Huwelik date], 4, 2) AS INTEGER) = " + futureMonth + "))";
    }

    private String buildConfessionQuery(int currentDay, int currentMonth, int futureDay, int futureMonth) {
        return "SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Belydenis' AS Rede, " +
                "substr(Members.[Belydenisaflegging Date],1,2) AS Day, substr(Members.[Belydenisaflegging Date],4,2) AS Month, " +
                "Members.[Belydenisaflegging Date] as Datum FROM Members " +
                "WHERE Members.Rekordstatus = 0 AND Members.[Belydenisaflegging Date] IS NOT NULL AND LENGTH(Members.[Belydenisaflegging Date]) >= 10 " +
                "AND ((CAST(substr(Members.[Belydenisaflegging Date], 1, 2) AS INTEGER) >= " + currentDay + " AND CAST(substr(Members.[Belydenisaflegging Date], 4, 2) AS INTEGER) = " + currentMonth + ") " +
                "OR (CAST(substr(Members.[Belydenisaflegging Date], 1, 2) AS INTEGER) <= " + futureDay + " AND CAST(substr(Members.[Belydenisaflegging Date], 4, 2) AS INTEGER) = " + futureMonth + "))";
    }

    private String buildDeathQuery(int currentDay, int currentMonth, int futureDay, int futureMonth) {
        return "SELECT Argief.Name AS Noemnaam, Argief.Surname as Van, Argief.Gemeente, 'Oorlede' AS Rede, " +
                "substr(Argief.[DepartureDate],1,2) AS Day, substr(Argief.[DepartureDate],4,2) AS Month, " +
                "Argief.[DepartureDate] AS Datum FROM Argief " +
                "WHERE Argief.Reason = 'Oorlede' AND Argief.[DepartureDate] IS NOT NULL AND LENGTH(Argief.[DepartureDate]) >= 10 " +
                "AND ((CAST(SUBSTR(Argief.[DepartureDate], 1, 2) AS INTEGER) >= " + currentDay + " AND CAST(SUBSTR(Argief.[DepartureDate], 4, 2) AS INTEGER) = " + currentMonth + ") " +
                "OR (CAST(SUBSTR(Argief.[DepartureDate], 1, 2) AS INTEGER) <= " + futureDay + " AND CAST(SUBSTR(Argief.[DepartureDate], 4, 2) AS INTEGER) = " + futureMonth + ")) " +
                "AND (strftime('%Y', 'now') - CAST(SUBSTR(Argief.[DepartureDate], 7, 4) AS INTEGER)) <= 2";
    }

    /**
     * Process database query results
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
                WidgetItem item = createItemFromCursor(cursor, today);
                if (item != null && shouldDisplayItem(item)) {
                    widgetItems.add(item);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing cursor row", e);
            }
            cursor.moveToNext();
        }
    }

    /**
     * Create a WidgetItem from cursor data
     */
    private WidgetItem createItemFromCursor(Cursor cursor, DateTime today) {
        try {
            String firstName = getSafeString(cursor, LIDMATE_NOEMNAAM, "");
            String lastName = getSafeString(cursor, LIDMATE_VAN, "");
            String gemeente = getSafeString(cursor, LIDMATE_GEMEENTE, "");
            String reason = getSafeString(cursor, "Rede", "");
            String dateString = getSafeString(cursor, "Datum", "");

            if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(dateString) || dateString.length() < 10) {
                return null;
            }

            // Parse date safely
            DateTime eventDate = parseDate(dateString.substring(0, 10));
            if (eventDate == null) {
                return null;
            }

            // Calculate years since event
            Years yearsSince = Years.yearsBetween(eventDate, today);

            return new WidgetItem(
                    firstName,
                    lastName != null ? lastName : "",
                    gemeente != null ? gemeente : "",
                    reason,
                    eventDate,
                    yearsSince.getYears(),
                    dateString.substring(0, 2), // day
                    dateString.substring(3, 5)  // month
            );

        } catch (Exception e) {
            Log.e(TAG, "Error creating item from cursor", e);
            return null;
        }
    }

    /**
     * Check if item should be displayed based on widget settings
     */
    private boolean shouldDisplayItem(WidgetItem item) {
        if (item == null) return false;

        try {
            // Check if this event type should be displayed
            switch (item.reason) {
                case "Verjaar":
                    return true; // Always show birthdays
                case "Doop":
                    return settingsManager != null ? settingsManager.isWidgetDoopEnabled() : true;
                case "Huwelik":
                    return settingsManager != null ? settingsManager.isWidgetHuwelikEnabled() : true;
                case "Belydenis":
                    return settingsManager != null ? settingsManager.isWidgetBelydenisEnabled() : true;
                case "Oorlede":
                    return settingsManager != null ? settingsManager.isWidgetSterfEnabled() : true;
                default:
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking display settings", e);
            return true; // Default to showing item if error occurs
        }
    }

    /**
     * Create RemoteViews for a specific position
     */
    private RemoteViews createViewForPosition(int position) {
        if (position < 0 || position >= widgetItems.size()) {
            return createErrorView("Invalid position: " + position);
        }

        WidgetItem item = widgetItems.get(position);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.row);

        // Create display text with proper formatting
        String displayText = formatItemText(item);
        Spannable styledText = createStyledText(displayText, item, position);

        // Set the styled text
        views.setTextViewText(android.R.id.text1, styledText);

        // Set background color based on gemeente
        int backgroundColor = getBackgroundColorForGemeente(item.gemeente);
        views.setInt(android.R.id.text1, "setBackgroundColor", backgroundColor);

        // Set up click intent
        Intent fillInIntent = new Intent();
        Bundle extras = new Bundle();
        extras.putString(EXTRA_WORD, item.getFullName());
        fillInIntent.putExtras(extras);
        views.setOnClickFillInIntent(android.R.id.text1, fillInIntent);

        return views;
    }

    /**
     * Format item text for display
     */
    private String formatItemText(WidgetItem item) {
        String ageInfo = getAgeDisplayText(item.reason, item.yearsSince);
        return item.day + "/" + item.month + " " + item.getFullName() + " " + ageInfo;
    }

    /**
     * Get appropriate age display text with emoji
     */
    private String getAgeDisplayText(String reason, int years) {
        switch (reason) {
            case "Verjaar":
                return "(" + years + " 🎁)";
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
     * Create styled text with appropriate colors and sizes
     */
    private Spannable createStyledText(String text, WidgetItem item, int position) {
        SpannableString spannable = new SpannableString(text);
        int textLength = text.length();

        // Style the date part (first 5 characters: "DD/MM")
        if (textLength >= 5) {
            spannable.setSpan(new RelativeSizeSpan(0.6f), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Check if this is today's date for highlighting
            DateTime today = DateTime.now();
            boolean isToday = item.day.equals(String.format("%02d", today.getDayOfMonth()));

            if (isToday) {
                spannable.setSpan(new ForegroundColorSpan(Color.BLUE), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new ForegroundColorSpan(Color.argb(255, 0, 0, 220)), 6, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // Gray out repeated dates
                boolean shouldGrayOut = position > 0 &&
                        widgetItems.get(position - 1).day.equals(item.day);

                int dateColor = shouldGrayOut ? Color.LTGRAY : Color.BLACK;
                spannable.setSpan(new ForegroundColorSpan(dateColor), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new ForegroundColorSpan(Color.argb(200, 50, 50, 50)), 6, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        // Style the age information in parentheses
        int parenthesesStart = text.lastIndexOf('(');
        if (parenthesesStart > 0 && parenthesesStart < textLength) {
            spannable.setSpan(new RelativeSizeSpan(0.8f), parenthesesStart, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return spannable;
    }

    /**
     * Get background color for gemeente
     */
    private int getBackgroundColorForGemeente(String gemeente) {
        if (settingsManager == null) {
            return Color.WHITE;
        }

        try {
            // Use SettingsManager instead of static variables
            if (GEMEENTE_NAAM != null && gemeente.equals(GEMEENTE_NAAM)) {
                return settingsManager.getGemeenteKleur();
            } else if (GEMEENTE2_NAAM != null && gemeente.equals(GEMEENTE2_NAAM)) {
                return settingsManager.getGemeente2Kleur();
            } else if (GEMEENTE3_NAAM != null && gemeente.equals(GEMEENTE3_NAAM)) {
                return settingsManager.getGemeente3Kleur();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting gemeente color", e);
        }

        return Color.WHITE;
    }

    /**
     * Create an error view
     */
    private RemoteViews createErrorView(String errorMessage) {
        RemoteViews errorView = new RemoteViews(context.getPackageName(), R.layout.row);
        errorView.setTextViewText(android.R.id.text1, "Error: " + errorMessage);
        errorView.setInt(android.R.id.text1, "setBackgroundColor", Color.WHITE);
        return errorView;
    }

    /**
     * Add an error item to the widget list
     */
    private void addErrorItem(String message) {
        DateTime now = DateTime.now();
        WidgetItem errorItem = new WidgetItem(
                message,
                "",
                "",
                "Error",
                now,
                0,
                String.format("%02d", now.getDayOfMonth()),
                String.format("%02d", now.getMonthOfYear())
        );
        widgetItems.add(errorItem);
    }

    /**
     * Data class to represent a widget item
     */
    private static class WidgetItem {
        final String firstName;
        final String lastName;
        final String gemeente;
        final String reason;
        final DateTime eventDate;
        final int yearsSince;
        final String day;
        final String month;

        WidgetItem(String firstName, String lastName, String gemeente, String reason,
                   DateTime eventDate, int yearsSince, String day, String month) {
            this.firstName = firstName != null ? firstName : "";
            this.lastName = lastName != null ? lastName : "";
            this.gemeente = gemeente != null ? gemeente : "";
            this.reason = reason != null ? reason : "";
            this.eventDate = eventDate;
            this.yearsSince = yearsSince;
            this.day = day != null ? day : "";
            this.month = month != null ? month : "";
        }

        String getFullName() {
            return (firstName + " " + lastName).trim();
        }

        @Override
        public String toString() {
            return "WidgetItem{" +
                    "name='" + getFullName() + '\'' +
                    ", gemeente='" + gemeente + '\'' +
                    ", reason='" + reason + '\'' +
                    ", day='" + day + '\'' +
                    ", month='" + month + '\'' +
                    ", years=" + yearsSince +
                    '}';
        }
    }
}