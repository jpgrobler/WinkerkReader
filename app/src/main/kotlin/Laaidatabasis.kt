package za.co.jpsoft.winkerkreader

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
import za.co.jpsoft.winkerkreader.data.SpinnerAdapter
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.DATA_DATUM
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_DB
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WkrDir
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WINKERK_DB
import za.co.jpsoft.winkerkreader.data.winkerk_DB_Helper
import java.io.*
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

class Laaidatabasis : AppCompatActivity() {

    private lateinit var settings: SharedPreferences
    private var currentWorkInfoLiveData: LiveData<WorkInfo?>? = null
    private var workInfoObserver: Observer<WorkInfo?>? = null

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

    private var receiveFileTaskUSB = ReceiveFileTask()
    private var receiveFileTaskWiFi = ReceiveFileTask()

    private val weeksdagArray = arrayOf("Sondag", "Maandag", "Dinsdag", "Woensdag", "Donderdag", "Vrydag", "Saterdag")
    private var FlagCancelledUSB = false
    private var FlagCancelledWiFi = false

    // Activity Result Launcher for file picker
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val fileUri = data?.data
            if (fileUri != null) {
                val filePath = fileUri.path
                if (filePath != null) {
                    val context = this
                    val dbPath = context.applicationInfo.dataDir + "/databases/"
                    var `in`: InputStream? = null
                    var out: OutputStream? = null
                    try {
                        `in` = contentResolver.openInputStream(fileUri)
                        out = Files.newOutputStream(Paths.get(dbPath + "/" + DB_NAME))
                        val buffer = ByteArray(1024)
                        var len: Int
                        while (`in`!!.read(buffer).also { len = it } != -1) {
                            out!!.write(buffer, 0, len)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Write buffer out failed", e)
                    } finally {
                        try {
                            `in`?.close()
                            out?.close()
                        } catch (e: IOException) {
                            Log.e(TAG, "Stream close failed", e)
                        }
                    }
                    try {
                        unregisterReceiver(recieverDownloadComplete)
                    } catch (_: IllegalArgumentException) {
                    }
                    performDatabaseReloadAndRestart()
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.laaidatabasis)

        settings = getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
        initializeSettings()

        requestPermissionsIfNeeded()

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
        try {
            if (recieverDownloadComplete != null) {
                unregisterReceiver(recieverDownloadComplete)
            }
        } catch (e: Exception) {
            Log.e("WinkerkReader Laaidatabasis", "Error: $e")
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun initializeSettings() {
        AutoDL = settings.getBoolean("AUTO_DL", false)
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 787)
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 786)
        }
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
        val weeksdagStatusAdapter = SpinnerAdapter(this, null, weeksdagArray)
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
                        Toast.makeText(this@Laaidatabasis, message, Toast.LENGTH_LONG).show()
                    } else if (workInfo.state == WorkInfo.State.FAILED) {
                        photoStatus.text = "Misluk"
                        photoStatus.visibility = View.VISIBLE
                        Toast.makeText(this@Laaidatabasis, "Foto-sinkronisasie het misluk", Toast.LENGTH_SHORT).show()
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
        val intent = Intent(this, MainActivity2::class.java).apply {
            putExtra("SENDER_CLASS_NAME", "WysVerjaar")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        WinkerkContract.winkerkEntry.SORTORDER = "VERJAAR"
        WinkerkContract.winkerkEntry.SOEKLIST = false
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
            performDatabaseReloadAndRestart()
        } else {
            Toast.makeText(this, "Onsuksesvol", Toast.LENGTH_SHORT).show()
            navigateToMainActivity()
        }

        deleteButton.isChecked = false
        radioGroup.clearCheck()
    }

    private fun resetGemeenteSettings() {
        WinkerkContract.winkerkEntry.GEMEENTE_NAAM = ""
        WinkerkContract.winkerkEntry.GEMEENTE_EPOS = ""

        settings.edit()
            .putString("Gemeente", "")
            .putString("Gemeente_Epos", "")
            .putString("DATA_DATUM", "")
            .apply()
    }

    private fun performDatabaseReloadAndRestart() {
        try {
            contentResolver.call(WinkerkContract.winkerkEntry.CONTENT_URI, "reloadDatabase", null, null)
            val dummy = contentResolver.query(WinkerkContract.winkerkEntry.CONTENT_URI, null, null, null, null)
            dummy?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during database reload", e)
        }
        val intent = Intent(this, MainActivity2::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun handlePickFile() {
        val chooseFile = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        pickFileLauncher.launch(Intent.createChooser(chooseFile, "Kies die databasis"))

        findViewById<Button>(R.id.laai_picker).setBackgroundColor(Color.GREEN)
    }

    private fun handleNetworkTransfer() {
        val networkButton = findViewById<Button>(R.id.laai_socket)
        val ipAddress = findViewById<EditText>(R.id.server_ip)
        laai_boodskap = findViewById(R.id.laai_boodskap)

        if (FlagCancelledWiFi) {
            networkButton.background.clearColorFilter()
            receiveFileTaskWiFi.cancel()
            laai_boodskap.text = "Aflaai gekanselleer"
            FlagCancelledWiFi = false
        } else {
            val ipText = ipAddress.text.toString()
            if (ipText.isNotEmpty() && checkIPv4(ipText)) {
                networkButton.background.setColorFilter(Color.YELLOW, PorterDuff.Mode.DARKEN)
                saveIPAddress(ipText)
                SERVER_IP = ipText
                SERVER_PORT = 49514
                startWiFiFileTransfer()
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
            usbButton.background.clearColorFilter()
            ipAddress.setText("")
            receiveFileTaskUSB.cancel()
            laai_boodskap.text = "Aflaai gekanselleer"
            FlagCancelledUSB = false
        } else {
            usbButton.background.setColorFilter(Color.YELLOW, PorterDuff.Mode.DARKEN)
            ipAddress.setText("127.0.0.1")
            SERVER_IP = "localhost"
            SERVER_PORT = 49514
            startUSBFileTransfer()
            FlagCancelledUSB = true
        }
    }

    private fun saveIPAddress(ipAddress: String) {
        settings.edit().putString("IP", ipAddress).apply()
    }

    private fun startWiFiFileTransfer() {
        receiveFileTaskWiFi.cancel() // Cancel any ongoing transfer
        receiveFileTaskWiFi = ReceiveFileTask() // Create fresh instance
        receiveFileTaskWiFi.execute()
    }

    private fun startUSBFileTransfer() {
        receiveFileTaskUSB.cancel()
        receiveFileTaskUSB = ReceiveFileTask()
        receiveFileTaskUSB.execute()
    }

    private fun initializeProgressBars() {
        findViewById<ProgressBar>(R.id.laai_indeterminateBar).visibility = View.GONE
        findViewById<ProgressBar>(R.id.laai_indeterminateBar2).visibility = View.GONE
    }

    private fun initializeDataInfo() {
        findViewById<TextView>(R.id.datadate).text = "Huidige Data: $DATA_DATUM"
        val dbLinkView = findViewById<EditText>(R.id.db_link)
        val dropBoxUrl = settings.getString("DropBox", "")
        if (!dropBoxUrl.isNullOrEmpty()) {
            dbLinkView.setText(dropBoxUrl)
        }
    }

    private fun scanForDatabaseFiles() {
        try {
            getFileList(MEDIA_PATH)
            getFileList(Environment.getExternalStorageDirectory().toString() + "/WinkerkReader/")
            getFileList(MEDIA_PATH2)
        } catch (e: Exception) {
            Log.e("WinkerkReader Laaidatabasis", "Error scanning files: $e")
        }

        backupCurrentDatabase()
    }

    private fun backupCurrentDatabase() {
        try {
            val dataDir = File(applicationInfo.dataDir, "/databases/")
            val currentDB = File(dataDir, INFO_DB)
            val backupDB = File(WkrDir, INFO_DB)

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
            Log.e("WinkerkReader Laaidatabasis", "Error backing up database: $e")
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
            text = "${this@Laaidatabasis.fileList[index]["Path"]}\n${size} Mb$additionalData"
            id = index
            background = ContextCompat.getDrawable(this@Laaidatabasis, R.drawable.border2)
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
            Log.e("WinkerkReader Laaidatabasis", "Error reading database info: $e")
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
                performDatabaseReloadAndRestart()
            } else {
                Toast.makeText(this, "WKR - Dropbox Databasis laai was onsuksesvol", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "WKR - Dropbox Databasis te klein", Toast.LENGTH_LONG).show()
        }
        finish()
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
        winkerk_DB_Helper.closeInstance(WINKERK_DB)
        winkerk_DB_Helper.closeInstance(INFO_DB)

        val context = this
        val dbPath = context.applicationInfo.dataDir + "/databases/"

        var result = false
        val sourceFile = File(nfile)

        if (checkPermission()) {
            var `is`: InputStream? = null
            try {
                `is` = FileInputStream(sourceFile)
                result = true
            } catch (e: FileNotFoundException) {
                Log.e(TAG, "Laai Nuwe Data Input stream failed", e)
                result = false
            }

            if (result) {
                var dest: OutputStream? = null
                try {
                    dest = FileOutputStream("$dbPath/$DB_NAME")
                } catch (e: FileNotFoundException) {
                    val mediaStorageDir = File(applicationInfo.dataDir, "/databases/")
                    mediaStorageDir.mkdirs()
                    try {
                        dest = Files.newOutputStream(Paths.get("$dbPath/$DB_NAME"))
                    } catch (ss: IOException) {
                        Log.e(TAG, "Laai Nuwe Data Output stream failed", ss)
                        result = false
                    }
                }

                try {
                    writeExtractedFileToDisk(`is`!!, dest!!)
                    result = true
                } catch (e: IOException) {
                    Log.e(TAG, "Write ExtractedFileToDisk failed", e)
                    result = false
                }

                if (delete) {
                    try {
                        val absolutePathToFile = sourceFile.absolutePath
                        sourceFile.delete()
                        MediaScannerConnection.scanFile(this, arrayOf(absolutePathToFile), null, null)
                        if (sourceFile.exists()) {
                            sourceFile.canonicalFile.delete()
                            if (sourceFile.exists()) {
                                applicationContext.deleteFile(sourceFile.name)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "File Delete failed", e)
                    }
                }

                try {
                    val data = File(applicationInfo.dataDir, "/databases/")
                    val currentDBPath = "/$INFO_DB"
                    val backupDBPath = "/$INFO_DB"
                    val currentDB = File(data, currentDBPath)
                    val backupDB = File(WkrDir, backupDBPath)

                    if (backupDB.exists()) {
                        backupDB.canonicalFile.delete()
                        if (backupDB.exists()) {
                            applicationContext.deleteFile(backupDB.name)
                        }
                    }

                    if (currentDB.exists()) {
                        FileInputStream(currentDB).channel.use { src ->
                            FileOutputStream(backupDB).channel.use { dst ->
                                src.transferTo(0, src.size(), dst)
                            }
                        }
                    }

                    MediaScannerConnection.scanFile(this, arrayOf(backupDB.absolutePath), null, null)
                } catch (e: Exception) {
                    Log.e("WinkerkReader Laaidatabasis", "Error: $e")
                }
            }
        }

        if (syncPhotosAfterDb) {
            startPhotoSync()
        }

        return result
    }

    private fun checkPermission(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return if (result == PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun downloadFromDropBoxUrl(url: String) {
        val context = this
        val dbPath = context.applicationInfo.dataDir + "/databases/"

        if (isDownloadManagerAvailable()) {
            val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            recieverDownloadComplete = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (myDownloadReference == reference) {
                        val laai_boodskap = findViewById<TextView>(R.id.laai_boodskap)
                        val query = DownloadManager.Query().setFilterById(reference)
                        manager.query(query).use { cursor ->
                            if (cursor.moveToFirst()) {
                                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val fileNameIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                if (statusIdx >= 0 && reasonIdx >= 0 && fileNameIdx >= 0) {
                                    val status = cursor.getInt(statusIdx)
                                    val reason = cursor.getInt(reasonIdx)
                                    val downloadFileLocalUri = cursor.getString(fileNameIdx).replace("file://", "")
                                    val downloadFileLocalUri2 = manager.getUriForDownloadedFile(reference)

                                    when (status) {
                                        DownloadManager.STATUS_SUCCESSFUL -> {
                                            findViewById<Button>(R.id.dbLinkButton).visibility = View.VISIBLE

                                            var `in`: InputStream? = null
                                            var out: OutputStream? = null
                                            try {
                                                `in` = contentResolver.openInputStream(downloadFileLocalUri2!!)
                                                out = Files.newOutputStream(File("$dbPath/$DB_NAME").toPath())
                                                val buffer = ByteArray(1024)
                                                var len: Int
                                                while (`in`!!.read(buffer).also { len = it } != -1) {
                                                    out!!.write(buffer, 0, len)
                                                }
                                            } catch (e: IOException) {
                                                Log.e(TAG, "Write outbuffer failed", e)
                                            } finally {
                                                try {
                                                    `in`?.close()
                                                    out?.close()
                                                } catch (e: IOException) {
                                                    Log.e(TAG, "Stream close failed", e)
                                                }
                                                try {
                                                    unregisterReceiver(recieverDownloadComplete)
                                                } catch (_: IllegalArgumentException) {
                                                }
                                                performDatabaseReloadAndRestart()
                                                return
                                            }
                                        }
                                        DownloadManager.STATUS_FAILED -> Toast.makeText(this@Laaidatabasis, "FAILED: $reason", Toast.LENGTH_LONG).show()
                                        DownloadManager.STATUS_PAUSED -> Toast.makeText(this@Laaidatabasis, "PAUSED: $reason", Toast.LENGTH_LONG).show()
                                        DownloadManager.STATUS_PENDING -> Toast.makeText(this@Laaidatabasis, "PENDING! ", Toast.LENGTH_LONG).show()
                                        DownloadManager.STATUS_RUNNING -> Toast.makeText(this@Laaidatabasis, "RUNNING! ", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        try {
                            unregisterReceiver(recieverDownloadComplete)
                        } catch (_: IllegalArgumentException) {
                        }
                    }
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

    companion object {
        private const val TAG = "Laaidatabasis"
        private const val DB_NAME = WINKERK_DB
        private const val PICKFILE_RESULT_CODE = 1
        private const val MEDIA_PATH = "/storage/emulated/0/"
        private const val MEDIA_PATH2 = "/storage/emulated/0/Download/"
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

    // -------------------------------------------------------------------------
    // Inner class ReceiveFileTask
    // -------------------------------------------------------------------------
    private inner class ReceiveFileTask {
        private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())
        @Volatile
        private var isCancelled = false

        fun execute() {
            onPreExecute()
            executorService.submit {
                try {
                    doInBackground()
                } finally {
                    onPostExecute()
                    executorService.shutdown()
                }
            }
        }

        fun cancel() {
            isCancelled = true
            executorService.shutdownNow()
            onCancelled()
        }

        private fun onCancelled() {
            Log.d(Laaidatabasis::class.java.canonicalName, "ReceiveFileTask USB / WIFI was canceled")
        }

        private fun onPostExecute() {
            mainHandler.post {
                findViewById<TextView>(R.id.laai_boodskap).text = "Klaar"
            }
        }

        private fun onPreExecute() {
            mainHandler.post {
                findViewById<TextView>(R.id.laai_boodskap).text = "Begin"
            }
        }

        private fun doInBackground() {
            val retryAttempts = AtomicInteger(5)
            val retryInterval = 2000
            var connected = false
            var socket: Socket? = null
            var ackSocket: Socket? = null
            var checksumSocket: Socket? = null

            while (!connected && retryAttempts.get() > 0 && !isCancelled) {
                try {
                    socket = Socket(SERVER_IP, SERVER_PORT)
                    ackSocket = Socket(SERVER_IP, SERVER_PORT + 1)
                    checksumSocket = Socket(SERVER_IP, SERVER_PORT + 2)
                    connected = true
                } catch (e: IOException) {
                    val attemptsRemaining = retryAttempts.decrementAndGet()
                    mainHandler.post {
                        findViewById<TextView>(R.id.laai_boodskap).text = "Waiting for server... Attempts remaining: $attemptsRemaining"
                    }
                    try {
                        Thread.sleep(retryInterval.toLong())
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }

            if (connected) {
                mainHandler.post {
                    findViewById<TextView>(R.id.laai_boodskap).text = "Server connected. Starting download..."
                }
                startFileTransfer(socket!!, ackSocket!!, checksumSocket!!)
            } else {
                mainHandler.post {
                    findViewById<TextView>(R.id.laai_boodskap).text = "Unable to connect to the server after retries."
                }
            }
        }

        private fun startFileTransfer(socket: Socket, ackSocket: Socket, checksumSocket: Socket) {
            var buffer = ByteArray(8192)
            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            var bufferedOutputStream: BufferedOutputStream? = null
            var ackWriter: BufferedWriter? = null
            var checksumReader: BufferedReader? = null

            try {
                Log.d(TAG, "Getting input stream...")
                inputStream = socket.getInputStream()
                Log.d(TAG, "Input stream obtained")
                outputStream = socket.getOutputStream()
                Log.d(TAG, "Output stream obtained")

                val fileName = "WinkerReader.sqlite"
                val filePath = getExternalFilesDir(null)!!.absolutePath + "/" + fileName

                bufferedOutputStream = BufferedOutputStream(FileOutputStream(getExternalFilesDir(null)!!.absolutePath + "/" + fileName))
                ackWriter = BufferedWriter(OutputStreamWriter(ackSocket.getOutputStream()))
                checksumReader = BufferedReader(InputStreamReader(checksumSocket.getInputStream()))
                val reader = BufferedReader(InputStreamReader(inputStream))

                Log.d(TAG, "Waiting for file size...")
                val fileSizeString = reader.readLine()
                Log.d(TAG, "Received file size: $fileSizeString")
                val fileSize = fileSizeString.toLong()
                ackWriter.write("ACK\n")
                ackWriter.flush()
                val bufferSizeString = reader.readLine()
                val bufferSize = bufferSizeString.toInt()
                ackWriter.write("ACK\n")
                ackWriter.flush()
                buffer = ByteArray(bufferSize)
                var totalBytesReceived = 0L
                var chunks = 0
                ackWriter.write("ACK\n")
                ackWriter.flush()

                while (totalBytesReceived < fileSize) {
                    var totalBytesRead = 0
                    val chunkSize = buffer.size

                    while (totalBytesRead < chunkSize) {
                        val bytesRead = inputStream.read(buffer, totalBytesRead, chunkSize - totalBytesRead)
                        if (bytesRead == -1) {
                            throw IOException("Connection closed prematurely.")
                        }
                        totalBytesRead += bytesRead
                        if (totalBytesReceived + totalBytesRead.toLong() == fileSize) {
                            break
                        }
                    }

                    ackWriter.write("ACK\n")
                    ackWriter.flush()
                    chunks++
                    Log.e("FileTransfer", "Chunk #$chunks")
                    Log.e("FileTransfer", "Chunk size $totalBytesRead")
                    val checksumString = checksumReader.readLine()
                    ackWriter.write("ACK\n")
                    ackWriter.flush()
                    val chunkChecksum = calculateChecksum(buffer, 0, totalBytesRead)
                    if (chunkChecksum != checksumString) {
                        Log.e("FileTransfer", "Checksum mismatch for chunk. Aborting.")
                        ackWriter.write("ERROR\n")
                        ackWriter.flush()
                    } else {
                        Log.e("FileTransfer", "Checksum valid.")
                        ackWriter.write("ACK\n")
                        ackWriter.flush()
                        totalBytesReceived += totalBytesRead.toLong()
                        bufferedOutputStream.write(buffer, 0, totalBytesRead)
                        bufferedOutputStream.flush()
                    }

                    val progress = (totalBytesReceived.toFloat() / fileSize.toFloat() * 100).toInt()
                    runOnUiThread {
                        findViewById<TextView>(R.id.laai_boodskap).text = "Received: $progress%"
                    }
                }

                Log.d("FileTransfer", "File transfer complete. File saved to: $filePath")
                runOnUiThread {
                    findViewById<TextView>(R.id.laai_boodskap).text = "File transfer complete."
                }
                processDownloadedFile(filePath, fileName)

            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    findViewById<TextView>(R.id.laai_boodskap).text = "Error receiving file."
                }
            } finally {
                try {
                    bufferedOutputStream?.close()
                    inputStream?.close()
                    outputStream?.close()
                    if (!socket.isClosed) socket.close()
                    checksumReader?.close()
                    ackWriter?.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing file transfer streams", e)
                }
            }
        }

        private fun processDownloadedFile(filePath: String, fileName: String) {
            val deleteButton = findViewById<RadioButton>(R.id.laai_wisuit)
            delete = deleteButton.isChecked
            if (LaaiNuweData(filePath)) {
                runOnUiThread { performDatabaseReloadAndRestart() }
            } else {
                Toast.makeText(this@Laaidatabasis, "File loading failed.", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@Laaidatabasis, MainActivity2::class.java).apply {
                    putExtra("SENDER_CLASS_NAME", "Laaidatabasis")
                }
                startActivity(intent)
                finish()
            }
            deleteButton.isChecked = false
        }

        private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): String? {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(data, offset, length)
                val hashBytes = digest.digest()
                val hexString = StringBuilder()
                for (b in hashBytes) {
                    hexString.append(String.format("%02x", b))
                }
                hexString.toString()
            } catch (e: NoSuchAlgorithmException) {
                Log.e(TAG, "SHA-256 algorithm not available for checksum", e)
                null
            }
        }
    }
}