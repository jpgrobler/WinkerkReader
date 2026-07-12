package za.co.jpsoft.winkerkreader.data.pastoral.model

import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity

/**
 * UI model combining a pastoral reminder with live congregation member fields.
 * Member data is resolved outside Room via
 * [za.co.jpsoft.winkerkreader.data.pastoral.repository.MemberGuidResolver].
 */
data class ReminderWithMember(
    val reminder: FollowUpReminderEntity,
    val displayName: String,
    val cellphone: String?,
    val photoPath: String?
)
