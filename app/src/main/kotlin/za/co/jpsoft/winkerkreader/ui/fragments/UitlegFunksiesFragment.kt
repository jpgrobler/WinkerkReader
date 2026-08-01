package za.co.jpsoft.winkerkreader.ui.fragments

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import za.co.jpsoft.winkerkreader.databinding.FragmentUitlegFunksiesBinding
import za.co.jpsoft.winkerkreader.di.UserPrefs
import za.co.jpsoft.winkerkreader.ui.activities.UitlegActivity
import za.co.jpsoft.winkerkreader.ui.activities.UitlegCalendarSelectionListener
import za.co.jpsoft.winkerkreader.utils.prefs.*

@AndroidEntryPoint
class UitlegFunksiesFragment : Fragment() {

    @Inject
    lateinit var quickActionPrefs: QuickActionPrefs
    @Inject
    lateinit var callMonitorPrefs: CallMonitorPrefs
    @Inject
    lateinit var appearancePrefs: AppearancePrefs
    @Inject
    lateinit var securityPrefs: SecurityPrefs
    @Inject
    @UserPrefs
    lateinit var calendarPrefs: CalendarPrefs
    private var _binding: FragmentUitlegFunksiesBinding? = null
    private val binding get() = _binding!!
    private var listener: UitlegCalendarSelectionListener? = null

    // Initial values
    private var initialShowQuickActions = false
    private var initialMonitorOproepe = false
    private var initialLogOproepe = false
    private var initialLogVOIP = false
    private var initialHtml = false
    private var initialW1 = false
    private var initialW2 = false
    private var initialW3 = false
    private var initialAutoStart = false
    private var initialCalendarId: Long? = -1L
    private var initialBiometricLock = false
    private var initialBiometricTimeoutMs = Long.MAX_VALUE

    private var initialQaDetail = false
    private var initialQaSms = false
    private var initialQaWhatsApp = false
    private var initialQaCall = false
    private var initialQaEmail = false
    private var initialQaLandline = false
    private var initialQaNote = false
    private var initialQaReminder = false
    private var initialQaCopy = false
    private var initialQaCopyContacts = false

    private var isInitializing = true
    private var isDirty = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = activity as? UitlegCalendarSelectionListener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUitlegFunksiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tempAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("Laai kalenders…")
        )
        tempAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.calendarSpinner.adapter = tempAdapter

        loadPreferences()
        setupListeners()
        isInitializing = false
        isDirty = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.nestedScrollView) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }

        Handler(Looper.getMainLooper()).postDelayed({
            updateSaveButtonState()
        }, 300)
    }

    private fun loadPreferences() {
        initialShowQuickActions = quickActionPrefs.showQuickActionBar
        binding.uitlegShowQuickActions.isChecked = initialShowQuickActions

        initialMonitorOproepe = callMonitorPrefs.callMonitorEnabled
        binding.uitlegMonitorOproepe.isChecked = initialMonitorOproepe

        initialLogOproepe = callMonitorPrefs.callLogEnabled
        binding.uitlegLogOproepe.isChecked = initialLogOproepe

        initialLogVOIP = callMonitorPrefs.voipLogEnabled
        binding.uitlegLogVOIP.isChecked = initialLogVOIP

        initialHtml = appearancePrefs.eposHtml
        binding.uitlegHtml.isChecked = initialHtml

        initialW1 = appearancePrefs.whatsapp1
        binding.uitlegW1.isChecked = initialW1
        initialW2 = appearancePrefs.whatsapp2
        binding.uitlegW2.isChecked = initialW2
        initialW3 = appearancePrefs.whatsapp3
        binding.uitlegW3.isChecked = initialW3

        initialAutoStart = callMonitorPrefs.autoStartEnabled
        binding.autoStartSwitch.isChecked = initialAutoStart

        initialBiometricLock = securityPrefs.biometricEnabled
        binding.uitlegBiometricLock.isChecked = initialBiometricLock

        initialBiometricTimeoutMs = securityPrefs.biometricTimeoutMs
        binding.biometricTimeoutSpinner.setSelection(if (initialBiometricTimeoutMs == Long.MAX_VALUE) 0 else 1)
        binding.biometricTimeoutSpinner.isEnabled = initialBiometricLock

        initialCalendarId = calendarPrefs.selectedCalendarId

        initialQaDetail = quickActionPrefs.quickActionDetail
        binding.qaDetail.isChecked = initialQaDetail

        initialQaSms = quickActionPrefs.quickActionSms
        binding.qaSms.isChecked = initialQaSms

        initialQaWhatsApp = quickActionPrefs.quickActionWhatsApp
        binding.qaWhatsapp.isChecked = initialQaWhatsApp

        initialQaCall = quickActionPrefs.quickActionCall
        binding.qaCall.isChecked = initialQaCall

        initialQaEmail = quickActionPrefs.quickActionEmail
        binding.qaEmail.isChecked = initialQaEmail

        initialQaLandline = quickActionPrefs.quickActionLandline
        binding.qaLandline.isChecked = initialQaLandline

        initialQaNote = quickActionPrefs.quickActionNote
        binding.qaNote.isChecked = initialQaNote

        initialQaReminder = quickActionPrefs.quickActionReminder
        binding.qaReminder.isChecked = initialQaReminder

        initialQaCopy = quickActionPrefs.quickActionCopy
        binding.qaCopy.isChecked = initialQaCopy

        initialQaCopyContacts = quickActionPrefs.quickActionCopyContacts
        binding.qaCopyContacts.isChecked = initialQaCopyContacts
    }

    private fun setupListeners() {
        val checkboxes = listOf(
            binding.uitlegMonitorOproepe,
            binding.uitlegLogOproepe,
            binding.uitlegLogVOIP,
            binding.uitlegHtml,
            binding.uitlegW1,
            binding.uitlegW2,
            binding.uitlegW3,
            binding.autoStartSwitch,
            binding.uitlegBiometricLock,
            binding.qaDetail,
            binding.qaSms,
            binding.qaWhatsapp,
            binding.qaCall,
            binding.qaEmail,
            binding.qaLandline,
            binding.qaNote,
            binding.qaReminder,
            binding.qaCopy,
            binding.qaCopyContacts
        )
        checkboxes.forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ -> onUserChanged() }
        }

        binding.uitlegBiometricLock.setOnCheckedChangeListener { _, isChecked ->
            binding.biometricTimeoutSpinner.isEnabled = isChecked
            onUserChanged()
        }

        binding.uitlegShowQuickActions.setOnCheckedChangeListener { _, _ -> onUserChanged() }

        binding.biometricTimeoutSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    onUserChanged()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        binding.funksoieStoor.setOnClickListener { saveFunctionSettings() }
    }

    fun setCallCalendarSpinner(adapter: ArrayAdapter<String>, selectedId: Long) {
        binding.calendarSpinner.adapter = adapter
        adapter.notifyDataSetChanged()

        val position = (0 until adapter.count).firstOrNull {
            (activity as? UitlegActivity)?.getCalendarIdAtPosition(it) == selectedId
        }
        if (position != null && position < adapter.count) {
            binding.calendarSpinner.setSelection(position)
        }
        initialCalendarId = selectedId

        binding.calendarSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val activity = requireActivity() as? UitlegActivity ?: return
                    val calId = activity.getCalendarIdAtPosition(position)
                    listener?.onCallCalendarSelected(calId)
                    if (calId != initialCalendarId) {
                        onUserChanged()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        isInitializing = false
        isDirty = false
        updateSaveButtonState()
    }

    private fun onUserChanged() {
        if (!isInitializing) {
            isDirty = true
            updateSaveButtonState()
        }
    }

    private fun updateSaveButtonState() {
        val enabled = isDirty || isAnySettingChanged()
        binding.funksoieStoor.isEnabled = enabled
        binding.funksoieStoor.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun isAnySettingChanged(): Boolean {
        if (binding.uitlegMonitorOproepe.isChecked != initialMonitorOproepe) return true
        if (binding.uitlegLogOproepe.isChecked != initialLogOproepe) return true
        if (binding.uitlegLogVOIP.isChecked != initialLogVOIP) return true
        if (binding.uitlegHtml.isChecked != initialHtml) return true
        if (binding.uitlegW1.isChecked != initialW1) return true
        if (binding.uitlegW2.isChecked != initialW2) return true
        if (binding.uitlegW3.isChecked != initialW3) return true
        if (binding.autoStartSwitch.isChecked != initialAutoStart) return true
        if (binding.uitlegBiometricLock.isChecked != initialBiometricLock) return true
        if (binding.uitlegShowQuickActions.isChecked != initialShowQuickActions) return true

        val selectedTimeoutMs =
            if (binding.biometricTimeoutSpinner.selectedItemPosition == 0) Long.MAX_VALUE else 10_000L
        if (selectedTimeoutMs != initialBiometricTimeoutMs) return true

        if (binding.calendarSpinner.adapter != null && binding.calendarSpinner.adapter.count > 0) {
            val firstItem = binding.calendarSpinner.adapter.getItem(0)?.toString()
            if (firstItem != "Laai kalenders…" && firstItem != "Geen kalenders gevind") {
                val selectedId = (activity as? UitlegActivity)?.getCalendarIdAtPosition(
                    binding.calendarSpinner.selectedItemPosition
                ) ?: -1L
                if (selectedId != initialCalendarId) return true
            }
        }

        if (binding.qaDetail.isChecked != initialQaDetail) return true
        if (binding.qaSms.isChecked != initialQaSms) return true
        if (binding.qaWhatsapp.isChecked != initialQaWhatsApp) return true
        if (binding.qaCall.isChecked != initialQaCall) return true
        if (binding.qaEmail.isChecked != initialQaEmail) return true
        if (binding.qaLandline.isChecked != initialQaLandline) return true
        if (binding.qaNote.isChecked != initialQaNote) return true
        if (binding.qaReminder.isChecked != initialQaReminder) return true
        if (binding.qaCopy.isChecked != initialQaCopy) return true
        if (binding.qaCopyContacts.isChecked != initialQaCopyContacts) return true

        return false
    }

    private fun saveFunctionSettings() {
        callMonitorPrefs.callMonitorEnabled = binding.uitlegMonitorOproepe.isChecked
        callMonitorPrefs.callLogEnabled = binding.uitlegLogOproepe.isChecked
        callMonitorPrefs.voipLogEnabled = binding.uitlegLogVOIP.isChecked
        callMonitorPrefs.autoStartEnabled = binding.autoStartSwitch.isChecked

        appearancePrefs.whatsapp1 = binding.uitlegW1.isChecked
        appearancePrefs.whatsapp2 = binding.uitlegW2.isChecked
        appearancePrefs.whatsapp3 = binding.uitlegW3.isChecked
        appearancePrefs.eposHtml = binding.uitlegHtml.isChecked

        securityPrefs.biometricEnabled = binding.uitlegBiometricLock.isChecked
        securityPrefs.biometricTimeoutMs =
            if (binding.biometricTimeoutSpinner.selectedItemPosition == 0) Long.MAX_VALUE else 10_000L

        (activity as? UitlegActivity)?.saveCallCalendarId()

        quickActionPrefs.showQuickActionBar = binding.uitlegShowQuickActions.isChecked
        quickActionPrefs.quickActionDetail = binding.qaDetail.isChecked
        quickActionPrefs.quickActionSms = binding.qaSms.isChecked
        quickActionPrefs.quickActionWhatsApp = binding.qaWhatsapp.isChecked
        quickActionPrefs.quickActionCall = binding.qaCall.isChecked
        quickActionPrefs.quickActionEmail = binding.qaEmail.isChecked
        quickActionPrefs.quickActionLandline = binding.qaLandline.isChecked
        quickActionPrefs.quickActionNote = binding.qaNote.isChecked
        quickActionPrefs.quickActionReminder = binding.qaReminder.isChecked
        quickActionPrefs.quickActionCopy = binding.qaCopy.isChecked
        quickActionPrefs.quickActionCopyContacts = binding.qaCopyContacts.isChecked

        initialMonitorOproepe = binding.uitlegMonitorOproepe.isChecked
        initialLogOproepe = binding.uitlegLogOproepe.isChecked
        initialLogVOIP = binding.uitlegLogVOIP.isChecked
        initialHtml = binding.uitlegHtml.isChecked
        initialW1 = binding.uitlegW1.isChecked
        initialW2 = binding.uitlegW2.isChecked
        initialW3 = binding.uitlegW3.isChecked
        initialAutoStart = binding.autoStartSwitch.isChecked
        initialCalendarId = (activity as? UitlegActivity)?.getCalendarIdAtPosition(
            binding.calendarSpinner.selectedItemPosition
        ) ?: -1L
        initialBiometricLock = binding.uitlegBiometricLock.isChecked
        initialBiometricTimeoutMs = securityPrefs.biometricTimeoutMs
        initialShowQuickActions = binding.uitlegShowQuickActions.isChecked

        initialQaDetail = binding.qaDetail.isChecked
        initialQaSms = binding.qaSms.isChecked
        initialQaWhatsApp = binding.qaWhatsapp.isChecked
        initialQaCall = binding.qaCall.isChecked
        initialQaEmail = binding.qaEmail.isChecked
        initialQaLandline = binding.qaLandline.isChecked
        initialQaNote = binding.qaNote.isChecked
        initialQaReminder = binding.qaReminder.isChecked
        initialQaCopy = binding.qaCopy.isChecked
        initialQaCopyContacts = binding.qaCopyContacts.isChecked

        isDirty = false
        updateSaveButtonState()
        Toast.makeText(requireContext(), "Funksie-instellings gestoor", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}