# WinkerkReader — Android Best Practice Analysis

> Analysis date: 2026-06-10 (updated 2026-06-28) | compileSdk 37 | targetSdk 35 | minSdk 26 | Kotlin/JVM 17  
> **Architecture & flows:** see [`architecture.md`](architecture.md)

---

## Executive Summary

The codebase has reached an **excellent state of maturity** following the completion of all high and medium priority architectural refactorings. All major anti-patterns identified in previous scans—including raw thread leaks, mutable global state, redundant resource instantiations, and deprecated API usages—have been successfully resolved. The application now demonstrates modern Android architecture, robust thread safety, efficient resource management, and reliable background call logging.

Severity legend: 🔴 High · 🟡 Medium · 🟢 Low / Polish

---

## 1. Architecture & Maintenance (Ongoing)

### 🟢 `WinkerkReader.kt` Application class — empty `onCreate`

**File:** `WinkerkReader.kt`

A good place to initialise WorkManager's `Configuration` (for custom thread pools or logging), set up Glide's `AppGlideModule`, or initialise Timber/logging. Currently empty.

---

## What's Already Well Done ✅

| Pattern | Status | Description |
|---|---|---|
| **MainActivity Architecture** | ✅ Refactored | Extracted monolithic code into clean coordinator delegates (`ActivityResultCoordinator`, `MainSearchFilterCoordinator`, `MainStartupCoordinator`, `MemberListInteractionController`, etc.). |
| **SQL Injection Protection** | ✅ Refactored | Migrated database queries in `MemberViewModel` to parameterized `selectionArgs`. |
| **Call Logging Telephony** | ✅ Modernized | Implemented API 31+ `TelephonyCallback` registration with safe legacy `PhoneStateListener` fallbacks. |
| **VoIP Call Logging** | ✅ Upgraded | Upgraded VoIP call detection using system notification properties (`Notification.CallStyle` APIs, category-based state parsing) and removal listeners. |
| **Missed Call Fallback** | ✅ Implemented | Queries system CallLog database as a fallback when telephony callbacks return unknown numbers. |
| **External Storage Access** | ✅ Modernized | Migrated from deprecated `getExternalStorageDirectory` and hardcoded `/storage/emulated/0/` paths to Scoped Storage `getExternalFilesDir(null)`. |
| **Database Connections** | ✅ Cleaned | Manual `.close()` anti-patterns removed to prevent resource leaks and database locking. |
| **Image Loading Pipeline** | ✅ Migrated | Replaced legacy custom executor-based image downloader with Glide. |
| **Thread Management** | ✅ Enforced | Enforced Activity executor lifecycle shutdown and coroutine scopes. |
| **Settings.Secure IPC** | ✅ Optimized | Moved secure IPC reads out of `MainActivity.onCreate` to background threads. |
| **AppSessionState** | ✅ Eliminated | Eliminated global mutable singletons in favor of unidirectional data flow. |
| **SettingsManager** | ✅ Implemented | Thread-safe Singleton pattern implemented for shared preferences. |
| **PhotoDownloadWorker** | ✅ Refactored | Migrated to `CoroutineWorker` running on `Dispatchers.IO`. |
| **SQLiteAssetHelper** | ✅ Replaced | Replaced redundant helper instantiations with `WinkerkDbHelper` singleton. |
| **Exact Alarms** | ✅ Migrated | Migrated to modern WorkManager schedulers and standard Manifest constants. |
| **Build Optimization** | ✅ Optimized | Disabled dependenciesInfo and updated compileSdk to 37 / targetSdk to 35. |
| **Legacy Cleanup** | ✅ Removed | Removed unused `MyService.kt`, Joda-Time libraries, and obsolete manifest support tags. |
| **Paging 3 member list** | ✅ Implemented | `MemberPagingSource` + `MemberViewModel.pagingDataFlowWithRefresh`; same-query refresh via `invalidate()` not Pager recreation. |
| **List scroll preservation** | ✅ Implemented | `MemberListScrollHelper`, offset-based `getRefreshKey()`, visible-item rebind for reminder icons. |
| **EventViewModel threading** | ✅ Fixed | DB query on `Dispatchers.IO`; LiveData updates on main thread (`withContext` pattern). |
| **Pastoral CRM** | ✅ Implemented | Separate Room DB, repositories, Bediening UI, widgets, WorkManager notifications. |
| **MainActivity startup** | ✅ Optimized | Single `loadInitialData()` via `AppInitializer.onReady` (no duplicate refresh on DB ready). |

---

## Final Status Summary

The WinkerkReader modernization is **substantially complete**. The application architecture adheres to modern Android standards (2026):

- **Reactive Data Flow**: ViewModels manage state; congregation data via Paging 3 + ContentProvider; pastoral data via Room Flow/LiveData.
- **Background Integrity**: WorkManager and foreground services with coroutine support and cooperative cancellation.
- **Resource Efficiency**: Connection pooling, singleton helpers, `cachedIn(viewModelScope)` for paging.
- **UI Coordination**: MainActivity split into focused coordinators (see [`architecture.md`](architecture.md) §13.1).
- **Dual Database**: Intentional split — `Winkerk.db` (members) via Provider; `wkr_pastoral.db` (CRM) via direct Room.

### Remaining polish opportunities 🟢

| Item | File | Notes |
|------|------|-------|
| Empty `Application.onCreate` extras | `WinkerkReader.kt` | Optional Timber/Glide module init |
| `MemberViewModel.loadData()` deprecated | `MemberViewModel.kt` | Legacy path still used by coordinators; migrate fully to paging StateFlows |
| `BirthdayReminderWorker` stub | `workers/BirthdayReminderWorker.kt` | SMS scheduling logic incomplete |
| Pastoral case management | `PastoralCaseActivity.kt` | Placeholder for future sprint |

The codebase is maintainable and documented in [`architecture.md`](architecture.md) for future feature work.
