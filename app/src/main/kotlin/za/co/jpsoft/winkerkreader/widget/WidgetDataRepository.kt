package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase
import za.co.jpsoft.winkerkreader.widget.WidgetRow
import za.co.jpsoft.winkerkreader.widget.WidgetQueryBuilder
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object WidgetDataRepository {
    private const val TAG = "WidgetDataRepository"
    private var cachedRows: List<WidgetRow>? = null

    fun getWidgetRows(): List<WidgetRow> = cachedRows ?: emptyList()

    fun invalidateCache() {
        cachedRows = null
        if (BuildConfig.DEBUG) Log.d(TAG, "Cache invalidated")
    }

    fun refreshCache(context: Context) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Refreshing widget cache")
        try {
            val rows = queryWidgetData(context)
            cachedRows = rows
            if (BuildConfig.DEBUG) Log.d(TAG, "Cache refreshed with ${rows.size} rows")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to refresh widget cache", e)
            cachedRows = emptyList()
        }
    }

    private fun queryWidgetData(context: Context): List<WidgetRow> {
        val db = WinkerkDatabase.getInstance(context).openHelper.writableDatabase
        val query = WidgetQueryBuilder.buildCombinedQuery()
        val rows = ArrayList<WidgetRow>()
        val today = LocalDate.now()

        db.query(SimpleSQLiteQuery(query)).use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val firstNameIdx = cursor.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)
                    val lastNameIdx = cursor.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_VAN)
                    val gemeenteIdx = cursor.getColumnIndex(WinkerkContract.winkerkEntry.LIDMATE_GEMEENTE)
                    val reasonIdx = cursor.getColumnIndex("Rede")
                    val dateIdx = cursor.getColumnIndex("Datum")

                    if (firstNameIdx < 0 || reasonIdx < 0 || dateIdx < 0) continue

                    val firstName = cursor.getString(firstNameIdx) ?: continue
                    val lastName = if (lastNameIdx >= 0) cursor.getString(lastNameIdx) ?: "" else ""
                    val gemeente = if (gemeenteIdx >= 0) cursor.getString(gemeenteIdx) ?: "" else ""
                    val reason = cursor.getString(reasonIdx) ?: ""
                    val dateString = cursor.getString(dateIdx) ?: continue
                    if (dateString.length < 10) continue

                    val settings = SettingsManager.getInstance(context)
                    when (reason) {
                        "Doop" -> if (!settings.widgetDoop) continue
                        "Huwelik" -> if (!settings.widgetHuwelik) continue
                        "Belydenis" -> if (!settings.widgetBelydenis) continue
                        "Oorlede" -> if (!settings.widgetSterf) continue
                    }

                    val eventDate = Utils.parseDate(dateString.substring(0, 10)) ?: continue
                    val years = ChronoUnit.YEARS.between(eventDate, today).toInt()
                    val ageDisplay = when (reason) {
                        "Verjaar" -> "($years 🎂)"
                        "Doop" -> "($years 💧)"
                        "Huwelik" -> "($years 💍)"
                        "Belydenis" -> "($years ⛪)"
                        "Oorlede" -> "($years 🪦)"
                        else -> "($years)"
                    }

                    val eventMonthDay = java.time.MonthDay.of(eventDate.monthValue, eventDate.dayOfMonth)
                    val todayMonthDay = java.time.MonthDay.from(today)
                    val shouldShow = eventMonthDay.isAfter(todayMonthDay) || eventMonthDay == todayMonthDay

                    if (shouldShow) {
                        val fullName = "$firstName $lastName".trim()
                        val displayText = "$fullName $ageDisplay"
                        rows.add(
                            WidgetRow(
                                displayText = displayText,
                                day = dateString.substring(0, 2),
                                month = dateString.substring(3, 5),
                                reason = reason,
                                gemeente = gemeente
                            )
                        )
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Error processing widget row", e)
                }
            }
        }
        return rows
    }
}