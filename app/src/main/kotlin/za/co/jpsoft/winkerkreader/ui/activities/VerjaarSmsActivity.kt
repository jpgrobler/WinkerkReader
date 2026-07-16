package za.co.jpsoft.winkerkreader.ui.activities

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.databinding.VerjaarBinding
import za.co.jpsoft.winkerkreader.services.receivers.AlarmReceiver
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.EventViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.MainNavigationController
import za.co.jpsoft.winkerkreader.utils.MemberActionHandler
import za.co.jpsoft.winkerkreader.utils.MessageComposer
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.utils.forceShowIcons
import java.util.Calendar
import java.util.Locale

class VerjaarSmsActivity : BaseActivity() {

    companion object {
        private const val TAG = "VerjaarSmsActivity"
        private const val MAX_SMS_MESSAGE_LENGTH = 160
        private const val AUTO_SAVE_DELAY_MS = 500L
    }

    private lateinit var binding: VerjaarBinding
    private lateinit var memberListAdapter: MemberListAdapter
    private lateinit var eventViewModel: EventViewModel
    private lateinit var memberViewModel: MemberViewModel
    private val navigationController by lazy { MainNavigationController(this) }

    private var autoSms = false
    private var keuse: String = "Verjaar"
    private val saveHandler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    private var selectedHour = 8
    private var selectedMinute = 0

    // Permission launchers
    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Snackbar.make(
                binding.root,
                "SMS permission required to send greetings",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VerjaarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()

        // Back pressed handler with confirmation if tagged members exist
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })

        // Observe loading state
        eventViewModel.isLoading.observe(this) { loading ->
            binding.verjaarProgress.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentMessage()
    }

    override fun onDestroy() {
        saveRunnable?.let { saveHandler.removeCallbacks(it) }
        saveRunnable = null
        super.onDestroy()
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
        setupViews()
        setupRecyclerView()
        setupEventTypeSelection()
        setupMessageInput()
        setupTimePicker()
        setupButtons()
        handleAutoSMS()
    }

    private fun requestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun initializeSharedPreferences() {
        val prefs = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)
        autoSms = prefs.getBoolean("AUTO_SMS", false)

        // Load saved time
        val hourStr = prefs.getString("SMS-HOUR", "08") ?: "08"
        val minuteStr = prefs.getString("SMS-MINUTE", "00") ?: "00"
        selectedHour = hourStr.toIntOrNull() ?: 8
        selectedMinute = minuteStr.toIntOrNull() ?: 0

        updateTimeDisplay()
    }

    private fun setupViews() {
        val prefs = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)
        binding.autosmsRadio.isChecked = autoSms
        binding.herinnerRadio.isChecked = prefs.getBoolean("HERINNER", false)

        binding.autosmsRadio.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("AUTO_SMS", isChecked) }
        }

        binding.herinnerRadio.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("HERINNER", isChecked) }
        }

        // Click listener for SMS icon
        binding.verjaarSms.setOnClickListener {
            sendSmsToSelectedMembers()
        }
    }

    private fun setupRecyclerView() {
        binding.lidmaatList.layoutManager = LinearLayoutManager(this)
        memberListAdapter = MemberListAdapter(
            onItemClick = { _, item, _ ->
                showPopupMenuForMember(item)
            },
            onItemLongClick = { item, _ ->
                toggleMemberTag(item)
                true
            }
        )
        binding.lidmaatList.adapter = memberListAdapter

        eventViewModel = ViewModelProvider(this)[EventViewModel::class.java]

        // ✅ FIX: Use the correct factory for MemberViewModel
        val settingsManager = SettingsManager.getInstance(this)
        val initialCongregations = listOfNotNull(
            settingsManager.gemeenteNaam.takeIf { it.isNotBlank() },
            settingsManager.gemeente2Naam.takeIf { it.isNotBlank() },
            settingsManager.gemeente3Naam.takeIf { it.isNotBlank() }
        ).toSet()

        val savedStateHandle = SavedStateHandle()
        memberViewModel = ViewModelProvider(
            this,
            MemberViewModel.MemberViewModelFactory(
                application,
                savedStateHandle,
                initialCongregations
            )
        ).get(MemberViewModel::class.java)

        memberListAdapter.updateState(
            listView = 2,
            soekList = false,
            soek = "",
            recordStatus = "0",
            sortOrder = "VERJAAR"
        )

        // ✅ Observe data - submit to adapter
        eventViewModel.eventList.observe(this) { members ->
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Observer received ${members.size} members for $keuse")
            }
            if (members.isNotEmpty()) {
                lifecycleScope.launch {
                    val pagingData = PagingData.from(members)
                    memberListAdapter.submitData(lifecycle, pagingData)
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Event Type Selection (Using Material Chips)
    // ------------------------------------------------------------------------

    private fun setupEventTypeSelection() {
        // Set initial selection based on keuse
        val chipId = when (keuse) {
            "Verjaar" -> R.id.Keuse_Verjaar
            "Doop" -> R.id.Keuse_Doop
            "Huwelik" -> R.id.Keuse_Huwelik
            "Bely" -> R.id.Keuse_Belydenis
            else -> R.id.Keuse_Verjaar
        }
        binding.keuse.check(chipId)

        // Listen for chip selection changes
        binding.keuse.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val checkedId = checkedIds[0]
                handleEventTypeChange(checkedId)
            }
        }

        // Load initial data
        val initialChipId = binding.keuse.checkedChipId
        if (initialChipId != -1) {
            handleEventTypeChange(initialChipId)
        } else {
            // Default to Verjaar
            binding.KeuseVerjaar.isChecked = true
            handleEventTypeChange(R.id.Keuse_Verjaar)
        }
    }

    private fun handleEventTypeChange(checkedId: Int) {
        val prefs = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)

        // ✅ STEP 1: Clear the list IMMEDIATELY to prevent old data showing
        lifecycleScope.launch {
            memberListAdapter.submitData(lifecycle, PagingData.empty())
        }

        // ✅ STEP 2: Update the message and icon
        when (checkedId) {
            R.id.Keuse_Verjaar -> {
                keuse = "Verjaar"
                setMessageForEventType(
                    prefs, "VerjaarBoodskap",
                    "<<<naam>>>\nBaie geluk met jou verjaarsdag!\nMag die Here se genade jou daagliks vervul!\nGroete Ds "
                )
                binding.verjaarSms.setImageResource(R.drawable.bdaysms)
                binding.verjaarSms.contentDescription = getString(R.string.verjaar_send_birthday)
            }

            R.id.Keuse_Doop -> {
                keuse = "Doop"
                setMessageForEventType(
                    prefs, "DoopBoodskap",
                    "<<<naam>>>\nBaie geluk met jou doopherdenking!\nMag die Here se genade jou daagliks vervul!\nGroete Ds "
                )
                binding.verjaarSms.setImageResource(R.drawable.doopsms)
                binding.verjaarSms.contentDescription = getString(R.string.verjaar_send_baptism)
            }

            R.id.Keuse_Huwelik -> {
                keuse = "Huwelik"
                setMessageForEventType(
                    prefs, "HuwelikBoodskap",
                    "<<<naam>>>\nBaie geluk met jou huweliksherdenking!\nMag die Here se genade jou daagliks vervul!\nGroete Ds "
                )
                binding.verjaarSms.setImageResource(R.drawable.huweliksms)
                binding.verjaarSms.contentDescription = getString(R.string.verjaar_send_wedding)
            }

            R.id.Keuse_Belydenis -> {
                keuse = "Bely"
                setMessageForEventType(
                    prefs, "BelyBoodskap",
                    "<<<naam>>>\nBaie geluk met jou herdenking van jou belydenis van geloof!\nMag die Here se genade jou daagliks vervul!\nGroete Ds "
                )
                binding.verjaarSms.setImageResource(R.drawable.bely)
                binding.verjaarSms.contentDescription = getString(R.string.verjaar_send_confession)
            }
        }

        // ✅ STEP 3: Update chip visual state
        updateChipSelection(checkedId)

        // ✅ STEP 4: Load the new data for the selected event type
        // The observer will update the list when data arrives
        eventViewModel.loadEventData(keuse)
    }

    private fun updateChipSelection(selectedChipId: Int) {
        for (i in 0 until binding.keuse.childCount) {
            val chip = binding.keuse.getChildAt(i) as? com.google.android.material.chip.Chip
            chip?.isChecked = chip?.id == selectedChipId
        }
    }

    private fun setMessageForEventType(prefs: SharedPreferences, key: String, default: String) {
        binding.boodskap.setText(prefs.getString(key, default))
    }

    private fun saveCurrentMessage() {
        val key = when (keuse) {
            "Doop" -> "DoopBoodskap"
            "Huwelik" -> "HuwelikBoodskap"
            "Bely" -> "BelyBoodskap"
            else -> "VerjaarBoodskap"
        }
        getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)
            .edit { putString(key, binding.boodskap.text.toString()) }
    }

    // ------------------------------------------------------------------------
    // Message Input
    // ------------------------------------------------------------------------

    private fun setupMessageInput() {
        binding.boodskap.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val remaining = MAX_SMS_MESSAGE_LENGTH - (s?.length ?: 0)
                //binding.charCount.text = getString(R.string.verjaar_char_count, remaining)

                // Debounce save
                saveRunnable?.let { saveHandler.removeCallbacks(it) }
                saveRunnable = Runnable { saveCurrentMessage() }
                saveHandler.postDelayed(saveRunnable!!, AUTO_SAVE_DELAY_MS)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ------------------------------------------------------------------------
    // Time Picker
    // ------------------------------------------------------------------------

    private fun setupTimePicker() {
        binding.timePickerButton.setOnClickListener {
            showTimePicker()
        }

        // Also allow clicking on time display to open picker
        binding.timeDisplay.setOnClickListener {
            showTimePicker()
        }
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(selectedHour)
            .setMinute(selectedMinute)
            .setTitleText(R.string.verjaar_select_time)
            .build()

        picker.addOnPositiveButtonClickListener {
            selectedHour = picker.hour
            selectedMinute = picker.minute
            updateTimeDisplay()
            saveTimeSettings()
            setupAlarm()
            Snackbar.make(binding.root, R.string.verjaar_time_updated, Snackbar.LENGTH_SHORT).show()
        }

        picker.show(supportFragmentManager, "time_picker")
    }

    private fun updateTimeDisplay() {
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
        binding.timeDisplay.text = timeStr
    }

    private fun saveTimeSettings() {
        val hourStr = String.format(Locale.getDefault(), "%02d", selectedHour)
        val minuteStr = String.format(Locale.getDefault(), "%02d", selectedMinute)

        getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)
            .edit {
                putString("SMS-HOUR", hourStr)
                putString("SMS-MINUTE", minuteStr)
                putBoolean("SMS-TIMEUPDATE", true)
                putBoolean("FROM_MENU", false)
            }
    }

    private fun setupAlarm() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)
        }
        val triggerTime = if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.timeInMillis + AlarmManager.INTERVAL_DAY
        } else {
            calendar.timeInMillis
        }
        val alarmIntent = Intent(this, AlarmReceiver::class.java).apply { action = "VerjaarSMS" }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, alarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        (getSystemService(ALARM_SERVICE) as AlarmManager).setRepeating(
            AlarmManager.RTC_WAKEUP, triggerTime, AlarmManager.INTERVAL_DAY, pendingIntent
        )
    }

    private fun setupButtons() {
        binding.opdateerBoodskap.setOnClickListener {
            saveCurrentMessage()
            Snackbar.make(binding.root, R.string.verjaar_message_updated, Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    // ------------------------------------------------------------------------
    // SMS Sending
    // ------------------------------------------------------------------------

    private suspend fun sendSmsToMemberSuspend(
        member: MemberItem,
        template: String,
        smsManager: SmsManager
    ): Boolean = withContext(Dispatchers.IO) {
        val phone = fixphonenumber(member.cellphone)
        if (phone.isNullOrEmpty()) return@withContext false

        val personalized = MessageComposer.personalize(template, member)
        return@withContext try {
            val parts = smsManager.divideMessage(personalized)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            logSentMessage(phone, personalized)
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "SMS failed: ${e.message}")
            false
        }
    }

    private fun sendSmsToSelectedMembers() {
        saveCurrentMessage()
        val messageTemplate = binding.boodskap.text.toString()
        val smsManager = getSystemService(SmsManager::class.java) ?: run {
            Snackbar.make(
                binding.root,
                R.string.verjaar_sms_manager_unavailable,
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val members = memberListAdapter.getCurrentItems()
        if (members.isEmpty()) {
            Snackbar.make(binding.root, R.string.verjaar_no_members, Snackbar.LENGTH_SHORT).show()
            return
        }

        // Show progress dialog
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.verjaar_sending_sms))
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            var sentCount = 0
            for (member in members) {
                if (shouldSendSmsToMember(member)) {
                    val success = sendSmsToMemberSuspend(member, messageTemplate, smsManager)
                    if (success) sentCount++
                    delay(1000)
                }
            }
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                Snackbar.make(
                    binding.root,
                    getString(R.string.verjaar_sms_sent, sentCount),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun shouldSendSmsToMember(member: MemberItem) = member.tag == 1 || autoSms

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
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to log SMS: ${e.message}")
        }
    }

    // ------------------------------------------------------------------------
    // Member Selection (Tagging)
    // ------------------------------------------------------------------------

    private fun toggleMemberTag(member: MemberItem) {
        val newTag = if (member.tag == 1) 0 else 1
        val values = ContentValues().apply { put(WinkerkContract.winkerkEntry.LIDMATE_TAG, newTag) }
        val selection = "${WinkerkContract.winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ?"
        val selectionArgs = arrayOf(member.id.toString())
        contentResolver.update(
            WinkerkContract.winkerkEntry.CONTENT_URI,
            values,
            selection,
            selectionArgs
        )
        // Show feedback
        val message = if (newTag == 1) {
            getString(R.string.verjaar_member_selected, member.name)
        } else {
            getString(R.string.verjaar_member_deselected, member.name)
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()

        // ✅ Refresh the list to show updated tag state
        eventViewModel.loadEventData(keuse)
    }

    // ------------------------------------------------------------------------
    // Popup Menu
    // ------------------------------------------------------------------------

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

        popup.menu.findItem(R.id.kyk_lidmaat_detail).title =
            getString(R.string.verjaar_detail, name, surname)
        popup.menu.findItem(R.id.submenu_bel).title = getString(R.string.verjaar_call, name)
        popup.menu.findItem(R.id.submenu_teks).title = getString(R.string.verjaar_text, name)
        popup.menu.findItem(R.id.submenu_ander).title = name

        if (phone.isNotEmpty()) {
            popup.menu.findItem(R.id.bel_selfoon).title =
                getString(R.string.verjaar_call_phone, phone)
            popup.menu.findItem(R.id.stuur_sms).title = getString(R.string.verjaar_sms_phone, phone)
        } else {
            popup.menu.findItem(R.id.submenu_bel).subMenu?.removeItem(R.id.bel_selfoon)
            popup.menu.findItem(R.id.submenu_teks).subMenu?.removeItem(R.id.stuur_sms)
        }
        if (landline.isNotEmpty()) {
            popup.menu.findItem(R.id.bel_landlyn).title =
                getString(R.string.verjaar_call_landline, landline)
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
                        Snackbar.make(
                            binding.root,
                            R.string.verjaar_no_phone,
                            Snackbar.LENGTH_SHORT
                        ).show()
                        return@setOnMenuItemClickListener false
                    }
                    val msg = MessageComposer.personalize(binding.boodskap.text.toString(), member)
                    sendWhatsApp(phone, item.itemId, msg)
                    true
                }

                else -> MemberActionHandler(this, member, memberViewModel).handleAction(item.itemId)
            }
        }
        popup.show()
    }

    // ------------------------------------------------------------------------
    // WhatsApp Methods
    // ------------------------------------------------------------------------

    private fun sendWhatsApp(phone: String, type: Int, message: String): Boolean {
        return try {
            when (type) {
                R.id.stuur_whatsapp -> sendWhatsAppMethod1(phone, message)
                R.id.stuur_whatsapp2 -> sendWhatsAppMethod2(phone, message)
                R.id.stuur_whatsapp3 -> sendWhatsAppMethod3(phone, message)
                else -> false
            }
        } catch (_: Exception) {
            Snackbar.make(
                binding.root,
                R.string.verjaar_whatsapp_not_installed,
                Snackbar.LENGTH_SHORT
            ).show()
            false
        }
    }

    private fun sendWhatsAppMethod1(phone: String, message: String): Boolean {
        val uri = "smsto: $phone".toUri()
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
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply { `package` = "com.whatsapp" }
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

    // ------------------------------------------------------------------------
    // Auto SMS & Back Press
    // ------------------------------------------------------------------------

    private fun handleAutoSMS() {
        val prefs = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)
        autoSms = prefs.getBoolean("AUTO_SMS", false)
        val fromMenu = prefs.getBoolean("FROM_MENU", false)
        if (!fromMenu && autoSms) {
            binding.verjaarSms.performClick()
            prefs.edit { putBoolean("FROM_MENU", false) }
            finish()
        }
    }

    private fun handleBackPress() {
        // Check if any members are tagged
        val members = memberListAdapter.getCurrentItems()
        val hasTaggedMembers = members.any { it.tag == 1 }

        if (hasTaggedMembers) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.verjaar_confirm_exit_title)
                .setMessage(R.string.verjaar_confirm_exit_message)
                .setPositiveButton(R.string.verjaar_confirm_exit_yes) { _, _ ->
                    clearTagsAndFinish()
                }
                .setNegativeButton(R.string.verjaar_confirm_exit_no, null)
                .show()
        } else {
            clearTagsAndFinish()
        }
    }

    private fun clearTagsAndFinish() {
        val values = ContentValues().apply { put(WinkerkContract.winkerkEntry.LIDMATE_TAG, 0) }
        contentResolver.update(WinkerkContract.winkerkEntry.CONTENT_URI, values, null, null)
        finish()
    }
}