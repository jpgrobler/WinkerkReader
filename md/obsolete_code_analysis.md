# Obsolete Android Code Modernization Report: WinkerkReader

> **See also:** [`architecture.md`](architecture.md) for current class layout and program flows.

This report details the successful modernization of legacy APIs, obsolete patterns, and deprecated components in the WinkerkReader codebase, referencing its current `targetSdk 35` and `compileSdk 37` status.

---

## 1. Completed Modernizations & Cleanup

### ✅ Telephony Monitoring (`PhoneStateListener`)
- **Legacy Issue**: `PhoneStateListener` is deprecated starting with Android 12 (API 31). The app relied on it exclusively to monitor incoming calls and extract phone numbers.
- **Modernized Status**: Migrated to `TelephonyCallback` on devices running Android 12+ (API 31+). For backward compatibility, the app dynamically falls back to the legacy `PhoneStateListener` on older platforms. Under the new architecture, incoming numbers are safely forwarded via intent extras from the `IncomingCall` broadcast receiver to `CallMonitoringService` (working around API 31+ number restriction rules).

### ✅ External Storage Access & Scoped Storage
- **Legacy Issue**: The app used deprecated `Environment.getExternalStorageDirectory()` and hardcoded paths like `"/storage/emulated/0/"`, which caused crashes and storage blocks under scoped storage guidelines. It also included `android:requestLegacyExternalStorage="true"` in the manifest, which is ignored on modern Android versions.
- **Modernized Status**: Fully migrated to `Context.getExternalFilesDir(null)` and `Context.filesDir` to store database files and user images. Hardcoded directories and raw environment storage calls have been completely removed. The manifest tag has also been cleaned up.

### ✅ Activity Results (`onActivityResult`)
- **Legacy Issue**: Raw overrides of `onActivityResult` and calls to `startActivityForResult` were deprecated and broke encapsulation.
- **Modernized Status**: Refactored the activity launch flows in `MainActivity` to use the modern **Activity Result API** (`registerForActivityResult`). The registration and invocation logic are neatly isolated within the `ActivityResultCoordinator` helper class.

### ✅ Time Management (Joda-Time to java.time)
- **Legacy Issue**: The project imported Joda-Time, adding library size and redundant date manipulation helpers.
- **Modernized Status**: The Joda-Time library dependency has been removed from `app/build.gradle` and all Kotlin files. The project now relies on Java 8's standardized `java.time` APIs (LocalDate, LocalDateTime, etc.) supported via Gradle core library desugaring.

### ✅ Manifest & Configuration Cleanup
- **Legacy Issue**: Obsolete tags like `android.support.PARENT_ACTIVITY` and unused services remained in the manifest.
- **Modernized Status**: Cleaned up the manifest file. Removed legacy meta-data support tags and removed unused legacy services.

---

## 2. Future Architecture Recommendations

### 📅 Database Migration (SQLite to Room)
- **Current Setup**: SQLite database accessed via `ContentResolver` / `WinkerkProvider` backed by `WinkerkDbHelper` and `DatabaseHelper`.
- **Status**: Room dependencies (`room-runtime` 2.8.4 and `room-compiler`) are added to `app/build.gradle` and are ready for use.
- **Recommendation**: As a long-term goal, write Room Entities and DAOs to replace raw SQLite cursor helper logic. Note that since the app uses `WinkerkProvider` to expose data to widgets, the Room database can serve as the backing source for the Content Provider, preserving external IPC.
