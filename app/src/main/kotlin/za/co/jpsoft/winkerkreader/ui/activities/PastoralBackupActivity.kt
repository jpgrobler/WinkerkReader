package za.co.jpsoft.winkerkreader.ui.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.databinding.ActivityPastoralBackupBinding
import za.co.jpsoft.winkerkreader.ui.adapters.BackupListAdapter
import za.co.jpsoft.winkerkreader.utils.db.PastoralDatabaseBackup
import za.co.jpsoft.winkerkreader.utils.db.PastoralDatabaseBackup.BackupFileInfo
import java.io.File

@AndroidEntryPoint
class PastoralBackupActivity : BaseActivity() {

    @Inject
    lateinit var pastoralDbBackup: PastoralDatabaseBackup

    private lateinit var binding: ActivityPastoralBackupBinding
    private lateinit var backupAdapter: BackupListAdapter

    private val pickBackupFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            lifecycleScope.launch {
                val result = pastoralDbBackup.importFromUri(this@PastoralBackupActivity, uri)
                handleImportResult(result)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPastoralBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.backupScroll) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Pastorale Rugsteun & Herstel"

        setupRecyclerView()
        setupButtons()
        refreshBackupList()
    }

    override fun onResume() {
        super.onResume()
        refreshBackupList()
    }

    private fun setupRecyclerView() {
        backupAdapter = BackupListAdapter(
            onRestore = { backupFileInfo ->
                confirmRestore(backupFileInfo)
            },
            onDelete = { backupFileInfo ->
                confirmDelete(backupFileInfo)
            }
        )
        binding.rvBackups.apply {
            adapter = backupAdapter
            layoutManager = LinearLayoutManager(this@PastoralBackupActivity)
            setHasFixedSize(true)
        }
    }

    private fun setupButtons() {
        binding.btnCreateBackup.setOnClickListener { createBackup() }
        binding.btnShareBackup.setOnClickListener { shareBackup() }
        binding.btnRestore.setOnClickListener {
            pickBackupFile.launch(arrayOf("application/octet-stream", "*/*"))
        }
        binding.btnPruneOld.setOnClickListener {
            pruneOldBackups()
        }
    }

    private fun refreshBackupList() {
        val backups = pastoralDbBackup.listBackupFiles(this)
        backupAdapter.submitList(backups)
        binding.tvBackupCount.text = "${backups.size} rugsteunlêers gevind"
    }

    // ========== Actions ==========

    private fun createBackup() {
        binding.restoreProgress.visibility = View.VISIBLE
        binding.btnCreateBackup.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val success = pastoralDbBackup.backupNow(applicationContext)
            withContext(Dispatchers.Main) {
                binding.restoreProgress.visibility = View.GONE
                binding.btnCreateBackup.isEnabled = true
                if (success) {
                    refreshBackupList()
                    Toast.makeText(
                        this@PastoralBackupActivity,
                        "Rugsteun geskep",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@PastoralBackupActivity,
                        "Kon nie rugsteun skep nie",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun shareBackup() {
        val latest = pastoralDbBackup.listBackupFiles(this).firstOrNull()
        if (latest == null) {
            Snackbar.make(
                binding.root,
                "Geen rugsteun beskikbaar om te deel nie",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", latest.file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Pastorale rugsteun")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Deel Rugsteun"))
    }

    private fun confirmRestore(backupInfo: BackupFileInfo) {
        AlertDialog.Builder(this)
            .setTitle("Herstel rugsteun")
            .setMessage("Wil jy die rugsteun van ${backupInfo.displayName} herstel? Bestaande pastorale data sal vervang word.")
            .setPositiveButton("Herstel") { _, _ ->
                performRestore(backupInfo.file)
            }
            .setNegativeButton("Kanselleer", null)
            .show()
    }

    private fun performRestore(backupFile: File) {
        lifecycleScope.launch {
            val result = pastoralDbBackup.importBackup(this@PastoralBackupActivity, backupFile)
            withContext(Dispatchers.Main) {
                if (result) {
                    Toast.makeText(
                        this@PastoralBackupActivity,
                        "Rugsteun suksesvol herstel",
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshBackupList()
                } else {
                    Toast.makeText(this@PastoralBackupActivity, "Herstel misluk", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private fun confirmDelete(backupInfo: BackupFileInfo) {
        AlertDialog.Builder(this)
            .setTitle("Verwyder rugsteun")
            .setMessage("Verwyder ${backupInfo.displayName} permanent?")
            .setPositiveButton("Verwyder") { _, _ ->
                if (backupInfo.file.delete()) {
                    refreshBackupList()
                    Toast.makeText(this, "Rugsteun verwyder", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Kon nie verwyder nie", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Kanselleer", null)
            .show()
    }

    private fun pruneOldBackups() {
        val days = 7
        AlertDialog.Builder(this)
            .setTitle("Skoonmaak ou rugsteunlêers")
            .setMessage("Alle kiekies ouer as $days dae sal verwyder word. Die mees onlangse rugsteun word behou. Gaan jy voort?")
            .setPositiveButton("Skoonmaak") { _, _ ->
                val deleted = pastoralDbBackup.pruneOldBackups(this, days)
                refreshBackupList()
                Toast.makeText(this, "$deleted lêers verwyder", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Kanselleer", null)
            .show()
    }

    private fun handleImportResult(result: PastoralDatabaseBackup.ImportResult) {
        val msg = when (result) {
            is PastoralDatabaseBackup.ImportResult.Success -> "Rugsteun herstel"
            is PastoralDatabaseBackup.ImportResult.TooNew -> "Lêer is nuwer – dateer app op"
            PastoralDatabaseBackup.ImportResult.InvalidFile -> "Ongeldige rugsteunlêer"
            PastoralDatabaseBackup.ImportResult.ReadError -> "Kon nie lêer lees nie"
            PastoralDatabaseBackup.ImportResult.Failed -> "Herstel misluk"
        }
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
        if (result is PastoralDatabaseBackup.ImportResult.Success) {
            refreshBackupList()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}