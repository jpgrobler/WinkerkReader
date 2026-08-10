package za.co.jpsoft.winkerkreader.ui.activities

import android.Manifest

import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.members.repository.ChurchInfoRepository
import za.co.jpsoft.winkerkreader.data.members.setup.WinkerkDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.setup.PastoralDemoDataSeeder
import za.co.jpsoft.winkerkreader.databinding.LaaidatabasisBinding
import za.co.jpsoft.winkerkreader.ui.controllers.CollapsibleCardController
import za.co.jpsoft.winkerkreader.ui.controllers.DatabaseImportController
import za.co.jpsoft.winkerkreader.ui.controllers.DropboxDownloadController
import za.co.jpsoft.winkerkreader.ui.controllers.LocalDatabaseFileController
import za.co.jpsoft.winkerkreader.ui.controllers.NetworkTransferController
import za.co.jpsoft.winkerkreader.ui.controllers.PhotoSyncController
import za.co.jpsoft.winkerkreader.utils.db.PastoralDatabaseBackup
import za.co.jpsoft.winkerkreader.utils.network.CloudUrlTransformer
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs
import za.co.jpsoft.winkerkreader.utils.prefs.BackupPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.SyncPrefs
import za.co.jpsoft.winkerkreader.utils.ui.MainNavigationController
import java.io.File
import java.util.regex.Pattern


@AndroidEntryPoint
class LaaiDatabasisActivity : BaseActivity() {

    companion object {
        private const val TAG = "LaaiDatabasisActivity"
        const val DB_NAME = WinkerkContract.winkerkEntry.WINKERK_DB
        const val EXTRA_PROMPT_RESTORE = "pastoral_prompt_restore"
        private val CURRENT_PASTORAL_SCHEMA_VERSION
            get() = PastoralDatabaseBackup.CURRENT_PASTORAL_SCHEMA_VERSION

        fun isDownloadManagerAvailable(): Boolean = true

        fun checkIPv4(s: String): Boolean {
            val reg0To255 = "(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])"
            val regex = "$reg0To255\\.$reg0To255\\.$reg0To255\\.$reg0To255"
            val p = Pattern.compile(regex)
            val m = p.matcher(s)
            return m.matches()
        }
    }

    // ─── Injected Preferences ──────────────────────────────────────────────────
    @Inject
    lateinit var syncPrefs: SyncPrefs
    @Inject
    lateinit var memberListPrefs: MemberListPrefs
    @Inject
    lateinit var congregationPrefs: CongregationPrefs
    @Inject
    lateinit var backupPrefs: BackupPrefs
    @Inject
    lateinit var appearancePrefs: AppearancePrefs
    @Inject
    lateinit var pastoralDbBackup: PastoralDatabaseBackup

    private lateinit var importController: DatabaseImportController
    private lateinit var binding: LaaidatabasisBinding
    private lateinit var collapsibleCardController: CollapsibleCardController
    private lateinit var fileListController: LocalDatabaseFileController
    private lateinit var photoSyncController: PhotoSyncController
    private lateinit var dropboxController: DropboxDownloadController
    private lateinit var networkController: NetworkTransferController

    @Inject
    lateinit var churchInfoRepo: ChurchInfoRepository
    private val navigationController by lazy { MainNavigationController(this) }

    @Inject
    lateinit var pastoralDemoDataSeeder: PastoralDemoDataSeeder
    private var autoDl = false
    private var delete: Boolean = false
    private var syncPhotosAfterDb: Boolean = false
    private var fromMenu: Boolean = true
    private var pcProtocolVersion: String = "v2"

    // Raw SharedPreferences only for controllers that still need it
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
                val success = importController.importFromUri(uri)
                binding.laaiIndeterminateBar.visibility = View.GONE
                if (success) {
                    Toast.makeText(
                        this@LaaiDatabasisActivity,
                        "Databasis suksesvol gelaai",
                        Toast.LENGTH_SHORT
                    ).show()
                    importController.reloadAndFinish()
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

    private fun cancelOngoingDownloads() {
        dropboxController.cancel()
        networkController.cancel()
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

        importController = DatabaseImportController(
            context = this,
            onError = { msg -> showError(msg) },
            onReloadDone = {
                // Reload gemeente names and emails from the newly imported database
                lifecycleScope.launch {
                    churchInfoRepo.loadChurchInfo()
                    congregationPrefs.ensureDefaultColors()
                    pastoralDemoDataSeeder.clearDemoData()
                    navigateBackToMain()
                }
            }
        )

        lifecycleScope.launch {
            refreshDatabaseDateSuspend()
            updateDateDisplay()
        }

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

        binding.serverIp.setText(syncPrefs.serverIp)

        photoSyncController = PhotoSyncController(
            lifecycleOwner = this,
            workManager = WorkManager.getInstance(this),
            syncPrefs = syncPrefs,
            progressBar = binding.photoSyncProgress,
            statusLabel = binding.photoSyncStatus,
            syncButton = binding.startPhotoSync,
            forceSyncCheck = { binding.forceSyncCheck.isChecked },
            protocolVersion = { pcProtocolVersion }
        )
        binding.startPhotoSync.setOnClickListener { photoSyncController.startSync() }

        dropboxController = DropboxDownloadController(
            context = this,
            lifecycleScope = lifecycleScope,
            onFileReady = { uri -> importController.processDownloadedFile(uri) },
            onError = { msg -> showError(msg) },
            onProgress = { bytes, total ->
                val pct = if (total > 0) (bytes * 100 / total).toInt() else 0
                binding.laaiBoodskap.text = getString(R.string.download_received_percent, pct)
            }
        )

        networkController = NetworkTransferController(
            lifecycleOwner = this,
            lifecycleScope = lifecycleScope,
            workManager = WorkManager.getInstance(this),
            serverIpInput = binding.serverIp,
            progressLabel = binding.laaiBoodskap,
            wifiButton = binding.laaiSocket,
            usbButton = binding.laaiUSB,
            protocolVersion = { pcProtocolVersion },
            saveIp = { ip -> syncPrefs.serverIp = ip },  // ✅ use typed prefs
            onFileDownloaded = { file ->
                val ok = importController.processTempFile(file)
                if (ok) {
                    // Reopen Room + ContentProvider; navigate via onReloadDone
                    withContext(Dispatchers.Main) {
                        importController.reloadAndFinish()
                    }
                }
                ok
            },
            // reloadAndFinish already navigates; avoid a second Main launch
            onNavigateBack = { }
        )

        initializeButtons()
        initializeVersionToggle()

        collapsibleCardController = CollapsibleCardController(syncPrefs)
        collapsibleCardController.setupAll(binding)
        initializeProgressBars()
        initializeDataInfo()

        fileListController = LocalDatabaseFileController(
            activity = this,
            fileListGroup = binding.laaiFilelist,
            loadButton = binding.laaiLaai
        )
        fileListController.scan(winkerkEntry.getWkrDir(this))
        fileListController.setupUI()

        handleIntentExtras()

        syncPhotosAfterDb = syncPrefs.syncPhotos
        binding.syncPhotos.isChecked = syncPhotosAfterDb
        binding.syncPhotos.setOnCheckedChangeListener { _, isChecked ->
            syncPrefs.syncPhotos = isChecked
            syncPhotosAfterDb = isChecked
        }

        handleAutomaticDownload()
    }

    override fun onDestroy() {
        super.onDestroy()
        dropboxController.cancel()
        networkController.cancel()
        photoSyncController.cleanup()
    }

    private fun initializeSettings() {
        autoDl = syncPrefs.autoDl
        pcProtocolVersion = syncPrefs.pcProtocolVersion
        fromMenu = syncPrefs.fromMenu
    }

    private fun initializeVersionToggle() {
        val group = binding.pcVersionGroup
        group.check(if (pcProtocolVersion == "v3") R.id.btnV3 else R.id.btnV2)

        group.setOnCheckedChangeListener { _, checkedId ->
            pcProtocolVersion = if (checkedId == R.id.btnV3) "v3" else "v2"
            syncPrefs.pcProtocolVersion = pcProtocolVersion
        }
    }

    private fun initializeButtons() {
        binding.dbLinkButton.setOnClickListener { handleDropboxDownload() }
        binding.laaiLaai.setOnClickListener { handleLoadDatabase() }
        binding.laaiPicker.setOnClickListener { handlePickFile() }
        binding.laaiSocket.setOnClickListener { networkController.handleWiFiClick() }
        binding.laaiUSB.setOnClickListener { networkController.handleUSBClick() }
    }

    private fun navigateToMainActivity() {
        memberListPrefs.defLayout = "VERJAAR"
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
        val downloadUrl = CloudUrlTransformer.transform(binding.dbLink.text.toString())
        dropboxController.startDownload(downloadUrl)
        syncPrefs.dropboxUrl = downloadUrl
        binding.laaiBoodskap.text = getString(R.string.db_dropbox_downloading)
        binding.dbLinkButton.visibility = View.INVISIBLE
        binding.laaiLocal.visibility = View.GONE
    }

    private fun handleLoadDatabase() {
        val filePath = fileListController.getSelectedPath() ?: run {
            Toast.makeText(this, "Kies asseblief 'n databasis", Toast.LENGTH_SHORT).show()
            return
        }
        delete = binding.laaiWisuit.isChecked

        lifecycleScope.launch {
            binding.laaiBoodskap.text = "Besig om databasis te laai..."
            binding.laaiIndeterminateBar.visibility = View.VISIBLE
            val success = importController.importFromFile(File(filePath), deleteSource = delete)
            binding.laaiIndeterminateBar.visibility = View.GONE

            if (success) {
                WinkerkDatabase.getInstance(this@LaaiDatabasisActivity)
                refreshDatabaseDateSuspend()
                updateDateDisplay()

                Toast.makeText(
                    this@LaaiDatabasisActivity,
                    "Databasis suksesvol gelaai",
                    Toast.LENGTH_SHORT
                ).show()
                importController.reloadAndFinish()
            } else {
                Toast.makeText(
                    this@LaaiDatabasisActivity,
                    "Kon nie databasis laai nie",
                    Toast.LENGTH_LONG
                ).show()
                binding.laaiBoodskap.text = "Laai misluk"
            }
            binding.laaiWisuit.isChecked = false
            fileListController.clearSelection()
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

    private fun initializeProgressBars() {
        binding.laaiIndeterminateBar.visibility = View.GONE
        binding.laaiIndeterminateBar2.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            refreshDatabaseDateSuspend()
            updateDateDisplay()
        }
    }

    private fun initializeDataInfo() {
        updateDateDisplay()
        val dropBoxUrl = syncPrefs.dropboxUrl
        if (dropBoxUrl.isNotEmpty()) {
            binding.dbLink.setText(dropBoxUrl)
        }
    }

    private fun updateDateDisplay() {
        val date = congregationPrefs.dataDatum
        if (date.isNotEmpty()) {
            binding.datadate.text = getString(R.string.current_data_info, date)
        } else {
            binding.datadate.text = getString(R.string.current_data_info, "Onbekend")
        }
    }

    private suspend fun refreshDatabaseDateSuspend() {
        withContext(Dispatchers.IO) {
            try {
                val date = WinkerkDatabase.getInstance(this@LaaiDatabasisActivity)
                    .datumDao()
                    .getDataDatum()
                congregationPrefs.dataDatum = date ?: ""
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "Database date loaded via Room: '${congregationPrefs.dataDatum}'"
                    )
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error reading database date from Room", e)
                congregationPrefs.dataDatum = ""
            }
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
        val file = File(filePath)
        val fileSizeKB = file.length() / 1024
        val fileSizeMB = fileSizeKB / 1024

        if (fileSizeMB < 1) {
            Snackbar.make(
                binding.root,
                "Dropbox databasis te klein ($fileSizeKB KB)",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        Snackbar.make(
            binding.root,
            "Probeer Dropbox databasis laai ($fileSizeKB KB)",
            Snackbar.LENGTH_LONG
        ).show()

        lifecycleScope.launch {
            val success = importController.importFromFile(file)
            if (success) {
                Toast.makeText(
                    this@LaaiDatabasisActivity,
                    "Databasis suksesvol gelaai",
                    Toast.LENGTH_SHORT
                ).show()
                importController.reloadAndFinish()
            } else {
                Toast.makeText(
                    this@LaaiDatabasisActivity,
                    "Databasis laai misluk",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleAutomaticDownload() {
        // Use fromMenu from syncPrefs
        if (!fromMenu && autoDl && binding.dbLink.text.toString() != getString(R.string.dbLink)) {
            syncPrefs.fromMenu = false
            binding.dbLinkButton.performClick()
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            binding.laaiBoodskap.text = message
        }
    }

    private val pickBackupFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            lifecycleScope.launch {
                val result = pastoralDbBackup.importFromUri(this@LaaiDatabasisActivity, uri)
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
}