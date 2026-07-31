package za.co.jpsoft.winkerkreader.data.pastoral.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.pastoral.dao.FollowUpReminderDao
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContext
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.Utils.toLocalDateSafe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manages calendar sync for pastoral reminders.
 */
class CalendarSyncManager(
    private val calendarManager: CalendarManager,
    private val settingsManager: SettingsManager,
    private val reminderDao: FollowUpReminderDao,
    private val memberResolver: MemberGuidResolver
) {
    private val zoneId = ZoneId.systemDefault()
    private val TAG = "CalendarSyncMgr"

    /**
     * Sync a single reminder to the calendar.
     * @return true if a new event was created, false if skipped or failed.
     */
    suspend fun syncToCalendar(reminderId: String): Boolean = withContext(Dispatchers.IO) {
        if (!settingsManager.pastoral.pastoralCalendarSyncEnabled) {
            if (BuildConfig.DEBUG) Log.d(TAG, "sync disabled, skipping $reminderId")
            return@withContext false
        }
        val calendarId = settingsManager.pastoral.pastoralCalendarId ?: run {
            if (BuildConfig.DEBUG) Log.w(TAG, "no pastoral calendar selected, skipping $reminderId")
            return@withContext false
        }

        val reminder = reminderDao.getById(reminderId)
            ?: throw IllegalArgumentException("Reminder not found: $reminderId")

        if (reminder.calendarSynced && reminder.calendarEventId != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "already synced, eventId=${reminder.calendarEventId}")
            return@withContext false
        }
        if (reminder.status != "PENDING") {
            if (BuildConfig.DEBUG) Log.d(TAG, "reminder $reminderId is not PENDING, skipping")
            return@withContext false
        }

        val displayName = memberResolver.resolve(reminder.memberGuid)?.displayName
            ?: reminder.memberDisplayNameCache.orEmpty()

        val context = TemplateContext.from(reminder.contextJson)
        val (calendarTitle, calendarNote) = buildCalendarEventDetails(reminder, displayName, context)
        val params = buildCalendarEventParams(reminder)

        val eventId = calendarManager.addPastoralEvent(
            calendarId = calendarId,
            reminderId = reminderId,
            memberDisplayName = displayName,
            title = calendarTitle,
            note = calendarNote,
            startMillis = params.startMillis,
            endMillis = params.endMillis,
            isAllDay = params.isAllDay
        )

        if (eventId != null) {
            reminderDao.update(
                reminder.copy(
                    calendarEventId = eventId,
                    calendarSynced = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (BuildConfig.DEBUG) Log.i(TAG, "created event $eventId for reminder $reminderId")
            true
        } else {
            if (BuildConfig.DEBUG) Log.w(TAG, "addPastoralEvent returned null for $reminderId")
            false
        }
    }

    /**
     * Delete the calendar event if the reminder is synced.
     */
    fun deleteCalendarEventIfSynced(reminder: FollowUpReminderEntity) {
        if (reminder.calendarSynced && reminder.calendarEventId != null) {
            val deleted = calendarManager.deletePastoralEvent(reminder.calendarEventId)
            if (!deleted && BuildConfig.DEBUG) {
                Log.w(TAG, "Calendar event ${reminder.calendarEventId} not found on delete")
            }
        }
    }

    /**
     * Sync a list of reminders to the calendar (auto-sync on creation).
     */
    suspend fun syncRemindersToCalendar(reminders: List<FollowUpReminderEntity>) {
        if (!settingsManager.pastoral.pastoralCalendarSyncEnabled) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Auto-sync disabled, skipping calendar sync")
            return
        }
        val calendarId = settingsManager.pastoral.pastoralCalendarId
        if (calendarId == null) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Auto-sync enabled but no pastoral calendar selected")
            return
        }
        reminders.forEach { reminder ->
            try {
                withContext(NonCancellable) {
                    syncToCalendar(reminder.reminderId)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to auto-sync ${reminder.reminderId}", e)
            }
        }
    }

    // ------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------

    private data class CalendarEventParams(
        val startMillis: Long,
        val endMillis: Long,
        val isAllDay: Boolean
    )

    private fun buildCalendarEventParams(reminder: FollowUpReminderEntity): CalendarEventParams {
        val scheduleType = ScheduleType.fromStored(reminder.scheduleType)
        return when (scheduleType) {
            ScheduleType.DATE_ONLY -> {
                val endMillis = reminder.dueDateUtc + TimeUnit.DAYS.toMillis(1)
                CalendarEventParams(
                    startMillis = reminder.dueDateUtc,
                    endMillis = endMillis,
                    isAllDay = true
                )
            }
            ScheduleType.TIMED -> {
                val endMillis = reminder.dueEndUtc
                    ?: (reminder.dueDateUtc + TimeUnit.HOURS.toMillis(1))
                CalendarEventParams(
                    startMillis = reminder.dueDateUtc,
                    endMillis = endMillis,
                    isAllDay = false
                )
            }
        }
    }

    private fun buildCalendarEventDetails(
        reminder: FollowUpReminderEntity,
        displayName: String,
        context: TemplateContext
    ): Pair<String, String> {
        val symbol = reminder.symbol ?: ""
        val titleBase = reminder.title
        val noteBase = reminder.note ?: ""
        val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        val anchorDateStr = reminder.anchorDateUtc.toLocalDateSafe()
            ?.format(dateFormatter) ?: ""

        val title = when (reminder.templateId) {
            "sys-NA_STERF" -> {
                val deceasedName = context.getString("deceasedName") ?: ""
                val deceasedDate = context.getDate("deceasedDate")
                    ?.format(dateFormatter) ?: ""
                "$displayName - $symbol$deceasedName${if (deceasedDate.isNotEmpty()) " ($deceasedDate)" else ""}"
            }
            "sys-OPERASIE" -> {
                val hospital = context.getString("hospital") ?: ""
                "$displayName - $symbol $hospital ${if (anchorDateStr.isNotEmpty()) " ($anchorDateStr)" else ""}"
            }
            "sys-NUWE_LID" -> {
                val anchorDate = if (reminder.anchorDateUtc != null) {
                    Instant.ofEpochMilli(reminder.anchorDateUtc)
                        .atZone(zoneId).toLocalDate()
                        .format(dateFormatter)
                } else ""
                "$displayName - $symbol ${if (anchorDate.isNotEmpty()) " (Intrek datum: $anchorDate)" else ""}"
            }
            "sys-SIEKTE" -> {
                val illness = context.getString("illness") ?: ""
                "$symbol$displayName - $illness${if (anchorDateStr.isNotEmpty()) " (💊 $anchorDateStr)" else ""}"
            }
            "sys-TRAUMA" -> {
                val traumaType = context.getString("traumaType") ?: ""
                "$symbol$displayName - $traumaType${if (anchorDateStr.isNotEmpty()) " (⚠️ $anchorDateStr)" else ""}"
            }
            else -> "$symbol$displayName - $titleBase"
        }

        val note = buildString {
            append(titleBase)
            if (noteBase.isNotEmpty()) {
                append("\n\n").append(noteBase)
            }
            val contextLines = mutableListOf<String>()
            context.values.forEach { (key, value) ->
                if (value.isNotBlank()) {
                    when (key) {
                        "deceasedName" -> contextLines.add("Oorledene: $value")
                        "deceasedDob" -> contextLines.add("Geboortedatum: $value")
                        "deceasedDate" -> contextLines.add("Sterfdatum: $value")
                        "hospital" -> contextLines.add("Hospitaal: $value")
                        "illness" -> contextLines.add("Siekte: $value")
                        "traumaType" -> contextLines.add("Tipe trauma: $value")
                        "traumaDate" -> contextLines.add("Traumadatum: $value")
                    }
                }
            }
            if (contextLines.isNotEmpty()) {
                append("\n\n").append(contextLines.joinToString("\n"))
            }
        }
        return Pair(title, note)
    }
}