package za.co.jpsoft.winkerkreader.data.pastoral.model

enum class NoteCategory(val labelAf: String, val symbol: String) {
    HUISBESOEK("Huisbesoek",       "🏠"),
    TELEFOON("Telefoongesprek",     "📞"),
    WHATSAPP("WhatsApp",            "💬"),
    EPOS("E-pos",                   "✉️"),
    KERK("By kerk",                 "⛪"),
    GEBED("Gebed",                  "🙏"),
    KONSULTASIE("Konsultasie",      "🤝"),
    ANDER("Ander",                  "📝");

    companion object {
        fun fromStored(value: String): NoteCategory =
            entries.firstOrNull { it.name == value } ?: ANDER
    }
}