package za.co.jpsoft.winkerkreader.data.pastoral.model

/**
 * Combined dashboard data for the Bediening "Vandag" tab.
 */
data class VandagDashboard(
    val dueToday: List<ReminderWithMember>,
    val overdue: List<ReminderWithMember>,
    val todayCount: Int,
    val overdueCount: Int
)
