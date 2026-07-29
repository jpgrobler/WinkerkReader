# Graph Report - .  (2026-07-29)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 2707 nodes · 4417 edges · 195 communities (143 shown, 52 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 189 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `fdd8aac1`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- LaaiDatabasisActivity
- StelHerinneringBottomSheet
- PermissionsActivity
- WhatsAppNotificationService
- PastoralBackupActivity
- MemberDao
- AndroidLayoutFixer
- MainActivity
- .delay
- .put
- PhoneCallMonitor
- ServiceKeepAlive
- ArgiefListActivity
- MainNavigationController
- PermissionManager
- WidgetViewsFactory
- UitlegPastoraalFragment
- MemberViewHolder
- PastoralNoteEntity
- PastoralReminderRepository
- MemberViewModel
- OproepDetailService
- FilterBottomSheet
- UitlegVertoonFragment
- ReminderTemplateDao
- LidmaatDetailActivity
- VoegNotaByBottomSheet
- SettingsManager
- FollowUpReminderEntity
- DatabaseImportController
- Context
- CallLogDao
- PastoralWidgetFactory
- MainSearchFilterCoordinator
- VerjaarSmsActivity
- CallLogAdapter
- UitlegActivity
- LidmaatDetailViewModel
- UitlegFunksiesFragment
- BaseActivity
- MemberListAdapter
- FilterBox
- CongregationMemberGuidResolver
- VoipCallInfoExtractor
- PastoralNoteAdapter
- StartupActions
- StepEditorDialog
- BedieningActivity
- CallLogActivity
- BedieningVandagFragment
- MemberUtils
- SQLiteStatementValidator
- TemplateEditorActivity
- CalendarManager
- DropboxDownloadController
- MemberItem
- MainQueryMode
- BedieningViewModel
- PastoralWidgetKeyguardFactory
- updateWidget
- LeakCanaryHelper
- MemberRepository
- .initialize
- MenuItemHandler
- ReminderWithMember
- PastoralNotificationHelper
- IllegalArgumentException
- CallerInfoResult
- src/wrap_logs.py
- wrap_logs.py
- PastoralDatabase
- EventViewModel
- IncomingCall
- TemplateWithSteps
- MemberListInteractionController
- UnifiedCallMonitor
- NoteCategory
- ScheduleType
- LidmaatDetailPastoralViewModel
- MainMenuController
- SettingsActivity
- TemplateStepEntity
- BedieningSeksieController
- MemberPhotoController
- SortOrderController
- TemplateManagerViewModel
- DatabaseInitializer
- PendingReminderMiniAdapter
- SpinnerAdapter
- LidmaatPastoralSectionController
- PastoralTaskScriptManager
- ServiceUtils
- .setupBirthdayAlarmIfEnabled
- DatabaseMigrationHelper
- CallMonitoringService
- .getString
- CongregationChipController
- MainViewModel
- LocalDatabaseFileController
- WorkManagerHelper
- PastoralReminderRepositoryCalendarTest
- TemplateManagerAdapter
- MemberPagingSource
- PastoralDatabaseInitializer.kt
- TemplateContext
- NetworkTransferController
- .onUpdate
- WidgetQueryBuilder
- TemplateRepository
- WorkScheduler
- WidgetRefreshWorker
- CalendarManagerPastoralTest
- PastoralReminderRepositoryTest
- ReminderQueryRepository
- SmsList
- PastoralMetaDao
- TemplateContextSchema
- MainSwipeGestureController
- PastoralReminderBadgeController
- PhotoHelper
- ServerFileValidator
- BottomCardBehavior
- MemberItemSeparator
- ReminderStatus
- TemplateContextFormBuilder
- BedieningPagerAdapter
- CallLogExporter
- BirthdayScrollHelper
- CalendarSyncManager
- NoteAuthManager
- GoogleTaskSyncManager
- WhatsAppContactLoader
- vector_size_check.py
- CallLogDatabase
- CollapsibleCardController
- ContactRepository
- AlarmReceiver
- PastoralReminderActionReceiver
- VoipCallStateDetector
- AppAuthState
- DatabaseBackupHelper
- DeviceIdManager
- BirthdayReminderWorker
- DropboxDownloadWorker
- Utils
- WellBehavedEditText
- .create
- BackPressHandler
- CallerNameResolver
- ColorUtils
- EncryptedPrefsManager
- .getRealPathFromURI_API19
- gradlew
- PhotoSyncController
- ChurchInfoRepository
- winkerkEntry
- CloudUrlTransformer
- .create
- .shareReminder
- PermissionRationaleHelper
- ReminderEventBus
- InitState
- FamilyMemberSpinnerController
- create
- FilterCheckBox
- ColorObserver
- ArchiveEntity.kt
- DatumEntity.kt
- PastoralCaseActivity.kt
- SearchCheckBoxPreferences
- ListViewSelected.kt
- PastoralDashboardViewModel.kt
- MemberItemMapper.kt
- PastoralDailyDigestWorker.kt
- .create
- SearchCheckBox
- .create
- ReminderPreviewAdapter
- buildMemberQuery
- ActivityResultCoordinator
- VoipNotificationHandler
- .onPrepareOptionsMenu
- VoipCallTracker
- .onCreate
- FollowUpReminderWorker
- .navigateBackToMain

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 60 edges
2. `MemberItem` - 53 edges
3. `MemberViewModel` - 42 edges
4. `VerjaarSmsActivity` - 41 edges
5. `FollowUpReminderEntity` - 40 edges
6. `SettingsManager` - 38 edges
7. `LaaiDatabasisActivity` - 35 edges
8. `MainNavigationController` - 32 edges
9. `PermissionManager` - 30 edges
10. `PastoralReminderRepository` - 29 edges

## Surprising Connections (you probably didn't know these)
- `seedDatabaseAsync()` --calls--> `PastoralDatabaseInitializer`  [INFERRED]
  app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/PastoralDatabase.kt → app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/PastoralDatabaseInitializer.kt
- `buildSystemTemplates()` --calls--> `ReminderTemplateEntity`  [INFERRED]
  app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/PastoralDatabaseInitializer.kt → app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/entities/ReminderTemplateEntity.kt
- `areContentsTheSame()` --references--> `FollowUpReminderEntity`  [EXTRACTED]
  app/src/main/kotlin/za/co/jpsoft/winkerkreader/ui/adapters/PendingReminderMiniAdapter.kt → app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/entities/FollowUpReminderEntity.kt
- `areItemsTheSame()` --references--> `FollowUpReminderEntity`  [EXTRACTED]
  app/src/main/kotlin/za/co/jpsoft/winkerkreader/ui/adapters/PendingReminderMiniAdapter.kt → app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/entities/FollowUpReminderEntity.kt
- `create()` --calls--> `CalendarSyncManager`  [INFERRED]
  app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/repository/PastoralReminderRepository.kt → app/src/main/kotlin/za/co/jpsoft/winkerkreader/data/pastoral/repository/CalendarSyncManager.kt

## Import Cycles
- None detected.

## Communities (195 total, 52 thin omitted)

### Community 0 - "LaaiDatabasisActivity"
Cohesion: 0.12
Nodes (4): Bundle, LaaidatabasisBinding, SharedPreferences, LaaiDatabasisActivity

### Community 1 - "StelHerinneringBottomSheet"
Cohesion: 0.13
Nodes (11): BottomSheetDialogFragment, Bundle, LayoutInflater, View, ViewGroup, Mode, ADHOC, TEMPLATE (+3 more)

### Community 2 - "PermissionsActivity"
Cohesion: 0.08
Nodes (18): ActivityPermissionsBinding, Bundle, RecyclerView, ViewGroup, ViewHolder, PermissionItem, PermissionsActivity, PermissionsAdapter (+10 more)

### Community 3 - "WhatsAppNotificationService"
Cohesion: 0.16
Nodes (4): Notification, StatusBarNotification, WhatsAppNotificationService, NotificationListenerService

### Community 4 - "PastoralBackupActivity"
Cohesion: 0.07
Nodes (19): ActivityPastoralBackupBinding, Bundle, PastoralBackupActivity, BackupListAdapter, RecyclerView, ViewGroup, ViewHolder, ViewHolder (+11 more)

### Community 5 - "MemberDao"
Cohesion: 0.05
Nodes (23): ArgiefDao, Cursor, SupportSQLiteQuery, DatumDao, Cursor, SupportSQLiteQuery, MemberDao, MemberEntity (+15 more)

### Community 6 - "AndroidLayoutFixer"
Cohesion: 0.06
Nodes (32): collect_files(), main(), Walk through root_dir and yield (rel_path, list_of_file_paths)     for each dir, Write all contents of 'files' into a single .txt file placed in output_dir/rel_p, write_aggregated_file(), generate_tree(), main(), Recursively build a string representation of the directory tree. (+24 more)

### Community 7 - "MainActivity"
Cohesion: 0.09
Nodes (8): ActivityMainBinding, Bundle, LiveData, MotionEvent, Observer, SearchView, WorkInfo, MainActivity

### Community 8 - ".delay"
Cohesion: 0.06
Nodes (21): CallLogDatabaseBackup, Context, Job, FileDownloadWorker, ByteArray, CoroutineWorker, Result, Socket (+13 more)

### Community 9 - ".put"
Cohesion: 0.05
Nodes (25): DatabaseHelper, getInstance(), Context, SQLiteDatabase, PersistedActiveCall, CallRecord, CallNotificationDiagnostics, Context (+17 more)

### Community 10 - "PhoneCallMonitor"
Cohesion: 0.18
Nodes (4): Context, PhoneCallMonitor, PhoneStateListener, TelephonyManager

### Community 11 - "ServiceKeepAlive"
Cohesion: 0.08
Nodes (18): isRunning(), Context, IBinder, Intent, Notification, Service, ServiceKeepAlive, start() (+10 more)

### Community 12 - "ArgiefListActivity"
Cohesion: 0.08
Nodes (18): ArgiefListActivity, ArgiefLysAdapter, Bundle, Context, Cursor, Menu, MenuItem, View (+10 more)

### Community 14 - "PermissionManager"
Cohesion: 0.11
Nodes (3): Activity, Intent, PermissionManager

### Community 15 - "WidgetViewsFactory"
Cohesion: 0.11
Nodes (9): Intent, RemoteViews, RemoteViewsFactory, RemoteViewsService, ListViewWidgetService, WidgetViewsFactory, Context, WidgetDataRepository (+1 more)

### Community 16 - "UitlegPastoraalFragment"
Cohesion: 0.13
Nodes (9): ArrayAdapter, Bundle, Context, Fragment, LayoutInflater, View, ViewGroup, UitlegPastoraalFragment (+1 more)

### Community 17 - "MemberViewHolder"
Cohesion: 0.15
Nodes (8): CompactViewHolder, DetailedViewHolder, com, ImageView, TextView, View, ViewGroup, MemberViewHolder

### Community 18 - "PastoralNoteEntity"
Cohesion: 0.11
Nodes (5): Flow, PastoralNoteDao, PastoralNoteEntity, Flow, PastoralNoteRepository

### Community 19 - "PastoralReminderRepository"
Cohesion: 0.10
Nodes (3): VandagDashboard, Flow, PastoralReminderRepository

### Community 20 - "MemberViewModel"
Cohesion: 0.09
Nodes (6): AndroidViewModel, LiveData, StateFlow, MemberViewModel, PagingParams, QueryRequest

### Community 21 - "OproepDetailService"
Cohesion: 0.11
Nodes (11): FloatingViewTouchListener, isServiceRunning(), Context, IBinder, Intent, MotionEvent, Service, TextView (+3 more)

### Community 22 - "FilterBottomSheet"
Cohesion: 0.16
Nodes (8): FilterBottomSheet, BottomSheetDialogFragment, Bundle, LayoutInflater, View, ViewGroup, FragmentFilterBinding, MaterialAutoCompleteTextView

### Community 23 - "UitlegVertoonFragment"
Cohesion: 0.15
Nodes (8): Bundle, Fragment, LayoutInflater, TextView, View, ViewGroup, UitlegVertoonFragment, FragmentUitlegVertoonBinding

### Community 24 - "ReminderTemplateDao"
Cohesion: 0.12
Nodes (3): Flow, ReminderTemplateDao, ReminderTemplateEntity

### Community 25 - "LidmaatDetailActivity"
Cohesion: 0.16
Nodes (5): Menu, MenuItem, View, LidmaatDetailActivity, LidmaatDetailBinding

### Community 26 - "VoegNotaByBottomSheet"
Cohesion: 0.15
Nodes (10): BottomSheetDialogFragment, Bundle, LayoutInflater, View, ViewGroup, newInstance(), newInstanceForEdit(), VoegNotaByBottomSheet (+2 more)

### Community 27 - "SettingsManager"
Cohesion: 0.08
Nodes (12): getInstance(), GoogleTasksMode, API, OFF, SHARE, Context, SharedPreferences, SettingsManager (+4 more)

### Community 28 - "FollowUpReminderEntity"
Cohesion: 0.15
Nodes (3): FollowUpReminderDao, Flow, FollowUpReminderEntity

### Community 29 - "DatabaseImportController"
Cohesion: 0.07
Nodes (19): Error, T, Loading, Success, UiState, collectUiState(), Flow, T (+11 more)

### Community 30 - "Context"
Cohesion: 0.20
Nodes (13): buildWidgetRemoteViews(), getEventEmojis(), isNightMode(), AppWidgetManager, AppWidgetProvider, Bitmap, Context, IntArray (+5 more)

### Community 31 - "CallLogDao"
Cohesion: 0.13
Nodes (5): ActiveCallEntity, CallLogDao, Flow, CallLogEntity, ActiveCallReconciler

### Community 32 - "PastoralWidgetFactory"
Cohesion: 0.13
Nodes (7): Context, Intent, RemoteViews, RemoteViewsFactory, RemoteViewsService, PastoralWidgetFactory, PastoralWidgetRemoteViewsService

### Community 34 - "VerjaarSmsActivity"
Cohesion: 0.08
Nodes (7): Bundle, MenuItem, SharedPreferences, VerjaarSmsActivity, MemberActionHandler, SmsManager, VerjaarBinding

### Community 35 - "CallLogAdapter"
Cohesion: 0.16
Nodes (7): CallLog, CallLogAdapter, CallLogDiffCallback, CallLogViewHolder, RecyclerView, ViewGroup, DiffUtil

### Community 36 - "UitlegActivity"
Cohesion: 0.11
Nodes (8): ActivityUitlegBinding, ArrayAdapter, Bundle, FragmentStateAdapter, IntArray, UitlegActivity, UitlegPagerAdapter, UitlegCalendarSelectionListener

### Community 37 - "LidmaatDetailViewModel"
Cohesion: 0.21
Nodes (5): FamilyMemberItem, AndroidViewModel, LiveData, Uri, LidmaatDetailViewModel

### Community 38 - "UitlegFunksiesFragment"
Cohesion: 0.08
Nodes (17): Fragment, ArrayAdapter, Bundle, Context, Fragment, LayoutInflater, View, ViewGroup (+9 more)

### Community 39 - "BaseActivity"
Cohesion: 0.08
Nodes (13): BaseActivity, AppCompatActivity, Bundle, Bundle, com, View, RegistreerActivity, UserData (+5 more)

### Community 40 - "MemberListAdapter"
Cohesion: 0.14
Nodes (6): RecyclerView, MemberListAdapter, RecyclerView, MemberListScrollHelper, ScrollState, PagingDataAdapter

### Community 41 - "FilterBox"
Cohesion: 0.23
Nodes (6): createFromParcel(), FilterBox, Parcel, Parcelable, newArray(), MaterialSwitch

### Community 42 - "CongregationMemberGuidResolver"
Cohesion: 0.30
Nodes (4): MemberDisplay, CongregationMemberGuidResolver, android, MemberGuidResolver

### Community 43 - "VoipCallInfoExtractor"
Cohesion: 0.18
Nodes (4): Bundle, ContentResolver, Uri, VoipCallInfoExtractor

### Community 44 - "PastoralNoteAdapter"
Cohesion: 0.18
Nodes (10): areContentsTheSame(), areItemsTheSame(), ListAdapter, RecyclerView, View, ViewGroup, ViewHolder, NoteUiState (+2 more)

### Community 46 - "StepEditorDialog"
Cohesion: 0.13
Nodes (14): editInstance(), Bundle, scheduleType, newInstance(), StepEditorDialog, Dialog, DialogFragment, DialogStepEditorBinding (+6 more)

### Community 47 - "BedieningActivity"
Cohesion: 0.19
Nodes (8): ActivityBedieningBinding, BedieningActivity, Bundle, Context, Intent, Menu, MenuItem, launch()

### Community 48 - "CallLogActivity"
Cohesion: 0.23
Nodes (5): ActivityCallLogBinding, CallLogActivity, Bundle, Menu, za

### Community 49 - "BedieningVandagFragment"
Cohesion: 0.17
Nodes (7): BedieningVandagFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, FragmentBedieningVandagBinding

### Community 50 - "MemberUtils"
Cohesion: 0.24
Nodes (3): AppCompatActivity, Context, MemberUtils

### Community 52 - "TemplateEditorActivity"
Cohesion: 0.23
Nodes (7): ActivityTemplateEditorBinding, Bundle, Context, Menu, MenuItem, launch(), TemplateEditorActivity

### Community 53 - "CalendarManager"
Cohesion: 0.11
Nodes (10): CallTypeConverter, CalendarInfo, CallType, ENDED, INCOMING, MISSED, OTHER, OUTGOING (+2 more)

### Community 55 - "MemberItem"
Cohesion: 0.20
Nodes (8): MemberItem, areContentsTheSame(), areItemsTheSame(), View, ViewGroup, QuickActionHelper, MessageComposer, PopupWindow

### Community 56 - "MainQueryMode"
Cohesion: 0.15
Nodes (11): Address, Age, Birthday, Family, Filter, MainQueryMode, Raw, Search (+3 more)

### Community 57 - "BedieningViewModel"
Cohesion: 0.09
Nodes (14): BedieningViewModel, Filter, AGTERSTALLIG, ALS, HIERDIE_WEEK, VANDAG, Flow, SharedFlow (+6 more)

### Community 58 - "PastoralWidgetKeyguardFactory"
Cohesion: 0.14
Nodes (6): Intent, RemoteViews, RemoteViewsFactory, RemoteViewsService, PastoralWidgetKeyguardFactory, PastoralWidgetKeyguardRemoteViewsService

### Community 59 - "updateWidget"
Cohesion: 0.27
Nodes (14): forceRefreshWidgets(), AppWidgetManager, AppWidgetProvider, Bitmap, Context, IntArray, Intent, RemoteViews (+6 more)

### Community 60 - "LeakCanaryHelper"
Cohesion: 0.23
Nodes (7): DebugLeakCanaryHelper, DebugWinkerkReader, Application, Application, LeakCanaryHelper, NoOpLeakCanaryHelper, WinkerkReader

### Community 61 - "MemberRepository"
Cohesion: 0.17
Nodes (5): ContentResolver, za, MemberRepository, fromCursor(), Cursor

### Community 62 - ".initialize"
Cohesion: 0.32
Nodes (5): AppInitializer, Context, LifecycleCoroutineScope, progress, success

### Community 63 - "MenuItemHandler"
Cohesion: 0.22
Nodes (3): MenuItem, MenuItem, MenuItemHandler

### Community 64 - "ReminderWithMember"
Cohesion: 0.16
Nodes (9): ReminderWithMember, areContentsTheSame(), areItemsTheSame(), BedieningReminderAdapter, ListAdapter, RecyclerView, View, ViewGroup (+1 more)

### Community 65 - "PastoralNotificationHelper"
Cohesion: 0.30
Nodes (3): Context, PendingIntent, PastoralNotificationHelper

### Community 66 - "IllegalArgumentException"
Cohesion: 0.23
Nodes (3): MemberNotFoundException, ReminderCrudRepository, IllegalArgumentException

### Community 67 - "CallerInfoResult"
Cohesion: 0.21
Nodes (8): CallerInfoResolver, android, Context, CallerInfoResult, Contact, Member, MultipleMembers, Unknown

### Community 68 - "src/wrap_logs.py"
Cohesion: 0.21
Nodes (13): already_wrapped(), collect_log_statement(), count_parens(), is_in_line_comment(), main(), process_file(), Starting at lines[start] (which begins a Log call), collect all lines     that a, Wrap a Log statement (one or more lines) with if (BuildConfig.DEBUG).     Single (+5 more)

### Community 69 - "wrap_logs.py"
Cohesion: 0.21
Nodes (13): already_wrapped(), collect_log_statement(), count_parens(), is_in_line_comment(), main(), process_file(), Starting at lines[start] (which begins a Log call), collect all lines     that a, Wrap a Log statement (one or more lines) with if (BuildConfig.DEBUG).     Single (+5 more)

### Community 70 - "PastoralDatabase"
Cohesion: 0.24
Nodes (8): buildDatabase(), getInstance(), Context, RoomDatabase, SupportSQLiteDatabase, migrate(), PastoralDatabase, seedDatabaseAsync()

### Community 71 - "EventViewModel"
Cohesion: 0.27
Nodes (4): EventViewModel, AndroidViewModel, Cursor, LiveData

### Community 72 - "IncomingCall"
Cohesion: 0.36
Nodes (4): IncomingCall, BroadcastReceiver, Context, Intent

### Community 73 - "TemplateWithSteps"
Cohesion: 0.16
Nodes (8): TemplateWithSteps, areContentsTheSame(), areItemsTheSame(), RecyclerView, ViewGroup, ViewHolder, TemplatePickerAdapter, ViewHolder

### Community 74 - "MemberListInteractionController"
Cohesion: 0.26
Nodes (4): Menu, MenuItem, View, MemberListInteractionController

### Community 75 - "UnifiedCallMonitor"
Cohesion: 0.26
Nodes (4): ActiveCall, getInstance(), Context, UnifiedCallMonitor

### Community 76 - "NoteCategory"
Cohesion: 0.18
Nodes (10): fromStored(), NoteCategory, ANDER, EPOS, GEBED, HUISBESOEK, KERK, KONSULTASIE (+2 more)

### Community 77 - "ScheduleType"
Cohesion: 0.15
Nodes (5): fromStored(), ScheduleType, DATE_ONLY, TIMED, PastoralReminderDates

### Community 78 - "LidmaatDetailPastoralViewModel"
Cohesion: 0.18
Nodes (7): areContentsTheSame(), areItemsTheSame(), SharedFlow, StateFlow, ViewModel, LidmaatDetailPastoralViewModel, PreviewItem

### Community 79 - "MainMenuController"
Cohesion: 0.27
Nodes (3): Menu, SearchView, MainMenuController

### Community 80 - "SettingsActivity"
Cohesion: 0.21
Nodes (7): ArrayAdapter, Bundle, View, ViewGroup, SettingsActivity, SettingsAdapter, SubMenuBinding

### Community 81 - "TemplateStepEntity"
Cohesion: 0.16
Nodes (5): TemplateStepEntity, RecyclerView, ViewGroup, StepEditorAdapter, ViewHolder

### Community 85 - "TemplateManagerViewModel"
Cohesion: 0.18
Nodes (7): Factory, SharedFlow, StateFlow, T, ViewModel, ViewModelProvider, TemplateManagerViewModel

### Community 86 - "DatabaseInitializer"
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

### Community 93 - "DatabaseMigrationHelper"
Cohesion: 0.39
Nodes (3): DatabaseMigrationHelper, Context, SQLiteDatabase

### Community 94 - "CallMonitoringService"
Cohesion: 0.16
Nodes (7): CallMonitoringService, isServiceRunning(), Context, IBinder, Intent, Notification, Service

### Community 97 - "MainViewModel"
Cohesion: 0.22
Nodes (3): AndroidViewModel, StateFlow, MainViewModel

### Community 99 - "WorkManagerHelper"
Cohesion: 0.31
Nodes (3): Context, WorkInfo, WorkManagerHelper

### Community 101 - "TemplateManagerAdapter"
Cohesion: 0.18
Nodes (10): ActivityTemplateManagerBinding, Bundle, Context, launch(), TemplateManagerActivity, ListAdapter, RecyclerView, ViewGroup (+2 more)

### Community 102 - "MemberPagingSource"
Cohesion: 0.25
Nodes (5): MemberPagingSource, LoadParams, LoadResult, PagingSource, PagingState

### Community 103 - "PastoralDatabaseInitializer.kt"
Cohesion: 0.29
Nodes (9): buildSystemTemplates(), callback(), Context, RoomDatabase, originalStepsFor(), PastoralDatabaseInitializer, seedIfEmptyBlocking(), SeedTemplate (+1 more)

### Community 104 - "TemplateContext"
Cohesion: 0.36
Nodes (4): build(), Builder, from(), TemplateContext

### Community 106 - ".onUpdate"
Cohesion: 0.36
Nodes (7): AppWidgetManager, AppWidgetProvider, Context, IntArray, PastoralWidgetKeyguardProvider, refreshWidgets(), updateWidget()

### Community 110 - "WidgetRefreshWorker"
Cohesion: 0.28
Nodes (4): Context, CoroutineWorker, Result, WidgetRefreshWorker

### Community 113 - "ReminderQueryRepository"
Cohesion: 0.35
Nodes (3): DayBounds, Flow, ReminderQueryRepository

### Community 114 - "SmsList"
Cohesion: 0.36
Nodes (5): createFromParcel(), Parcel, Parcelable, newArray(), SmsList

### Community 116 - "TemplateContextSchema"
Cohesion: 0.39
Nodes (4): DateField, Field, TemplateContextSchema, Text

### Community 117 - "MainSwipeGestureController"
Cohesion: 0.46
Nodes (3): MotionEvent, View, MainSwipeGestureController

### Community 119 - "PhotoHelper"
Cohesion: 0.32
Nodes (3): Context, ImageView, PhotoHelper

### Community 120 - "ServerFileValidator"
Cohesion: 0.46
Nodes (3): FileCheckResult, Uri, ServerFileValidator

### Community 121 - "BottomCardBehavior"
Cohesion: 0.48
Nodes (4): BottomCardBehavior, IntArray, View, CoordinatorLayout

### Community 123 - "ReminderStatus"
Cohesion: 0.33
Nodes (6): fromStored(), ReminderStatus, CANCELLED, COMPLETED, PENDING, SNOOZED

### Community 124 - "TemplateContextFormBuilder"
Cohesion: 0.25
Nodes (3): TemplateContextFormBuilder, MaterialButton, TextInputEditText

### Community 125 - "BedieningPagerAdapter"
Cohesion: 0.29
Nodes (3): BedieningPagerAdapter, Fragment, FragmentStateAdapter

### Community 126 - "CallLogExporter"
Cohesion: 0.33
Nodes (3): CallLogExporter, Context, Uri

### Community 127 - "BirthdayScrollHelper"
Cohesion: 0.33
Nodes (3): BirthdayScrollHelper, Context, RecyclerView

### Community 129 - "NoteAuthManager"
Cohesion: 0.29
Nodes (3): isAuthAvailable(), Context, NoteAuthManager

### Community 131 - "WhatsAppContactLoader"
Cohesion: 0.38
Nodes (3): Context, LifecycleCoroutineScope, WhatsAppContactLoader

### Community 132 - "vector_size_check.py"
Cohesion: 0.43
Nodes (5): find_vector_files(), get_pathdata_attrs(), main(), optimize_file(), Returns list of (element_tag, byte_length, raw_string) for each     android:path

### Community 133 - "CallLogDatabase"
Cohesion: 0.22
Nodes (6): CallLogDatabase, getInstance(), Context, RoomDatabase, CallLogImporter, Context

### Community 134 - "CollapsibleCardController"
Cohesion: 0.33
Nodes (4): CollapsibleCardController, LaaidatabasisBinding, TextView, View

### Community 136 - "AlarmReceiver"
Cohesion: 0.33
Nodes (4): AlarmReceiver, BroadcastReceiver, Context, Intent

### Community 137 - "PastoralReminderActionReceiver"
Cohesion: 0.33
Nodes (4): BroadcastReceiver, Context, Intent, PastoralReminderActionReceiver

### Community 138 - "VoipCallStateDetector"
Cohesion: 0.18
Nodes (10): CallState, ENDED, INCOMING, MISSED, OUTGOING, SCREENING, UNKNOWN, Bundle (+2 more)

### Community 142 - "BirthdayReminderWorker"
Cohesion: 0.40
Nodes (3): BirthdayReminderWorker, CoroutineWorker, Result

### Community 143 - "DropboxDownloadWorker"
Cohesion: 0.40
Nodes (3): DropboxDownloadWorker, CoroutineWorker, Result

### Community 145 - "WellBehavedEditText"
Cohesion: 0.40
Nodes (3): WellBehavedEditText, AppCompatEditText, Rect

### Community 146 - ".create"
Cohesion: 0.40
Nodes (3): BedieningViewModelFactory, T, ViewModelProvider

### Community 148 - "CallerNameResolver"
Cohesion: 0.33
Nodes (3): CallerNameResolver, ContentResolver, Context

### Community 150 - "EncryptedPrefsManager"
Cohesion: 0.50
Nodes (3): EncryptedPrefsManager, Context, SharedPreferences

### Community 151 - ".getRealPathFromURI_API19"
Cohesion: 0.40
Nodes (3): Context, Uri, RealPathUtil

### Community 152 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 153 - "PhotoSyncController"
Cohesion: 0.33
Nodes (4): LiveData, Observer, WorkInfo, PhotoSyncController

### Community 155 - "winkerkEntry"
Cohesion: 0.31
Nodes (4): Context, WinkerkContract, winkerkEntry, BaseColumns

### Community 157 - ".create"
Cohesion: 0.50
Nodes (3): T, ViewModelProvider, MemberViewModelFactory

### Community 161 - "InitState"
Cohesion: 0.50
Nodes (4): AwaitingAuth, InitState, LoadingData, Ready

### Community 162 - "FamilyMemberSpinnerController"
Cohesion: 0.36
Nodes (4): FamilyMember, FamilyMemberSpinnerController, TextView, Spinner

### Community 163 - "create"
Cohesion: 0.33
Nodes (3): create(), Context, ReminderBackupHelper

### Community 183 - ".create"
Cohesion: 0.40
Nodes (3): T, ViewModelProvider, LidmaatDetailViewModelFactory

### Community 184 - "SearchCheckBox"
Cohesion: 0.31
Nodes (5): createFromParcel(), Parcel, Parcelable, newArray(), SearchCheckBox

### Community 185 - ".create"
Cohesion: 0.25
Nodes (4): FamilyMemberRepository, T, ViewModelProvider, LidmaatDetailPastoralViewModelFactory

### Community 186 - "ReminderPreviewAdapter"
Cohesion: 0.39
Nodes (5): ListAdapter, RecyclerView, ViewGroup, ReminderPreviewAdapter, ViewHolder

### Community 187 - "buildMemberQuery"
Cohesion: 0.67
Nodes (5): appendOrderByClause(), appendWhereClause(), buildMemberQuery(), buildQuery(), SqlRequest

### Community 188 - "ActivityResultCoordinator"
Cohesion: 0.50
Nodes (3): ActivityResultLauncher, ActivityResultCoordinator, Intent

### Community 189 - "VoipNotificationHandler"
Cohesion: 0.41
Nodes (3): Bundle, StatusBarNotification, VoipNotificationHandler

### Community 193 - "FollowUpReminderWorker"
Cohesion: 0.40
Nodes (3): FollowUpReminderWorker, CoroutineWorker, Result

## Knowledge Gaps
- **77 isolated node(s):** `INCOMING`, `OUTGOING`, `MISSED`, `ENDED`, `OTHER` (+72 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **52 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SettingsManager` connect `SettingsManager` to `LaaiDatabasisActivity`, `WhatsAppNotificationService`, `UitlegActivity`, `UitlegFunksiesFragment`, `MainActivity`, `UitlegPastoraalFragment`, `MemberViewHolder`, `BedieningVandagFragment`, `UitlegVertoonFragment`, `LidmaatDetailActivity`, `.setupBirthdayAlarmIfEnabled`?**
  _High betweenness centrality (0.191) - this node is a cross-community bridge._
- **Why does `BaseActivity` connect `BaseActivity` to `LaaiDatabasisActivity`, `PermissionsActivity`, `VerjaarSmsActivity`, `PastoralBackupActivity`, `TemplateManagerAdapter`, `UitlegActivity`, `MainActivity`, `ArgiefListActivity`, `BedieningActivity`, `CallLogActivity`, `SettingsActivity`, `TemplateEditorActivity`, `LidmaatDetailActivity`?**
  _High betweenness centrality (0.190) - this node is a cross-community bridge._
- **Why does `MainActivity` connect `MainActivity` to `MainNavigationController`, `PermissionManager`, `BackPressHandler`, `MemberViewModel`, `SettingsManager`, `InitState`, `MainSearchFilterCoordinator`, `BaseActivity`, `MemberListAdapter`, `StartupActions`, `SearchCheckBox`, `ActivityResultCoordinator`, `.onPrepareOptionsMenu`, `MenuItemHandler`, `MemberListInteractionController`, `MainMenuController`, `SortOrderController`, `CongregationChipController`, `MainViewModel`, `WorkScheduler`, `MainSwipeGestureController`, `PastoralReminderBadgeController`?**
  _High betweenness centrality (0.181) - this node is a cross-community bridge._
- **What connects `INCOMING`, `OUTGOING`, `MISSED` to the rest of the system?**
  _77 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `LaaiDatabasisActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.1206896551724138 - nodes in this community are weakly interconnected._
- **Should `StelHerinneringBottomSheet` be split into smaller, more focused modules?**
  _Cohesion score 0.12535612535612536 - nodes in this community are weakly interconnected._
- **Should `PermissionsActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.08392603129445235 - nodes in this community are weakly interconnected._