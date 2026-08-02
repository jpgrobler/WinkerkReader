package za.co.jpsoft.winkerkreader.widget

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract
import za.co.jpsoft.winkerkreader.data.members.setup.WinkerkDatabase
import za.co.jpsoft.winkerkreader.utils.prefs.WidgetPrefs
import za.co.jpsoft.winkerkreader.widget.WidgetDataRepository.init
import za.co.jpsoft.winkerkreader.widget.WidgetDataRepository.widgetPrefs
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Repository for widget data (upcoming events – birthdays, baptisms, weddings, etc.)
 *
 * Requires [widgetPrefs] to be set via [init] before any data access.
 * This is called once from the Application class to avoid the need for
 * a full DI refactor of the entire widget subsystem.
 */
object WidgetDataRepository {

    private const val TAG = "WidgetDataRepository"
    private const val MIN_REFRESH_INTERVAL_MS = 5000L

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private var cachedRows: List<WidgetRow>? = null
    private var lastRefreshTime = 0L
    private lateinit var widgetPrefs: WidgetPrefs
    private var isInitialized = false

    /**
     * Must be called once before any other method (typically from Application).
     * @param prefs The WidgetPrefs instance injected via Hilt.
     */
    fun init(prefs: WidgetPrefs) {
        widgetPrefs = prefs
        isInitialized = true
        if (BuildConfig.DEBUG) Log.d(TAG, "WidgetDataRepository initialized")
    }

    fun getWidgetRows(): List<WidgetRow> {
        checkInitialized()
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
        checkInitialized()
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
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to refresh widget cache", e)
            cachedRows = emptyList()
        }
    }

    fun forceRefresh(context: Context) {
        invalidateCache()
        refreshCache(context)
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("WidgetDataRepository not initialized – call init() first")
        }
    }

    private fun queryWidgetData(context: Context): List<WidgetRow> {
        val rows = mutableListOf<WidgetRow>()
        val today = LocalDate.now()
        val todayMonthDay = MonthDay.from(today)

        try {
            val memberDao = WinkerkDatabase.getInstance(context).memberDao()
            val query = WidgetQueryBuilder.buildCombinedQuery()

            if (BuildConfig.DEBUG) Log.d(TAG, "Executing query: $query")

            memberDao.queryRaw(SimpleSQLiteQuery(query)).use { cursor ->
                if (BuildConfig.DEBUG) Log.d(TAG, "Cursor has ${cursor.count} rows")

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

                        // ── Filter by widget preferences ──────────────────────────
                        when (reason) {
                            "Doop" -> if (!widgetPrefs.widgetDoop) continue
                            "Huwelik" -> if (!widgetPrefs.widgetHuwelik) continue
                            "Belydenis" -> if (!widgetPrefs.widgetBelydenis) continue
                            "Oorlede" -> if (!widgetPrefs.widgetSterf) continue
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

                        val eventMonthDay = MonthDay.of(eventDate.monthValue, eventDate.dayOfMonth)
                        if (eventMonthDay.isBefore(todayMonthDay)) continue

                        val years = ChronoUnit.YEARS.between(eventDate, today).toInt()
                        val emoji = emojiMap[reason] ?: ""
                        val ageDisplay = if (years > 0) "($years $emoji)" else ""

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
            if (BuildConfig.DEBUG) Log.e(TAG, "Error querying widget data", e)
        }

        return rows
    }
}