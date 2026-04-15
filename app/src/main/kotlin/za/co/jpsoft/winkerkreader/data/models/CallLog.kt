package za.co.jpsoft.winkerkreader.data.models

data class CallLog(
    var id: Long,
    var callerInfo: String,
    var timestamp: Long,
    var formattedDateTime: String,
    var callType: String = "INCOMING",
    var source: String = "WhatsApp",
    var duration: Long = 0
)