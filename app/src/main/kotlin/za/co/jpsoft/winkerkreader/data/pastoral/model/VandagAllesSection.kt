package za.co.jpsoft.winkerkreader.data.pastoral.model

sealed class VandagAllesSection(open val title: String, open val items: List<VandagAllesItem>) {
    data class Celebrations(override val title: String, override val items: List<VandagAllesItem>) :
        VandagAllesSection(title, items)

    data class DueToday(override val title: String, override val items: List<VandagAllesItem>) :
        VandagAllesSection(title, items)

    data class Overdue(override val title: String, override val items: List<VandagAllesItem>) :
        VandagAllesSection(title, items)
}