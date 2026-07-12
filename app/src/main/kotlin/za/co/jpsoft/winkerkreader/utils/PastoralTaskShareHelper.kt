package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.content.Intent
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pushes a reminder to any task/note app via the Android share sheet.
 * No Google sign-in, no API setup — works with Google Tasks (if it registers
 * as a share target on the device), Microsoft To Do, Keep, or any other app
 * the pastor has installed that accepts shared text.
 */
object PastoralTaskShareHelper {

    fun shareReminder(
        context: Context,
        reminder: FollowUpReminderEntity,
        memberDisplayName: String
    ) {
        val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        val dueDate = Instant.ofEpochMilli(reminder.dueDateUtc)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(dateFormatter)

        val body = buildString {
            append(reminder.title)
            append("\n")
            append(memberDisplayName)
            append(" · ")
            append(dueDate)
            if (!reminder.note.isNullOrBlank()) {
                append("\n\n")
                append(reminder.note)
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, reminder.title)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.tasks_deel_titel))
        )
    }
}