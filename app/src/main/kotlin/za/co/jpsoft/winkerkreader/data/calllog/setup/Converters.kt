package za.co.jpsoft.winkerkreader.data.calllog.setup

import androidx.room.TypeConverter
import za.co.jpsoft.winkerkreader.data.calllog.models.CallType

class CallTypeConverter {
    @TypeConverter
    fun fromCallType(value: CallType): String = value.name

    @TypeConverter
    fun toCallType(value: String): CallType =
        runCatching { CallType.valueOf(value) }.getOrDefault(CallType.OTHER)
}