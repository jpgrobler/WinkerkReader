package za.co.jpsoft.winkerkreader.ui.activities

import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.ui.adapters.WinkerkCursorAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.EventViewModel
import za.co.jpsoft.winkerkreader.services.receivers.AlarmReceiver
import za.co.jpsoft.winkerkreader.utils.Utils
import za.co.jpsoft.winkerkreader.utils.AppSessionState
import za.co.jpsoft.winkerkreader.utils.MemberActionHandler
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.loader.app.LoaderManager

import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.utils.forceShowIcons
import za.co.jpsoft.winkerkreader.utils.getIntOrDefault
import za.co.jpsoft.winkerkreader.utils.getStringOrEmpty
import za.co.jpsoft.winkerkreader.utils.getStringOrNull
import java.net.URLEncoder
import java.util.*

class VerjaarSmsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VerjaarSmsActivity"
        private const val MAX_SMS_MESSAGE_LENGTH = 160
    }

    private lateinit var mCursorAdapter: WinkerkCursorAdapter
    private lateinit var mTextView: TextView
    private var autoSms = false
    private lateinit var viewModel: EventViewModel
    private var listItemPositionForPopupMenu = 0
    private var listItemPositionForPopupMenu2 = 0
    private lateinit var radioGroup: RadioGroup

    private var currentLiveData: LiveData<Cursor?>? = null
    private var currentObserver: Observer<Cursor?>? = null

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            mTextView.text = (MAX_SMS_MESSAGE_LENGTH - (s?.length ?: 0)).toString()
        }
        override fun afterTextChanged(s: Editable?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.verjaar)

        AppSessionState.sortOrder = "VERJAAR"

        initializeComponents()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                LoaderManager.getInstance(this@VerjaarSmsActivity).destroyLoader(10)
                mCursorAdapter.swapCursor(null)
                val values = ContentValues().apply {
                    put(winkerkEntry.LIDMATE_TAG, 0)
                }
                contentResolver.update(winkerkEntry.CONTENT_URI, values, null, null)
                finish()
            }
        })
    }

    override fun onDestroy() {
        currentLiveData?.removeObserver(currentObserver ?: return)
        mCursorAdapter.swapCursor(null)
        AppSessionState.sortOrder = za.co.jpsoft.winkerkreader.utils.SettingsManager(this).defLayout
        AppSessionState.soekList = false
        LoaderManager.getInstance(this).destroyLoader(10)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initializeComponents() {
        initializeViewModel()
        requestPermissions()
        initializeSharedPreferences()
        initializeViews()
        setupListView()
        setupEventTypeSelection()
        setupMessageInput()
        setupTimeInput()
        setupButtons()
        loadInitialData()
        handleAutoSMS()
    }

    private fun initializeViewModel() {
        viewModel = ViewModelProvider(this)[EventViewModel::class.java]
    }

    private fun requestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            }
        }
    }

    private fun initializeSharedPreferences() {
        val settings = getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)
        autoSms = settings.getBoolean("AUTO_SMS", false)

        val hour = settings.getString("SMS-HOUR", "08") ?: "08"
        val minute = settings.getString("SMS-MINUTE", "00") ?: "00"

        findViewById<EditText>(R.id.time_hour).setText(hour)
        findViewById<EditText>(R.id.time_minute).setText(minute)
    }

    private fun initializeViews() {
        mTextView = findViewById(R.id.char_count)
        radioGroup = findViewById(R.id.keuse)

        val messageEdit = findViewById<EditText>(R.id.boodskap)
        messageEdit.addTextChangedListener(textWatcher)

        val autoSmsCheck = findViewById<CheckBox>(R.id.autosms_radio)
        val reminderCheck = findViewById<CheckBox>(R.id.herinner_radio)

        val settings = getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)
        autoSmsCheck.isChecked = autoSms
        reminderCheck.isChecked = settings.getBoolean("HERINNER", false)

        setupCheckboxListeners(autoSmsCheck, reminderCheck)
    }

    private fun setupCheckboxListeners(autoSmsCheck: CheckBox, reminderCheck: CheckBox) {
        autoSmsCheck.setOnClickListener {
            getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE).edit()
                .putBoolean("AUTO_SMS", autoSmsCheck.isChecked)
                .apply()
        }
        reminderCheck.setOnClickListener {
            getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE).edit()
                .putBoolean("HERINNER", reminderCheck.isChecked)
                .apply()
        }
    }

    private fun syncRadioButtonWithData() {
        val checkedId = radioGroup.checkedRadioButtonId
        if (checkedId == -1) {
            findViewById<RadioButton>(R.id.Keuse_Verjaar).isChecked = true
            handleEventTypeChange(R.id.Keuse_Verjaar)
        } else {
            handleEventTypeChange(checkedId)
        }
    }

    private fun setupEventTypeSelection() {
        val birthdayRadio = findViewById<RadioButton>(R.id.Keuse_Verjaar)
        val baptismRadio = findViewById<RadioButton>(R.id.Keuse_Doop)
        val marriageRadio = findViewById<RadioButton>(R.id.Keuse_Huwelik)
        val confessionRadio = findViewById<RadioButton>(R.id.Keuse_Belydenis)

        setRadioButtonSelection(birthdayRadio, baptismRadio, marriageRadio, confessionRadio)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            handleEventTypeChange(checkedId)
        }

        val checkedId = radioGroup.checkedRadioButtonId
        if (checkedId != -1) {
            handleEventTypeChange(checkedId)
        }
    }

    private fun setRadioButtonSelection(
        birthday: RadioButton,
        baptism: RadioButton,
        marriage: RadioButton,
        confession: RadioButton
    ) {
        birthday.isChecked = false
        baptism.isChecked = false
        marriage.isChecked = false
        confession.isChecked = false

        when (AppSessionState.keuse) {
            "Verjaar" -> birthday.isChecked = true
            "Doop" -> baptism.isChecked = true
            "Huwelik" -> marriage.isChecked = true
            "Bely" -> confession.isChecked = true
            else -> {
                birthday.isChecked = true
                AppSessionState.keuse = "Verjaar"
            }
        }
    }

    private fun handleEventTypeChange(checkedId: Int) {
        val messageEdit = findViewById<EditText>(R.id.boodskap)
        val imageView = findViewById<ImageView>(R.id.verjaar_sms)
        val settings = getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)

        mCursorAdapter.swapCursor(null)

        when (checkedId) {
            R.id.Keuse_Verjaar -> {
                AppSessionState.keuse = "Verjaar"
                Log.d(TAG, "Switching to Birthday view")
                setMessageForEventType(messageEdit, settings, "VerjaarBoodskap",
                    "<<<naam>>>\nBaie geluk met jou verjaarsdag!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ")
                imageView.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.bdaysms, null))
                observeViewModel(viewModel.getBirthdayData(this))
            }
            R.id.Keuse_Doop -> {
                AppSessionState.keuse = "Doop"
                Log.d(TAG, "Switching to Baptism view")
                setMessageForEventType(messageEdit, settings, "DoopBoodskap",
                    "<<<naam>>>\nBaie geluk met jou doopherdenking!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ")
                imageView.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.doopsms, null))
                observeViewModel(viewModel.getBaptismData(this))
            }
            R.id.Keuse_Huwelik -> {
                AppSessionState.keuse = "Huwelik"
                Log.d(TAG, "Switching to Wedding view")
                setMessageForEventType(messageEdit, settings, "HuwelikBoodskap",
                    "<<<naam>>>\nBaie geluk met jou huweliksherdenking!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ")
                imageView.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.huweliksms, null))
                observeViewModel(viewModel.getWeddingData(this))
            }
            R.id.Keuse_Belydenis -> {
                AppSessionState.keuse = "Bely"
                Log.d(TAG, "Switching to Confession view")
                setMessageForEventType(messageEdit, settings, "BelyBoodskap",
                    "<<<naam>>>\nBaie geluk met jou herdenking van jou belydenis van geloof!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ")
                imageView.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.bely, null))
                observeViewModel(viewModel.getConfessionData(this))
            }
        }

        AppSessionState.soekList = false
    }

    private fun observeViewModel(liveData: LiveData<Cursor?>) {
        currentLiveData?.removeObserver(currentObserver ?: return)

        val observer = Observer<Cursor?> { cursor ->
            if (cursor != null) {
                Log.d(TAG, "Cursor updated with ${cursor.count} items for ${AppSessionState.keuse}")
                mCursorAdapter.swapCursor(cursor)
                mCursorAdapter.notifyDataSetChanged()
            } else {
                Log.w(TAG, "Received null cursor for ${AppSessionState.keuse}")
                mCursorAdapter.swapCursor(null)
            }
        }

        currentObserver = observer
        currentLiveData = liveData
        liveData.observe(this, observer)
    }

    private fun setMessageForEventType(
        messageEdit: EditText,
        settings: SharedPreferences,
        prefKey: String,
        defaultMessage: String
    ) {
        val savedMessage = settings.getString(prefKey, "")
        messageEdit.setText(if (savedMessage.isNullOrEmpty()) defaultMessage else savedMessage)
    }

    private fun setupMessageInput() {
        val messageEdit = findViewById<EditText>(R.id.boodskap)
        val settings = getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)

        val prefKey = when (AppSessionState.keuse) {
            "Doop" -> "DoopBoodskap"
            "Huwelik" -> "HuwelikBoodskap"
            "Bely" -> "BelyBoodskap"
            else -> "VerjaarBoodskap"
        }
        val defaultMessage = when (AppSessionState.keuse) {
            "Doop" -> "<<<naam>>>\nBaie geluk met jou doopherdenking!\nMag die Here se genade jou daagliks vervul!\nGroete Ds "
            "Huwelik" -> "<<<naam>>>\nBaie geluk met jou huweliksherdenking!\nMag die Here se genade jou daagliks vervul!\nGroete Ds "
            "Bely" -> "<<<naam>>>\nBaie geluk met jou herdenking van jou belydenis van geloof!\nMag die Here se genade jou daagliks vervul!\nGroete Ds "
            else -> "<<<naam>>>\nBaie geluk met jou verjaarsdag!\nMag die Here se genade jou daagliks vervul!\nGroete Ds "
        }
        setMessageForEventType(messageEdit, settings, prefKey, defaultMessage)
    }

    private fun setupTimeInput() {
        val hourEdit = findViewById<EditText>(R.id.time_hour)
        val minuteEdit = findViewById<EditText>(R.id.time_minute)
        val newTime = arrayOf("")

        hourEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && hourEdit.length() < 2) {
                newTime[0] = "0" + hourEdit.text
                hourEdit.setText(newTime[0])
            }
        }
        minuteEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && minuteEdit.length() < 2) {
                newTime[0] = "0" + minuteEdit.text
                minuteEdit.setText(newTime[0])
            }
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.set_time).setOnClickListener(::handleSetTimeClick)
        findViewById<ImageView>(R.id.verjaar_sms).setOnClickListener(::handleSendSmsClick)
        findViewById<Button>(R.id.opdateerBoodskap).setOnClickListener(::handleUpdateMessageClick)
    }

    private fun handleSetTimeClick(view: View) {
        val hourEdit = findViewById<EditText>(R.id.time_hour)
        val minuteEdit = findViewById<EditText>(R.id.time_minute)

        val hour = formatTimeUnit(hourEdit.text.toString())
        val minute = formatTimeUnit(minuteEdit.text.toString())

        saveTimeSettings(hour, minute)
        setupAlarm(hour, minute)
        navigateToMainActivity()
    }

    private fun formatTimeUnit(timeUnit: String): String {
        return if (timeUnit.length < 2) "0$timeUnit" else timeUnit
    }

    private fun saveTimeSettings(hour: String, minute: String) {
        getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE).edit()
            .putString("SMS-HOUR", hour)
            .putString("SMS-MINUTE", minute)
            .putBoolean("SMS-TIMEUPDATE", true)
            .putBoolean("FROM_MENU", false)
            .apply()
        Toast.makeText(this, "Tyd opgedateer", Toast.LENGTH_SHORT).show()
    }

    private fun setupAlarm(hour: String, minute: String) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.toInt())
            set(Calendar.MINUTE, minute.toInt())
            set(Calendar.SECOND, 0)
        }
        val now = Calendar.getInstance()

        val alarmIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = "VerjaarSMS"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            alarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = if (calendar.timeInMillis <= now.timeInMillis) {
            calendar.timeInMillis + AlarmManager.INTERVAL_DAY + 1
        } else {
            calendar.timeInMillis
        }
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerTime, AlarmManager.INTERVAL_DAY, pendingIntent)
    }

    private fun navigateToMainActivity() {
        Intent(this, MainActivity::class.java).apply {
            putExtra("SENDER_CLASS_NAME", "WysVerjaar")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            AppSessionState.sortOrder = "VERJAAR"
            AppSessionState.soekList = false
            startActivity(this)
        }
        finish()
    }

    private fun handleSendSmsClick(view: View) {
        val messageEdit = findViewById<EditText>(R.id.boodskap)
        val message = messageEdit.text.toString()
        saveCurrentMessage(message)
        sendSmsToSelectedMembers(message)
    }

    private fun saveCurrentMessage(message: String) {
        val prefKey = when (AppSessionState.keuse) {
            "Doop" -> "DoopBoodskap"
            "Huwelik" -> "HuwelikBoodskap"
            "Bely" -> "BelyBoodskap"
            else -> "VerjaarBoodskap"
        }
        getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE).edit()
            .putString(prefKey, message)
            .apply()
    }

    private fun sendSmsToSelectedMembers(messageTemplate: String) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: run {
            Toast.makeText(this, "SMS manager not available", Toast.LENGTH_SHORT).show()
            return
        }

        val listView = findViewById<ListView>(R.id.lidmaat_list)
        val cursor = mCursorAdapter.getItem(listView.firstVisiblePosition) as? Cursor
            ?: run {
                Toast.makeText(this, "Geen lede gevind nie", Toast.LENGTH_SHORT).show()
                return
            }

        if (cursor.count == 0) {
            Toast.makeText(this, "Geen lede gevind nie", Toast.LENGTH_SHORT).show()
            return
        }

        var count = 0
        cursor.moveToPosition(-1)
        while (cursor.moveToNext()) {
            if (shouldSendSmsToMember(cursor)) {
                count += sendSmsToMember(cursor, messageTemplate, smsManager)
                SystemClock.sleep(1000)
            }
        }
        Toast.makeText(this, "$count verjaarsdag sms'e is gestuur!", Toast.LENGTH_SHORT).show()
    }

    private fun shouldSendSmsToMember(cursor: Cursor): Boolean {
        return cursor.getIntOrDefault(winkerkEntry.LIDMATE_TAG, 0) == 1 || autoSms
    }

    private fun sendSmsToMember(cursor: Cursor, messageTemplate: String, smsManager: SmsManager): Int {
        val phoneNumber = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_SELFOON)
        if (phoneNumber.isEmpty()) return 0

        val name = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)
        val personalizedMessage = messageTemplate.replace("<<<naam>>>", name)

        try {
            val messageParts = smsManager.divideMessage(personalizedMessage)
            smsManager.sendMultipartTextMessage(phoneNumber, null, messageParts, null, null)

            logSentMessage(phoneNumber, personalizedMessage)

            Toast.makeText(this, "${messageParts.size} SMS na $name is gestuur!",
                Toast.LENGTH_SHORT).show()
            return 1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS: ${e.message}")
            return 0
        }
    }

    private fun logSentMessage(phoneNumber: String, message: String) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, phoneNumber)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.BODY, message)
            }
            contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log SMS: ${e.message}")
        }
    }

    private fun handleUpdateMessageClick(view: View) {
        val messageEdit = findViewById<EditText>(R.id.boodskap)
        saveCurrentMessage(messageEdit.text.toString())
        Toast.makeText(this, "Boodskap opgedateer", Toast.LENGTH_SHORT).show()
    }

    private fun setupListView() {
        val listView = findViewById<ListView>(R.id.lidmaat_list)
        mCursorAdapter = WinkerkCursorAdapter(this, null)
        listView.adapter = mCursorAdapter
        listView.isFastScrollEnabled = true
        listView.isClickable = true

        listView.setOnItemLongClickListener { _, _, position, id ->
            val cursor = mCursorAdapter.getItem(position) as Cursor
            val values = ContentValues().apply {
                put(winkerkEntry.LIDMATE_TAG, if (cursor.getIntOrDefault(winkerkEntry.LIDMATE_TAG, 0) == 0) 1 else 0)
            }
            val memberUri = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, id)
            val rowsAffected = contentResolver.update(memberUri, values,
                "${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ?", arrayOf(id.toString()))
            if (rowsAffected == 1) updateUI()
            rowsAffected == 1
        }

        listView.setOnItemClickListener { _, view, position, id ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.lidmaatlist_menu, popup.menu)
            popup.forceShowIcons()

            val cursor = mCursorAdapter.getItem(position) as Cursor
            listItemPositionForPopupMenu = position
            listItemPositionForPopupMenu2 = id.toInt()

            setupPopupMenuItems(popup, cursor)
            popup.show()
            popup.setOnMenuItemClickListener { item ->
                handlePopupMenuClick(item, cursor)
            }
        }
    }

    private fun setupPopupMenuItems(popup: PopupMenu, cursor: Cursor) {
        val name = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)
        val surname = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN)
        val phone = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_SELFOON)
        val landline = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LANDLYN)
        val email = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_EPOS)

        popup.menu.findItem(R.id.kyk_lidmaat_detail).title = "Detail van $name $surname"
        popup.menu.findItem(R.id.submenu_bel).title = "Skakel $name"
        popup.menu.findItem(R.id.submenu_teks).title = "Teks $name"
        popup.menu.findItem(R.id.submenu_ander).title = name

        if (phone.isNotEmpty()) {
            popup.menu.findItem(R.id.bel_selfoon).title = "Skakel $phone"
            popup.menu.findItem(R.id.stuur_sms).title = "SMS na $phone"
        } else {
            popup.menu.findItem(R.id.submenu_bel).subMenu?.removeItem(R.id.bel_selfoon)
            popup.menu.findItem(R.id.submenu_teks).subMenu?.removeItem(R.id.stuur_sms)
        }

        if (landline.isNotEmpty()) {
            popup.menu.findItem(R.id.bel_landlyn).title = "Skakel $landline"
        } else {
            popup.menu.findItem(R.id.submenu_bel).subMenu?.removeItem(R.id.bel_landlyn)
        }

        if (email.isEmpty()) {
            popup.menu.findItem(R.id.submenu_teks).subMenu?.removeItem(R.id.stuur_epos)
        }

        removeWhatsAppMenuItems(popup)
    }

    private fun removeWhatsAppMenuItems(popup: PopupMenu) {
        if (!za.co.jpsoft.winkerkreader.utils.SettingsManager(this).whatsapp1) {
            popup.menu.findItem(R.id.submenu_teks).subMenu?.removeItem(R.id.stuur_whatsapp)
        }
        if (!za.co.jpsoft.winkerkreader.utils.SettingsManager(this).whatsapp2) {
            popup.menu.findItem(R.id.submenu_teks).subMenu?.removeItem(R.id.stuur_whatsapp2)
        }
        if (!za.co.jpsoft.winkerkreader.utils.SettingsManager(this).whatsapp3) {
            popup.menu.findItem(R.id.submenu_teks).subMenu?.removeItem(R.id.stuur_whatsapp3)
        }
    }

    private fun handlePopupMenuClick(item: MenuItem, cursor: Cursor): Boolean {
        return when (item.itemId) {
            R.id.stuur_whatsapp, R.id.stuur_whatsapp2, R.id.stuur_whatsapp3 -> {
                val phone = cursor.getStringOrNull(winkerkEntry.LIDMATE_SELFOON)?.let { Utils.fixphonenumber(it) }
                if (phone.isNullOrEmpty()) {
                    Toast.makeText(this, "Geen selfoonnommer beskikbaar", Toast.LENGTH_SHORT).show()
                    return false
                }
                val naam = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)
                val message = getPersonalizedMessage(naam)
                sendWhatsApp(phone, item.itemId, message)
            }
            else -> MemberActionHandler(this, cursor).handleAction(item.itemId)
        }
    }
    private fun getPersonalizedMessage(naam: String): String {
        val template = findViewById<EditText>(R.id.boodskap).text.toString()
        return template.replace("<<<naam>>>", naam)
    }
    private fun sendWhatsApp(phoneNumber: String, whatsAppType: Int, message: String): Boolean {
        return try {
            when (whatsAppType) {
                R.id.stuur_whatsapp -> sendWhatsAppMethod1(phoneNumber, message)
                R.id.stuur_whatsapp2 -> sendWhatsAppMethod2(phoneNumber, message)
                R.id.stuur_whatsapp3 -> sendWhatsAppMethod3(phoneNumber, message)
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WhatsApp: ${e.message}")
            Toast.makeText(this, "WhatsApp nie geïnstalleer nie", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun sendWhatsAppMethod1(phoneNumber: String, message: String): Boolean {
        val uri = Uri.parse("smsto: $phoneNumber")
        Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("jid", phoneNumber)
            `package` = "com.whatsapp"
            putExtra("sms_body", message)
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(this, ""))
        }
        return true
    }

    private fun sendWhatsAppMethod2(phoneNumber: String, message: String): Boolean {
        return try {
            val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
            val url = "https://api.whatsapp.com/send?phone=$phoneNumber&text=$encodedMessage"
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                `package` = "com.whatsapp"
            }.also { intent ->
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return true
                }
            }
            false
        } catch (e: java.io.UnsupportedEncodingException) {
            Log.e(TAG, "UTF-8 encoding not supported", e)
            // fallback without encoding
            val url = "https://api.whatsapp.com/send?phone=$phoneNumber&text=$message"
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                `package` = "com.whatsapp"
                startActivity(this)
            }
            true
        }
    }

    private fun sendWhatsAppMethod3(phoneNumber: String, message: String): Boolean {
        Intent("android.intent.action.MAIN").apply {
            action = Intent.ACTION_SEND
            `package` = "com.whatsapp"
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra("jid", "${phoneNumber}@s.whatsapp.net")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(this)
        }
        return true
    }
    private fun loadInitialData() {
        val checkedId = radioGroup.checkedRadioButtonId
        if (checkedId == -1) {
            findViewById<RadioButton>(R.id.Keuse_Verjaar).isChecked = true
            handleEventTypeChange(R.id.Keuse_Verjaar)
        } else {
            handleEventTypeChange(checkedId)
        }
        handleAutoSMS()
    }

    private fun handleAutoSMS() {
        val settings = getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)
        autoSms = settings.getBoolean("AUTO_SMS", false)
        val fromMenu = settings.getBoolean("FROM_MENU", false)

        if (!fromMenu && autoSms) {
            findViewById<ImageView>(R.id.verjaar_sms).performClick()
            settings.edit().putBoolean("FROM_MENU", false).apply()
            finish()
        }
    }

    private fun updateUI() {
        val checkedId = radioGroup.checkedRadioButtonId
        mCursorAdapter.swapCursor(null)

        when (checkedId) {
            R.id.Keuse_Verjaar -> {
                AppSessionState.keuse = "Verjaar"
                Log.d(TAG, "updateUI: Switching to Birthday")
                observeViewModel(viewModel.getBirthdayData(this))
            }
            R.id.Keuse_Doop -> {
                AppSessionState.keuse = "Doop"
                Log.d(TAG, "updateUI: Switching to Baptism")
                observeViewModel(viewModel.getBaptismData(this))
            }
            R.id.Keuse_Huwelik -> {
                AppSessionState.keuse = "Huwelik"
                Log.d(TAG, "updateUI: Switching to Wedding")
                observeViewModel(viewModel.getWeddingData(this))
            }
            R.id.Keuse_Belydenis -> {
                AppSessionState.keuse = "Bely"
                Log.d(TAG, "updateUI: Switching to Confession")
                observeViewModel(viewModel.getConfessionData(this))
            }
        }
    }
}