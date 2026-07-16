package za.co.jpsoft.winkerkreader.widget

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object WidgetDataRepository {
    private const val TAG = "WidgetDataRepository"
    private var cachedRows: List<WidgetRow>? = null
    private var lastRefreshTime = 0L
    private const val MIN_REFRESH_INTERVAL_MS = 5000L

    // Dates are stored as dd/MM/yyyy in the WinKerk database (confirmed via diagnostic logging).
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun getWidgetRows(): List<WidgetRow> {
        val rows = cachedRows ?: emptyList()
        if (BuildConfig.DEBUG) Log.d(TAG, "getWidgetRows: returning ${rows.size} rows")
        return rows
    }

    fun invalidateCache() {
        cachedRows = null
        lastRefreshTime = 0L
        if (BuildConfig.DEBUG) Log.d(TAG, "Cache invalidated")
    }

    fun refreshCache(context: Context) {
        try {
            val currentTime = System.currentTimeMillis()
            if (cachedRows != null && currentTime - lastRefreshTime < MIN_REFRESH_INTERVAL_MS) {
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "Cache is fresh (${cachedRows?.size ?: 0} rows), using cached data"
                )
                return
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "🔄 Refreshing widget cache...")

            val rows = queryWidgetData(context)
            cachedRows = rows
            lastRefreshTime = currentTime

            context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_refresh_time", currentTime)
                .apply()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "✅ Cache refreshed with ${rows.size} rows")
                if (rows.isEmpty()) Log.w(TAG, "⚠️ No rows returned from query!")
            }
        } catch (e: Exception) {
            // Ensure callers always get a valid (empty) list rather than a stale null.
            Log.e(TAG, "Failed to refresh widget cache", e)
            cachedRows = emptyList()
        }
    }

    private fun queryWidgetData(context: Context): List<WidgetRow> {
        val rows = mutableListOf<WidgetRow>()
        val today = LocalDate.now()
        val todayMonthDay = MonthDay.from(today)
        val settings = SettingsManager.getInstance(context)

        try {
            val db = WinkerkDatabase.getInstance(context).openHelper.writableDatabase
            val query = WidgetQueryBuilder.buildCombinedQuery()

            if (BuildConfig.DEBUG) Log.d(TAG, "Executing query: $query")

            db.query(SimpleSQLiteQuery(query)).use { cursor ->
                if (BuildConfig.DEBUG) Log.d(TAG, "Cursor has ${cursor.count} rows")

                // Pre-cache column indices — getColumnIndex is O(n) on the column list.
                val firstNameIdx =
                    cursor.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)
                val lastNameIdx = cursor.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_VAN)
                val gemeenteIdx =
                    cursor.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_GEMEENTE)
                val reasonIdx = cursor.getColumnIndex("Rede")
                val dateIdx = cursor.getColumnIndex("Datum")
                val dayIdx = cursor.getColumnIndex("Day")
                val monthIdx = cursor.getColumnIndex("Month")

                if (firstNameIdx < 0 || reasonIdx < 0 || dateIdx < 0) {
                    if (BuildConfig.DEBUG) {
                        Log.w(
                            TAG,
                            "Missing required columns — firstNameIdx=$firstNameIdx, reasonIdx=$reasonIdx, dateIdx=$dateIdx"
                        )
                    }
                    return emptyList()
                }

                val emojiMap = mapOf(
                    "Verjaar" to "🎂",
                    "Doop" to "💧",
                    "Huwelik" to "💍",
                    "Belydenis" to "⛪",
                    "Oorlede" to "🪦"
                )

                while (cursor.moveToNext()) {
                    try {
                        val firstName = cursor.getString(firstNameIdx) ?: ""
                        if (firstName.isEmpty()) continue

                        val lastName =
                            if (lastNameIdx >= 0) cursor.getString(lastNameIdx) ?: "" else ""
                        val gemeente =
                            if (gemeenteIdx >= 0) cursor.getString(gemeenteIdx) ?: "" else ""
                        val reason = cursor.getString(reasonIdx) ?: ""
                        val dateString = cursor.getString(dateIdx) ?: ""
                        val day = cursor.getString(dayIdx) ?: ""
                        val month = cursor.getString(monthIdx) ?: ""

                        if (dateString.length < 10) {
                            if (BuildConfig.DEBUG) Log.w(TAG, "Invalid date string: $dateString")
                            continue
                        }

                        // Filter by user settings before doing any further work.
                        when (reason) {
                            "Doop" -> if (!settings.widgetDoop) continue
                            "Huwelik" -> if (!settings.widgetHuwelik) continue
                            "Belydenis" -> if (!settings.widgetBelydenis) continue
                            "Oorlede" -> if (!settings.widgetSterf) continue
                        }

                        val eventDate = try {
                            LocalDate.parse(dateString.substring(0, 10), DATE_FORMATTER)
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) Log.w(
                                TAG,
                                "Failed to parse date: $dateString",
                                e
                            )
                            continue
                        }

                        // SQL already filters to the lookahead window, but guard against
                        // same-month edge cases letting past days through.
                        val eventMonthDay = MonthDay.of(eventDate.monthValue, eventDate.dayOfMonth)
                        if (eventMonthDay.isBefore(todayMonthDay)) continue

                        val years = ChronoUnit.YEARS.between(eventDate, today).toInt()
                        val emoji = emojiMap[reason] ?: ""
                        // Show age only when meaningful (> 0 suppresses "(0 🎂)" for newborns
                        // and also avoids negative values for any data anomalies).
                        val ageDisplay = "($years $emoji)"

                        val fullName =
                            if (lastName.isNotEmpty()) "$firstName $lastName" else firstName
                        val displayText =
                            if (ageDisplay.isNotEmpty()) "$fullName $ageDisplay" else fullName

                        rows.add(
                            WidgetRow(
                                displayText = displayText,
                                day = day,
                                month = month,
                                reason = reason,
                                gemeente = gemeente
                            )
                        )

                        if (BuildConfig.DEBUG) Log.d(
                            TAG,
                            "Added row: $displayText ($day/$month) - $reason"
                        )

                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Error processing widget row", e)
                    }
                }
            }

            rows.sortWith(compareBy<WidgetRow> { it.month.toIntOrNull() ?: 0 }
                .thenBy { it.day.toIntOrNull() ?: 0 })

        } catch (e: Exception) {
            Log.e(TAG, "Error querying widget data", e)
        }

        return rows
    }

    fun forceRefresh(context: Context) {
        invalidateCache()
        refreshCache(context)
    }
}