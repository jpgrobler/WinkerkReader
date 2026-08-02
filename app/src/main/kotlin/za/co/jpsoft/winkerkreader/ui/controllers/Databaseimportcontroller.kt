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
        // 1. Validate
        if (!ServerFileValidator.checkSingleFile(tempFile).success) {
            reportError("Aflaailêer is nie 'n geldige databasis nie")
            tempFile.delete()
            return@withContext false
        }

        // 2. Migrate + Room verify
        if (!DatabaseMigrationHelper.migrateAndVerify(context, tempFile)) {
            reportError("Databasis is ongeldig – kontak ondersteuning")
            tempFile.delete()
            return@withContext false
        }

        // 3. Close global Room connection — give SQLite time to release file handles
        withContext(Dispatchers.Main) {
            WinkerkDatabase.closeInstance()
        }
        delay(200)
        System.gc()

        // 4. Atomic replace
        val dbPath = File(context.applicationInfo.dataDir, "databases")
        val dbFile = File(dbPath, DB_NAME)
        if (dbFile.exists() && !dbFile.delete()) {
            reportError("Kon bestaande databasis nie verwyder nie")
            tempFile.delete()
            return@withContext false
        }
        if (!tempFile.renameTo(dbFile)) {
            tempFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()
        }

        true
    }

    suspend fun processDownloadedFile(downloadUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
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

                val success = processTempFile(tempFile)
                if (success) {
                    withContext(Dispatchers.Main) {
                        reloadAndFinish()
                    }
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

//package za.co.jpsoft.winkerkreader.ui.controllers
//
//import android.content.Context
//import android.media.MediaScannerConnection
//import android.net.Uri
//import android.os.Handler
//import android.os.Looper
//import android.util.Log
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.withContext
//import za.co.jpsoft.winkerkreader.BuildConfig
//import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.winkerkEntry
//import za.co.jpsoft.winkerkreader.data.WinkerkDbHelper
//import za.co.jpsoft.winkerkreader.data.members.setup.WinkerkDatabase
//import za.co.jpsoft.winkerkreader.utils.db.DatabaseMigrationHelper
//import za.co.jpsoft.winkerkreader.utils.files.ServerFileValidator
//import za.co.jpsoft.winkerkreader.widget.WidgetDataRepository
//import java.io.File
//import java.io.FileOutputStream
//
///**
// * Handles the full database import pipeline for LaaiDatabasisActivity.
// *
// * Extracted from LaaiDatabasisActivity. Owns:
// *  - Copying a source (File or Uri) to a temp file
// *  - Delegating validation to [ServerFileValidator]
// *  - Delegating migration + Room verification to [DatabaseMigrationHelper]
// *  - Replacing the live database atomically
// *  - Signalling the ContentProvider to reload and invalidating widget cache
// *
// * All heavy IO runs on [Dispatchers.IO]. Callbacks are always invoked on the
// * Main dispatcher so they are safe to use for UI updates.
// *
// * ── Wiring in LaaiDatabasisActivity ──────────────────────────────────────────
// *
// *   importController = DatabaseImportController(
// *       context      = this,
// *       onError      = { msg -> showError(msg) },
// *       onReloadDone = { navigateBackToMain() }
// *   )
// *
// *   // Replace importDatabaseFromFile():
// *   lifecycleScope.launch {
// *       val ok = importController.importFromFile(file, deleteSource = delete)
// *       if (ok) importController.reloadAndFinish()
// *   }
// *
// *   // Replace importDatabaseFromUri():
// *   val ok = importController.importFromUri(uri)
// *   if (ok) importController.reloadAndFinish()
// *
// *   // Replace WorkManager SUCCEEDED handler:
// *   val ok = importController.processTempFile(tempFile)
// *   if (ok) importController.reloadAndFinish()
// *
// *   // Replace processDownloadedFile():
// *   importController.processDownloadedFile(downloadUri)
// */
//class DatabaseImportController(
//    private val context: Context,
//    private val onError: (String) -> Unit,
//    private val onReloadDone: () -> Unit
//) {
//
//    companion object {
//        private const val TAG = "DatabaseImportController"
//        private const val DB_NAME = winkerkEntry.WINKERK_DB
//        private const val TEMP_FILE_NAME = "Winkerk.db.new"
//    }
//
//    // ── Public API ────────────────────────────────────────────────────────────
//
//    /**
//     * Copies [sourceFile] to a temp file, validates, migrates, and replaces
//     * the live database. Optionally deletes [sourceFile] on success.
//     *
//     * Was [LaaiDatabasisActivity.importDatabaseFromFile].
//     */
//    suspend fun importFromFile(
//        sourceFile: File,
//        deleteSource: Boolean = false
//    ): Boolean = withContext(Dispatchers.IO) {
//        if (!sourceFile.exists()) {
//            reportError("Lêer nie gevind nie")
//            return@withContext false
//        }
//
//        closeDatabaseConnections()
//
//        val dbPath = ensureDatabaseDirectory() ?: return@withContext false
//        val tempFile = File(dbPath, TEMP_FILE_NAME).also { it.delete() }
//
//        try {
//            sourceFile.inputStream().use { input ->
//                FileOutputStream(tempFile).use { output ->
//                    input.copyTo(output)
//                    output.fd.sync()
//                }
//            }
//        } catch (e: Exception) {
//            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to copy to temp file", e)
//            reportError("Kon nie databasis kopieer nie: ${e.message}")
//            tempFile.delete()
//            return@withContext false
//        }
//
//        val success = processTempFile(tempFile)
//
//        if (success && deleteSource) {
//            try {
//                sourceFile.delete()
//                MediaScannerConnection.scanFile(
//                    context, arrayOf(sourceFile.absolutePath), null, null
//                )
//            } catch (e: Exception) {
//                if (BuildConfig.DEBUG) Log.w(TAG, "Could not delete source file", e)
//            }
//        }
//        success
//    }
//
//    /**
//     * Copies the content at [uri] (SAF file picker result) to a temp file,
//     * then validates, migrates, and replaces the live database.
//     *
//     * Was [LaaiDatabasisActivity.importDatabaseFromUri].
//     */
//    suspend fun importFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
//        closeDatabaseConnections()
//
//        val dbPath = ensureDatabaseDirectory() ?: return@withContext false
//        val tempFile = File(dbPath, TEMP_FILE_NAME).also { it.delete() }
//
//        try {
//            context.contentResolver.openInputStream(uri)?.use { input ->
//                FileOutputStream(tempFile).use { output ->
//                    input.copyTo(output)
//                    output.fd.sync()
//                }
//            } ?: run {
//                reportError("Kon nie lêer oopmaak nie")
//                tempFile.delete()
//                return@withContext false
//            }
//        } catch (e: Exception) {
//            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to copy from URI", e)
//            reportError("Kon nie databasis lees nie: ${e.message}")
//            tempFile.delete()
//            return@withContext false
//        }
//
//        processTempFile(tempFile)
//    }
//
//    /**
//     * Validates, migrates, and atomically replaces the live database with
//     * [tempFile]. Expects [tempFile] to already exist in the databases directory.
//     *
//     * Called after both file/URI imports and after a successful WorkManager
//     * download (WiFi/USB and Dropbox paths).
//     *
//     * Was [LaaiDatabasisActivity.processTempDatabase].
//     */
//    suspend fun processTempFile(tempFile: File): Boolean = withContext(Dispatchers.IO) {
//        // 1. Validate
//        if (!ServerFileValidator.checkSingleFile(tempFile).success) {
//            reportError("Aflaailêer is nie 'n geldige databasis nie")
//            tempFile.delete()
//            return@withContext false
//        }
//
//        // 2. Migrate + Room verify
//        if (!DatabaseMigrationHelper.migrateAndVerify(context, tempFile)) {
//            reportError("Databasis is ongeldig – kontak ondersteuning")
//            tempFile.delete()
//            return@withContext false
//        }
//
//        // 3. Close global connections — give SQLite time to release file handles
//        withContext(Dispatchers.Main) {
//            WinkerkDatabase.closeInstance()
//            WinkerkDbHelper.closeInstance(DB_NAME)
//        }
//        delay(200)
//        System.gc()
//
//        // 4. Atomic replace
//        val dbPath = File(context.applicationInfo.dataDir, "databases")
//        val dbFile = File(dbPath, DB_NAME)
//        if (dbFile.exists() && !dbFile.delete()) {
//            reportError("Kon bestaande databasis nie verwyder nie")
//            tempFile.delete()
//            return@withContext false
//        }
//        if (!tempFile.renameTo(dbFile)) {
//            tempFile.copyTo(dbFile, overwrite = true)
//            tempFile.delete()
//        }
//
//        true
//    }
//
//    /**
//     * Copies the content at [downloadUri] (DownloadManager result) to a temp
//     * file and delegates to [processTempFile]. On success calls [reloadAndFinish].
//     *
//     * Was [LaaiDatabasisActivity.processDownloadedFile].
//     */
//    suspend fun processDownloadedFile(downloadUri: Uri) {
//        withContext(Dispatchers.IO) {
//            try {
//                val dbPath = context.applicationInfo.dataDir + "/databases/"
//                val dbDir = File(dbPath).also {
//                    if (!it.exists() && !it.mkdirs()) {
//                        reportError("Kon nie databasisgids skep nie")
//                        return@withContext
//                    }
//                }
//
//                val tempFile = File(dbDir, TEMP_FILE_NAME).also { it.delete() }
//
//                context.contentResolver.openInputStream(downloadUri)?.use { input ->
//                    FileOutputStream(tempFile).use { output ->
//                        input.copyTo(output)
//                        output.fd.sync()
//                    }
//                } ?: run {
//                    reportError("Kon nie lêer oopmaak nie")
//                    return@withContext
//                }
//
//                val success = processTempFile(tempFile)
//                if (success) {
//                    withContext(Dispatchers.Main) {
//                        reloadAndFinish()
//                    }
//                }
//            } catch (e: Exception) {
//                if (BuildConfig.DEBUG) Log.e(TAG, "Download processing failed", e)
//                reportError("Fout met verwerking: ${e.message}")
//            }
//        }
//    }
//
//    /**
//     * Signals the ContentProvider to reload the database, invalidates the
//     * widget cache, then calls [onReloadDone] after a short delay to allow
//     * the provider to settle.
//     *
//     * Was [LaaiDatabasisActivity.reloadDatabaseAndFinish].
//     */
//    fun reloadAndFinish() {
//        try {
//            context.contentResolver.call(
//                winkerkEntry.CONTENT_URI, "reloadDatabase", null, null
//            )
//            WidgetDataRepository.invalidateCache()
//            Handler(Looper.getMainLooper()).postDelayed({
//                onReloadDone()
//            }, 200)
//        } catch (e: IllegalStateException) {
//            if (BuildConfig.DEBUG) Log.e(TAG, "Room schema error during reload", e)
//            val dbFile = File(context.applicationInfo.dataDir, "databases/$DB_NAME")
//            if (dbFile.exists()) dbFile.delete()
//            onError("Databasis fout – kontak ondersteuning")
//            onReloadDone()
//        } catch (e: Exception) {
//            if (BuildConfig.DEBUG) Log.e(TAG, "Error during database reload", e)
//            onReloadDone()
//        }
//    }
//
//    // ── Private helpers ───────────────────────────────────────────────────────
//
//    private suspend fun closeDatabaseConnections() {
//        withContext(Dispatchers.Main) {
//            WinkerkDbHelper.closeAllInstances()
//            WinkerkDatabase.closeInstance()
//        }
//    }
//
//    private suspend fun ensureDatabaseDirectory(): File? {
//        val dbPath = File(context.applicationInfo.dataDir, "databases")
//        if (!dbPath.exists() && !dbPath.mkdirs()) {
//            reportError("Kon nie databasisgids skep nie")
//            return null
//        }
//        return dbPath
//    }
//
//    /** Reports [message] via [onError] on the Main dispatcher. */
//    private suspend fun reportError(message: String) {
//        withContext(Dispatchers.Main) { onError(message) }
//    }
//}