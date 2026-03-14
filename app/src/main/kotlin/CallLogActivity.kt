// CallLogActivity.kt
package za.co.jpsoft.winkerkreader

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CallLogActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var callLogAdapter: CallLogAdapter
    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_log)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Oproeplog"
        }

        val clearButton = findViewById<Button>(R.id.clearButton)
        clearButton.setOnClickListener { showClearLogsDialog() }

        recyclerView = findViewById(R.id.recyclerView)
        databaseHelper = DatabaseHelper(this)

        setupRecyclerView()
        loadCallLogs()
    }

    private fun setupRecyclerView() {
        callLogAdapter = CallLogAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = callLogAdapter
    }

    private fun loadCallLogs() {
        val logs = databaseHelper.getAllCallLogs()
        callLogAdapter.updateLogs(logs)
    }

    private fun showClearLogsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Wis Oproeplog uit")
            .setMessage("Is jy seker jy wil al die oproepinligting uitvee?\n Dit kan nie omgekeer word nie!")
            .setPositiveButton("Wis uit") { _, _ ->
                val success = databaseHelper.clearAllCallLogs()
                if (success) {
                    Toast.makeText(this, "All logs cleared", Toast.LENGTH_SHORT).show()
                    loadCallLogs()
                } else {
                    Toast.makeText(this, "Failed to clear logs", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Kanselleer", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        loadCallLogs()
    }
}