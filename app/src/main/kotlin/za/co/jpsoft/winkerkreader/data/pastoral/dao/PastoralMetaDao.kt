package za.co.jpsoft.winkerkreader.data.pastoral.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import za.co.jpsoft.winkerkreader.data.pastoral.entities.PastoralMetaEntity

@Dao
interface PastoralMetaDao {

    @Query("SELECT * FROM pastoral_meta WHERE id = 1 LIMIT 1")
    suspend fun get(): PastoralMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: PastoralMetaEntity)

    @Update
    suspend fun update(meta: PastoralMetaEntity)
}
