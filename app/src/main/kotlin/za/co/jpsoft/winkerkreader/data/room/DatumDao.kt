package za.co.jpsoft.winkerkreader.data.room

import androidx.room.Dao
import androidx.room.Query

@Dao
interface DatumDao {

    @Query("SELECT DataDatum FROM Datum LIMIT 1")
    fun getDataDatum(): String?
}