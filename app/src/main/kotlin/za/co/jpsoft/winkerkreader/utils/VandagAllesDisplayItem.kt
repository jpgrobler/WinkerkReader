package za.co.jpsoft.winkerkreader.utils

import za.co.jpsoft.winkerkreader.data.pastoral.model.VandagAllesItem

/**
 * Sealed hierarchy for flattened "Vandag (Alles)" display items.
 * Allows mixing section headers, celebrations, and reminders in a single flat list.
 * Used by BedieningAllesAdapter to handle multiple view types.
 */
sealed class VandagAllesDisplayItem {
    abstract val id: String

    /**
     * Section header (e.g., "🎂 Vieringe", "⚠️ Agterstallig")
     */
    data class Header(
        val title: String,
        val sectionType: SectionType
    ) : VandagAllesDisplayItem() {
        override val id: String = "header_${sectionType.name}"

        enum class SectionType {
            CELEBRATIONS, OVERDUE, DUE_TODAY
        }
    }

    /**
     * Celebration item (birthday, anniversary, etc.)
     */
    data class Celebration(
        val item: VandagAllesItem.Celebration
    ) : VandagAllesDisplayItem() {
        override val id: String = "celebration_${item.memberGuid}_${item.eventType}"
    }

    /**
     * Reminder item (follow-up, pastoral visit, etc.)
     */
    data class Reminder(
        val item: VandagAllesItem.Reminder
    ) : VandagAllesDisplayItem() {
        override val id: String = "reminder_${item.reminderWithMember.reminder.reminderId}"
    }
}