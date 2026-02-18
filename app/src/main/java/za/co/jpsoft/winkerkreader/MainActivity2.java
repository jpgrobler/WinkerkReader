package za.co.jpsoft.winkerkreader;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import za.co.jpsoft.winkerkreader.data.DeviceIdManager;
import za.co.jpsoft.winkerkreader.data.FilterBox;
import za.co.jpsoft.winkerkreader.data.SmsList;
import za.co.jpsoft.winkerkreader.data.WinkerkCursorAdapter;
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

import org.joda.time.DateTime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static za.co.jpsoft.winkerkreader.Utils.fixphonenumber;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.*;

import java.util.concurrent.atomic.AtomicBoolean;


public class MainActivity2 extends AppCompatActivity {
    private final AtomicBoolean isLoadingWhatsAppContacts = new AtomicBoolean(false);
    private final AtomicBoolean whatsAppContactsLoaded = new AtomicBoolean(false);    // Constants
    private static final String TAG = "Winkerk_MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final int CODE_DRAW_OVER_OTHER_APP_PERMISSION = 2084;
    public static final int SEARCH_LIST_REQUEST = 16895;
    private static final int FILTER_LIST_REQUEST = 16896;

    public static final String CHANNEL_ID = "winkerkReaderServiceChannel";
    public static final String SEARCH_CHECK_BOX = "SEARCH_CHECK_BOX";
    public static final String FILTER_CHECK_BOX = "FILTER_CHECK_BOX";
    public static final String SMS_LYS = "SMS_LYS";

    // Views
    private WinkerkCursorAdapter cursorAdapter;
    private ListView memberListView;
    private ProgressBar progressBar;
    private TextView sortOrderView;
    private TextView memberCountView;
    private TextView searchTextView;
    private TextView churchNameView;
    private RelativeLayout searchItemBlock;
    private Menu optionsMenu;

    // Data
    private MemberViewModel viewModel;
    private SharedPreferences preferences;
    private GestureDetector gestureDetector;
    private ExecutorService backgroundExecutor;

    // State
    private int listItemPosition;
    private long listItemId;
    private String currentLayout = "";
    private ArrayList<SearchCheckBox> searchList;
    private ArrayList<FilterBox> filterList;
    public static final List<String> whatsappContacts = new ArrayList<>();

    public static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    public static final int PHONE_PERMISSIONS_REQUEST = 101;
    public static final int CALENDAR_PERMISSIONS_REQUEST = 102;
    public static final String[] CALENDAR_PERMISSIONS = {
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
    };

    private PermissionManager permissionManager;
    // Permissions
    public final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
    };

    private void logServiceStatus() {
        boolean callMonitorRunning = CallMonitoringService.isServiceRunning();
        boolean incomingCallRunning = isOproepServiceRunning(this);
        boolean myServiceRunning = isMyServiceRunning(this);

        Log.d(TAG, "Service Status - CallMonitor: " + callMonitorRunning +
                ", IncomingCall: " + incomingCallRunning +
                ", MyService: " + myServiceRunning);
    }

    private void ensureServicesAreRunning() {
        boolean callMonitorEnabled = preferences.getBoolean("CallMonitor", true);

        // Only restart if enabled but not running
        if (callMonitorEnabled && !CallMonitoringService.isServiceRunning()) {
            Log.d(TAG, "CallMonitoring service was killed, restarting...");
            startMonitoringServiceIfEnabled(this, preferences);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check permissions
        if (permissionManager.isCheckOnStartEnabled() && !permissionManager.isFirstLaunch()) {
            checkAndRequestPermissions();
        }

        // Only restart services if they were supposed to be running but aren't
        ensureServicesAreRunning();
    }

    private void openNotificationSettings() {
        Toast.makeText(this, "Please enable notification access for this app", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startActivity(intent);
    }

    private void startMonitoringServiceIfEnabled(Context context, SharedPreferences preferences) {
        boolean callMonitorEnabled = preferences.getBoolean("CallMonitor", true);

        // Start CallMonitoringService only if it's not already running
        if (callMonitorEnabled && !CallMonitoringService.isServiceRunning()) {
            try {
                Intent serviceIntent = new Intent(context, CallMonitoringService.class);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }

                Log.d(TAG, "Call monitoring service started successfully");
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception - check permissions", e);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start call monitoring service", e);
            }
        } else {
            Log.d(TAG, "Call monitoring service already running or disabled");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE);

        permissionManager = new PermissionManager(this);
        checkAndRequestPermissions();

        setContentView(R.layout.activity_main);
        initializeComponents();

        // Only start services once during onCreate, not onResume
        startMonitoringServiceIfEnabled(this, preferences);

        setupViewModel();
        loadPreferences();
        checkStoragePermissions();
        setupPermissions();
        initializeData(savedInstanceState);
        setupEventHandlers();
        setupAlarms();
        loadInitialData();

        String notificationEnabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        String packageName = getPackageName();
        if (!(notificationEnabled != null && notificationEnabled.contains(packageName))){
            openNotificationSettings();
        }
    }

    private void checkAndRequestPermissions() {
        // Case 1: First launch - always show permissions
        if (permissionManager.isFirstLaunch()) {
            showFirstLaunchPermissionDialog();
            return;
        }

        // Case 2: Not first launch but check is enabled and permissions missing
        if (permissionManager.isCheckOnStartEnabled() && !permissionManager.hasEssentialPermissions()) {
            showPermissionReminderDialog();
        }
    }

    private void showFirstLaunchPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Welcome to WinkerkReader!")
                .setMessage("This app requires several permissions to function properly.\n\n" +
                        "Please grant the necessary permissions to continue.")
                .setCancelable(false)
                .setPositiveButton("Grant Permissions", (dialog, which) -> {
                    permissionManager.setFirstLaunchComplete();
                    Intent intent = new Intent(MainActivity2.this, PermissionsActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Exit", (dialog, which) -> {
                    finish();
                })
                .show();
    }

    private void showPermissionReminderDialog() {
        int missingCount = permissionManager.getMissingPermissionsCount();

        new AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("You have " + missingCount + " missing permission(s).\n\n" +
                        "Some features may not work correctly without these permissions.")
                .setPositiveButton("Grant Now", (dialog, which) -> {
                    Intent intent = new Intent(MainActivity2.this, PermissionsActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Later", null)
                .setNeutralButton("Don't Ask Again", (dialog, which) -> {
                    showDisablePermissionCheckDialog();
                })
                .show();
    }

    private void showDisablePermissionCheckDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Disable Permission Check")
                .setMessage("Are you sure you want to disable the permission check on startup?\n\n" +
                        "You can re-enable it later from the settings menu.")
                .setPositiveButton("Yes, Disable", (dialog, which) -> {
                    permissionManager.setCheckOnStart(false);
                    Toast.makeText(this, "Permission check disabled. Enable it from Settings.",
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void checkStoragePermissions() {
        if (PermissionHelper.hasStoragePermissions(this)) {
            onStoragePermissionsGranted();
        } else {
            // Debug current status
            //debugPermissionStatus();
            //showPermissionSettingsDialog();
            // Request permissions based on Android version
            PermissionHelper.requestStoragePermissions(this);
        }
    }
    private void showPermissionSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Storage permission is required. Please enable it in Settings.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void checkPermissions() {
        String notificationEnabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        String packageName = getPackageName();
        boolean hasNotificationAccess = notificationEnabled != null && notificationEnabled.contains(packageName);

        boolean hasPhonePermissions = true;
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                hasPhonePermissions = false;
                break;
            }
        }

        boolean hasCalendarPermissions = true;
        for (String permission : CALENDAR_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                hasCalendarPermissions = false;
                break;
            }
        }
        boolean checkPermission = false;
            int result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (result == PackageManager.PERMISSION_GRANTED) {
                result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
                checkPermission = true;
            }
        //updateStatusText(hasNotificationAccess, hasPhonePermissions, hasCalendarPermissions);
    }
    public boolean isCallMonitoringServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (CallMonitoringService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }
    public boolean isOproepServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (oproepdetail.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }
    public boolean isMyServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (MyService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
            return false;
    }
    private void initializeComponents() {
        // Initialize executors
        backgroundExecutor = Executors.newSingleThreadExecutor();

        // Initialize views
        progressBar = findViewById(R.id.indeterminateBar);
        sortOrderView = findViewById(R.id.sortorder);
        memberCountView = findViewById(R.id.main_Count);
        searchTextView = findViewById(R.id.search_text);
        churchNameView = findViewById(R.id.main_gemeentenaam);
        searchItemBlock = findViewById(R.id.search_item_block);
        memberListView = findViewById(R.id.lidmaat_list);

        // Initialize adapter
        cursorAdapter = new WinkerkCursorAdapter(this, null);
        memberListView.setAdapter(cursorAdapter);
        memberListView.setFastScrollEnabled(true);

        // Initialize gesture detector
        gestureDetector = new GestureDetector(this, new SwipeGestureDetector());

        // Setup progress bar
        progressBar.setVisibility(View.GONE);
        progressBar.setIndeterminate(true);

        ProgressBar progressBar2 = findViewById(R.id.main_indeterminateBar2);
        progressBar2.setVisibility(View.GONE);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(MemberViewModel.class);

        // Observe data changes
        viewModel.getRowCount().observe(this, count -> {
            String newt = "[" + count + "]";
            memberCountView.setText(newt);
        });

        viewModel.getTextLiveData().observe(this, searchText -> {
            searchTextView.setText(searchText);
            searchItemBlock.setVisibility(searchText.isEmpty() ? View.GONE : View.VISIBLE);
        });

        viewModel.getVerjaarFLag().observe(this, this::handleBirthdayFlag);
    }

    private void loadPreferences() {
        //preferences = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE);

        // Load UI preferences
        LIST_FOTO = preferences.getBoolean("LIST_FOTO", true);
        LIST_VERJAARBLOK = preferences.getBoolean("LIST_VERJAARBLOK", true);
        LIST_HUWELIKBLOK = preferences.getBoolean("LIST_HUWELIKBLOK", true);
        LIST_WYK = preferences.getBoolean("LIST_WYK", true);
        LIST_WHATSAPP = preferences.getBoolean("LIST_WHATSAPP", true);
        LIST_EPOS = preferences.getBoolean("LIST_EPOS", true);
        LIST_OUDERDOM = preferences.getBoolean("LIST_OUDERDOM", true);
        LIST_SELFOON = preferences.getBoolean("LIST_SELFOON", true);
        LIST_TELEFOON = preferences.getBoolean("LIST_TELEFOON", true);
        OPROEPMONITOR = preferences.getBoolean("CallMonitor", true);
        DEFLAYOUT = preferences.getString("DefLayout", "GESINNE");
        WHATSAPP1 = preferences.getBoolean("Whatsapp1", true);
        WHATSAPP2 = preferences.getBoolean("Whatsapp2", true);
        WHATSAPP3 = preferences.getBoolean("Whatsapp3", true);
        EPOSHTML = preferences.getBoolean("EposHtml", false);

        // Load color preferences with migration
        loadColorPreferences();

        // Load widget preferences
        WIDGET_DOOP = preferences.getBoolean("Widget_Doop", true);
        WIDGET_BELYDENIS = preferences.getBoolean("Widget_Belydenis", true);
        WIDGET_HUWELIK = preferences.getBoolean("Widget_Huwelik", true);
    }

    // Save ArrayList<String> to SharedPreferences
    public void saveArrayList(ArrayList<String> list, String key) {
        SharedPreferences.Editor editor = preferences.edit();
        Set<String> set = new HashSet<>(list);
        editor.putStringSet(key, set);
        editor.apply();
    }
    // Retrieve ArrayList<String> from SharedPreferences
    public ArrayList<String> getArrayList(String key) {
        //SharedPreferences.Editor editor = preferences.edit();
        Set<String> set = preferences.getStringSet(key, new HashSet<String>());
        return new ArrayList<>(set);
    }
    private void loadColorPreferences() {
        SharedPreferences.Editor editor = preferences.edit();

        try {
            GEMEENTE_KLEUR = preferences.getInt("Gem1_Kleur", -1);
        } catch (ClassCastException e) {
            String colorStr = preferences.getString("Gem1_Kleur", "-1");
            GEMEENTE_KLEUR = Integer.parseInt(colorStr);
            editor.putInt("Gem1_Kleur", GEMEENTE_KLEUR);
        }

        try {
            GEMEENTE2_KLEUR = preferences.getInt("Gem2_Kleur", -3355444);
        } catch (ClassCastException e) {
            String colorStr = preferences.getString("Gem2_Kleur", "-3355444");
            GEMEENTE2_KLEUR = Integer.parseInt(colorStr);
            editor.putInt("Gem2_Kleur", GEMEENTE2_KLEUR);
        }

        try {
            GEMEENTE3_KLEUR = preferences.getInt("Gem3_Kleur", -256);
        } catch (ClassCastException e) {
            String colorStr = preferences.getString("Gem3_Kleur", "-256");
            GEMEENTE3_KLEUR = Integer.parseInt(colorStr);
            editor.putInt("Gem3_Kleur", GEMEENTE3_KLEUR);
        }

        editor.apply();
    }

    private void setupPermissions() {
        //requestPermissions();
        PermissionHelper.requestAllPermissions(this, PermissionHelper.REQUEST_CODE_ALL_PERMISSIONS);
        PermissionHelper.requestPermissionGroup(this, PermissionHelper.STORAGE_PERMISSIONS, PermissionHelper.REQUEST_CODE_STORAGE);
        checkOverlayPermission();
        createNotificationChannel();
        checkAndRequestStoragePermissions();
    }
    private void onStoragePermissionsGranted() {
        Toast.makeText(this, "Storage permissions granted! App is ready to use.", Toast.LENGTH_SHORT).show();
        // Initialize your file operations here
    }
    private void debugPermissionStatus() {
        for (String permission : PermissionHelper.STORAGE_PERMISSIONS) {
            int status = ContextCompat.checkSelfPermission(this, permission);
            boolean shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);

            Log.d("Permission Debug", "Permission: " + permission);
            Log.d("Permission Debug", "Status: " + (status == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
            Log.d("Permission Debug", "Should show rationale: " + shouldShow);
            Log.d("Permission Debug", "---");
        }
    }
    private void testStoragePermissionRequest() {
        String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        // Check each permission individually
        List<String> neededPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(permission);
                Log.d("Test", "Need permission: " + permission);
            }
        }

        if (!neededPermissions.isEmpty()) {
            Log.d("Test", "Requesting " + neededPermissions.size() + " permissions");
            ActivityCompat.requestPermissions(this,
                    neededPermissions.toArray(new String[0]),
                    1001);
        } else {
            Log.d("Test", "All permissions already granted");
        }
    }
    private void checkAndRequestStoragePermissions() {
        List<String> notGranted = PermissionHelper.getNotGrantedPermissions(this, PermissionHelper.STORAGE_PERMISSIONS);

        if (notGranted.isEmpty()) {
            // All permissions granted
            onStoragePermissionsGranted();
        } else {
            // Show which permissions are missing
            StringBuilder missing = new StringBuilder("Missing storage permissions:\n");
            for (String permission : notGranted) {
                missing.append("• ").append(PermissionHelper.getPermissionDisplayName(permission)).append("\n");
            }

            Log.w("Permissions", missing.toString());

            // Request the missing permissions
            PermissionHelper.requestPermissionGroup(this,
                    PermissionHelper.STORAGE_PERMISSIONS,
                    PermissionHelper.REQUEST_CODE_STORAGE);
        }
    }
    private void requestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
        String notificationEnabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        String packageName = getPackageName();
        boolean hasNotificationAccess = notificationEnabled != null && notificationEnabled.contains(packageName);

        boolean hasPhonePermissions = true;
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                hasPhonePermissions = false;
                break;
            }
        }

        boolean hasCalendarPermissions = true;
        for (String permission : CALENDAR_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                hasCalendarPermissions = false;
                break;
            }
        }
    }

    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, CODE_DRAW_OVER_OTHER_APP_PERMISSION);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Oproep", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void initializeData(Bundle savedInstanceState) {
        // Initialize Android ID
        winkerkEntry.id = DeviceIdManager.getDeviceId(this);

        // Initialize version info
        setupVersionInfo();

        // Initialize search and filter lists
        initializeSearchAndFilterLists();

        // Restore state if available
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        }

        // Set default layout
        if (DEFLAYOUT.isEmpty()) {
            DEFLAYOUT = "GESINNE";
            currentLayout = "GESINNE";
        }

        winkerkEntry.SORTORDER = DEFLAYOUT;
        winkerkEntry.SOEKLIST = false;
    }

    private void setupVersionInfo() {
        try {
            PackageManager manager = getPackageManager();
            PackageInfo info = manager.getPackageInfo(getPackageName(), 0);
            String versionName = "v" + info.versionName;

            TextView versionView = findViewById(R.id.version);
            versionView.setText(versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get package info", e);
        }
    }
    private ArrayList<SearchCheckBox> createDefaultSearchList() {
        ArrayList<SearchCheckBox> defaultList = new ArrayList<>();
        defaultList.add(new SearchCheckBox(LIDMATE_VAN,"","Van",true));
        defaultList.add(new SearchCheckBox(LIDMATE_NOEMNAAM,"","Noemnaam",true));
        defaultList.add(new SearchCheckBox(LIDMATE_VOORNAME,"","Voorname",true));
        defaultList.add(new SearchCheckBox(LIDMATE_WYK,"","Wyk",true));
        defaultList.add(new SearchCheckBox(LIDMATE_SELFOON,"","Selfoon",true));
        defaultList.add(new SearchCheckBox(ADRESSE_LANDLYN,"","Landlyn",true));
        defaultList.add(new SearchCheckBox(LIDMATE_NOOIENSVAN,"","Nooiensvan",true));
        defaultList.add(new SearchCheckBox(LIDMATE_BEROEP,"","Beroep",true));
        defaultList.add(new SearchCheckBox(LIDMATE_EPOS,"","Epos",true));
        defaultList.add(new SearchCheckBox(LIDMATE_STRAATADRES,"","Adres",true));
        return defaultList;
    }
    private void initializeSearchAndFilterLists() {
        filterList = new ArrayList<>();
        searchList = new ArrayList<>();
        SearchCheckBoxPreferences prefsManager = new SearchCheckBoxPreferences(this);

        // Load saved list or create new one
        searchList = prefsManager.getSearchCheckBoxList();

        if (searchList.isEmpty()) {
            // Initialize with default values if no saved data exists
            searchList = createDefaultSearchList();
            prefsManager.saveSearchCheckBoxList(searchList);
        }
    }

    private void restoreInstanceState(Bundle savedInstanceState) {
        try {
            ArrayList<SearchCheckBox> savedSearchList =
                    (ArrayList<SearchCheckBox>) savedInstanceState.getSerializable(SEARCH_CHECK_BOX);
            if (savedSearchList != null) {
                searchList = savedSearchList;
                SearchCheckBoxPreferences prefsManager = new SearchCheckBoxPreferences(this);
                prefsManager.saveSearchCheckBoxList(searchList);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to restore search list", e);
            // searchList already initialized with defaults
        }
    }

    private void setupEventHandlers() {
        setupSearchCloseHandler();
        setupSortOrderClickHandler();
        setupChurchNameClickHandler();
        setupListViewHandlers();
    }

    private void setupSearchCloseHandler() {
        ImageView searchClose = findViewById(R.id.main_search_text_close);
        searchClose.setOnClickListener(v -> {
            RECORDSTATUS = "0";
            searchItemBlock.setVisibility(View.GONE);
            winkerkEntry.SOEKLIST = false;
            DEFLAYOUT = preferences.getString("DefLayout", "GESINNE");
            sortOrderView.setBackground(null);
            sortOrderView.setText(DEFLAYOUT);
            winkerkEntry.SORTORDER = DEFLAYOUT;
            observeDataset();
        });
    }

    private void setupSortOrderClickHandler() {
        sortOrderView.setOnClickListener(v -> {
            Drawable background = v.getBackground();
            if (background instanceof ColorDrawable) {
                v.setBackground(null);
                v.setBackgroundColor(Color.WHITE);
            } else {
                v.setBackgroundResource(R.color.selected_view);
            }
        });
    }

    private void setupChurchNameClickHandler() {
        churchNameView.setOnClickListener(this::showGroupFunctionMenu);
    }

    private void setupListViewHandlers() {
        memberListView.setOnItemLongClickListener(this::onMemberLongClick);
        memberListView.setOnItemClickListener(this::onMemberClick);
    }

    private boolean onMemberLongClick(AdapterView<?> parent, View view, int position, long id) {
        Cursor cursor = (Cursor) cursorAdapter.getItem(position);
        if (cursor == null) return false;

        ContentValues values = new ContentValues();
        int tagColumnIndex = cursor.getColumnIndex(LIDMATE_TAG);
        int currentTag = cursor.getInt(tagColumnIndex);
        values.put(LIDMATE_TAG, currentTag == 0 ? 1 : 0);

        Uri memberUri = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, id);
        int rowsAffected = getContentResolver().update(memberUri, values,
                LIDMATE_TABLE_NAME + "._rowid_ =?", new String[]{String.valueOf(id)});

        if (rowsAffected == 1) {
            observeDataset();
        }

        return rowsAffected == 1;
    }

    private void onMemberClick(AdapterView<?> parent, View view, int position, long id) {
        showMemberPopupMenu(view, position, id);
    }

    private void setupAlarms() {
        setupAutoDownloadAlarm();
        setupReminderAlarm();
        setupWidgetRefreshAlarm();
    }

    private void setupAutoDownloadAlarm() {
        SharedPreferences.Editor editor = preferences.edit();

        if (preferences.getBoolean("AUTO_DL", false) ||
                preferences.getBoolean("DL-TIMEUPDATE", false)) {

            String hour = preferences.getString("DL-HOUR", "08");
            String minute = preferences.getString("DL-MINUTE", "00");
            int day = preferences.getInt("DL-DAY", 6);

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hour));
            calendar.set(Calendar.MINUTE, Integer.parseInt(minute));
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.DAY_OF_WEEK, day);

            Calendar now = Calendar.getInstance();
            editor.putBoolean("FROM_MENU", false);
            editor.apply();

            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setAction("DropBoxDownLoad");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);

                long triggerTime = calendar.getTimeInMillis();
                if (triggerTime <= now.getTimeInMillis()) {
                    triggerTime += AlarmManager.INTERVAL_DAY * 7;
                }

                scheduleRepeatingAlarm(alarmManager, triggerTime,
                        AlarmManager.INTERVAL_DAY * 7, pendingIntent);
            }
        }
    }
    public void applyFilterList(ArrayList<FilterBox> filterList) {
        this.filterList = filterList;
    }
    private void setupReminderAlarm() {
        SharedPreferences.Editor editor = preferences.edit();

        if (preferences.getBoolean("HERINNER", false) ||
                preferences.getBoolean("SMS-TIMEUPDATE", false)) {

            String hour = preferences.getString("SMS-HOUR", "08");
            String minute = preferences.getString("SMS-MINUTE", "00");

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hour));
            calendar.set(Calendar.MINUTE, Integer.parseInt(minute));
            calendar.set(Calendar.SECOND, 0);

            Calendar now = Calendar.getInstance();
            editor.putBoolean("SMS-TIMEUPDATE", false);
            editor.putBoolean("FROM_MENU", false);
            editor.apply();

            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setAction("VerjaarSMS");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null) {
                long triggerTime = calendar.getTimeInMillis();
                if (triggerTime <= now.getTimeInMillis()) {
                    triggerTime += AlarmManager.INTERVAL_DAY;
                }

                scheduleRepeatingAlarm(alarmManager, triggerTime,
                        AlarmManager.INTERVAL_DAY, pendingIntent);
            }
        }
    }

    private void setupWidgetRefreshAlarm() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 6);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        Intent intent = new Intent(this, WinkerkReaderWidgetProvider.class);
        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");

        int[] ids = AppWidgetManager.getInstance(this)
                .getAppWidgetIds(new ComponentName(this, WinkerkReaderWidgetProvider.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            scheduleRepeatingAlarm(alarmManager, calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }

    private void scheduleRepeatingAlarm(AlarmManager alarmManager, long triggerTime,
                                        long interval, PendingIntent pendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Check if we can schedule exact alarms on Android 12+
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                // Request exact alarm permission
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
                // Fallback to inexact alarm
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerTime, interval, pendingIntent);
        }
    }

    private void loadInitialData() {
        // Hide keyboard if visible
        if (getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        }

        // Set initial UI state
        searchItemBlock.setVisibility(View.GONE);
        sortOrderView.setText(winkerkEntry.SORTORDER);
        sortOrderView.setTag(winkerkEntry.SORTORDER);
        memberCountView.setText("[0]");

        // Load church and database info
        backgroundExecutor.execute(() -> {
            setDatabaseDate();
            setChurchInfo();

            runOnUiThread(() -> {
                String churchText = GEMEENTE_NAAM + " " + GEMEENTE2_NAAM + " " + GEMEENTE3_NAAM;
                churchNameView.setText(churchText.trim());
                observeDataset();

                // Only load WhatsApp contacts if not already loaded or loading
                loadWhatsAppContactsAtomic();
            });
        });
    }

//    private void loadInitialData() {
//        // Hide keyboard if visible
//        if (getCurrentFocus() != null) {
//            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
//            if (imm != null) {
//                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
//            }
//        }
//
//        // Set initial UI state
//        searchItemBlock.setVisibility(View.GONE);
//        sortOrderView.setText(winkerkEntry.SORTORDER);
//        sortOrderView.setTag(winkerkEntry.SORTORDER);
//        memberCountView.setText("[0]");
//
//        // Load church and database info
//        backgroundExecutor.execute(() -> {
//            setDatabaseDate();
//            setChurchInfo();
//
//            runOnUiThread(() -> {
//                String churchText = GEMEENTE_NAAM + " " + GEMEENTE2_NAAM + " " + GEMEENTE3_NAAM;
//                churchNameView.setText(churchText.trim());
//                observeDataset();
//                loadWhatsAppContacts();
//            });
//        });
//    }

    void observeDataset() {
        searchItemBlock.setVisibility(winkerkEntry.SOEKLIST ? View.VISIBLE : View.GONE);
        sortOrderView.setText(winkerkEntry.SORTORDER);
        sortOrderView.setTag(winkerkEntry.SORTORDER);

        switch (DEFLAYOUT) {
            case "SOEK_DATA":
                winkerkEntry.SOEKLIST = true;
                viewModel.getSOEK_DATA(this).observe(this, cursor -> {
                    cursorAdapter.swapCursor(cursor);
                });
                break;
            case "FILTER_DATA":
                winkerkEntry.SOEKLIST = false;
                viewModel.getFILTER_DATA(this, filterList).observe(this, cursor -> {
                    cursorAdapter.swapCursor(cursor);
                });
                searchItemBlock.setVisibility(View.VISIBLE);
                break;
            case "ADRES":
                viewModel.getLIDMAAT_DATA_ADRES(this).observe(this, cursor -> {
                    cursorAdapter.swapCursor(cursor);
                });
                break;
            case "GESINNE":
                viewModel.getGESINNE_DATA(this).observe(this, cursor -> {
                    cursorAdapter.swapCursor(cursor);
                });
                break;
            case "HUWELIK":
                viewModel.getHUWELIK_DATA(this).observe(this, cursor -> {
                    cursorAdapter.swapCursor(cursor);
                });
                break;
            case "OUDERDOM":
                viewModel.getOUDERDOM_DATA(this).observe(this, cursor -> {
                    cursorAdapter.swapCursor(cursor);
                });
                break;
            case "VAN":
                viewModel.getLIDMAAT_DATA(this).observe(this, cursor -> {
                    cursorAdapter.swapCursor(cursor);
                });
                break;
            case "VERJAAR":
                viewModel.getLIDMAAT_DATA_VERJAAR(this).observe(this, cursor -> {
                    cursorAdapter.swapCursor(cursor);
                });
                break;
            case "WYK":
                viewModel.getLIDMAAT_DATA_WYK(this).observe(this, cursor -> {
                    cursorAdapter.swapCursor(cursor);
                });
                break;
        }
    }

    private void handleBirthdayFlag(Boolean showBirthday) {
        if (!showBirthday || cursorAdapter.getCursor() == null) return;

        backgroundExecutor.execute(() -> {
            try {
                DateTime today = DateTime.now();
                Cursor data = cursorAdapter.getCursor();

                if (data.getCount() == 0) return;

                int birthdayColumnIndex = data.getColumnIndex(LIDMATE_GEBOORTEDATUM);
                if (birthdayColumnIndex == -1) return;

                data.moveToFirst();
                String currentMonth = today.toString().substring(5, 7).trim();
                String currentDay = today.toString().substring(8, 10).trim();

                // Find today's birthdays
                int targetPosition = findTodaysBirthday(data, birthdayColumnIndex, currentMonth, currentDay);

                if (targetPosition != -1) {
                    runOnUiThread(() -> {
                        memberListView.post(() -> {
                            memberListView.setSelection(targetPosition);
                        });
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling birthday flag", e);
            }
        });
    }

    private int findTodaysBirthday(Cursor data, int columnIndex, String targetMonth, String targetDay) {
        data.moveToFirst();

        // First, find the month
        while (!data.isAfterLast()) {
            if (!data.isNull(columnIndex) && !data.getString(columnIndex).isEmpty()) {
                String dateString = data.getString(columnIndex);
                if (dateString.length() >= 5) {
                    String month = dateString.substring(3, 5).trim();
                    if (month.equals(targetMonth)) {
                        break;
                    }
                }
            }
            data.moveToNext();
        }

        // Then find the day within that month
        while (!data.isAfterLast()) {
            if (!data.isNull(columnIndex) && !data.getString(columnIndex).isEmpty()) {
                String dateString = data.getString(columnIndex);
                if (dateString.length() >= 2) {
                    String day = dateString.substring(0, 2).trim();
                    if (day.equals(targetDay)) {
                        return data.getPosition();
                    }
                }
            }
            data.moveToNext();
        }

        return -1;
    }

//    private void loadWhatsAppContacts() {
//        if (!whatsappContacts.isEmpty()) return;
//
//        backgroundExecutor.execute(() -> {
//            try {
//                ContentResolver contentResolver = getContentResolver();
//
//                // Query WhatsApp contacts
//                String[] projection = {
//                        ContactsContract.RawContacts._ID,
//                        ContactsContract.RawContacts.CONTACT_ID
//                };
//
//                String selection = ContactsContract.RawContacts.ACCOUNT_TYPE + " = ?";
//                String[] selectionArgs = {"com.whatsapp"};
//
//                try (Cursor rawContactsCursor = contentResolver.query(
//                        ContactsContract.RawContacts.CONTENT_URI,
//                        projection, selection, selectionArgs, null)) {
//
//                    if (rawContactsCursor == null || rawContactsCursor.getCount() == 0) {
//                        return;
//                    }
//
//                    int contactIdIndex = rawContactsCursor.getColumnIndex(
//                            ContactsContract.RawContacts.CONTACT_ID);
//
//                    if (contactIdIndex == -1) return;
//
//                    while (rawContactsCursor.moveToNext()) {
//                        String contactId = rawContactsCursor.getString(contactIdIndex);
//                        if (contactId != null) {
//                            loadWhatsAppContactNumber(contentResolver, contactId);
//                        }
//                    }
//
//                    runOnUiThread(() -> {
//                        if (!whatsappContacts.isEmpty()) {
//                            Toast.makeText(this, "WhatsApp Contacts loaded", Toast.LENGTH_SHORT).show();
//                        }
//                    });
//                }
//
//            } catch (Exception e) {
//                Log.e(TAG, "Error loading WhatsApp contacts", e);
//            }
//        });
//    }


    private void loadWhatsAppContactsAtomic() {
        // Check if already loaded
        if (whatsAppContactsLoaded.get() && !whatsappContacts.isEmpty()) {
            Log.d(TAG, "WhatsApp contacts already loaded, skipping...");
            return;
        }

        // Try to set loading flag atomically
        if (!isLoadingWhatsAppContacts.compareAndSet(false, true)) {
            Log.d(TAG, "WhatsApp contacts are currently being loaded, skipping...");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                ContentResolver contentResolver = getContentResolver();

                String[] projection = {
                        ContactsContract.RawContacts._ID,
                        ContactsContract.RawContacts.CONTACT_ID
                };

                String selection = ContactsContract.RawContacts.ACCOUNT_TYPE + " = ?";
                String[] selectionArgs = {"com.whatsapp"};

                try (Cursor rawContactsCursor = contentResolver.query(
                        ContactsContract.RawContacts.CONTENT_URI,
                        projection, selection, selectionArgs, null)) {

                    if (rawContactsCursor == null || rawContactsCursor.getCount() == 0) {
                        Log.d(TAG, "No WhatsApp contacts found");
                        return;
                    }

                    int contactIdIndex = rawContactsCursor.getColumnIndex(
                            ContactsContract.RawContacts.CONTACT_ID);

                    if (contactIdIndex == -1) return;

                    synchronized (whatsappContacts) {
                        whatsappContacts.clear();
                    }

                    while (rawContactsCursor.moveToNext()) {
                        String contactId = rawContactsCursor.getString(contactIdIndex);
                        if (contactId != null) {
                            loadWhatsAppContactNumber(contentResolver, contactId);
                        }
                    }

                    whatsAppContactsLoaded.set(true);

                    runOnUiThread(() -> {
                        if (!whatsappContacts.isEmpty()) {
                            Toast.makeText(this,
                                    "WhatsApp Contacts loaded: " + whatsappContacts.size(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error loading WhatsApp contacts", e);
                whatsAppContactsLoaded.set(false);
            } finally {
                isLoadingWhatsAppContacts.set(false);
            }
        });
    }

//    private void loadWhatsAppContacts() {
//        // Check if already loaded
//        if (whatsAppContactsLoaded && !whatsappContacts.isEmpty()) {
//            Log.d(TAG, "WhatsApp contacts already loaded, skipping...");
//            return;
//        }
//
//        // Check if currently loading
//        if (isLoadingWhatsAppContacts) {
//            Log.d(TAG, "WhatsApp contacts are currently being loaded, skipping...");
//            return;
//        }
//
//        // Mark as loading
//        isLoadingWhatsAppContacts = true;
//
//        backgroundExecutor.execute(() -> {
//            try {
//                ContentResolver contentResolver = getContentResolver();
//
//                // Query WhatsApp contacts
//                String[] projection = {
//                        ContactsContract.RawContacts._ID,
//                        ContactsContract.RawContacts.CONTACT_ID
//                };
//
//                String selection = ContactsContract.RawContacts.ACCOUNT_TYPE + " = ?";
//                String[] selectionArgs = {"com.whatsapp"};
//
//                try (Cursor rawContactsCursor = contentResolver.query(
//                        ContactsContract.RawContacts.CONTENT_URI,
//                        projection, selection, selectionArgs, null)) {
//
//                    if (rawContactsCursor == null || rawContactsCursor.getCount() == 0) {
//                        Log.d(TAG, "No WhatsApp contacts found");
//                        return;
//                    }
//
//                    int contactIdIndex = rawContactsCursor.getColumnIndex(
//                            ContactsContract.RawContacts.CONTACT_ID);
//
//                    if (contactIdIndex == -1) return;
//
//                    // Clear existing contacts before loading new ones
//                    synchronized (whatsappContacts) {
//                        whatsappContacts.clear();
//                    }
//
//                    while (rawContactsCursor.moveToNext()) {
//                        String contactId = rawContactsCursor.getString(contactIdIndex);
//                        if (contactId != null) {
//                            loadWhatsAppContactNumber(contentResolver, contactId);
//                        }
//                    }
//
//                    // Mark as successfully loaded
//                    whatsAppContactsLoaded = true;
//
//                    runOnUiThread(() -> {
//                        if (!whatsappContacts.isEmpty()) {
//                            Toast.makeText(this,
//                                    "WhatsApp Contacts loaded: " + whatsappContacts.size(),
//                                    Toast.LENGTH_SHORT).show();
//                            Log.d(TAG, "Loaded " + whatsappContacts.size() + " WhatsApp contacts");
//                        }
//                    });
//                }
//
//            } catch (Exception e) {
//                Log.e(TAG, "Error loading WhatsApp contacts", e);
//                whatsAppContactsLoaded = false; // Allow retry on error
//            } finally {
//                // Always reset loading flag
//                isLoadingWhatsAppContacts = false;
//            }
//        });
//    }

    private void loadWhatsAppContactNumber(ContentResolver contentResolver, String contactId) {
        String[] phoneProjection = {
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        String phoneSelection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?";
        String[] phoneSelectionArgs = {contactId};

        try (Cursor phoneCursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                phoneProjection, phoneSelection, phoneSelectionArgs, null)) {

            if (phoneCursor != null && phoneCursor.moveToFirst()) {
                int numberIndex = phoneCursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER);

                if (numberIndex != -1) {
                    String number = phoneCursor.getString(numberIndex);
                    if (number != null && !number.isEmpty()) {
                        String formattedNumber = fixphonenumber(number);
                        synchronized (whatsappContacts) {
                            whatsappContacts.add(formattedNumber);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading phone number for contact " + contactId, e);
        }
    }

    private void showMemberPopupMenu(View view, int position, long id) {
        listItemPosition = position;
        listItemId = id;

        PopupMenu popup = new PopupMenu(this, view);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.lidmaatlist_menu, popup.getMenu());
        setForceShowIcon(popup);

        Cursor cursor = (Cursor) cursorAdapter.getItem((int) id);
        if (cursor == null) return;

        cursor.moveToPosition(position);
        configurePopupMenu(popup, cursor);

        popup.show();
        popup.setOnMenuItemClickListener(item -> handlePopupMenuClick(item, cursor));
    }

    private void configurePopupMenu(PopupMenu popup, Cursor cursor) {
        int nameIndex = cursor.getColumnIndex(LIDMATE_NOEMNAAM);
        int surnameIndex = cursor.getColumnIndex(LIDMATE_VAN);
        int cellIndex = cursor.getColumnIndex(LIDMATE_SELFOON);
        int phoneIndex = cursor.getColumnIndex(ADRESSE_LANDLYN);
        int emailIndex = cursor.getColumnIndex(LIDMATE_EPOS);

        String name = cursor.getString(nameIndex);
        String surname = cursor.getString(surnameIndex);
        Menu menu = popup.getMenu();

        // Configure menu titles
        popup.getMenu().findItem(R.id.kyk_lidmaat_detail)
                .setTitle("Detail van " + name + " " + surname);
        popup.getMenu().findItem(R.id.submenu_bel)
                .setTitle("Skakel " + name);
        popup.getMenu().findItem(R.id.submenu_teks)
                .setTitle("Teks " + name);
        popup.getMenu().findItem(R.id.submenu_ander)
                .setTitle(name);

        // Remove menu items based on available data
        if (cursor.isNull(cellIndex)) {
            safeRemoveMenuItem(menu, R.id.submenu_bel, R.id.bel_selfoon);
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_sms);
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp);
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp2);
            safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp3);
        } else {
            String cellNumber = cursor.getString(cellIndex);
            popup.getMenu().findItem(R.id.bel_selfoon).setTitle("Skakel " + cellNumber);
            popup.getMenu().findItem(R.id.stuur_sms).setTitle("SMS na " + cellNumber);
        }

        if (cursor.isNull(phoneIndex)) {
            safeRemoveMenuItem(menu, R.id.submenu_bel, R.id.bel_landlyn);
            //popup.getMenu().findItem(R.id.submenu_bel).getSubMenu().removeItem(R.id.bel_landlyn);
        } else {
            String phoneNumber = cursor.getString(phoneIndex);
            popup.getMenu().findItem(R.id.bel_landlyn).setTitle("Skakel " + phoneNumber);
        }

        if (cursor.isNull(emailIndex) || cursor.getString(emailIndex).isEmpty()) {
            safeRemoveMenuItem(menu, R.id.submenu_bel, R.id.stuur_epos);
            //popup.getMenu().findItem(R.id.submenu_teks).getSubMenu().removeItem(R.id.stuur_epos);
        }

        if (!WHATSAPP1) safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp);
        if (!WHATSAPP2) safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp2);
        if (!WHATSAPP3) safeRemoveMenuItem(menu, R.id.submenu_teks, R.id.stuur_whatsapp3);
    }
    // Create a helper method for safe removal
    private void safeRemoveMenuItem(Menu menu, int submenuId, int itemId) {
        if (menu == null) return;

        MenuItem submenu = menu.findItem(submenuId);
        if (submenu != null && submenu.hasSubMenu()) {
            SubMenu subMenu = submenu.getSubMenu();
            assert subMenu != null;
            if (subMenu.findItem(itemId) != null) {
                subMenu.removeItem(itemId);
            }
        }
    }
    private boolean handlePopupMenuClick(MenuItem item, Cursor cursor) {
        MemberActionHandler actionHandler = new MemberActionHandler(this, cursor);
        return actionHandler.handleAction(item.getItemId());
    }

    private void showGroupFunctionMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.groepfunksie_menu, popup.getMenu());
        setForceShowIcon(popup);

        Cursor cursor = cursorAdapter.getCursor();
        if (cursor == null || cursor.getCount() < 1) return;

        int[] counts = calculateMemberCounts(cursor);
        int totalCount = counts[0];
        int selectedCount = counts[1];

        popup.getMenu().findItem(R.id.sms_groep).setTitle("Almal (" + totalCount + ")");
        popup.getMenu().findItem(R.id.sms_selected).setTitle("Geselekteerdes (" + selectedCount + ")");
        popup.getMenu().findItem(R.id.almal_in_groep).setTitle("Almal (" + totalCount + ")");
        popup.getMenu().findItem(R.id.selected_in_groep).setTitle("Geselekteerdes (" + selectedCount + ")");

        popup.show();
        popup.setOnMenuItemClickListener(item -> handleGroupMenuClick(item, cursor));
    }

    private int[] calculateMemberCounts(Cursor cursor) {
        cursor.moveToFirst();
        int totalCount = cursor.getCount();
        int selectedCount = 0;
        int tagIndex = cursor.getColumnIndex(LIDMATE_TAG);

        if (tagIndex != -1) {
            for (int i = 0; i < totalCount; i++) {
                if (cursor.getInt(tagIndex) == 1) {
                    selectedCount++;
                }
                cursor.moveToNext();
            }
        }

        return new int[]{totalCount, selectedCount};
    }

    private boolean handleGroupMenuClick(MenuItem item, Cursor cursor) {
        if (cursor == null || cursor.getCount() < 1) return false;

        ArrayList<SmsList> smsList = new ArrayList<>();
        GroupMemberCollector collector = new GroupMemberCollector(cursor);

        int id = item.getItemId();

        if (id == R.id.sms_groep || id == R.id.almal_in_groep) {
            smsList = collector.collectAllMembers();
        } else if (id == R.id.sms_selected || id == R.id.selected_in_groep) {
            smsList = collector.collectSelectedMembers();
        }



        return true;
    }

    private void setDatabaseDate() {
        try (SQLiteAssetHelper helper = new SQLiteAssetHelper(this, WINKERK_DB, null, 1);
             SQLiteDatabase database = helper.getReadableDatabase();
             Cursor cursor = database.rawQuery("SELECT * FROM Datum", null)) {

            if (cursor != null && cursor.moveToFirst()) {
                int dateIndex = cursor.getColumnIndex("DataDatum");
                if (dateIndex != -1) {
                    winkerkEntry.DATA_DATUM = cursor.getString(dateIndex);
                } else {
                    winkerkEntry.DATA_DATUM = "";
                }
            } else {
                winkerkEntry.DATA_DATUM = "";
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Error reading database date", e);
            winkerkEntry.DATA_DATUM = "";
        }
    }

    private void setChurchInfo() {
        String query = "SELECT DISTINCT Members._rowid_ as _id, Gemeente, [Gemeente epos] " +
                "FROM Members GROUP BY Gemeente, [Gemeente epos]";

        try (SQLiteAssetHelper helper = new SQLiteAssetHelper(this, WINKERK_DB, null, 1);
             SQLiteDatabase database = helper.getReadableDatabase();
             Cursor cursor = database.rawQuery(query, null)) {

            // Initialize all church variables
            GEMEENTE_NAAM = "";
            GEMEENTE_EPOS = "";
            GEMEENTE2_NAAM = "";
            GEMEENTE2_EPOS = "";
            GEMEENTE3_NAAM = "";
            GEMEENTE3_EPOS = "";

            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex("Gemeente");
                int emailIndex = cursor.getColumnIndex("Gemeente epos");

                if (nameIndex != -1 && emailIndex != -1) {
                    // First church
                    GEMEENTE_NAAM = cursor.getString(nameIndex) != null ? cursor.getString(nameIndex) : "";
                    GEMEENTE_EPOS = cursor.getString(emailIndex) != null ? cursor.getString(emailIndex) : "";

                    // Second church
                    if (cursor.moveToNext()) {
                        GEMEENTE2_NAAM = cursor.getString(nameIndex) != null ? cursor.getString(nameIndex) : "";
                        GEMEENTE2_EPOS = cursor.getString(emailIndex) != null ? cursor.getString(emailIndex) : "";

                        // Third church
                        if (cursor.moveToNext()) {
                            GEMEENTE3_NAAM = cursor.getString(nameIndex) != null ? cursor.getString(nameIndex) : "";
                            GEMEENTE3_EPOS = cursor.getString(emailIndex) != null ? cursor.getString(emailIndex) : "";
                        }
                    }
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Error reading church info", e);
        }
    }

    // Menu and navigation methods
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.optionsMenu = menu;

        if (menu.getClass().getSimpleName().equals("MenuBuilder")) {
            try {
                Method method = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                method.setAccessible(true);
                method.invoke(menu, true);
            } catch (Exception e) {
                Log.e(TAG, "Failed to show menu icons", e);
            }
        }

        getMenuInflater().inflate(R.menu.menu_main, menu);
        setupSearchView(menu);

        return true;
    }

    private void setupSearchView(Menu menu) {
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchItem.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM
                | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

        if (searchView != null) {
            searchView.setSubmitButtonEnabled(true);
            // Use AndroidX AppCompat resource IDs
            int searchSrcTextId;
            searchSrcTextId = androidx.appcompat.R.id.search_src_text;
            int searchPlateId = androidx.appcompat.R.id.search_plate;
            // Configure search view appearance
            //int searchPlateId = getResources().getIdentifier("search_src_text", "id", getPackageName());
            EditText searchPlate = searchView.findViewById(searchSrcTextId);
            if (searchPlate != null) {
                searchPlate.setHint("Soek");
            }

            //int searchPlateViewId = getResources().getIdentifier("search_plate", "id", getPackageName());
            View searchPlateView = searchView.findViewById(searchPlateId);
            if (searchPlateView != null) {
                searchPlateView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            }

            // Set up search manager
            SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
            if (searchManager != null) {
                searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            }

            // Set up listeners
            searchView.setOnCloseListener(() -> {
                winkerkEntry.SORTORDER = DEFLAYOUT;
                winkerkEntry.SOEKLIST = false;
                observeDataset();
                return false;
            });

            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return handleSearchQuery(query);
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    return true;
                }
            });
        }
    }

    private boolean handleSearchQuery(String query) {
        searchItemBlock.setVisibility(View.VISIBLE);

        String searchText = RECORDSTATUS.equals("2") ? "Onaktief " + winkerkEntry.SOEK : winkerkEntry.SOEK;
        searchTextView.setText(searchText);

        winkerkEntry.SOEK = query.trim();
        DEFLAYOUT = "SOEK_DATA";
        observeDataset();

        if (optionsMenu != null) {
            optionsMenu.findItem(R.id.action_search).collapseActionView();
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        MenuItemHandler menuHandler = new MenuItemHandler(this, preferences);
        return menuHandler.handleMenuItem(item) || super.onOptionsItemSelected(item);
    }

    // Touch and gesture handling
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    // Activity lifecycle methods
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(SEARCH_CHECK_BOX, searchList);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            winkerkEntry.SORTORDER = DEFLAYOUT;
            winkerkEntry.SOEKLIST = false;
            try {
                cursorAdapter.swapCursor(null);
            } finally {
                observeDataset();
            }
            return;
        }

        switch (requestCode) {
            case SEARCH_LIST_REQUEST:
                searchList = (ArrayList<SearchCheckBox>) data.getSerializableExtra(SEARCH_CHECK_BOX);
                break;
            case FILTER_LIST_REQUEST:
                filterList = Objects.requireNonNull(data.getExtras()).getParcelableArrayList(FILTER_CHECK_BOX);
                //filterList = data.getExtras().getParcelableArrayList(FILTER_CHECK_BOX);
                winkerkEntry.SORTORDER = "Filter";
                DEFLAYOUT = "FILTER_DATA";
                winkerkEntry.SOEKLIST = false;
                observeDataset();
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Handle permission results if needed
    }

    @Override
    public void onBackPressed() {
        finish();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        // Reset flags when activity is destroyed
        isLoadingWhatsAppContacts.set(false);
        whatsAppContactsLoaded.set(false);

        // Clean up other resources
        if (cursorAdapter != null) {
            cursorAdapter.swapCursor(null);
        }

        if (backgroundExecutor != null) {
            backgroundExecutor.shutdown();
        }

        super.onDestroy();
    }

    // Gesture detector class
    private class SwipeGestureDetector extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_MIN_DISTANCE = 120;
        private static final int SWIPE_MAX_OFF_PATH = 200;
        private static final int SWIPE_THRESHOLD_VELOCITY = 200;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
                float diffAbs = Math.abs(e1.getY() - e2.getY());
                float diff = e1.getX() - e2.getX();

                if (diffAbs > SWIPE_MAX_OFF_PATH) {
                    return false;
                }

                if (diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    onLeftSwipe();
                } else if (-diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    onRightSwipe();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error on gestures", e);
            }
            return false;
        }
    }

    private void onLeftSwipe() {
        NavigationHandler.handleLeftSwipe(this, sortOrderView);
    }

    private void onRightSwipe() {
        NavigationHandler.handleRightSwipe(this, sortOrderView);
    }

    // Utility methods
    private static void setForceShowIcon(PopupMenu popupMenu) {
        try {
            Field[] fields = popupMenu.getClass().getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popupMenu);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                    Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                    setForceIcons.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to show popup menu icons", e);
        }
    }

    // Helper classes for organization
    private static class GroupMemberCollector {
        private final Cursor cursor;

        public GroupMemberCollector(Cursor cursor) {
            this.cursor = cursor;
        }

        public ArrayList<SmsList> collectAllMembers() {
            ArrayList<SmsList> list = new ArrayList<>();
            cursor.moveToFirst();

            int nameIndex = cursor.getColumnIndex(LIDMATE_NOEMNAAM);
            int surnameIndex = cursor.getColumnIndex(LIDMATE_VAN);
            int phoneIndex = cursor.getColumnIndex(LIDMATE_SELFOON);
            int guidIndex = cursor.getColumnIndex(LIDMATE_LIDMAATGUID);

            for (int i = 0; i < cursor.getCount(); i++) {
                if (!cursor.isNull(phoneIndex)) {
                    list.add(new SmsList(
                            cursor.getString(nameIndex),
                            cursor.getString(surnameIndex),
                            cursor.getString(phoneIndex),
                            cursor.getInt(0),
                            cursor.getString(guidIndex)
                    ));
                }
                cursor.moveToNext();
            }

            return list;
        }

        public ArrayList<SmsList> collectSelectedMembers() {
            ArrayList<SmsList> list = new ArrayList<>();
            cursor.moveToFirst();

            int nameIndex = cursor.getColumnIndex(LIDMATE_NOEMNAAM);
            int surnameIndex = cursor.getColumnIndex(LIDMATE_VAN);
            int phoneIndex = cursor.getColumnIndex(LIDMATE_SELFOON);
            int tagIndex = cursor.getColumnIndex(LIDMATE_TAG);
            int guidIndex = cursor.getColumnIndex(LIDMATE_LIDMAATGUID);

            for (int i = 0; i < cursor.getCount(); i++) {
                if (!cursor.isNull(phoneIndex) && cursor.getInt(tagIndex) == 1) {
                    list.add(new SmsList(
                            cursor.getString(nameIndex),
                            cursor.getString(surnameIndex),
                            cursor.getString(phoneIndex),
                            cursor.getInt(0),
                            cursor.getString(guidIndex)
                    ));
                }
                cursor.moveToNext();
            }

            return list;
        }
    }
}