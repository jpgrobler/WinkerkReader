package za.co.jpsoft.winkerkreader;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class CallLogActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private CallLogAdapter callLogAdapter;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_log);

        // Enable back button in action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Oproeplog");
        }
    // Bind clear button
        Button clearButton = findViewById(R.id.clearButton);
        clearButton.setOnClickListener(v -> showClearLogsDialog());

        // Initialize views
        recyclerView = findViewById(R.id.recyclerView);
        databaseHelper = new DatabaseHelper(this);

        // Setup RecyclerView
        setupRecyclerView();

        // Load call logs
        loadCallLogs();
    }
    private void showClearLogsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Wis Oproeplog uit")
                .setMessage("Is jy seker jy wil a die oproepinligting uitvee?\n Dit kan nie omgekeer word nie!")
                .setPositiveButton("Wis uit", (dialog, which) -> {
                    boolean success = databaseHelper.clearAllCallLogs();
                    if (success) {
                        Toast.makeText(this, "All logs cleared", Toast.LENGTH_SHORT).show();
                        loadCallLogs(); // Refresh UI
                    } else {
                        Toast.makeText(this, "Failed to clear logs", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Kanselleer", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
    private void setupRecyclerView() {
        callLogAdapter = new CallLogAdapter(new java.util.ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(callLogAdapter);
    }

    private void loadCallLogs() {
        List<CallLog> logs = databaseHelper.getAllCallLogs();
        callLogAdapter.updateLogs(logs);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCallLogs(); // Refresh when returning to activity
    }
}