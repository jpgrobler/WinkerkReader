package za.co.jpsoft.winkerkreader.data.pastoral.model

/**
 * Congregation member fields resolved from [za.co.jpsoft.winkerkreader.data.pastoral.repository.MemberGuidResolver].
 */
data class MemberDisplay(
    val guid: String,
    val displayName: String,      // e.g. "Pieter Abrie" (full name for display)
    val surname: String? = null,  // e.g. "Abrie"
    val givenName: String? = null,// e.g. "Pieter" (or "Piet" if noemnaam)
    val cellphone: String? = null,
    val photoPath: String? = null,
    val familyHeadGuid: String? = null,
    val isArchived: Boolean = false
)
