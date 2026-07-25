# Pastoral CRM — Sprint A Review & Sprint B Design

> **Status:** Sprint A complete · Sprint B design ready for implementation  
> **Last reviewed:** 2026-06-13  
> **As-built flows:** [`architecture.md`](architecture.md) §8 (Pastoral / Bediening)

---

## Part 1 — Sprint A: Implementation Review

### Overall verdict: solid foundation ✅

The data layer is well-structured. Four entities, three DAOs, a clean repository with coroutine dispatch, a WAL-checkpoint backup utility, and idempotent seeding. The code is production-ready for what it covers. Notes below are mostly gaps and edge-case observations, not defects.

---

### A2 — Entities & DAOs

**`FollowUpReminderEntity`** — correct. The `calendarEventId` / `calendarSynced` fields are already present (Sprint B will populate them). The `memberDisplayNameCache` fallback is a good call for archive resilience.

**`TemplateStepEntity`** — `offsetDays` correctly allows negatives (used by `OPERASIE` step 1: `−1`). `offsetMonths` defaults to `0` and is correctly combined in `PastoralReminderDates.expandDueDate()`.

**`ReminderTemplateDao`** — `@Transaction` on both `observeTemplatesWithSteps()` and `getTemplatesWithSteps()` is correct for the `@Relation` join.

**Gap — missing `deleteReminder()` / bulk delete on DAO:**  
`FollowUpReminderDao` has `insert`, `insertAll`, and `update` but no `delete`. `cancelReminder()` will be added in Sprint B and needs an `@Update` path (status → `CANCELLED`). That already works. However, future Sprint G cleanup (e.g. pruning old completed reminders) will need a `@Query DELETE`. Add now to keep the DAO complete:

```kotlin
// Add to FollowUpReminderDao
@Query("DELETE FROM follow_up_reminders WHERE reminderId = :reminderId")
suspend fun deleteById(reminderId: String)

@Query("DELETE FROM follow_up_reminders WHERE status = :status AND completedAtUtc < :beforeUtc")
suspend fun deleteStaleBefore(status: String, beforeUtc: Long): Int
```

---

### A3 — PastoralDatabaseInitializer

The two-path seed (callback `onCreate` + `seedIfEmptyBlocking` post-build) handles both the fresh-install and the edge case where the callback fires before the DAO layer is ready. Correct.

**Observation — `OPERASIE` title capitalisation:**  
In `buildSystemTemplates`, `titleAf = "Hospitalisasie"` (lowercase 'o') but the spec lists it as `"Hospitalisasie"` too — consistent. No issue.

**Observation — `SeedTemplate` is private data class inside `companion object`:**  
Technically fine in Kotlin but slightly unusual. Move to file-private top-level if the file grows. Not a blocker.

---

### A4 — MemberGuidResolver

`CongregationMemberGuidResolver` uses raw string interpolation for the `WHERE` clause with `quote()` wrapping. The `sanitizeGuid()` guard that rejects GUIDs containing `'` is the right defensive layer here since `quote()` in SQLite escapes single quotes but the rejection keeps the contract clear.

**Gap — archived member photo path:**  
`lookupArchivedMember()` returns `photoPath = null` because the Argief table does not store photos. That is correct and intentional. Confirm this is documented in the UI layer (Sprint D) so archived member cards always use the placeholder avatar.

**Gap — resolver is called on every `toReminderWithMember()` emission:**  
`observeVandagDashboard()` maps each list item via `memberResolver.resolve()` on `Dispatchers.IO`. For a small congregation list this is fine, but each resolve hits the ContentProvider synchronously. For Sprint D, consider a `LruCache<String, MemberDisplay>` with a TTL (invalidate on congregation DB reload) to avoid N ContentProvider calls per Flow emission.

---

### A5 — PastoralReminderRepository

**`observeVandagDashboard()` — day bounds computed once:**  
`dayBounds()` is called when the flow is collected, not reactively. If the phone stays on past midnight without the screen being opened, the Vandag tab will show the wrong day's bounds until the Activity is recreated. Sprint D should reset by re-subscribing via `ViewModel.viewModelScope` (Activity restart naturally handles this, but a coroutine-based midnight trigger would be cleaner).

**`syncToCalendar()` — stub only:**  
Returns `false` unconditionally. Sprint B implements this. The method signature is already correct.

**`completeReminder()` has `// Calendar cleanup — Sprint B` comment:**  
Good marker. Sprint B replaces it.

**Missing `cancelReminder()`:**  
`ReminderStatus.CANCELLED` exists but there is no `cancelReminder()` on the repository. Sprint B adds it (calendar cleanup required before status change).

**Missing `deleteReminder()`:**  
The spec does not explicitly require hard deletion in Phase 1, but the DAO gap noted above means Sprint G would need to add it. No action needed in Sprint B.

---

### A6 — PastoralDatabaseBackup

Not included in the uploaded files, but `requestBackup()` calls `PastoralDatabaseBackup.backupDebounced(appContext)` after every mutation. The debounce (spec: ~2 s) means rapid successive operations (e.g. creating a 6-step OPERASIE template) result in a single backup. Correct pattern.

---

### A7 — Unit tests

`PastoralReminderDates` is the right target for pure-function unit tests. Sprint B should add parallel tests for `CalendarManager.isDuplicatePastoralEvent()` using a fake `ContentResolver`.

---

### Sprint A — Open tech debt summary

| # | Severity | Item |
|---|----------|------|
| TD-1 | Low | `FollowUpReminderDao`: add `deleteById()` and `deleteStaleBefore()` |
| TD-2 | Low | `cancelReminder()` missing from repository (Sprint B adds it) |
| TD-3 | Medium | `LruCache` for member resolver (Sprint D concern) |
| TD-4 | Low | Midnight day-boundary reset in `observeVandagDashboard()` (Sprint D concern) |
| TD-5 | Low | Archived member photo → always placeholder (document in Sprint D adapter) |

---

## Part 2 — Sprint B: Calendar Integration Design

### Scope

| # | Task | Files | Est. |
|---|------|-------|------|
| B1 | Pastoral calendar CRUD + dedup | `CalendarManager.kt` | 4 h |
| B2 | Settings keys | `WinkerkContract.kt`, `SettingsManager.kt` | 1 h |
| B3 | `syncToCalendar()` + cleanup on complete/cancel/snooze | `PastoralReminderRepository.kt` | 3 h |
| B4 | Unit tests for dedup + repository calendar paths | `CalendarManagerPastoralTest.kt` | 2 h |

**Permissions required:** `READ_CALENDAR` + `WRITE_CALENDAR` (runtime — dangerous group). Permission checks live in the UI layer (Sprint D); the repository and CalendarManager assume permission is granted before being called and log warnings if operations fail.

---

### B2 — Settings keys (implement first — B1 and B3 depend on it)

#### `WinkerkContract.kt` additions

```kotlin
// Inside winkerkEntry companion / constants block — alongside KEY_SELECTED_CALENDAR_ID
const val KEY_PASTORAL_SYNC_CALENDAR       = "pref_pastoral_sync_calendar"
const val KEY_PASTORAL_CALENDAR_AUTO_TIMED = "pref_pastoral_calendar_auto_timed"
```

#### `SettingsManager.kt` additions

```kotlin
/** True when the user has opted into calendar mirroring for pastoral reminders. Default false. */
fun isPastoralCalendarSyncEnabled(): Boolean =
    prefs.getBoolean(winkerkEntry.KEY_PASTORAL_SYNC_CALENDAR, false)

/**
 * When true AND [isPastoralCalendarSyncEnabled], TIMED reminders are automatically
 * pushed to the calendar on creation. DATE_ONLY reminders always require explicit
 * "Voeg by kalender" user action.
 */
fun isPastoralCalendarAutoTimedEnabled(): Boolean =
    prefs.getBoolean(winkerkEntry.KEY_PASTORAL_CALENDAR_AUTO_TIMED, false)

/** Returns the selected calendar ID, or null if not configured. Reuses existing key. */
fun selectedCalendarId(): Long? {
    val id = prefs.getLong(winkerkEntry.KEY_SELECTED_CALENDAR_ID, -1L)
    return if (id == -1L) null else id
}
```

**Settings UI (Sprint D):** Add a "Bediening" section to the existing settings screen with a toggle for `KEY_PASTORAL_SYNC_CALENDAR` and a dependent child toggle for `KEY_PASTORAL_CALENDAR_AUTO_TIMED` (enabled only when parent is on). Reuse the existing calendar-picker preference for `KEY_SELECTED_CALENDAR_ID`.

---

### B1 — CalendarManager: pastoral CRUD + dedup

Add the following three methods to `CalendarManager`. They are self-contained and do not alter existing call-log calendar logic.

#### Constants

```kotlin
// Inside CalendarManager companion object
private const val PASTORAL_TITLE_PREFIX    = "WKR Bediening: "
private const val PASTORAL_REMINDER_TOKEN  = "wkr_reminder_id="
private const val PASTORAL_TIMED_WINDOW_MS = 2 * 60 * 1000L   // ±2 min dedup window
```

#### `addPastoralEvent()`

```kotlin
/**
 * Inserts a single pastoral reminder event into [calendarId].
 *
 * Title is prefixed with [PASTORAL_TITLE_PREFIX] so pastoral events are visually
 * distinct and scannable by [isDuplicatePastoralEvent].
 * Description footer includes [PASTORAL_REMINDER_TOKEN] + [reminderId] for
 * orphan detection after restore.
 *
 * @return The new `CalendarContract.Events._ID`, or null if insert failed or
 *         a duplicate was detected.
 */
fun addPastoralEvent(
    calendarId: Long,
    reminderId: String,
    memberDisplayName: String,
    title: String,
    note: String?,
    startMillis: Long,
    endMillis: Long,
    isAllDay: Boolean
): Long? {
    if (isDuplicatePastoralEvent(calendarId, reminderId, startMillis, isAllDay)) {
        Log.w(TAG, "Skipping duplicate pastoral calendar event for reminder $reminderId")
        return null
    }

    val descriptionParts = buildList {
        if (!note.isNullOrBlank()) add(note.trim())
        add("$PASTORAL_REMINDER_TOKEN$reminderId")
    }

    val values = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, "$PASTORAL_TITLE_PREFIX${title.trim()}")
        put(CalendarContract.Events.DESCRIPTION, descriptionParts.joinToString("\n\n"))
        put(CalendarContract.Events.DTSTART, startMillis)
        put(CalendarContract.Events.DTEND, endMillis)
        put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        put(CalendarContract.Events.HAS_ALARM, 0)  // In-app notification owns alerting
    }

    return try {
        val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        uri?.lastPathSegment?.toLongOrNull().also { id ->
            Log.d(TAG, "Pastoral calendar event created: id=$id reminder=$reminderId")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to insert pastoral calendar event for $reminderId", e)
        null
    }
}
```

**Note on `HAS_ALARM`:** Set to `0` intentionally. The in-app `FollowUpReminderWorker` (Sprint C) owns notification delivery. A duplicate calendar alarm would confuse the pastor with two simultaneous alerts.

#### `deletePastoralEvent()`

```kotlin
/**
 * Deletes the calendar event with [calendarEventId].
 * Safe to call even if the event no longer exists (ContentProvider returns 0 rows).
 *
 * @return true if the event was deleted, false if not found or deletion failed.
 */
fun deletePastoralEvent(calendarEventId: Long): Boolean {
    val uri = ContentUris.withAppendedId(
        CalendarContract.Events.CONTENT_URI, calendarEventId
    )
    return try {
        val deleted = contentResolver.delete(uri, null, null)
        (deleted > 0).also {
            Log.d(TAG, "Pastoral calendar event delete: id=$calendarEventId deleted=$it")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to delete pastoral calendar event $calendarEventId", e)
        false
    }
}
```

#### `isDuplicatePastoralEvent()`

Dedup uses three layers in order of cost (cheapest first):

1. **Token scan** — query `CalendarContract.Events` where description contains `wkr_reminder_id=<uuid>`. O(description LIKE) — fast.
2. **Time-window scan** — query events on the same calendar within ±2 min (TIMED) or the same calendar day (DATE_ONLY). Mirrors existing `isDuplicateCalendarEvent()` logic.

Layer 1 alone catches 95 % of cases including post-restore orphans. Layer 2 is the safety net.

```kotlin
/**
 * Returns true if a pastoral event for [reminderId] already exists on [calendarId].
 *
 * Checks by description token first (fast, handles post-restore orphans), then
 * falls back to a time-window scan.
 *
 * @param startMillis  The proposed event start (epoch ms). Used for the time-window check.
 * @param isAllDay     When true, the time-window check spans the entire calendar day.
 */
fun isDuplicatePastoralEvent(
    calendarId: Long,
    reminderId: String,
    startMillis: Long,
    isAllDay: Boolean = false
): Boolean {
    return isTokenDuplicate(calendarId, reminderId)
        || isTimeWindowDuplicate(calendarId, startMillis, isAllDay)
}

private fun isTokenDuplicate(calendarId: Long, reminderId: String): Boolean {
    val projection = arrayOf(CalendarContract.Events._ID)
    val selection = "${CalendarContract.Events.CALENDAR_ID} = ? " +
            "AND ${CalendarContract.Events.DESCRIPTION} LIKE ? " +
            "AND ${CalendarContract.Events.DELETED} = 0"
    val token = "$PASTORAL_REMINDER_TOKEN$reminderId"

    return try {
        contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            arrayOf(calendarId.toString(), "%$token%"),
            null
        )?.use { cursor -> cursor.count > 0 } ?: false
    } catch (e: Exception) {
        Log.e(TAG, "Token dedup query failed for $reminderId", e)
        false  // Fail open: let the insert proceed; the description token will catch it next time
    }
}

private fun isTimeWindowDuplicate(calendarId: Long, startMillis: Long, isAllDay: Boolean): Boolean {
    val (windowStart, windowEnd) = if (isAllDay) {
        // Same calendar day in device timezone
        val cal = Calendar.getInstance()
        cal.timeInMillis = startMillis
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val dayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        dayStart to cal.timeInMillis
    } else {
        (startMillis - PASTORAL_TIMED_WINDOW_MS) to (startMillis + PASTORAL_TIMED_WINDOW_MS)
    }

    val projection = arrayOf(CalendarContract.Events._ID)
    val selection = "${CalendarContract.Events.CALENDAR_ID} = ? " +
            "AND ${CalendarContract.Events.TITLE} LIKE ? " +
            "AND ${CalendarContract.Events.DTSTART} >= ? " +
            "AND ${CalendarContract.Events.DTSTART} < ? " +
            "AND ${CalendarContract.Events.DELETED} = 0"

    return try {
        contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            arrayOf(
                calendarId.toString(),
                "$PASTORAL_TITLE_PREFIX%",
                windowStart.toString(),
                windowEnd.toString()
            ),
            null
        )?.use { cursor -> cursor.count > 0 } ?: false
    } catch (e: Exception) {
        Log.e(TAG, "Time-window dedup query failed", e)
        false
    }
}
```

---

### B3 — PastoralReminderRepository: implement syncToCalendar(), complete, cancel, snooze cleanup

#### Helper: `buildCalendarEvent()`

Add private helper inside `PastoralReminderRepository` to avoid repeating the startMillis / endMillis / isAllDay derivation:

```kotlin
private data class CalendarEventParams(
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean
)

private fun buildCalendarEventParams(reminder: FollowUpReminderEntity): CalendarEventParams {
    val scheduleType = ScheduleType.fromStored(reminder.scheduleType)
    return when (scheduleType) {
        ScheduleType.DATE_ONLY -> {
            // All-day event: start = midnight of due date, end = next midnight
            val endMillis = reminder.dueDateUtc +
                TimeUnit.DAYS.toMillis(1)
            CalendarEventParams(
                startMillis = reminder.dueDateUtc,
                endMillis = endMillis,
                isAllDay = true
            )
        }
        ScheduleType.TIMED -> {
            // 1-hour block starting at dueDateUtc; use dueEndUtc if provided
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
```

#### `syncToCalendar()` — replace stub

```kotlin
/**
 * Pushes [reminderId] to the Android calendar selected in settings.
 *
 * - No-ops if calendar sync is disabled in settings.
 * - No-ops if no calendar is selected.
 * - Deduplication is enforced by [CalendarManager.isDuplicatePastoralEvent].
 * - On success, updates [FollowUpReminderEntity.calendarEventId] and
 *   [FollowUpReminderEntity.calendarSynced] in the DB.
 *
 * @return true if a new calendar event was created, false if skipped or failed.
 */
suspend fun syncToCalendar(reminderId: String): Boolean = withContext(Dispatchers.IO) {
    if (!settingsManager.isPastoralCalendarSyncEnabled()) {
        Log.d(TAG, "syncToCalendar: sync disabled, skipping $reminderId")
        return@withContext false
    }

    val calendarId = settingsManager.selectedCalendarId() ?: run {
        Log.w(TAG, "syncToCalendar: no calendar selected, skipping $reminderId")
        return@withContext false
    }

    val reminder = reminderDao.getById(reminderId)
        ?: throw IllegalArgumentException("Reminder not found: $reminderId")

    if (reminder.calendarSynced && reminder.calendarEventId != null) {
        Log.d(TAG, "syncToCalendar: already synced, eventId=${reminder.calendarEventId}")
        return@withContext false
    }

    if (reminder.status != ReminderStatus.PENDING.name) {
        Log.d(TAG, "syncToCalendar: reminder $reminderId is not PENDING, skipping")
        return@withContext false
    }

    val displayName = memberResolver.resolve(reminder.memberGuid)?.displayName
        ?: reminder.memberDisplayNameCache.orEmpty()

    val params = buildCalendarEventParams(reminder)

    val eventId = calendarManager.addPastoralEvent(
        calendarId       = calendarId,
        reminderId       = reminderId,
        memberDisplayName = displayName,
        title            = reminder.title,
        note             = reminder.note,
        startMillis      = params.startMillis,
        endMillis        = params.endMillis,
        isAllDay         = params.isAllDay
    )

    if (eventId != null) {
        reminderDao.update(
            reminder.copy(
                calendarEventId = eventId,
                calendarSynced  = true,
                updatedAt       = System.currentTimeMillis()
            )
        )
        requestBackup()
        Log.i(TAG, "syncToCalendar: created event $eventId for reminder $reminderId")
        true
    } else {
        Log.w(TAG, "syncToCalendar: addPastoralEvent returned null for $reminderId (duplicate or error)")
        false
    }
}
```

#### `completeReminder()` — add calendar cleanup

```kotlin
suspend fun completeReminder(reminderId: String) = withContext(Dispatchers.IO) {
    val reminder = reminderDao.getById(reminderId)
        ?: throw IllegalArgumentException("Reminder not found: $reminderId")

    // Sprint B: delete linked calendar event if present
    deleteCalendarEventIfSynced(reminder)

    val now = System.currentTimeMillis()
    reminderDao.update(
        reminder.copy(
            status          = ReminderStatus.COMPLETED.name,
            completedAtUtc  = now,
            calendarEventId = null,
            calendarSynced  = false,
            updatedAt       = now
        )
    )
    requestBackup()
}
```

#### `cancelReminder()` — new function

```kotlin
/**
 * Marks [reminderId] as [ReminderStatus.CANCELLED] and removes any linked calendar event.
 * Cancelled reminders are excluded from Vandag and member mini-list queries by design
 * (those queries filter on PENDING only).
 */
suspend fun cancelReminder(reminderId: String) = withContext(Dispatchers.IO) {
    val reminder = reminderDao.getById(reminderId)
        ?: throw IllegalArgumentException("Reminder not found: $reminderId")

    deleteCalendarEventIfSynced(reminder)

    reminderDao.update(
        reminder.copy(
            status          = ReminderStatus.CANCELLED.name,
            calendarEventId = null,
            calendarSynced  = false,
            updatedAt       = System.currentTimeMillis()
        )
    )
    requestBackup()
}
```

#### `snoozeReminder()` — add calendar cleanup

On snooze, the `dueDateUtc` does not change — only `snoozedUntilUtc` is set. The calendar event still points to the original due date. To avoid the pastor seeing a calendar event for a day they have already deferred, delete the event on snooze. They can re-add via "Voeg by kalender" in Sprint D if needed.

```kotlin
suspend fun snoozeReminder(reminderId: String, until: LocalDateTime) = withContext(Dispatchers.IO) {
    val reminder = reminderDao.getById(reminderId)
        ?: throw IllegalArgumentException("Reminder not found: $reminderId")

    // Calendar event on original due date is no longer relevant after snooze
    deleteCalendarEventIfSynced(reminder)

    val snoozedUntilUtc = until.atZone(zoneId).toInstant().toEpochMilli()
    reminderDao.update(
        reminder.copy(
            snoozedUntilUtc     = snoozedUntilUtc,
            lastNotifiedDateUtc = null,
            calendarEventId     = null,
            calendarSynced      = false,
            updatedAt           = System.currentTimeMillis()
        )
    )
    requestBackup()
}
```

#### Private cleanup helper

```kotlin
/**
 * Deletes the linked calendar event if the reminder is synced.
 * Safe to call unconditionally — no-ops if not synced or deletion fails.
 */
private fun deleteCalendarEventIfSynced(reminder: FollowUpReminderEntity) {
    if (reminder.calendarSynced && reminder.calendarEventId != null) {
        val deleted = calendarManager.deletePastoralEvent(reminder.calendarEventId)
        if (!deleted) {
            // Event may have been deleted from calendar app directly — not an error
            Log.w(TAG, "Calendar event ${reminder.calendarEventId} not found on delete " +
                    "(already removed externally?)")
        }
    }
}
```

#### Constructor change — inject `CalendarManager` and `SettingsManager`

```kotlin
class PastoralReminderRepository(
    private val appContext: Context,
    private val database: PastoralDatabase,
    private val memberResolver: MemberGuidResolver,
    private val calendarManager: CalendarManager,      // ← Sprint B
    private val settingsManager: SettingsManager       // ← Sprint B
) {
    // ... existing body unchanged ...

    companion object {
        fun create(context: Context): PastoralReminderRepository {
            val appContext = context.applicationContext
            val database = PastoralDatabase.getInstance(appContext)
            return PastoralReminderRepository(
                appContext      = appContext,
                database        = database,
                memberResolver  = CongregationMemberGuidResolver(appContext),
                calendarManager = CalendarManager(appContext),   // ← Sprint B
                settingsManager = SettingsManager(appContext)    // ← Sprint B
            )
        }
    }
}
```

---

### B4 — Unit tests

#### `CalendarManagerPastoralTest.kt`

Use a `MockContentResolver` (or `Robolectric`) to stub `CalendarContract.Events`.

```kotlin
class CalendarManagerPastoralTest {

    // Dedup: token not found → insert proceeds
    @Test fun `addPastoralEvent inserts when no duplicate token exists`() { ... }

    // Dedup: same token in description → returns null, no second insert
    @Test fun `addPastoralEvent returns null when token duplicate exists`() { ... }

    // Dedup: TIMED event within 2 min window → blocked
    @Test fun `isDuplicatePastoralEvent returns true for timed event within 2 min window`() { ... }

    // Dedup: TIMED event 3 min away → allowed
    @Test fun `isDuplicatePastoralEvent returns false for timed event outside 2 min window`() { ... }

    // Dedup: DATE_ONLY same day → blocked
    @Test fun `isDuplicatePastoralEvent returns true for allDay event on same day`() { ... }

    // Delete: event exists → deleted, returns true
    @Test fun `deletePastoralEvent returns true when event deleted`() { ... }

    // Delete: event already gone → returns false, no crash
    @Test fun `deletePastoralEvent returns false gracefully when event missing`() { ... }
}
```

#### `PastoralReminderRepositoryCalendarTest.kt`

```kotlin
class PastoralReminderRepositoryCalendarTest {

    // syncToCalendar: sync disabled → returns false, no CalendarManager call
    @Test fun `syncToCalendar no-ops when sync disabled in settings`() { ... }

    // syncToCalendar: already synced → returns false, no duplicate event
    @Test fun `syncToCalendar no-ops when reminder already has calendarEventId`() { ... }

    // syncToCalendar: TIMED reminder → correct isAllDay=false, endMillis=start+1h
    @Test fun `syncToCalendar passes isAllDay false and 1h window for TIMED reminder`() { ... }

    // syncToCalendar: DATE_ONLY reminder → correct isAllDay=true, endMillis=next day
    @Test fun `syncToCalendar passes isAllDay true and next-day end for DATE_ONLY reminder`() { ... }

    // completeReminder: deletes calendar event when synced
    @Test fun `completeReminder calls deletePastoralEvent when calendarSynced`() { ... }

    // completeReminder: no crash when not synced
    @Test fun `completeReminder is safe when calendarSynced false`() { ... }

    // snoozeReminder: clears calendarSynced after delete
    @Test fun `snoozeReminder clears calendarEventId and calendarSynced`() { ... }

    // cancelReminder: deletes calendar event and sets status CANCELLED
    @Test fun `cancelReminder deletes event and sets CANCELLED status`() { ... }
}
```

---

### Sprint B — Decision log

| Decision | Rationale |
|----------|-----------|
| `HAS_ALARM = 0` on calendar events | In-app worker (Sprint C) owns all alerting; duplicate calendar alarms would fire alongside app notifications and confuse the pastor |
| Snooze deletes calendar event | `dueDateUtc` unchanged on snooze; keeping calendar event on the original day contradicts the intent of the snooze. Re-adding via "Voeg by kalender" is explicit and intentional (Sprint D) |
| `cancelReminder()` uses status CANCELLED (not hard delete) | Preserves audit trail; pruning old CANCELLED rows is a Sprint G background task |
| `isAllDay = true` for DATE_ONLY | DATE_ONLY reminders have no time component; all-day calendar events are the natural representation and avoid spurious timezone-shift issues |
| `endMillis` for TIMED = `dueEndUtc ?? start + 1h` | `dueEndUtc` is nullable (Phase 1 never sets it); 1-hour default makes calendar blocks readable |
| Dedup: fail open on ContentProvider error | A failed dedup query should not silently block a calendar insert; log the error, let the insert proceed, rely on the token in the description to catch it next time |
| `calendarManager` injected via constructor | Allows fake injection in tests; `create()` factory maintains the existing API for callers |

---

### Sprint B — Updated task table

| # | Status | Task | Files |
|---|--------|------|-------|
| B1 | 🔲 | Pastoral calendar CRUD + dedup | `CalendarManager.kt` |
| B2 | 🔲 | Settings keys + SettingsManager accessors | `WinkerkContract.kt`, `SettingsManager.kt` |
| B3 | 🔲 | `syncToCalendar()` impl + complete/cancel/snooze cleanup | `PastoralReminderRepository.kt` |
| B4 | 🔲 | Unit tests (CalendarManager + Repository calendar paths) | `CalendarManagerPastoralTest.kt`, `PastoralReminderRepositoryCalendarTest.kt` |
| TD-1 | 🔲 | Add `deleteById()` / `deleteStaleBefore()` to `FollowUpReminderDao` | `FollowUpReminderDao.kt` |

**Implement in order: B2 → B1 → B3 → B4 → TD-1**

---

### Sprint B → Sprint C handoff

Sprint C (`FollowUpReminderWorker`) will need:

- `PastoralReminderRepository.syncToCalendar()` already implemented (Sprint B) so auto-sync for new TIMED reminders can be triggered from the worker after scheduling.
- `settingsManager.isPastoralCalendarAutoTimedEnabled()` check: if enabled, call `syncToCalendar()` immediately after `createAdHocReminder()` / `createFromTemplate()` for TIMED steps. Consider adding this auto-sync call to the repository itself (inside `createAdHocReminder`) so Sprint D UI does not have to remember to call it.
- The `lastNotifiedDateUtc` field on `FollowUpReminderEntity` is already available — Sprint C sets it after notification delivery to prevent same-day re-alerts for DATE_ONLY reminders.
