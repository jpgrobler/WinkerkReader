package za.co.jpsoft.winkerkreader.ui.controllers

import android.app.Activity
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WINKERK_DB
import java.io.File

/**
 * Scans the WKR directory for local database files and builds the
 * RadioButton list UI on LaaiDatabasisActivity.
 *
 * Extracted from LaaiDatabasisActivity. Owns:
 *  - [scan] — file system scan
 *  - [setupUI] — RadioButton construction
 *  - [getSelectedPath] — read the current selection (used by handleLoadDatabase)
 *
 * ── Wiring ───────────────────────────────────────────────────────────────────
 *
 *   fileListController = LocalDatabaseFileController(
 *       activity      = this,
 *       fileListGroup = binding.laaiFilelist,
 *       loadButton    = binding.laaiLaai
 *   )
 *   fileListController.scan(winkerkEntry.getWkrDir(this))
 *   fileListController.setupUI()
 *
 *   // In handleLoadDatabase():
 *   val path = fileListController.getSelectedPath() ?: return
 */
class LocalDatabaseFileController(
    private val activity: Activity,
    private val fileListGroup: RadioGroup,
    private val loadButton: Button
) {
    private val tag = "LocalDatabaseFileController"
    private val fileList = ArrayList<HashMap<String, String>>()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Scans [searchPath] for files named [WINKERK_DB] and populates
     * the internal file list. Call before [setupUI].
     *
     * Was [LaaiDatabasisActivity.scanForDatabaseFiles] + [getFileList] + [addFileToList].
     */
    fun scan(searchPath: String) {
        fileList.clear()
        try {
            File(searchPath).listFiles()?.forEach { file ->
                if (!file.isDirectory && file.name == WINKERK_DB) {
                    fileList.add(hashMapOf("Title" to file.name, "Path" to file.path))
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(tag, "Error scanning files", e)
        }
    }

    /**
     * Builds RadioButton views from the scanned file list and adds them to
     * [fileListGroup]. Hides [loadButton] when no files are found.
     *
     * Was [LaaiDatabasisActivity.setupFileListUI] + [addFileRadioButton].
     */
    fun setupUI() {
        if (fileList.isEmpty()) {
            loadButton.visibility = View.GONE
            return
        }
        loadButton.visibility = View.VISIBLE
        fileList.indices.forEach { i -> addFileRadioButton(i) }
    }

    /**
     * Returns the file path of the currently selected RadioButton,
     * or null if nothing is selected.
     */
    fun getSelectedPath(): String? {
        val id = fileListGroup.checkedRadioButtonId
        return if (id < 0 || id >= fileList.size) null else fileList[id]["Path"]
    }

    /** Resets the RadioGroup selection. */
    fun clearSelection() {
        fileListGroup.clearCheck()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun addFileRadioButton(index: Int) {
        val file = File(fileList[index]["Path"] ?: return)
        val sizeMb = (file.length() / 1024 / 1024).toInt()
        val additionalData = getFileAdditionalData(file.path)

        val radioButton = RadioButton(activity).apply {
            text = "${file.path}\n$sizeMb Mb$additionalData"
            id = index
            background = ContextCompat.getDrawable(activity, R.drawable.border2)
            layoutParams = LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT
            )
        }
        fileListGroup.addView(radioButton)
    }

    /**
     * Opens [filePath] read-only and queries congregation info for display
     * alongside the file in the list. Returns empty string on any failure.
     *
     * Was [LaaiDatabasisActivity.getFileAdditionalData].
     */
    private fun getFileAdditionalData(filePath: String): String {
        return try {
            SQLiteDatabase.openDatabase(
                filePath, null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).use { db ->
                db.rawQuery(
                    "SELECT MyCongregationInfo.Name, Denominations.Abbreviation " +
                            "FROM MyCongregationInfo " +
                            "JOIN Congregations ON (MyCongregationInfo.CongregationGUID = Congregations.CongregationGUID) " +
                            "JOIN Denominations ON (quote(MyCongregationInfo.DenominationGUID) = quote(Denominations.DenominationGUID))",
                    null
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val abbrev = cursor.getString(cursor.getColumnIndex("Abbreviation")) ?: ""
                        val name = cursor.getString(cursor.getColumnIndex("Name")) ?: ""
                        "\nGemeente: $abbrev $name"
                    } else ""
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(tag, "Error reading database info from $filePath", e)
            ""
        }
    }
}