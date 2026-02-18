package za.co.jpsoft.winkerkreader;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.WorkInfo;

import za.co.jpsoft.winkerkreader.data.AppUpdateManager;
import za.co.jpsoft.winkerkreader.data.CheckUpdateWorker;
import za.co.jpsoft.winkerkreader.data.DownloadUpdateWorker;

import java.io.File;

/**
 * Activity for checking, downloading, and installing app updates
 * Uses WorkManager for background operations
 */
public class UpdateActivity extends AppCompatActivity {
    private static final String TAG = "UpdateActivity";
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private static final int REQUEST_INSTALL_PACKAGES_PERMISSION = 1002;

    private AppUpdateManager updateManager;
    private Button btnCheckUpdate;
    private Button btnDownloadUpdate;
    private Button btnInstallUpdate;
    private TextView tvStatus;
    private ProgressBar progressBar;

    private String downloadedFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);

        // Initialize views
        initializeViews();

        // Initialize update manager
        updateManager = new AppUpdateManager(this);

        // Set click listeners
        setupClickListeners();

        // Request necessary permissions
        requestPermissions();

        // Initial state
        setInitialState();
    }

    private void initializeViews() {
        btnCheckUpdate = findViewById(R.id.btn_check_update);
        btnDownloadUpdate = findViewById(R.id.btn_download_update);
        btnInstallUpdate = findViewById(R.id.btn_install_update);
        tvStatus = findViewById(R.id.tv_status);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void setupClickListeners() {
        btnCheckUpdate.setOnClickListener(v -> checkForUpdate());
        btnDownloadUpdate.setOnClickListener(v -> downloadUpdate());
        btnInstallUpdate.setOnClickListener(v -> installUpdate());
    }

    private void setInitialState() {
        btnDownloadUpdate.setEnabled(false);
        btnInstallUpdate.setEnabled(false);
        progressBar.setVisibility(View.GONE);
    }

    private void requestPermissions() {
        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
            }
        }

        // Request install packages permission (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                showInstallPermissionDialog();
            }
        }
    }

    private void showInstallPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("This app needs permission to install updates. Please enable it in settings.")
                .setPositiveButton("OK", (dialog, which) -> {
                    // In production, you would launch the settings screen:
                    // Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    // intent.setData(Uri.parse("package:" + getPackageName()));
                    // startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void checkForUpdate() {
        tvStatus.setText("Toets vir opdatering...");
        progressBar.setVisibility(View.VISIBLE);
        btnCheckUpdate.setEnabled(false);

        updateManager.checkForUpdate().observe(this, workInfo -> {
            if (workInfo != null) {
                WorkInfo.State state = workInfo.getState();

                if (state == WorkInfo.State.SUCCEEDED) {
                    handleUpdateCheckSuccess(workInfo);
                } else if (state == WorkInfo.State.FAILED) {
                    handleUpdateCheckFailure(workInfo);
                } else if (state == WorkInfo.State.RUNNING) {
                    tvStatus.setText("Toets vir opdatering...");
                }
            }
        });
    }

    private void handleUpdateCheckSuccess(WorkInfo workInfo) {
        progressBar.setVisibility(View.GONE);
        btnCheckUpdate.setEnabled(true);

        boolean updateAvailable = workInfo.getOutputData()
                .getBoolean(CheckUpdateWorker.KEY_UPDATE_AVAILABLE, false);
        String currentVersion = workInfo.getOutputData()
                .getString(CheckUpdateWorker.KEY_CURRENT_VERSION);
        String serverVersion = workInfo.getOutputData()
                .getString(CheckUpdateWorker.KEY_SERVER_VERSION);

        if (updateAvailable) {
            tvStatus.setText("Opdatering beskikbaar!\nCurrent: " + currentVersion +
                    "\nAvailable: " + serverVersion);
            btnDownloadUpdate.setEnabled(true);

            showUpdateDialog(currentVersion, serverVersion);
        } else {
            tvStatus.setText("Jou app is opdatum! (v" + currentVersion + ")");
            btnDownloadUpdate.setEnabled(false);
            Toast.makeText(this, "Geen opdaterings beskikbaar nie", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleUpdateCheckFailure(WorkInfo workInfo) {
        progressBar.setVisibility(View.GONE);
        btnCheckUpdate.setEnabled(true);

        String errorMessage = workInfo.getOutputData()
                .getString(CheckUpdateWorker.KEY_ERROR_MESSAGE);

        if (errorMessage != null && !errorMessage.isEmpty()) {
            tvStatus.setText("Fout het voorgekom:\n" + errorMessage);
        } else {
            tvStatus.setText("Probeer weer asb");
        }

        Log.e(TAG, "Update check failed: " + errorMessage);
        Toast.makeText(this, "Kon nie vir opdatering toets nie", Toast.LENGTH_SHORT).show();
    }

    private void showUpdateDialog(String currentVersion, String serverVersion) {
        new AlertDialog.Builder(this)
                .setTitle("Opdatering beskikaar")
                .setMessage("'n Nuwe weergaweis beskikbaar'!\n\n" +
                        "Huidige: " + currentVersion + "\n" +
                        "Nuwe: " + serverVersion + "\n\n" +
                        "Kan ons dit nou aflaai?")
                .setPositiveButton("Aflaai", (dialog, which) -> downloadUpdate())
                .setNegativeButton("Later", null)
                .setCancelable(true)
                .show();
    }

    private void downloadUpdate() {
        tvStatus.setText("Downloading update...");
        progressBar.setVisibility(View.VISIBLE);
        btnDownloadUpdate.setEnabled(false);

        updateManager.downloadUpdate().observe(this, workInfo -> {
            if (workInfo != null) {
                WorkInfo.State state = workInfo.getState();

                if (state == WorkInfo.State.SUCCEEDED) {
                    handleDownloadSuccess(workInfo);
                } else if (state == WorkInfo.State.FAILED) {
                    handleDownloadFailure(workInfo);
                } else if (state == WorkInfo.State.RUNNING) {
                    tvStatus.setText("Besig om af te laai...");
                }
            }
        });
    }

    private void handleDownloadSuccess(WorkInfo workInfo) {
        progressBar.setVisibility(View.GONE);

        downloadedFilePath = workInfo.getOutputData()
                .getString(DownloadUpdateWorker.KEY_FILE_PATH);

        if (downloadedFilePath != null && !downloadedFilePath.isEmpty()) {
            tvStatus.setText("Aflaai suksesvol. Reg om te installeer.");
            btnInstallUpdate.setEnabled(true);
            btnDownloadUpdate.setEnabled(false);

            Toast.makeText(this, "Aflaai was suksesvol", Toast.LENGTH_SHORT).show();

            // Show install dialog
            showInstallDialog();
        } else {
            tvStatus.setText("Aflaai het voltooi, file is weg.");
            btnDownloadUpdate.setEnabled(true);
            Log.e(TAG, "Downloaded file path is null or empty");
        }
    }

    private void handleDownloadFailure(WorkInfo workInfo) {
        progressBar.setVisibility(View.GONE);
        btnDownloadUpdate.setEnabled(true);

        String errorMessage = workInfo.getOutputData()
                .getString(DownloadUpdateWorker.KEY_ERROR_MESSAGE);

        if (errorMessage != null && !errorMessage.isEmpty()) {
            tvStatus.setText("Aflaai onsuksesvol:\n" + errorMessage);
        } else {
            tvStatus.setText("Aflaai onsuksesvol. Probeer weer.");
        }

        Log.e(TAG, "Download failed: " + errorMessage);
        Toast.makeText(this, "Aflaai onsuksesvol.", Toast.LENGTH_SHORT).show();
    }

    private void showInstallDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reg om te installeer")
                .setMessage("Opdatering is suksesvol afgalaai.\n\nInstalleer nou?")
                .setPositiveButton("Installeer", (dialog, which) -> installUpdate())
                .setNegativeButton("Later", null)
                .setCancelable(true)
                .show();
    }

    private void installUpdate() {
        if (downloadedFilePath == null || downloadedFilePath.isEmpty()) {
            Toast.makeText(this, "Geen opdatering beskikbaar", Toast.LENGTH_SHORT).show();
            return;
        }

        File apkFile = new File(downloadedFilePath);
        if (!apkFile.exists()) {
            Toast.makeText(this, "Opdatering nie gevind", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "APK bestaan nie: " + downloadedFilePath);
            return;
        }

        // Install the update
        updateManager.installUpdate(apkFile);

        // Provide feedback
        Toast.makeText(this, "Opening installer...", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted");
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "Notification permission denied");
                Toast.makeText(this,
                        "Notification permission denied. You won't see download progress.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up old update files when activity is destroyed
        if (updateManager != null) {
            updateManager.cleanupOldUpdates();
        }
    }

    @Override
    public void onBackPressed() {
        // Allow user to go back even during operations
        super.onBackPressed();
    }
}