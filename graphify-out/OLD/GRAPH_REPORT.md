# Graph Report - Winkerk10Reader 2024  (2026-07-25)

## Corpus Check
- 257 files · ~430,413 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2900 nodes · 4612 edges · 206 communities (159 shown, 47 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 164 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `02a9bbac`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- LaaiDatabasisActivity
- StelHerinneringBottomSheet
- WhatsAppNotificationService
- PastoralBackupActivity
- WinkerkProvider
- CallLogActivity
- AndroidLayoutFixer
- .downloadFile
- BaseActivity
- DatabaseHelper
- PermissionsActivity
- PhoneCallMonitor
- ServiceKeepAlive
- ArgiefListActivity
- MainNavigationController
- PermissionManager
- MemberViewModel
- EventViewModel
- WidgetViewsFactory
- MainActivity
- PastoralNoteEntity
- TemplateStepEntity
- PastoralReminderRepository
- UitlegPastoraalFragment
- OproepDetailService
- MemberViewHolder
- UitlegVertoonFragment
- LidmaatDetailActivity
- SettingsManager
- FollowUpReminderEntity
- VerjaarSmsActivity
- VoegNotaByBottomSheet
- PhotoSyncManager
- Context
- CallLogDao
- PastoralWidgetFactory
- MemberListAdapter
- UitlegActivity
- LidmaatDetailViewModel
- SearchCheckBox
- FilterBottomSheet
- UitlegFunksiesFragment
- ChecksumSocketTransfer
- CongregationMemberGuidResolver
- UitlegWidgetFragment
- PastoralNoteAdapter
- StartupActions
- StepEditorDialog
- BedieningActivity
- ReminderWithMember
- BedieningVandagFragment
- MemberUtils
- SQLiteStatementValidator
- TemplateEditorActivity
- CallType
- MemberItem
- BedieningViewModel
- PastoralWidgetKeyguardFactory
- updateWidget
- LeakCanaryHelper
- MemberRepository
- MemberListInteractionController
- WorkScheduler
- FilterBox
- SearchCheckBoxPreferences
- MenuItemHandler
- BedieningReminderAdapter
- PastoralNotificationHelper
- WinkerkDbHelper
- CallerInfoResult
- src/wrap_logs.py
- wrap_logs.py
- PastoralDatabase
- IncomingCall
- TemplatePickerAdapter
- UnifiedCallMonitor
- .onCreate
- NoteCategory
- ScheduleType
- PastoralDatabaseInitializer.kt
- StepEditorAdapter
- BedieningSeksieController
- MemberPhotoController
- MainQueryMode
- TemplateManagerViewModel
- DatabaseInitializer
- .loadDataAndFinalize
- PendingReminderMiniAdapter
- SpinnerAdapter
- LidmaatPastoralSectionController
- PastoralTaskScriptManager
- ServiceUtils
- .setupBirthdayAlarmIfEnabled
- CalendarManager
- .updateSortOrder
- MainSearchFilterCoordinator
- MainViewModel
- WorkManagerHelper
- PastoralReminderRepositoryCalendarTest
- TemplateManagerActivity
- MemberPagingSource
- TemplateContext
- .onUpdate
- WidgetQueryBuilder
- PastoralBackupWorker.kt
- PhotoDownloadWorkerOld
- WidgetRefreshWorker
- CalendarManagerPastoralTest
- PastoralReminderRepositoryTest
- OproepUtils
- SmsList
- PastoralMetaDao
- .getString
- TemplateContextSchema
- PastoralReminderBadgeController
- PhotoHelper
- ServerFileValidator
- BottomCardBehavior
- MemberItemSeparator
- TemplateWithSteps
- ReminderStatus
- .put
- BedieningPagerAdapter
- TemplateManagerAdapter
- MainMenuController
- BirthdayScrollHelper
- CallNotificationDiagnostics
- NoteAuthManager
- WhatsAppContactLoader
- vector_size_check.py
- CallLogDatabase
- ContactRepository
- AlarmReceiver
- PastoralReminderActionReceiver
- Filter
- AppAuthState
- DatabaseBackupHelper
- DeviceIdManager
- BirthdayReminderWorker
- DropboxDownloadWorker
- .isTouchInsideView
- .handleEventTypeChange
- WellBehavedEditText
- .create
- CallerNameResolver
- EncryptedPrefsManager
- .getRealPathFromURI_API19
- gradlew
- ChurchInfoRepository
- UitlegCalendarSelectionListener
- .create
- .shareReminder
- PermissionRationaleHelper
- ReminderEventBus
- ThemeMode
- Utils
- create
- FilterCheckBox
- ColorObserver
- ArchiveEntity.kt
- DatumEntity.kt
- PastoralCaseActivity.kt
- ListViewSelected.kt
- PastoralDashboardViewModel.kt
- MemberItemMapper.kt
- PastoralDailyDigestWorker.kt
- .onCreateView
- CallMonitoringService
- Part 2 — Sprint B: Calendar Integration Design
- SortOrderController
- CongregationChipController
- winkerkEntry
- CallLogExporter
- 4. Room schema
- SearchCheckBox
- 1. Completed Modernizations & Cleanup
- Parameterised Queries for SOEK/FILTER SQL — Implementation Status
- Part 1 — Sprint A: Implementation Review
- WorkScheduler
- 10. Implementation tasks
- Pastoral CRM — Phase 1 Technical Specification
- B3 — PastoralReminderRepository: implement syncToCalendar(), complete, cancel, snooze cleanup
- WinkerkReader — Android Best Practice Analysis
- 2. Key features
- .onCreateView
- ColorUtils
- 0. Related local database: `wkr_info.db` (current app)
- 3. Calendar integration (no duplication)
- 7. `BedieningActivity` — “Vandag” tab
- ActivityResultCoordinator
- InitState
- 2. Local DB + PC transfer
- 8. “Stel herinnering” on member detail
- 5. Repository layer
- 6. WorkManager — notifications
- .onOptionsItemSelected

## God Nodes (most connected - your core abstractions)
1. `LaaiDatabasisActivity` - 67 edges
2. `MainActivity` - 61 edges
3. `MemberItem` - 54 edges
4. `WhatsAppNotificationService` - 47 edges
5. `PastoralReminderRepository` - 44 edges
6. `VerjaarSmsActivity` - 41 edges
7. `MemberViewModel` - 41 edges
8. `FollowUpReminderEntity` - 39 edges
9. `SettingsManager` - 39 edges
10. `StelHerinneringBottomSheet` - 34 edges

## Surprising Connections (you probably didn't know these)
- `seedDatabaseAsync()` --calls--> `PastoralDatabaseInitializer`  [INFERRED]
  app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/PastoralDatabase.kt → app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/PastoralDatabaseInitializer.kt
- `buildSystemTemplates()` --calls--> `ReminderTemplateEntity`  [INFERRED]
  app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/PastoralDatabaseInitializer.kt → app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/entities/ReminderTemplateEntity.kt
- `areContentsTheSame()` --references--> `FollowUpReminderEntity`  [EXTRACTED]
  app/src/main/kotlin/za/co/jpsoft/winkerkreader/ui/adapters/PendingReminderMiniAdapter.kt → app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/entities/FollowUpReminderEntity.kt
- `areItemsTheSame()` --references--> `FollowUpReminderEntity`  [EXTRACTED]
  app/src/main/kotlin/za/co/jpsoft/winkerkreader/ui/adapters/PendingReminderMiniAdapter.kt → app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/entities/FollowUpReminderEntity.kt
- `create()` --calls--> `CongregationMemberGuidResolver`  [INFERRED]
  app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/repository/PastoralReminderRepository.kt → app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/repository/MemberGuidResolver.kt

## Import Cycles
- None detected.

## Communities (206 total, 47 thin omitted)

### Community 0 - "LaaiDatabasisActivity"
Cohesion: 0.05
Nodes (16): checkIPv4(), BroadcastReceiver, Bundle, LiveData, MenuItem, Observer, SharedPreferences, SQLiteDatabase (+8 more)

### Community 1 - "StelHerinneringBottomSheet"
Cohesion: 0.05
Nodes (31): areContentsTheSame(), areItemsTheSame(), ListAdapter, RecyclerView, ViewGroup, ReminderPreviewAdapter, ViewHolder, FamilyMember (+23 more)

### Community 2 - "WhatsAppNotificationService"
Cohesion: 0.07
Nodes (14): CallState, ENDED, INCOMING, MISSED, OUTGOING, SCREENING, UNKNOWN, Bundle (+6 more)

### Community 3 - "PastoralBackupActivity"
Cohesion: 0.07
Nodes (19): ActivityPastoralBackupBinding, Bundle, PastoralBackupActivity, BackupListAdapter, RecyclerView, ViewGroup, ViewHolder, ViewHolder (+11 more)

### Community 4 - "WinkerkProvider"
Cohesion: 0.05
Nodes (23): ArgiefDao, Cursor, SupportSQLiteQuery, DatumDao, Cursor, SupportSQLiteQuery, MemberDao, MemberEntity (+15 more)

### Community 5 - "CallLogActivity"
Cohesion: 0.17
Nodes (7): ActivityCallLogBinding, CallLogActivity, Bundle, Menu, MenuItem, Uri, za

### Community 6 - "AndroidLayoutFixer"
Cohesion: 0.06
Nodes (32): collect_files(), main(), Walk through root_dir and yield (rel_path, list_of_file_paths)     for each dir, Write all contents of 'files' into a single .txt file placed in output_dir/rel_p, write_aggregated_file(), generate_tree(), main(), Recursively build a string representation of the directory tree. (+24 more)

### Community 7 - ".downloadFile"
Cohesion: 0.08
Nodes (16): CallLogDatabaseBackup, Context, Job, FileDownloadWorker, ByteArray, CoroutineWorker, Result, Socket (+8 more)

### Community 8 - "BaseActivity"
Cohesion: 0.16
Nodes (6): Bundle, com, View, RegistreerActivity, UserData, RegistreerBinding

### Community 9 - "DatabaseHelper"
Cohesion: 0.10
Nodes (13): DatabaseHelper, getInstance(), Context, SQLiteDatabase, PersistedActiveCall, CursorDataExtractor, ByteArray, Cursor (+5 more)

### Community 10 - "PermissionsActivity"
Cohesion: 0.05
Nodes (25): ActivityPermissionsBinding, BaseActivity, AppCompatActivity, Bundle, Bundle, RecyclerView, ViewGroup, ViewHolder (+17 more)

### Community 11 - "PhoneCallMonitor"
Cohesion: 0.16
Nodes (5): ContentResolver, Context, PhoneCallMonitor, PhoneStateListener, TelephonyManager

### Community 12 - "ServiceKeepAlive"
Cohesion: 0.08
Nodes (18): isRunning(), Context, IBinder, Intent, Notification, Service, ServiceKeepAlive, start() (+10 more)

### Community 13 - "ArgiefListActivity"
Cohesion: 0.08
Nodes (18): ArgiefListActivity, ArgiefLysAdapter, Bundle, Context, Cursor, Menu, MenuItem, View (+10 more)

### Community 15 - "PermissionManager"
Cohesion: 0.11
Nodes (3): Activity, Intent, PermissionManager

### Community 16 - "MemberViewModel"
Cohesion: 0.10
Nodes (5): AndroidViewModel, LiveData, StateFlow, MemberViewModel, PagingParams

### Community 17 - "EventViewModel"
Cohesion: 0.27
Nodes (4): EventViewModel, AndroidViewModel, Cursor, LiveData

### Community 18 - "WidgetViewsFactory"
Cohesion: 0.11
Nodes (9): Intent, RemoteViews, RemoteViewsFactory, RemoteViewsService, ListViewWidgetService, WidgetViewsFactory, Context, WidgetDataRepository (+1 more)

### Community 19 - "MainActivity"
Cohesion: 0.08
Nodes (9): ActivityMainBinding, Bundle, LiveData, Menu, MotionEvent, Observer, SearchView, WorkInfo (+1 more)

### Community 20 - "PastoralNoteEntity"
Cohesion: 0.11
Nodes (5): Flow, PastoralNoteDao, PastoralNoteEntity, Flow, PastoralNoteRepository

### Community 21 - "TemplateStepEntity"
Cohesion: 0.11
Nodes (3): ReminderTemplateDao, ReminderTemplateEntity, TemplateStepEntity

### Community 22 - "PastoralReminderRepository"
Cohesion: 0.18
Nodes (4): CalendarEventParams, MemberNotFoundException, PastoralReminderRepository, IllegalArgumentException

### Community 23 - "UitlegPastoraalFragment"
Cohesion: 0.13
Nodes (9): ArrayAdapter, Bundle, Context, Fragment, LayoutInflater, View, ViewGroup, UitlegPastoraalFragment (+1 more)

### Community 24 - "OproepDetailService"
Cohesion: 0.11
Nodes (11): FloatingViewTouchListener, isServiceRunning(), Context, IBinder, Intent, MotionEvent, Service, TextView (+3 more)

### Community 25 - "MemberViewHolder"
Cohesion: 0.15
Nodes (8): CompactViewHolder, DetailedViewHolder, com, ImageView, TextView, View, ViewGroup, MemberViewHolder

### Community 26 - "UitlegVertoonFragment"
Cohesion: 0.15
Nodes (8): Bundle, Fragment, LayoutInflater, TextView, View, ViewGroup, UitlegVertoonFragment, FragmentUitlegVertoonBinding

### Community 27 - "LidmaatDetailActivity"
Cohesion: 0.12
Nodes (7): Bundle, Menu, MenuItem, Uri, View, LidmaatDetailActivity, LidmaatDetailBinding

### Community 28 - "SettingsManager"
Cohesion: 0.08
Nodes (12): getInstance(), GoogleTasksMode, API, OFF, SHARE, Context, SharedPreferences, SettingsManager (+4 more)

### Community 29 - "FollowUpReminderEntity"
Cohesion: 0.14
Nodes (3): FollowUpReminderDao, Flow, FollowUpReminderEntity

### Community 30 - "VerjaarSmsActivity"
Cohesion: 0.17
Nodes (3): Bundle, VerjaarSmsActivity, VerjaarBinding

### Community 31 - "VoegNotaByBottomSheet"
Cohesion: 0.16
Nodes (9): BottomSheetDialogFragment, Bundle, LayoutInflater, View, ViewGroup, newInstance(), newInstanceForEdit(), VoegNotaByBottomSheet (+1 more)

### Community 32 - "PhotoSyncManager"
Cohesion: 0.12
Nodes (12): Error, T, Loading, Success, UiState, collectUiState(), Flow, T (+4 more)

### Community 33 - "Context"
Cohesion: 0.20
Nodes (13): buildWidgetRemoteViews(), getEventEmojis(), isNightMode(), AppWidgetManager, AppWidgetProvider, Bitmap, Context, IntArray (+5 more)

### Community 34 - "CallLogDao"
Cohesion: 0.13
Nodes (5): ActiveCallEntity, CallLogDao, Flow, CallLogEntity, ActiveCallReconciler

### Community 35 - "PastoralWidgetFactory"
Cohesion: 0.13
Nodes (7): Context, Intent, RemoteViews, RemoteViewsFactory, RemoteViewsService, PastoralWidgetFactory, PastoralWidgetRemoteViewsService

### Community 36 - "MemberListAdapter"
Cohesion: 0.14
Nodes (6): RecyclerView, MemberListAdapter, RecyclerView, MemberListScrollHelper, ScrollState, PagingDataAdapter

### Community 37 - "UitlegActivity"
Cohesion: 0.13
Nodes (7): ActivityUitlegBinding, ArrayAdapter, Bundle, FragmentStateAdapter, IntArray, UitlegActivity, UitlegPagerAdapter

### Community 38 - "LidmaatDetailViewModel"
Cohesion: 0.17
Nodes (7): FamilyMemberItem, MemberDetailItem, AndroidViewModel, Cursor, LiveData, Uri, LidmaatDetailViewModel

### Community 39 - "SearchCheckBox"
Cohesion: 0.21
Nodes (7): ArrayAdapter, Bundle, View, ViewGroup, SettingsActivity, SettingsAdapter, SubMenuBinding

### Community 40 - "FilterBottomSheet"
Cohesion: 0.18
Nodes (5): FilterBottomSheet, BottomSheetDialogFragment, FragmentFilterBinding, MaterialAutoCompleteTextView, MaterialSwitch

### Community 41 - "UitlegFunksiesFragment"
Cohesion: 0.08
Nodes (17): Fragment, ArrayAdapter, Bundle, Context, Fragment, LayoutInflater, View, ViewGroup (+9 more)

### Community 42 - "ChecksumSocketTransfer"
Cohesion: 0.18
Nodes (6): ChecksumSocketTransfer, ByteArray, Socket, SocketTrio, Installation, Context

### Community 43 - "CongregationMemberGuidResolver"
Cohesion: 0.19
Nodes (7): MemberDisplay, CongregationMemberGuidResolver, android, MemberGuidResolver, FollowUpReminderWorker, CoroutineWorker, Result

### Community 44 - "UitlegWidgetFragment"
Cohesion: 0.04
Nodes (45): 10. Database sync flow, 11. Call monitoring flow, 12. Background workers, 13.1 MainActivity delegation tree, 13.2 ViewModel dependencies, 13.3 Utility hub (`SettingsManager`), 13. Class interconnection map, 14. Key file index (+37 more)

### Community 45 - "PastoralNoteAdapter"
Cohesion: 0.18
Nodes (10): areContentsTheSame(), areItemsTheSame(), ListAdapter, RecyclerView, View, ViewGroup, ViewHolder, NoteUiState (+2 more)

### Community 47 - "StepEditorDialog"
Cohesion: 0.13
Nodes (14): editInstance(), Bundle, scheduleType, newInstance(), StepEditorDialog, Dialog, DialogFragment, DialogStepEditorBinding (+6 more)

### Community 48 - "BedieningActivity"
Cohesion: 0.19
Nodes (8): ActivityBedieningBinding, BedieningActivity, Bundle, Context, Intent, Menu, MenuItem, launch()

### Community 49 - "ReminderWithMember"
Cohesion: 0.14
Nodes (6): ReminderWithMember, VandagDashboard, DayBounds, Flow, areContentsTheSame(), areItemsTheSame()

### Community 50 - "BedieningVandagFragment"
Cohesion: 0.17
Nodes (7): BedieningVandagFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, FragmentBedieningVandagBinding

### Community 51 - "MemberUtils"
Cohesion: 0.17
Nodes (4): AppCompatActivity, Context, MemberUtils, Utils

### Community 53 - "TemplateEditorActivity"
Cohesion: 0.23
Nodes (7): ActivityTemplateEditorBinding, Bundle, Context, Menu, MenuItem, launch(), TemplateEditorActivity

### Community 54 - "CallType"
Cohesion: 0.11
Nodes (10): CallTypeConverter, CalendarInfo, CallType, ENDED, INCOMING, MISSED, OTHER, OUTGOING (+2 more)

### Community 55 - "MemberItem"
Cohesion: 0.20
Nodes (8): MemberItem, areContentsTheSame(), areItemsTheSame(), View, ViewGroup, QuickActionHelper, MessageComposer, PopupWindow

### Community 56 - "BedieningViewModel"
Cohesion: 0.12
Nodes (9): BedieningViewModel, Flow, SharedFlow, StateFlow, ViewModel, SnoozeOption, ONE_WEEK, THREE_DAYS (+1 more)

### Community 57 - "PastoralWidgetKeyguardFactory"
Cohesion: 0.14
Nodes (6): Intent, RemoteViews, RemoteViewsFactory, RemoteViewsService, PastoralWidgetKeyguardFactory, PastoralWidgetKeyguardRemoteViewsService

### Community 58 - "updateWidget"
Cohesion: 0.27
Nodes (14): forceRefreshWidgets(), AppWidgetManager, AppWidgetProvider, Bitmap, Context, IntArray, Intent, RemoteViews (+6 more)

### Community 59 - "LeakCanaryHelper"
Cohesion: 0.23
Nodes (7): DebugLeakCanaryHelper, DebugWinkerkReader, Application, Application, LeakCanaryHelper, NoOpLeakCanaryHelper, WinkerkReader

### Community 60 - "MemberRepository"
Cohesion: 0.17
Nodes (5): ContentResolver, Cursor, MemberRepository, fromCursor(), Cursor

### Community 61 - "MemberListInteractionController"
Cohesion: 0.23
Nodes (4): Menu, MenuItem, View, MemberListInteractionController

### Community 62 - "WorkScheduler"
Cohesion: 0.32
Nodes (5): AppInitializer, Context, LifecycleCoroutineScope, progress, success

### Community 63 - "FilterBox"
Cohesion: 0.23
Nodes (10): appendOrderByClause(), appendWhereClause(), buildMemberQuery(), buildQuery(), SqlRequest, createFromParcel(), FilterBox, Parcel (+2 more)

### Community 65 - "MenuItemHandler"
Cohesion: 0.22
Nodes (3): MenuItem, MenuItem, MenuItemHandler

### Community 66 - "BedieningReminderAdapter"
Cohesion: 0.20
Nodes (6): BedieningReminderAdapter, ListAdapter, RecyclerView, View, ViewGroup, ViewHolder

### Community 67 - "PastoralNotificationHelper"
Cohesion: 0.30
Nodes (3): Context, PendingIntent, PastoralNotificationHelper

### Community 68 - "WinkerkDbHelper"
Cohesion: 0.24
Nodes (8): closeAllInstances(), closeInstance(), getInstance(), Context, SQLiteDatabase, setDatabaseDate(), WinkerkDbHelper, SQLiteAssetHelper

### Community 69 - "CallerInfoResult"
Cohesion: 0.23
Nodes (7): CallerInfoResolver, ContentResolver, CallerInfoResult, Contact, Member, MultipleMembers, Unknown

### Community 70 - "src/wrap_logs.py"
Cohesion: 0.21
Nodes (13): already_wrapped(), collect_log_statement(), count_parens(), is_in_line_comment(), main(), process_file(), Starting at lines[start] (which begins a Log call), collect all lines     that a, Wrap a Log statement (one or more lines) with if (BuildConfig.DEBUG).     Single (+5 more)

### Community 71 - "wrap_logs.py"
Cohesion: 0.21
Nodes (13): already_wrapped(), collect_log_statement(), count_parens(), is_in_line_comment(), main(), process_file(), Starting at lines[start] (which begins a Log call), collect all lines     that a, Wrap a Log statement (one or more lines) with if (BuildConfig.DEBUG).     Single (+5 more)

### Community 72 - "PastoralDatabase"
Cohesion: 0.24
Nodes (8): buildDatabase(), getInstance(), Context, RoomDatabase, SupportSQLiteDatabase, migrate(), PastoralDatabase, seedDatabaseAsync()

### Community 73 - "IncomingCall"
Cohesion: 0.36
Nodes (4): IncomingCall, BroadcastReceiver, Context, Intent

### Community 74 - "TemplatePickerAdapter"
Cohesion: 0.26
Nodes (5): RecyclerView, ViewGroup, ViewHolder, TemplatePickerAdapter, ViewHolder

### Community 75 - "UnifiedCallMonitor"
Cohesion: 0.26
Nodes (4): ActiveCall, getInstance(), Context, UnifiedCallMonitor

### Community 76 - ".onCreate"
Cohesion: 0.46
Nodes (3): MotionEvent, View, MainSwipeGestureController

### Community 77 - "NoteCategory"
Cohesion: 0.18
Nodes (10): fromStored(), NoteCategory, ANDER, EPOS, GEBED, HUISBESOEK, KERK, KONSULTASIE (+2 more)

### Community 78 - "ScheduleType"
Cohesion: 0.18
Nodes (5): fromStored(), ScheduleType, DATE_ONLY, TIMED, PastoralReminderDates

### Community 79 - "PastoralDatabaseInitializer.kt"
Cohesion: 0.29
Nodes (9): buildSystemTemplates(), callback(), Context, RoomDatabase, originalStepsFor(), PastoralDatabaseInitializer, seedIfEmptyBlocking(), SeedTemplate (+1 more)

### Community 80 - "StepEditorAdapter"
Cohesion: 0.24
Nodes (4): RecyclerView, ViewGroup, StepEditorAdapter, ViewHolder

### Community 83 - "MainQueryMode"
Cohesion: 0.12
Nodes (12): Address, Age, Birthday, Family, Filter, MainQueryMode, Raw, Search (+4 more)

### Community 84 - "TemplateManagerViewModel"
Cohesion: 0.18
Nodes (7): Factory, SharedFlow, StateFlow, T, ViewModel, ViewModelProvider, TemplateManagerViewModel

### Community 85 - "DatabaseInitializer"
Cohesion: 0.29
Nodes (4): DatabaseInitializer, Context, SQLiteDatabase, ProgressListener

### Community 87 - "PendingReminderMiniAdapter"
Cohesion: 0.25
Nodes (7): areContentsTheSame(), areItemsTheSame(), ListAdapter, RecyclerView, ViewGroup, PendingReminderMiniAdapter, ViewHolder

### Community 88 - "SpinnerAdapter"
Cohesion: 0.20
Nodes (7): ImageView, TextView, View, ViewGroup, SpinnerAdapter, ViewHolder, BaseAdapter

### Community 92 - ".setupBirthdayAlarmIfEnabled"
Cohesion: 0.29
Nodes (6): AlarmManager, DeviceBootReceiver, BroadcastReceiver, Context, Intent, PendingIntent

### Community 93 - "CalendarManager"
Cohesion: 0.06
Nodes (30): 1. Clone the repository, 2. Open in Android Studio, 3. Configure signing (for release builds), 4. Build, 🙏 Acknowledgements, 📦 Backup & Restore, 🎂 Birthdays & Events, ☎️ Call Monitoring & Logging (+22 more)

### Community 94 - ".updateSortOrder"
Cohesion: 0.10
Nodes (21): `activity_bediening.xml`, `AndroidManifest.xml`, BedieningActivity, BedieningVandagFragment, D0 — Add `observeDueThisWeek()` to FollowUpReminderDao, D1 — Layouts, D2 — BedieningViewModel + Factory, D3 — BedieningActivity + BedieningVandagFragment (+13 more)

### Community 96 - "MainViewModel"
Cohesion: 0.22
Nodes (3): AndroidViewModel, StateFlow, MainViewModel

### Community 97 - "WorkManagerHelper"
Cohesion: 0.31
Nodes (3): Context, WorkInfo, WorkManagerHelper

### Community 99 - "TemplateManagerActivity"
Cohesion: 0.31
Nodes (5): ActivityTemplateManagerBinding, Bundle, Context, launch(), TemplateManagerActivity

### Community 100 - "MemberPagingSource"
Cohesion: 0.25
Nodes (5): MemberPagingSource, LoadParams, LoadResult, PagingSource, PagingState

### Community 101 - "TemplateContext"
Cohesion: 0.36
Nodes (4): build(), Builder, from(), TemplateContext

### Community 102 - ".onUpdate"
Cohesion: 0.36
Nodes (7): AppWidgetManager, AppWidgetProvider, Context, IntArray, PastoralWidgetKeyguardProvider, refreshWidgets(), updateWidget()

### Community 104 - "PastoralBackupWorker.kt"
Cohesion: 0.31
Nodes (7): cancel(), exportToDownloads(), Context, CoroutineWorker, Result, PastoralBackupWorker, schedule()

### Community 105 - "PhotoDownloadWorkerOld"
Cohesion: 0.33
Nodes (4): ByteArray, CoroutineWorker, Result, PhotoDownloadWorkerOld

### Community 106 - "WidgetRefreshWorker"
Cohesion: 0.28
Nodes (4): Context, CoroutineWorker, Result, WidgetRefreshWorker

### Community 109 - "OproepUtils"
Cohesion: 0.32
Nodes (3): CallRecord, ContentValues, OproepUtils

### Community 110 - "SmsList"
Cohesion: 0.36
Nodes (5): createFromParcel(), Parcel, Parcelable, newArray(), SmsList

### Community 113 - "TemplateContextSchema"
Cohesion: 0.39
Nodes (4): DateField, Field, TemplateContextSchema, Text

### Community 115 - "PhotoHelper"
Cohesion: 0.32
Nodes (3): Context, ImageView, PhotoHelper

### Community 116 - "ServerFileValidator"
Cohesion: 0.46
Nodes (3): FileCheckResult, Uri, ServerFileValidator

### Community 117 - "BottomCardBehavior"
Cohesion: 0.48
Nodes (4): BottomCardBehavior, IntArray, View, CoordinatorLayout

### Community 119 - "TemplateWithSteps"
Cohesion: 0.28
Nodes (4): Flow, TemplateWithSteps, areContentsTheSame(), areItemsTheSame()

### Community 120 - "ReminderStatus"
Cohesion: 0.33
Nodes (6): fromStored(), ReminderStatus, CANCELLED, COMPLETED, PENDING, SNOOZED

### Community 122 - "BedieningPagerAdapter"
Cohesion: 0.29
Nodes (3): BedieningPagerAdapter, Fragment, FragmentStateAdapter

### Community 123 - "TemplateManagerAdapter"
Cohesion: 0.43
Nodes (5): ListAdapter, RecyclerView, ViewGroup, TemplateManagerAdapter, ViewHolder

### Community 124 - "MainMenuController"
Cohesion: 0.27
Nodes (3): Menu, SearchView, MainMenuController

### Community 125 - "BirthdayScrollHelper"
Cohesion: 0.33
Nodes (3): BirthdayScrollHelper, Context, RecyclerView

### Community 127 - "NoteAuthManager"
Cohesion: 0.29
Nodes (3): isAuthAvailable(), Context, NoteAuthManager

### Community 128 - "WhatsAppContactLoader"
Cohesion: 0.38
Nodes (3): Context, LifecycleCoroutineScope, WhatsAppContactLoader

### Community 129 - "vector_size_check.py"
Cohesion: 0.43
Nodes (5): find_vector_files(), get_pathdata_attrs(), main(), optimize_file(), Returns list of (element_tag, byte_length, raw_string) for each     android:path

### Community 130 - "CallLogDatabase"
Cohesion: 0.22
Nodes (6): CallLogDatabase, getInstance(), Context, RoomDatabase, CallLogImporter, Context

### Community 132 - "AlarmReceiver"
Cohesion: 0.33
Nodes (4): AlarmReceiver, BroadcastReceiver, Context, Intent

### Community 133 - "PastoralReminderActionReceiver"
Cohesion: 0.33
Nodes (4): BroadcastReceiver, Context, Intent, PastoralReminderActionReceiver

### Community 134 - "Filter"
Cohesion: 0.33
Nodes (5): Filter, AGTERSTALLIG, ALS, HIERDIE_WEEK, VANDAG

### Community 138 - "BirthdayReminderWorker"
Cohesion: 0.40
Nodes (3): BirthdayReminderWorker, CoroutineWorker, Result

### Community 139 - "DropboxDownloadWorker"
Cohesion: 0.40
Nodes (3): DropboxDownloadWorker, CoroutineWorker, Result

### Community 140 - ".isTouchInsideView"
Cohesion: 0.10
Nodes (20): `bottom_sheet_stel_herinnering.xml`, Decision log, E0 — Verify GUID extra key (before writing any code), E1 — Layout block to insert into `lidmaat_detail.xml`, E2 — Layouts, E3 — LidmaatDetailPastoralViewModel + Factory, E4 — StelHerinneringBottomSheet, E5 — Adapters (+12 more)

### Community 142 - "WellBehavedEditText"
Cohesion: 0.40
Nodes (3): WellBehavedEditText, AppCompatEditText, Rect

### Community 143 - ".create"
Cohesion: 0.40
Nodes (3): BedieningViewModelFactory, T, ViewModelProvider

### Community 145 - "EncryptedPrefsManager"
Cohesion: 0.50
Nodes (3): EncryptedPrefsManager, Context, SharedPreferences

### Community 146 - ".getRealPathFromURI_API19"
Cohesion: 0.40
Nodes (3): Context, Uri, RealPathUtil

### Community 147 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 150 - ".create"
Cohesion: 0.50
Nodes (3): T, ViewModelProvider, MemberViewModelFactory

### Community 154 - "ThemeMode"
Cohesion: 0.11
Nodes (17): 1. Add `setupFollowUpReminderWork()` and call it from `setupAlarms()`, 2. Create the pastoral notification channel from `setupPermissions()`, 3. Add import, AndroidManifest changes, C0 — FollowUpReminderDao: add one-shot suspend query, C1 — FollowUpReminderWorker, C3 — MainActivity: wire-up, C4a — PastoralNotificationHelper (+9 more)

### Community 155 - "Utils"
Cohesion: 0.11
Nodes (18): 1. Project overview, 3. Architecture summary, 4. Program flow (quick reference), 5. Directory structure, 6. Key ViewModels, 7. Background processing, 8. Documentation index, 9. Modernization status (2026) (+10 more)

### Community 175 - ".onCreateView"
Cohesion: 0.16
Nodes (7): CallLog, CallLogAdapter, CallLogDiffCallback, CallLogViewHolder, RecyclerView, ViewGroup, DiffUtil

### Community 176 - "CallMonitoringService"
Cohesion: 0.16
Nodes (7): CallMonitoringService, isServiceRunning(), Context, IBinder, Intent, Notification, Service

### Community 177 - "Part 2 — Sprint B: Calendar Integration Design"
Cohesion: 0.12
Nodes (17): `addPastoralEvent()`, B1 — CalendarManager: pastoral CRUD + dedup, B2 — Settings keys (implement first — B1 and B3 depend on it), B4 — Unit tests, `CalendarManagerPastoralTest.kt`, Constants, `deletePastoralEvent()`, `isDuplicatePastoralEvent()` (+9 more)

### Community 180 - "winkerkEntry"
Cohesion: 0.31
Nodes (4): Context, WinkerkContract, winkerkEntry, BaseColumns

### Community 181 - "CallLogExporter"
Cohesion: 0.33
Nodes (3): CallLogExporter, Context, Uri

### Community 183 - "4. Room schema"
Cohesion: 0.20
Nodes (10): 4.1 Package structure, 4.2 Entities, 4.3 Database class, 4.4 Seed templates (`PastoralDatabaseInitializer`), 4.5 Key DAO queries, 4. Room schema, `follow_up_reminders`, `pastoral_meta` (singleton `id = 1`) (+2 more)

### Community 184 - "SearchCheckBox"
Cohesion: 0.31
Nodes (5): createFromParcel(), Parcel, Parcelable, newArray(), SearchCheckBox

### Community 185 - "1. Completed Modernizations & Cleanup"
Cohesion: 0.22
Nodes (9): 1. Completed Modernizations & Cleanup, 2. Future Architecture Recommendations, ✅ Activity Results (`onActivityResult`), 📅 Database Migration (SQLite to Room), ✅ External Storage Access & Scoped Storage, ✅ Manifest & Configuration Cleanup, Obsolete Android Code Modernization Report: WinkerkReader, ✅ Telephony Monitoring (`PhoneStateListener`) (+1 more)

### Community 186 - "Parameterised Queries for SOEK/FILTER SQL — Implementation Status"
Cohesion: 0.22
Nodes (9): 1. Status: COMPLETED ✅, 1. Updated `MemberViewModel.kt` Signatures & Structures, 2. Refactored Architecture, 3. Parameterized Query Generation, 4. Query Validation, 5. Summary of Benefits, Filters (`FILTER_DATA`), Parameterised Queries for SOEK/FILTER SQL — Implementation Status (+1 more)

### Community 187 - "Part 1 — Sprint A: Implementation Review"
Cohesion: 0.22
Nodes (9): A2 — Entities & DAOs, A3 — PastoralDatabaseInitializer, A4 — MemberGuidResolver, A5 — PastoralReminderRepository, A6 — PastoralDatabaseBackup, A7 — Unit tests, Overall verdict: solid foundation ✅, Part 1 — Sprint A: Implementation Review (+1 more)

### Community 189 - "10. Implementation tasks"
Cohesion: 0.25
Nodes (8): 10. Implementation tasks, Sprint A — Data foundation (3–4 days), Sprint B — Calendar (1–2 days), Sprint C — Notifications (2 days), Sprint D — BedieningActivity (3 days), Sprint E — Member detail (2–3 days), Sprint F — PC backup (1–2 days), Sprint G — Polish (1 day)

### Community 190 - "Pastoral CRM — Phase 1 Technical Specification"
Cohesion: 0.25
Nodes (8): 11. String resources (Afrikaans), 12. Manual test checklist, 13. Phase 2 hooks (design only), 14. Summary, 1. Design principles, 9. CalendarManager extensions, Implementation progress (summary), Pastoral CRM — Phase 1 Technical Specification

### Community 191 - "B3 — PastoralReminderRepository: implement syncToCalendar(), complete, cancel, snooze cleanup"
Cohesion: 0.25
Nodes (8): B3 — PastoralReminderRepository: implement syncToCalendar(), complete, cancel, snooze cleanup, `cancelReminder()` — new function, `completeReminder()` — add calendar cleanup, Constructor change — inject `CalendarManager` and `SettingsManager`, Helper: `buildCalendarEvent()`, Private cleanup helper, `snoozeReminder()` — add calendar cleanup, `syncToCalendar()` — replace stub

### Community 192 - "WinkerkReader — Android Best Practice Analysis"
Cohesion: 0.29
Nodes (7): 1. Architecture & Maintenance (Ongoing), Executive Summary, Final Status Summary, Remaining polish opportunities 🟢, What's Already Well Done ✅, WinkerkReader — Android Best Practice Analysis, 🟢 `WinkerkReader.kt` Application class — empty `onCreate`

### Community 193 - "2. Key features"
Cohesion: 0.29
Nodes (7): 2. Key features, Communication & monitoring, Database synchronization, Member browsing, Member detail, Pastoral / Bediening (CRM), Widgets

### Community 194 - ".onCreateView"
Cohesion: 0.40
Nodes (4): Bundle, LayoutInflater, View, ViewGroup

### Community 196 - "0. Related local database: `wkr_info.db` (current app)"
Cohesion: 0.40
Nodes (5): 0.1 Verdict: **Yes, `wkr_info.db` is used — but narrowly**, 0.2 Provider paths on `wkr_info.db` (in `WinkerkProvider`), 0.3 Tables (from `WinkerkContract`), 0.4 Implication for pastoral CRM, 0. Related local database: `wkr_info.db` (current app)

### Community 197 - "3. Calendar integration (no duplication)"
Cohesion: 0.40
Nodes (5): 3.1 Why schedule in the app?, 3.2 Two reminder kinds, 3.3 Anti-duplication, 3.4 User flow, 3. Calendar integration (no duplication)

### Community 198 - "7. `BedieningActivity` — “Vandag” tab"
Cohesion: 0.40
Nodes (5): 7. `BedieningActivity` — “Vandag” tab, `BedieningViewModel`, Layout, Navigation, Reminder card (`item_bediening_reminder.xml`)

### Community 199 - "ActivityResultCoordinator"
Cohesion: 0.50
Nodes (3): ActivityResultLauncher, ActivityResultCoordinator, Intent

### Community 200 - "InitState"
Cohesion: 0.50
Nodes (4): AwaitingAuth, InitState, LoadingData, Ready

### Community 201 - "2. Local DB + PC transfer"
Cohesion: 0.50
Nodes (4): 2.1 File locations, 2.2 Why `MemberGUID` is required, 2.3 PC backup strategy, 2. Local DB + PC transfer

### Community 202 - "8. “Stel herinnering” on member detail"
Cohesion: 0.50
Nodes (4): 8. “Stel herinnering” on member detail, Layout (`lidmaat_detail.xml`), `LidmaatDetailViewModel`, `StelHerinneringBottomSheet`

### Community 203 - "5. Repository layer"
Cohesion: 0.67
Nodes (3): 5. Repository layer, `MemberGuidResolver` **(✅ A4 — implemented)**, `PastoralReminderRepository` **(✅ A5 — implemented)**

### Community 204 - "6. WorkManager — notifications"
Cohesion: 0.67
Nodes (3): 6. WorkManager — notifications, `FollowUpReminderWorker`, `WorkManagerHelper`

## Knowledge Gaps
- **292 isolated node(s):** `INCOMING`, `OUTGOING`, `MISSED`, `ENDED`, `OTHER` (+287 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **47 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `BaseActivity` connect `PermissionsActivity` to `LaaiDatabasisActivity`, `PastoralBackupActivity`, `TemplateManagerActivity`, `CallLogActivity`, `UitlegActivity`, `SearchCheckBox`, `BaseActivity`, `ArgiefListActivity`, `BedieningActivity`, `MainActivity`, `TemplateEditorActivity`, `LidmaatDetailActivity`, `VerjaarSmsActivity`?**
  _High betweenness centrality (0.159) - this node is a cross-community bridge._
- **Why does `SettingsManager` connect `SettingsManager` to `LaaiDatabasisActivity`, `WhatsAppNotificationService`, `UitlegActivity`, `UitlegFunksiesFragment`, `.setupBirthdayAlarmIfEnabled`, `BedieningVandagFragment`, `MainActivity`, `UitlegPastoraalFragment`, `MemberViewHolder`, `UitlegVertoonFragment`, `LidmaatDetailActivity`, `create`?**
  _High betweenness centrality (0.147) - this node is a cross-community bridge._
- **Why does `MainActivity` connect `MainActivity` to `PermissionsActivity`, `MainNavigationController`, `PermissionManager`, `MemberViewModel`, `SettingsManager`, `MemberListAdapter`, `StartupActions`, `SortOrderController`, `CongregationChipController`, `SearchCheckBox`, `WorkScheduler`, `MemberListInteractionController`, `MenuItemHandler`, `ActivityResultCoordinator`, `InitState`, `.onCreate`, `.loadDataAndFinalize`, `MainSearchFilterCoordinator`, `MainViewModel`, `PastoralReminderBadgeController`, `MainMenuController`?**
  _High betweenness centrality (0.145) - this node is a cross-community bridge._
- **What connects `INCOMING`, `OUTGOING`, `MISSED` to the rest of the system?**
  _292 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `LaaiDatabasisActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.05153153153153153 - nodes in this community are weakly interconnected._
- **Should `StelHerinneringBottomSheet` be split into smaller, more focused modules?**
  _Cohesion score 0.052403846153846155 - nodes in this community are weakly interconnected._
- **Should `WhatsAppNotificationService` be split into smaller, more focused modules?**
  _Cohesion score 0.07017543859649122 - nodes in this community are weakly interconnected._