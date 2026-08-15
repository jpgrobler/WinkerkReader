package za.co.jpsoft.winkerkreader.ui.controllers

import android.util.Log
import androidx.lifecycle.lifecycleScope
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.pastoral.dao.FollowUpReminderDao
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralNoteRepository
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.MainViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.ReminderEventBus

/**
 * Manages the badge count displayed in the "Bediening" menu item and
 * keeps the member list adapter informed about which members have pending reminders.
 */
class PastoralReminderBadgeController(
    private val activity: MainActivity,
    private val followUpReminderDao: FollowUpReminderDao,
    private val memberViewModel: MemberViewModel,
    private val mainViewModel: MainViewModel,
    private val pastoralNoteRepository: PastoralNoteRepository
) {

    var badgeCount: Int = 0
        private set

    fun setup() {
        observePendingCount()
        loadPendingReminderGuids()
        observeRefreshEvents()
    }

    // ------------------------------------------------------------------------
    // Badge count (from MainViewModel)
    // ------------------------------------------------------------------------

    private fun observePendingCount() {
        activity.lifecycleScope.launch {
            mainViewModel.pendingReminderCount.collect { count ->
                badgeCount = count
                activity.invalidateOptionsMenu()
            }
        }
    }

    // ------------------------------------------------------------------------
    // Member GUIDs with pending reminders (for the list adapter)
    // ------------------------------------------------------------------------

    private fun loadPendingReminderGuids() {
        if (BuildConfig.DEBUG) Log.d("PastoralBadgeCtrl", "loadPendingReminderGuids called")
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Load reminder GUIDs
                val allPending = followUpReminderDao.getAllPending()
                val reminderGuids = allPending.mapNotNull { reminder ->
                    var guid = reminder.memberGuid?.takeIf { it.isNotBlank() }
                    if (guid == null) {
                        val name = reminder.memberDisplayNameCache
                        if (!name.isNullOrBlank()) {
                            guid = resolveMemberGuidByName(name)
                        }
                    }
                    guid
                }.distinct()

                // Load note GUIDs
                val noteGuids = pastoralNoteRepository.getMemberGuidsWithNotes().toSet()

                withContext(Dispatchers.Main) {
                    memberViewModel.updatePendingRemindersSet(reminderGuids.toSet())
                    // Update the adapter with note GUIDs – we need to get the adapter from the activity
                    activity.binding.lidmaatList.adapter?.let { adapter ->
                        if (adapter is MemberListAdapter) {
                            adapter.updatePendingNoteGuids(noteGuids)
                        }
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("PastoralBadgeCtrl", "Failed to load pending guids", e)
            }

        }
    }

    private suspend fun getMemberGuidsWithNotes(): Set<String> {
        return withContext(Dispatchers.IO) {
            pastoralNoteRepository.getMemberGuidsWithNotes().toSet()
        }
    }
    /**
     * Resolves a member GUID by matching the display name (format "FirstName LastName").
     * Returns null if no match is found.
     */
    private suspend fun resolveMemberGuidByName(name: String): String? =
        withContext(Dispatchers.IO) {
            val parts = name.split(' ')
            val firstName = parts.firstOrNull() ?: ""
            val lastName = parts.drop(1).joinToString(" ")
            if (firstName.isBlank() && lastName.isBlank()) return@withContext null

            val query = """
                SELECT MemberGUID FROM Members 
                WHERE Noemnaam LIKE ? AND Van LIKE ?
                LIMIT 1
            """.trimIndent()
            val selectionArgs = arrayOf("%$firstName%", "%$lastName%")

            try {
                val cursor = activity.contentResolver.query(
                    winkerkEntry.CONTENT_URI,
                    arrayOf(winkerkEntry.LIDMATE_LIDMAATGUID),
                    query,
                    selectionArgs,
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) return@withContext it.getString(0)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(
                    "PastoralBadgeCtrl",
                    "Error resolving member GUID by name",
                    e
                )
            }
            null
        }

    // ------------------------------------------------------------------------
    // Refresh on changes (via ReminderEventBus)
    // ------------------------------------------------------------------------

    private fun observeRefreshEvents() {
        activity.lifecycleScope.launch {
            ReminderEventBus.refreshPending.collect {
                loadPendingReminderGuids()
            }
        }
    }

    fun refresh() {
        // 1. Force the ViewModel to recalculate the total count from the DB
        mainViewModel.refreshPendingReminderCount()
        // 2. Reload the per‑member GUIDs for the list icons
        loadPendingReminderGuids()
    }
}