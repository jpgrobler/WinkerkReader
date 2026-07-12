package za.co.jpsoft.winkerkreader.ui.models

import za.co.jpsoft.winkerkreader.data.models.FilterBox

sealed class MainQueryMode {
    data object Search : MainQueryMode()
    data class Filter(val filters: ArrayList<FilterBox>) : MainQueryMode()
    data object Address : MainQueryMode()
    data object Family : MainQueryMode()
    data object Wedding : MainQueryMode()
    data object Age : MainQueryMode()
    data object Surname : MainQueryMode()
    data object Birthday : MainQueryMode()
    data object Ward : MainQueryMode()
    data class Raw(val layout: String) : MainQueryMode()
}
