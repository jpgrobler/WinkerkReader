package za.co.jpsoft.winkerkreader.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.FragmentUitlegPastoraalBinding
import za.co.jpsoft.winkerkreader.ui.activities.UitlegActivity
import za.co.jpsoft.winkerkreader.ui.activities.UitlegCalendarSelectionListener
import za.co.jpsoft.winkerkreader.utils.PastoralTaskScriptManager
import za.co.jpsoft.winkerkreader.utils.SettingsManager

class UitlegPastoraalFragment : Fragment() {
    private var isLoggedIn = false
    private var _binding: FragmentUitlegPastoraalBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager
    private var listener: UitlegCalendarSelectionListener? = null
    private var taskLists: List<Pair<String, String>> = emptyList()
    private var selectedTaskListId: String? = null

    // Aanvanklike waardes
    private var initialAutoSync = false
    private var initialMode = SettingsManager.GoogleTasksMode.OFF
    private var initialUrl: String? = null
    private var initialSecret: String? = null
    private var initialTaskListId: String? = null
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
        _binding = FragmentUitlegPastoraalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager.getInstance(requireContext())

        // Tydelike spinner
        val tempAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("Laai kalenders…"))
        tempAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.pastoralCalendarSpinner.adapter = tempAdapter

        loadInitialState()
        setupGoogleTasksUI()
        setupListeners()
        isInitializing = false
        isDirty = false

        Handler(Looper.getMainLooper()).postDelayed({
            updateSaveButtonState()
        }, 300)

        if (settingsManager.googleTasksMode() == SettingsManager.GoogleTasksMode.API &&
            settingsManager.isTasksScriptConfigured()) {
            loadTaskLists()
        }

        val savedUrl = settingsManager.tasksScriptUrl
        val savedSecret = settingsManager.tasksScriptSecret
        if (!savedUrl.isNullOrBlank() && !savedSecret.isNullOrBlank()) {
            // Assume they were working (or we can check later); set to "Refresh Lists"
            binding.btnRefreshLists.text = getString(R.string.refresh_lists_btn)
            isLoggedIn = true
        } else {
            binding.btnRefreshLists.text = getString(R.string.login_button)
        }
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Credential changed → reset login state
                if (isLoggedIn) {
                    isLoggedIn = false
                    binding.btnRefreshLists.text = getString(R.string.login_button)
                }
            }
        }
        binding.appsScriptLink.addTextChangedListener(textWatcher)
        binding.appsScriptKey.addTextChangedListener(textWatcher)
        setupBackupStatusSection()
    }

    private fun loadInitialState() {
        initialAutoSync = settingsManager.pastoralCalendarSyncEnabled
        initialMode = settingsManager.googleTasksMode()
        initialUrl = settingsManager.tasksScriptUrl
        initialSecret = settingsManager.tasksScriptSecret
        initialTaskListId = settingsManager.googleTasksListId
        initialCalendarId = settingsManager.getPastoralCalendarId() ?: -1L
    }

    fun setPastoralCalendarSpinner(adapter: ArrayAdapter<String>, selectedId: Long) {
        binding.pastoralCalendarSpinner.adapter = adapter
        adapter.notifyDataSetChanged()

        val position = (0 until adapter.count).firstOrNull {
            (activity as? UitlegActivity)?.getCalendarIdAtPosition(it) == selectedId
        }
        if (position != null && position < adapter.count) {
            binding.pastoralCalendarSpinner.setSelection(position)
        }
        initialCalendarId = selectedId

        binding.pastoralCalendarSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val activity = requireActivity() as? UitlegActivity
                val calId = activity!!.getCalendarIdAtPosition(position)
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

    private fun setupGoogleTasksUI() {
        when (initialMode) {
            SettingsManager.GoogleTasksMode.OFF -> binding.tasksModeOff.isChecked = true
            SettingsManager.GoogleTasksMode.API -> binding.tasksModeApi.isChecked = true
            SettingsManager.GoogleTasksMode.SHARE -> binding.tasksModeShare.isChecked = true
        }
        val isApi = initialMode == SettingsManager.GoogleTasksMode.API
        binding.tasksListSpinner.visibility = if (isApi) View.VISIBLE else View.GONE
        binding.btnRefreshLists.visibility = if (isApi) View.VISIBLE else View.GONE

        selectedTaskListId = initialTaskListId
        binding.appsScriptLink.setText(initialUrl ?: "")
        binding.appsScriptKey.setText(initialSecret ?: "")
        binding.pastoralCalendarAutoSync.isChecked = initialAutoSync
    }

    private fun setupListeners() {
        binding.appsScriptLink.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { onUserChanged() }
        })
        binding.appsScriptKey.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { onUserChanged() }
        })

        binding.tasksModeGroup.setOnCheckedChangeListener { _, _ -> onUserChanged() }
        binding.pastoralCalendarAutoSync.setOnCheckedChangeListener { _, _ -> onUserChanged() }

        binding.tasksListSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newId = if (position < taskLists.size) taskLists[position].first else null
                if (newId != selectedTaskListId) {
                    selectedTaskListId = newId
                    onUserChanged()
                } else {
                    selectedTaskListId = newId  // keep in sync without marking dirty
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnRefreshLists.setOnClickListener { loadTaskLists() }
        binding.btnCopyScript.setOnClickListener { copyScriptToClipboard() }
        binding.tasksSave.setOnClickListener { saveGoogleTasksSettings() }
    }

    private fun onUserChanged() {
        if (!isInitializing) {
            isDirty = true
            updateSaveButtonState()
        }
    }

    private fun updateSaveButtonState() {
        val enabled = isDirty || isAnySettingChanged()
        binding.tasksSave.isEnabled = enabled
        binding.tasksSave.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun isAnySettingChanged(): Boolean {
        if (binding.pastoralCalendarAutoSync.isChecked != initialAutoSync) return true

        val currentMode = when (binding.tasksModeGroup.checkedRadioButtonId) {
            R.id.tasks_mode_api -> SettingsManager.GoogleTasksMode.API
            R.id.tasks_mode_share -> SettingsManager.GoogleTasksMode.SHARE
            else -> SettingsManager.GoogleTasksMode.OFF
        }
        if (currentMode != initialMode) return true

        val currentUrl = binding.appsScriptLink.text?.toString()?.trim()?.ifBlank { null }
        if (currentUrl != initialUrl) return true
        val currentSecret = binding.appsScriptKey.text?.toString()?.trim()?.ifBlank { null }
        if (currentSecret != initialSecret) return true

        if (selectedTaskListId != initialTaskListId) return true

        if (binding.pastoralCalendarSpinner.adapter != null && binding.pastoralCalendarSpinner.adapter.count > 0) {
            val firstItem = binding.pastoralCalendarSpinner.adapter.getItem(0)?.toString()
            if (firstItem != "Laai kalenders…" && firstItem != "Geen kalenders gevind") {
                val selectedId = (activity as? UitlegActivity)?.getCalendarIdAtPosition(
                    binding.pastoralCalendarSpinner.selectedItemPosition
                ) ?: -1L
                if (selectedId != initialCalendarId) return true
            }
        }

        return false
    }

    private fun loadTaskLists() {
        val url = binding.appsScriptLink.text?.toString()?.trim()?.ifBlank { null }
        val secret = binding.appsScriptKey.text?.toString()?.trim()?.ifBlank { null }

        if (url.isNullOrBlank() || secret.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Stel eers die Apps Script URL en Geheime kode", Toast.LENGTH_SHORT).show()
            return
        }

        // If not logged in, this is a login attempt
        if (!isLoggedIn) {
            binding.btnRefreshLists.isEnabled = false
            binding.btnRefreshLists.text = getString(R.string.logging_in) // optional, or just keep "Login"
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val listData = PastoralTaskScriptManager.listTaskLists(url, secret)
                withContext(Dispatchers.Main) {
                    binding.btnRefreshLists.isEnabled = true
                    if (listData == null) {
                        Toast.makeText(
                            requireContext(),
                            "Login failed. Check URL and secret key.",
                            Toast.LENGTH_LONG
                        ).show()
                        // Keep isLoggedIn = false, button shows "Login"
                    } else {
                        // Success
                        isLoggedIn = true
                        taskLists = listData
                        populateTaskListSpinner()
                        binding.btnRefreshLists.text = getString(R.string.refresh_lists_btn)
                        Toast.makeText(
                            requireContext(),
                            "Login successful! Lists loaded.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnRefreshLists.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        "Login failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun populateTaskListSpinner() {
        val items = if (taskLists.isEmpty()) {
            listOf(getString(R.string.tasks_no_lists))
        } else {
            taskLists.map { it.second }
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.tasksListSpinner.adapter = adapter

        if (selectedTaskListId != null) {
            val index = taskLists.indexOfFirst { it.first == selectedTaskListId }
            if (index >= 0) binding.tasksListSpinner.setSelection(index)
        }
        // Listener already set
    }

    private fun saveGoogleTasksSettings() {
        val selectedId = binding.tasksModeGroup.checkedRadioButtonId
        val mode = when (selectedId) {
            R.id.tasks_mode_api -> SettingsManager.GoogleTasksMode.API
            R.id.tasks_mode_share -> SettingsManager.GoogleTasksMode.SHARE
            else -> SettingsManager.GoogleTasksMode.OFF
        }
        settingsManager.setGoogleTasksMode(mode)
        settingsManager.pastoralCalendarSyncEnabled = binding.pastoralCalendarAutoSync.isChecked

        (activity as? UitlegActivity)?.savePastoralCalendarId()

        val scriptUrl = binding.appsScriptLink.text?.toString()?.trim()
        val scriptSecret = binding.appsScriptKey.text?.toString()?.trim()
        settingsManager.tasksScriptUrl = scriptUrl?.ifBlank { null }
        settingsManager.tasksScriptSecret = scriptSecret?.ifBlank { null }
        settingsManager.googleTasksListId = selectedTaskListId

        // Update initial values
        initialAutoSync = binding.pastoralCalendarAutoSync.isChecked
        initialMode = mode
        initialUrl = scriptUrl?.ifBlank { null }
        initialSecret = scriptSecret?.ifBlank { null }
        initialTaskListId = selectedTaskListId
        initialCalendarId = (activity as? UitlegActivity)?.getCalendarIdAtPosition(
            binding.pastoralCalendarSpinner.selectedItemPosition
        ) ?: -1L

        isDirty = false
        updateSaveButtonState()
        Toast.makeText(requireContext(), "Pastorale instellings gestoor", Toast.LENGTH_SHORT).show()
    }

    private fun copyScriptToClipboard() {
        // ... (unchanged)
        val scriptCode = """…"""
        val instructions = """…"""
        val fullText = "$scriptCode\n\n$instructions"
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("WinkerkReader Script", fullText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Skrip en instruksies is na knipbord gekopieer", Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupBackupStatusSection() {
        binding.backupLocationText.text =
            "Ligging: ${za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.getWkrDir(requireContext())}"

        binding.backupStatusPastoralText.text =
            "Herinneringe & notas: ${formatBackupTimestamp(settingsManager.lastPastoralBackupTimestamp)}"

        binding.backupStatusCallLogText.text =
            "Oproeplog: ${formatBackupTimestamp(settingsManager.lastCallLogBackupTimestamp)}"

        binding.callLogBackupSwitch.isChecked = settingsManager.callLogBackupEnabled
        binding.callLogBackupSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.callLogBackupEnabled = isChecked
            if (isChecked) {
                // Back up immediately on enabling, rather than waiting for the
                // next call/mutation, so the status line updates right away.
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        za.co.jpsoft.winkerkreader.data.calllog.CallLogDatabaseBackup.backupNow(requireContext())
                    }
                    binding.backupStatusCallLogText.text =
                        "Oproeplog: ${formatBackupTimestamp(settingsManager.lastCallLogBackupTimestamp)}"
                }
            }
        }
    }

    private fun formatBackupTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "Nog nie rugsteun gemaak nie"

        val now = System.currentTimeMillis()
        val diffMs = now - timestamp
        val diffMinutes = diffMs / (60 * 1000)
        val diffHours = diffMs / (60 * 60 * 1000)
        val diffDays = diffMs / (24 * 60 * 60 * 1000)

        return when {
            diffMinutes < 1 -> "Nou-nou"
            diffMinutes < 60 -> "$diffMinutes minute gelede"
            diffHours < 24 -> "$diffHours ure gelede"
            diffDays == 1L -> "Gister"
            else -> {
                val formatter = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale("af"))
                formatter.format(java.util.Date(timestamp))
            }
        }
    }
}