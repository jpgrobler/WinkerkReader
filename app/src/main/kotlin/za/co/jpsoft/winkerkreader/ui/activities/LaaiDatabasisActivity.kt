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
import android.provider.Settings
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.*
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
import java.io.*
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

class LaaiDatabasisActivity : AppCompatActivity() {

    private lateinit var settings: SharedPreferences
    private lateinit var settingsManager: SettingsManager
    private var currentWorkInfoLiveData: LiveData<WorkInfo?>? = null
    private var workInfoObserver: Observer<WorkInfo?> = Observer { }

    private lateinit var syncPhotosCheck: CheckBox
    private lateinit var startPhotoSyncBtn: Button
    private lateinit var photoProgress: ProgressBar
    private lateinit var photoStatus: TextView
    private var syncPhotosAfterDb = false

    private var SERVER_IP: String? = null
    private var SERVER_PORT = 49514

    private var myDownloadReference: Long = 0
    private var recieverDownloadComplete: BroadcastReceiver? = null

    private val fileList = ArrayList<HashMap<String, String>>()
    private var delete = false

    private lateinit var laai_boodskap: TextView

    private lateinit var eText: EditText
    private var AutoDL = false

    private val weeksdagArray = arrayOf("Sondag", "Maandag", "Dinsdag", "Woensdag", "Donderdag", "Vrydag", "Saterdag")
    private var FlagCancelledUSB = false
    private var FlagCancelledWiFi = false

    // Track ongoing file download work
    private var fileDownloadWorkId: UUID? = null

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) Log.d("LaaiDatabasis", "Notification permission granted")
    }

    private val storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) Log.d("LaaiDatabasis", "Storage permission granted")
    }

    // Modern Activity Result Launcher for file picker
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
                    unregisterReceiver(recieverDownloadComplete)
                } catch (_: IllegalArgumentException) {}

                reloadDatabaseAndFinish()
            } catch (e: IOException) {
                Log.e(TAG, "File copy failed", e)
                Toast.makeText(this, "Kon nie lêer kopieer nie: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBackPressed() {
        // Cancel ongoing file download work if any
        fileDownloadWorkId?.let { workId ->
            WorkManager.getInstance(this).cancelWorkById(workId)
            fileDownloadWorkId = null
        }
        if (myDownloadReference != 0L) {
            getSystemService(Context.DOWNLOAD_SERVICE)?.let {
                (it as DownloadManager).remove(myDownloadReference)
                myDownloadReference = 0
            }
        }
        try {
            recieverDownloadComplete?.let { unregisterReceiver(it) }
        } catch (e: Exception) { }
        navigateBackToMain()
        // Do NOT call super.onBackPressed() because navigateBackToMain already finishes the activity
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
        setContentView(R.layout.laaidatabasis)

        settings = getSharedPreferences(WinkerkContract.PREFS_USER_INFO, MODE_PRIVATE)
        settingsManager = SettingsManager.getInstance(this)
        initializeSettings()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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

        syncPhotosCheck = findViewById(R.id.sync_photos)
        startPhotoSyncBtn = findViewById(R.id.start_photo_sync)
        photoProgress = findViewById(R.id.photo_sync_progress)
        photoStatus = findViewById(R.id.photo_sync_status)

        syncPhotosAfterDb = settings.getBoolean("SYNC_PHOTOS", false)
        syncPhotosCheck.isChecked = syncPhotosAfterDb
        syncPhotosCheck.setOnCheckedChangeListener { _, isChecked ->
            settings.edit().putBoolean("SYNC_PHOTOS", isChecked).apply()
            syncPhotosAfterDb = isChecked
        }

        startPhotoSyncBtn.setOnClickListener { startPhotoSync() }

        handleAutomaticDownload()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any ongoing file download work
        fileDownloadWorkId?.let { workId ->
            WorkManager.getInstance(this).cancelWorkById(workId)
            fileDownloadWorkId = null
        }
        // Remove observer for photo sync (safe even if observer is null)
        currentWorkInfoLiveData?.removeObserver(workInfoObserver)
        try {
            recieverDownloadComplete?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering download receiver", e)
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers (unchanged except for socket transfer replaced by WorkManager)
    // -------------------------------------------------------------------------

    private fun initializeSettings() {
        AutoDL = settings.getBoolean("AUTO_DL", false)
    }

    private fun initializeTimePickerUI() {
        val hour = settings.getString("DL-HOUR", "12") ?: "12"
        val minute = settings.getString("DL-MINUTE", "00") ?: "00"

        eText = findViewById(R.id.tydText)
        eText.inputType = InputType.TYPE_NULL
        eText.setText("$hour:$minute")
        eText.setOnClickListener { showTimePicker() }

        findViewById<EditText>(R.id.server_ip).setText(settings.getString("IP", ""))
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val currentHour = calendar[Calendar.HOUR_OF_DAY]
        val currentMinutes = calendar[Calendar.MINUTE]

        val picker = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val timeText = "$selectedHour:$selectedMinute"
            eText.setText(timeText)
            saveTimeSettings(selectedHour, selectedMinute)
            Toast.makeText(this, "Tyd opgedateer", Toast.LENGTH_SHORT).show()
        }, currentHour, currentMinutes, true)
        picker.show()
    }

    private fun saveTimeSettings(hour: Int, minute: Int) {
        settings.edit()
            .putString("DL-HOUR", hour.toString())
            .putString("DL-MINUTE", minute.toString())
            .putBoolean("DL-TIMEUPDATE", true)
            .putBoolean("AUTO_DL", true)
            .apply()
    }

    private fun initializeSpinnerUI() {
        val day = settings.getInt("DL-DAY", 6)
        val weeksDag = findViewById<Spinner>(R.id.weeksdag)
        val weeksdagStatusAdapter = za.co.jpsoft.winkerkreader.ui.adapters.SpinnerAdapter(this, null, weeksdagArray)
        weeksDag.adapter = weeksdagStatusAdapter
        weeksDag.setSelection(day - 1)
        weeksDag.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveDaySelection(position + 1)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun saveDaySelection(day: Int) {
        settings.edit().putInt("DL-DAY", day).apply()
    }

    private fun initializeCheckboxUI() {
        val autoDLCheck = findViewById<CheckBox>(R.id.alDropBox)
        autoDLCheck.isChecked = AutoDL
        autoDLCheck.setOnClickListener {
            AutoDL = autoDLCheck.isChecked
            settings.edit().putBoolean("AUTO_DL", AutoDL).apply()
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

        settings.edit()
            .putBoolean("DL-TIMEUPDATE", true)
            .putBoolean("FROM_MENU", false)
            .apply()

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
        findViewById<Button>(R.id.button1).setOnClickListener { handleSetTimeClick() }
        findViewById<Button>(R.id.dbLinkButton).setOnClickListener { handleDropboxDownload() }
        findViewById<Button>(R.id.laai_laai).setOnClickListener { handleLoadDatabase() }
        findViewById<Button>(R.id.laai_picker).setOnClickListener { handlePickFile() }
        findViewById<Button>(R.id.laai_socket).setOnClickListener { handleNetworkTransfer() }
        findViewById<Button>(R.id.laai_USB).setOnClickListener { handleUSBTransfer() }
    }

    private fun startPhotoSync() {
        val forceSyncCheck = findViewById<CheckBox>(R.id.force_sync_check)
        val forceSync = forceSyncCheck.isChecked

        WorkManager.getInstance(this).cancelAllWorkByTag("photo_sync")

        val ip = settings.getString("IP", "")
        if (ip.isNullOrEmpty()) {
            Toast.makeText(this, "Please set server IP first", Toast.LENGTH_SHORT).show()
            return
        }

        photoProgress.visibility = View.VISIBLE
        photoStatus.visibility = View.VISIBLE
        photoProgress.progress = 0
        photoStatus.text = "Begin foto-sinkronisasie..."
        startPhotoSyncBtn.isEnabled = false

        val inputData = Data.Builder()
            .putString("SERVER_IP", ip)
            .putBoolean("FORCE_SYNC", forceSync)
            .build()

        val photoWorkRequest = OneTimeWorkRequest.Builder(PhotoDownloadWorker::class.java)
            .setInputData(inputData)
            .addTag("photo_sync")
            .build()

        WorkManager.getInstance(this).enqueue(photoWorkRequest)

        currentWorkInfoLiveData?.removeObserver(workInfoObserver ?: return)
        currentWorkInfoLiveData = WorkManager.getInstance(this).getWorkInfoByIdLiveData(photoWorkRequest.id)
        workInfoObserver = Observer { workInfo ->
            if (workInfo != null) {
                if (workInfo.state.isFinished) {
                    photoProgress.visibility = View.GONE
                    startPhotoSyncBtn.isEnabled = true

                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        val output = workInfo.outputData
                        val success = output.getInt("SUCCESS_COUNT", 0)
                        val fail = output.getInt("FAIL_COUNT", 0)
                        val message = "Klaar: $success sukses, $fail misluk"
                        photoStatus.text = message
                        photoStatus.visibility = View.VISIBLE
                        Toast.makeText(this@LaaiDatabasisActivity, message, Toast.LENGTH_LONG).show()
                    } else if (workInfo.state == WorkInfo.State.FAILED) {
                        photoStatus.text = "Misluk"
                        photoStatus.visibility = View.VISIBLE
                        Toast.makeText(this@LaaiDatabasisActivity, "Foto-sinkronisasie het misluk", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val progress = workInfo.progress
                    val prog = progress.getInt("progress", 0)
                    val tot = progress.getInt("total", 0)
                    val guid = progress.getString("currentGuid")
                    if (tot > 0) {
                        photoProgress.max = tot
                        photoProgress.progress = prog
                        photoStatus.text = "Sinchroniseer foto's: $prog/$tot (${guid ?: ""})"
                        photoStatus.visibility = View.VISIBLE
                    }
                }
            }
        }
        currentWorkInfoLiveData!!.observe(this, workInfoObserver!!)

        Toast.makeText(this, "Foto-sinkronisasie begin...", Toast.LENGTH_SHORT).show()
    }

    private fun handleSetTimeClick() {
        val hourEdit = findViewById<EditText>(R.id.time_hour)
        val minuteEdit = findViewById<EditText>(R.id.time_minute)

        var hour = hourEdit.text.toString()
        var minute = minuteEdit.text.toString()

        if (hour.length < 2) hour = "0$hour"
        if (minute.length < 2) minute = "0$minute"

        settings.edit()
            .putString("DL-HOUR", hour)
            .putString("DL-MINUTE", minute)
            .putBoolean("DL-TIMEUPDATE", true)
            .apply()

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
        findViewById<Button>(R.id.dbLinkButton).apply {
            setBackgroundColor(Color.GREEN)
        }

        val dbLinkView = findViewById<EditText>(R.id.db_link)
        val downloadUrl = processDownloadUrl(dbLinkView.text.toString())

        downloadFromDropBoxUrl(downloadUrl)

        settings.edit().putString("DropBox", downloadUrl).apply()

        findViewById<TextView>(R.id.laai_boodskap).text = "WKR - Databasis word nou van Dropbox afgelaai\nMoenie die skerm toemaak nie!!"
        findViewById<Button>(R.id.dbLinkButton).visibility = View.INVISIBLE
        findViewById<View>(R.id.laai_local).visibility = View.GONE
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
        val radioGroup = findViewById<RadioGroup>(R.id.laai_filelist)
        val radioButtonID = radioGroup.checkedRadioButtonId

        if (radioButtonID == -1) {
            Toast.makeText(this, "Kies asseblief 'n databasis", Toast.LENGTH_SHORT).show()
            return
        }

        val deleteButton = findViewById<RadioButton>(R.id.laai_wisuit)
        delete = deleteButton.isChecked

        val filePath = fileList[radioButtonID]["Path"]
        if (LaaiNuweData(filePath!!)) {
            Toast.makeText(this, "Suksesvol", Toast.LENGTH_SHORT).show()
            resetGemeenteSettings()
            reloadDatabaseAndFinish()
        } else {
            Toast.makeText(this, "Onsuksesvol", Toast.LENGTH_SHORT).show()
            navigateToMainActivity()
        }

        deleteButton.isChecked = false
        radioGroup.clearCheck()
    }

    private fun resetGemeenteSettings() {
        settingsManager.gemeenteNaam = ""
        settingsManager.gemeenteEpos = ""

        settings.edit()
            .putString("Gemeente", "")
            .putString("Gemeente_Epos", "")
            .putString("DATA_DATUM", "")
            .apply()
    }

    private fun handlePickFile() {
        pickFileLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3"))
        findViewById<Button>(R.id.laai_picker).setBackgroundColor(Color.GREEN)
    }

    // -------------------------------------------------------------------------
    // REFACTORED: WiFi and USB transfer using WorkManager
    // -------------------------------------------------------------------------

    private fun handleNetworkTransfer() {
        val networkButton = findViewById<Button>(R.id.laai_socket)
        val ipAddress = findViewById<EditText>(R.id.server_ip)
        laai_boodskap = findViewById(R.id.laai_boodskap)

        if (FlagCancelledWiFi) {
            // Cancel ongoing work
            fileDownloadWorkId?.let { WorkManager.getInstance(this).cancelWorkById(it) }
            fileDownloadWorkId = null
            networkButton.background.clearColorFilter()
            laai_boodskap.text = "Aflaai gekanselleer"
            FlagCancelledWiFi = false
        } else {
            val ipText = ipAddress.text.toString()
            if (ipText.isNotEmpty() && checkIPv4(ipText)) {
                networkButton.background.setColorFilter(Color.YELLOW, PorterDuff.Mode.DARKEN)
                saveIPAddress(ipText)
                SERVER_IP = ipText
                SERVER_PORT = 49514
                startFileDownload(ipText, SERVER_PORT, networkButton, isWiFi = true)
                FlagCancelledWiFi = true
            } else {
                laai_boodskap.text = "Voer geldige IP adres in asb"
            }
        }
    }

    private fun handleUSBTransfer() {
        val usbButton = findViewById<Button>(R.id.laai_USB)
        val ipAddress = findViewById<EditText>(R.id.server_ip)
        laai_boodskap = findViewById(R.id.laai_boodskap)

        if (FlagCancelledUSB) {
            fileDownloadWorkId?.let { WorkManager.getInstance(this).cancelWorkById(it) }
            fileDownloadWorkId = null
            usbButton.background.clearColorFilter()
            ipAddress.setText("")
            laai_boodskap.text = "Aflaai gekanselleer"
            FlagCancelledUSB = false
        } else {
            usbButton.background.setColorFilter(Color.YELLOW, PorterDuff.Mode.DARKEN)
            ipAddress.setText("127.0.0.1")
            SERVER_IP = "localhost"
            SERVER_PORT = 49514
            startFileDownload("127.0.0.1", SERVER_PORT, usbButton, isWiFi = false)
            FlagCancelledUSB = true
        }
    }

    private fun startFileDownload(serverIp: String, port: Int, button: Button, isWiFi: Boolean) {
        laai_boodskap.text = "Begin aflaai..."

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

        // Observe progress and result
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(workRequest.id).observe(this) { workInfo ->
            if (workInfo == null) return@observe

            // Update progress
            val progress = workInfo.progress.getInt(FileDownloadWorker.KEY_PROGRESS, 0)
            if (progress > 0) {
                laai_boodskap.text = "Ontvang: $progress%"
            }

            if (workInfo.state.isFinished) {
                button.background.clearColorFilter()
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    laai_boodskap.text = "Aflaai voltooi"
                    Toast.makeText(this, "Databasis suksesvol ontvang", Toast.LENGTH_SHORT).show()
                    // Worker already called reloadDatabase, just delay then finish
                    Handler(Looper.getMainLooper()).postDelayed({
                        navigateBackToMain()
                    }, 1500)
                } else {
                    laai_boodskap.text = "Aflaai misluk"
                    Toast.makeText(this, "Kon nie databasis aflaai nie", Toast.LENGTH_LONG).show()
                }
                // Reset flags
                if (isWiFi) FlagCancelledWiFi = false else FlagCancelledUSB = false
                fileDownloadWorkId = null
            }
        }
    }

    private fun saveIPAddress(ipAddress: String) {
        settings.edit().putString("IP", ipAddress).apply()
    }

    private fun initializeProgressBars() {
        findViewById<ProgressBar>(R.id.laai_indeterminateBar).visibility = View.GONE
        findViewById<ProgressBar>(R.id.laai_indeterminateBar2).visibility = View.GONE
    }

    private fun initializeDataInfo() {
        findViewById<TextView>(R.id.datadate).text = "Huidige Data: ${settingsManager.dataDatum}"
        val dbLinkView = findViewById<EditText>(R.id.db_link)
        val dropBoxUrl = settings.getString("DropBox", "")
        if (!dropBoxUrl.isNullOrEmpty()) {
            dbLinkView.setText(dropBoxUrl)
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

            if (backupDB.exists()) {
                backupDB.delete()
            }

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
        val fileListGroup = findViewById<RadioGroup>(R.id.laai_filelist)
        val loadButton = findViewById<Button>(R.id.laai_laai)

        if (this.fileList.isEmpty()) {
            loadButton.visibility = View.GONE
            return
        }

        loadButton.visibility = View.VISIBLE

        for (i in this.fileList.indices) {
            addFileRadioButton(fileListGroup, i)
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
        val fromMenu = settings.getBoolean("FROM_MENU", false)
        val dbLinkView = findViewById<EditText>(R.id.db_link)

        if (!fromMenu && AutoDL && dbLinkView.text.toString() != getString(R.string.dbLink)) {
            settings.edit().putBoolean("FROM_MENU", false).apply()
            findViewById<Button>(R.id.dbLinkButton).performClick()
        }
    }

    private fun getFileList(searchpath: String?): ArrayList<HashMap<String, String>> {
        println(searchpath)
        if (searchpath != null) {
            val home = File(searchpath)
            val listFiles = home.listFiles()
            if (listFiles != null) {
                for (file in listFiles) {
                    println(file.absolutePath)
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

        if (!checkPermission()) return false

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            inputStream = FileInputStream(sourceFile)
            outputStream = FileOutputStream("$dbPath/$DB_NAME")
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Laai Nuwe Data stream failed", e)
            showError("Kan databasis lêer nie oopmaak nie")
            return false
        }

        try {
            writeExtractedFileToDisk(inputStream!!, outputStream!!)
            result = true
        } catch (e: IOException) {
            Log.e(TAG, "Write ExtractedFileToDisk failed", e)
            showError("Fout tydens skryf van databasis")
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
                if (sourceFile.exists()) {
                    sourceFile.canonicalFile.delete()
                    if (sourceFile.exists()) applicationContext.deleteFile(sourceFile.name)
                }
            } catch (e: IOException) {
                Log.e(TAG, "File Delete failed", e)
            }
        }

        // Backup existing INFO_DB (unchanged logic, already safe)
        try {
            val dataDir = File(applicationInfo.dataDir, "/databases/")
            val currentDB = File(dataDir, INFO_DB)
            val backupDB = File(winkerkEntry.getWkrDir(this), INFO_DB)
            if (backupDB.exists()) backupDB.delete()
            if (currentDB.exists()) {
                FileInputStream(currentDB).channel.use { src ->
                    FileOutputStream(backupDB).channel.use { dst ->
                        src.transferTo(0, src.size(), dst)
                    }
                }
            }
            MediaScannerConnection.scanFile(this, arrayOf(backupDB.absolutePath), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up database", e)
        }

        if (syncPhotosAfterDb) startPhotoSync()
        return result
    }

    private fun checkPermission(): Boolean {
        // Scoped storage does not require READ/WRITE permissions for app-specific directories
        return true
    }

    private fun downloadFromDropBoxUrl(url: String) {
        val context = this
        val dbPath = context.applicationInfo.dataDir + "/databases/"

        if (!isDownloadManagerAvailable()) return

        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        recieverDownloadComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (manager == null) {
                    showError("DownloadManager not available")
                    return
                }

                val reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (myDownloadReference != reference) return

                val query = DownloadManager.Query().setFilterById(reference)
                manager.query(query).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val fileNameIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        if (statusIdx >= 0 && reasonIdx >= 0 && fileNameIdx >= 0) {
                            val status = cursor.getInt(statusIdx)
                            val reason = cursor.getInt(reasonIdx)

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    val downloadUri = manager.getUriForDownloadedFile(reference)
                                    if (downloadUri == null) {
                                        showError("Download succeeded but file URI is null")
                                        return
                                    }

                                    findViewById<Button>(R.id.dbLinkButton).visibility = View.VISIBLE
                                    contentResolver.call(WinkerkContract.winkerkEntry.CONTENT_URI, "closeDatabase", null, null)

                                    var input: InputStream? = null
                                    var output: OutputStream? = null
                                    try {
                                        input = contentResolver.openInputStream(downloadUri)
                                        if (input == null) {
                                            showError("Cannot open downloaded file")
                                            return
                                        }
                                        output = Files.newOutputStream(File("$dbPath/$DB_NAME").toPath())
                                        val buffer = ByteArray(1024)
                                        var len: Int
                                        while (input.read(buffer).also { len = it } != -1) {
                                            output!!.write(buffer, 0, len)
                                        }
                                    } catch (e: IOException) {
                                        Log.e(TAG, "Write outbuffer failed", e)
                                        showError("Failed to save database: ${e.message}")
                                    } finally {
                                        input?.close()
                                        output?.close()
                                    }
                                    try { unregisterReceiver(recieverDownloadComplete) } catch (_: IllegalArgumentException) {}
                                    reloadDatabaseAndFinish()
                                    return
                                }
                                DownloadManager.STATUS_FAILED -> showError("Download failed: $reason")
                                DownloadManager.STATUS_PAUSED -> showError("Download paused: $reason")
                                DownloadManager.STATUS_PENDING -> showError("Download pending...")
                                DownloadManager.STATUS_RUNNING -> showError("Download running...")
                            }
                        }
                    }
                }
                try { unregisterReceiver(recieverDownloadComplete) } catch (_: IllegalArgumentException) {}
            }
        }
        registerReceiver(recieverDownloadComplete, intentFilter, RECEIVER_EXPORTED)

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDescription("WinkerkReader Database Download")
            setTitle(WINKERK_DB)
            setMimeType("application/vnd.sqlite3")
            setVisibleInDownloadsUi(true)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, DB_NAME)
        }

        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        myDownloadReference = manager.enqueue(request)
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            laai_boodskap.text = message
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
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD
        }

        fun checkIPv4(s: String): Boolean {
            val reg0To255 = "(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])"
            val regex = "$reg0To255\\.$reg0To255\\.$reg0To255\\.$reg0To255"
            val p = Pattern.compile(regex)
            val m = p.matcher(s)
            return m.matches()
        }
    }
}