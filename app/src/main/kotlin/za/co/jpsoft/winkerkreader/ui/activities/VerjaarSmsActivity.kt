package za.co.jpsoft.winkerkreader.ui.activities

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Telephony
import android.telephony.SmsManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.services.receivers.AlarmReceiver
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.EventViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.databinding.VerjaarBinding
import za.co.jpsoft.winkerkreader.utils.MemberActionHandler
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.Utils
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.utils.forceShowIcons
import java.util.*

class VerjaarSmsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VerjaarSmsActivity"
        private const val MAX_SMS_MESSAGE_LENGTH = 160
    }

    private lateinit var binding: VerjaarBinding
    private lateinit var memberListAdapter: MemberListAdapter
    private lateinit var eventViewModel: EventViewModel
    private lateinit var memberViewModel: MemberViewModel
    private var autoSms = false
    private var keuse: String = "Verjaar"
    private val saveHandler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    // Permission launchers
    private val smsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) Toast.makeText(this, "SMS permission required to send greetings.", Toast.LENGTH_LONG).show()
    }
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

        // Also save in onPause as a fallback
    override fun onPause() {
        super.onPause()
        saveCurrentMessage()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VerjaarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Clear selected tags
                val values = ContentValues().apply { put(WinkerkContract.winkerkEntry.LIDMATE_TAG, 0) }
                contentResolver.update(WinkerkContract.winkerkEntry.CONTENT_URI, values, null, null)
                finish()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        // No cursor cleanup needed – ViewModel handles it
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ------------------------------------------------------------------------
    // Initialisation
    // ------------------------------------------------------------------------

    private fun initializeComponents() {
        requestPermissions()
        initializeSharedPreferences()
        initializeViews()
        setupRecyclerView()
        setupEventTypeSelection()
        setupMessageInput()
        setupTimeInput()
        setupButtons()
        handleAutoSMS()
    }

    private fun requestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun initializeSharedPreferences() {
        val prefs = getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)
        autoSms = prefs.getBoolean("AUTO_SMS", false)
        binding.timeHour.setText(prefs.getString("SMS-HOUR", "08"))
        binding.timeMinute.setText(prefs.getString("SMS-MINUTE", "00"))
    }

    private fun initializeViews() {
        val prefs = getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)
        binding.autosmsRadio.isChecked = autoSms
        binding.herinnerRadio.isChecked = prefs.getBoolean("HERINNER", false)

        binding.autosmsRadio.setOnClickListener {
            prefs.edit().putBoolean("AUTO_SMS", binding.autosmsRadio.isChecked).apply()
        }
        binding.herinnerRadio.setOnClickListener {
            prefs.edit().putBoolean("HERINNER", binding.herinnerRadio.isChecked).apply()
        }
    }

    private fun setupRecyclerView() {
        binding.lidmaatList.layoutManager = LinearLayoutManager(this)
        memberListAdapter = MemberListAdapter(
            onItemClick = { _, item, _ -> showPopupMenuForMember(item) },
            onItemLongClick = { item, _ ->
                toggleMemberTag(item)
                true
            }
        )
        binding.lidmaatList.adapter = memberListAdapter


        eventViewModel = ViewModelProvider(this)[EventViewModel::class.java]
        memberViewModel = ViewModelProvider(this)[MemberViewModel::class.java]

        memberListAdapter.updateState(
            listView = 2, // or settingsManager.listView
            soekList = false,
            soek = "",
            recordStatus = "0",
            sortOrder = "VERJAAR"
        )

        eventViewModel.eventList.observe(this) { members ->
            Log.d(TAG, "Observer received ${members.size} members")
            if (members.isNotEmpty()) {
                Log.d(TAG, "First member: ${members[0].name} ${members[0].surname}")
            }
            memberListAdapter.submitList(members)
        }
    }

    // ------------------------------------------------------------------------
    // Event type selection (Verjaar, Doop, Huwelik, Bely)
    // ------------------------------------------------------------------------

    private fun setupEventTypeSelection() {
        setRadioButtonSelection(binding.KeuseVerjaar, binding.KeuseDoop, binding.KeuseHuwelik, binding.KeuseBelydenis)

        binding.keuse.setOnCheckedChangeListener { _, checkedId ->
            handleEventTypeChange(checkedId)
        }
        // Load initial data
        handleEventTypeChange(binding.keuse.checkedRadioButtonId)
    }

    private fun setRadioButtonSelection(birthday: RadioButton, baptism: RadioButton, marriage: RadioButton, confession: RadioButton) {
        listOf(birthday, baptism, marriage, confession).forEach { it.isChecked = false }
        when (keuse) {
            "Verjaar" -> birthday.isChecked = true
            "Doop"    -> baptism.isChecked = true
            "Huwelik" -> marriage.isChecked = true
            "Bely"    -> confession.isChecked = true
            else      -> {
                birthday.isChecked = true
                keuse = "Verjaar"
            }
        }
    }

    private fun handleEventTypeChange(checkedId: Int) {
        val prefs = getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)

        when (checkedId) {
            R.id.Keuse_Verjaar -> {
                keuse = "Verjaar"
                setMessageForEventType(prefs, "VerjaarBoodskap",
                    "<<<naam>>>\nBaie geluk met jou verjaarsdag!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ")
                binding.verjaarSms.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.bdaysms, null))
                eventViewModel.loadEventData(this, "Verjaar")
            }
            R.id.Keuse_Doop -> {
                keuse = "Doop"
                setMessageForEventType(prefs, "DoopBoodskap",
                    "<<<naam>>>\nBaie geluk met jou doopherdenking!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ")
                binding.verjaarSms.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.doopsms, null))
                eventViewModel.loadEventData(this, "Doop")
            }
            R.id.Keuse_Huwelik -> {
                keuse = "Huwelik"
                setMessageForEventType(prefs, "HuwelikBoodskap",
                    "<<<naam>>>\nBaie geluk met jou huweliksherdenking!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ")
                binding.verjaarSms.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.huweliksms, null))
                eventViewModel.loadEventData(this, "Huwelik")
            }
            R.id.Keuse_Belydenis -> {
                keuse = "Bely"
                setMessageForEventType(prefs, "BelyBoodskap",
                    "<<<naam>>>\nBaie geluk met jou herdenking van jou belydenis van geloof!\nMag die Here se genade jou daagliks vervul!\nGroete Ds ")
                binding.verjaarSms.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.bely, null))
                eventViewModel.loadEventData(this, "Bely")
            }
        }
    }

    private fun setMessageForEventType(prefs: SharedPreferences, key: String, default: String) {
        binding.boodskap.setText(prefs.getString(key, default))
    }

    private fun saveCurrentMessage() {
        val key = when (keuse) {
            "Doop"    -> "DoopBoodskap"
            "Huwelik" -> "HuwelikBoodskap"
            "Bely"    -> "BelyBoodskap"
            else      -> "VerjaarBoodskap"
        }
        getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)
            .edit().putString(key, binding.boodskap.text.toString()).apply()
    }

    // ------------------------------------------------------------------------
    // Message input & time setup
    // ------------------------------------------------------------------------

    private fun setupMessageInput() {
        binding.boodskap.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Update character count
                val remaining = MAX_SMS_MESSAGE_LENGTH - (s?.length ?: 0)
                binding.charCount.text = remaining.toString()

                // Debounce save
                saveRunnable?.let { saveHandler.removeCallbacks(it) }
                saveRunnable = Runnable { saveCurrentMessage() }
                saveHandler.postDelayed(saveRunnable!!, 500)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupTimeInput() {
        binding.timeHour.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.timeHour.length() < 2) binding.timeHour.setText("0${binding.timeHour.text}")
        }
        binding.timeMinute.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.timeMinute.length() < 2) binding.timeMinute.setText("0${binding.timeMinute.text}")
        }
    }

    private fun setupButtons() {
        binding.setTime.setOnClickListener(::handleSetTimeClick)
        binding.verjaarSms.setOnClickListener { sendSmsToSelectedMembers() }
        binding.opdateerBoodskap.setOnClickListener {
            saveCurrentMessage()
            Toast.makeText(this, "Boodskap opgedateer", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSetTimeClick(view: View) {
        val hour = formatTimeUnit(binding.timeHour.text.toString())
        val minute = formatTimeUnit(binding.timeMinute.text.toString())
        saveTimeSettings(hour, minute)
        setupAlarm(hour, minute)
        navigateToMainActivity()
    }

    private fun formatTimeUnit(unit: String) = if (unit.length < 2) "0$unit" else unit

    private fun saveTimeSettings(hour: String, minute: String) {
        getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)
            .edit()
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
        val triggerTime = if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.timeInMillis + AlarmManager.INTERVAL_DAY
        } else {
            calendar.timeInMillis
        }
        val alarmIntent = Intent(this, AlarmReceiver::class.java).apply { action = "VerjaarSMS" }
        val pendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).setRepeating(
            AlarmManager.RTC_WAKEUP, triggerTime, AlarmManager.INTERVAL_DAY, pendingIntent)
    }

    private fun navigateToMainActivity() {
        SettingsManager.getInstance(this).defLayout = "VERJAAR"
        Intent(this, MainActivity::class.java).apply {
            putExtra("SENDER_CLASS_NAME", "WysVerjaar")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(this)
        }
        finish()
    }

    // ------------------------------------------------------------------------
    // SMS sending (unchanged logic, adapted to MemberItem list)
    // ------------------------------------------------------------------------

    private suspend fun sendSmsToMemberSuspend(
        member: MemberItem,
        template: String,
        smsManager: SmsManager
    ): Boolean = withContext(Dispatchers.IO) {
        val phone = fixphonenumber(member.cellphone)
        if (phone.isNullOrEmpty()) return@withContext false

        val personalized = template.replace("<<<naam>>>", member.name)
        return@withContext try {
            val parts = smsManager.divideMessage(personalized)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            logSentMessage(phone, personalized)
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed: ${e.message}")
            false
        }
    }

    private fun sendSmsToSelectedMembers() {
        saveCurrentMessage()
        val messageTemplate = binding.boodskap.text.toString()
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: run {
            Toast.makeText(this, "SMS manager not available", Toast.LENGTH_SHORT).show()
            return
        }

        val members = memberListAdapter.currentList
        if (members.isEmpty()) {
            Toast.makeText(this, "Geen lede gevind nie", Toast.LENGTH_SHORT).show()
            return
        }

        // Show a progress dialog (optional)
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Besig om SMS'e te stuur...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var sentCount = 0
            for (member in members) {
                if (shouldSendSmsToMember(member)) {
                    val success = sendSmsToMemberSuspend(member, messageTemplate, smsManager)
                    if (success) sentCount++
                    // Delay 1 second without blocking the thread
                    delay(1000)
                }
            }
            // Switch back to main thread to update UI
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                Toast.makeText(this@VerjaarSmsActivity,
                    "$sentCount SMS'e is gestuur!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shouldSendSmsToMember(member: MemberItem) = member.tag == 1 || autoSms

    private fun sendSmsToMember(member: MemberItem, template: String, smsManager: SmsManager): Boolean {
        val phone = fixphonenumber(member.cellphone)
        if (phone.isNullOrEmpty()) return false

        val personalized = template.replace("<<<naam>>>", member.name)
        return try {
            val parts = smsManager.divideMessage(personalized)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            logSentMessage(phone, personalized)
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed: ${e.message}")
            false
        }
    }

    private fun logSentMessage(phone: String, message: String) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, phone)
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

    // ------------------------------------------------------------------------
    // Member selection (tagging) & popup menu
    // ------------------------------------------------------------------------

    private fun toggleMemberTag(member: MemberItem) {
        val newTag = if (member.tag == 1) 0 else 1
        val values = ContentValues().apply { put(WinkerkContract.winkerkEntry.LIDMATE_TAG, newTag) }
        val selection = "${WinkerkContract.winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ?"
        val selectionArgs = arrayOf(member.id.toString())
        contentResolver.update(
            WinkerkContract.winkerkEntry.CONTENT_URI,  // base URI, not the appended one
            values,
            selection,
            selectionArgs
        )
        // Reload data to refresh the list
        eventViewModel.loadEventData(this, keuse)
    }

    private fun showPopupMenuForMember(member: MemberItem) {
        val anchor = binding.lidmaatList
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.lidmaatlist_menu, popup.menu)
        popup.forceShowIcons()

        val name = member.name
        val surname = member.surname
        val phone = member.cellphone
        val landline = member.landline
        val email = member.email

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

        val settings = SettingsManager.getInstance(this)
        if (!settings.whatsapp1) popup.menu.findItem(R.id.submenu_teks).subMenu?.removeItem(R.id.stuur_whatsapp)
        if (!settings.whatsapp2) popup.menu.findItem(R.id.submenu_teks).subMenu?.removeItem(R.id.stuur_whatsapp2)
        if (!settings.whatsapp3) popup.menu.findItem(R.id.submenu_teks).subMenu?.removeItem(R.id.stuur_whatsapp3)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.stuur_whatsapp, R.id.stuur_whatsapp2, R.id.stuur_whatsapp3 -> {
                    val phone = fixphonenumber(member.cellphone)
                    if (phone.isNullOrEmpty()) {
                        Toast.makeText(this, "Geen selfoonnommer beskikbaar", Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener false
                    }
                    val msg = binding.boodskap.text.toString().replace("<<<naam>>>", member.name)
                    sendWhatsApp(phone, item.itemId, msg)
                    true
                }
                else -> MemberActionHandler(this, member, memberViewModel).handleAction(item.itemId)
            }
        }
        popup.show()
    }

    private fun sendWhatsApp(phone: String, type: Int, message: String): Boolean {
        return try {
            when (type) {
                R.id.stuur_whatsapp  -> sendWhatsAppMethod1(phone, message)
                R.id.stuur_whatsapp2 -> sendWhatsAppMethod2(phone, message)
                R.id.stuur_whatsapp3 -> sendWhatsAppMethod3(phone, message)
                else -> false
            }
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp nie geïnstalleer nie", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun sendWhatsAppMethod1(phone: String, message: String): Boolean {
        val uri = Uri.parse("smsto: $phone")
        Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("jid", phone)
            `package` = "com.whatsapp"
            putExtra("sms_body", message)
            putExtra(Intent.EXTRA_TEXT, message)
            startActivity(Intent.createChooser(this, ""))
        }
        return true
    }

    private fun sendWhatsAppMethod2(phone: String, message: String): Boolean {
        val encoded = java.net.URLEncoder.encode(message, "UTF-8")
        val url = "https://api.whatsapp.com/send?phone=$phone&text=$encoded"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { `package` = "com.whatsapp" }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            return true
        }
        return false
    }

    private fun sendWhatsAppMethod3(phone: String, message: String): Boolean {
        Intent(Intent.ACTION_SEND).apply {
            `package` = "com.whatsapp"
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra("jid", "${phone}@s.whatsapp.net")
            startActivity(this)
        }
        return true
    }

    private fun handleAutoSMS() {
        val prefs = getSharedPreferences(WinkerkContract.PREFS_USER_INFO, Context.MODE_PRIVATE)
        autoSms = prefs.getBoolean("AUTO_SMS", false)
        val fromMenu = prefs.getBoolean("FROM_MENU", false)
        if (!fromMenu && autoSms) {
            binding.verjaarSms.performClick()
            prefs.edit().putBoolean("FROM_MENU", false).apply()
            finish()
        }
    }
}