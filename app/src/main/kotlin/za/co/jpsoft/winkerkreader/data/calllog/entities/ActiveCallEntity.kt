package za.co.jpsoft.winkerkreader.data.calllog.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import za.co.jpsoft.winkerkreader.data.calllog.models.CallType

@Entity(tableName = "active_calls")
data class ActiveCallEntity(
    @PrimaryKey
    val callId: String,
    val number: String,
    val contactName: String,
    val callType: CallType,
    val source: String,
    val startTime: Long
)