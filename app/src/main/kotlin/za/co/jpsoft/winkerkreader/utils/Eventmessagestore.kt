package za.co.jpsoft.winkerkreader.utils

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persists and loads the SMS/greeting message template for each event type.
 *
 * Extracted from VerjaarSmsActivity where the same key-mapping `when` block
 * appeared in three separate methods: handleEventTypeChange(), saveCurrentMessage(),
 * and setMessageForEventType(). Single source of truth now.
 *
 * Usage in VerjaarSmsActivity:
 *
 *   // Load on event type change:
 *   binding.boodskap.setText(EventMessageStore.load(prefs, keuse))
 *
 *   // Save on pause / debounce:
 *   EventMessageStore.save(prefs, keuse, binding.boodskap.text.toString())
 */
object EventMessageStore {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the stored message for [eventType], falling back to the default
     * greeting if none has been saved yet.
     */
    fun load(prefs: SharedPreferences, eventType: String): String =
        prefs.getString(keyFor(eventType), defaultFor(eventType)) ?: defaultFor(eventType)

    /**
     * Persists [message] for [eventType].
     */
    fun save(prefs: SharedPreferences, eventType: String, message: String) {
        prefs.edit { putString(keyFor(eventType), message) }
    }

    /**
     * Returns the SharedPreferences key for [eventType].
     * Exposed so callers can observe specific keys if needed.
     */
    fun keyFor(eventType: String): String = when (eventType) {
        "Doop" -> "DoopBoodskap"
        "Huwelik" -> "HuwelikBoodskap"
        "Bely" -> "BelyBoodskap"
        else -> "VerjaarBoodskap"   // "Verjaar" and any unknown type
    }

    /**
     * Returns the factory-default greeting for [eventType].
     * These are the hardcoded defaults that were previously scattered across
     * the three when-blocks inside VerjaarSmsActivity.
     */
    fun defaultFor(eventType: String): String = when (eventType) {
        "Doop" ->
            "<<<naam>>>\nBaie geluk met jou doopherdenking!\n" +
                    "Mag die Here se genade jou daagliks vervul!\nGroete Ds "

        "Huwelik" ->
            "<<<naam>>>\nBaie geluk met jou huweliksherdenking!\n" +
                    "Mag die Here se genade jou daagliks vervul!\nGroete Ds "

        "Bely" ->
            "<<<naam>>>\nBaie geluk met jou herdenking van jou belydenis van geloof!\n" +
                    "Mag die Here se genade jou daagliks vervul!\nGroete Ds "

        else ->  // "Verjaar"
            "<<<naam>>>\nBaie geluk met jou verjaarsdag!\n" +
                    "Mag die Here se genade jou daagliks vervul!\nGroete Ds "
    }
}