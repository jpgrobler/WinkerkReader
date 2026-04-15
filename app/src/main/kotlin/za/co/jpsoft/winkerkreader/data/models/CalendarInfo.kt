package za.co.jpsoft.winkerkreader.data.models

// CalendarInfo.kt


/**
 * Data class representing a calendar on the device.
 */
data class CalendarInfo(
    var id: Long,
    var name: String,
    var displayName: String,
    var accountName: String
)