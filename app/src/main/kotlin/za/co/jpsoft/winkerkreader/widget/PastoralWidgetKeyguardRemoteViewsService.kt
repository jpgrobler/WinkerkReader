package za.co.jpsoft.winkerkreader.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.utils.Utils.toLocalDateSafe
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PastoralWidgetKeyguardRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return PastoralWidgetKeyguardFactory(applicationContext)
    }

    private class PastoralWidgetKeyguardFactory(private val context: Context) : RemoteViewsFactory {

        private val TAG = "PastoralWidgetKeyguardFactory"
        private var reminders: List<FollowUpReminderEntity> = emptyList()
        private val zoneId = ZoneId.systemDefault()
        private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault())

        override fun onCreate() { /* no-op */
        }

        override fun onDataSetChanged() {
            if (BuildConfig.DEBUG) Log.d(TAG, "onDataSetChanged called")
            try {
                val db = PastoralDatabase.getInstance(context)
                reminders = db.followUpReminderDao().getAllPending()
                    .sortedBy { it.dueDateUtc }
                if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${reminders.size} pending reminders")
            } catch (e: Exception) {
                reminders = emptyList()
            }
        }

        override fun onDestroy() {
            reminders = emptyList()
        }

        override fun getCount(): Int = reminders.size

        override fun getViewAt(position: Int): RemoteViews {
            val reminder = reminders[position]
            val views = RemoteViews(context.packageName, R.layout.widget_pastoral_keyguard_item)

            val displayName =
                reminder.memberDisplayNameCache?.takeIf { it.isNotBlank() } ?: "Lidmaat"
            val dueDate = reminder.dueDateUtc.toLocalDateSafe() ?: LocalDate.now()
            val today = LocalDate.now(zoneId)
            val isToday = dueDate == today
            val dateStr =
                if (isToday) context.getString(R.string.datum_vandag) else dateFormatter.format(
                    dueDate
                )

            // Build the combined string
            val symbol = reminder.symbol?.takeIf { it.isNotBlank() } ?: ""
            val combinedText = "$dateStr $displayName: $symbol${reminder.title}"
            views.setTextViewText(R.id.widget_pastoral_keyguard_item_text, combinedText)

            // Optional: color today differently (only if you want)
            // We keep it simple for keyguard – all white.

            return views
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long =
            reminders[position].reminderId.hashCode().toLong()

        override fun hasStableIds(): Boolean = false
    }
}