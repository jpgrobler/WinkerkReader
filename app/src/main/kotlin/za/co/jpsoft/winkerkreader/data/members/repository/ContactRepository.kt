package za.co.jpsoft.winkerkreader.data.members.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/**
 * Central repository for contact-related data, specifically WhatsApp availability.
 * Replaces the static sharing pattern in MainActivity.
 */
object ContactRepository {
    // Thread-safe set for internal storage
    private val _whatsappContacts = Collections.synchronizedSet(mutableSetOf<String>())

    // Observable state for the UI
    private val _contactsUpdateFlow = MutableStateFlow<Set<String>>(emptySet())
    val contactsUpdateFlow: StateFlow<Set<String>> = _contactsUpdateFlow.asStateFlow()

    /**
     * Updates the set of WhatsApp contacts.
     */
    fun updateWhatsAppContacts(newContacts: Collection<String>) {
        val copy = newContacts.toSet()
        _whatsappContacts.clear()
        _whatsappContacts.addAll(copy)
        _contactsUpdateFlow.value = copy
    }

    /**
     * Quick check if a specific number is in the WhatsApp list.
     */
    fun isWhatsAppContact(phoneNumber: String): Boolean {
        return _whatsappContacts.contains(phoneNumber)
    }

    /**
     * Gets a snapshot of the current contacts.
     */
    fun getWhatsAppContactsSnapshot(): Set<String> {
        return _whatsappContacts.toSet()
    }
}
