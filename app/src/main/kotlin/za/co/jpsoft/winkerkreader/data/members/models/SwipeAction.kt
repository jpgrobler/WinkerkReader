package za.co.jpsoft.winkerkreader.data.members.models

sealed class SwipeAction(val key: String, val labelAfrikaans: String) {
    object Niks : SwipeAction("Niks", "⛔ Niks")
    object VolgendeSortering : SwipeAction("VOLGENDE_SORTERING", "🔄 Volgende Sortering")
    object Besonderhede : SwipeAction("BESONDERHEDE", "ℹ️ Besonderhede")
    object Bel : SwipeAction("BEL", "📞 Bel")
    object WhatsApp : SwipeAction("WHATSAPP", "💬 WhatsApp")
    object Sms : SwipeAction("SMS", "✉️ SMS")
    object Epos : SwipeAction("EPOS", "📧 E-pos")
    object Nota : SwipeAction("Nota", "📝 Nota")
    object Herinnering : SwipeAction("HERINNERING", "❤️ Herinnering")

    companion object {
        val all: List<SwipeAction> = listOf(
            Niks, VolgendeSortering, Besonderhede, Bel, WhatsApp, Sms, Epos, Nota, Herinnering
        )

        fun labels(): Array<String> = all.map { it.labelAfrikaans }.toTypedArray()

        fun fromKey(key: String): SwipeAction = when (key) {
            "VOLGENDE_SORTERING" -> VolgendeSortering
            "BESONDERHEDE" -> Besonderhede
            "BEL" -> Bel
            "WHATSAPP" -> WhatsApp
            "SMS" -> Sms
            "EPOS" -> Epos
            "Nota" -> Nota
            "HERINNERING" -> Herinnering
            else -> Niks
        }
    }
}