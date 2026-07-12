package za.co.jpsoft.winkerkreader.data.pastoral.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pastoral_meta")
data class PastoralMetaEntity(
    @PrimaryKey val id: Int = 1,
    val schemaVersion: Int = 1,
    val deviceId: String,
    val congregationName: String?,
    val lastBackupUtc: Long?
)
