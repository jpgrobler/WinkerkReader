package za.co.jpsoft.winkerkreader.services

import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.widget.WinkerkReaderWidgetProvider
import za.co.jpsoft.winkerkreader.utils.Utils.parseDate
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.ui.activities.VerjaarSmsActivity
import za.co.jpsoft.winkerkreader.widget.WidgetQueryBuilder

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper
import za.co.jpsoft.winkerkreader.data.WinkerkDbHelper
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Consolidated widget service with improved error handling, thread safety, and performance.
 * This service powers the home screen widget that displays upcoming events (birthdays,
 * baptisms, weddings, confessions, and recent deaths).
 */
class ListViewWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        Log.d(TAG, "Creating RemoteViewsFactory")

        // Device ID is no longer stored in global AppSessionState singleton
        // RemoteViewsFactory can obtain it via DeviceIdManager if needed.

        return WidgetViewsFactory(applicationContext, intent)
    }

    companion object {
        private const val TAG = "ListViewWidgetService"
    }
}

/**
 * Factory that builds the list of widget items.
 */
class WidgetViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val tag = "WidgetViewsFactory"
    private val dataLock = ReentrantLock()

    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    // Data lists (keeping the original structure)
    private val records = mutableListOf<String>()  // display text (name + age)
    private val records2 = mutableListOf<String>() // day
    private val records3 = mutableListOf<String>() // month
    private val records4 = mutableListOf<String>() // reason
    private val records5 = mutableListOf<String>() // gemeente

    // Database components
    private var databaseHelper: SQLiteAssetHelper? = null
    private var database: SQLiteDatabase? = null

    // Widget settings (cached)
    private var widgetDoop = true
    private var widgetBelydenis = true
    private var widgetHuwelik = true
    private var widgetSterf = true

    init {
        Log.d(tag, "Created factory for widget $appWidgetId")
    }

    override fun onCreate() {
        Log.d(tag, "onCreate")
        try {
            initializeDatabase()
            loadWidgetSettings()
            updateView()
        } catch (e: Exception) {
            Log.e(tag, "Error in onCreate", e)
            addErrorItem("Failed to initialize")
        }
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
        dataLock.withLock {
            closeDatabase()
            clearAllData()
        }
    }

    override fun getCount(): Int = dataLock.withLock { records.size }

    override fun getViewAt(position: Int): RemoteViews = dataLock.withLock {
        try {
            if (position !in records.indices) {
                return createErrorRow("Invalid position")
            }
            createViewForPosition(position)
        } catch (e: Exception) {
            Log.e(tag, "Error creating view for position $position", e)
            createErrorRow("Error: ${e.message}")
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun onDataSetChanged() {
        Log.d(tag, "onDataSetChanged - refreshing data")
        dataLock.withLock {
            try {
                loadWidgetSettings()
                updateView()
            } catch (e: Exception) {
                Log.e(tag, "Error in onDataSetChanged", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun initializeDatabase() {
        try {
            if (databaseHelper == null) {
                databaseHelper = WinkerkDbHelper.getInstance(context, WinkerkContract.winkerkEntry.WINKERK_DB)
            }
            if (database == null || database?.isOpen != true) {
                database = databaseHelper?.readableDatabase
                Log.d(tag, "Database connection established")
            }
        } catch (e: SQLiteException) {
            Log.e(tag, "Database initialization failed", e)
            database = null
        }
    }

    private fun closeDatabase() {
        try {
            database?.close()
            database = null
            databaseHelper?.close()
            databaseHelper = null
            Log.d(tag, "Database connection closed")
        } catch (e: Exception) {
            Log.e(tag, "Error closing database", e)
        }
    }

    private fun loadWidgetSettings() {
        try {
            val settings = context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
            widgetDoop = settings.getBoolean("Widget_Doop", true)
            widgetBelydenis = settings.getBoolean("Widget_Belydenis", true)
            widgetHuwelik = settings.getBoolean("Widget_Huwelik", true)
            widgetSterf = settings.getBoolean("Widget_Sterwe", true)
            Log.d(tag, "Widget settings loaded - Doop: $widgetDoop, Belydenis: $widgetBelydenis, Huwelik: $widgetHuwelik, Sterf: $widgetSterf")
        } catch (e: Exception) {
            Log.e(tag, "Error loading widget settings", e)
        }
    }

    private fun updateView() {
        Log.d(tag, "Starting data update")
        clearAllData()

        if (database == null || database?.isOpen != true) {
            initializeDatabase()
            if (database == null) {
                addErrorItem("Database unavailable")
                return
            }
        }

        try {
            val query = buildCombinedQuery()
            Log.d(tag, "Executing query")

            database?.rawQuery(query, null)?.use { cursor ->
                processQueryResults(cursor)
                Log.d(tag, "Query processed successfully, found ${records.size} items")
            } ?: addErrorItem("No data available")

        } catch (e: SQLiteException) {
            Log.e(tag, "Database query failed", e)
            addErrorItem("Database error")
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error loading data", e)
            addErrorItem("Error loading data")
        }
    }

    private fun buildCombinedQuery(): String {
        return WidgetQueryBuilder.buildCombinedQuery()
    }

//    private fun buildCombinedQuery(): String {
//        val today = LocalDate.now()
//        val futureDate = today.plusDays(LOOK_AHEAD_DAYS)
//
//        val currentDay = today.dayOfMonth
//        val currentMonth = today.monthOfYear
//        val futureDay = futureDate.dayOfMonth
//        val futureMonth = futureDate.monthOfYear
//
//        return """
//            SELECT * FROM (
//                ${buildBirthdayQuery(currentDay, currentMonth, futureDay, futureMonth)}
//                UNION ALL
//                ${buildBaptismQuery(currentDay, currentMonth, futureDay, futureMonth)}
//                UNION ALL
//                ${buildMarriageQuery(currentDay, currentMonth, futureDay, futureMonth)}
//                UNION ALL
//                ${buildConfessionQuery(currentDay, currentMonth, futureDay, futureMonth)}
//                UNION ALL
//                ${buildDeathQuery(currentDay, currentMonth, futureDay, futureMonth)}
//            ) ORDER BY Month ASC, Day ASC, Van ASC, Noemnaam ASC
//        """.trimIndent()
//    }

//    private fun buildBirthdayQuery(
//        currentDay: Int,
//        currentMonth: Int,
//        futureDay: Int,
//        futureMonth: Int
//    ): String = """
//        SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Verjaar' AS Rede,
//               substr(Members.Geboortedatum,1,2) AS Day, substr(Members.Geboortedatum,4,2) AS Month,
//               Members.Geboortedatum as Datum FROM Members
//        WHERE Members.Rekordstatus = "0" AND Members.Geboortedatum IS NOT NULL AND LENGTH(Members.Geboortedatum) >= 10
//          AND ((CAST(substr(Geboortedatum, 1, 2) AS INTEGER) >= $currentDay AND CAST(substr(Geboortedatum, 4, 2) AS INTEGER) = $currentMonth)
//            OR (CAST(substr(Geboortedatum, 1, 2) AS INTEGER) <= $futureDay AND CAST(substr(Geboortedatum, 4, 2) AS INTEGER) = $futureMonth))
//    """.trimIndent()
//
//    private fun buildBaptismQuery(
//        currentDay: Int,
//        currentMonth: Int,
//        futureDay: Int,
//        futureMonth: Int
//    ): String = """
//        SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Doop' AS Rede,
//               substr(Members.[Doop date],1,2) AS Day, substr(Members.[Doop date],4,2) AS Month,
//               Members.[Doop date] as Datum FROM Members
//        WHERE Members.Rekordstatus = "0" AND Members.[Doop date] IS NOT NULL AND LENGTH(Members.[Doop date]) >= 10
//          AND ((CAST(substr(Members.[Doop date], 1, 2) AS INTEGER) >= $currentDay AND CAST(substr(Members.[Doop date], 4, 2) AS INTEGER) = $currentMonth)
//            OR (CAST(substr(Members.[Doop date], 1, 2) AS INTEGER) <= $futureDay AND CAST(substr(Members.[Doop date], 4, 2) AS INTEGER) = $futureMonth))
//    """.trimIndent()
//
//    private fun buildMarriageQuery(
//        currentDay: Int,
//        currentMonth: Int,
//        futureDay: Int,
//        futureMonth: Int
//    ): String = """
//        SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Huwelik' AS Rede,
//               substr(Members.[Huwelik date],1,2) AS Day, substr(Members.[Huwelik date],4,2) AS Month,
//               Members.[Huwelik date] as Datum FROM Members
//        WHERE Members.Rekordstatus = "0" AND Members.[Huwelik date] IS NOT NULL AND LENGTH(Members.[Huwelik date]) >= 10
//          AND ((CAST(substr(Members.[Huwelik date], 1, 2) AS INTEGER) >= $currentDay AND CAST(substr(Members.[Huwelik date], 4, 2) AS INTEGER) = $currentMonth)
//            OR (CAST(substr(Members.[Huwelik date], 1, 2) AS INTEGER) <= $futureDay AND CAST(substr(Members.[Huwelik date], 4, 2) AS INTEGER) = $futureMonth))
//    """.trimIndent()
//
//    private fun buildConfessionQuery(
//        currentDay: Int,
//        currentMonth: Int,
//        futureDay: Int,
//        futureMonth: Int
//    ): String = """
//        SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Belydenis' AS Rede,
//               substr(Members.[Belydenisaflegging Date],1,2) AS Day, substr(Members.[Belydenisaflegging Date],4,2) AS Month,
//               Members.[Belydenisaflegging Date] as Datum FROM Members
//        WHERE Members.[Belydenisaflegging Date] IS NOT NULL AND LENGTH(Members.[Belydenisaflegging Date]) >= 10
//          AND ((CAST(substr(Members.[Belydenisaflegging Date], 1, 2) AS INTEGER) >= $currentDay AND CAST(substr(Members.[Belydenisaflegging Date], 4, 2) AS INTEGER) = $currentMonth)
//            OR (CAST(substr(Members.[Belydenisaflegging Date], 1, 2) AS INTEGER) <= $futureDay AND CAST(substr(Members.[Belydenisaflegging Date], 4, 2) AS INTEGER) = $futureMonth))
//    """.trimIndent()
//
//    private fun buildDeathQuery(
//        currentDay: Int,
//        currentMonth: Int,
//        futureDay: Int,
//        futureMonth: Int
//    ): String = """
//        SELECT Argief.Name AS Noemnaam, Argief.Surname as Van, Argief.Gemeente, 'Oorlede' AS Rede,
//               substr(Argief.[DepartureDate],1,2) AS Day, substr(Argief.[DepartureDate],4,2) AS Month,
//               Argief.[DepartureDate] AS Datum FROM Argief
//        WHERE Argief.Reason = 'Oorlede' AND Argief.[DepartureDate] IS NOT NULL AND LENGTH(Argief.[DepartureDate]) >= 10
//          AND ((CAST(SUBSTR(Argief.[DepartureDate], 1, 2) AS INTEGER) >= $currentDay AND CAST(SUBSTR(Argief.[DepartureDate], 4, 2) AS INTEGER) = $currentMonth)
//            OR (CAST(SUBSTR(Argief.[DepartureDate], 1, 2) AS INTEGER) <= $futureDay AND CAST(SUBSTR(Argief.[DepartureDate], 4, 2) AS INTEGER) = $futureMonth))
//          AND (strftime('%Y', 'now') - CAST(SUBSTR(Argief.[DepartureDate], 7, 4) AS INTEGER)) <= 2
//    """.trimIndent()

    private fun processQueryResults(cursor: Cursor) {
        if (cursor.count == 0) {
            addErrorItem("No upcoming events")
            return
        }

        val today = LocalDate.now()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            try {
                processCurrentRow(cursor, today)
            } catch (e: Exception) {
                Log.e(tag, "Error processing cursor row", e)
            }
            cursor.moveToNext()
        }

        if (records.isEmpty()) {
            addErrorItem("No events in date range")
        }
    }

    private fun processCurrentRow(cursor: Cursor, today: LocalDate) {
        val firstNameIdx = cursor.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)
        val lastNameIdx = cursor.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_VAN)
        val gemeenteIdx = cursor.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_GEMEENTE)
        val reasonIdx = cursor.getColumnIndex("Rede")
        val dateIdx = cursor.getColumnIndex("Datum")

        if (firstNameIdx < 0 || reasonIdx < 0 || dateIdx < 0) {
            Log.w(tag, "Missing required column indices in cursor")
            return
        }

        val firstName = cursor.getString(firstNameIdx)
        val lastName = if (lastNameIdx >= 0) cursor.getString(lastNameIdx) else ""
        val gemeente = if (gemeenteIdx >= 0) cursor.getString(gemeenteIdx) else ""
        val reason = cursor.getString(reasonIdx)
        val dateString = cursor.getString(dateIdx)

        if (firstName.isNullOrEmpty() || dateString.isNullOrEmpty() || dateString.length < 10) {
            return
        }

        // Filter by widget settings
        if (!shouldDisplayItem(reason)) {
            return
        }

        val eventDate = try {
            parseDate(dateString.substring(0, 10)) ?: return
        } catch (e: Exception) {
            Log.w(tag, "Error parsing date: $dateString", e)
            return
        }

        val ageDisplay = try {
            val years = ChronoUnit.YEARS.between(eventDate, today).toInt()
            getAgeDisplayText(reason, years)
        } catch (e: Exception) {
            Log.w(tag, "Error calculating age", e)
            "(?)"
        }

        val eventMonthDay = MonthDay.of(eventDate.monthValue, eventDate.dayOfMonth)
        val todayMonthDay = MonthDay.from(today)
        val shouldShow = eventMonthDay.isAfter(todayMonthDay) || eventMonthDay == todayMonthDay

        if (shouldShow) {
            val fullName = "$firstName $lastName".trim()
            val displayText = "$fullName $ageDisplay"

            records.add(displayText)
            records2.add(dateString.substring(0, 2))
            records3.add(dateString.substring(3, 5))
            records4.add(reason)
            records5.add(gemeente ?: "")
        }
    }

    private fun shouldDisplayItem(reason: String?): Boolean {
        if (reason.isNullOrEmpty()) return true
        return when (reason) {
            "Verjaar" -> true
            "Doop" -> widgetDoop
            "Huwelik" -> widgetHuwelik
            "Belydenis" -> widgetBelydenis
            "Oorlede" -> widgetSterf
            else -> true
        }
    }

    private fun getAgeDisplayText(reason: String, years: Int): String = when (reason) {
        "Verjaar" -> "($years 🎂)"
        "Doop" -> "($years 💧)"
        "Huwelik" -> "($years 💍)"
        "Belydenis" -> "($years ⛪)"
        "Oorlede" -> "($years 🪦)"
        else -> "($years)"
    }

    private fun createViewForPosition(position: Int): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.row)

        val today = LocalDate.now()
        val name = records[position]
        val day = records2[position]
        val month = records3[position]
        val gemeente = records5[position]

        // Determine background color based on gemeente
        var backgroundColor = Color.WHITE
        if (gemeente.isNotEmpty()) {
            val settings = SettingsManager.getInstance(context)
            when (gemeente) {
                settings.gemeenteNaam -> backgroundColor = settings.gemeenteKleur
                settings.gemeente2Naam -> backgroundColor = settings.gemeente2Kleur
                settings.gemeente3Naam -> backgroundColor = settings.gemeente3Kleur
            }
        }
        row.setInt(android.R.id.text1, "setBackgroundColor", backgroundColor)

        val displayText = "$day/$month $name"
        val spannable = SpannableString(displayText)
        val textLength = spannable.length

        // Date formatting (first 5 chars: "dd/mm")
        spannable.setSpan(RelativeSizeSpan(0.6f), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Gray out repeated dates
        if (position > 0 && day == records2[position - 1]) {
            spannable.setSpan(ForegroundColorSpan(Color.LTGRAY), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            spannable.setSpan(ForegroundColorSpan(Color.BLACK), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Main text color (dark gray)
        spannable.setSpan(ForegroundColorSpan(Color.argb(200, 50, 50, 50)), 6, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Highlight today's date
        if (day == today.toString().substring(8, 10)) {
            spannable.setSpan(ForegroundColorSpan(Color.BLUE), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.argb(255, 0, 0, 220)), 6, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Parentheses formatting (age part)
        val startPos = textLength - 7
        if (startPos > -1) {
            val parenthesesPos = spannable.toString().indexOf('(', startPos)
            if (parenthesesPos > 0) {
                spannable.setSpan(RelativeSizeSpan(0.8f), parenthesesPos, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        row.setTextViewText(android.R.id.text1, spannable)

        // Set up click intent (opens VerjaarSmsActivity when item is tapped)
        val fillInIntent = Intent().apply {
            putExtra(WinkerkReaderWidgetProvider.EXTRA_WORD, name)
        }
        row.setOnClickFillInIntent(android.R.id.text1, fillInIntent)

        return row
    }

    private fun clearAllData() {
        records.clear()
        records2.clear()
        records3.clear()
        records4.clear()
        records5.clear()
    }

    private fun addErrorItem(message: String) {
        val now = LocalDate.now()
        records.add(message)
        records2.add(String.format("%02d", now.dayOfMonth))
        records3.add(String.format("%02d", now.monthValue))
        records4.add("Error")
        records5.add("")
        Log.w(tag, "Added error item: $message")
    }

    private fun createErrorRow(errorMessage: String): RemoteViews {
        return RemoteViews(context.packageName, R.layout.row).apply {
            setTextViewText(android.R.id.text1, "Error: $errorMessage")
            setInt(android.R.id.text1, "setBackgroundColor", Color.RED)
        }
    }

    companion object {
        private const val LOOK_AHEAD_DAYS = 15
    }
}