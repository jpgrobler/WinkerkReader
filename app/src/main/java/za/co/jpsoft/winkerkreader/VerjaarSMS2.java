package za.co.jpsoft.winkerkreader;

import static za.co.jpsoft.winkerkreader.R.drawable.bdaysms;
import static za.co.jpsoft.winkerkreader.Utils.fixphonenumber;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.DEFLAYOUT;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.KEUSE;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_EPOS;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_LANDLYN;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_SELFOON;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_STRAATADRES;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_TABLE_NAME;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_TAG;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_VAN;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WHATSAPP1;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WHATSAPP2;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WHATSAPP3;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.ViewModelProvider;

import za.co.jpsoft.winkerkreader.data.CursorDataExtractor;
import za.co.jpsoft.winkerkreader.data.WinkerkContract;
import za.co.jpsoft.winkerkreader.data.WinkerkCursorAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class VerjaarSMS2 extends AppCompatActivity {
    private WinkerkCursorAdapter mCursorAdapter;
    private Cursor whatsappContactCursor;
    private TextView mTextView;
    private Boolean AutoSMS;
    private EventViewModel viewModel;
    private int listItemPositionForPopupMenu;
    private int listItemPositionForPopupMenu2;
    private String message = "Hi toets!";
    private RadioGroup radioGroup;
    private RadioButton radioButton;
    private androidx.lifecycle.LiveData<Cursor> currentLiveData;

    private androidx.lifecycle.Observer<Cursor> currentObserver;
    private final TextWatcher mTextEditorWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            //This sets a textview to the current length
            mTextView.setText(String.valueOf(160 - s.length()));
        }

        public void afterTextChanged(Editable s) {
        }
    };

    private final int MAX_SMS_MESSAGE_LENGTH = 160;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String message = null;

            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    message = "Message sent!";
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    message = "Error. Message not sent.";
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    message = "Error: No service.";
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    message = "Error: Null PDU.";
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    message = "Error: Radio off.";
                    break;
            }


        }


    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

//    @Override
//    public void onBackPressed(){
//        super.onBackPressed();
//        getLoaderManager().destroyLoader(10);
//        mCursorAdapter.swapCursor(null);
//        ContentValues values = new ContentValues();
//        WinkerkContract.winkerkEntry.SORTORDER = "VERJAAR";
//        values.put(LIDMATE_TAG, 0);
//        int rowsAffected = getContentResolver().update(WinkerkContract.winkerkEntry.CONTENT_URI, values, null, null);
//        // startActivity(intent);
//        finish();
//    }

    @Override
    protected void onDestroy(){
        if (currentLiveData != null && currentObserver != null) {
            currentLiveData.removeObserver(currentObserver);
        }
        mCursorAdapter.swapCursor(null);
        WinkerkContract.winkerkEntry.SORTORDER = DEFLAYOUT;
        WinkerkContract.winkerkEntry.SOEKLIST = false;
        getLoaderManager().destroyLoader(10);
        super.onDestroy();
    }

    private void initializeComponents() {
        // Step 1: Basic setup
        initializeViewModel();
        requestPermissions();
        initializeSharedPreferences();
        initializeViews();
        setupListView();  // Creates mCursorAdapter

        // Step 2: UI components that need mCursorAdapter
        setupEventTypeSelection();
        setupMessageInput();
        setupTimeInput();
        setupButtons();

        // Step 3: Load data
        loadInitialData();
        handleAutoSMS();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.verjaar);
        WinkerkContract.winkerkEntry.SORTORDER = "VERJAAR";

        initializeComponents();  // Single method call with proper order
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                getLoaderManager().destroyLoader(10);
                mCursorAdapter.swapCursor(null);
                ContentValues values = new ContentValues();
                WinkerkContract.winkerkEntry.SORTORDER = "VERJAAR";
                values.put(LIDMATE_TAG, 0);
                int rowsAffected = getContentResolver().update(WinkerkContract.winkerkEntry.CONTENT_URI, values, null, null);
                // startActivity(intent);
                finish();
            }
        });
    }

    private void initializeViewModel() {
        viewModel = new ViewModelProvider(this).get(EventViewModel.class);
    }

    private void requestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, 1);
        }

        // Request POST_NOTIFICATIONS permission for API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
            }
        }
    }

    private void initializeSharedPreferences() {
        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        AutoSMS = settings.getBoolean("AUTO_SMS", false);

        String hour = settings.getString("SMS-HOUR", "08");
        String minute = settings.getString("SMS-MINUTE", "00");

        EditText hourEdit = findViewById(R.id.time_hour);
        EditText minuteEdit = findViewById(R.id.time_minute);
        hourEdit.setText(hour);
        minuteEdit.setText(minute);
    }

    private void initializeViews() {
        mTextView = findViewById(R.id.char_count);
        radioGroup = findViewById(R.id.keuse);

        EditText messageEdit = findViewById(R.id.boodskap);
        messageEdit.addTextChangedListener(mTextEditorWatcher);

        CheckBox autoSmsCheck = findViewById(R.id.autosms_radio);
        CheckBox reminderCheck = findViewById(R.id.herinner_radio);

        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        autoSmsCheck.setChecked(AutoSMS);
        reminderCheck.setChecked(settings.getBoolean("HERINNER", false));

        setupCheckboxListeners(autoSmsCheck, reminderCheck);
    }

    private void setupCheckboxListeners(CheckBox autoSmsCheck, CheckBox reminderCheck) {
        autoSmsCheck.setOnClickListener(v -> {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_USER_INFO, 0).edit();
            editor.putBoolean("AUTO_SMS", autoSmsCheck.isChecked());
            editor.apply();
        });

        reminderCheck.setOnClickListener(v -> {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_USER_INFO, 0).edit();
            editor.putBoolean("HERINNER", reminderCheck.isChecked());
            editor.apply();
        });
    }
    private void syncRadioButtonWithData() {
        int checkedId = radioGroup.getCheckedRadioButtonId();

        // If no radio button is checked, default to Verjaar and check it
        if (checkedId == -1) {
            RadioButton birthdayRadio = findViewById(R.id.Keuse_Verjaar);
            birthdayRadio.setChecked(true);
            checkedId = R.id.Keuse_Verjaar;
        }

        handleEventTypeChange(checkedId);
    }
    private void setupEventTypeSelection() {
        RadioButton birthdayRadio = findViewById(R.id.Keuse_Verjaar);
        RadioButton baptismRadio = findViewById(R.id.Keuse_Doop);
        RadioButton marriageRadio = findViewById(R.id.Keuse_Huwelik);
        RadioButton confessionRadio = findViewById(R.id.Keuse_Belydenis);

        setRadioButtonSelection(birthdayRadio, baptismRadio, marriageRadio, confessionRadio);

        radioGroup.setOnCheckedChangeListener((group, checkedId) ->
                handleEventTypeChange(checkedId));

        // ✅ Force trigger the change listener to load initial data
        int checkedId = radioGroup.getCheckedRadioButtonId();
        if (checkedId != -1) {
            handleEventTypeChange(checkedId);
        }
    }

    private void setRadioButtonSelection(RadioButton birthday, RadioButton baptism,
                                         RadioButton marriage, RadioButton confession) {
        // Reset all
        birthday.setChecked(false);
        baptism.setChecked(false);
        marriage.setChecked(false);
        confession.setChecked(false);

        // Set based on current selection
        switch (KEUSE) {
            case "Verjaar":
                birthday.setChecked(true);
                break;
            case "Doop":
                baptism.setChecked(true);
                break;
            case "Huwelik":
                marriage.setChecked(true);
                break;
            case "Bely":
                confession.setChecked(true);
                break;
            default:
                birthday.setChecked(true);
                KEUSE = "Verjaar";
                break;
        }
    }

    private void handleEventTypeChange(int checkedId) {
        EditText messageEdit = findViewById(R.id.boodskap);
        ImageView imageView = findViewById(R.id.verjaar_sms);
        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);

        // Clear current cursor first
        mCursorAdapter.swapCursor(null);

        if (checkedId == R.id.Keuse_Verjaar) {
            KEUSE = "Verjaar";
            Log.d("VerjaarSMS2", "Switching to Birthday view");
            setMessageForEventType(messageEdit, settings, "VerjaarBoodskap",
                    "<<<naam>>>\nBaie geluk met jou verjaarsdag!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ");
            imageView.setImageDrawable(ResourcesCompat.getDrawable(getResources(), bdaysms, null));
            observeViewModel(viewModel.getBirthdayData(this));

        } else if (checkedId == R.id.Keuse_Doop) {
            KEUSE = "Doop";
            Log.d("VerjaarSMS2", "Switching to Baptism view");
            setMessageForEventType(messageEdit, settings, "DoopBoodskap",
                    "<<<naam>>>\nBaie geluk met jou doopherdenking!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ");
            imageView.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.doopsms, null));
            observeViewModel(viewModel.getBaptismData(this));

        } else if (checkedId == R.id.Keuse_Huwelik) {
            KEUSE = "Huwelik";
            Log.d("VerjaarSMS2", "Switching to Wedding view");
            setMessageForEventType(messageEdit, settings, "HuwelikBoodskap",
                    "<<<naam>>>\nBaie geluk met jou huweliksherdenking!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ");
            imageView.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.huweliksms, null));
            observeViewModel(viewModel.getWeddingData(this));

        } else if (checkedId == R.id.Keuse_Belydenis) {
            KEUSE = "Bely";
            Log.d("VerjaarSMS2", "Switching to Confession view");
            setMessageForEventType(messageEdit, settings, "BelyBoodskap",
                    "<<<naam>>>\nBaie geluk met jou herdenking van jou belydenis van geloof!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ");
            imageView.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.bely, null));
            observeViewModel(viewModel.getConfessionData(this));
        }

        WinkerkContract.winkerkEntry.SOEKLIST = false;
    }


    private void observeViewModel(androidx.lifecycle.LiveData<Cursor> liveData) {
        // Remove previous observer if it exists
        if (currentLiveData != null && currentObserver != null) {
            currentLiveData.removeObserver(currentObserver);
        }

        // Create new observer
        currentObserver = cursor -> {
            if (cursor != null) {
                Log.d("VerjaarSMS2", "Cursor updated with " + cursor.getCount() + " items for " + KEUSE);
                mCursorAdapter.swapCursor(cursor);
                mCursorAdapter.notifyDataSetChanged();
            } else {
                Log.w("VerjaarSMS2", "Received null cursor for " + KEUSE);
                mCursorAdapter.swapCursor(null);
            }
        };

        // Store reference to current LiveData
        currentLiveData = liveData;

        // Observe the new LiveData with the new observer
        currentLiveData.observe(this, currentObserver);
    }

    private void setMessageForEventType(EditText messageEdit, SharedPreferences settings,
                                        String prefKey, String defaultMessage) {
        String savedMessage = settings.getString(prefKey, "");
        messageEdit.setText(savedMessage.isEmpty() ? defaultMessage : savedMessage);
    }

    private void setupMessageInput() {
        EditText messageEdit = findViewById(R.id.boodskap);
        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);

        // Set initial message based on current event type
        String prefKey = getMessagePreferenceKey();
        String defaultMessage = getDefaultMessage();
        setMessageForEventType(messageEdit, settings, prefKey, defaultMessage);
    }

    private String getMessagePreferenceKey() {
        switch (KEUSE) {
            case "Doop": return "DoopBoodskap";
            case "Huwelik": return "HuwelikBoodskap";
            case "Bely": return "BelyBoodskap";
            default: return "VerjaarBoodskap";
        }
    }

    private String getDefaultMessage() {
        switch (KEUSE) {
            case "Doop":
                return "<<<naam>>>\nBaie geluk met jou doopherdenking!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ";
            case "Huwelik":
                return "<<<naam>>>\nBaie geluk met jou huweliksherdenking!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ";
            case "Bely":
                return "<<<naam>>>\nBaie geluk met jou herdenking van jou belydenis van geloof!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ";
            default:
                return "<<<naam>>>\nBaie geluk met jou verjaarsdag!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ";
        }
    }

    private void setupTimeInput() {
        EditText hourEdit = findViewById(R.id.time_hour);
        EditText minuteEdit = findViewById(R.id.time_minute);
        final String[] newtime = new String[1];

        hourEdit.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus && hourEdit.length() < 2) {
                newtime[0] = "0" + hourEdit.getText();
                hourEdit.setText(newtime[0]);
            }
        });

        minuteEdit.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus && minuteEdit.length() < 2) {
                newtime[0] = "0" + minuteEdit.getText();
                minuteEdit.setText(newtime[0]);
            }
        });
    }

    private void setupButtons() {
        Button setTimeButton = findViewById(R.id.set_time);
        setTimeButton.setOnClickListener(this::handleSetTimeClick);

        ImageView smsButton = findViewById(R.id.verjaar_sms);
        smsButton.setOnClickListener(this::handleSendSmsClick);

        Button updateMessageButton = findViewById(R.id.opdateerBoodskap);
        updateMessageButton.setOnClickListener(this::handleUpdateMessageClick);
    }

    private void handleSetTimeClick(View view) {
        EditText hourEdit = findViewById(R.id.time_hour);
        EditText minuteEdit = findViewById(R.id.time_minute);

        String hour = formatTimeUnit(hourEdit.getText().toString());
        String minute = formatTimeUnit(minuteEdit.getText().toString());

        saveTimeSettings(hour, minute);
        setupAlarm(hour, minute);
        navigateToMainActivity();
    }

    private String formatTimeUnit(String timeUnit) {
        return timeUnit.length() < 2 ? "0" + timeUnit : timeUnit;
    }

    private void saveTimeSettings(String hour, String minute) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_USER_INFO, 0).edit();
        editor.putString("SMS-HOUR", hour);
        editor.putString("SMS-MINUTE", minute);
        editor.putBoolean("SMS-TIMEUPDATE", true);
        editor.putBoolean("FROM_MENU", false);
        editor.apply();

        Toast.makeText(this, "Tyd opgedateer", Toast.LENGTH_SHORT).show();
    }

    private void setupAlarm(String hour, String minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hour));
        calendar.set(Calendar.MINUTE, Integer.parseInt(minute));
        calendar.set(Calendar.SECOND, 0);

        Calendar now = Calendar.getInstance();

        Intent alarmIntent = new Intent(this, AlarmReceiver.class);
        alarmIntent.setAction("VerjaarSMS");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                alarmIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        long triggerTime = calendar.getTimeInMillis() <= now.getTimeInMillis()
                ? calendar.getTimeInMillis() + AlarmManager.INTERVAL_DAY + 1
                : calendar.getTimeInMillis();

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerTime, AlarmManager.INTERVAL_DAY, pendingIntent);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity2.class);
        intent.putExtra("SENDER_CLASS_NAME", "WysVerjaar");
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        WinkerkContract.winkerkEntry.SORTORDER = "VERJAAR";
        WinkerkContract.winkerkEntry.SOEKLIST = false;
        startActivity(intent);
        finish();
    }

    private void handleSendSmsClick(View view) {
        EditText messageEdit = findViewById(R.id.boodskap);
        saveCurrentMessage(messageEdit.getText().toString());
        sendSmsToSelectedMembers(messageEdit.getText().toString());
    }

    private void saveCurrentMessage(String message) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_USER_INFO, 0).edit();
        String prefKey = getMessagePreferenceKey();
        editor.putString(prefKey, message);
        editor.apply();
    }

    private void sendSmsToSelectedMembers(String messageTemplate) {
        SmsManager smsManager = SmsManager.getDefault();
        ListView listView = findViewById(R.id.lidmaat_list);

        Cursor cursor = (Cursor) mCursorAdapter.getItem(listView.getFirstVisiblePosition());
        if (cursor == null || cursor.getCount() == 0) {
            Toast.makeText(this, "Geen lede gevind nie", Toast.LENGTH_SHORT).show();
            return;
        }

        int count = 0;
        cursor.moveToPosition(-1);

        while (cursor.moveToNext()) {
            if (shouldSendSmsToMember(cursor)) {
                count += sendSmsToMember(cursor, messageTemplate, smsManager);
                SystemClock.sleep(1000); // Rate limiting
            }
        }

        Toast.makeText(this, count + " verjaarsdag sms'e is gestuur!", Toast.LENGTH_SHORT).show();
    }

    private boolean shouldSendSmsToMember(Cursor cursor) {
        int tagColumn = cursor.getColumnIndex(LIDMATE_TAG);
        return (cursor.getInt(tagColumn) == 1) || AutoSMS;
    }

    private int sendSmsToMember(Cursor cursor, String messageTemplate, SmsManager smsManager) {
        int nameColumn = cursor.getColumnIndex(LIDMATE_NOEMNAAM);
        int phoneColumn = cursor.getColumnIndex(LIDMATE_SELFOON);

        if (cursor.isNull(phoneColumn)) {
            return 0;
        }

        String phoneNumber = cursor.getString(phoneColumn);
        if (phoneNumber.isEmpty()) {
            return 0;
        }

        String personalizedMessage = messageTemplate.replace("<<<naam>>>", cursor.getString(nameColumn));

        try {
            ArrayList<String> messageParts = smsManager.divideMessage(personalizedMessage);
            smsManager.sendMultipartTextMessage(phoneNumber, null, messageParts, null, null);

            // Log to SMS database
            logSentMessage(phoneNumber, personalizedMessage);

            Toast.makeText(this, messageParts.size() + " SMS na " + cursor.getString(nameColumn) + " is gestuur!",
                    Toast.LENGTH_SHORT).show();

            return 1;
        } catch (Exception e) {
            Log.e("VerjaarSMS2", "Failed to send SMS: " + e.getMessage());
            return 0;
        }
    }

    private void logSentMessage(String phoneNumber, String message) {
        try {
            ContentValues values = new ContentValues();
            values.put(Telephony.Sms.ADDRESS, phoneNumber);
            values.put(Telephony.Sms.DATE, System.currentTimeMillis());
            values.put(Telephony.Sms.READ, 1);
            values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT);
            values.put(Telephony.Sms.BODY, message);

            getContentResolver().insert(Telephony.Sms.Sent.CONTENT_URI, values);
        } catch (Exception e) {
            Log.e("VerjaarSMS2", "Failed to log SMS: " + e.getMessage());
        }
    }

    private void handleUpdateMessageClick(View view) {
        EditText messageEdit = findViewById(R.id.boodskap);
        saveCurrentMessage(messageEdit.getText().toString());
        Toast.makeText(this, "Boodskap opgedateer", Toast.LENGTH_SHORT).show();
    }

    private void setupListView() {
        ListView listView = findViewById(R.id.lidmaat_list);
        mCursorAdapter = new WinkerkCursorAdapter(this, null);
        listView.setAdapter(mCursorAdapter);
        listView.setFastScrollEnabled(true);
        listView.setClickable(true);

        listView.setOnItemLongClickListener(this::handleListItemLongClick);
        listView.setOnItemClickListener(this::handleListItemClick);
    }

    private boolean handleListItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Cursor cursor = (Cursor) mCursorAdapter.getItem(position);
        ContentValues values = new ContentValues();

        int tagColumn = cursor.getColumnIndex(LIDMATE_TAG);
        int newTagValue = cursor.getInt(tagColumn) == 0 ? 1 : 0;
        values.put(LIDMATE_TAG, newTagValue);

        Uri memberUri = ContentUris.withAppendedId(WinkerkContract.winkerkEntry.CONTENT_URI, id);
        int rowsAffected = getContentResolver().update(memberUri, values,
                LIDMATE_TABLE_NAME + "._rowid_ = ?", new String[]{String.valueOf(id)});

        if (rowsAffected == 1) {
            UpdateUI();
        }

        return rowsAffected == 1;
    }

    private void handleListItemClick(AdapterView<?> parent, View view, int position, long id) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.lidmaatlist_menu, popup.getMenu());
        setForceShowIcon(popup);

        Cursor cursor = (Cursor) mCursorAdapter.getItem(position);
        listItemPositionForPopupMenu = position;
        listItemPositionForPopupMenu2 = (int) id;

        setupPopupMenuItems(popup, cursor);
        popup.show();
        popup.setOnMenuItemClickListener(item -> handlePopupMenuClick(item, cursor));
    }

    private void setupPopupMenuItems(PopupMenu popup, Cursor cursor) {
        int nameColumn = cursor.getColumnIndex(LIDMATE_NOEMNAAM);
        int surnameColumn = cursor.getColumnIndex(LIDMATE_VAN);
        int phoneColumn = cursor.getColumnIndex(LIDMATE_SELFOON);
        int landlineColumn = cursor.getColumnIndex(LIDMATE_LANDLYN);
        int emailColumn = cursor.getColumnIndex(LIDMATE_EPOS);

        String name = cursor.getString(nameColumn);
        String surname = cursor.getString(surnameColumn);

        popup.getMenu().findItem(R.id.kyk_lidmaat_detail).setTitle("Detail van " + name + " " + surname);
        popup.getMenu().findItem(R.id.submenu_bel).setTitle("Skakel " + name);
        popup.getMenu().findItem(R.id.submenu_teks).setTitle("Teks " + name);
        popup.getMenu().findItem(R.id.submenu_ander).setTitle(name);

        // Remove menu items for missing data
        if (cursor.isNull(phoneColumn)) {
            removePhoneMenuItems(popup);
        } else {
            popup.getMenu().findItem(R.id.bel_selfoon).setTitle("Skakel " + cursor.getString(phoneColumn));
            popup.getMenu().findItem(R.id.stuur_sms).setTitle("SMS na " + cursor.getString(phoneColumn));
        }

        if (cursor.isNull(landlineColumn)) {
            popup.getMenu().findItem(R.id.submenu_bel).getSubMenu().removeItem(R.id.bel_landlyn);
        } else {
            popup.getMenu().findItem(R.id.bel_landlyn).setTitle("Skakel " + cursor.getString(landlineColumn));
        }

        if (cursor.isNull(emailColumn) || cursor.getString(emailColumn).isEmpty()) {
            popup.getMenu().findItem(R.id.submenu_teks).getSubMenu().removeItem(R.id.stuur_epos);
        }

        // Remove WhatsApp options if not enabled
        removeWhatsAppMenuItems(popup);
    }

    private void removePhoneMenuItems(PopupMenu popup) {
        popup.getMenu().findItem(R.id.submenu_bel).getSubMenu().removeItem(R.id.bel_selfoon);
        popup.getMenu().findItem(R.id.submenu_teks).getSubMenu().removeItem(R.id.stuur_sms);
        removeWhatsAppMenuItems(popup);
    }

    private void removeWhatsAppMenuItems(PopupMenu popup) {
        if (!WHATSAPP1) popup.getMenu().findItem(R.id.submenu_teks).getSubMenu().removeItem(R.id.stuur_whatsapp);
        if (!WHATSAPP2) popup.getMenu().findItem(R.id.submenu_teks).getSubMenu().removeItem(R.id.stuur_whatsapp2);
        if (!WHATSAPP3) popup.getMenu().findItem(R.id.submenu_teks).getSubMenu().removeItem(R.id.stuur_whatsapp3);
    }

    private boolean handlePopupMenuClick(MenuItem item, Cursor cursor) {
        // Extract cursor data
        MemberData memberData = extractMemberData(cursor);

        int id = item.getItemId();

        if (id == R.id.kyk_lidmaat_detail) {
            return openMemberDetails(memberData.id);
        } else if (id == R.id.bel_selfoon) {
            return makePhoneCall(memberData.phone);
        } else if (id == R.id.bel_landlyn) {
            return makePhoneCall(memberData.landline);
        } else if (id == R.id.stuur_sms) {
            return sendSms(memberData.phone, memberData.name);
        } else if (id == R.id.stuur_epos) {
            return sendEmail(memberData.email);
        } else if (id == R.id.stuur_whatsapp || id == R.id.stuur_whatsapp2 || id == R.id.stuur_whatsapp3) {
            return sendWhatsApp(memberData.phone, item.getItemId(), memberData.name);
        } else if (id == R.id.kopieer) {
            return copyMemberInfo(memberData);
        } else if (id == R.id.nota) {
            return createNote(memberData);
        } else if (id == R.id.copy_to_contacts) {
            return copyToContacts(memberData);
        } else {
            return false;
        }
    }

    private MemberData extractMemberData(Cursor cursor) {
        MemberData data = new MemberData();

        data.id = CursorDataExtractor.getSafeInt(cursor, "_id", -1);
        data.name = CursorDataExtractor.getSafeString(cursor, LIDMATE_NOEMNAAM, "");
        data.surname = CursorDataExtractor.getSafeString(cursor, LIDMATE_VAN, "");
        data.phone = getPhoneNumber(CursorDataExtractor.getSafeString(cursor, LIDMATE_SELFOON, ""));
        data.landline = getPhoneNumber(CursorDataExtractor.getSafeString(cursor, LIDMATE_LANDLYN, ""));
        data.email = CursorDataExtractor.getSafeString(cursor, LIDMATE_EPOS, "");
        data.address = CursorDataExtractor.getSafeString(cursor, LIDMATE_STRAATADRES, "");
        data.birthday = CursorDataExtractor.getSafeString(cursor, LIDMATE_GEBOORTEDATUM, "");

        return data;
    }
//    private MemberData extractMemberData(Cursor cursor) {
//        MemberData data = new MemberData();
//        data.id = cursor.getInt(cursor.getColumnIndex("_id"));
//        data.name = cursor.getString(cursor.getColumnIndex(LIDMATE_NOEMNAAM));
//        data.surname = cursor.getString(cursor.getColumnIndex(LIDMATE_VAN));
//        data.phone = getPhoneNumber(cursor.getString(cursor.getColumnIndex(LIDMATE_SELFOON)));
//        data.landline = getPhoneNumber(cursor.getString(cursor.getColumnIndex(LIDMATE_LANDLYN)));
//        data.email = cursor.getString(cursor.getColumnIndex(LIDMATE_EPOS));
//        data.address = cursor.getString(cursor.getColumnIndex(LIDMATE_STRAATADRES));
//        data.birthday = cursor.getString(cursor.getColumnIndex(LIDMATE_GEBOORTEDATUM));
//        return data;
//    }

    private String getPhoneNumber(String number) {
        return (number != null && !number.isEmpty()) ? fixphonenumber(number) : null;
    }

    private void loadInitialData() {
        // Get the currently checked radio button
        int checkedId = radioGroup.getCheckedRadioButtonId();

        // If no radio button is checked (shouldn't happen due to setupEventTypeSelection),
        // default to Verjaar
        if (checkedId == -1) {
            checkedId = R.id.Keuse_Verjaar;
            RadioButton birthdayRadio = findViewById(R.id.Keuse_Verjaar);
            birthdayRadio.setChecked(true);
        }

        // Load data for the currently selected event type
        handleEventTypeChange(checkedId);
        handleAutoSMS();
    }

    private void handleAutoSMS() {
        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        AutoSMS = settings.getBoolean("AUTO_SMS", false);
        boolean fromMenu = settings.getBoolean("FROM_MENU", false);

        if (!fromMenu && AutoSMS) {
            ImageView smsButton = findViewById(R.id.verjaar_sms);
            smsButton.performClick();

            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("FROM_MENU", false);
            editor.apply();
            finish();
        }
    }

    // Helper classes
    private static class MemberData {
        int id;
        String name;
        String surname;
        String phone;
        String landline;
        String email;
        String address;
        String birthday;
    }

    // Implementation methods for menu actions
    private boolean openMemberDetails(int memberId) {
        try {
            Intent intent = new Intent(this, lidmaat_detail_Activity.class);
            WinkerkContract.winkerkEntry.LIDMAATID = memberId;
            Uri memberUri = ContentUris.withAppendedId(WinkerkContract.winkerkEntry.CONTENT_URI, memberId);
            intent.setData(memberUri);
            startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e("VerjaarSMS2", "Failed to open member details: " + e.getMessage());
            Toast.makeText(this, "Kan nie lidmaat besonderhede oopmaak nie", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean makePhoneCall(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        try {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            startActivity(callIntent);
            return true;
        } catch (Exception e) {
            Log.e("VerjaarSMS2", "Failed to make phone call: " + e.getMessage());
            Toast.makeText(this, "Kan nie oproep maak nie", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean sendSms(String phoneNumber, String naam) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        try {
            Intent smsIntent = new Intent(Intent.ACTION_VIEW);
            smsIntent.setData(Uri.parse("sms:" + phoneNumber));

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                smsIntent.setType("vnd.android-dir/mms-sms");
            }

            // Verify that there's an app to handle this intent
            if (smsIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(smsIntent);
                return true;
            } else {
                Log.e("VerjaarSMS2", "No SMS app available");
                Toast.makeText(this, "Geen SMS app gevind nie", Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (Exception e) {
            Log.e("VerjaarSMS2", "Failed to send SMS: " + e.getMessage(), e);
            Toast.makeText(this, "Kan nie SMS stuur nie", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean sendEmail(String emailAddress) {
        if (emailAddress == null || emailAddress.isEmpty()) {
            return false;
        }

        try {
            Intent emailIntent = new Intent(Intent.ACTION_VIEW);
            emailIntent.setData(Uri.parse("mailto:" + emailAddress));
            startActivity(emailIntent);
            return true;
        } catch (Exception e) {
            Log.e("VerjaarSMS2", "Failed to send email: " + e.getMessage());
            Toast.makeText(this, "Kan nie e-pos stuur nie", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean sendWhatsApp(String phoneNumber, int whatsAppType, String naam) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        EditText messageEdit = findViewById(R.id.boodskap);
        String message = messageEdit.getText().toString().replace("<<<naam>>>", '*'+naam+'*');
        try {
            if (whatsAppType == R.id.stuur_whatsapp) {
                return sendWhatsAppMethod1(phoneNumber, message);
            } else if (whatsAppType == R.id.stuur_whatsapp2) {
                return sendWhatsAppMethod2(phoneNumber, message);
            } else if (whatsAppType == R.id.stuur_whatsapp3) {
                return sendWhatsAppMethod3(phoneNumber, message);
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.e("VerjaarSMS2", "Failed to send WhatsApp: " + e.getMessage());
            Toast.makeText(this, "WhatsApp nie geïnstalleer nie", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean sendWhatsAppMethod1(String phoneNumber, String message) throws Exception {
        Uri uri = Uri.parse("smsto: " + phoneNumber);
        Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
        intent.putExtra("jid", phoneNumber);
        intent.setPackage("com.whatsapp");
        intent.putExtra("sms_body", message);
        intent.putExtra("android.intent.extra.TEXT", message);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, ""));
        return true;
    }

    private boolean sendWhatsAppMethod2(String phoneNumber, String message) {
        try {
            PackageManager packageManager = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String encodedMessage = URLEncoder.encode(message, "UTF-8");
            String url = "https://api.whatsapp.com/send?phone=" + phoneNumber + "&text=" + encodedMessage;
            intent.setPackage("com.whatsapp");
            intent.setData(Uri.parse(url));

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent);
                return true;
            }
            return false;
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e("VerjaarSMS2", "UTF-8 encoding not supported: " + e.getMessage());
            // Fallback without encoding
            try {
                String url = "https://api.whatsapp.com/send?phone=" + phoneNumber + "&text=" + message;
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setPackage("com.whatsapp");
                intent.setData(Uri.parse(url));
                startActivity(intent);
                return true;
            } catch (Exception fallbackException) {
                throw new RuntimeException("Failed to send WhatsApp message", fallbackException);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to send WhatsApp message", e);
        }
    }

    private boolean sendWhatsAppMethod3(String phoneNumber, String message) throws Exception {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setAction("android.intent.action.SEND");
        intent.setPackage("com.whatsapp");
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.putExtra("android.intent.extra.TEXT", message);
        intent.putExtra("jid", phoneNumber + "@s.whatsapp.net");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
        return true;
    }

    private boolean copyMemberInfo(MemberData memberData) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return false;
            }

            StringBuilder clipData = new StringBuilder();

            if (memberData.name != null && !memberData.name.isEmpty()) {
                clipData.append("\r\nNaam: ").append(memberData.name);
            }
            if (memberData.surname != null && !memberData.surname.isEmpty()) {
                clipData.append("\r\nVan: ").append(memberData.surname);
            }
            if (memberData.phone != null && !memberData.phone.isEmpty()) {
                clipData.append("\r\nSelfoon: ").append(memberData.phone);
            }
            if (memberData.landline != null && !memberData.landline.isEmpty()) {
                clipData.append("\r\nLandlyn: ").append(memberData.landline);
            }
            if (memberData.email != null && !memberData.email.isEmpty()) {
                clipData.append("\r\nEpos: ").append(memberData.email);
            }
            if (memberData.address != null && !memberData.address.isEmpty()) {
                clipData.append("\r\nAdres: ").append(memberData.address);
            }

            String finalClipData = clipData.toString().replaceAll("\r\n\r\n", "\r\n");
            ClipData clip = ClipData.newPlainText("member_info", finalClipData);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(this, "Inligting gekopieer", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            Log.e("VerjaarSMS2", "Failed to copy member info: " + e.getMessage());
            return false;
        }
    }

    private boolean createNote(MemberData memberData) {
        try {
            Intent noteIntent = new Intent();
            noteIntent.setType("vnd.android.cursor.item/event");

            long currentTime = new Date().getTime();
            noteIntent.putExtra("beginTime", currentTime);
            noteIntent.putExtra("endTime", currentTime + DateUtils.HOUR_IN_MILLIS);
            noteIntent.putExtra("title", memberData.name + " " + memberData.surname);

            StringBuilder noteData = new StringBuilder();
            if (memberData.name != null && !memberData.name.isEmpty()) {
                noteData.append("\r\nNaam: ").append(memberData.name);
            }
            if (memberData.surname != null && !memberData.surname.isEmpty()) {
                noteData.append("\r\nVan: ").append(memberData.surname);
            }
            if (memberData.phone != null && !memberData.phone.isEmpty()) {
                noteData.append("\r\nSelfoon: ").append(memberData.phone);
            }
            if (memberData.landline != null && !memberData.landline.isEmpty()) {
                noteData.append("\r\nLandlyn: ").append(memberData.landline);
            }
            if (memberData.email != null && !memberData.email.isEmpty()) {
                noteData.append("\r\nEpos: ").append(memberData.email);
            }
            if (memberData.address != null && !memberData.address.isEmpty()) {
                noteData.append("\r\nAdres: ").append(memberData.address);
            }

            noteIntent.putExtra("description", noteData.toString());
            noteIntent.setAction(Intent.ACTION_EDIT);
            startActivity(noteIntent);
            return true;
        } catch (Exception e) {
            Log.e("VerjaarSMS2", "Failed to create note: " + e.getMessage());
            Toast.makeText(this, "Kan nie nota skep nie", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean copyToContacts(MemberData memberData) {
        try {
            Intent contactIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            contactIntent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);

            String fullName = memberData.name + ", " + memberData.surname;
            contactIntent.putExtra(ContactsContract.Intents.Insert.NAME, fullName);

            if (memberData.phone != null && !memberData.phone.isEmpty()) {
                contactIntent.putExtra(ContactsContract.Intents.Insert.PHONE, memberData.phone);
                contactIntent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
            }

            if (memberData.landline != null && !memberData.landline.isEmpty()) {
                contactIntent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, memberData.landline);
                contactIntent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME);
            }

            if (memberData.email != null && !memberData.email.isEmpty()) {
                contactIntent.putExtra(ContactsContract.Intents.Insert.EMAIL, memberData.email);
            }

            if (memberData.address != null && !memberData.address.isEmpty()) {
                String cleanAddress = memberData.address.replace("\r\n", ", ");
                contactIntent.putExtra(ContactsContract.Intents.Insert.POSTAL, cleanAddress);
            }

            // Add birthday if available
            if (memberData.birthday != null && memberData.birthday.length() >= 10) {
                String birthday = memberData.birthday.substring(0, 10);

                ArrayList<ContentValues> data = new ArrayList<>();
                ContentValues birthdayData = new ContentValues();
                birthdayData.put(ContactsContract.CommonDataKinds.Event.MIMETYPE,
                        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE);
                birthdayData.put(ContactsContract.CommonDataKinds.Event.TYPE,
                        ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY);
                birthdayData.put(ContactsContract.CommonDataKinds.Event.START_DATE, birthday);
                data.add(birthdayData);

                // Add nickname
                ContentValues nicknameData = new ContentValues();
                nicknameData.put(ContactsContract.CommonDataKinds.Nickname.MIMETYPE,
                        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE);
                nicknameData.put(ContactsContract.CommonDataKinds.Nickname.TYPE,
                        ContactsContract.CommonDataKinds.Nickname.TYPE_SHORT_NAME);
                nicknameData.put(ContactsContract.CommonDataKinds.Nickname.NAME, memberData.name);
                data.add(nicknameData);

                contactIntent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data);
            }

            startActivity(contactIntent);
            return true;
        } catch (Exception e) {
            Log.e("VerjaarSMS2", "Failed to copy to contacts: " + e.getMessage());
            Toast.makeText(this, "Kan nie na kontakte kopieer nie", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void UpdateUI() {
        int checkedId = radioGroup.getCheckedRadioButtonId();

        // Clear current cursor first
        mCursorAdapter.swapCursor(null);

        if (checkedId == R.id.Keuse_Verjaar) {
            KEUSE = "Verjaar";
            Log.d("VerjaarSMS2", "UpdateUI: Switching to Birthday");
            observeViewModel(viewModel.getBirthdayData(this));
        } else if (checkedId == R.id.Keuse_Doop) {
            KEUSE = "Doop";
            Log.d("VerjaarSMS2", "UpdateUI: Switching to Baptism");
            observeViewModel(viewModel.getBaptismData(this));
        } else if (checkedId == R.id.Keuse_Huwelik) {
            KEUSE = "Huwelik";
            Log.d("VerjaarSMS2", "UpdateUI: Switching to Wedding");
            observeViewModel(viewModel.getWeddingData(this));
        } else if (checkedId == R.id.Keuse_Belydenis) {
            KEUSE = "Bely";
            Log.d("VerjaarSMS2", "UpdateUI: Switching to Confession");
            observeViewModel(viewModel.getConfessionData(this));
        }
    }

    private void setForceShowIcon(PopupMenu popupMenu) {
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
        } catch (Throwable e) {
            Log.e("VerjaarSMS2", "Failed to force show popup icons: " + e.getMessage());
        }
    }

}
