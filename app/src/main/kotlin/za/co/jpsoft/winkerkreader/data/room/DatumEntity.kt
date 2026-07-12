package za.co.jpsoft.winkerkreader.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Datum")
data class DatumEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id") val id: Long? = null,

    @ColumnInfo(name = "DataDatum") val dataDatum: String? = null
)