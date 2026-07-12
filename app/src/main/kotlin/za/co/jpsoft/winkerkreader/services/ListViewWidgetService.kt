// File: services/ListViewWidgetService.kt
package za.co.jpsoft.winkerkreader.services

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.WidgetDataRepository
import za.co.jpsoft.winkerkreader.widget.WidgetRow
import za.co.jpsoft.winkerkreader.widget.WinkerkReaderWidgetProvider
import java.time.LocalDate

/**
 * Consolidated widget service with improved error handling and thread safety.
 * Now reads from WidgetDataRepository, decoupling from the main database.
 */
class ListViewWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        if (BuildConfig.DEBUG) Log.d(TAG, "Creating RemoteViewsFactory")
        return WidgetViewsFactory(applicationContext, intent)
    }

    companion object {
        private const val TAG = "ListViewWidgetService"
    }
}

/**
 * Factory that builds the list of widget items from the repository.
 */
class WidgetViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val tag = "WidgetViewsFactory"
    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    override fun onCreate() {
        if (BuildConfig.DEBUG) Log.d(tag, "onCreate")
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.d(tag, "onDestroy")
    }

    override fun onDataSetChanged() {
        if (BuildConfig.DEBUG) Log.d("WidgetViewsFactory", "onDataSetChanged - refreshing cache")
        WidgetDataRepository.refreshCache(context)
        val rows = WidgetDataRepository.getWidgetRows()
        if (BuildConfig.DEBUG) Log.d("WidgetViewsFactory", "Rows loaded: ${rows.size}")
    }

    override fun getCount(): Int {
        val count = WidgetDataRepository.getWidgetRows().size
        if (BuildConfig.DEBUG) Log.d("WidgetViewsFactory", "getCount = $count")
        return count
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (BuildConfig.DEBUG) Log.d("WidgetViewsFactory", "getViewAt position=$position")
        val rows = WidgetDataRepository.getWidgetRows()
        if (position >= rows.size) {
            return createErrorRow("Invalid position")
        }
        return createViewForRow(rows[position], position, rows)
    }

    private fun createViewForRow(
        row: WidgetRow,
        position: Int,
        allRows: List<WidgetRow>
    ): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.row)

        val today = LocalDate.now()
        val day = row.day
        val month = row.month
        val name = row.displayText
        val gemeente = row.gemeente

        // Background color based on gemeente
        var backgroundColor = Color.WHITE
        if (gemeente.isNotEmpty()) {
            val settings = SettingsManager.getInstance(context)
            when (gemeente) {
                settings.gemeenteNaam -> backgroundColor = settings.gemeenteKleur
                settings.gemeente2Naam -> backgroundColor = settings.gemeente2Kleur
                settings.gemeente3Naam -> backgroundColor = settings.gemeente3Kleur
            }
        }
        remoteViews.setInt(android.R.id.text1, "setBackgroundColor", backgroundColor)

        val displayText = "$day/$month $name"
        val spannable = SpannableString(displayText)
        val textLength = spannable.length

        // Date formatting (first 5 chars: "dd/mm")
        spannable.setSpan(RelativeSizeSpan(0.6f), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Gray out repeated dates
        if (position > 0 && day == allRows[position - 1].day) {
            spannable.setSpan(
                ForegroundColorSpan(Color.LTGRAY),
                0,
                5,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else {
            spannable.setSpan(
                ForegroundColorSpan(Color.BLACK),
                0,
                5,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Main text color (dark gray)
        spannable.setSpan(
            ForegroundColorSpan(Color.argb(200, 50, 50, 50)),
            6, textLength,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Highlight today's date
        val todayDay = today.toString().substring(8, 10)
        if (day == todayDay) {
            spannable.setSpan(
                ForegroundColorSpan(Color.BLUE),
                0,
                5,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.argb(255, 0, 0, 220)),
                6, textLength,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Parentheses formatting (age part)
        val startPos = textLength - 7
        if (startPos > -1) {
            val parenthesesPos = spannable.toString().indexOf('(', startPos)
            if (parenthesesPos > 0) {
                spannable.setSpan(
                    RelativeSizeSpan(0.8f),
                    parenthesesPos, textLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        remoteViews.setTextViewText(android.R.id.text1, spannable)

        // Set up click intent (opens VerjaarSmsActivity when tapped)
        val fillInIntent = Intent().apply {
            putExtra(WinkerkReaderWidgetProvider.EXTRA_WORD, name)
        }
        remoteViews.setOnClickFillInIntent(android.R.id.text1, fillInIntent)

        return remoteViews
    }

    private fun createErrorRow(errorMessage: String): RemoteViews {
        return RemoteViews(context.packageName, R.layout.row).apply {
            setTextViewText(android.R.id.text1, "Error: $errorMessage")
            setInt(android.R.id.text1, "setBackgroundColor", Color.RED)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}