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

        override fun onCreate() {
            if (BuildConfig.DEBUG) Log.d(TAG, "onCreate")
        }

        override fun onDataSetChanged() {
            if (BuildConfig.DEBUG) Log.d(TAG, "onDataSetChanged - loading data")
            try {
                val db = PastoralDatabase.getInstance(context)
                reminders = db.followUpReminderDao().getAllPending()
                    .sortedBy { it.dueDateUtc }
                if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${reminders.size} pending reminders")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error loading reminders", e)
                reminders = emptyList()
            }
        }

        override fun onDestroy() {
            reminders = emptyList()
        }

        override fun getCount(): Int {
            val count = reminders.size
            if (BuildConfig.DEBUG) Log.d(TAG, "getCount = $count")
            return count
        }

        override fun getViewAt(position: Int): RemoteViews {
            val darkGrey = "#444444".toColorInt()
            if (BuildConfig.DEBUG) Log.d(TAG, "getViewAt position $position")
            val reminder = reminders[position]
            val views = RemoteViews(context.packageName, R.layout.widget_pastoral_item)

            val displayName =
                reminder.memberDisplayNameCache?.takeIf { it.isNotBlank() } ?: "Lidmaat"
            val dueDate = reminder.dueDateUtc.toLocalDateSafe() ?: LocalDate.now()
            val today = LocalDate.now(zoneId)
            val isToday = dueDate == today
            val isOverdue = dueDate.isBefore(today)

            val dateStr =
                if (isToday) context.getString(R.string.datum_vandag) else dateFormatter.format(
                    dueDate
                )
            val symbol = getReminderSymbol(reminder)

            // Build the full text: "dd/MM Name: Title"
            val fullText = "$dateStr $displayName: $symbol${reminder.title}"
            val spannable = SpannableString(fullText)

            // 1. Date part (first N characters)
            val dateEnd = dateStr.length
            spannable.setSpan(
                RelativeSizeSpan(0.8f),  // smaller
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

            // 2. Name part (after date + space)
            val nameStart = dateEnd + 1  // +1 for space
            val nameEnd = nameStart + displayName.length
            if (nameEnd <= fullText.length) {
                spannable.setSpan(
                    RelativeSizeSpan(1.25f),  // bigger
                    nameStart, nameEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
//                spannable.setSpan(
//                    StyleSpan(Typeface.BOLD),
//                    nameStart, nameEnd,
//                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
//                )
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
                        nameStart, nameEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            // 3. Symbol
            val symbolStart = nameEnd + 1
            val symbolEnd = symbolStart + symbol.length
            if (symbolEnd <= fullText.length) {
                spannable.setSpan(
                    RelativeSizeSpan(1.5f),  // bigger
                    symbolStart, symbolEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    symbolStart, symbolEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // 4. Title part (after ": " if present, or after name + space)
            val titleStart = symbolEnd + 1
            if (titleStart > 1 && titleStart < fullText.length) {
                spannable.setSpan(
                    RelativeSizeSpan(1f),
                    titleStart, fullText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(darkGrey),
                    titleStart, fullText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            views.setTextViewText(R.id.widget_pastoral_item_text, spannable)

            // Overdue badge
            views.setViewVisibility(
                R.id.widget_pastoral_item_overdue,
                if (isOverdue) View.VISIBLE else View.GONE
            )

            return views
        }

        private fun getReminderSymbol(reminder: FollowUpReminderEntity): String {
            return reminder.symbol?.takeIf { it.isNotBlank() } ?: ""
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            reminders[position].reminderId.hashCode().toLong()

        override fun hasStableIds(): Boolean = false
    }
}