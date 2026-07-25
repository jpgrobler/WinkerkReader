# Pastoral CRM — Phase 1 Technical Specification

> **Status:** Sprint A complete (8/8) — ready for Sprint B  
> **Last reviewed:** 2026-06-12  
> **Scope:** Local pastoral DB · Room schema · Reminder templates · `BedieningActivity` (“Vandag” tab) · “Stel herinnering” on member detail · PC backup · Calendar strategy  
> **Out of scope (Phase 1):** Sorg-gevalle, visit logs, bereavement wizard, widgets  
> **Implemented architecture (as-built):** see [`architecture.md`](architecture.md) §8 — many Sprint B–E items are now implemented beyond this spec’s original progress table.

### Implementation progress (summary)

| Sprint | Progress | Notes |
|--------|----------|-------|
| **A — Data foundation** | **8 / 8** ✅ | Complete — Room, seed, resolver, repository, backup, unit tests |
| B — Calendar | 0 / 3 | Not started |
| C — Notifications | 0 / 4 | Stub classes only (`FollowUpReminderWorker`, `PastoralDailyDigestWorker`) |
| D — BedieningActivity | 0 / 6 | Stub `BedieningActivity` only |
| E — Member detail | 0 / 5 | Not started |
| F — PC backup | 0 / 5 | Not started |
| G — Polish | 0 / 3 | Not started |

**Sprint A detail:**

| Task | Status | Implementation notes |
|------|--------|----------------------|
| **A1** `PASTORAL_DB` constant | ✅ Done | `WinkerkContract.winkerkEntry.PASTORAL_DB = "wkr_pastoral.db"`; referenced by `PastoralDatabase.DB_NAME` |
| **A2** Room entities, DAOs, database v1 | ✅ Done | `data/pastoral/**` — 4 entities, 3 DAOs, `PastoralDatabase` v1; schema exported to `app/schemas/.../1.json` |
| **A3** `PastoralDatabaseInitializer` — seed templates | ✅ Done | `PastoralDatabaseInitializer.kt` seeds meta + 4 system templates; wired via `onCreate` callback + idempotent post-build seed |
| **A4** `MemberGuidResolver` | ✅ Done | `fun interface MemberGuidResolver` + `CongregationMemberGuidResolver`; Members + Argief lookup |
| **A5** `PastoralReminderRepository` | ✅ Done | Template/ad-hoc CRUD, complete/snooze, `observeVandagDashboard`; debounced backup hooks |
| **A6** `PastoralDatabaseBackup` | ✅ Done | `utils/PastoralDatabaseBackup.kt` — WAL checkpoint, copy to `getWkrDir()`, `lastBackupUtc` update |
| **A7** Unit tests: template date expansion | ✅ Done | `PastoralReminderRepositoryTest` + `PastoralReminderDates` (OPERASIE offsets, NA_STERF, DATE_ONLY/TIMED) |
| **A8** Add `room-ktx` dependency | ✅ Done | `room-runtime`, `room-ktx`, `room-compiler` (KSP) v2.8.4 in `app/build.gradle` |

**Tech debt / cleanup:** Empty placeholder UI classes remain — `BedieningActivity.kt`, `PastoralCaseActivity.kt`, `PastoralDashboardViewModel.kt`. Wire up in Sprint D.

---

## 0. Related local database: `wkr_info.db` (current app)

Before designing `wkr_pastoral.db`, the existing local database was audited.

### 0.1 Verdict: **Yes, `wkr_info.db` is used — but narrowly**

| Area | Usage |
|------|--------|
| **Opened at runtime** | `WinkerkProvider.onCreate()` → `WinkerkDbHelper.getInstance(context, INFO_DB)` |
| **Active write path** | `LidmaatDetailActivity.processSelectedImage()` saves local photo metadata to table `WKR_Info` (`Member_GUID`, `Foto_Path`, `Group`) via `INFO_LOADER_FOTO_URI` |
| **Also updated** | Same flow writes `Members.Fotostoorplek` in `WinkerkReader.sqlite` — main DB path is the primary display source |
| **Backup to PC folder** | `LaaiDatabasisActivity.backupCurrentDatabase()` copies internal `wkr_info.db` → `getWkrDir()/wkr_info.db` |
| **Reload handling** | `FileDownloadWorker` and `LaaiNuweData()` call `WinkerkDbHelper.closeInstance(INFO_DB)` when congregation DB is replaced |
| **PC file picker** | Only `WinkerkReader.sqlite` is listed for import (`addFileToList` filters on `WINKERK_DB` only) — `wkr_info.db` is backed up but not offered for restore via the same UI |

### 0.2 Provider paths on `wkr_info.db` (in `WinkerkProvider`)

| URI match | Operation | Called from UI? |
|-----------|-----------|-----------------|
| `FOTO` | query / insert / update on `WKR_Info` | **Write only** — `LidmaatDetailActivity` (no read queries found in Kotlin) |
| `FOTO_UPDATER` | ATTACH `wkr_info.db` to main DB for cross-DB SQL | **No callers** — dead path |
| `WKR_GROEPE_LYS` | insert / update `wkrGroepe` | **No callers** — dead path |
| `WKR_GROEPLEDE` | insert `wkrLidmate2Groepe` | **No callers** — dead path |

### 0.3 Tables (from `WinkerkContract`)

- `WKR_Info` — local per-member photo path overrides (`INFO_TABLENAME`)
- `wkrGroepe` / `wkrLidmate2Groepe` — legacy app-specific groups (provider only, no current UI)

### 0.4 Implication for pastoral CRM

**Do not store pastoral data in `wkr_info.db`.** Use a separate file:

```
wkr_pastoral.db   ← new Room database (pastoral reminders, templates)
wkr_info.db       ← existing local device data (photos, legacy groups)
WinkerkReader.sqlite ← synced congregation data (read-mostly)
```

Rationale:
- Clear separation of concerns and schema ownership (Room migrations vs `SQLiteAssetHelper`)
- Congregation DB reload must never touch pastoral data
- PC backup/restore can mirror the existing `wkr_info.db` copy pattern independently
- Avoid coupling pastoral CRM to legacy unused `wkrGroepe` tables

---

## 1. Design principles

| Principle | Decision |
|-----------|----------|
| **Link to congregation data** | Every pastoral record stores `member_guid` (church DB `MemberGUID`). Optional `family_head_guid` for family-level follow-ups. |
| **Local source of truth** | Pastoral data never writes to synced `WinkerkReader.sqlite`. |
| **PC backup** | Plain SQLite file `wkr_pastoral.db`, copied to `getWkrDir()` and transferable like `wkr_info.db`. |
| **Calendar is optional** | App owns reminders; Android Calendar mirrors **timed appointments only**, with deduplication. |
| **Afrikaans UI** | Strings follow existing `strings.xml` patterns. |

---

## 2. Local DB + PC transfer

### 2.1 File locations

```
Internal (Room):  /data/data/<package>/databases/wkr_pastoral.db
Backup copy:      getExternalFilesDir()/wkr_pastoral.db
```

Add to `WinkerkContract.winkerkEntry` **(✅ A1 — implemented):**

```kotlin
const val PASTORAL_DB = "wkr_pastoral.db"
```

### 2.2 Why `MemberGUID` is required

- Survives congregation DB re-sync from PC (`_rowid_` changes; GUID does not).
- Joins pastoral reminders to `Members` via `ContentProvider`.
- On restore to a new phone: pastoral DB restores independently; GUIDs resolve if the same congregation DB is loaded.

**Validation:** On insert, verify `member_guid` exists in church DB. Soft-warning if archived; still allow insert with `member_display_name_cache`.

### 2.3 PC backup strategy

Mirror `LaaiDatabasisActivity.backupCurrentDatabase()` **(✅ A6 — implemented as `PastoralDatabaseBackup`)**:

```kotlin
// PastoralDatabaseBackup.kt
fun backupPastoralDatabase(context: Context) {
    val src = context.getDatabasePath(PASTORAL_DB)
    val dst = File(winkerkEntry.getWkrDir(context), PASTORAL_DB)
    // 1. PastoralDatabase.close() / checkpoint WAL
    // 2. FileChannel.transferTo
    // 3. MediaScannerConnection.scanFile (optional)
}
```

**When to backup:**
- After reminder create / update / complete (debounced ~2s)
- On app background (`ProcessLifecycleOwner`)
- Before congregation DB reload in `FileDownloadWorker` (backup pastoral first; never overwrite pastoral on congregation import)

**PC transfer (Phase 1):**
1. **Manual:** Bediening or Laai admin → *“Stuur bedieningsdatabasis”* (share intent / SAF).
2. **Phase 1b:** Extend `FileDownloadWorker` / PC companion to transfer `wkr_pastoral.db` as a second file on port `49514`.

**Restore from PC:**
- Include `wkr_pastoral.db` in `LaaiDatabasisActivity` file scan (extend `addFileToList` beyond `WINKERK_DB` only).
- On import: close Room → replace file → reopen with migration check.
- Confirm dialog: *“X herinnerings herstel”*.

---

## 3. Calendar integration (no duplication)

### 3.1 Why schedule in the app?

| In-app | Android Calendar only |
|--------|----------------------|
| Linked to `MemberGUID`, family, templates | No congregation context |
| **Bediening → Vandag** with bel / WhatsApp actions | Pastor tracks separately |
| Backed up to PC with pastoral DB | Lost if phone dies |
| Template series in one tap | Manual re-entry |
| Works without `WRITE_CALENDAR` | Permission required upfront |
| Private notes stay in app | Risk on shared calendars |

**Conclusion:** App = pastoral workflow. Calendar = optional visibility for fixed appointments.

### 3.2 Two reminder kinds

| Kind | `schedule_type` | Notification | Calendar |
|------|-----------------|--------------|----------|
| **Day reminder** | `DATE_ONLY` | Morning of due date | No auto-sync |
| **Timed appointment** | `TIMED` | At scheduled time | Optional on user action |

### 3.3 Anti-duplication

Store on `follow_up_reminders`:

```kotlin
calendar_event_id: Long? = null    // CalendarContract.Events._ID
calendar_synced: Boolean = false
```

**Rules:**
1. Calendar event created **only** when user taps *“Voeg by kalender”* or enables timed auto-sync in settings.
2. Title prefix: `WKR Bediening:` (distinct from call-log events in `CalendarManager`).
3. Description footer: `wkr_reminder_id=<uuid>`.
4. Before insert: `CalendarManager.isDuplicatePastoralEvent()` checks `calendar_event_id` or description token.
5. On complete / snooze / delete: update or delete linked calendar event.
6. Extend existing `isDuplicateCalendarEvent()` time-window logic (±2 min timed; same calendar day for date-only).

**Settings** (reuse `KEY_SELECTED_CALENDAR_ID`):
- `KEY_PASTORAL_SYNC_CALENDAR` — default `false`
- `KEY_PASTORAL_CALENDAR_AUTO_TIMED` — auto-sync `TIMED` only when enabled

### 3.4 User flow

```
Stel herinnering → wkr_pastoral.db → Vandag tab
       ↓ (optional)
"Voeg by kalender" → one event, ID stored on reminder
       ↓
Voltooi in app → calendar event removed/marked done
```

Phase 1 does **not** import arbitrary Google Calendar events.

---

## 4. Room schema

### 4.1 Package structure

**✅ A2–A7 — implemented** (entities, DAOs, models, `PastoralDatabase`, seed initializer, resolver, repository, backup, date-expansion tests):

```
data/pastoral/
├── PastoralDatabase.kt
├── PastoralDatabaseInitializer.kt
├── entities/
│   ├── PastoralMetaEntity.kt
│   ├── ReminderTemplateEntity.kt
│   ├── TemplateStepEntity.kt
│   └── FollowUpReminderEntity.kt
├── dao/
│   ├── ReminderTemplateDao.kt
│   ├── FollowUpReminderDao.kt
│   └── PastoralMetaDao.kt
├── model/
│   ├── ReminderStatus.kt
│   ├── ScheduleType.kt
│   ├── TemplateWithSteps.kt
│   └── ReminderWithMember.kt
└── repository/
    ├── PastoralReminderRepository.kt
    ├── MemberGuidResolver.kt
    └── PastoralDatabaseBackup.kt
```

Add `androidx.room:room-ktx` to `app/build.gradle` (runtime + compiler already present).

### 4.2 Entities

#### `pastoral_meta` (singleton `id = 1`)

```kotlin
@Entity(tableName = "pastoral_meta")
data class PastoralMetaEntity(
    @PrimaryKey val id: Int = 1,
    val schemaVersion: Int = 1,
    val deviceId: String,
    val congregationName: String?,
    val lastBackupUtc: Long?
)
```

#### `reminder_templates`

```kotlin
@Entity(tableName = "reminder_templates")
data class ReminderTemplateEntity(
    @PrimaryKey val templateId: String,
    val code: String,              // NA_STERF, OPERASIE, ...
    val titleAf: String,
    val descriptionAf: String?,
    val isSystem: Boolean = true,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
```

#### `template_steps`

```kotlin
@Entity(
    tableName = "template_steps",
    foreignKeys = [ForeignKey(
        entity = ReminderTemplateEntity::class,
        parentColumns = ["templateId"],
        childColumns = ["templateId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("templateId")]
)
data class TemplateStepEntity(
    @PrimaryKey val stepId: String,
    val templateId: String,
    val stepOrder: Int,
    val offsetDays: Int,           // negative allowed, e.g. -1 = day before anchor (operasie)
    val offsetMonths: Int = 0,
    val defaultTitleAf: String,
    val defaultNoteAf: String?,
    val scheduleType: String,      // DATE_ONLY | TIMED
    val defaultHour: Int? = 8,
    val defaultMinute: Int? = 0
)
```

#### `follow_up_reminders`

```kotlin
@Entity(
    tableName = "follow_up_reminders",
    indices = [
        Index("memberGuid"),
        Index("dueDateUtc"),
        Index("status"),
        Index("calendarEventId")
    ]
)
data class FollowUpReminderEntity(
    @PrimaryKey val reminderId: String,
    val memberGuid: String,
    val familyHeadGuid: String?,
    val templateId: String?,
    val templateStepId: String?,
    val anchorDateUtc: Long?,
    val title: String,
    val note: String?,
    val scheduleType: String,
    val dueDateUtc: Long,
    val dueEndUtc: Long?,
    val status: String,            // PENDING | COMPLETED | SNOOZED | CANCELLED
    val completedAtUtc: Long?,
    val snoozedUntilUtc: Long?,
    val lastNotifiedDateUtc: Long?, // DATE_ONLY: once per day
    val calendarEventId: Long?,
    val calendarSynced: Boolean = false,
    val memberDisplayNameCache: String?,
    val createdAt: Long,
    val updatedAt: Long
)
```

```kotlin
enum class ReminderStatus { PENDING, COMPLETED, SNOOZED, CANCELLED }
enum class ScheduleType { DATE_ONLY, TIMED }
```

### 4.3 Database class

```kotlin
@Database(
    entities = [
        PastoralMetaEntity::class,
        ReminderTemplateEntity::class,
        TemplateStepEntity::class,
        FollowUpReminderEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class PastoralDatabase : RoomDatabase() {
    abstract fun reminderTemplateDao(): ReminderTemplateDao
    abstract fun followUpReminderDao(): FollowUpReminderDao
    abstract fun pastoralMetaDao(): PastoralMetaDao

    companion object {
        const val DB_NAME = "wkr_pastoral.db"
        // getInstance(context), closeInstance() for backup/restore
    }
}
```

### 4.4 Seed templates (`PastoralDatabaseInitializer`)

| `code` | `titleAf` | Steps (`offsetDays`) |
|--------|-----------|----------------------|
| `NA_STERF` | Na sterfgeval | 3, 14, 30, 90, 365 |
| `OPERASIE` | operasie | -1, 0, 1, 3, 7, 14 |
| `ALGEMEEN` | Algemene opvolg | 7 |
| `NUWE_LID` | Nuwe lid | 7, 30, 90 |

**`OPERASIE` step detail** (anchor date = operation date):

| `stepOrder` | `offsetDays` | `defaultTitleAf` |
|-------------|--------------|------------------|
| 1 | -1 | Kontak voor operasie |
| 2 | 0 | Hospitalisasiedag |
| 3 | 1 | 1 dag na operasie |
| 4 | 3 | 3 dae na operasie |
| 5 | 7 | 1 week na operasie |
| 6 | 14 | 2 weke na operasie |

The `-1` step supports contacting the person (or family contact) **before** the procedure. Template expansion uses `anchorDate.plusDays(offsetDays)` — negative offsets are valid.

Run in `RoomDatabase.Callback.onCreate()` or first-launch initializer.

### 4.5 Key DAO queries

```kotlin
// Due today (Vandag tab)
@Query("""
    SELECT * FROM follow_up_reminders
    WHERE status = 'PENDING'
      AND dueDateUtc <= :endOfDayUtc
      AND (snoozedUntilUtc IS NULL OR snoozedUntilUtc <= :nowUtc)
    ORDER BY dueDateUtc ASC
""")
fun observeDueToday(endOfDayUtc: Long, nowUtc: Long): Flow<List<FollowUpReminderEntity>>

// Overdue
@Query("""
    SELECT * FROM follow_up_reminders
    WHERE status = 'PENDING' AND dueDateUtc < :startOfTodayUtc
    ORDER BY dueDateUtc ASC
""")
fun observeOverdue(startOfTodayUtc: Long): Flow<List<FollowUpReminderEntity>>

// Member detail mini-list
@Query("""
    SELECT * FROM follow_up_reminders
    WHERE memberGuid = :guid AND status = 'PENDING'
    ORDER BY dueDateUtc
""")
fun observePendingForMember(guid: String): Flow<List<FollowUpReminderEntity>>
```

---

## 5. Repository layer

### `MemberGuidResolver` **(✅ A4 — implemented)**

```kotlin
fun interface MemberGuidResolver {
    fun resolve(memberGuid: String): MemberDisplay?
}

// CongregationMemberGuidResolver: ContentProvider query on Members WHERE MemberGUID = ?
// Falls back to Argief; MemberDisplay also carries photoPath + isArchived
```

### `PastoralReminderRepository` **(✅ A5 — implemented)**

```kotlin
suspend fun createFromTemplate(
    memberGuid: String,
    templateId: String,
    anchorDate: LocalDate,
    customTitle: String? = null
): List<String>

suspend fun createAdHocReminder(
    memberGuid: String,
    title: String,
    note: String?,
    dueDate: LocalDate,
    scheduleType: ScheduleType,
    dueTime: LocalTime? = null
): String

suspend fun completeReminder(reminderId: String)
suspend fun snoozeReminder(reminderId: String, until: LocalDateTime)
suspend fun syncToCalendar(reminderId: String): Boolean
fun observeVandagDashboard(): Flow<VandagDashboard>
```

Template expansion: for each `template_steps` row, compute `dueDateUtc` from `anchorDate + offsetDays/Months`, insert `FollowUpReminderEntity`, call `PastoralDatabaseBackup.backupDebounced()`.

---

## 6. WorkManager — notifications

### `FollowUpReminderWorker`

- Query `PENDING` where due and not snoozed.
- `DATE_ONLY`: notify once per day (`lastNotifiedDateUtc`).
- Notification actions: *Voltooi* | *Uitstel 1 dag* | *Maak oop*.
- `PendingIntent` → `BedieningActivity` with `reminder_id` extra.

### `WorkManagerHelper`

```kotlin
fun scheduleFollowUpReminders(context: Context, hour: Int = 7, minute: Int = 0)
```

Wire from `MainActivity.setupReminderWork()` alongside birthday reminder.

Reuse `WinkerkContract.CHANNEL_ID`.

---

## 7. `BedieningActivity` — “Vandag” tab

### Layout

```
activity_bediening.xml
├── Toolbar ("Bediening")
├── TabLayout → "Vandag" (badge = overdue + today count)
└── ViewPager2
    └── BedieningVandagFragment
        ├── Chips: Vandag | Agterstallig | Hierdie week
        ├── RecyclerView (reminder cards)
        └── Empty state
```

### Reminder card (`item_bediening_reminder.xml`)

- Photo (Glide + `MemberGuidResolver`)
- Name, title, due date, overdue badge
- Actions: Bel | WhatsApp | Voltooi | ⋮ (uitstel, kalender, maak lid oop)

### Navigation

- `menu_main.xml` → `R.id.action_bediening`
- `MenuItemHandler` → `BedieningActivity`
- Badge on menu: lightweight COUNT query in `MainActivity.onResume()`

### `BedieningViewModel`

```kotlin
val vandagItems: StateFlow<List<ReminderWithMember>>
val overdueCount: StateFlow<Int>
val todayCount: StateFlow<Int>
```

---

## 8. “Stel herinnering” on member detail

### Layout (`lidmaat_detail.xml`)

New block after contact row:

```xml
<LinearLayout android:id="@+id/detail_bedieningBlock">
    <Button android:id="@+id/detail_stel_herinnering" />
    <TextView android:id="@+id/detail_herinnering_count" />
    <RecyclerView android:id="@+id/detail_pending_reminders" android:maxHeight="120dp" />
</LinearLayout>
```

### `StelHerinneringBottomSheet`

1. **Type:** Sjabloon | Enkel herinnering
2. **Template picker** or ad-hoc fields (title, note, date, optional time)
3. **Anchor date** (templates) — default today; suggest Argief departure date if deceased
4. **Preview** of reminders to be created
5. **Confirm** → toast *“X herinnerings gestel”*

### `LidmaatDetailViewModel`

```kotlin
val pendingReminders: LiveData<List<FollowUpReminderEntity>>
fun loadPendingReminders(memberGuid: String)
fun createFromTemplate(...)
fun createAdHoc(...)
```

Also add options menu item *“Stel herinnering”* on `LidmaatDetailActivity`.

---

## 9. CalendarManager extensions

```kotlin
fun addPastoralEvent(
    calendarId: Long,
    reminderId: String,
    memberDisplayName: String,
    title: String,
    note: String?,
    startMillis: Long,
    endMillis: Long,
    isAllDay: Boolean
): Long?

fun deletePastoralEvent(calendarEventId: Long): Boolean
fun isDuplicatePastoralEvent(calendarId: Long, reminderId: String, startMillis: Long): Boolean
```

Event description must include `wkr_reminder_id=<reminderId>`.

---

## 10. Implementation tasks

### Sprint A — Data foundation (3–4 days)

| # | Status | Task | Files |
|---|--------|------|-------|
| A1 | ✅ | Add `PASTORAL_DB` constant | `WinkerkContract.kt` |
| A2 | ✅ | Room entities, DAOs, `PastoralDatabase` v1 | `data/pastoral/**` |
| A3 | ✅ | `PastoralDatabaseInitializer` — seed templates | `PastoralDatabaseInitializer.kt` |
| A4 | ✅ | `MemberGuidResolver` | `data/pastoral/repository/MemberGuidResolver.kt` |
| A5 | ✅ | `PastoralReminderRepository` | `data/pastoral/repository/PastoralReminderRepository.kt` |
| A6 | ✅ | `PastoralDatabaseBackup` | `utils/PastoralDatabaseBackup.kt` |
| A7 | ✅ | Unit tests: template date expansion | `test/.../PastoralReminderRepositoryTest.kt` |
| A8 | ✅ | Add `room-ktx` dependency | `app/build.gradle` |

### Sprint B — Calendar (1–2 days)

| # | Task | Files |
|---|------|-------|
| B1 | Pastoral calendar CRUD + dedup | `CalendarManager.kt` |
| B2 | Settings keys | `WinkerkContract.kt`, `SettingsManager.kt` |
| B3 | `syncToCalendar()` + cleanup on complete | `PastoralReminderRepository.kt` |

### Sprint C — Notifications (2 days)

| # | Task | Files |
|---|------|-------|
| C1 | `FollowUpReminderWorker` | `workers/FollowUpReminderWorker.kt` |
| C2 | `scheduleFollowUpReminders()` | `WorkManagerHelper.kt` |
| C3 | Wire in `MainActivity` | `MainActivity.kt` |
| C4 | Notification + action receiver | `PastoralNotificationHelper.kt`, `PastoralReminderActionReceiver.kt` |

### Sprint D — BedieningActivity (3 days)

| # | Task | Files |
|---|------|-------|
| D1 | Layouts | `res/layout/activity_bediening.xml`, etc. |
| D2 | Activity + fragment + ViewModel | `BedieningActivity.kt`, `BedieningVandagFragment.kt` |
| D3 | Adapter with member join + actions | `BedieningReminderAdapter.kt` |
| D4 | Main menu entry | `menu_main.xml`, `MenuItemHandler.kt` |
| D5 | Strings (Afrikaans) | `strings.xml` |
| D6 | Manifest | `AndroidManifest.xml` |

### Sprint E — Member detail (2–3 days)

| # | Task | Files |
|---|------|-------|
| E1 | `detail_bedieningBlock` | `lidmaat_detail.xml` |
| E2 | `StelHerinneringBottomSheet` | `ui/bottomsheets/` |
| E3 | Wire activity + ViewModel | `LidmaatDetailActivity.kt`, `LidmaatDetailViewModel.kt` |
| E4 | Pending reminders mini-list | adapter |
| E5 | Options menu item | detail menu XML |

### Sprint F — PC backup (1–2 days)

| # | Task | Files |
|---|------|-------|
| F1 | Debounced backup on DB changes | repository hooks |
| F2 | Backup before congregation reload | `FileDownloadWorker.kt` |
| F3 | Include `wkr_pastoral.db` in file scan | `LaaiDatabasisActivity.kt` |
| F4 | Import with schema version check | `LaaiDatabasisActivity.kt` |
| F5 | Share / export menu action | `BedieningActivity` or admin |

### Sprint G — Polish (1 day)

| # | Task |
|---|------|
| G1 | Empty states, GUID-not-found errors |
| G2 | ProGuard rules for Room |
| G3 | Manual test checklist (§12) |

**Estimated total: 13–17 dev days**

---

## 11. String resources (Afrikaans)

```xml
<string name="bediening_title">Bediening</string>
<string name="bediening_tab_vandag">Vandag</string>
<string name="detail_bediening">Bediening</string>
<string name="detail_stel_herinnering">Stel herinnering</string>
<string name="detail_herinnering_count">%d aktiewe herinnerings</string>
<string name="herinnering_stel_sjabloon">Gebruik sjabloon</string>
<string name="herinnering_enkel">Enkel herinnering</string>
<string name="herinnering_verwysingsdatum">Verwysingsdatum</string>
<string name="herinnering_voeg_by_kalender">Voeg by kalender</string>
<string name="herinnering_voltooi">Voltooi</string>
<string name="herinnering_uitstel">Uitstel 1 dag</string>
<string name="mainmenu_bediening">Bediening</string>
<string name="bediening_agterstallig">Agterstallig</string>
<string name="bediening_geen_herinnerings">Geen herinnerings vir vandag nie</string>
```

---

## 12. Manual test checklist

- [ ] Create `NA_STERF` template from member detail → 5 reminders in DB
- [ ] `MemberGUID` resolves correct name after congregation DB reload
- [ ] Vandag tab shows due reminders; overdue styled differently
- [ ] Complete reminder → removed from Vandag; calendar event deleted if synced
- [ ] Snooze → reappears on correct day
- [ ] Notification actions work from lock screen
- [ ] `wkr_pastoral.db` appears in `getWkrDir()` after create
- [ ] Copy to PC → restore on second device → reminders intact
- [ ] Calendar: *“Voeg by kalender”* creates exactly one event; no duplicate on retry
- [ ] Congregation DB download does not delete or overwrite `wkr_pastoral.db`
- [ ] `wkr_info.db` photo data unaffected by pastoral operations

---

## 13. Phase 2 hooks (design only)

Reserve repository seams for:
- `pastoral_cases` (illness tracking)
- `visit_logs` (contact diary)
- `bereavement_cases` (deceased + bereaved GUID lists)
- Caller ID badge when open case exists

---

## 14. Summary

| Concern | Solution |
|---------|----------|
| **`wkr_info.db`** | Used today for local `WKR_Info` photo metadata; pastoral data goes in separate `wkr_pastoral.db` |
| **MemberGUID** | Required on every reminder; resolved via ContentProvider |
| **PC backup** | Copy `wkr_pastoral.db` to `getWkrDir()`; extend Laai import/export |
| **Calendar** | App owns reminders; optional mirror for timed visits with `calendar_event_id` dedup |
| **Phase 1** | Room + templates + Vandag + Stel herinnering |
