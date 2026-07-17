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
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.utils.SettingsManager
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
        private val congregationCache = mutableMapOf<String, String?>()

        override fun onCreate() {
            if (BuildConfig.DEBUG) Log.d(TAG, "onCreate")
            // ✅ Do NOT load data here – onDataSetChanged() will be called next.
        }

        override fun onDataSetChanged() {
            congregationCache.clear()
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
                        if (BuildConfig.DEBUG) Log.w(
                            TAG,
                            "⚠️ No pastoral reminders found - widget will show empty state"
                        )
                    } else {
                        reminders.take(3).forEach { reminder ->
                            if (BuildConfig.DEBUG) Log.d(
                                TAG,
                                "  Reminder: ${reminder.title} due ${reminder.dueDateUtc}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error loading pastoral reminders", e)
                reminders = emptyList()
                dataLoaded = false
            }
        }

        override fun onDestroy() {
            congregationCache.clear()
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
                if (BuildConfig.DEBUG) Log.e(TAG, "Error binding view at position $position", e)
                views.setTextViewText(R.id.widget_pastoral_item_text, "Fout")
            }

            return views
        }

        private fun bindReminderToViews(views: RemoteViews, reminder: FollowUpReminderEntity) {
            // --- Dynamic colours ---
            val surfaceColor = ContextCompat.getColor(context, R.color.md_theme_surface)
            val primaryContainerColor =
                ContextCompat.getColor(context, R.color.md_theme_primaryContainer)
            val onSurfaceColor = ContextCompat.getColor(context, R.color.md_theme_onSurface)
            val onSurfaceVariantColor =
                ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)
            val primaryColor = ContextCompat.getColor(context, R.color.md_theme_primary)

            // Determine if today
            val dueDate = reminder.dueDateUtc.toLocalDateSafe() ?: LocalDate.now()
            val today = LocalDate.now(zoneId)
            val isToday = dueDate == today
            val isOverdue = dueDate.isBefore(today)

            // Set row background: primary container for today, otherwise surface
            val bgColor = if (isToday) primaryContainerColor else surfaceColor
            views.setInt(R.id.widget_pastoral_item_root, "setBackgroundColor", bgColor)
            // --- Congregation colour indicator ---
            val congregationName = getMemberCongregationCached(reminder.memberGuid, context)
            val settingsManager = SettingsManager.getInstance(context)
            val congregationColor = when (congregationName) {
                settingsManager.gemeenteNaam -> settingsManager.gemeenteKleur
                settingsManager.gemeente2Naam -> settingsManager.gemeente2Kleur
                settingsManager.gemeente3Naam -> settingsManager.gemeente3Kleur
                else -> ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)
            }
            views.setInt(
                R.id.widget_pastoral_congregation_indicator,
                "setColorFilter",
                congregationColor
            )
            // Build display text
            val displayName =
                reminder.memberDisplayNameCache?.takeIf { it.isNotBlank() } ?: "Lidmaat"
            val dateStr = if (isToday) {
                context.getString(R.string.datum_vandag)
            } else {
                dateFormatter.format(dueDate)
            }
            val symbol = reminder.symbol?.takeIf { it.isNotBlank() } ?: ""
            val fullText = "$dateStr $displayName: $symbol${reminder.title}"

            // Build spans (same logic, but use M3 colours)
            val spannable = SpannableString(fullText)
            val textLength = fullText.length

            // Date part
            val dateEnd = dateStr.length
            if (dateEnd <= textLength) {
                spannable.setSpan(
                    RelativeSizeSpan(0.8f),
                    0,
                    dateEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (isToday) {
                    spannable.setSpan(
                        ForegroundColorSpan(primaryColor),
                        0,
                        dateEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        dateEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    spannable.setSpan(
                        ForegroundColorSpan(onSurfaceVariantColor),
                        0,
                        dateEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            // Name part
            val nameStart = dateEnd + 1
            val nameEnd = nameStart + displayName.length
            if (nameEnd <= textLength) {
                spannable.setSpan(
                    RelativeSizeSpan(1.25f),
                    nameStart,
                    nameEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(onSurfaceColor),
                    nameStart,
                    nameEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Symbol (if any)
            val symbolStart = nameEnd + 1
            val symbolEnd = symbolStart + symbol.length
            if (symbol.isNotEmpty() && symbolEnd <= textLength) {
                spannable.setSpan(
                    RelativeSizeSpan(1.5f),
                    symbolStart,
                    symbolEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    symbolStart,
                    symbolEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // Keep symbol color as onSurface (or maybe primary? leave as onSurface)
            }

            // Title part
            val titleStart = if (symbol.isNotEmpty()) symbolEnd + 1 else nameEnd + 1
            if (titleStart < textLength) {
                spannable.setSpan(
                    RelativeSizeSpan(1f),
                    titleStart,
                    textLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(onSurfaceColor),
                    titleStart,
                    textLength,
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

        private fun getMemberCongregation(memberGuid: String?, context: Context): String? {
            if (memberGuid.isNullOrEmpty()) return null
            return try {
                val projection = arrayOf(WinkerkContract.winkerkEntry.LIDMATE_GEMEENTE)
                val selection = "${WinkerkContract.winkerkEntry.LIDMATE_LIDMAATGUID} = ?"
                context.contentResolver.query(
                    WinkerkContract.winkerkEntry.CONTENT_URI,
                    projection,
                    selection,
                    arrayOf(memberGuid),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(WinkerkContract.winkerkEntry.LIDMATE_GEMEENTE))
                    } else null
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error fetching congregation for $memberGuid", e)
                null
            }
        }

        private fun createEmptyView(): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_pastoral_item)
            views.setTextViewText(
                R.id.widget_pastoral_item_text,
                context.getString(R.string.widget_pastoral_empty)
            )
            views.setViewVisibility(R.id.widget_pastoral_item_overdue, View.GONE)
            views.setViewVisibility(R.id.widget_pastoral_congregation_indicator, View.GONE)
            // Set background to surface (or surfaceContainerHighest)
            views.setInt(
                R.id.widget_pastoral_item_root, "setBackgroundColor",
                ContextCompat.getColor(context, R.color.md_theme_surface)
            )
            return views
        }

        private fun getMemberCongregationCached(memberGuid: String?, context: Context): String? {
            if (memberGuid.isNullOrEmpty()) return null
            return congregationCache.getOrPut(memberGuid) {
                getMemberCongregation(memberGuid, context)
            }
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long =
            reminders.getOrNull(position)?.reminderId?.hashCode()?.toLong() ?: 0L
        override fun hasStableIds(): Boolean = false
    }
}