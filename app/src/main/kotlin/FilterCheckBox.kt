package za.co.jpsoft.winkerkreader.data

import android.os.Parcel
import android.os.Parcelable
import java.io.Serializable

/**
 * Created by Pieter Grobler on 06/09/2017.
 */
class FilterCheckBox @JvmOverloads constructor(
    title: String = "",
    list: List<String>? = null,
    text1: String = "",
    text2: String = "",
    text3: String = "",
    count: Int = 1,
    checked: Boolean = false
) : Serializable {

    companion object {
        private const val DEFAULT_DESCRIPTION = "N/A"
    }

    private var _title: String = title
    private var _list: List<String>? = list
    private var _text1: String = text1
    private var _text2: String = text2
    private var _text3: String = text3
    private var _count: Int = count
    private var _checked: Boolean = checked

    var title: String
        get() = _title.ifEmpty { DEFAULT_DESCRIPTION }
        set(value) { _title = value }

    var list: List<String>?
        get() = _list
        set(value) { _list = value }

    var text1: String
        get() = _text1.ifEmpty { DEFAULT_DESCRIPTION }
        set(value) { _text1 = value }

    var text2: String
        get() = _text2.ifEmpty { DEFAULT_DESCRIPTION }
        set(value) { _text2 = value }

    var text3: String
        get() = _text3.ifEmpty { DEFAULT_DESCRIPTION }
        set(value) { _text3 = value }

    var count: Int
        get() = _count
        set(value) { _count = value }

    var checked: Boolean
        get() = _checked
        set(value) { _checked = value }
}