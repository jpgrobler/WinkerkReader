package za.co.jpsoft.winkerkreader.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Argief")
data class ArgiefEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id") val id: Long? = null,

    @ColumnInfo(name = "Tag") val tag: String? = null,
    @ColumnInfo(name = "ArchiveGUID") val archiveGUID: String? = null,
    @ColumnInfo(name = "Surname") val surname: String? = null,
    @ColumnInfo(name = "Name") val name: String? = null,
    @ColumnInfo(name = "MaidenName") val maidenName: String? = null,
    @ColumnInfo(name = "MemberStatus") val memberStatus: String? = null,
    @ColumnInfo(name = "CertificateStatus") val certificateStatus: String? = null,
    @ColumnInfo(name = "PreviousCongregation") val previousCongregation: String? = null,
    @ColumnInfo(name = "DateReceived") val dateReceived: String? = null,
    @ColumnInfo(name = "Comment") val comment: String? = null,
    @ColumnInfo(name = "Reason") val reason: String? = null,
    @ColumnInfo(name = "ResignationDetail") val resignationDetail: String? = null,
    @ColumnInfo(name = "DepartureTo") val departureTo: String? = null,
    @ColumnInfo(name = "DepartureDate") val departureDate: String? = null,
    @ColumnInfo(name = "DocCode") val docCode: String? = null,
    @ColumnInfo(name = "Document") val document: String? = null,
    @ColumnInfo(name = "OldAddress") val oldAddress: String? = null,
    @ColumnInfo(name = "NewAddress") val newAddress: String? = null,
    @ColumnInfo(name = "DateOfBirth") val dateOfBirth: String? = null,
    @ColumnInfo(name = "Gender") val gender: String? = null,
    @ColumnInfo(name = "MaritalStatus") val maritalStatus: String? = null,
    @ColumnInfo(name = "BaptismDate") val baptismDate: String? = null,
    @ColumnInfo(name = "BaptismMinister") val baptismMinister: String? = null,
    @ColumnInfo(name = "Father") val father: String? = null,
    @ColumnInfo(name = "Mother") val mother: String? = null,
    @ColumnInfo(name = "ConfessionDate") val confessionDate: String? = null,
    @ColumnInfo(name = "ConfessionMinister") val confessionMinister: String? = null,
    @ColumnInfo(name = "ConfessionRemark") val confessionRemark: String? = null,
    @ColumnInfo(name = "AcceptanceDate") val acceptanceDate: String? = null,
    @ColumnInfo(name = "ArchiveDate") val archiveDate: String? = null,
    @ColumnInfo(name = "ResignationRemark") val resignationRemark: String? = null,
    @ColumnInfo(name = "User") val user: String? = null,
    @ColumnInfo(name = "Gemeente") val gemeente: String? = null,
    @ColumnInfo(name = "Gemeente epos") val gemeenteEpos: String? = null
)