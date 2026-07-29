package za.co.jpsoft.winkerkreader.data.pastoral.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.pastoral.dao.FollowUpReminderDao
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContext
import za.co.jpsoft.winkerkreader.utils.PastoralTaskScriptManager
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.Utils.toLocalDateSafe

/**
 * Manages Google Tasks sync for pastoral reminders.
 */
class GoogleTaskSyncManager(
    private val settingsManager: SettingsManager,
    private val reminderDao: FollowUpReminderDao,
    private val memberResolver: MemberGuidResolver
) {
    private val TAG = "GoogleTaskSyncMgr"

    /**
     * Sync a single reminder to Google Tasks via Apps Script.
     */
    suspend fun syncToGoogleTasksViaScript(reminderId: String): Boolean =
        withContext(Dispatchers.IO) {
            val url = settingsManager.tasksScriptUrl ?: return@withContext false
            val secret = settingsManager.tasksScriptSecret ?: return@withContext false

            val reminder = reminderDao.getById(reminderId)
                ?: throw IllegalArgumentException("Reminder not found: $reminderId")

            if (url.isNullOrBlank() || secret.isNullOrBlank()) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Script not configured")
                return@withContext false
            }
            if (reminder.googleTaskSynced && reminder.googleTaskId != null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Already synced: ${reminder.googleTaskId}")
                return@withContext false
            }

            val displayName = memberResolver.resolve(reminder.memberGuid)?.displayName
                ?: reminder.memberDisplayNameCache.orEmpty()

            val noteDetails = buildString {
                append("Lidmaat: $displayName")
                if (!reminder.memberSurname.isNullOrBlank()) {
                    append("\nVan: ${reminder.memberSurname}")
                }
                if (!reminder.memberGivenName.isNullOrBlank()) {
                    append("\nNoemnaam: ${reminder.memberGivenName}")
                }
                append("\nHerinnering: ${reminder.title}")
                if (!reminder.note.isNullOrBlank()) {
                    append("\nNota: ${reminder.note}")
                }
                val dueDateStr = reminder.dueDateUtc.toLocalDateSafe()?.toString() ?: "Onbekend"
                append("\nSperdatum: $dueDateStr")
                val context = TemplateContext.from(reminder.contextJson)
                context.values.forEach { (key, value) ->
                    if (value.isNotBlank()) {
                        append("\n$key: $value")
                    }
                }
            }
            val listId = settingsManager.googleTasksListId

            val taskId = PastoralTaskScriptManager.pushTask(
                scriptUrl = url,
                secret = secret,
                title = "$displayName — ${reminder.title}",
                notes = noteDetails,
                dueDateUtc = reminder.dueDateUtc,
                listId = listId
            )

            if (taskId != null) {
                reminderDao.update(
                    reminder.copy(
                        googleTaskId = taskId,
                        googleTaskSynced = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                if (BuildConfig.DEBUG) Log.i(TAG, "Google Task created $taskId")
                true
            } else {
                false
            }
        }

    /**
     * Delete the Google Task if it was synced.
     */
    fun deleteGoogleTaskIfSynced(reminder: FollowUpReminderEntity) {
        if (!reminder.googleTaskSynced || reminder.googleTaskId == null) return
        val url = settingsManager.tasksScriptUrl ?: return
        val secret = settingsManager.tasksScriptSecret ?: return
        val deleted = PastoralTaskScriptManager.deleteTask(url, secret, reminder.googleTaskId)
        if (BuildConfig.DEBUG) Log.d(TAG, "Google Task delete ${reminder.googleTaskId}: $deleted")
    }

    /**
     * Mark the Google Task as completed.
     */
    fun completeGoogleTaskIfSynced(reminder: FollowUpReminderEntity) {
        if (!reminder.googleTaskSynced || reminder.googleTaskId == null) return
        val url = settingsManager.tasksScriptUrl ?: return
        val secret = settingsManager.tasksScriptSecret ?: return
        val done = PastoralTaskScriptManager.completeTask(url, secret, reminder.googleTaskId)
        if (BuildConfig.DEBUG) Log.d(TAG, "Google Task complete ${reminder.googleTaskId}: $done")
    }

    /**
     * Sync multiple reminders to Google Tasks (auto-sync on creation).
     */
    suspend fun syncRemindersToGoogleTasks(reminders: List<FollowUpReminderEntity>) {
        if (settingsManager.googleTasksMode() != SettingsManager.GoogleTasksMode.API) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Google Tasks auto-sync disabled (mode != API)")
            return
        }
        if (!settingsManager.isTasksScriptConfigured()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Google Tasks script not configured – skipping")
            return
        }
        reminders.forEach { reminder ->
            try {
                withContext(NonCancellable) {
                    syncToGoogleTasksViaScript(reminder.reminderId)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Auto sync failed for ${reminder.reminderId}", e)
            }
        }
    }
}