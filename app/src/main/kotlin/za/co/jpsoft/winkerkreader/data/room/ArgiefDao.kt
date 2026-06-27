package za.co.jpsoft.winkerkreader.data.room

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import android.database.Cursor

@Dao
interface ArgiefDao {

    @RawQuery(observedEntities = [ArgiefEntity::class])
    fun queryRaw(query: SupportSQLiteQuery): Cursor
}