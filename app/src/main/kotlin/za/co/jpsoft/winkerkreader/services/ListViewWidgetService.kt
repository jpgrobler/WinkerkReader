package za.co.jpsoft.winkerkreader.services

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.widget.WidgetDataRepository
import za.co.jpsoft.winkerkreader.widget.WidgetRow
import za.co.jpsoft.winkerkreader.widget.WinkerkReaderWidgetProvider
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class ListViewWidgetService : RemoteViewsService() {

    @Inject
    lateinit var congregationPrefs: CongregationPrefs

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        if (BuildConfig.DEBUG) Log.d(TAG, "Creating RemoteViewsFactory")
        return WidgetViewsFactory(applicationContext, intent, congregationPrefs)
    }

    companion object {
        private const val TAG = "ListViewWidgetService"
    }
}

class WidgetViewsFactory(
    private val context: Context,
    intent: Intent,
    private val congregationPrefs: CongregationPrefs
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
        if (BuildConfig.DEBUG) Log.d(tag, "🔄 onDataSetChanged - forcing cache refresh")

        // Force refresh the cache
        WidgetDataRepository.refreshCache(context)

        val rows = WidgetDataRepository.getWidgetRows()
        if (BuildConfig.DEBUG) {
            Log.d(tag, "Rows loaded: ${rows.size}")
            if (rows.isEmpty()) {
                if (BuildConfig.DEBUG) Log.w(
                    tag,
                    "⚠️ No widget rows loaded! Check database access."
                )
            }
        }
    }

    override fun getCount(): Int {
        val count = WidgetDataRepository.getWidgetRows().size
        if (BuildConfig.DEBUG) Log.d(tag, "getCount = $count")
        return count
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (BuildConfig.DEBUG) Log.d(tag, "getViewAt position=$position")
        val rows = WidgetDataRepository.getWidgetRows()

        if (position >= rows.size || rows.isEmpty()) {
            if (BuildConfig.DEBUG) Log.w(tag, "No rows available at position $position")
            return createEmptyRow()
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
        val congregationName = row.gemeente

        // --- Determine if this row is today ---
        val todayDay = today.toString().substring(8, 10)  // "dd"
        val isToday = day == todayDay

        // Use injected congregationPrefs instead of SettingsManager
        val congregationColor = when (congregationName) {
            congregationPrefs.gemeenteNaam -> congregationPrefs.gemeenteKleur
            congregationPrefs.gemeente2Naam -> congregationPrefs.gemeente2Kleur
            congregationPrefs.gemeente3Naam -> congregationPrefs.gemeente3Kleur
            else -> ContextCompat.getColor(context, R.color.md_theme_surface)
        }
        remoteViews.setInt(R.id.congregation_indicator, "setColorFilter", congregationColor)

        // --- Choose background colour ---
        val bgResId = if (isToday) {
            R.color.md_theme_primaryContainer
        } else {
            R.color.md_theme_surface
        }
        val backgroundColor = ContextCompat.getColor(context, bgResId)
        remoteViews.setInt(R.id.row_container, "setBackgroundColor", backgroundColor)

        // --- Build the display text with spans ---
        val displayText = "$day/$month $name"
        val spannable = SpannableString(displayText)
        val textLength = spannable.length

        // Date formatting (first 5 chars: "dd/mm")
        if (textLength >= 5) {
            spannable.setSpan(RelativeSizeSpan(0.6f), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Gray out repeated dates
        if (position > 0 && allRows.isNotEmpty() && day == allRows[position - 1].day) {
            spannable.setSpan(
                ForegroundColorSpan(
                    ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)
                ),
                0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, R.color.md_theme_onSurface)),
                0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Main text colour
        if (textLength > 6) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, R.color.md_theme_onSurface)),
                6, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Highlight today’s row
        if (isToday) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, R.color.md_theme_primary)),
                0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (textLength > 6) {
                spannable.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.md_theme_primary)),
                    6, textLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
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

        // Set up click intent
        val fillInIntent = Intent().apply {
            putExtra(WinkerkReaderWidgetProvider.EXTRA_WORD, name)
        }
        remoteViews.setOnClickFillInIntent(android.R.id.text1, fillInIntent)

        return remoteViews
    }

    private fun createEmptyRow(): RemoteViews {
        if (BuildConfig.DEBUG) Log.d(tag, "Creating empty row")
        val remoteViews = RemoteViews(context.packageName, R.layout.row)
        remoteViews.setTextViewText(android.R.id.text1, "Geen verjaarsdae")
        remoteViews.setInt(
            android.R.id.text1, "setBackgroundColor",
            ContextCompat.getColor(context, R.color.md_theme_surfaceContainerHighest)
        )
        return remoteViews
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}