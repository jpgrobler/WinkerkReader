package za.co.jpsoft.winkerkreader.ui.activities

import android.Manifest
import android.app.AlertDialog
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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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
import za.co.jpsoft.winkerkreader.workers.PhotoDownloadWorker
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

class LaaiDatabasisActivity : AppCompatActivity() {


    companion object {
        private const val TAG = "LaaiDatabasisActivity"
        const val DB_NAME = WINKERK_DB
        const val EXTRA_PROMPT_RESTORE = "pastoral_prompt_restore"
        private const val PICKFILE_RESULT_CODE = 1
        private val CURRENT_PASTORAL_SCHEMA_VERSION
            get() = PastoralDatabaseBackup.CURRENT_PASTORAL_SCHEMA_VERSION
        private val RECEIVER_EXPORTED =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0
        private var privateDownloadFile: File? = null
        private fun writeExtractedFileToDisk(`in`: InputStream, outs: OutputStream) {
            val buffer = ByteArray(1024)
            var length: Int
            while (`in`.read(buffer).also { length = it } > 0) {
                outs.write(buffer, 0, length)
            }
            outs.flush()
            outs.close()
            `in`.close()
        }

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

            // ✅ STEP 1: Close ALL database connections BEFORE any file operations
            lifecycleScope.launch {
                try {
                    // Close all database connections
                    withContext(Dispatchers.IO) {
                        WinkerkDbHelper.closeAllInstances()
                    WinkerkDatabase.closeInstance()
                        delay(200) // Now delay is available
                        System.gc()
                    }

                    // Show progress on main thread
                    binding.laaiBoodskap.text = "Besig om databasis te laai..."
                    binding.laaiIndeterminateBar.visibility = View.VISIBLE

                    // Perform the copy on a background thread
                    val result = withContext(Dispatchers.IO) {
                        copyDatabaseFromUri(uri)
                    }

                    // Handle result on main thread
                    binding.laaiIndeterminateBar.visibility = View.GONE

                    if (result) {
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
                } catch (e: Exception) {
                    binding.laaiIndeterminateBar.visibility = View.GONE
                    if (BuildConfig.DEBUG) Log.e(TAG, "Error picking file", e)
                    Toast.makeText(
                        this@LaaiDatabasisActivity,
                        "Fout: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.laaiBoodskap.text = "Fout: ${e.message}"
                }
            }
        }

    /**
     * Copy database from URI to app's database directory
     * Returns true on success, false on failure
     */
    private suspend fun copyDatabaseFromUri(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val dbPath = File(applicationInfo.dataDir, "databases")
                if (!dbPath.exists() && !dbPath.mkdirs()) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Failed to create databases directory")
                    return@withContext false
                }

                val targetFile = File(dbPath, DB_NAME)

                // Delete existing database if it exists
                if (targetFile.exists()) {
                    if (!targetFile.delete()) {
                        // Try to rename instead of delete
                        val backupFile =
                            File(dbPath, "${DB_NAME}.old_${System.currentTimeMillis()}")
                        if (targetFile.renameTo(backupFile)) {
                            if (BuildConfig.DEBUG) Log.d(
                                TAG,
                                "Renamed existing database to ${backupFile.name}"
                            )
                        } else {
                            if (BuildConfig.DEBUG) Log.e(
                                TAG,
                                "Failed to delete or rename existing database"
                            )
                            return@withContext false
                        }
                    }
                }

                // Copy the file
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Failed to open input stream from URI")
                    return@withContext false
                }

                val outputStream = FileOutputStream(targetFile)
                try {
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Error copying file", e)
                    targetFile.delete()
                    return@withContext false
                }

                // Validate the copied file
                if (!isValidDatabaseFile(targetFile, 5)) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Copied file is not a valid database")
                    targetFile.delete()
                    return@withContext false
                }

                // ✅ STEP 2: Migrate the database schema
                if (!migrateDownloadedDatabase(targetFile)) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Migration failed")
                    targetFile.delete()
                    return@withContext false
                }

                // ✅ STEP 3: Set database date
                try {
                    WinkerkDbHelper.setDatabaseDate(this@LaaiDatabasisActivity)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Failed to set database date", e)
                }

                if (BuildConfig.DEBUG) Log.d(TAG, "Database copied and migrated successfully")
                true

            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error copying database from URI", e)
                false
            }
        }
    }

    private fun cancelOngoingDownloads() {
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
        checkForPastoralBackup()
    }

    override fun onDestroy() {
        super.onDestroy()
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

        val photoWorkRequest = OneTimeWorkRequest.Builder(PhotoDownloadWorker::class.java)
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

        // Use the same copy logic but from a file path instead of URI
        lifecycleScope.launch {
            binding.laaiBoodskap.text = "Besig om databasis te laai..."
            binding.laaiIndeterminateBar.visibility = View.VISIBLE

            val result = withContext(Dispatchers.IO) {
                // Close connections first
                WinkerkDbHelper.closeAllInstances()
                WinkerkDatabase.closeInstance()
                delay(200) // Now delay is available
                System.gc()

                // Copy from file path
                val sourceFile = File(filePath)
                if (!sourceFile.exists()) {
                    return@withContext false
                }

                val dbPath = File(applicationInfo.dataDir, "databases")
                if (!dbPath.exists() && !dbPath.mkdirs()) {
                    return@withContext false
                }

                val targetFile = File(dbPath, DB_NAME)

                // Delete existing
                if (targetFile.exists()) {
                    targetFile.delete()
                }

                // Copy
                try {
                    sourceFile.inputStream().use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Error copying file", e)
                    return@withContext false
                }

                // Validate
                if (!isValidDatabaseFile(targetFile, 5)) {
                    targetFile.delete()
                    return@withContext false
                }

                // Migrate
                if (!migrateDownloadedDatabase(targetFile)) {
                    targetFile.delete()
                    return@withContext false
                }

                true
            }

            binding.laaiIndeterminateBar.visibility = View.GONE

            if (result) {
                // Reload and finish
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
        val workRequest = OneTimeWorkRequest.Builder(FileDownloadWorker::class.java)
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
                if (workInfo.state.isFinished) {
                    button.background.clearColorFilter()
                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        val dbFile = File(applicationInfo.dataDir, "databases/$DB_NAME")
                        if (dbFile.exists()) {
                            migrateDownloadedDatabase(dbFile)
                        }
                        binding.laaiBoodskap.setText(R.string.download_completed)
                        Toast.makeText(this, R.string.db_received_success, Toast.LENGTH_SHORT)
                            .show()
                        Handler(Looper.getMainLooper()).postDelayed({ navigateBackToMain() }, 1500)
                    } else {
                        binding.laaiBoodskap.setText(R.string.download_failed)
                        Toast.makeText(this, R.string.db_download_failed, Toast.LENGTH_LONG).show()
                    }
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
        // ✅ FIX: Refresh date when returning to activity
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
            // Force a fresh read from the database
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
                // Close existing helper
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

        // Pastoral restore request from BedieningActivity / MainActivity snackbar
        if (intentMain.getBooleanExtra(EXTRA_PROMPT_RESTORE, false)) {
            openFilePicker()       // launches the SAF OpenDocument picker
            return
        }

        // Existing congregation DB auto-update path
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
            if (LaaiNuweData(filePath)) {
                val appDbFile = File(applicationInfo.dataDir, "databases/$DB_NAME")
                if (migrateDownloadedDatabase(appDbFile)) {
                    reloadDatabaseAndFinish()
                } else {
                    Toast.makeText(this, "Databasis omskakeling misluk", Toast.LENGTH_LONG).show()
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

    private fun LaaiNuweData(nfile: String): Boolean {
        WinkerkDatabase.closeInstance()
        //WinkerkDbHelper.closeInstance(WinkerkContract.winkerkEntry.INFO_DB)
        val dbPath = applicationInfo.dataDir + "/databases/"
        var result = false
        val sourceFile = File(nfile)
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            inputStream = FileInputStream(sourceFile)
            outputStream = FileOutputStream("$dbPath/$WINKERK_DB")
            writeExtractedFileToDisk(inputStream, outputStream)
            result = true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Laai Nuwe Data failed", e)
            showError("Kon nie databasis laai nie")
            result = false
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
        if (delete) {
            try {
                val absolutePath = sourceFile.absolutePath
                sourceFile.delete()
                MediaScannerConnection.scanFile(this, arrayOf(absolutePath), null, null)
            } catch (e: IOException) {
                Log.e(TAG, "File Delete failed", e)
            }
        }
        if (syncPhotosAfterDb) startPhotoSync()
        return result
    }

    private fun downloadFromDropBoxUrl(url: String) {
        if (isFinishing || isDestroyed) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Activity destroyed, ignoring download")
            return
        }

        val dbPath = applicationInfo.dataDir + "/databases/"
        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)

        // Create a private file – no public exposure
        privateDownloadFile = File(getExternalFilesDir(null), "WinkerkReader_temp.db")
        privateDownloadFile?.parentFile?.mkdirs()

        recieverDownloadComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val manager = getSystemService(DOWNLOAD_SERVICE) as? DownloadManager ?: return
                val reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (myDownloadReference != reference) return

                val query = DownloadManager.Query().setFilterById(reference)
                manager.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        showError("Kon nie aflaaistatus lees nie")
                        privateDownloadFile?.delete()
                        privateDownloadFile = null
                        return
                    }
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIdx < 0) {
                        showError("Aflaaistatus onbekend")
                        privateDownloadFile?.delete()
                        privateDownloadFile = null
                        return
                    }
                    val status = cursor.getInt(statusIdx)
                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        showError("Aflaai misluk (status $status)")
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

                    try {
                        // 1. Ensure databases directory exists
                        val dbDir = File(dbPath)
                        if (!dbDir.exists() && !dbDir.mkdirs()) {
                            showError("Kon nie databasisgids skep nie")
                            privateDownloadFile?.delete()
                            privateDownloadFile = null
                            return
                        }

                        // 2. Create a temporary file in the same directory
                        val tempFile = File(dbDir, "WinkerkReader_temp.db")
                        if (tempFile.exists()) tempFile.delete()

                        // 3. Write the downloaded content to the temp file
                        contentResolver.openInputStream(downloadUri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                                output.fd.sync()
                            }
                        } ?: run {
                            showError("Kon nie lêer oopmaak nie")
                            privateDownloadFile?.delete()
                            privateDownloadFile = null
                            return
                        }

                        // 4. Validate database file (SQLite header + minimum rows)
                        if (!isValidDatabaseFile(tempFile, 5)) {
                            showError("Aflaailêer is nie 'n geldige databasis nie")
                            tempFile.delete()
                            privateDownloadFile?.delete()
                            privateDownloadFile = null
                            return
                        }

                        // 5. Close Room to release any locks on the target file
                        WinkerkDatabase.closeInstance()

                        // 6. Rename temp file to the real database file
                        val dbFile = File(dbDir, DB_NAME)
                        if (dbFile.exists() && !dbFile.delete()) {
                            showError("Kon bestaande databasis nie verwyder nie")
                            tempFile.delete()
                            privateDownloadFile?.delete()
                            privateDownloadFile = null
                            return
                        }
                        if (!tempFile.renameTo(dbFile)) {
                            tempFile.copyTo(dbFile, overwrite = true)
                            tempFile.delete()
                        }

                        // 7. Migrate the new database to Room schema
                        val migrated = migrateDownloadedDatabase(dbFile)
                        if (!migrated) {
                            showError("Databasis omskakeling misluk – kontak ondersteuning")
                            privateDownloadFile?.delete()
                            privateDownloadFile = null
                            return
                        }

                        // ✅ Success – delete the private downloaded file
                        privateDownloadFile?.delete()
                        privateDownloadFile = null

                        // 8. Reload and finish with a small delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            reloadDatabaseAndFinish()
                        }, 300)

                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Download processing failed", e)
                        showError("Fout met verwerking: ${e.message}")
                        privateDownloadFile?.delete()
                        privateDownloadFile = null
                    } finally {
                        try {
                            unregisterReceiver(this)
                            isDownloadReceiverRegistered = false
                            recieverDownloadComplete = null
                        } catch (_: Exception) {
                            // ignore
                        }
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
            setDestinationUri(Uri.fromFile(privateDownloadFile))  // 🔒 private
        }
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        myDownloadReference = manager.enqueue(request)
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            binding.laaiBoodskap.text = message
        }
    }

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

    private fun reloadDatabaseAndFinish() {
        try {
            contentResolver.call(winkerkEntry.CONTENT_URI, "reloadDatabase", null, null)
            // Old: WidgetViewsFactory.invalidateCache()
            WidgetDataRepository.invalidateCache()
            Handler(Looper.getMainLooper()).postDelayed({
                navigateBackToMain()
            }, 200)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error during database reload", e)
            navigateBackToMain()
        }
    }

    /**
     * Checks for a pastoral DB backup in the WKR directory and offers import if found.
     * Call this from onResume() or after the congregation DB scan completes.
     */
    private fun checkForPastoralBackup() {
        val backupFile = PastoralDatabaseBackup.findBackupFile(this)
            ?: return

        val backupVersion = PastoralDatabaseBackup.readSchemaVersion(backupFile)
        val backupSizeMb = "%.1f".format(backupFile.length() / 1_048_576.0)
        val backupDate = java.text.SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault())
            .format(Date(backupFile.lastModified()))

        when {
            backupVersion < 0 -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.pastoral_import_fout_titel))
                    .setMessage(getString(R.string.pastoral_import_onleesbaar))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }

            backupVersion > CURRENT_PASTORAL_SCHEMA_VERSION -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.pastoral_import_fout_titel))
                    .setMessage(
                        getString(
                            R.string.pastoral_import_weergawe_fout,
                            backupVersion,
                            CURRENT_PASTORAL_SCHEMA_VERSION
                        )
                    )
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }

            else -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.pastoral_import_gevind_titel))
                    .setMessage(
                        getString(
                            R.string.pastoral_import_gevind_boodskap,
                            backupDate,
                            backupSizeMb
                        )
                    )
                    .setPositiveButton(getString(R.string.pastoral_import_ja)) { _, _ ->
                        performPastoralImport(backupFile)
                    }
                    .setNegativeButton(getString(R.string.pastoral_import_nee), null)
                    .show()
            }
        }
    }

    private fun performPastoralImport(backupFile: File) {
        val progressDialog = AlertDialog.Builder(this)
            .setMessage(getString(R.string.pastoral_import_besig))
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            val success = PastoralDatabaseBackup.importBackup(
                context = applicationContext,
                backupFile = backupFile
            )
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                if (success) {
                    Toast.makeText(
                        this@LaaiDatabasisActivity,
                        getString(R.string.pastoral_import_sukses),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    AlertDialog.Builder(this@LaaiDatabasisActivity)
                        .setTitle(getString(R.string.pastoral_import_fout_titel))
                        .setMessage(getString(R.string.pastoral_import_misluk))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    /**
     * Migrates VARCHAR columns to TEXT in a newly downloaded/copied database file
     * BEFORE Room opens it. Uses plain SQLiteDatabase (not Room) to avoid
     * Room's schema validation triggering first.
     */
    private fun migrateDownloadedDatabase(dbFile: File): Boolean {
        if (!dbFile.exists()) {
            if (BuildConfig.DEBUG) Log.e(TAG, "DB file does not exist")
            return false
        }
        return try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                listOf("Members", "Argief", "Datum").forEach { tableName ->
                    migrateTableVarcharToText(db, tableName)
                }
                db.execSQL("PRAGMA user_version = 1")
                if (BuildConfig.DEBUG) Log.i(TAG, "Migration successful on ${dbFile.name}")
            }
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Migration failed on ${dbFile.name}", e)
            false
        }
    }

    /**
     * Migrates a table to ensure all columns (except _id) are TEXT, and that _id
     * is correctly defined as INTEGER PRIMARY KEY AUTOINCREMENT.
     * If the old table does NOT have an _id column, it is omitted from the INSERT,
     * allowing the new AUTOINCREMENT to generate _id values automatically.
     */
    /**
     * Migrates a table to ensure all columns (except _id) are TEXT, and that _id
     * is correctly defined as INTEGER PRIMARY KEY AUTOINCREMENT with or without
     * NOT NULL depending on the table (Members requires NOT NULL).
     *
     * Handles the case where the old table does NOT have an _id column – in that
     * case, we let Room auto‑generate the _id values.
     */
    private fun migrateTableVarcharToText(db: SQLiteDatabase, tableName: String) {
        // Check if table exists
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { if (!it.moveToFirst()) return }

        // Read existing columns from the old table
        val oldColumns = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info('$tableName')", null).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                oldColumns.add(name)
            }
        }

        if (oldColumns.isEmpty()) return

        val withNotNull = tableName == "Members"

        // Build new table definition: _id first, then all other columns as TEXT
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

        // Prepare INSERT – handle presence/absence of _id
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
        // SQLite has no standard MIME type; offer both options so pickers show .db files
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
            // Re-init Room and reload UI
            PastoralDatabase.getInstance(this) // triggers migration if needed
            finish()
        }
    }

    // Add to LaaiDatabasisActivity.kt

    /**
     * Load database from a file on the device (Downloads folder or other locations)
     */
    private fun loadDatabaseFromDevice(filePath: String) {
        // First, close all database connections
        WinkerkDbHelper.closeAllInstances()
        WinkerkDatabase.closeInstance()

        // Force garbage collection to release any lingering connections
        System.gc()

        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            Toast.makeText(this, "Lêer nie gevind nie", Toast.LENGTH_LONG).show()
            return
        }

        // Validate the file is a valid SQLite database
        if (!isValidDatabaseFile(sourceFile, 5)) {
            Toast.makeText(this, "Lêer is nie 'n geldige databasis nie", Toast.LENGTH_LONG).show()
            return
        }

        val dbPath = File(applicationInfo.dataDir, "databases")
        if (!dbPath.exists() && !dbPath.mkdirs()) {
            Toast.makeText(this, "Kon nie databasis gids skep nie", Toast.LENGTH_LONG).show()
            return
        }

        val targetFile = File(dbPath, DB_NAME)

        try {
            // Close any existing connections to the target file
            WinkerkDbHelper.closeInstance(DB_NAME)

            // Wait a moment for any lingering connections to close
            Thread.sleep(100)

            // Delete existing database if it exists
            if (targetFile.exists()) {
                if (!targetFile.delete()) {
                    // Try to rename instead of delete
                    val backupFile = File(dbPath, "${DB_NAME}.old")
                    targetFile.renameTo(backupFile)
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "Renamed existing database to ${backupFile.name}"
                    )
                }
            }

            // Copy the file
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }

            // Verify the copied file
            if (!isValidDatabaseFile(targetFile, 5)) {
                targetFile.delete()
                Toast.makeText(this, "Gekopieerde lêer is nie geldig nie", Toast.LENGTH_LONG).show()
                return
            }

            // Migrate the database schema
            val migrated = migrateDownloadedDatabase(targetFile)
            if (!migrated) {
                targetFile.delete()
                Toast.makeText(this, "Databasis omskakeling misluk", Toast.LENGTH_LONG).show()
                return
            }

            // Success
            Toast.makeText(this, "Databasis suksesvol gelaai", Toast.LENGTH_SHORT).show()

            // Reload database and finish
            reloadDatabaseAndFinish()

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error loading database from device", e)
            Toast.makeText(this, "Fout: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Validates that the given file is a genuine SQLite database with the correct schema.
     */
    private fun isValidDatabaseFile(file: File, minMemberCount: Int = 10): Boolean {
        if (!file.exists() || file.length() < 512) {
            if (BuildConfig.DEBUG) Log.e(TAG, "File too small or missing: ${file.absolutePath}")
            return false
        }

        // 1. Check SQLite header (first 16 bytes)
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

        // 2. Check if the file is a valid database with the required tables
        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                .use { db ->
                    // Check if Members table exists
                    val cursor = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='Members'",
                        null
                    )
                    val hasMembersTable = cursor.use { it.moveToFirst() }

                    if (!hasMembersTable) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Members table not found in database")
                        return false
                    }

                    // Check row count
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
}
