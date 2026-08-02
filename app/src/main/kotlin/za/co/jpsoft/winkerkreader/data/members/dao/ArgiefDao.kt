package za.co.jpsoft.winkerkreader.data.members.dao

import android.database.Cursor
import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import za.co.jpsoft.winkerkreader.data.members.entities.ArgiefEntity

@Dao
interface ArgiefDao {

    @RawQuery(observedEntities = [ArgiefEntity::class])
    fun queryRaw(query: SupportSQLiteQuery): Cursor
}