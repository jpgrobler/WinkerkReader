package za.co.jpsoft.winkerkreader.utils.messaging

import za.co.jpsoft.winkerkreader.data.members.models.MemberItem
import za.co.jpsoft.winkerkreader.utils.messaging.MessageComposer.personalize

/**
 * Single source of truth for turning a message template into the final text
 * that gets sent to a specific member.
 *
 * Before this existed, the exact same `<<<naam>>>` replacement was copy-pasted
 * independently at every send site — once in the SMS path
 * (VerjaarSmsActivity.sendSmsToMemberSuspend) and again in the WhatsApp path
 * (VerjaarSmsActivity's popup menu handler). Every delivery channel (SMS,
 * WhatsApp, and any future channel) should call [personalize] instead of
 * doing its own `.replace(...)` — that way there's exactly one place to fix
 * or extend placeholder logic, no matter how many channels exist.
 *
 * This deliberately does NOT know anything about *how* a message is sent
 * (SmsManager, WhatsApp Intents, email, etc.) — that's the delivery
 * channel's job. MessageComposer only ever answers "what text should this
 * member receive?", which keeps generation and delivery decoupled.
 */
object MessageComposer {

    // Add new placeholders here as they're needed — e.g. "<<<gemeente>>>",
    // "<<<ouderdom>>>" — each as its own named constant, substituted in
    // [personalize] below. Keeping them as constants (rather than inline
    // strings scattered at call sites) makes every supported placeholder
    // discoverable in one place.
    private const val PLACEHOLDER_NAAM = "<<<naam>>>"

    /**
     * Replace every supported placeholder in [template] with the corresponding
     * value from [member]. Safe to call with a template that contains none,
     * some, or all supported placeholders — anything not present is simply
     * left untouched.
     *
     * @param template the raw message template, as typed by the user (may
     *   contain zero or more placeholders)
     * @param member the member this message is being personalized for
     * @return the final text ready to hand to any delivery channel
     */
    fun personalize(template: String, member: MemberItem): String {
        return template.replace(PLACEHOLDER_NAAM, member.name)
    }
}