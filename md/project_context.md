# WinkerkReader Project Context

WinkerkReader is an Android application for church office bearers and members to view and manage congregation data. It supports database synchronization from a central source, powerful search/filter, call monitoring, pastoral follow-up reminders, and home-screen widgets.

> **Architecture deep-dive:** See [`architecture.md`](architecture.md) for detailed program flows, Mermaid diagrams, and class interconnection maps.

---

## 1. Project overview

| Item | Value |
|------|-------|
| **Purpose** | Mobile viewer and management tool for congregation member data |
| **Platform** | Android (Kotlin) |
| **Package** | `za.co.jpsoft.winkerkreader` |
| **Target SDK** | 35 · **Compile SDK** 37 · **Min SDK** 26 |
| **Launcher** | `MainActivity` (`singleInstance`) |

### Core data domains

- **Members (Lidmate):** names, contact info, photos, family links, ward, status
- **Addresses, groups, milestones:** Doop, Belydenis, Huwelik, Verjaar
- **Archive (Argief):** former members
- **Pastoral CRM:** follow-up reminders, notes, multi-step templates (separate DB)

---

## 2. Key features

### Member browsing
- Paged member list (Paging 3, 50 rows/page) with compact or detailed layout
- Sort by Van, Gesinne, Adres, Verjaar, Huwelik, Ouderdam, Wyk
- Multi-field search and advanced filter panel
- Scroll position preserved when returning from member detail
- Pastoral reminder bell icons on members with pending follow-ups

### Member detail
- Full profile, family members, milestone dates, photo
- Edit membership status, gender, marriage status (with optional email report)
- Pastoral notes and pending reminders per member
- Quick actions: call, SMS, WhatsApp, calendar note

### Pastoral / Bediening (CRM)
- **Vandag dashboard** — overdue and due-today reminders
- **Templates** — reusable multi-step follow-up sequences
- **Notifications** — daily worker + action buttons (complete / snooze)
- **Widgets** — pastoral reminder list on home screen
- **Backup** — scheduled export of pastoral database

### Database synchronization
- WiFi Direct / network transfer (port 49514)
- Cloud: Dropbox, OneDrive, Google Drive, SharePoint
- Local import via Storage Access Framework
- Scheduled background downloads (WorkManager)

### Communication & monitoring
- Caller ID overlay for known numbers
- Regular + VoIP call logging (WhatsApp, Meet, Teams, …)
- Birthday / milestone SMS tool (`VerjaarSmsActivity`)
- WhatsApp contact mapping for list icons

### Widgets
- **Birthday widget** — today's Verjaar, Doop, Huwelik, Belydenis, Sterf
- **Pastoral widget** — pending follow-up reminders

---

## 3. Architecture summary

### Layered structure

```
UI (Activities, Fragments, Adapters, Bottom sheets)
    ↓
ViewModels (MemberViewModel, BedieningViewModel, …)
    ↓
Repositories / PagingSource / ContentResolver
    ↓
WinkerkProvider → WinkerkDatabase (congregation)
PastoralDatabase (pastoral — direct Room access)
```

### Dual database

| Database | File | Access |
|----------|------|--------|
| Congregation | `Winkerk.db` | `ContentProvider` (`WinkerkProvider`) |
| Pastoral | `wkr_pastoral.db` | Room DAOs directly |

Members are linked to pastoral records via **`memberGuid`** (UUID).

### MainActivity decomposition

`MainActivity` is the hub activity. Logic is split into coordinators:

| Coordinator | Responsibility |
|-------------|----------------|
| `MainStartupCoordinator` | Permissions, DB init, first data load |
| `MainSearchFilterCoordinator` | Search, filter, query mode → `MemberViewModel` |
| `MemberListInteractionController` | Row tap/long-press, popup menu, pastoral sheets |
| `MainMenuController` | Options menu, SearchView, active/inactive toggle |
| `ActivityResultCoordinator` | Search/filter activity results |
| `MemberListScrollHelper` | Save/restore list scroll position |
| `MainNavigationController` | All inter-activity navigation |

---

## 4. Program flow (quick reference)

### App cold start
```
WinkerkReader → AppInitializer → DatabaseInitializer + WorkScheduler + widgets
MainActivity → AppAuthGuard → MainStartupCoordinator → setupViewModel
             → onReady: loadInitialData (once)
```

### Member list load
```
MainSearchFilterCoordinator.refresh()
  → MemberViewModel.loadData(MainQueryMode)
    → MemberQueryBuilder → MemberPagingSource → WinkerkProvider
  → MemberListAdapter.submitData(lifecycle, pagingData)
```

Same-query refresh uses `PagingSource.invalidate()` + `getRefreshKey()` to keep scroll position.

### Open member detail → back
```
Popup menu → MemberUtils.openMemberDetail() → LidmaatDetailActivity
MainActivity.onPause() → MemberListScrollHelper.saveScrollState()
MainActivity.onResume() → restore scroll (no full list reload)
```

### Pastoral reminder
```
StelHerinneringBottomSheet → PastoralReminderRepository → PastoralDatabase
                          → optional CalendarManager
                          → widget refresh + list bell icons on resume
```

### Widget → Verjaar SMS
```
WinkerkReaderWidgetProvider → ListViewWidgetService → WidgetDataRepository
Row tap → VerjaarSmsActivity → EventViewModel.loadEventData() (IO + main thread LiveData)
```

Full sequence diagrams: [`architecture.md`](architecture.md)

---

## 5. Directory structure

```
app/src/main/kotlin/za/co/jpsoft/winkerkreader/
├── WinkerkReader.kt                 Application entry
├── data/
│   ├── WinkerkContract.kt           URIs, column names, prefs keys
│   ├── WinkerkProvider.kt           ContentProvider → Room
│   ├── MemberRepository.kt          Legacy full-list queries
│   ├── MemberPagingSource.kt        Paging 3 source
│   ├── MemberQueryBuilder.kt        SQL builder per event type
│   ├── models/                      MemberItem, FilterBox, …
│   ├── room/                        WinkerkDatabase, entities, DAOs
│   └── pastoral/                    PastoralDatabase, entities, DAOs, repositories
├── services/                        Foreground services, receivers
├── ui/
│   ├── activities/                  MainActivity, LidmaatDetail, Bediening, …
│   ├── fragments/                   BedieningVandag, Uitleg tabs
│   ├── viewmodels/                  MemberViewModel, BedieningViewModel, …
│   ├── adapters/                    MemberListAdapter, BedieningReminderAdapter, …
│   ├── bottomsheets/              StelHerinnering, VoegNotaBy
│   └── common/                      UiState helpers
├── utils/                           SettingsManager, navigation, permissions, calendar
├── widget/                          Widget providers + data repositories
└── workers/                         WorkManager background tasks
```

---

## 6. Key ViewModels

| ViewModel | Screen(s) | Data source |
|-----------|-----------|-------------|
| `MemberViewModel` | MainActivity, VerjaarSmsActivity | Paging + ContentProvider |
| `MainViewModel` | MainActivity | Pastoral badge count, filter UI state |
| `LidmaatDetailViewModel` | LidmaatDetailActivity | ContentProvider |
| `LidmaatDetailPastoralViewModel` | Detail + bottom sheets | PastoralDatabase |
| `BedieningViewModel` | BedieningActivity | PastoralReminderRepository |
| `EventViewModel` | VerjaarSmsActivity | ContentProvider (today's events) |
| `ArgiefViewModel` | ArgiefListActivity | ContentProvider (archive) |
| `TemplateManagerViewModel` | TemplateManagerActivity | ReminderTemplateDao |

---

## 7. Background processing

| Mechanism | Used for |
|-----------|----------|
| **Foreground services** | Call monitoring, caller ID overlay, notification listener |
| **WorkManager** | Widget refresh, pastoral notifications, DB download, backup, photos |
| **AlarmManager** | Widget daily update (01:00), birthday SMS alarms |
| **Coroutines** | ViewModels, repositories, EventViewModel queries |

---

## 8. Documentation index

| File | Description |
|------|-------------|
| **[architecture.md](architecture.md)** | **Detailed flows, diagrams, class interconnections** |
| [android_best_practice_analysis.md](android_best_practice_analysis.md) | Code quality & modernization review |
| [obsolete_code_analysis.md](obsolete_code_analysis.md) | Legacy/dead code inventory |
| [parameterised_queries_plan.md](parameterised_queries_plan.md) | SQL parameterization plan |
| [pastoral_crm_phase1_spec.md](pastoral_crm_phase1_spec.md) | Pastoral CRM Phase 1 spec |
| [pastoral_crm_sprint_b_spec.md](pastoral_crm_sprint_b_spec.md) | Sprint B — calendar |
| [pastoral_crm_sprint_c_spec.md](pastoral_crm_sprint_c_spec.md) | Sprint C — notifications |
| [pastoral_crm_sprint_d_spec.md](pastoral_crm_sprint_d_spec.md) | Sprint D — Bediening UI |
| [pastoral_crm_sprint_e_spec.md](pastoral_crm_sprint_e_spec.md) | Sprint E — member detail pastoral |

---

## 9. Modernization status (2026)

| Area | Status |
|------|--------|
| MainActivity coordinator refactor | ✅ Complete |
| SQL parameterization | ✅ Complete |
| Scoped storage migration | ✅ Complete |
| Activity Result API | ✅ Complete |
| Telephony / VoIP modernization | ✅ Complete |
| Paging 3 member list | ✅ Complete |
| Scroll preservation (detail return) | ✅ Complete — `MemberListScrollHelper`, `invalidate()` paging, visible-item rebind |
| Pastoral CRM (Phase 1 + sprints) | ✅ Substantially implemented |
| Room for congregation data | ✅ WinkerkDatabase v4 via Provider |
| EventViewModel thread safety | ✅ IO on `Dispatchers.IO`, LiveData on main thread |

See [android_best_practice_analysis.md](android_best_practice_analysis.md) for the full review.
