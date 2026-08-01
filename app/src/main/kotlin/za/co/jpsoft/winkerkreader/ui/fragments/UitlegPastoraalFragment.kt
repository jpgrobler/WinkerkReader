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
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.FragmentUitlegPastoraalBinding
import za.co.jpsoft.winkerkreader.di.UserPrefs
import za.co.jpsoft.winkerkreader.ui.activities.UitlegActivity
import za.co.jpsoft.winkerkreader.ui.activities.UitlegCalendarSelectionListener
import za.co.jpsoft.winkerkreader.utils.PastoralTaskScriptManager
import za.co.jpsoft.winkerkreader.utils.prefs.*

@AndroidEntryPoint
class UitlegPastoraalFragment : Fragment() {

    @Inject
    lateinit var pastoralPrefs: PastoralPrefs
    @Inject
    lateinit var tasksPrefs: TasksPrefs
    @Inject
    lateinit var backupPrefs: BackupPrefs

    @Inject
    @UserPrefs
    lateinit var calendarPrefs: CalendarPrefs
    private var isLoggedIn = false
    private var _binding: FragmentUitlegPastoraalBinding? = null
    private val binding get() = _binding!!
    private var listener: UitlegCalendarSelectionListener? = null
    private var taskLists: List<Pair<String, String>> = emptyList()
    private var selectedTaskListId: String? = null

    private var initialAutoSync = false
    private var initialMode = TasksPrefs.GoogleTasksMode.OFF
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

        val tempAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("Laai kalenders…")
        )
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

        if (tasksPrefs.googleTasksMode() == TasksPrefs.GoogleTasksMode.API &&
            tasksPrefs.isTasksScriptConfigured()
        ) {
            loadTaskLists()
        }

        val savedUrl = tasksPrefs.tasksScriptUrl
        val savedSecret = tasksPrefs.tasksScriptSecret
        if (!savedUrl.isNullOrBlank() && !savedSecret.isNullOrBlank()) {
            binding.btnRefreshLists.text = getString(R.string.refresh_lists_btn)
            isLoggedIn = true
        } else {
            binding.btnRefreshLists.text = getString(R.string.login_button)
        }
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
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
        initialAutoSync = pastoralPrefs.pastoralCalendarSyncEnabled
        initialMode = tasksPrefs.googleTasksMode()
        initialUrl = tasksPrefs.tasksScriptUrl
        initialSecret = tasksPrefs.tasksScriptSecret
        initialTaskListId = tasksPrefs.googleTasksListId
        initialCalendarId = pastoralPrefs.pastoralCalendarId ?: -1L
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

        binding.pastoralCalendarSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val activity = requireActivity() as? UitlegActivity
                    val calId = activity!!.getCalendarIdAtPosition(position)
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

    private fun setupGoogleTasksUI() {
        when (initialMode) {
            TasksPrefs.GoogleTasksMode.OFF -> binding.tasksModeOff.isChecked = true
            TasksPrefs.GoogleTasksMode.API -> binding.tasksModeApi.isChecked = true
            TasksPrefs.GoogleTasksMode.SHARE -> binding.tasksModeShare.isChecked = true
        }
        val isApi = initialMode == TasksPrefs.GoogleTasksMode.API
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
            override fun afterTextChanged(s: Editable?) {
                onUserChanged()
            }
        })
        binding.appsScriptKey.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onUserChanged()
            }
        })

        binding.tasksModeGroup.setOnCheckedChangeListener { _, _ -> onUserChanged() }
        binding.pastoralCalendarAutoSync.setOnCheckedChangeListener { _, _ -> onUserChanged() }

        binding.tasksListSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val newId = if (position < taskLists.size) taskLists[position].first else null
                    if (newId != selectedTaskListId) {
                        selectedTaskListId = newId
                        onUserChanged()
                    } else {
                        selectedTaskListId = newId
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
            R.id.tasks_mode_api -> TasksPrefs.GoogleTasksMode.API
            R.id.tasks_mode_share -> TasksPrefs.GoogleTasksMode.SHARE
            else -> TasksPrefs.GoogleTasksMode.OFF
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
            Toast.makeText(
                requireContext(),
                "Stel eers die Apps Script URL en Geheime kode",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!isLoggedIn) {
            binding.btnRefreshLists.isEnabled = false
            binding.btnRefreshLists.text = getString(R.string.logging_in)
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
                    } else {
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
    }

    private fun saveGoogleTasksSettings() {
        val selectedId = binding.tasksModeGroup.checkedRadioButtonId
        val mode = when (selectedId) {
            R.id.tasks_mode_api -> TasksPrefs.GoogleTasksMode.API
            R.id.tasks_mode_share -> TasksPrefs.GoogleTasksMode.SHARE
            else -> TasksPrefs.GoogleTasksMode.OFF
        }
        tasksPrefs.setGoogleTasksMode(mode)
        pastoralPrefs.pastoralCalendarSyncEnabled = binding.pastoralCalendarAutoSync.isChecked

        (activity as? UitlegActivity)?.savePastoralCalendarId()

        val scriptUrl = binding.appsScriptLink.text?.toString()?.trim()
        val scriptSecret = binding.appsScriptKey.text?.toString()?.trim()
        tasksPrefs.tasksScriptUrl = scriptUrl?.ifBlank { null }
        tasksPrefs.tasksScriptSecret = scriptSecret?.ifBlank { null }
        tasksPrefs.googleTasksListId = selectedTaskListId

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

    // copyScriptToClipboard, onDestroyView, setupBackupStatusSection, formatBackupTimestamp remain unchanged
    // (but update references to backupPrefs where needed)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupBackupStatusSection() {
        var ligg = "Ligging: ${
            za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.getWkrDir(requireContext())
        }"
        binding.backupLocationText.text = ligg
        ligg =
            "Herinneringe & notas: ${formatBackupTimestamp(backupPrefs.lastPastoralBackupTimestamp)}"
        binding.backupStatusPastoralText.text = ligg
        ligg = "Oproeplog: ${formatBackupTimestamp(backupPrefs.lastCallLogBackupTimestamp)}"
        binding.backupStatusCallLogText.text = ligg

        binding.callLogBackupSwitch.isChecked = backupPrefs.callLogBackupEnabled
        binding.callLogBackupSwitch.setOnCheckedChangeListener { _, isChecked ->
            backupPrefs.callLogBackupEnabled = isChecked
            if (isChecked) {
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        za.co.jpsoft.winkerkreader.data.calllog.CallLogDatabaseBackup.backupNow(
                            requireContext()
                        )
                    }
                    ligg =
                        "Oproeplog: ${formatBackupTimestamp(backupPrefs.lastCallLogBackupTimestamp)}"
                    binding.backupStatusCallLogText.text = ligg
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
                val formatter =
                    java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale("af"))
                formatter.format(java.util.Date(timestamp))
            }
        }
    }
    private fun copyScriptToClipboard() {
        val scriptCode = """
/**
 * WinkerkReader Pastoral Reminders — Google Tasks Bridge
 * Deploy as: Web App | Execute as: Me | Who has access: Anyone
 */
const WKR_MARKER = '[WKR] ';
function doGet(e) {
  var secret = PropertiesService.getScriptProperties().getProperty('SECRET');
  if (!e.parameter || e.parameter.secret !== secret) {
    console.log('Invalid secret provided');
    return respond('UNAUTHORIZED');
  }
  var action = e.parameter.action;
  console.log('Action: ' + action);
  try {
    if (action === 'add')      return handleAdd(e);
    if (action === 'delete')   return handleDelete(e);
    if (action === 'complete') return handleComplete(e);
    if (action === 'list')     return handleList(e);
    return respond('ERROR:unknown_action');
  } catch (err) {
    console.error('Error in doGet: ' + err.message);
    return respond('ERROR:' + err.message);
  }
}
function handleAdd(e) {
  var title = e.parameter.title || 'Herinnering';
  var notes = e.parameter.notes || '';
  var due   = e.parameter.due;
  var listId = e.parameter.listId;
  var fullTitle = WKR_MARKER + title;
  console.log('Adding task: ' + fullTitle);
  var taskListId = (listId && listId !== '') ? listId : getDefaultTaskListId();
  var task = { title: fullTitle, notes: notes };
  if (due) task.due = due + 'T00:00:00.000Z';
  var created = Tasks.Tasks.insert(task, taskListId);
  console.log('Task created with ID: ' + created.id);
  return respond('OK:' + created.id);
}
function handleDelete(e) {
  var taskId = e.parameter.taskId;
  if (!taskId) return respond('ERROR:no_taskId');
  if (!taskBelongsToUs(taskId)) return respond('ERROR:not_our_task');
  console.log('Deleting task: ' + taskId);
  var taskListId = getDefaultTaskListId();
  Tasks.Tasks.remove(taskListId, taskId);
  return respond('DELETED');
}
function handleComplete(e) {
  var taskId = e.parameter.taskId;
  if (!taskId) return respond('ERROR:no_taskId');
  if (!taskBelongsToUs(taskId)) return respond('ERROR:not_our_task');
  console.log('Completing task: ' + taskId);
  var taskListId = getDefaultTaskListId();
  var task = Tasks.Tasks.get(taskListId, taskId);
  task.status = 'completed';
  task.completed = new Date().toISOString();
  Tasks.Tasks.update(task, taskListId, taskId);
  return respond('COMPLETED');
}
function handleList(e) {
  var lists = Tasks.Tasklists.list();
  var result = lists.items.map(function(item) { return { id: item.id, title: item.title }; });
  return ContentService.createTextOutput(JSON.stringify(result)).setMimeType(ContentService.MimeType.JSON);
}
var CACHED_TASK_LIST_ID = null;
function getDefaultTaskListId() {
  if (CACHED_TASK_LIST_ID) return CACHED_TASK_LIST_ID;
  var lists = Tasks.Tasklists.list();
  CACHED_TASK_LIST_ID = lists.items[0].id;
  return CACHED_TASK_LIST_ID;
}
function respond(text) {
  return ContentService.createTextOutput(text).setMimeType(ContentService.MimeType.TEXT);
}
function taskBelongsToUs(taskId) {
  try {
    var taskListId = getDefaultTaskListId();
    var task = Tasks.Tasks.get(taskListId, taskId);
    return task.title && task.title.startsWith(WKR_MARKER);
  } catch (e) {
    console.log('Error fetching task ' + taskId + ': ' + e.message);
    return false;
  }
}
    """.trimIndent()

        val instructions = """
📋 INSTALLASIE-INSTRUKSIES:

1. Gaan na script.google.com en skep 'n nuwe projek.
2. Vee die standaardkode uit en plak die skrip hierbo in.
3. Klik op "Project Settings" (rat-ikoon) en voeg 'n script property by:
   - Eienskap: SECRET
   - Waarde: Kies 'n geheim (bv. 'MyGeheim123') – onthou dit!
4. Klik op "Services" (+), voeg "Tasks API" by en aktiveer dit.
5. Klik op "Deploy" → "New deployment" → "Web app".
6. Stel "Execute as" op "Me" en "Who has access" op "Anyone".
7. Klik "Deploy" en kopieer die /exec URL.
8. Plak die URL in die "Apps Script-skakel" veld hierbo.
9. Plak dieselfde SECRET in die "Geheime kode" veld hierbo.
10. Stoor die instellings.

Die skrip is nou gereed!
    """.trimIndent()

        val fullText = "$scriptCode\n\n$instructions"
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("WinkerkReader Script", fullText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            requireContext(),
            "Skrip en instruksies is na knipbord gekopieer",
            Toast.LENGTH_LONG
        ).show()
    }
}