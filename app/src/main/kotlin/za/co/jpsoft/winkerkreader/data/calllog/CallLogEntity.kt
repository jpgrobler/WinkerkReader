package za.co.jpsoft.winkerkreader.data.calllog

import androidx.room.Entity
import androidx.room.PrimaryKey
import za.co.jpsoft.winkerkreader.data.models.CallType

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callerInfo: String,
    val timestamp: Long,
    val dateTime: String,           // formatted display string, kept for parity with legacy rows
    val callType: CallType,
    val source: String,             // "Phone Call", "WhatsApp", etc.
    val duration: Long = 0L
)

