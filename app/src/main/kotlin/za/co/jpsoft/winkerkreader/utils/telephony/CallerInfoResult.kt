package za.co.jpsoft.winkerkreader.utils.telephony

/**
 * Represents the result of resolving a caller (phone number) against
 * the app's internal databases.
 */
sealed class CallerInfoResult {

    /** No matching member or contact found. */
    object Unknown : CallerInfoResult()

    /**
     * A known member from the congregation database.
     * @param name       Full display name (e.g., "Jan Botha")
     * @param guid       Unique member GUID (for further lookups)
     * @param surname    (optional) Surname
     * @param firstName  (optional) First name
     * @param phone      (optional) Primary phone number
     * @param memberType (optional) e.g., "Lidmaat", "Kind", "Buite‑lid"
     * @param gemeente   (optional) Congregation name
     */
    data class Member(
        val name: String,
        val guid: String,
        val surname: String? = null,
        val firstName: String? = null,
        val phone: String? = null,
        val memberType: String? = null,
        val gemeente: String? = null,
        val familyHeadGuid: String? = null
    ) : CallerInfoResult()

    /**
     * A contact from the device's contacts list.
     * @param name        Display name of the contact
     * @param phoneNumber The phone number that was matched
     */
    data class Contact(
        val name: String,
        val phoneNumber: String,
    ) : CallerInfoResult()
    data class MultipleMembers(val members: List<Member>) : CallerInfoResult()
}