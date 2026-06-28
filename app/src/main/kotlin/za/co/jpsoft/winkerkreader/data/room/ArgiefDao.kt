package za.co.jpsoft.winkerkreader.data.room

import android.database.Cursor
import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface ArgiefDao {

    @RawQuery(observedEntities = [ArgiefEntity::class])
    fun queryRaw(query: SupportSQLiteQuery): Cursor
}