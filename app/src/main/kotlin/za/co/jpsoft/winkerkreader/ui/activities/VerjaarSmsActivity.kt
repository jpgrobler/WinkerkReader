package za.co.jpsoft.winkerkreader.ui.activities

import android.Manifest
import android.content.ContentValues
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.databinding.VerjaarBinding
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter
import za.co.jpsoft.winkerkreader.ui.helpers.QuickActionHelper
import za.co.jpsoft.winkerkreader.ui.viewmodels.EventViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.*
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.QuickActionPrefs
import java.util.Locale

@AndroidEntryPoint
class VerjaarSmsActivity : AuthBaseActivity() {

    companion object {
        private const val TAG = "VerjaarSmsActivity"
        private const val AUTO_SAVE_DELAY_MS = 500L
    }

    // ─── Injected Preferences ──────────────────────────────────────────────────
    @Inject
    lateinit var memberListPrefs: MemberListPrefs
    @Inject
    lateinit var congregationPrefs: CongregationPrefs
    @Inject
    lateinit var appearancePrefs: AppearancePrefs
    @Inject
    lateinit var quickActionPrefs: QuickActionPrefs

    private lateinit var binding: VerjaarBinding
    private lateinit var memberListAdapter: MemberListAdapter
    private lateinit var eventViewModel: EventViewModel
    private lateinit var memberViewModel: MemberViewModel
    private lateinit var quickActionHelper: QuickActionHelper
    private lateinit var smsSender: BirthdaySmsSender
    private lateinit var prefs: SharedPreferences
    private var autoSms = false
    private var keuse: String = "Verjaar"
    private val saveHandler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    private var selectedHour = 8
    private var selectedMinute = 0
    private var isSending = false  // prevent multiple concurrent sends

    // Permission launchers
    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Snackbar.make(binding.root, "SMS permission required", Snackbar.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VerjaarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.lidmaatList) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }

        smsSender = BirthdaySmsSender(contentResolver)
        prefs = getSharedPreferences("VerjaarSmsPrefs", MODE_PRIVATE)

        initializeComponents()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })

        // Observe loading state for member list (keeps progress bar in sync)
        eventViewModel.isLoading.observe(this) { loading ->
            if (!isSending) {
                binding.verjaarProgress.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentMessage()
    }

    override fun onDestroy() {
        quickActionHelper.dismiss()
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

        binding.verjaarSms.setOnClickListener {
            sendSmsToSelectedMembers()
        }
    }

    private fun setupRecyclerView() {
        binding.lidmaatList.layoutManager = LinearLayoutManager(this)

        // ─── QuickActionHelper now receives quickActionPrefs instead of SettingsManager ───
        quickActionHelper = QuickActionHelper(this, quickActionPrefs, appearancePrefs)
        quickActionHelper.expandCallback = { _, item -> showPopupMenuForMember(item) }

        memberListAdapter = MemberListAdapter(
            memberListPrefs = memberListPrefs,
            congregationPrefs = congregationPrefs,
            onItemClick = { view, item, _ ->
                val template = binding.boodskap.text.toString()
                val personalizedMessage = MessageComposer.personalize(template, item)
                quickActionHelper.showQuickActions(view, item, personalizedMessage)
            },
            onItemLongClick = { item, _ ->
                toggleMemberTag(item)
                true
            }
        )
        binding.lidmaatList.adapter = memberListAdapter

        eventViewModel = ViewModelProvider(this)[EventViewModel::class.java]

        val initialCongregations = listOfNotNull(
            congregationPrefs.gemeenteNaam.takeIf { it.isNotBlank() },
            congregationPrefs.gemeente2Naam.takeIf { it.isNotBlank() },
            congregationPrefs.gemeente3Naam.takeIf { it.isNotBlank() }
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
            sortOrder = "VERJAAR",
            useCongregationIndicator = congregationPrefs.useCongregationIndicator
        )

        eventViewModel.eventList.observe(this) { members ->
            if (members.isNotEmpty()) {
                lifecycleScope.launch {
                    memberListAdapter.submitData(lifecycle, PagingData.from(members))
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Event Type Selection
    // ------------------------------------------------------------------------

    private fun setupEventTypeSelection() {
        val chipId = when (keuse) {
            "Verjaar" -> R.id.Keuse_Verjaar
            "Doop" -> R.id.Keuse_Doop
            "Huwelik" -> R.id.Keuse_Huwelik
            "Bely" -> R.id.Keuse_Belydenis
            else -> R.id.Keuse_Verjaar
        }
        binding.keuse.check(chipId)

        binding.keuse.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                handleEventTypeChange(checkedIds[0])
            }
        }

        val initialChipId = binding.keuse.checkedChipId
        if (initialChipId != -1) {
            handleEventTypeChange(initialChipId)
        } else {
            binding.KeuseVerjaar.isChecked = true
            handleEventTypeChange(R.id.Keuse_Verjaar)
        }
    }

    private fun handleEventTypeChange(checkedId: Int) {
        val prefs = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)

        lifecycleScope.launch {
            memberListAdapter.submitData(lifecycle, PagingData.empty())
        }

        when (checkedId) {
            R.id.Keuse_Verjaar -> {
                keuse = "Verjaar"
                binding.boodskap.setText(EventMessageStore.load(prefs, keuse))
                binding.verjaarSms.setImageResource(R.drawable.bdaysms)
                binding.verjaarSms.contentDescription = getString(R.string.verjaar_send_birthday)
            }
            R.id.Keuse_Doop -> {
                keuse = "Doop"
                binding.boodskap.setText(EventMessageStore.load(prefs, keuse))
                binding.verjaarSms.setImageResource(R.drawable.doopsms)
                binding.verjaarSms.contentDescription = getString(R.string.verjaar_send_baptism)
            }
            R.id.Keuse_Huwelik -> {
                keuse = "Huwelik"
                binding.boodskap.setText(EventMessageStore.load(prefs, keuse))
                binding.verjaarSms.setImageResource(R.drawable.huweliksms)
                binding.verjaarSms.contentDescription = getString(R.string.verjaar_send_wedding)
            }
            R.id.Keuse_Belydenis -> {
                keuse = "Bely"
                binding.boodskap.setText(EventMessageStore.load(prefs, keuse))
                binding.verjaarSms.setImageResource(R.drawable.bely)
                binding.verjaarSms.contentDescription = getString(R.string.verjaar_send_confession)
            }
        }

        updateChipSelection(checkedId)
        eventViewModel.loadEventData(keuse)
    }

    private fun updateChipSelection(selectedChipId: Int) {
        for (i in 0 until binding.keuse.childCount) {
            val chip = binding.keuse.getChildAt(i) as? com.google.android.material.chip.Chip
            chip?.isChecked = chip?.id == selectedChipId
        }
    }

    private fun saveCurrentMessage() {
        EventMessageStore.save(prefs, keuse, binding.boodskap.text.toString())
    }

    // ------------------------------------------------------------------------
    // Message Input
    // ------------------------------------------------------------------------

    private fun setupMessageInput() {
        binding.boodskap.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
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
        binding.timePickerButton.setOnClickListener { showTimePicker() }
        binding.timeDisplay.setOnClickListener { showTimePicker() }
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
            BirthdayAlarmScheduler.schedule(this, selectedHour, selectedMinute)
            Snackbar.make(binding.root, R.string.verjaar_time_updated, Snackbar.LENGTH_SHORT).show()
        }

        picker.show(supportFragmentManager, "time_picker")
    }

    private fun updateTimeDisplay() {
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
        binding.timeDisplay.text = timeStr
    }

    private fun saveTimeSettings() {
        BirthdayAlarmScheduler.schedule(this, selectedHour, selectedMinute)
        getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE).edit {
            putString("SMS-HOUR", String.format(Locale.getDefault(), "%02d", selectedHour))
            putString("SMS-MINUTE", String.format(Locale.getDefault(), "%02d", selectedMinute))
            putBoolean("SMS-TIMEUPDATE", true)
            putBoolean("FROM_MENU", false)
        }
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

    private fun sendSmsToSelectedMembers() {
        if (isSending) return

        val template = binding.boodskap.text.toString()
        if (template.isBlank()) {
            Snackbar.make(binding.root, "Voer asseblief 'n boodskap in", Snackbar.LENGTH_SHORT)
                .show()
            return
        }

        val smsManager = getSystemService(SmsManager::class.java) ?: run {
            Snackbar.make(binding.root, "SMS nie beskikbaar nie", Snackbar.LENGTH_SHORT).show()
            return
        }

        val members = memberListAdapter.getCurrentItems()
        if (members.isEmpty()) {
            Snackbar.make(binding.root, "Geen lede om te stuur nie", Snackbar.LENGTH_SHORT).show()
            return
        }

        isSending = true
        binding.verjaarProgress.visibility = View.VISIBLE
        binding.verjaarSms.isEnabled = false

        lifecycleScope.launch {
            val count = smsSender.sendToMembers(
                members = members,
                template = template,
                smsManager = smsManager,
                shouldSend = { member -> member.tag == 1 || autoSms }
            )
            withContext(Dispatchers.Main) {
                isSending = false
                binding.verjaarProgress.visibility = View.GONE
                binding.verjaarSms.isEnabled = true
                val message = if (count == 0) "Geen SMS'e gestuur" else "$count SMS'e gestuur"
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    // ------------------------------------------------------------------------
    // Member Selection (Tagging)
    // ------------------------------------------------------------------------

    private fun toggleMemberTag(member: MemberItem) {
        val newTag = if (member.tag == 1) 0 else 1
        val values = ContentValues().apply { put(WinkerkContract.winkerkEntry.LIDMATE_TAG, newTag) }
        val selection = "${WinkerkContract.winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ?"
        contentResolver.update(
            WinkerkContract.winkerkEntry.CONTENT_URI,
            values,
            selection,
            arrayOf(member.id.toString())
        )
        val message = if (newTag == 1) {
            getString(R.string.verjaar_member_selected, member.name)
        } else {
            getString(R.string.verjaar_member_deselected, member.name)
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        eventViewModel.loadEventData(keuse)
    }

    // ------------------------------------------------------------------------
    // Popup Menu
    // ------------------------------------------------------------------------

    private fun showPopupMenuForMember(member: MemberItem) {
        val popup = PopupMenu(this, binding.lidmaatList)
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
            popup.menu.findItem(R.id.stuur_sms).title =
                getString(R.string.verjaar_sms_phone, phone)
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

        // ─── Use injected appearancePrefs instead of SettingsManager ───
        if (!appearancePrefs.whatsapp1) popup.menu.findItem(R.id.submenu_teks).subMenu?.removeItem(
            R.id.stuur_whatsapp
        )
        if (!appearancePrefs.whatsapp2) popup.menu.findItem(R.id.submenu_teks).subMenu?.removeItem(
            R.id.stuur_whatsapp2
        )
        if (!appearancePrefs.whatsapp3) popup.menu.findItem(R.id.submenu_teks).subMenu?.removeItem(
            R.id.stuur_whatsapp3
        )

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.stuur_whatsapp, R.id.stuur_whatsapp2, R.id.stuur_whatsapp3 -> {
                    val phoneNumber = fixphonenumber(member.cellphone)
                    if (phoneNumber.isNullOrEmpty()) {
                        Snackbar.make(
                            binding.root,
                            R.string.verjaar_no_phone,
                            Snackbar.LENGTH_SHORT
                        ).show()
                        return@setOnMenuItemClickListener false
                    }
                    val msg = MessageComposer.personalize(binding.boodskap.text.toString(), member)
                    val method = when (item.itemId) {
                        R.id.stuur_whatsapp3 -> 3
                        R.id.stuur_whatsapp2 -> 2
                        else -> 1
                    }
                    WhatsAppMessageSender.send(this, phoneNumber, method, msg)
                    true
                }
                else -> MemberActionHandler(this, member, memberViewModel).handleAction(item.itemId)
            }
        }
        popup.show()
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