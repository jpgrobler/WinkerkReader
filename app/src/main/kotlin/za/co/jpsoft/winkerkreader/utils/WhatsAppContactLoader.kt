package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.repositories.ContactRepository

/**
 * Utility to load WhatsApp profile numbers from the system contacts provider.
 * Pushes results into [ContactRepository] — no direct Activity references.
 */
object WhatsAppContactLoader {
    private const val TAG = "WhatsAppContactLoader"

    @Volatile
    private var isJobRunning = false

    /**
     * Loads WhatsApp contacts in a background coroutine.
     * Uses [LifecycleCoroutineScope] obtained from the calling Activity so the
     * job is automatically cancelled if the Activity is destroyed.
     *
     * The [context] is used only for the ContentResolver query; applicationContext
     * is extracted internally to prevent leaking an Activity reference.
     */
    fun loadWhatsAppContactsAtomic(context: Context, lifecycleScope: LifecycleCoroutineScope) {
        if (isJobRunning) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Contact load job is already running. Skipping.")
            return
        }
        val appContext = context.applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            isJobRunning = true
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "Starting WhatsApp contact load…")
                val contactList = queryWhatsAppContacts(appContext)
                ContactRepository.updateWhatsAppContacts(contactList)
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "Loaded ${contactList.size} WhatsApp contacts into Repository"
                )
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "Loaded ${contactList.size} WhatsApp contacts: $contactList"
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error loading WhatsApp contacts", e)
            } finally {
                isJobRunning = false
            }
        }
    }

    private fun queryWhatsAppContacts(context: Context): List<String> {
        val contactList = mutableListOf<String>()
        val cursor: Cursor? = try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.DATA1),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf("vnd.android.cursor.item/vnd.com.whatsapp.profile"),
                null
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Query failed", e)
            null
        }

        cursor?.use {
            val dataIndex = it.getColumnIndex(ContactsContract.Data.DATA1)
            if (dataIndex != -1) {
                while (it.moveToNext()) {
                    val contact = it.getString(dataIndex)
                    if (!contact.isNullOrEmpty()) {
                        // 🔧 Format the number to match the adapter
                        val formatted = Utils.fixphonenumber(contact) ?: contact
                        contactList.add(formatted)
                    }
                }
            }
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${contactList.size} WhatsApp contacts")
        return contactList
    }

    /**
     * Resets the running state. Call on app exit or if a hard reset is needed.
     */
    fun reset() {
        isJobRunning = false
    }
}
