package za.co.jpsoft.winkerkreader.data

import android.os.Parcel
import android.os.Parcelable

/**
 * Created by Pieter Grobler on 15/09/2017.
 */
data class SmsList(
    var naam: String,
    var van: String,
    var nommer: String,
    var id: Int,
    var guid: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(naam)
        parcel.writeString(van)
        parcel.writeString(nommer)
        parcel.writeInt(id)
        parcel.writeString(guid)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<SmsList> {
            override fun createFromParcel(parcel: Parcel): SmsList = SmsList(parcel)
            override fun newArray(size: Int): Array<SmsList?> = arrayOfNulls(size)
        }
    }
}