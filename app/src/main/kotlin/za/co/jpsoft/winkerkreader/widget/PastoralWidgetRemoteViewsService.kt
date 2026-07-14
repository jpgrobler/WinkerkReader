package za.co.jpsoft.winkerkreader.widget

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.utils.Utils.toLocalDateSafe
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PastoralWidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return PastoralWidgetFactory(applicationContext)
    }

    private class PastoralWidgetFactory(private val context: Context) : RemoteViewsFactory {

        private val TAG = "PastoralWidgetFactory"
        private var reminders: List<FollowUpReminderEntity> = emptyList()
        private val zoneId = ZoneId.systemDefault()
        private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault())
        private var dataLoaded = false

        override fun onCreate() {
            if (BuildConfig.DEBUG) Log.d(TAG, "onCreate")
            // ✅ Force load data immediately
            loadData()
        }

        override fun onDataSetChanged() {
            if (BuildConfig.DEBUG) Log.d(TAG, "🔄 onDataSetChanged - loading pastoral reminders")
            loadData()
        }

        private fun loadData() {
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "Loading pastoral reminders from database...")

                val db = PastoralDatabase.getInstance(context)
                reminders = db.followUpReminderDao().getAllPending()
                    .sortedBy { it.dueDateUtc }

                dataLoaded = true

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "✅ Loaded ${reminders.size} pastoral reminders")
                    if (reminders.isEmpty()) {
                        Log.w(TAG, "⚠️ No pastoral reminders found - widget will show empty state")
                    } else {
                        // Log first few reminders for debugging
                        reminders.take(3).forEach { reminder ->
                            Log.d(TAG, "  Reminder: ${reminder.title} due ${reminder.dueDateUtc}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading pastoral reminders", e)
                reminders = emptyList()
                dataLoaded = false
            }
        }

        override fun onDestroy() {
            if (BuildConfig.DEBUG) Log.d(TAG, "onDestroy")
            reminders = emptyList()
            dataLoaded = false
        }

        override fun getCount(): Int {
            val count = reminders.size
            if (BuildConfig.DEBUG) Log.d(TAG, "getCount = $count")
            return count
        }

        override fun getViewAt(position: Int): RemoteViews {
            if (position >= reminders.size) {
                if (BuildConfig.DEBUG) {
                    Log.w(
                        TAG,
                        "getViewAt: position $position out of bounds (size=${reminders.size})"
                    )
                }
                return createEmptyView()
            }

            val reminder = reminders[position]
            val views = RemoteViews(context.packageName, R.layout.widget_pastoral_item)

            try {
                bindReminderToViews(views, reminder)
            } catch (e: Exception) {
                Log.e(TAG, "Error binding view at position $position", e)
                views.setTextViewText(R.id.widget_pastoral_item_text, "Fout")
            }

            return views
        }

        private fun bindReminderToViews(views: RemoteViews, reminder: FollowUpReminderEntity) {
            val darkGrey = "#444444".toColorInt()

            val displayName =
                reminder.memberDisplayNameCache?.takeIf { it.isNotBlank() } ?: "Lidmaat"
            val dueDate = reminder.dueDateUtc.toLocalDateSafe() ?: LocalDate.now()
            val today = LocalDate.now(zoneId)
            val isToday = dueDate == today
            val isOverdue = dueDate.isBefore(today)

            val dateStr = if (isToday) {
                context.getString(R.string.datum_vandag)
            } else {
                dateFormatter.format(dueDate)
            }

            val symbol = reminder.symbol?.takeIf { it.isNotBlank() } ?: ""
            val fullText = "$dateStr $displayName: $symbol${reminder.title}"

            // Build styled text
            val spannable = SpannableString(fullText)
            val textLength = fullText.length

            // 1. Date part
            val dateEnd = dateStr.length
            if (dateEnd <= textLength) {
                spannable.setSpan(
                    RelativeSizeSpan(0.8f),
                    0, dateEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                if (isToday) {
                    spannable.setSpan(
                        ForegroundColorSpan(ContextCompat.getColor(context, R.color.primary_blue)),
                        0, dateEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        0, dateEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    spannable.setSpan(
                        ForegroundColorSpan(darkGrey),
                        0, dateEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            // 2. Name part (after date + space)
            val nameStart = dateEnd + 1
            val nameEnd = nameStart + displayName.length
            if (nameEnd <= textLength) {
                spannable.setSpan(
                    RelativeSizeSpan(1.25f),
                    nameStart, nameEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                spannable.setSpan(
                    ForegroundColorSpan(darkGrey),
                    nameStart, nameEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // 3. Symbol
            val symbolStart = nameEnd + 1
            val symbolEnd = symbolStart + symbol.length
            if (symbolEnd <= textLength && symbol.isNotEmpty()) {
                spannable.setSpan(
                    RelativeSizeSpan(1.5f),
                    symbolStart, symbolEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    symbolStart, symbolEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // 4. Title part
            val titleStart = if (symbol.isNotEmpty()) symbolEnd + 1 else nameEnd + 1
            if (titleStart < textLength) {
                spannable.setSpan(
                    RelativeSizeSpan(1f),
                    titleStart, textLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(darkGrey),
                    titleStart, textLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            views.setTextViewText(R.id.widget_pastoral_item_text, spannable)

            // Overdue badge
            views.setViewVisibility(
                R.id.widget_pastoral_item_overdue,
                if (isOverdue) View.VISIBLE else View.GONE
            )
        }

        private fun createEmptyView(): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_pastoral_item)
            views.setTextViewText(
                R.id.widget_pastoral_item_text,
                context.getString(R.string.widget_pastoral_empty)
            )
            views.setViewVisibility(R.id.widget_pastoral_item_overdue, View.GONE)
            return views
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long =
            reminders.getOrNull(position)?.reminderId?.hashCode()?.toLong() ?: 0L
        override fun hasStableIds(): Boolean = false
    }
}