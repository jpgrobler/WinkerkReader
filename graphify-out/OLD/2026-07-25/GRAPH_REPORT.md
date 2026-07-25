# Graph Report - .  (2026-07-25)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 2591 nodes · 4293 edges · 176 communities (132 shown, 44 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 162 edges (avg confidence: 0.8)
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

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 73 edges
2. `LaaiDatabasisActivity` - 67 edges
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

## Communities (176 total, 44 thin omitted)

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
Cohesion: 0.07
Nodes (17): ActivityCallLogBinding, CallLog, CallLogActivity, Bundle, Menu, MenuItem, Uri, CallLogAdapter (+9 more)

### Community 6 - "AndroidLayoutFixer"
Cohesion: 0.06
Nodes (32): collect_files(), main(), Walk through root_dir and yield (rel_path, list_of_file_paths)     for each dir, Write all contents of 'files' into a single .txt file placed in output_dir/rel_p, write_aggregated_file(), generate_tree(), main(), Recursively build a string representation of the directory tree. (+24 more)

### Community 7 - ".downloadFile"
Cohesion: 0.08
Nodes (16): CallLogDatabaseBackup, Context, Job, FileDownloadWorker, ByteArray, CoroutineWorker, Result, Socket (+8 more)

### Community 8 - "BaseActivity"
Cohesion: 0.08
Nodes (13): BaseActivity, AppCompatActivity, Bundle, Bundle, com, View, RegistreerActivity, UserData (+5 more)

### Community 9 - "DatabaseHelper"
Cohesion: 0.10
Nodes (13): DatabaseHelper, getInstance(), Context, SQLiteDatabase, PersistedActiveCall, CursorDataExtractor, ByteArray, Cursor (+5 more)

### Community 10 - "PermissionsActivity"
Cohesion: 0.08
Nodes (18): ActivityPermissionsBinding, Bundle, RecyclerView, ViewGroup, ViewHolder, PermissionItem, PermissionsActivity, PermissionsAdapter (+10 more)

### Community 11 - "PhoneCallMonitor"
Cohesion: 0.08
Nodes (12): CallMonitoringService, isServiceRunning(), Context, IBinder, Intent, Notification, Service, ContentResolver (+4 more)

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
Nodes (6): AndroidViewModel, LiveData, StateFlow, MemberViewModel, PagingParams, QueryRequest

### Community 17 - "EventViewModel"
Cohesion: 0.12
Nodes (13): appendOrderByClause(), appendWhereClause(), buildMemberQuery(), buildQuery(), SqlRequest, Context, WinkerkContract, winkerkEntry (+5 more)

### Community 18 - "WidgetViewsFactory"
Cohesion: 0.11
Nodes (9): Intent, RemoteViews, RemoteViewsFactory, RemoteViewsService, ListViewWidgetService, WidgetViewsFactory, Context, WidgetDataRepository (+1 more)

### Community 19 - "MainActivity"
Cohesion: 0.10
Nodes (7): ActivityMainBinding, LiveData, Menu, Observer, SearchView, WorkInfo, MainActivity

### Community 20 - "PastoralNoteEntity"
Cohesion: 0.11
Nodes (5): Flow, PastoralNoteDao, PastoralNoteEntity, Flow, PastoralNoteRepository

### Community 21 - "TemplateStepEntity"
Cohesion: 0.10
Nodes (4): Flow, ReminderTemplateDao, ReminderTemplateEntity, TemplateStepEntity

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
Cohesion: 0.18
Nodes (5): Fragment, TextView, View, UitlegVertoonFragment, FragmentUitlegVertoonBinding

### Community 27 - "LidmaatDetailActivity"
Cohesion: 0.12
Nodes (7): Bundle, Menu, MenuItem, Uri, View, LidmaatDetailActivity, LidmaatDetailBinding

### Community 28 - "SettingsManager"
Cohesion: 0.09
Nodes (8): getInstance(), GoogleTasksMode, API, OFF, SHARE, Context, SharedPreferences, SettingsManager

### Community 29 - "FollowUpReminderEntity"
Cohesion: 0.14
Nodes (3): FollowUpReminderDao, Flow, FollowUpReminderEntity

### Community 30 - "VerjaarSmsActivity"
Cohesion: 0.15
Nodes (4): Bundle, MenuItem, VerjaarSmsActivity, VerjaarBinding

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
Cohesion: 0.11
Nodes (7): ActiveCallEntity, CallLogDao, Flow, CallLogEntity, CallLogImporter, Context, ActiveCallReconciler

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
Cohesion: 0.14
Nodes (12): ArrayAdapter, Bundle, View, ViewGroup, SettingsActivity, SettingsAdapter, createFromParcel(), Parcel (+4 more)

### Community 40 - "FilterBottomSheet"
Cohesion: 0.16
Nodes (8): FilterBottomSheet, BottomSheetDialogFragment, Bundle, LayoutInflater, View, ViewGroup, FragmentFilterBinding, MaterialAutoCompleteTextView

### Community 41 - "UitlegFunksiesFragment"
Cohesion: 0.15
Nodes (9): ArrayAdapter, Bundle, Context, Fragment, LayoutInflater, View, ViewGroup, UitlegFunksiesFragment (+1 more)

### Community 42 - "ChecksumSocketTransfer"
Cohesion: 0.18
Nodes (6): ChecksumSocketTransfer, ByteArray, Socket, SocketTrio, Installation, Context

### Community 43 - "CongregationMemberGuidResolver"
Cohesion: 0.19
Nodes (7): MemberDisplay, CongregationMemberGuidResolver, android, MemberGuidResolver, FollowUpReminderWorker, CoroutineWorker, Result

### Community 44 - "UitlegWidgetFragment"
Cohesion: 0.16
Nodes (8): Fragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, UitlegWidgetFragment, FragmentUitlegWidgetBinding

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
Cohesion: 0.24
Nodes (3): AppCompatActivity, Context, MemberUtils

### Community 53 - "TemplateEditorActivity"
Cohesion: 0.23
Nodes (7): ActivityTemplateEditorBinding, Bundle, Context, Menu, MenuItem, launch(), TemplateEditorActivity

### Community 54 - "CallType"
Cohesion: 0.16
Nodes (8): CallTypeConverter, CallType, ENDED, INCOMING, MISSED, OTHER, OUTGOING, UNKNOWN

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
Cohesion: 0.19
Nodes (5): Menu, MenuItem, View, MemberListInteractionController, MemberActionHandler

### Community 62 - "WorkScheduler"
Cohesion: 0.18
Nodes (6): AppInitializer, Context, LifecycleCoroutineScope, WorkScheduler, progress, success

### Community 63 - "FilterBox"
Cohesion: 0.21
Nodes (6): createFromParcel(), FilterBox, Parcel, Parcelable, newArray(), MaterialSwitch

### Community 64 - "SearchCheckBoxPreferences"
Cohesion: 0.15
Nodes (3): Bundle, SharedPreferences, SearchCheckBoxPreferences

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
Cohesion: 0.20
Nodes (5): ActivityResultLauncher, ActivityResultCoordinator, Intent, MotionEvent, MainSwipeGestureController

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
Cohesion: 0.17
Nodes (11): Address, Age, Birthday, Family, Filter, MainQueryMode, Raw, Search (+3 more)

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
Cohesion: 0.33
Nodes (3): TemplateWithSteps, areContentsTheSame(), areItemsTheSame()

### Community 120 - "ReminderStatus"
Cohesion: 0.33
Nodes (6): fromStored(), ReminderStatus, CANCELLED, COMPLETED, PENDING, SNOOZED

### Community 122 - "BedieningPagerAdapter"
Cohesion: 0.29
Nodes (3): BedieningPagerAdapter, Fragment, FragmentStateAdapter

### Community 123 - "TemplateManagerAdapter"
Cohesion: 0.43
Nodes (5): ListAdapter, RecyclerView, ViewGroup, TemplateManagerAdapter, ViewHolder

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
Cohesion: 0.40
Nodes (4): CallLogDatabase, getInstance(), Context, RoomDatabase

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
Cohesion: 0.50
Nodes (4): ThemeMode, DARK, LIGHT, SYSTEM

### Community 175 - ".onCreateView"
Cohesion: 0.50
Nodes (3): Bundle, LayoutInflater, ViewGroup

## Knowledge Gaps
- **74 isolated node(s):** `INCOMING`, `OUTGOING`, `MISSED`, `ENDED`, `OTHER` (+69 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **44 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SettingsManager` connect `SettingsManager` to `LaaiDatabasisActivity`, `WhatsAppNotificationService`, `UitlegActivity`, `ThemeMode`, `UitlegFunksiesFragment`, `UitlegWidgetFragment`, `.setupBirthdayAlarmIfEnabled`, `BedieningVandagFragment`, `MainActivity`, `UitlegPastoraalFragment`, `MemberViewHolder`, `UitlegVertoonFragment`, `LidmaatDetailActivity`, `create`?**
  _High betweenness centrality (0.219) - this node is a cross-community bridge._
- **Why does `BaseActivity` connect `BaseActivity` to `LaaiDatabasisActivity`, `PastoralBackupActivity`, `TemplateManagerActivity`, `CallLogActivity`, `UitlegActivity`, `SearchCheckBox`, `PermissionsActivity`, `ArgiefListActivity`, `BedieningActivity`, `MainActivity`, `TemplateEditorActivity`, `LidmaatDetailActivity`, `VerjaarSmsActivity`?**
  _High betweenness centrality (0.210) - this node is a cross-community bridge._
- **Why does `MainActivity` connect `MainActivity` to `BaseActivity`, `.isTouchInsideView`, `MainNavigationController`, `PermissionManager`, `MemberViewModel`, `SettingsManager`, `MemberListAdapter`, `SearchCheckBox`, `StartupActions`, `MemberListInteractionController`, `WorkScheduler`, `SearchCheckBoxPreferences`, `MenuItemHandler`, `.onCreate`, `.loadDataAndFinalize`, `.updateSortOrder`, `MainSearchFilterCoordinator`, `MainViewModel`, `PastoralReminderBadgeController`, `MainMenuController`?**
  _High betweenness centrality (0.193) - this node is a cross-community bridge._
- **What connects `INCOMING`, `OUTGOING`, `MISSED` to the rest of the system?**
  _74 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `LaaiDatabasisActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.05153153153153153 - nodes in this community are weakly interconnected._
- **Should `StelHerinneringBottomSheet` be split into smaller, more focused modules?**
  _Cohesion score 0.052403846153846155 - nodes in this community are weakly interconnected._
- **Should `WhatsAppNotificationService` be split into smaller, more focused modules?**
  _Cohesion score 0.07017543859649122 - nodes in this community are weakly interconnected._