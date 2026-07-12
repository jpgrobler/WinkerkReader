package za.co.jpsoft.winkerkreader.data.pastoral.model

enum class ScheduleType {
    DATE_ONLY,
    TIMED;

    companion object {
        fun fromStored(value: String): ScheduleType =
            entries.firstOrNull { it.name == value } ?: DATE_ONLY
    }
}
