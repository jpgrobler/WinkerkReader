package za.co.jpsoft.winkerkreader.data.models

/**
 * Data model for the detailed view of a member.
 */
data class MemberDetailItem(
    val id: Int = 0,
    val guid: String = "",
    val familyHeadGuid: String = "",
    val name: String = "",
    val surname: String = "",
    val fullNames: String = "",
    val maidenName: String = "",
    val cellphone: String = "",
    val landline: String = "",
    val ward: String = "",
    val birthday: String = "",
    val age: Long = -1,
    val streetAddress: String = "",
    val postalAddress: String = "",
    val email: String = "",
    val profession: String = "",
    val employer: String = "",
    val gender: String = "",
    val marriageStatus: String = "",
    val memberStatus: String = "",
    val certificateStatus: String = "",
    // Milestones
    val baptismDate: String = "",
    val baptismDs: String = "",
    val confessionDate: String = "",
    val confessionDs: String = "",
    val marriageDate: String = "",
    val marriageYears: Long = -1,
    val deathDate: String = "",
    val gemeente: String = ""
)

/**
 * Data model for a family member in the detail view.
 */
data class FamilyMemberItem(
    val id: Int = 0,
    val name: String = "",
    val surname: String = "",
    val birthday: String = "",
    val age: Long = -1,
    val picturePath: String = "",
    val guid: String = "",
    val FamilyHead: String? = ""
)
