package za.co.jpsoft.winkerkreader.ui.components

import android.os.Parcel
import android.os.Parcelable

data class SearchCheckBox(
    var columnName: String,
    var columnValue: String,
    var description: String,
    var isChecked: Boolean
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(columnName)
        parcel.writeString(columnValue)
        parcel.writeString(description)
        parcel.writeByte(if (isChecked) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<SearchCheckBox> {
            override fun createFromParcel(parcel: Parcel): SearchCheckBox = SearchCheckBox(parcel)
            override fun newArray(size: Int): Array<SearchCheckBox?> = arrayOfNulls(size)
        }
    }
}