package za.co.jpsoft.winkerkreader.data.pastoral.model

sealed class VandagAllesItem {
    data class Celebration(
        val id: String,
        val name: String,
        val eventType: CelebrationType,
        val detailText: String,
        val memberGuid: String,
        val cellphone: String?
    ) : VandagAllesItem()

    data class Reminder(
        val reminderWithMember: ReminderWithMember
    ) : VandagAllesItem()

    enum class CelebrationType(val labelAf: String, val emoji: String) {
        BIRTHDAY("Verjaarsdag", "🎂"),
        BAPTISM("Doop", "💧"),
        WEDDING("Huwelik", "💍"),
        DEATH("Sterfgeval", "✝️")
    }
}