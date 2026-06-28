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
import androidx.fragment.app.Fragment
import za.co.jpsoft.winkerkreader.databinding.FragmentUitlegFunksiesBinding
import za.co.jpsoft.winkerkreader.ui.activities.UitlegActivity
import za.co.jpsoft.winkerkreader.ui.activities.UitlegCalendarSelectionListener
import za.co.jpsoft.winkerkreader.utils.SettingsManager

class UitlegFunksiesFragment : Fragment() {

    private var _binding: FragmentUitlegFunksiesBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager
    private var listener: UitlegCalendarSelectionListener? = null

    private var initialMonitorOproepe = false
    private var initialLogOproepe = false
    private var initialLogVOIP = false
    private var initialHtml = false
    private var initialW1 = false
    private var initialW2 = false
    private var initialW3 = false
    private var initialAutoStart = false
    private var initialCalendarId: Long = -1L

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
        settingsManager = SettingsManager.getInstance(requireContext())

        val tempAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("Laai kalenders…"))
        tempAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.calendarSpinner.adapter = tempAdapter

        loadPreferences()
        setupListeners()
        isInitializing = false
        isDirty = false

        Handler(Looper.getMainLooper()).postDelayed({
            updateSaveButtonState()
        }, 300)
    }

    private fun loadPreferences() {
        initialMonitorOproepe = settingsManager.callMonitorEnabled
        binding.uitlegMonitorOproepe.isChecked = initialMonitorOproepe

        initialLogOproepe = settingsManager.callLogEnabled
        binding.uitlegLogOproepe.isChecked = initialLogOproepe

        initialLogVOIP = settingsManager.voipLogEnabled
        binding.uitlegLogVOIP.isChecked = initialLogVOIP

        initialHtml = settingsManager.eposHtml
        binding.uitlegHtml.isChecked = initialHtml

        initialW1 = settingsManager.whatsapp1
        binding.uitlegW1.isChecked = initialW1

        initialW2 = settingsManager.whatsapp2
        binding.uitlegW2.isChecked = initialW2

        initialW3 = settingsManager.whatsapp3
        binding.uitlegW3.isChecked = initialW3

        initialAutoStart = settingsManager.autoStartEnabled
        binding.autoStartSwitch.isChecked = initialAutoStart

        initialCalendarId = settingsManager.selectedCalendarId
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
            binding.autoStartSwitch
        )
        checkboxes.forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ -> onUserChanged() }
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

        binding.calendarSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val activity = requireActivity() as? UitlegActivity ?: return
                val calId = activity.getCalendarIdAtPosition(position)
                listener?.onCallCalendarSelected(calId)
                // Only mark dirty if the user actually picked a different calendar
                if (calId != initialCalendarId) {
                    onUserChanged()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Update save state after adapter is set
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

        if (binding.calendarSpinner.adapter != null && binding.calendarSpinner.adapter.count > 0) {
            val firstItem = binding.calendarSpinner.adapter.getItem(0)?.toString()
            if (firstItem != "Laai kalenders…" && firstItem != "Geen kalenders gevind") {
                val selectedId = (activity as? UitlegActivity)?.getCalendarIdAtPosition(
                    binding.calendarSpinner.selectedItemPosition
                ) ?: -1L
                if (selectedId != initialCalendarId) return true
            }
        }

        return false
    }

    private fun saveFunctionSettings() {
        settingsManager.callMonitorEnabled = binding.uitlegMonitorOproepe.isChecked
        settingsManager.callLogEnabled = binding.uitlegLogOproepe.isChecked
        settingsManager.voipLogEnabled = binding.uitlegLogVOIP.isChecked
        settingsManager.whatsapp1 = binding.uitlegW1.isChecked
        settingsManager.whatsapp2 = binding.uitlegW2.isChecked
        settingsManager.whatsapp3 = binding.uitlegW3.isChecked
        settingsManager.eposHtml = binding.uitlegHtml.isChecked
        settingsManager.autoStartEnabled = binding.autoStartSwitch.isChecked

        (activity as? UitlegActivity)?.saveCallCalendarId()

        // Update initial values
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

        isDirty = false
        updateSaveButtonState()
        Toast.makeText(requireContext(), "Funksie-instellings gestoor", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}