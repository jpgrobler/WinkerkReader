package za.co.jpsoft.winkerkreader.ui.activities

import za.co.jpsoft.winkerkreader.ui.adapters.CallLogAdapter
import za.co.jpsoft.winkerkreader.utils.CallLogExporter
import za.co.jpsoft.winkerkreader.utils.PhoneCallMonitor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.DatabaseHelper
import za.co.jpsoft.winkerkreader.databinding.ActivityCallLogBinding
import za.co.jpsoft.winkerkreader.utils.UnifiedCallMonitor

class CallLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallLogBinding
    private lateinit var callLogAdapter: CallLogAdapter
    private lateinit var databaseHelper: DatabaseHelper
    private var currentCallLogs: List<za.co.jpsoft.winkerkreader.data.models.CallLog> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Oproeplog"
        }

        binding.clearButton.setOnClickListener { showClearLogsDialog() }
        databaseHelper = DatabaseHelper.getInstance(this)

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
        callLogAdapter = CallLogAdapter(emptyList())
        binding.recyclerView.adapter = callLogAdapter
    }

    private fun loadCallLogs() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val logs = databaseHelper.getAllCallLogs()
                currentCallLogs = logs
                withContext(Dispatchers.Main) {
                    callLogAdapter.updateLogs(logs)
                }
            } catch (e: Exception) {
                Log.e("CallLogActivity", "Error loading call logs", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CallLogActivity, "Error loading call logs", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportToCSV() {
        if (currentCallLogs.isEmpty()) {
            Toast.makeText(this, "No call logs to export", Toast.LENGTH_SHORT).show()
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
                            "Failed to export call logs",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    Toast.makeText(
                        this@CallLogActivity,
                        "Error exporting: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun shareCSV() {
        if (currentCallLogs.isEmpty()) {
            Toast.makeText(this, "No call logs to share", Toast.LENGTH_SHORT).show()
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
                                "Failed to share call logs",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@CallLogActivity,
                            "Failed to generate CSV for sharing",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    Toast.makeText(
                        this@CallLogActivity,
                        "Error sharing: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showExportSuccessDialog(fileUri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Export Successful")
            .setMessage("Call logs have been exported successfully.\n\nWould you like to share the file?")
            .setPositiveButton("Share") { _, _ ->
                CallLogExporter.shareCSV(this, fileUri)
            }
            .setNegativeButton("Close", null)
            .setNeutralButton("View File") { _, _ ->
                // Open the file in a file manager
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, "text/csv")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Open CSV file"))
                } catch (e: Exception) {
                    Toast.makeText(this, "No app found to open CSV files", Toast.LENGTH_SHORT).show()
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
                    val success = databaseHelper.clearAllCallLogs()
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(this@CallLogActivity, "All logs cleared", Toast.LENGTH_SHORT).show()
                            loadCallLogs()
                        } else {
                            Toast.makeText(this@CallLogActivity, "Failed to clear logs", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Kanselleer", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun showProgress(show: Boolean) {
        binding.progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun observeCallLogUpdates() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                UnifiedCallMonitor.getInstance(this@CallLogActivity).callLogUpdates.collect {
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