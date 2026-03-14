// CalendarInfo.kt
package za.co.jpsoft.winkerkreader

/**
 * Data class representing a calendar on the device.
 */
data class CalendarInfo(
    var id: Long,
    var name: String,
    var displayName: String,
    var accountName: String
)