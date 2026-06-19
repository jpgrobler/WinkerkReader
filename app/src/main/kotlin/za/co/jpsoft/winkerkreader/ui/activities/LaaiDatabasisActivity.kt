package za.co.jpsoft.winkerkreader.ui.activities

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkDbHelper
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_DB
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WINKERK_DB
import za.co.jpsoft.winkerkreader.services.receivers.AlarmReceiver
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.workers.FileDownloadWorker
import za.co.jpsoft.winkerkreader.workers.PhotoDownloadWorker
import za.co.jpsoft.winkerkreader.databinding.LaaidatabasisBinding
import za.co.jpsoft.winkerkreader.utils.PastoralDatabaseBackup
import java.io.*
import java.nio.file.Files
import java.util.*
import java.util.regex.Pattern

class LaaiDatabasisActivity : AppCompatActivity() {
    private val CURRENT_PASTORAL_SCHEMA_VERSION = 1
    companion object {
        private const val TAG = "LaaiDatabasisActivity"
        const val DB_NAME = WINKERK_DB
        private const val PICKFILE_RESULT_CODE = 1
        private val RECEIVER_EXPORTED = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0

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

        fun isDownloadManagerAvailable(): Boolean {
            return true
        }

        fun checkIPv4(s: String): Boolean {
            val reg0To255 = "(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])"
            val regex = "$reg0To255\\.$reg0To255\\.$reg0To255\\.$reg0To255"
            val p = Pattern.compile(regex)
            val m = p.matcher(s)
            return m.matches()
        }

    }
    private lateinit var settings: SharedPreferences
    private lateinit var settingsManager: SettingsManager
    private var currentWorkInfoLiveData: LiveData<WorkInfo?>? = null
    private var workInfoObserver: Observer<WorkInfo?> = Observer { }

    private lateinit var binding: LaaidatabasisBinding
    private var AutoDL = false

    private val weeksdagArray = arrayOf("Sondag", "Maandag", "Dinsdag", "Woensdag", "Donderdag", "Vrydag", "Saterdag")
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



    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) Log.d("LaaiDatabasis", "Notification permission granted")
    }

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val dbPath = File(applicationInfo.dataDir, "databases")
            if (!dbPath.exists()) dbPath.mkdirs()

            val targetFile = File(dbPath, DB_NAME)
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                try {
                    recieverDownloadComplete?.let { unregisterReceiver(it) }
                } catch (_: IllegalArgumentException) {}

                reloadDatabaseAndFinish()
            } catch (e: IOException) {
                Log.e(TAG, "File copy failed", e)
                Toast.makeText(this, "Kon nie lêer kopieer nie: ${e.message}", Toast.LENGTH_LONG).show()
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
        } catch (_: Exception) { }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            navigateBackToMain()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun navigateBackToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LaaidatabasisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = getSharedPreferences(WinkerkContract.PREFS_USER_INFO, MODE_PRIVATE)
        settingsManager = SettingsManager.getInstance(this)
        initializeSettings()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                cancelOngoingDownloads()
                navigateBackToMain()
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        initializeTimePickerUI()
        initializeSpinnerUI()
        initializeCheckboxUI()
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
        try {
            recieverDownloadComplete?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering download receiver", e)
        }
    }

    private fun initializeSettings() {
        AutoDL = settings.getBoolean("AUTO_DL", false)
    }

    private fun initializeTimePickerUI() {
        val hour = settings.getString("DL-HOUR", "12") ?: "12"
        val minute = settings.getString("DL-MINUTE", "00") ?: "00"

        binding.tydText.inputType = InputType.TYPE_NULL
        binding.tydText.setText("$hour:$minute")
        binding.tydText.setOnClickListener { showTimePicker() }

        binding.serverIp.setText(settings.getString("IP", ""))
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val currentHour = calendar[Calendar.HOUR_OF_DAY]
        val currentMinutes = calendar[Calendar.MINUTE]

        val picker = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val timeText = "$selectedHour:$selectedMinute"
            binding.tydText.setText(timeText)
            saveTimeSettings(selectedHour, selectedMinute)
            Toast.makeText(this, "Tyd opgedateer", Toast.LENGTH_SHORT).show()
        }, currentHour, currentMinutes, true)
        picker.show()
    }

    private fun saveTimeSettings(hour: Int, minute: Int) {
        settings.edit {
            putString("DL-HOUR", hour.toString())
            putString("DL-MINUTE", minute.toString())
            putBoolean("DL-TIMEUPDATE", true)
            putBoolean("AUTO_DL", true)
        }
    }

    private fun initializeSpinnerUI() {
        val day = settings.getInt("DL-DAY", 6)
        val weeksdagStatusAdapter = za.co.jpsoft.winkerkreader.ui.adapters.SpinnerAdapter(this, null, weeksdagArray)
        binding.weeksdag.adapter = weeksdagStatusAdapter
        binding.weeksdag.setSelection(day - 1)
        binding.weeksdag.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveDaySelection(position + 1)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun saveDaySelection(day: Int) {
        settings.edit { putInt("DL-DAY", day) }
    }

    private fun initializeCheckboxUI() {
        binding.alDropBox.isChecked = AutoDL
        binding.alDropBox.setOnClickListener {
            AutoDL = binding.alDropBox.isChecked
            settings.edit { putBoolean("AUTO_DL", AutoDL) }
            if (AutoDL) {
                setupAlarmForDownload()
            } else {
                cancelAlarmForDownload()
            }
        }
    }

    private fun setupAlarmForDownload() {
        val hour = settings.getString("DL-HOUR", "08") ?: "08"
        val minute = settings.getString("DL-MINUTE", "00") ?: "00"

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.toInt())
            set(Calendar.MINUTE, minute.toInt())
            set(Calendar.SECOND, 0)
        }
        val now = Calendar.getInstance()

        settings.edit {
            putBoolean("DL-TIMEUPDATE", true)
            putBoolean("FROM_MENU", false)
        }

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "DropBoxDownLoad"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)

        val triggerTime = if (calendar.timeInMillis <= now.timeInMillis) {
            calendar.timeInMillis + AlarmManager.INTERVAL_DAY * 7
        } else {
            calendar.timeInMillis
        }

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerTime, AlarmManager.INTERVAL_DAY * 7, pendingIntent)
    }

    private fun cancelAlarmForDownload() {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "DropBoxDownLoad"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    private fun initializeButtons() {
        binding.button1.setOnClickListener { handleUpdateClick(it) }
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
        currentWorkInfoLiveData = WorkManager.getInstance(this).getWorkInfoByIdLiveData(photoWorkRequest.id)
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
                        Toast.makeText(this@LaaiDatabasisActivity, message, Toast.LENGTH_LONG).show()
                    } else if (workInfo.state == WorkInfo.State.FAILED) {
                        binding.photoSyncStatus.setText(R.string.photo_sync_failed_status)
                        binding.photoSyncStatus.visibility = View.VISIBLE
                        Toast.makeText(this@LaaiDatabasisActivity, R.string.photo_sync_failed_toast, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val progress = workInfo.progress
                    val prog = progress.getInt("progress", 0)
                    val tot = progress.getInt("total", 0)
                    val guid = progress.getString("currentGuid")
                    if (tot > 0) {
                        binding.photoSyncProgress.max = tot
                        binding.photoSyncProgress.progress = prog
                        binding.photoSyncStatus.text = getString(R.string.photo_sync_progress, prog, tot, guid ?: "")
                        binding.photoSyncStatus.visibility = View.VISIBLE
                    }
                }
            }
        }
        currentWorkInfoLiveData!!.observe(this, workInfoObserver)

        Toast.makeText(this, "Foto-sinkronisasie begin...", Toast.LENGTH_SHORT).show()
    }

    private fun handleUpdateClick(unused: View) {
        val timeStr = binding.tydText.text.toString()
        val parts = timeStr.split(":")
        var hour = if (parts.isNotEmpty()) parts[0] else "12"
        var minute = if (parts.size > 1) parts[1] else "00"

        if (hour.length < 2) hour = "0$hour"
        if (minute.length < 2) minute = "0$minute"

        settings.edit {
            putString("DL-HOUR", hour)
            putString("DL-MINUTE", minute)
            putBoolean("DL-TIMEUPDATE", true)
        }

        Toast.makeText(this, "Tyd opgedateer", Toast.LENGTH_SHORT).show()

        setupAlarmAndNavigateToMain(hour, minute)
    }

    private fun setupAlarmAndNavigateToMain(hour: String, minute: String) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.toInt())
            set(Calendar.MINUTE, minute.toInt())
            set(Calendar.SECOND, 0)
        }
        val now = Calendar.getInstance()

        val alarmIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = "DropBoxDownLoad"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)

        val triggerTime = if (calendar.timeInMillis <= now.timeInMillis) {
            calendar.timeInMillis + AlarmManager.INTERVAL_DAY * 7
        } else {
            calendar.timeInMillis
        }

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerTime, AlarmManager.INTERVAL_DAY * 7, pendingIntent)

        navigateToMainActivity()
    }

    private fun navigateToMainActivity() {
        settingsManager.defLayout = "VERJAAR"
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("SENDER_CLASS_NAME", "WysVerjaar")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun handleDropboxDownload() {
        binding.dbLinkButton.setBackgroundColor(Color.GREEN)
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
            withContext(Dispatchers.IO) {
                PastoralDatabaseBackup.backupNow(applicationContext)  // await backup first
            }
            // Then load on main thread as before
            if (LaaiNuweData(filePath)) {
                Toast.makeText(this@LaaiDatabasisActivity, "Suksesvol", Toast.LENGTH_SHORT).show()
                resetGemeenteSettings()
                reloadDatabaseAndFinish()
            } else {
                Toast.makeText(this@LaaiDatabasisActivity, "Onsuksesvol", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
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
        pickFileLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3"))
        binding.laaiPicker.setBackgroundColor(Color.GREEN)
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
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(workRequest.id).observe(this) { workInfo ->
            if (workInfo == null) return@observe
            val progress = workInfo.progress.getInt(FileDownloadWorker.KEY_PROGRESS, 0)
            if (progress > 0) {
                binding.laaiBoodskap.text = getString(R.string.download_received_percent, progress)
            }
            if (workInfo.state.isFinished) {
                button.background.clearColorFilter()
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    binding.laaiBoodskap.setText(R.string.download_completed)
                    Toast.makeText(this, R.string.db_received_success, Toast.LENGTH_SHORT).show()
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

    private fun initializeDataInfo() {
        binding.datadate.text = getString(R.string.current_data_info, settingsManager.dataDatum)
        val dropBoxUrl = settings.getString("DropBox", "")
        if (!dropBoxUrl.isNullOrEmpty()) {
            binding.dbLink.setText(dropBoxUrl)
        }
    }

    private fun scanForDatabaseFiles() {
        try {
            getFileList(winkerkEntry.getWkrDir(this))
        } catch (e: Exception) {
            Log.e("WinkerkReader LaaiDatabasisActivity", "Error scanning files: $e")
        }
        backupCurrentDatabase()
    }

    private fun backupCurrentDatabase() {
        try {
            val dataDir = File(applicationInfo.dataDir, "/databases/")
            val currentDB = File(dataDir, INFO_DB)
            val backupDB = File(winkerkEntry.getWkrDir(this), INFO_DB)
            if (backupDB.exists()) backupDB.delete()
            if (currentDB.exists()) {
                FileInputStream(currentDB).use { fis ->
                    FileOutputStream(backupDB).use { fos ->
                        fis.channel.transferTo(0, fis.channel.size(), fos.channel)
                    }
                }
                MediaScannerConnection.scanFile(this, arrayOf(backupDB.absolutePath), null, null)
            }
        } catch (e: Exception) {
            Log.e("WinkerkReader LaaiDatabasisActivity", "Error backing up database: $e")
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
            text = "${this@LaaiDatabasisActivity.fileList[index]["Path"]}\n${size} Mb$additionalData"
            id = index
            background = ContextCompat.getDrawable(this@LaaiDatabasisActivity, R.drawable.border2)
            layoutParams = LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT
            )
        }

        fileListGroup.addView(radioButton)
    }
//    private fun addFileRadioButton(fileListGroup: RadioGroup, index: Int) {
//        val file = File(this.fileList[index]["Path"]!!)
//        val size = (file.length() / 1024 / 1024).toInt().toString()
//        val additionalData = getFileAdditionalData(this.fileList[index]["Path"])
//        val radioButton = RadioButton(this).apply {
//            text = getString(R.string.db_file_item_format, this@LaaiDatabasisActivity.fileList[index]["Path"] ?: "", size, additionalData)
//            id = index
//            background = ContextCompat.getDrawable(this@LaaiDatabasisActivity, R.drawable.border2)
//            layoutParams = LinearLayoutCompat.LayoutParams(
//                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
//                LinearLayoutCompat.LayoutParams.WRAP_CONTENT
//            )
//        }
//        fileListGroup.addView(radioButton)
//    }

    private fun getFileAdditionalData(filePath: String?): String {
        if (filePath == null) return ""
        return try {
            SQLiteDatabase.openDatabase(filePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { sqlite ->
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
            Log.e("WinkerkReader LaaiDatabasisActivity", "Error reading database info: $e")
            ""
        }
    }

    private fun handleIntentExtras() {
        val intentMain = intent
        if (intentMain.extras == null) return
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
                Toast.makeText(this, "WKR - Dropbox Databasis gelaai", Toast.LENGTH_LONG).show()
                reloadDatabaseAndFinish()
            } else {
                Toast.makeText(this, "WKR - Dropbox Databasis laai was onsuksesvol", Toast.LENGTH_LONG).show()
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
        WinkerkDbHelper.closeInstance(WINKERK_DB)
        WinkerkDbHelper.closeInstance(INFO_DB)
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
            Log.e(TAG, "Laai Nuwe Data failed", e)
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
            } catch (e: IOException) { Log.e(TAG, "File Delete failed", e) }
        }
        if (syncPhotosAfterDb) startPhotoSync()
        return result
    }


    private fun downloadFromDropBoxUrl(url: String) {
        val dbPath = applicationInfo.dataDir + "/databases/"
        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        recieverDownloadComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val manager = getSystemService(DOWNLOAD_SERVICE) as? DownloadManager ?: return
                val reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (myDownloadReference != reference) return

                val query = DownloadManager.Query().setFilterById(reference)
                manager.query(query).use { cursor ->
                    if (cursor.moveToFirst()) {
                        // Get column index safely
                        val statusColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusColumnIndex != -1) {
                            val status = cursor.getInt(statusColumnIndex)
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val downloadUri = manager.getUriForDownloadedFile(reference)
                                contentResolver.openInputStream(downloadUri!!)?.use { input ->
                                    FileOutputStream("$dbPath/$DB_NAME").use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                reloadDatabaseAndFinish()
                            }
                        } else {
                            // Handle missing column – log error or fallback
                            Log.e("LaaiDatabasis", "COLUMN_STATUS not found in cursor")
                        }
                    }
                }
                try { unregisterReceiver(this) } catch (_: Exception) {}
            }
        }
        registerReceiver(recieverDownloadComplete, intentFilter, RECEIVER_EXPORTED)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setTitle(WINKERK_DB)
            setMimeType("application/vnd.sqlite3")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, DB_NAME)
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
            contentResolver.call(WinkerkContract.winkerkEntry.CONTENT_URI, "reloadDatabase", null, null)
            Handler(Looper.getMainLooper()).postDelayed({
                navigateBackToMain()
            }, 200)
        } catch (e: Exception) {
            Log.e(TAG, "Error during database reload", e)
            navigateBackToMain()
        }
    }

    /**
     * Checks for a pastoral DB backup in the WKR directory and offers import if found.
     * Call this from onResume() or after the congregation DB scan completes.
     */
    private fun checkForPastoralBackup() {
        val backupFile = PastoralDatabaseBackup.findBackupFile(this)
            ?: return   // No backup present — nothing to do

        val backupVersion = PastoralDatabaseBackup.readSchemaVersion(backupFile)
        val backupSizeMb  = "%.1f".format(backupFile.length() / 1_048_576.0)
        val backupDate    = java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(backupFile.lastModified()))

        when {
            backupVersion < 0 -> {
                // Corrupt or unreadable — warn and skip
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.pastoral_import_fout_titel))
                    .setMessage(getString(R.string.pastoral_import_onleesbaar))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            backupVersion > CURRENT_PASTORAL_SCHEMA_VERSION -> {
                // Backup is newer than the app supports — refuse import
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
                // Compatible — offer import
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

    private fun performPastoralImport(backupFile: java.io.File) {
        val progressDialog = AlertDialog.Builder(this)
            .setMessage(getString(R.string.pastoral_import_besig))
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            val success = PastoralDatabaseBackup.importBackup(
                context    = applicationContext,
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
}