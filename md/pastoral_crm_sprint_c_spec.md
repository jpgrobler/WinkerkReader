# Pastoral CRM — Sprint C: Notifications Design

> **Status:** Ready for implementation  
> **Depends on:** Sprint A (data layer) + Sprint B (calendar integration)  
> **As-built flows:** [`architecture.md`](architecture.md) §8.3 (Notifications)

---

## Task table

| # | Task | Files |
|---|------|-------|
| C0 | Add `getPendingDue()` to DAO | `FollowUpReminderDao.kt` |
| C1 | `FollowUpReminderWorker` | `workers/FollowUpReminderWorker.kt` |
| C2 | `scheduleFollowUpReminders()` | `utils/WorkManagerHelper.kt` |
| C3 | Wire in `MainActivity` | `ui/activities/MainActivity.kt` |
| C4a | `PastoralNotificationHelper` | `utils/PastoralNotificationHelper.kt` |
| C4b | `PastoralReminderActionReceiver` | `receivers/PastoralReminderActionReceiver.kt` |

**Implement in order: C0 → C4a → C4b → C1 → C2 → C3**

---

## Observations from existing code

| Pattern | Decision |
|---------|----------|
| All workers are `CoroutineWorker` via `PeriodicWorkRequestBuilder` with 15-min flex | Follow same pattern for `FollowUpReminderWorker` |
| `CHANNEL_ID = "winkerkReaderServiceChannel"` on `MainActivity`, channel name "Oproep" | Create a **dedicated** pastoral channel — "Bediening" — so the pastor can silence call-log alerts separately from pastoral reminders |
| `setupAlarms()` calls three `setup*Work()` functions | Add `setupFollowUpReminderWork()` as a fourth call |
| `createNotificationChannel()` called from `setupPermissions()` | Call `PastoralNotificationHelper.ensureChannel()` from the same place |
| Workers call their own `WorkManager.getInstance()` — no injection | Follow same pattern |

---

## C0 — FollowUpReminderDao: add one-shot suspend query

The existing `observeDueToday()` is a `Flow` — usable by the UI but not by a worker that needs a one-shot result. Add:

```kotlin
// In FollowUpReminderDao.kt

/**
 * One-shot query for the worker — returns all PENDING reminders due today or earlier
 * that are not actively snoozed. Mirrors [observeDueToday] logic without the Flow wrapper.
 */
@Query(
    """
    SELECT * FROM follow_up_reminders
    WHERE status = :pendingStatus
      AND dueDateUtc <= :endOfDayUtc
      AND (snoozedUntilUtc IS NULL OR snoozedUntilUtc <= :nowUtc)
    ORDER BY dueDateUtc ASC
    """
)
suspend fun getPendingDue(
    endOfDayUtc: Long,
    nowUtc: Long,
    pendingStatus: String = ReminderStatus.PENDING.name
): List<FollowUpReminderEntity>
```

This covers both today's reminders and all overdue ones in a single query, which is exactly what the worker needs.

---

## C4a — PastoralNotificationHelper

New file: `utils/PastoralNotificationHelper.kt`

Responsibilities:
- Create and own the "Bediening" notification channel
- Build and post one notification per reminder
- Provide a stable notification ID per `reminderId`
- Wire the three action `PendingIntent`s

```kotlin
package za.co.jpsoft.winkerkreader.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.receivers.PastoralReminderActionReceiver
import za.co.jpsoft.winkerkreader.ui.activities.BedieningActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object PastoralNotificationHelper {

    const val CHANNEL_ID = "pastoral_bediening"
    private const val TAG = "PastoralNotifHelper"

    // -------------------------------------------------------------------------
    // Channel setup — call from MainActivity.setupPermissions()
    // -------------------------------------------------------------------------

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.bediening_title),   // "Bediening"
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.bediening_channel_description)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    // -------------------------------------------------------------------------
    // Post a notification for a single reminder
    // -------------------------------------------------------------------------

    fun postReminderNotification(
        context: Context,
        reminder: FollowUpReminderEntity,
        memberDisplayName: String
    ) {
        val notifId = notificationId(reminder.reminderId)

        val contentText = buildContentText(context, reminder, memberDisplayName)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pastoral)  // add this drawable in Sprint G
            .setContentTitle(reminder.title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setAutoCancel(false)   // stays until actioned — pastor must consciously dismiss
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent(context, reminder.reminderId, notifId))
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.herinnering_voltooi),   // "Voltooi"
                completePendingIntent(context, reminder.reminderId, notifId)
            )
            .addAction(
                R.drawable.ic_snooze,
                context.getString(R.string.herinnering_uitstel),   // "Uitstel 1 dag"
                snoozePendingIntent(context, reminder.reminderId, notifId)
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
            Log.d(TAG, "Posted notification $notifId for reminder ${reminder.reminderId}")
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted (Android 13+)
            Log.w(TAG, "POST_NOTIFICATIONS permission missing — notification not shown", e)
        }
    }

    fun cancelNotification(context: Context, reminderId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    // -------------------------------------------------------------------------
    // Stable notification ID
    // -------------------------------------------------------------------------

    fun notificationId(reminderId: String): Int = abs(reminderId.hashCode())

    // -------------------------------------------------------------------------
    // PendingIntents
    // -------------------------------------------------------------------------

    /** Tapping the notification body opens BedieningActivity. */
    private fun openPendingIntent(context: Context, reminderId: String, notifId: Int): PendingIntent {
        val intent = Intent(context, BedieningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(PastoralReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(PastoralReminderActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        return PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** "Voltooi" action button. */
    private fun completePendingIntent(context: Context, reminderId: String, notifId: Int): PendingIntent {
        val intent = Intent(context, PastoralReminderActionReceiver::class.java).apply {
            action = PastoralReminderActionReceiver.ACTION_COMPLETE
            putExtra(PastoralReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(PastoralReminderActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        return PendingIntent.getBroadcast(
            context,
            // Request code must be unique per (reminder × action) to avoid PendingIntent recycling
            notifId + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** "Uitstel 1 dag" action button. */
    private fun snoozePendingIntent(context: Context, reminderId: String, notifId: Int): PendingIntent {
        val intent = Intent(context, PastoralReminderActionReceiver::class.java).apply {
            action = PastoralReminderActionReceiver.ACTION_SNOOZE_1_DAY
            putExtra(PastoralReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(PastoralReminderActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        return PendingIntent.getBroadcast(
            context,
            notifId + 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // -------------------------------------------------------------------------
    // Content text helpers
    // -------------------------------------------------------------------------

    private fun buildContentText(
        context: Context,
        reminder: FollowUpReminderEntity,
        memberDisplayName: String
    ): String {
        val scheduleType = ScheduleType.fromStored(reminder.scheduleType)
        return when {
            // Overdue
            reminder.dueDateUtc < todayStartMillis() ->
                "$memberDisplayName · ${formatOverdueLabel(context, reminder.dueDateUtc)}"
            // Timed — show scheduled time
            scheduleType == ScheduleType.TIMED ->
                "$memberDisplayName · ${formatTime(reminder.dueDateUtc)}"
            // Date-only due today
            else ->
                memberDisplayName
        }
    }

    private fun formatOverdueLabel(context: Context, dueDateUtc: Long): String {
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        val date = Instant.ofEpochMilli(dueDateUtc)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return context.getString(R.string.bediening_agterstallig) + " (${date.format(formatter)})"
    }

    private fun formatTime(dueDateUtc: Long): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        return Instant.ofEpochMilli(dueDateUtc)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(formatter)
    }

    private fun todayStartMillis(): Long {
        val today = java.time.LocalDate.now(ZoneId.systemDefault())
        return today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
```

**Drawables needed (Sprint G can add proper icons; use placeholder for now):**

| Resource | Used for |
|----------|----------|
| `ic_notification_pastoral` | Small icon in status bar |
| `ic_check` | Voltooi action icon |
| `ic_snooze` | Uitstel action icon |

Use `R.drawable.ic_check_circle` / `R.drawable.ic_alarm` if already in the project, otherwise add simple vector drawables.

**Strings to add to `strings.xml`:**

```xml
<string name="bediening_channel_description">Herinnerings vir pastorale opvolg</string>
```

---

## C4b — PastoralReminderActionReceiver

New file: `receivers/PastoralReminderActionReceiver.kt`

This is a `BroadcastReceiver`. Since it needs to call `suspend` repository functions, it uses `goAsync()` to hold the receiver alive while a coroutine completes.

```kotlin
package za.co.jpsoft.winkerkreader.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import za.co.jpsoft.winkerkreader.utils.PastoralNotificationHelper
import java.time.LocalDateTime

class PastoralReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: run {
            Log.w(TAG, "onReceive: missing reminderId, ignoring")
            return
        }
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            try {
                val repository = PastoralReminderRepository.create(context.applicationContext)
                when (intent.action) {
                    ACTION_COMPLETE -> {
                        repository.completeReminder(reminderId)
                        Log.d(TAG, "Reminder $reminderId marked complete via notification")
                    }
                    ACTION_SNOOZE_1_DAY -> {
                        val until = LocalDateTime.now().plusDays(1)
                            .withHour(8).withMinute(0).withSecond(0).withNano(0)
                        repository.snoozeReminder(reminderId, until)
                        Log.d(TAG, "Reminder $reminderId snoozed to $until via notification")
                    }
                    else -> Log.w(TAG, "Unknown action: ${intent.action}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling reminder action for $reminderId", e)
            } finally {
                // Always cancel the notification and release goAsync
                if (notifId != -1) {
                    PastoralNotificationHelper.cancelNotification(context, reminderId)
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PastoralActionReceiver"

        const val ACTION_COMPLETE      = "za.co.jpsoft.winkerkreader.ACTION_PASTORAL_COMPLETE"
        const val ACTION_SNOOZE_1_DAY  = "za.co.jpsoft.winkerkreader.ACTION_PASTORAL_SNOOZE"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_NOTIF_ID    = "extra_notif_id"
    }
}
```

**Snooze time design:** Snoozing from a notification always snoozes to 08:00 the following day — a deliberate choice. If the pastor dismisses at 11 PM, they don't want a reminder at 11 PM tomorrow. The UI (Sprint D) will allow a custom snooze time.

**Register in `AndroidManifest.xml`:**

```xml
<receiver
    android:name=".receivers.PastoralReminderActionReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="za.co.jpsoft.winkerkreader.ACTION_PASTORAL_COMPLETE" />
        <action android:name="za.co.jpsoft.winkerkreader.ACTION_PASTORAL_SNOOZE" />
    </intent-filter>
</receiver>
```

---

## C1 — FollowUpReminderWorker

New file: `workers/FollowUpReminderWorker.kt`

Replaces the empty stub. Extends `CoroutineWorker` to match the existing codebase pattern.

```kotlin
package za.co.jpsoft.winkerkreader.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.repository.CongregationMemberGuidResolver
import za.co.jpsoft.winkerkreader.utils.PastoralNotificationHelper
import java.time.LocalDate
import java.time.ZoneId

class FollowUpReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "FollowUpReminderWorker started")

        val zoneId    = ZoneId.systemDefault()
        val nowUtc    = System.currentTimeMillis()
        val today     = LocalDate.now(zoneId)
        val startOfTodayUtc = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDayUtc     = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

        return try {
            val database    = PastoralDatabase.getInstance(applicationContext)
            val reminderDao = database.followUpReminderDao()
            val resolver    = CongregationMemberGuidResolver(applicationContext)

            // Single query: all PENDING reminders due today-or-earlier, not snoozed
            val dueReminders = reminderDao.getPendingDue(endOfDayUtc, nowUtc)
            Log.d(TAG, "Found ${dueReminders.size} due reminders")

            var notified = 0
            dueReminders.forEach { reminder ->
                val scheduleType = ScheduleType.fromStored(reminder.scheduleType)

                val shouldNotify = when (scheduleType) {
                    ScheduleType.DATE_ONLY -> {
                        // Only notify once per calendar day
                        val lastNotified = reminder.lastNotifiedDateUtc
                        lastNotified == null || lastNotified < startOfTodayUtc
                    }
                    ScheduleType.TIMED -> {
                        // Notify if the scheduled time is within the current day
                        // (worker fires at 7am — TIMED reminders for today appear here)
                        // lastNotifiedDateUtc guards against re-notification if worker
                        // retries or is re-scheduled
                        val lastNotified = reminder.lastNotifiedDateUtc
                        lastNotified == null || lastNotified < startOfTodayUtc
                    }
                }

                if (shouldNotify) {
                    val displayName = resolver.resolve(reminder.memberGuid)?.displayName
                        ?: reminder.memberDisplayNameCache.orEmpty()

                    PastoralNotificationHelper.postReminderNotification(
                        context          = applicationContext,
                        reminder         = reminder,
                        memberDisplayName = displayName
                    )

                    // Stamp lastNotifiedDateUtc so the worker doesn't re-notify today
                    reminderDao.update(
                        reminder.copy(
                            lastNotifiedDateUtc = startOfTodayUtc,
                            updatedAt           = nowUtc
                        )
                    )
                    notified++
                }
            }

            Log.i(TAG, "FollowUpReminderWorker complete — notified $notified reminders")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "FollowUpReminderWorker failed", e)
            // Retry once; WorkManager will back off automatically
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "follow_up_reminder_worker"
        private const val TAG = "FollowUpReminderWorker"
    }
}
```

**Why `Result.retry()` on exception:** A transient ContentProvider failure (member resolver) or DB lock should not silently swallow the notification run. WorkManager's default backoff (exponential, min 10 s) will retry. After the backoff the day has probably not changed, so `lastNotifiedDateUtc` will still suppress duplicates correctly.

---

## C2 — WorkManagerHelper: add scheduleFollowUpReminders()

Add to `WorkManagerHelper.kt` alongside the existing `scheduleBirthdayReminder()`:

```kotlin
// New constant alongside existing tags
private const val TAG_FOLLOW_UP = "follow_up_reminder"

/**
 * Schedule daily pastoral follow-up reminder check.
 *
 * Fires at [hour]:[minute] every day. Default 07:00 — before the pastor starts
 * the day, so reminders appear when they open the phone in the morning.
 */
fun scheduleFollowUpReminders(context: Context, hour: Int = 7, minute: Int = 0) {
    val workManager = WorkManager.getInstance(context)

    workManager.cancelUniqueWork(FollowUpReminderWorker.WORK_NAME)

    val now = Calendar.getInstance()
    val targetTime = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    var initialDelay = targetTime.timeInMillis - now.timeInMillis
    if (initialDelay <= 0) {
        initialDelay += TimeUnit.DAYS.toMillis(1)
    }

    val workRequest = PeriodicWorkRequestBuilder<FollowUpReminderWorker>(
        1, TimeUnit.DAYS,
        15, TimeUnit.MINUTES   // flex window — consistent with existing workers
    )
        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
        .addTag(TAG_FOLLOW_UP)
        .build()

    workManager.enqueueUniquePeriodicWork(
        FollowUpReminderWorker.WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        workRequest
    )

    Log.d("WorkManagerHelper", "Follow-up reminder worker scheduled at $hour:$minute")
}
```

**Also add to `cancelAllWork()`:**

```kotlin
fun cancelAllWork(context: Context) {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelUniqueWork(DropboxDownloadWorker.WORK_NAME)
    workManager.cancelUniqueWork(WidgetRefreshWorker.WORK_NAME)
    workManager.cancelUniqueWork(BirthdayReminderWorker.WORK_NAME)
    workManager.cancelUniqueWork(FollowUpReminderWorker.WORK_NAME)  // ← add
}
```

**Add import at top of `WorkManagerHelper.kt`:**

```kotlin
import za.co.jpsoft.winkerkreader.workers.FollowUpReminderWorker
```

---

## C3 — MainActivity: wire-up

Three changes, all small and consistent with existing patterns.

### 1. Add `setupFollowUpReminderWork()` and call it from `setupAlarms()`

```kotlin
// Find this:
private fun setupAlarms() {
    setupAutoDownloadWork()
    setupReminderWork()
    setupWidgetRefreshWork()
}

// Replace with:
private fun setupAlarms() {
    setupAutoDownloadWork()
    setupReminderWork()
    setupWidgetRefreshWork()
    setupFollowUpReminderWork()   // ← Sprint C
}

// Add the new function alongside the others:
private fun setupFollowUpReminderWork() {
    WorkManagerHelper.scheduleFollowUpReminders(this)
}
```

The follow-up worker always runs — it doesn't check a settings toggle here, because the repository itself guards on `ReminderStatus.PENDING` and there are no reminders unless the pastor has created them. No settings gate needed.

### 2. Create the pastoral notification channel from `setupPermissions()`

```kotlin
// Find this:
private fun setupPermissions() {
    PermissionHelper.requestAllPermissions(this, PermissionHelper.REQUEST_CODE_ALL_PERMISSIONS)
    checkOverlayPermission()
    createNotificationChannel()
}

// Replace with:
private fun setupPermissions() {
    PermissionHelper.requestAllPermissions(this, PermissionHelper.REQUEST_CODE_ALL_PERMISSIONS)
    checkOverlayPermission()
    createNotificationChannel()
    PastoralNotificationHelper.ensureChannel(this)   // ← Sprint C
}
```

### 3. Add import

```kotlin
import za.co.jpsoft.winkerkreader.utils.PastoralNotificationHelper
```

---

## AndroidManifest changes

Two additions required.

### Register the receiver (in `<application>` block)

```xml
<receiver
    android:name=".receivers.PastoralReminderActionReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="za.co.jpsoft.winkerkreader.ACTION_PASTORAL_COMPLETE" />
        <action android:name="za.co.jpsoft.winkerkreader.ACTION_PASTORAL_SNOOZE" />
    </intent-filter>
</receiver>
```

### POST_NOTIFICATIONS permission (Android 13+)

If not already present:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`PermissionHelper.requestAllPermissions()` should already request this if it's in the manifest. If not, add it to the permissions array in `PermissionHelper`.

---

## String resources

```xml
<!-- Already in Sprint B spec, confirm present: -->
<string name="bediening_title">Bediening</string>
<string name="bediening_agterstallig">Agterstallig</string>
<string name="herinnering_voltooi">Voltooi</string>
<string name="herinnering_uitstel">Uitstel 1 dag</string>

<!-- New for Sprint C: -->
<string name="bediening_channel_description">Herinnerings vir pastorale opvolg</string>
```

---

## Sprint C → Sprint D handoff notes

**`BedieningActivity` is referenced but not implemented yet.** The `openPendingIntent()` in `PastoralNotificationHelper` points to `BedieningActivity`. Until Sprint D lands, tapping the notification body will either silently fail or throw an `ActivityNotFoundException`. Two options for the interim:

- Option A (recommended): point the `openPendingIntent` to `MainActivity` temporarily with the `reminder_id` extra — it will open the app without crashing.
- Option B: create a minimal `BedieningActivity` stub that just shows a `Toast` so the intent resolves.

**Badge count on main menu.** Sprint D needs a `COUNT(*)` query for overdue + today reminders to badge the Bediening menu item. `FollowUpReminderDao.countOverdue()` already exists from Sprint A. Sprint D adds `countDueToday()` and combines them in `MainActivity.onResume()`.

**TIMED reminders — exact-time notifications.** The Sprint C worker fires at 7am and notifies TIMED reminders for the day with the time shown in the notification body. Phase 2 can improve this with a one-time `OneTimeWorkRequest` scheduled per TIMED reminder for precise delivery.

---

## Decision log

| Decision | Rationale |
|----------|-----------|
| Dedicated `pastoral_bediening` channel | Allows the pastor to silence call-log alerts independently of pastoral reminders — two different pastoral workflows |
| `goAsync()` + `SupervisorJob` in receiver | `BroadcastReceiver.onReceive()` must return quickly; `goAsync()` extends the window. `SupervisorJob` prevents one failed child coroutine from cancelling the receiver scope |
| Snooze from notification → next day 08:00 | Midnight snooze should not re-alert at midnight. Fixed 08:00 is predictable; the UI (Sprint D) allows custom time |
| `Result.retry()` on worker exception | Transient DB/ContentProvider errors should not silently drop notifications; WorkManager's exponential backoff handles retries safely |
| No settings gate on `scheduleFollowUpReminders()` | The worker is a no-op when there are no PENDING reminders. Gating it on a toggle adds friction without real benefit |
| `lastNotifiedDateUtc` set to `startOfTodayUtc` | Stamped to midnight so the comparison `< startOfTodayUtc` on the next day correctly re-enables notification, regardless of the exact time the worker fires |
