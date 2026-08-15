package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.members.setup.WinkerkDatabase
import za.co.jpsoft.winkerkreader.utils.db.DatabaseMigrationHelper
import za.co.jpsoft.winkerkreader.utils.files.ServerFileValidator
import za.co.jpsoft.winkerkreader.widget.WidgetDataRepository
import java.io.File
import java.io.FileOutputStream

class DatabaseImportController(
    private val context: Context,
    private val onError: (String) -> Unit,
    private val onReloadDone: () -> Unit
) {

    companion object {
        private const val TAG = "DatabaseImportController"
        private const val DB_NAME = winkerkEntry.WINKERK_DB
        private const val TEMP_FILE_NAME = "Winkerk.db.new"
    }

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun importFromFile(
        sourceFile: File,
        deleteSource: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) {
            reportError("Lêer nie gevind nie")
            return@withContext false
        }

        closeDatabaseConnections()

        val dbPath = ensureDatabaseDirectory() ?: return@withContext false
        val tempFile = File(dbPath, TEMP_FILE_NAME).also { it.delete() }

        try {
            sourceFile.inputStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to copy to temp file", e)
            reportError("Kon nie databasis kopieer nie: ${e.message}")
            tempFile.delete()
            return@withContext false
        }

        val success = processTempFile(tempFile)

        if (success && deleteSource) {
            try {
                sourceFile.delete()
                MediaScannerConnection.scanFile(
                    context, arrayOf(sourceFile.absolutePath), null, null
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Could not delete source file", e)
            }
        }
        success
    }

    suspend fun importFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        closeDatabaseConnections()

        val dbPath = ensureDatabaseDirectory() ?: return@withContext false
        val tempFile = File(dbPath, TEMP_FILE_NAME).also { it.delete() }

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            } ?: run {
                reportError("Kon nie lêer oopmaak nie")
                tempFile.delete()
                return@withContext false
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to copy from URI", e)
            reportError("Kon nie databasis lees nie: ${e.message}")
            tempFile.delete()
            return@withContext false
        }

        processTempFile(tempFile)
    }

    suspend fun processTempFile(tempFile: File): Boolean = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "processTempFile: validating ${tempFile.absolutePath}, size=${tempFile.length()}"
            )
        }

        // 1. Validate
        val validationResult = ServerFileValidator.checkSingleFile(tempFile)
        if (!validationResult.success) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Validation failed: ${validationResult.errorMessage}")
            }
            reportError("Aflaailêer is nie 'n geldige databasis nie: ${validationResult.errorMessage ?: "Onbekende fout"}")
            tempFile.delete()
            return@withContext false
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Validation passed. File size: ${validationResult.fileSize} bytes")
        }

        // 2. Migrate + Room verify
        if (!DatabaseMigrationHelper.migrateAndVerify(context, tempFile)) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Migration/verification failed for ${tempFile.absolutePath}")
            }
            reportError("Databasis is ongeldig – kontak ondersteuning")
            tempFile.delete()
            return@withContext false
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Migration and Room verification succeeded.")
        }

        // 3. Close global Room connection — give SQLite time to release file handles
        withContext(Dispatchers.Main) {
            WinkerkDatabase.closeInstance()
        }
        delay(200)
        System.gc()

        // 4. Atomic replace (also remove Room/SQLite sidecars from the old file)
        val dbPath = File(context.applicationInfo.dataDir, "databases")
        val dbFile = File(dbPath, DB_NAME)
        listOf(
            dbFile,
            File(dbPath, "$DB_NAME-wal"),
            File(dbPath, "$DB_NAME-shm"),
            File(dbPath, "$DB_NAME-journal")
        )
            .filter { it.exists() }
            .forEach { sidecar ->
                if (!sidecar.delete()) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Could not delete ${sidecar.name}")
                }
            }
        if (dbFile.exists()) {
            reportError("Kon bestaande databasis nie verwyder nie")
            tempFile.delete()
            return@withContext false
        }
        if (!tempFile.renameTo(dbFile)) {
            tempFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Database replaced successfully. New file: ${dbFile.absolutePath}, size=${dbFile.length()}"
            )
        }

        // 5. Open the replacement file so the next UI query does not hit a null/closed instance
        withContext(Dispatchers.Main) {
            WinkerkDatabase.getInstance(context)
        }

        true
    }

    suspend fun processDownloadedFile(downloadUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "processDownloadedFile: URI = $downloadUri")
                }

                val dbPath = context.applicationInfo.dataDir + "/databases/"
                val dbDir = File(dbPath).also {
                    if (!it.exists() && !it.mkdirs()) {
                        reportError("Kon nie databasisgids skep nie")
                        return@withContext
                    }
                }

                val tempFile = File(dbDir, TEMP_FILE_NAME).also { it.delete() }

                context.contentResolver.openInputStream(downloadUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                } ?: run {
                    reportError("Kon nie lêer oopmaak nie")
                    return@withContext
                }

                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "Temp file written: ${tempFile.absolutePath}, size=${tempFile.length()}"
                    )
                }

                val success = processTempFile(tempFile)
                if (success) {
                    withContext(Dispatchers.Main) {
                        reloadAndFinish()
                    }
                } else {
                    // processTempFile already reports an error via onError, but we can add an extra fallback.
                    // (It calls reportError internally; we don't need to double-report.)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Download processing failed", e)
                reportError("Fout met verwerking: ${e.message}")
            }
        }
    }

    fun reloadAndFinish() {
        try {
            context.contentResolver.call(
                winkerkEntry.CONTENT_URI, "reloadDatabase", null, null
            )
            WidgetDataRepository.invalidateCache()
            Handler(Looper.getMainLooper()).postDelayed({
                onReloadDone()
            }, 200)
        } catch (e: IllegalStateException) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Room schema error during reload", e)
            val dbFile = File(context.applicationInfo.dataDir, "databases/$DB_NAME")
            if (dbFile.exists()) dbFile.delete()
            onError("Databasis fout – kontak ondersteuning")
            onReloadDone()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error during database reload", e)
            onReloadDone()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun closeDatabaseConnections() {
        withContext(Dispatchers.Main) {
            WinkerkDatabase.closeInstance()
        }
    }

    private suspend fun ensureDatabaseDirectory(): File? {
        val dbPath = File(context.applicationInfo.dataDir, "databases")
        if (!dbPath.exists() && !dbPath.mkdirs()) {
            reportError("Kon nie databasisgids skep nie")
            return null
        }
        return dbPath
    }

    private suspend fun reportError(message: String) {
        withContext(Dispatchers.Main) { onError(message) }
    }
}