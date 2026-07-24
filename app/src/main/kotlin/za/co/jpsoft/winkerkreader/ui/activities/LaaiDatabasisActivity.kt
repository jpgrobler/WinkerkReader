package za.co.jpsoft.winkerkreader.ui.activities

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.sqlite.SQLiteDatabase
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WINKERK_DB
import za.co.jpsoft.winkerkreader.data.WinkerkDbHelper
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase
import za.co.jpsoft.winkerkreader.databinding.LaaidatabasisBinding
import za.co.jpsoft.winkerkreader.utils.MainNavigationController
import za.co.jpsoft.winkerkreader.utils.PastoralDatabaseBackup
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.widget.WidgetDataRepository
import za.co.jpsoft.winkerkreader.workers.FileDownloadWorker
import za.co.jpsoft.winkerkreader.workers.FileDownloadWorkerOld
import za.co.jpsoft.winkerkreader.workers.PhotoDownloadWorker
import za.co.jpsoft.winkerkreader.workers.PhotoDownloadWorkerOld
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.regex.Pattern

class LaaiDatabasisActivity : BaseActivity() {

    companion object {
        private const val TAG = "LaaiDatabasisActivity"
        const val DB_NAME = WINKERK_DB
        const val EXTRA_PROMPT_RESTORE = "pastoral_prompt_restore"
        private val CURRENT_PASTORAL_SCHEMA_VERSION
            get() = PastoralDatabaseBackup.CURRENT_PASTORAL_SCHEMA_VERSION
        private var privateDownloadFile: File? = null

        fun isDownloadManagerAvailable(): Boolean = true

        fun checkIPv4(s: String): Boolean {
            val reg0To255 = "(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])"
            val regex = "$reg0To255\\.$reg0To255\\.$reg0To255\\.$reg0To255"
            val p = Pattern.compile(regex)
            val m = p.matcher(s)
            return m.matches()
        }
    }

    private var isDownloadReceiverRegistered = false
    private lateinit var settings: SharedPreferences
    private lateinit var settingsManager: SettingsManager
    private val navigationController by lazy { MainNavigationController(this) }

    private var currentWorkInfoLiveData: LiveData<WorkInfo?>? = null
    private var workInfoObserver: Observer<WorkInfo?> = Observer { }

    private lateinit var binding: LaaidatabasisBinding
    private var AutoDL = false

    private var FlagCancelledUSB = false
    private var FlagCancelledWiFi = false

    private var fileDownloadWorkId: UUID? = null
    private var recieverDownloadComplete: BroadcastReceiver? = null
    private var myDownloadReference: Long = 0L
    private var SERVER_IP: String = ""
    private var SERVER_PORT: Int = 49514
    private var fileList: ArrayList<HashMap<String, String>> = ArrayList()
    private var delete: Boolean = false
    private var syncPhotosAfterDb: Boolean = false
    private var fromMenu: Boolean = true
    private var pcProtocolVersion: String = "v2"   // default = old protocol

    // Polling
    private val pollingHandler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    private var isPolling = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) Log.d("LaaiDatabasis", "Notification permission granted")
        }

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                Toast.makeText(this, "Geen lêer gekies nie", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            lifecycleScope.launch {
                binding.laaiBoodskap.text = "Besig om databasis te laai..."
                binding.laaiIndeterminateBar.visibility = View.VISIBLE
                val success = importDatabaseFromUri(uri)
                binding.laaiIndeterminateBar.visibility = View.GONE
                if (success) {
                    Toast.makeText(
                        this@LaaiDatabasisActivity,
                        "Databasis suksesvol gelaai",
                        Toast.LENGTH_SHORT
                    ).show()
                    reloadDatabaseAndFinish()
                } else {
                    Toast.makeText(
                        this@LaaiDatabasisActivity,
                        "Kon nie databasis laai nie",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.laaiBoodskap.text = "Laai misluk"
                }
            }
        }

    // ================================================================
    // COMMON DATABASE IMPORT HELPERS (ALL IO ON BACKGROUND)
    // ================================================================

    /**
     * Imports a database from a local file.
     * All IO runs on Dispatchers.IO.
     */
    private suspend fun importDatabaseFromFile(
        sourceFile: File,
        deleteSource: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) {
            withContext(Dispatchers.Main) { showError("Lêer nie gevind nie") }
            return@withContext false
        }

        // 1. Close all DB connections (on main for safe UI updates)
        withContext(Dispatchers.Main) {
            WinkerkDbHelper.closeAllInstances()
            WinkerkDatabase.closeInstance()
        }

        // 2. Ensure databases directory exists
        val dbPath = File(applicationInfo.dataDir, "databases")
        if (!dbPath.exists() && !dbPath.mkdirs()) {
            withContext(Dispatchers.Main) { showError("Kon nie databasisgids skep nie") }
            return@withContext false
        }

        // 3. Write to temp file
        val tempFile = File(dbPath, "Winkerk.db.new")
        tempFile.delete()
        try {
            sourceFile.inputStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to copy to temp file", e)
            withContext(Dispatchers.Main) {
                showError("Kon nie databasis kopieer nie: ${e.message}")
            }
            tempFile.delete()
            return@withContext false
        }

        // 4. Validate, migrate, verify, replace (common logic)
        val success = processTempDatabase(tempFile)   // processTempDatabase also runs on IO

        // 5. Delete source if requested
        if (success && deleteSource) {
            try {
                sourceFile.delete()
                MediaScannerConnection.scanFile(
                    this@LaaiDatabasisActivity,
                    arrayOf(sourceFile.absolutePath),
                    null,
                    null
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Could not delete source file", e)
            }
        }

        return@withContext success
    }

    /**
     * Imports a database from a content URI (SAF file picker).
     * All IO runs on Dispatchers.IO.
     */
    private suspend fun importDatabaseFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        // 1. Close DB connections
        withContext(Dispatchers.Main) {
            WinkerkDbHelper.closeAllInstances()
            WinkerkDatabase.closeInstance()
        }

        // 2. Ensure databases directory exists
        val dbPath = File(applicationInfo.dataDir, "databases")
        if (!dbPath.exists() && !dbPath.mkdirs()) {
            withContext(Dispatchers.Main) { showError("Kon nie databasisgids skep nie") }
            return@withContext false
        }

        // 3. Write to temp from URI
        val tempFile = File(dbPath, "Winkerk.db.new")
        tempFile.delete()
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            } ?: run {
                withContext(Dispatchers.Main) { showError("Kon nie lêer oopmaak nie") }
                tempFile.delete()
                return@withContext false
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to copy from URI to temp", e)
            withContext(Dispatchers.Main) {
                showError("Kon nie databasis lees nie: ${e.message}")
            }
            tempFile.delete()
            return@withContext false
        }

        return@withContext processTempDatabase(tempFile)
    }

    /**
     * Processes an existing temp database file.
     * All IO runs on Dispatchers.IO.
     */
    private suspend fun processTempDatabase(tempFile: File): Boolean = withContext(Dispatchers.IO) {
        // 1. Validate
        if (!isValidDatabaseFile(tempFile, 5)) {
            withContext(Dispatchers.Main) {
                showError("Aflaailêer is nie 'n geldige databasis nie")
            }
            tempFile.delete()
            return@withContext false
        }

        // 2. Migrate & verify (migrateAndVerifyDatabase runs on IO internally)
        if (!migrateAndVerifyDatabase(tempFile)) {
            withContext(Dispatchers.Main) {
                showError("Databasis is ongeldig – kontak ondersteuning")
            }
            tempFile.delete()
            return@withContext false
        }

        // 3. Close global DB connections immediately before replacing the file.
        //    delay + gc gives SQLite time to release all file handles on older devices.
        withContext(Dispatchers.Main) {
            WinkerkDatabase.closeInstance()
            WinkerkDbHelper.closeInstance(DB_NAME)
        }
        withContext(Dispatchers.IO) {
            delay(200)
            System.gc()
        }

        // 4. Replace active DB
        val dbPath = File(applicationInfo.dataDir, "databases")
        val dbFile = File(dbPath, DB_NAME)
        if (dbFile.exists() && !dbFile.delete()) {
            withContext(Dispatchers.Main) {
                showError("Kon bestaande databasis nie verwyder nie")
            }
            tempFile.delete()
            return@withContext false
        }
        if (!tempFile.renameTo(dbFile)) {
            tempFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()
        }

        // 5. Success – temp is already gone (renamed)
        return@withContext true
    }

    // ================================================================
    // ORIGINAL METHODS (now using helpers)
    // ================================================================

    private fun cancelOngoingDownloads() {
        stopPolling()
        fileDownloadWorkId?.let { workId ->
            WorkManager.getInstance(this).cancelWorkById(workId)
            fileDownloadWorkId = null
        }
        if (myDownloadReference != 0L) {
            getSystemService(DOWNLOAD_SERVICE)?.let {
                (it as DownloadManager).remove(myDownloadReference)
                myDownloadReference = 0L
            }
        }
        try {
            recieverDownloadComplete?.let { unregisterReceiver(it) }
            isDownloadReceiverRegistered = false
            recieverDownloadComplete = null
        } catch (_: Exception) {
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            navigateBackToMain()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun navigateBackToMain() {
        navigationController.navigateToMain()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LaaidatabasisBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.laaiScroll) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }
        settings = getSharedPreferences(WinkerkContract.PREFS_USER_INFO, MODE_PRIVATE)
        settingsManager = SettingsManager.getInstance(this)

        refreshDatabaseDate()

        initializeSettings()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                cancelOngoingDownloads()
                navigateBackToMain()
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.serverIp.setText(settings.getString("IP", ""))
        initializeButtons()
        initializeVersionToggle()
        initializeCollapsibleCards()
        initializeProgressBars()
        initializeDataInfo()
        scanForDatabaseFiles()
        setupFileListUI()
        handleIntentExtras()

        syncPhotosAfterDb = settings.getBoolean("SYNC_PHOTOS", false)
        binding.syncPhotos.isChecked = syncPhotosAfterDb
        binding.syncPhotos.setOnCheckedChangeListener { _, isChecked ->
            settings.edit { putBoolean("SYNC_PHOTOS", isChecked) }
            syncPhotosAfterDb = isChecked
        }

        binding.startPhotoSync.setOnClickListener { startPhotoSync() }

        handleAutomaticDownload()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
        fileDownloadWorkId?.let { workId ->
            WorkManager.getInstance(this).cancelWorkById(workId)
            fileDownloadWorkId = null
        }
        currentWorkInfoLiveData?.removeObserver(workInfoObserver)
        if (isDownloadReceiverRegistered) {
            try {
                recieverDownloadComplete?.let { unregisterReceiver(it) }
                isDownloadReceiverRegistered = false
                recieverDownloadComplete = null
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error unregistering download receiver", e)
            }
        }
    }

    private fun initializeSettings() {
        AutoDL = settings.getBoolean("AUTO_DL", false)
        pcProtocolVersion = settings.getString("PC_PROTOCOL_VERSION", "v2") ?: "v2"
    }

    private fun initializeVersionToggle() {
        val group = binding.pcVersionGroup
        group.check(if (pcProtocolVersion == "v3") R.id.btnV3 else R.id.btnV2)

        group.setOnCheckedChangeListener { _, checkedId ->
            pcProtocolVersion = if (checkedId == R.id.btnV3) "v3" else "v2"
            settings.edit { putString("PC_PROTOCOL_VERSION", pcProtocolVersion) }
        }
    }

    private fun initializeButtons() {
        binding.dbLinkButton.setOnClickListener { handleDropboxDownload() }
        binding.laaiLaai.setOnClickListener { handleLoadDatabase() }
        binding.laaiPicker.setOnClickListener { handlePickFile() }
        binding.laaiSocket.setOnClickListener { handleNetworkTransfer() }
        binding.laaiUSB.setOnClickListener { handleUSBTransfer() }
    }

    private fun startPhotoSync() {
        val forceSync = binding.forceSyncCheck.isChecked

        WorkManager.getInstance(this).cancelAllWorkByTag("photo_sync")

        val ip = settings.getString("IP", "")
        if (ip.isNullOrEmpty()) {
            Toast.makeText(this, "Please set server IP first", Toast.LENGTH_SHORT).show()
            return
        }

        binding.photoSyncProgress.visibility = View.VISIBLE
        binding.photoSyncStatus.visibility = View.VISIBLE
        binding.photoSyncProgress.progress = 0
        binding.photoSyncStatus.setText(R.string.photo_sync_starting)
        binding.startPhotoSync.isEnabled = false

        val inputData = Data.Builder()
            .putString("SERVER_IP", ip)
            .putBoolean("FORCE_SYNC", forceSync)
            .build()

        val workerClass = if (pcProtocolVersion == "v3")
            PhotoDownloadWorker::class.java
        else
            PhotoDownloadWorkerOld::class.java

        val photoWorkRequest = OneTimeWorkRequest.Builder(workerClass)
            .setInputData(inputData)
            .addTag("photo_sync")
            .build()

        WorkManager.getInstance(this).enqueue(photoWorkRequest)

        currentWorkInfoLiveData?.removeObserver(workInfoObserver)
        currentWorkInfoLiveData =
            WorkManager.getInstance(this).getWorkInfoByIdLiveData(photoWorkRequest.id)

        workInfoObserver = Observer { workInfo ->
            if (workInfo != null) {
                if (workInfo.state.isFinished) {
                    binding.photoSyncProgress.visibility = View.GONE
                    binding.startPhotoSync.isEnabled = true

                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        val output = workInfo.outputData
                        val success = output.getInt("SUCCESS_COUNT", 0)
                        val fail = output.getInt("FAIL_COUNT", 0)
                        val message = getString(R.string.photo_sync_done, success, fail)
                        binding.photoSyncStatus.text = message
                        binding.photoSyncStatus.visibility = View.VISIBLE
                        Toast.makeText(this@LaaiDatabasisActivity, message, Toast.LENGTH_LONG)
                            .show()
                    } else if (workInfo.state == WorkInfo.State.FAILED) {
                        binding.photoSyncStatus.setText(R.string.photo_sync_failed_status)
                        binding.photoSyncStatus.visibility = View.VISIBLE
                        Toast.makeText(
                            this@LaaiDatabasisActivity,
                            R.string.photo_sync_failed_toast,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val progress = workInfo.progress
                    val prog = progress.getInt("progress", 0)
                    val tot = progress.getInt("total", 0)
                    val guid = progress.getString("currentGuid")
                    if (tot > 0) {
                        binding.photoSyncProgress.max = tot
                        binding.photoSyncProgress.progress = prog
                        binding.photoSyncStatus.text =
                            getString(R.string.photo_sync_progress, prog, tot, guid ?: "")
                        binding.photoSyncStatus.visibility = View.VISIBLE
                    }
                }
            }
        }
        currentWorkInfoLiveData!!.observe(this, workInfoObserver)

        Toast.makeText(this, "Foto-sinkronisasie begin…", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToMainActivity() {
        settingsManager.defLayout = "VERJAAR"
        val extras = Bundle().apply {
            putString("SENDER_CLASS_NAME", "WysVerjaar")
        }
        navigationController.navigateToMain(extras)
        finish()
    }

    private fun handleDropboxDownload() {
        binding.dbLinkButton.backgroundTintList = ColorStateList.valueOf(
            MaterialColors.getColor(
                binding.dbLinkButton,
                com.google.android.material.R.attr.colorPrimaryContainer,
                0
            )
        )
        val downloadUrl = processDownloadUrl(binding.dbLink.text.toString())
        downloadFromDropBoxUrl(downloadUrl)
        settings.edit { putString("DropBox", downloadUrl) }
        binding.laaiBoodskap.text = getString(R.string.db_dropbox_downloading)
        binding.dbLinkButton.visibility = View.INVISIBLE
        binding.laaiLocal.visibility = View.GONE
    }

    private fun processDownloadUrl(url: String): String {
        return when {
            url.contains("www.dropbox.com") -> url.replace("dl=0", "dl=1")
            url.contains("1drv.ms") -> conv(url)
            url.contains("drive.google.com") -> conv2(url)
            url.contains("sharepoint.com") -> conv3(url)
            else -> url
        }
    }

    private fun handleLoadDatabase() {
        val radioButtonID = binding.laaiFilelist.checkedRadioButtonId
        if (radioButtonID == -1) {
            Toast.makeText(this, "Kies asseblief 'n databasis", Toast.LENGTH_SHORT).show()
            return
        }

        delete = binding.laaiWisuit.isChecked
        val filePath = fileList[radioButtonID]["Path"] ?: return

        lifecycleScope.launch {
            binding.laaiBoodskap.text = "Besig om databasis te laai..."
            binding.laaiIndeterminateBar.visibility = View.VISIBLE
            val success = importDatabaseFromFile(File(filePath), delete)
            binding.laaiIndeterminateBar.visibility = View.GONE
            if (success) {
                Toast.makeText(
                    this@LaaiDatabasisActivity,
                    "Databasis suksesvol gelaai",
                    Toast.LENGTH_SHORT
                ).show()
                reloadDatabaseAndFinish()
            } else {
                Toast.makeText(
                    this@LaaiDatabasisActivity,
                    "Kon nie databasis laai nie",
                    Toast.LENGTH_LONG
                ).show()
                binding.laaiBoodskap.text = "Laai misluk"
            }
            binding.laaiWisuit.isChecked = false
            binding.laaiFilelist.clearCheck()
        }
    }

    private fun resetGemeenteSettings() {
        settingsManager.gemeenteNaam = ""
        settingsManager.gemeenteEpos = ""
        settings.edit {
            putString("Gemeente", "")
            putString("Gemeente_Epos", "")
            putString("DATA_DATUM", "")
        }
    }

    private fun handlePickFile() {
        pickFileLauncher.launch(
            arrayOf(
                "application/octet-stream",
                "application/x-sqlite3",
                "application/vnd.sqlite3"
            )
        )
        binding.laaiPicker.backgroundTintList = ColorStateList.valueOf(
            MaterialColors.getColor(
                binding.laaiPicker,
                com.google.android.material.R.attr.colorPrimaryContainer,
                0
            )
        )
    }

    private fun handleNetworkTransfer() {
        if (FlagCancelledWiFi) {
            fileDownloadWorkId?.let { WorkManager.getInstance(this).cancelWorkById(it) }
            fileDownloadWorkId = null
            binding.laaiSocket.background.clearColorFilter()
            binding.laaiBoodskap.setText(R.string.download_cancelled)
            FlagCancelledWiFi = false
        } else {
            val ipText = binding.serverIp.text.toString()
            if (ipText.isNotEmpty() && checkIPv4(ipText)) {
                binding.laaiSocket.background.clearColorFilter()
                saveIPAddress(ipText)
                SERVER_IP = ipText
                SERVER_PORT = 49514
                startFileDownload(ipText, SERVER_PORT, binding.laaiSocket, isWiFi = true)
                FlagCancelledWiFi = true
            } else {
                binding.laaiBoodskap.setText(R.string.error_invalid_ip)
            }
        }
    }

    private fun handleUSBTransfer() {
        if (FlagCancelledUSB) {
            fileDownloadWorkId?.let { WorkManager.getInstance(this).cancelWorkById(it) }
            fileDownloadWorkId = null
            binding.laaiUSB.background.clearColorFilter()
            binding.serverIp.setText("")
            binding.laaiBoodskap.setText(R.string.download_cancelled)
            FlagCancelledUSB = false
        } else {
            binding.laaiUSB.background.clearColorFilter()
            binding.serverIp.setText("127.0.0.1")
            SERVER_IP = "127.0.0.1"
            SERVER_PORT = 49514
            startFileDownload("127.0.0.1", SERVER_PORT, binding.laaiUSB, isWiFi = false)
            FlagCancelledUSB = true
        }
    }

    private fun startFileDownload(serverIp: String, port: Int, button: Button, isWiFi: Boolean) {
        binding.laaiBoodskap.setText(R.string.download_starting)

        val inputData = Data.Builder()
            .putString(FileDownloadWorker.KEY_SERVER_IP, serverIp)
            .putInt(FileDownloadWorker.KEY_SERVER_PORT, port)
            .build()

        val workerClass = if (pcProtocolVersion == "v3")
            FileDownloadWorker::class.java
        else
            FileDownloadWorkerOld::class.java

        val workRequest = OneTimeWorkRequest.Builder(workerClass)
            .setInputData(inputData)
            .addTag("file_download")
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)
        fileDownloadWorkId = workRequest.id

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(workRequest.id)
            .observe(this) { workInfo ->
                if (workInfo == null) return@observe
                val progress = workInfo.progress.getInt(FileDownloadWorker.KEY_PROGRESS, 0)
                if (progress > 0) {
                    binding.laaiBoodskap.text =
                        getString(R.string.download_received_percent, progress)
                }
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    val tempFilePath =
                        workInfo.outputData.getString(FileDownloadWorker.KEY_FILE_PATH)
                    if (tempFilePath.isNullOrEmpty()) {
                        Toast.makeText(this, "Geen lêerpad ontvang", Toast.LENGTH_LONG).show()
                        if (isWiFi) FlagCancelledWiFi = false else FlagCancelledUSB = false
                        fileDownloadWorkId = null
                        return@observe
                    }
                    val tempFile = File(tempFilePath)
                    if (!tempFile.exists()) {
                        Toast.makeText(this, "Aflaaileer nie gevind nie", Toast.LENGTH_LONG).show()
                        if (isWiFi) FlagCancelledWiFi = false else FlagCancelledUSB = false
                        fileDownloadWorkId = null
                        return@observe
                    }

                    lifecycleScope.launch {
                        try {
                            val success = processTempDatabase(tempFile)   // runs on IO internally
                            if (success) {
                                withContext(Dispatchers.Main) {
                                    binding.laaiBoodskap.setText(R.string.download_completed)
                                    Toast.makeText(
                                        this@LaaiDatabasisActivity,
                                        R.string.db_received_success,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        navigateBackToMain()
                                    }, 1500)
                                }
                            } else {
                                // Error already shown by processTempDatabase
                            }
                        } finally {
                            if (isWiFi) FlagCancelledWiFi = false else FlagCancelledUSB = false
                            fileDownloadWorkId = null
                        }
                    }
                } else if (workInfo.state == WorkInfo.State.FAILED) {
                    binding.laaiBoodskap.setText(R.string.download_failed)
                    Toast.makeText(this, R.string.db_download_failed, Toast.LENGTH_LONG).show()
                    if (isWiFi) FlagCancelledWiFi = false else FlagCancelledUSB = false
                    fileDownloadWorkId = null
                }
            }
    }

    private fun saveIPAddress(ipAddress: String) {
        settings.edit { putString("IP", ipAddress) }
    }

    private fun initializeProgressBars() {
        binding.laaiIndeterminateBar.visibility = View.GONE
        binding.laaiIndeterminateBar2.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        refreshDatabaseDate()
        updateDateDisplay()
    }

    private fun initializeDataInfo() {
        updateDateDisplay()
        val dropBoxUrl = settings.getString("DropBox", "")
        if (!dropBoxUrl.isNullOrEmpty()) {
            binding.dbLink.setText(dropBoxUrl)
        }
    }

    private fun updateDateDisplay() {
        val date = settingsManager.dataDatum
        if (date.isNotEmpty()) {
            binding.datadate.text = getString(R.string.current_data_info, date)
        } else {
            binding.datadate.text = getString(R.string.current_data_info, "Onbekend")
        }
    }

    private fun refreshDatabaseDate() {
        try {
            WinkerkDbHelper.closeInstance(WinkerkContract.winkerkEntry.WINKERK_DB)
            val db = WinkerkDbHelper.getInstance(
                this,
                WinkerkContract.winkerkEntry.WINKERK_DB
            ).readableDatabase
            val cursor = db.rawQuery("SELECT DataDatum FROM Datum", null)
            cursor.use {
                if (it.moveToFirst()) {
                    val dateIdx = it.getColumnIndex("DataDatum")
                    if (dateIdx >= 0) {
                        val date = it.getString(dateIdx) ?: ""
                        settingsManager.dataDatum = date
                        if (BuildConfig.DEBUG) Log.d(TAG, "Database date loaded: $date")
                    }
                } else {
                    settingsManager.dataDatum = ""
                    if (BuildConfig.DEBUG) Log.d(TAG, "No date found in Datum table")
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error reading database date", e)
            settingsManager.dataDatum = ""
        }
    }

    private fun refreshDatabaseDateAsync() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    WinkerkDbHelper.closeInstance(WinkerkContract.winkerkEntry.WINKERK_DB)
                }

                val db = WinkerkDbHelper.getInstance(
                    this@LaaiDatabasisActivity,
                    WinkerkContract.winkerkEntry.WINKERK_DB
                ).readableDatabase

                val cursor = db.rawQuery("SELECT DataDatum FROM Datum", null)
                var date = ""
                cursor.use {
                    if (it.moveToFirst()) {
                        val dateIdx = it.getColumnIndex("DataDatum")
                        if (dateIdx >= 0) {
                            date = it.getString(dateIdx) ?: ""
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    settingsManager.dataDatum = date
                    updateDateDisplay()
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error loading database date", e)
                withContext(Dispatchers.Main) {
                    settingsManager.dataDatum = ""
                    updateDateDisplay()
                }
            }
        }
    }

    private fun scanForDatabaseFiles() {
        try {
            getFileList(winkerkEntry.getWkrDir(this))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(
                "WinkerkReader LaaiDatabasisActivity",
                "Error scanning files: $e"
            )
        }
    }

    private fun setupFileListUI() {
        if (this.fileList.isEmpty()) {
            binding.laaiLaai.visibility = View.GONE
            return
        }
        binding.laaiLaai.visibility = View.VISIBLE
        for (i in this.fileList.indices) {
            addFileRadioButton(binding.laaiFilelist, i)
        }
    }

    private fun addFileRadioButton(fileListGroup: RadioGroup, index: Int) {
        val file = File(this.fileList[index]["Path"])
        val size = (file.length() / 1024 / 1024).toInt().toString()
        val additionalData = getFileAdditionalData(this.fileList[index]["Path"])

        val radioButton = RadioButton(this).apply {
            text =
                "${this@LaaiDatabasisActivity.fileList[index]["Path"]}\n${size} Mb$additionalData"
            id = index
            background = ContextCompat.getDrawable(this@LaaiDatabasisActivity, R.drawable.border2)
            layoutParams = LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT
            )
        }

        fileListGroup.addView(radioButton)
    }

    private fun getFileAdditionalData(filePath: String?): String {
        if (filePath == null) return ""
        return try {
            SQLiteDatabase.openDatabase(
                filePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).use { sqlite ->
                val cursor = sqlite.rawQuery(
                    "SELECT MyCongregationInfo.Name, MyCongregationInfo.Email, Denominations.Abbreviation " +
                            "FROM MyCongregationInfo " +
                            "JOIN Congregations ON (MyCongregationInfo.CongregationGUID = Congregations.CongregationGUID) " +
                            "JOIN Denominations ON (quote(MyCongregationInfo.DenominationGUID) = quote(Denominations.DenominationGUID))",
                    null
                )
                cursor.use {
                    if (cursor.moveToFirst()) {
                        val abbrevIndex = cursor.getColumnIndex("Abbreviation")
                        val nameIndex = cursor.getColumnIndex("Name")
                        if (abbrevIndex >= 0 && nameIndex >= 0) {
                            val abbreviation = cursor.getString(abbrevIndex)
                            val gemeenteNaam = cursor.getString(nameIndex)
                            "\nGemeente: $abbreviation $gemeenteNaam"
                        } else ""
                    } else ""
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(
                "WinkerkReader LaaiDatabasisActivity",
                "Error reading database info: $e"
            )
            ""
        }
    }

    private fun handleIntentExtras() {
        val intentMain = intent
        if (intentMain.extras == null) return

        if (intentMain.getBooleanExtra(EXTRA_PROMPT_RESTORE, false)) {
            openFilePicker()
            return
        }

        val extra = intentMain.getStringExtra("DataBase_Update")
        if (extra.isNullOrEmpty()) return
        processAutomaticDatabaseUpdate(extra)
    }

    private fun processAutomaticDatabaseUpdate(filePath: String) {
        Toast.makeText(this, "WKR - Databasislaai", Toast.LENGTH_SHORT).show()
        val file = File(filePath)
        val fileSizeKB = file.length() / 1024
        val fileSizeMB = fileSizeKB / 1024
        Toast.makeText(this, "WKR - DROPBOX Databasis $fileSizeKB KB", Toast.LENGTH_LONG).show()
        if (fileSizeMB >= 1) {
            Toast.makeText(this, "WKR - Probeer Dropbox databasis laai", Toast.LENGTH_LONG).show()
            lifecycleScope.launch {
                val success = importDatabaseFromFile(file)
                if (success) {
                    Toast.makeText(
                        this@LaaiDatabasisActivity,
                        "Databasis suksesvol gelaai",
                        Toast.LENGTH_SHORT
                    ).show()
                    reloadDatabaseAndFinish()
                } else {
                    Toast.makeText(
                        this@LaaiDatabasisActivity,
                        "Databasis laai misluk",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            Toast.makeText(this, "WKR - Dropbox Databasis te klein", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleAutomaticDownload() {
        if (!fromMenu && AutoDL && binding.dbLink.text.toString() != getString(R.string.dbLink)) {
            settings.edit { putBoolean("FROM_MENU", false) }
            binding.dbLinkButton.performClick()
        }
    }

    private fun getFileList(searchpath: String?): ArrayList<HashMap<String, String>> {
        if (searchpath != null) {
            val home = File(searchpath)
            val listFiles = home.listFiles()
            if (listFiles != null) {
                for (file in listFiles) {
                    if (!file.isDirectory) {
                        addFileToList(file)
                    }
                }
            }
        }
        return fileList
    }

    private fun addFileToList(file: File) {
        if (file.name == WINKERK_DB) {
            val fileMap = HashMap<String, String>()
            fileMap["Title"] = file.name
            fileMap["Path"] = file.path
            fileList.add(fileMap)
        }
    }

    // ================================================================
    // DROPBOX DOWNLOAD WITH SAFE TEMP FILE PROCESSING
    // ================================================================

    private fun downloadFromDropBoxUrl(url: String) {
        if (isFinishing || isDestroyed) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Activity destroyed, ignoring download")
            return
        }

        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)

        // Check external dir
        val externalDir = getExternalFilesDir(null)
        if (externalDir == null) {
            showError("Geen eksterne berging beskikbaar nie")
            return
        }
        privateDownloadFile = File(externalDir, "WinkerkReader_temp.db")
        privateDownloadFile?.parentFile?.mkdirs()

        recieverDownloadComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (BuildConfig.DEBUG) Log.d("Dropbox", "BroadcastReceiver triggered!")
                val manager = getSystemService(DOWNLOAD_SERVICE) as? DownloadManager ?: return
                val reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (myDownloadReference != reference) return

                stopPolling()

                val query = DownloadManager.Query().setFilterById(reference)
                manager.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        showError("Kon nie aflaaistatus lees nie")
                        privateDownloadFile?.delete()
                        privateDownloadFile = null
                        return
                    }
                    val status =
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val reason = try {
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    } catch (_: IllegalArgumentException) {
                        -1
                    }
                    val bytesSoFar =
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val totalBytes =
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    if (BuildConfig.DEBUG) Log.d(
                        "Dropbox",
                        "Receiver: status=$status, reason=$reason, bytes=$bytesSoFar/$totalBytes"
                    )

                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        val errorMsg = when (status) {
                            DownloadManager.STATUS_FAILED -> "Misluk (rede $reason)"
                            DownloadManager.STATUS_PAUSED -> "Gepauseer (rede $reason)"
                            else -> "Onbekende status $status"
                        }
                        showError("Aflaai $errorMsg")
                        privateDownloadFile?.delete()
                        privateDownloadFile = null
                        return
                    }

                    val downloadUri = manager.getUriForDownloadedFile(reference)
                    if (downloadUri == null) {
                        showError("Aflaaileer nie gevind nie")
                        privateDownloadFile?.delete()
                        privateDownloadFile = null
                        return
                    }

                    // Launch coroutine to process the file
                    lifecycleScope.launch {
                        processDownloadedFile(downloadUri)
                    }
                }
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        registerReceiver(recieverDownloadComplete, intentFilter, flags)
        isDownloadReceiverRegistered = true

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setTitle(WINKERK_DB)
            setMimeType("application/vnd.sqlite3")
            setDestinationUri(Uri.fromFile(privateDownloadFile))
            addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        myDownloadReference = manager.enqueue(request)
        if (BuildConfig.DEBUG) Log.d("Dropbox", "Download enqueued with ID: $myDownloadReference")
        if (myDownloadReference < 0) {
            showError("Aflaai kon nie begin nie (ID=$myDownloadReference)")
            return
        }

        startProgressPolling(myDownloadReference)
    }

    /**
     * Process a downloaded file from a given Uri.
     * - Copies to a temp file (Winkerk.db.new) in the databases dir.
     * - Validates, migrates, and verifies with Room on the temp file.
     * - If successful, replaces the active database.
     * - All database operations run on IO thread.
     */
    private suspend fun processDownloadedFile(downloadUri: Uri) {
        if (isFinishing || isDestroyed) return

        withContext(Dispatchers.IO) {
            try {
                val dbPath = applicationInfo.dataDir + "/databases/"
                val dbDir = File(dbPath)
                if (!dbDir.exists() && !dbDir.mkdirs()) {
                    withContext(Dispatchers.Main) { showError("Kon nie databasisgids skep nie") }
                    return@withContext
                }

                val tempFile = File(dbDir, "Winkerk.db.new")
                if (tempFile.exists()) tempFile.delete()

                // Copy downloaded content to temp file
                contentResolver.openInputStream(downloadUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                } ?: run {
                    withContext(Dispatchers.Main) { showError("Kon nie lêer oopmaak nie") }
                    return@withContext
                }

                // Use the common helper (already wrapped in IO)
                val success = processTempDatabase(tempFile)

                // Cleanup and finalize
                withContext(Dispatchers.Main) {
                    privateDownloadFile?.delete()
                    privateDownloadFile = null
                    if (success) {
                        try {
                            recieverDownloadComplete?.let { unregisterReceiver(it) }
                            isDownloadReceiverRegistered = false
                            recieverDownloadComplete = null
                        } catch (_: Exception) {
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            reloadDatabaseAndFinish()
                        }, 300)
                    }
                }

            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Download processing failed", e)
                withContext(Dispatchers.Main) {
                    showError("Fout met verwerking: ${e.message}")
                }
                privateDownloadFile?.delete()
                privateDownloadFile = null
            }
        }
    }

    // ================================================================
    // POLLING
    // ================================================================

    private fun startProgressPolling(downloadId: Long) {
        stopPolling()
        isPolling = true
        val runnable = object : Runnable {
            override fun run() {
                if (!isPolling) return
                checkDownloadStatus(downloadId)

                val query = DownloadManager.Query().setFilterById(downloadId)
                val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                manager.query(query).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val status =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            isPolling = false
                            val uri = manager.getUriForDownloadedFile(downloadId)
                            if (uri != null) {
                                lifecycleScope.launch {
                                    processDownloadedFile(uri)
                                }
                            } else {
                                showError("Kon nie aflaaileer vind nie")
                            }
                            return
                        }
                        if (status == DownloadManager.STATUS_FAILED) {
                            isPolling = false
                            showError("Aflaai misluk")
                            return
                        }
                    } else {
                        isPolling = false
                        return
                    }
                }
                pollingHandler.postDelayed(this, 2000)
            }
        }
        pollingRunnable = runnable
        pollingHandler.post(runnable)
    }

    private fun stopPolling() {
        isPolling = false
        pollingRunnable?.let { pollingHandler.removeCallbacks(it) }
        pollingRunnable = null
    }

    fun checkDownloadStatus(downloadId: Long) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        manager.query(query).use { cursor ->
            if (cursor.moveToFirst()) {
                val status =
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val reason = try {
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                } catch (_: IllegalArgumentException) {
                    -1
                }
                val bytesSoFar =
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val totalBytes =
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val statusText = when (status) {
                    DownloadManager.STATUS_PENDING -> "Pending"
                    DownloadManager.STATUS_RUNNING -> "Running"
                    DownloadManager.STATUS_PAUSED -> "Paused (reason=$reason)"
                    DownloadManager.STATUS_SUCCESSFUL -> "Successful"
                    DownloadManager.STATUS_FAILED -> "Failed (reason=$reason)"
                    else -> "Unknown ($status)"
                }
                if (BuildConfig.DEBUG) Log.d(
                    "Dropbox",
                    "Poll: $statusText, Downloaded: $bytesSoFar/$totalBytes"
                )
            } else {
                if (BuildConfig.DEBUG) Log.e("Dropbox", "No download found with ID $downloadId")
            }
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            binding.laaiBoodskap.text = message
        }
    }

    // ================================================================
    // URL converters
    // ================================================================

    private fun conv2(text: String): String {
        var encodedUrl = text
        encodedUrl = encodedUrl.replace("/view?usp=sharing", "")
        encodedUrl = encodedUrl.replace("/file/d/", "/uc?export=download&id=")
        return encodedUrl
    }

    private fun conv3(text: String): String {
        val lastIndex = text.lastIndexOf("?")
        return if (lastIndex < 0) text else text.substring(0, lastIndex) + "?download=1"
    }

    private fun conv(text: String): String {
        val sharingUrl = text
        val bytes = sharingUrl.toByteArray()
        var base64Value = Base64.encodeToString(bytes, Base64.DEFAULT)
        var encodedUrl = "u!$base64Value".trim()
        encodedUrl = encodedUrl.replace("=".toRegex(), "")
        encodedUrl = encodedUrl.replace('/', '_')
        encodedUrl = encodedUrl.replace('+', '-')
        return "https://api.onedrive.com/v1.0/shares/$encodedUrl/root/content"
    }

    // ================================================================
    // Database reload & migration
    // ================================================================

    private fun reloadDatabaseAndFinish() {
        try {
            contentResolver.call(winkerkEntry.CONTENT_URI, "reloadDatabase", null, null)
            WidgetDataRepository.invalidateCache()
            Handler(Looper.getMainLooper()).postDelayed({
                navigateBackToMain()
            }, 200)
        } catch (e: IllegalStateException) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Room schema error during reload", e)
            val dbFile = File(applicationInfo.dataDir, "databases/$DB_NAME")
            if (dbFile.exists()) dbFile.delete()
            showError("Databasis fout – kontak ondersteuning")
            navigateBackToMain()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error during database reload", e)
            navigateBackToMain()
        }
    }

    // ================================================================
    // MIGRATION & ROOM VERIFICATION
    // ================================================================

    /**
     * Tries to migrate the database (normal + forced) and then verifies with Room.
     * All database operations run on IO.
     */
    private suspend fun migrateAndVerifyDatabase(dbFile: File): Boolean {
        if (!dbFile.exists()) return false

        // 1. Migrate (on IO)
        val migrated = withContext(Dispatchers.IO) {
            var migrated = migrateDownloadedDatabase(dbFile)
            if (!migrated) {
                if (BuildConfig.DEBUG) Log.w(
                    TAG,
                    "Normal migration failed, attempting forced migration"
                )
                migrated = forceMigrateDatabase(dbFile)
            }
            migrated
        }
        if (!migrated) {
            if (BuildConfig.DEBUG) Log.e(TAG, "All migration attempts failed")
            return false
        }

        // 2. Verify with Room (on IO)
        return withContext(Dispatchers.IO) {
            verifyRoomDatabaseOnFile(dbFile)
        }
    }

    /**
     * Normal migration: only runs if version < 4.
     */
    private fun migrateDownloadedDatabase(dbFile: File): Boolean {
        if (!dbFile.exists()) {
            if (BuildConfig.DEBUG) Log.e(TAG, "DB file does not exist")
            return false
        }
        return try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                if (db.version >= 4) {
                    if (BuildConfig.DEBUG) Log.i(
                        TAG,
                        "DB already at v${db.version}, skipping migration"
                    )
                    return@use
                }
                listOf("Members", "Argief", "Datum").forEach { tableName ->
                    migrateTableVarcharToText(db, tableName)
                }
                db.execSQL("PRAGMA user_version = 4")
                if (BuildConfig.DEBUG) Log.i(TAG, "Migration successful on ${dbFile.name}")
            }
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Migration failed on ${dbFile.name}", e)
            false
        }
    }

    /**
     * Force migration: applies VARCHAR→TEXT conversion and stamps version 4,
     * regardless of current version.
     */
    private fun forceMigrateDatabase(dbFile: File): Boolean {
        return try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                .use { db ->
                    listOf("Members", "Argief", "Datum").forEach { tableName ->
                        migrateTableVarcharToText(db, tableName)
                    }
                    db.execSQL("PRAGMA user_version = 4")
                    if (BuildConfig.DEBUG) Log.i(
                        TAG,
                        "Forced migration successful on ${dbFile.name}"
                    )
                    true
                }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Forced migration failed on ${dbFile.name}", e)
            false
        }
    }

    /**
     * Verifies that Room can open the given database file without throwing IllegalStateException.
     * Opens a temporary Room instance and runs a simple query.
     * This should be called on a background thread.
     */
    private fun verifyRoomDatabaseOnFile(dbFile: File): Boolean {
        // This function ONLY manages the local verification instance (dbFile.name = "Winkerk.db.new").
        // The global singleton (Winkerk.db) is managed by processTempDatabase, which closes it
        // explicitly after this function returns and before the rename step.
        var localDb: WinkerkDatabase? = null
        return try {
            localDb = androidx.room.Room.databaseBuilder(
                applicationContext,
                WinkerkDatabase::class.java,
                dbFile.name          // "Winkerk.db.new" – separate from the global singleton
            ).build()
            val count = localDb.memberDao().getCount()
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Room verification passed on ${dbFile.name}, count=$count"
            )
            true
        } catch (e: IllegalStateException) {
            if (BuildConfig.DEBUG) Log.e(
                TAG,
                "Room verification failed with IllegalStateException",
                e
            )
            false
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Room verification failed with other exception", e)
            false
        } finally {
            // Close only the local verification instance – both on success and failure.
            // Do NOT call WinkerkDatabase.closeInstance() here.
            try {
                localDb?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun migrateTableVarcharToText(db: SQLiteDatabase, tableName: String) {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { if (!it.moveToFirst()) return }

        val oldColumns = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info('$tableName')", null).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                oldColumns.add(name)
            }
        }

        if (oldColumns.isEmpty()) return

        val withNotNull = tableName == "Members"

        val newColumnDefs = mutableListOf<String>()
        newColumnDefs.add(
            if (withNotNull) "[_id] INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"
            else "[_id] INTEGER PRIMARY KEY AUTOINCREMENT"
        )
        val otherColumns = oldColumns.filter { it != "_id" }
        otherColumns.forEach { colName ->
            newColumnDefs.add("[$colName] TEXT")
        }

        val temp = "${tableName}_upgrade_new"
        val createTableColumns = newColumnDefs.joinToString(", ")
        db.execSQL("DROP TABLE IF EXISTS $temp")
        db.execSQL("CREATE TABLE $temp ($createTableColumns)")

        val columnsToInsert = if (oldColumns.contains("_id")) {
            oldColumns
        } else {
            otherColumns
        }

        val insertColumns = columnsToInsert.joinToString(", ") { "[$it]" }
        val selectColumns = columnsToInsert.joinToString(", ") { "[$it]" }

        val insertSql = "INSERT INTO $temp ($insertColumns) SELECT $selectColumns FROM $tableName"
        db.execSQL(insertSql)

        db.execSQL("DROP TABLE $tableName")
        db.execSQL("ALTER TABLE $temp RENAME TO $tableName")
    }

    private val pickBackupFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            lifecycleScope.launch {
                val result = PastoralDatabaseBackup.importFromUri(this@LaaiDatabasisActivity, uri)
                handleImportResult(result)
            }
        }

    private fun openFilePicker() {
        pickBackupFile.launch(arrayOf("application/octet-stream", "*/*"))
    }

    private fun handleImportResult(result: PastoralDatabaseBackup.ImportResult) {
        val msg = when (result) {
            is PastoralDatabaseBackup.ImportResult.Success ->
                if (result.migratedFrom < CURRENT_PASTORAL_SCHEMA_VERSION)
                    "Rugsteun herstel en opgradeer van v${result.migratedFrom}"
                else
                    "Rugsteun suksesvol herstel"

            is PastoralDatabaseBackup.ImportResult.TooNew ->
                "Lêer is van 'n nuwer weergawe (v${result.backupVersion}). Dateer die app op."

            PastoralDatabaseBackup.ImportResult.InvalidFile -> "Ongeldige rugsteunlêer"
            PastoralDatabaseBackup.ImportResult.ReadError -> "Kon nie lêer lees nie — probeer weer"
            PastoralDatabaseBackup.ImportResult.Failed -> "Herstel misluk"
        }
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
        if (result is PastoralDatabaseBackup.ImportResult.Success) {
            PastoralDatabase.getInstance(this)
            finish()
        }
    }

    private fun loadDatabaseFromDevice(filePath: String) {
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            Toast.makeText(this, "Lêer nie gevind nie", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            if (importDatabaseFromFile(sourceFile)) {
                Toast.makeText(
                    this@LaaiDatabasisActivity,
                    "Databasis suksesvol gelaai",
                    Toast.LENGTH_SHORT
                ).show()
                reloadDatabaseAndFinish()
            } else {
                Toast.makeText(
                    this@LaaiDatabasisActivity,
                    "Databasis laai misluk",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun isValidDatabaseFile(file: File, minMemberCount: Int = 10): Boolean {
        if (!file.exists() || file.length() < 512) {
            if (BuildConfig.DEBUG) Log.e(TAG, "File too small or missing: ${file.absolutePath}")
            return false
        }

        val header = try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(16)
                input.read(buffer)
                buffer
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to read file header", e)
            return false
        }

        val expectedHeader = "SQLite format 3\u0000".toByteArray()
        if (!header.contentEquals(expectedHeader)) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Invalid SQLite header in ${file.name}")
            return false
        }

        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                .use { db ->
                    val cursor = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='Members'",
                        null
                    )
                    val hasMembersTable = cursor.use { it.moveToFirst() }

                    if (!hasMembersTable) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Members table not found in database")
                        return false
                    }

                    val countCursor = db.rawQuery("SELECT COUNT(*) FROM Members", null)
                    val count = countCursor.use {
                        if (it.moveToFirst()) it.getInt(0) else 0
                    }

                    if (count < minMemberCount) {
                        if (BuildConfig.DEBUG) Log.e(
                            TAG,
                            "Members table has only $count rows (minimum $minMemberCount)"
                        )
                        return false
                    }

                    true
                }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error validating database", e)
            false
        }
    }

    // ================================================================
    // Collapsible Cards
    // ================================================================

    private fun setupCollapsibleCard(
        headerView: View,
        contentView: View,
        arrowView: TextView,
        prefKey: String,
        defaultOpen: Boolean
    ) {
        var isOpen = settings.getBoolean(prefKey, defaultOpen)

        fun applyState() {
            contentView.visibility = if (isOpen) View.VISIBLE else View.GONE
            arrowView.text = if (isOpen) "▼" else "▶"
        }

        applyState()

        headerView.setOnClickListener {
            isOpen = !isOpen
            applyState()
            settings.edit { putBoolean(prefKey, isOpen) }
        }
    }

    private fun initializeCollapsibleCards() {
        setupCollapsibleCard(
            headerView = binding.headerLocal,
            contentView = binding.contentLocal,
            arrowView = binding.arrowLocal,
            prefKey = "CARD_LOCAL_EXPANDED",
            defaultOpen = false
        )
        setupCollapsibleCard(
            headerView = binding.headerDropbox,
            contentView = binding.contentDropbox,
            arrowView = binding.arrowDropbox,
            prefKey = "CARD_DROPBOX_EXPANDED",
            defaultOpen = false
        )
        setupCollapsibleCard(
            headerView = binding.headerWifi,
            contentView = binding.contentWifi,
            arrowView = binding.arrowWifi,
            prefKey = "CARD_WIFI_EXPANDED",
            defaultOpen = true
        )
        setupCollapsibleCard(
            headerView = binding.headerPhoto,
            contentView = binding.contentPhoto,
            arrowView = binding.arrowPhoto,
            prefKey = "CARD_PHOTO_EXPANDED",
            defaultOpen = true
        )
    }
}