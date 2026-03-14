package za.co.jpsoft.winkerkreader.data

import android.os.Parcel
import android.os.Parcelable

/**
 * Created by Pieter Grobler on 12/09/2017.
 */
data class FilterBox(
    var title: String = "",
    var text1: String = "",
    var text2: String = "",
    var text3: String = "",
    var checked: Boolean = false
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title)
        parcel.writeString(text1)
        parcel.writeString(text2)
        parcel.writeString(text3)
        parcel.writeByte(if (checked) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<FilterBox> {
            override fun createFromParcel(parcel: Parcel): FilterBox = FilterBox(parcel)
            override fun newArray(size: Int): Array<FilterBox?> = arrayOfNulls(size)
        }
    }
}