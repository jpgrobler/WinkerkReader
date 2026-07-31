package za.co.jpsoft.winkerkreader.data.models

import java.time.LocalDate

/**
 * UI‑only representation of a pastoral reminder, used by the mini‑list adapter.
 * Contains only the data needed for display and interaction.
 */
data class PendingReminderUiItem(
    val reminderId: String,
    val title: String,
    val symbol: String?,
    val contextSuffix: String?,    // e.g. hospital name, deceased name, etc.
    val dueDate: LocalDate,
    val isOverdue: Boolean
)