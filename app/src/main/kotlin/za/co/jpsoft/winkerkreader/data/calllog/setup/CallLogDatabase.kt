package za.co.jpsoft.winkerkreader.data.calllog.setup

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import za.co.jpsoft.winkerkreader.data.calllog.dao.CallLogDao
import za.co.jpsoft.winkerkreader.data.calllog.entities.ActiveCallEntity
import za.co.jpsoft.winkerkreader.data.calllog.entities.CallLogEntity

@Database(
    entities = [CallLogEntity::class, ActiveCallEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(CallTypeConverter::class)
abstract class CallLogDatabase : RoomDatabase() {
    abstract fun callLogDao(): CallLogDao

    companion object {
        @Volatile
        private var instance: CallLogDatabase? = null

        fun getInstance(context: Context): CallLogDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CallLogDatabase::class.java,
                    "wkr_call_log.db"   // parallel naming to wkr_pastoral.db
                ).build().also { instance = it }
            }
        }
    }
}