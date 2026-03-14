package za.co.jpsoft.winkerkreader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import za.co.jpsoft.winkerkreader.data.AppUpdateManager
import za.co.jpsoft.winkerkreader.data.CheckUpdateWorker
import za.co.jpsoft.winkerkreader.data.DownloadUpdateWorker
import java.io.File

/**
 * Activity for checking, downloading, and installing app updates
 * Uses WorkManager for background operations
 */
class UpdateActivity : AppCompatActivity() {

    private lateinit var updateManager: AppUpdateManager
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnDownloadUpdate: Button
    private lateinit var btnInstallUpdate: Button
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private var downloadedFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)

        initializeViews()
        updateManager = AppUpdateManager(this)
        setupClickListeners()
        requestPermissions()
        setInitialState()
    }

    private fun initializeViews() {
        btnCheckUpdate = findViewById(R.id.btn_check_update)
        btnDownloadUpdate = findViewById(R.id.btn_download_update)
        btnInstallUpdate = findViewById(R.id.btn_install_update)
        tvStatus = findViewById(R.id.tv_status)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupClickListeners() {
        btnCheckUpdate.setOnClickListener { checkForUpdate() }
        btnDownloadUpdate.setOnClickListener { downloadUpdate() }
        btnInstallUpdate.setOnClickListener { installUpdate() }
    }

    private fun setInitialState() {
        btnDownloadUpdate.isEnabled = false
        btnInstallUpdate.isEnabled = false
        progressBar.visibility = View.GONE
    }

    private fun requestPermissions() {
        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }

        // Request install packages permission (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                showInstallPermissionDialog()
            }
        }
    }

    private fun showInstallPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs permission to install updates. Please enable it in settings.")
            .setPositiveButton("OK") { _, _ ->
                // In production, you would launch the settings screen:
                // Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                // intent.setData(Uri.parse("package:" + getPackageName()));
                // startActivity(intent);
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkForUpdate() {
        tvStatus.text = "Toets vir opdatering..."
        progressBar.visibility = View.VISIBLE
        btnCheckUpdate.isEnabled = false

        updateManager.checkForUpdate().observe(this) { workInfo ->
            workInfo?.let {
                when (it.state) {
                    WorkInfo.State.SUCCEEDED -> handleUpdateCheckSuccess(it)
                    WorkInfo.State.FAILED -> handleUpdateCheckFailure(it)
                    WorkInfo.State.RUNNING -> tvStatus.text = "Toets vir opdatering..."
                    else -> {}
                }
            }
        }
    }

    private fun handleUpdateCheckSuccess(workInfo: WorkInfo) {
        progressBar.visibility = View.GONE
        btnCheckUpdate.isEnabled = true

        val updateAvailable = workInfo.outputData.getBoolean(CheckUpdateWorker.KEY_UPDATE_AVAILABLE, false)
        val currentVersion = workInfo.outputData.getString(CheckUpdateWorker.KEY_CURRENT_VERSION)
        val serverVersion = workInfo.outputData.getString(CheckUpdateWorker.KEY_SERVER_VERSION)

        if (updateAvailable) {
            tvStatus.text = "Opdatering beskikbaar!\nCurrent: $currentVersion\nAvailable: $serverVersion"
            btnDownloadUpdate.isEnabled = true

            showUpdateDialog(currentVersion, serverVersion)
        } else {
            tvStatus.text = "Jou app is opdatum! (v$currentVersion)"
            btnDownloadUpdate.isEnabled = false
            Toast.makeText(this, "Geen opdaterings beskikbaar nie", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleUpdateCheckFailure(workInfo: WorkInfo) {
        progressBar.visibility = View.GONE
        btnCheckUpdate.isEnabled = true

        val errorMessage = workInfo.outputData.getString(CheckUpdateWorker.KEY_ERROR_MESSAGE)

        tvStatus.text = if (!errorMessage.isNullOrEmpty()) {
            "Fout het voorgekom:\n$errorMessage"
        } else {
            "Probeer weer asb"
        }

        Log.e(TAG, "Update check failed: $errorMessage")
        Toast.makeText(this, "Kon nie vir opdatering toets nie", Toast.LENGTH_SHORT).show()
    }

    private fun showUpdateDialog(currentVersion: String?, serverVersion: String?) {
        AlertDialog.Builder(this)
            .setTitle("Opdatering beskikaar")
            .setMessage(
                "'n Nuwe weergaweis beskikbaar'!\n\n" +
                        "Huidige: $currentVersion\n" +
                        "Nuwe: $serverVersion\n\n" +
                        "Kan ons dit nou aflaai?"
            )
            .setPositiveButton("Aflaai") { _, _ -> downloadUpdate() }
            .setNegativeButton("Later", null)
            .setCancelable(true)
            .show()
    }

    private fun downloadUpdate() {
        tvStatus.text = "Downloading update..."
        progressBar.visibility = View.VISIBLE
        btnDownloadUpdate.isEnabled = false

        updateManager.downloadUpdate().observe(this) { workInfo ->
            workInfo?.let {
                when (it.state) {
                    WorkInfo.State.SUCCEEDED -> handleDownloadSuccess(it)
                    WorkInfo.State.FAILED -> handleDownloadFailure(it)
                    WorkInfo.State.RUNNING -> tvStatus.text = "Besig om af te laai..."
                    else -> {}
                }
            }
        }
    }

    private fun handleDownloadSuccess(workInfo: WorkInfo) {
        progressBar.visibility = View.GONE

        downloadedFilePath = workInfo.outputData.getString(DownloadUpdateWorker.KEY_FILE_PATH)

        if (!downloadedFilePath.isNullOrEmpty()) {
            tvStatus.text = "Aflaai suksesvol. Reg om te installeer."
            btnInstallUpdate.isEnabled = true
            btnDownloadUpdate.isEnabled = false

            Toast.makeText(this, "Aflaai was suksesvol", Toast.LENGTH_SHORT).show()
            showInstallDialog()
        } else {
            tvStatus.text = "Aflaai het voltooi, file is weg."
            btnDownloadUpdate.isEnabled = true
            Log.e(TAG, "Downloaded file path is null or empty")
        }
    }

    private fun handleDownloadFailure(workInfo: WorkInfo) {
        progressBar.visibility = View.GONE
        btnDownloadUpdate.isEnabled = true

        val errorMessage = workInfo.outputData.getString(DownloadUpdateWorker.KEY_ERROR_MESSAGE)

        tvStatus.text = if (!errorMessage.isNullOrEmpty()) {
            "Aflaai onsuksesvol:\n$errorMessage"
        } else {
            "Aflaai onsuksesvol. Probeer weer."
        }

        Log.e(TAG, "Download failed: $errorMessage")
        Toast.makeText(this, "Aflaai onsuksesvol.", Toast.LENGTH_SHORT).show()
    }

    private fun showInstallDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reg om te installeer")
            .setMessage("Opdatering is suksesvol afgalaai.\n\nInstalleer nou?")
            .setPositiveButton("Installeer") { _, _ -> installUpdate() }
            .setNegativeButton("Later", null)
            .setCancelable(true)
            .show()
    }

    private fun installUpdate() {
        if (downloadedFilePath.isNullOrEmpty()) {
            Toast.makeText(this, "Geen opdatering beskikbaar", Toast.LENGTH_SHORT).show()
            return
        }

        val apkFile = File(downloadedFilePath!!)
        if (!apkFile.exists()) {
            Toast.makeText(this, "Opdatering nie gevind", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "APK bestaan nie: $downloadedFilePath")
            return
        }

        // Install the update
        updateManager.installUpdate(apkFile)

        // Provide feedback
        Toast.makeText(this, "Opening installer...", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted")
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "Notification permission denied")
                Toast.makeText(
                    this,
                    "Notification permission denied. You won't see download progress.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up old update files when activity is destroyed
        if (::updateManager.isInitialized) {
            updateManager.cleanupOldUpdates()
        }
    }

    override fun onBackPressed() {
        // Allow user to go back even during operations
        super.onBackPressed()
    }

    companion object {
        private const val TAG = "UpdateActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
        private const val REQUEST_INSTALL_PACKAGES_PERMISSION = 1002
    }
}