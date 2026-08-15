package za.co.jpsoft.winkerkreader.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.R.string.menu_call_log
import za.co.jpsoft.winkerkreader.data.calllog.dao.CallLogDao
import za.co.jpsoft.winkerkreader.data.calllog.models.CallLog
import za.co.jpsoft.winkerkreader.data.calllog.setup.CallLogDatabase
import za.co.jpsoft.winkerkreader.databinding.ActivityCallLogBinding
import za.co.jpsoft.winkerkreader.ui.adapters.CallLogAdapter
import za.co.jpsoft.winkerkreader.utils.telephony.CallLogExporter
import za.co.jpsoft.winkerkreader.utils.telephony.UnifiedCallMonitor

@AndroidEntryPoint
class CallLogActivity : AuthBaseActivity() {

    @Inject
    lateinit var unifiedCallMonitor: UnifiedCallMonitor

    private lateinit var binding: ActivityCallLogBinding
    private lateinit var callLogAdapter: CallLogAdapter
    private lateinit var callLogDao: CallLogDao   // was: private lateinit var databaseHelper: DatabaseHelper
    private var currentCallLogs: List<CallLog> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(menu_call_log)
        }

        binding.clearButton.setOnClickListener { showClearLogsDialog() }
        callLogDao = CallLogDatabase.getInstance(this).callLogDao()

        setupRecyclerView()
        loadCallLogs()
        observeCallLogUpdates()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_call_log, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            R.id.action_export_csv -> {
                exportToCSV()
                true
            }

            R.id.action_share_csv -> {
                shareCSV()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.setHasFixedSize(true) // 👈 Prevents jank
        callLogAdapter = CallLogAdapter(emptyList())
        binding.recyclerView.adapter = callLogAdapter
    }

    private fun loadCallLogs() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val logs = callLogDao.getAll().map { it.toDisplayModel() }
                currentCallLogs = logs
                withContext(Dispatchers.Main) {
                    callLogAdapter.updateLogs(logs)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("CallLogActivity", "Error loading call logs", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CallLogActivity,
                        R.string.all_logs_cleared,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun za.co.jpsoft.winkerkreader.data.calllog.entities.CallLogEntity.toDisplayModel() =
        CallLog(
        id = id,
        callerInfo = callerInfo,
        timestamp = timestamp,
        formattedDateTime = dateTime,
        callType = callType.name,
        source = source,
        duration = duration
    )

    private fun exportToCSV() {
        if (currentCallLogs.isEmpty()) {
            Toast.makeText(this, R.string.no_logs_to_export, Toast.LENGTH_SHORT).show()
            return
        }

        showProgress(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileUri = CallLogExporter.exportToCSV(this@CallLogActivity, currentCallLogs)

                withContext(Dispatchers.Main) {
                    showProgress(false)
                    if (fileUri != null) {
                        showExportSuccessDialog(fileUri)
                    } else {
                        Toast.makeText(
                            this@CallLogActivity,
                            R.string.failed_to_clear_logs,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    Toast.makeText(
                        this@CallLogActivity,
                        getString(R.string.error_exporting, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun shareCSV() {
        if (currentCallLogs.isEmpty()) {
            Toast.makeText(this, R.string.no_logs_to_export, Toast.LENGTH_SHORT).show()
            return
        }

        showProgress(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileUri = CallLogExporter.exportToCSV(this@CallLogActivity, currentCallLogs)

                withContext(Dispatchers.Main) {
                    showProgress(false)
                    if (fileUri != null) {
                        val shared = CallLogExporter.shareCSV(this@CallLogActivity, fileUri)
                        if (!shared) {
                            Toast.makeText(
                                this@CallLogActivity,
                                R.string.failed_to_clear_logs,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@CallLogActivity,
                            R.string.failed_to_clear_logs,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    Toast.makeText(
                        this@CallLogActivity,
                        getString(R.string.error_exporting, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showExportSuccessDialog(fileUri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.export_successful)
            .setMessage(R.string.export_success_msg)
            .setPositiveButton(R.string.share) { _, _ ->
                CallLogExporter.shareCSV(this, fileUri)
            }
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.view_file) { _, _ ->
                // Open the file in a file manager
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, "text/csv")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.open_csv_file)))
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.no_csv_app, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showClearLogsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Wis Oproeplog uit")
            .setMessage("Is jy seker jy wil al die oproepinligting uitvee?\n Dit kan nie omgekeer word nie!")
            .setPositiveButton("Wis uit") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val success = callLogDao.clearAll() >= 0
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(
                                this@CallLogActivity,
                                R.string.all_logs_cleared,
                                Toast.LENGTH_SHORT
                            ).show()
                            loadCallLogs()
                        } else {
                            Toast.makeText(
                                this@CallLogActivity,
                                R.string.failed_to_clear_logs,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton("Kanselleer", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun showProgress(show: Boolean) {
        binding.progressBar.visibility =
            if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun observeCallLogUpdates() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                unifiedCallMonitor.callLogUpdates.collect {
                    loadCallLogs()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload when returning to the screen
        loadCallLogs()
    }
}