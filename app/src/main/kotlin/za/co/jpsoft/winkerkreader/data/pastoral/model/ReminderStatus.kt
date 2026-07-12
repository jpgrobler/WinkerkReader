package za.co.jpsoft.winkerkreader.data.pastoral.model

enum class ReminderStatus {
    PENDING,
    COMPLETED,
    SNOOZED,
    CANCELLED;

    companion object {
        fun fromStored(value: String): ReminderStatus =
            entries.firstOrNull { it.name == value } ?: PENDING
    }
}
