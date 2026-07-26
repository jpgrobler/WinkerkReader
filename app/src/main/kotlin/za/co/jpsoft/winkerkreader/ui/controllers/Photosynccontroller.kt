package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.SharedPreferences
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.workers.PhotoDownloadWorker
import za.co.jpsoft.winkerkreader.workers.PhotoDownloadWorkerOld

/**
 * Manages the WorkManager-based photo synchronisation on LaaiDatabasisActivity.
 *
 * Extracted from LaaiDatabasisActivity.startPhotoSync(). Owns the
 * LiveData observation lifecycle for the photo sync worker.
 *
 * ── Wiring ───────────────────────────────────────────────────────────────────
 *
 *   photoSyncController = PhotoSyncController(
 *       lifecycleOwner   = this,
 *       workManager      = WorkManager.getInstance(this),
 *       settings         = settings,
 *       progressBar      = binding.photoSyncProgress,
 *       statusLabel      = binding.photoSyncStatus,
 *       syncButton       = binding.startPhotoSync,
 *       forceSyncCheck   = { binding.forceSyncCheck.isChecked },
 *       protocolVersion  = { pcProtocolVersion }
 *   )
 *   binding.startPhotoSync.setOnClickListener { photoSyncController.startSync() }
 */
class PhotoSyncController(
    private val lifecycleOwner: LifecycleOwner,
    private val workManager: WorkManager,
    private val settings: SharedPreferences,
    private val progressBar: ProgressBar,
    private val statusLabel: TextView,
    private val syncButton: Button,
    private val forceSyncCheck: () -> Boolean,
    private val protocolVersion: () -> String
) {
    private var currentWorkInfoLiveData: LiveData<WorkInfo?>? = null
    private var workInfoObserver: Observer<WorkInfo?> = Observer { }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Cancels any running photo sync, then enqueues a new WorkManager request.
     *
     * Was [LaaiDatabasisActivity.startPhotoSync].
     */
    fun startSync() {
        workManager.cancelAllWorkByTag("photo_sync")

        val ip = settings.getString("IP", "")
        if (ip.isNullOrEmpty()) {
            Toast.makeText(
                syncButton.context, "Please set server IP first", Toast.LENGTH_SHORT
            ).show()
            return
        }

        progressBar.visibility = android.view.View.VISIBLE
        statusLabel.visibility = android.view.View.VISIBLE
        progressBar.progress = 0
        statusLabel.setText(R.string.photo_sync_starting)
        syncButton.isEnabled = false

        val inputData = Data.Builder()
            .putString("SERVER_IP", ip)
            .putBoolean("FORCE_SYNC", forceSyncCheck())
            .build()

        val workerClass = if (protocolVersion() == "v3")
            PhotoDownloadWorker::class.java
        else
            PhotoDownloadWorkerOld::class.java

        val request = OneTimeWorkRequest.Builder(workerClass)
            .setInputData(inputData)
            .addTag("photo_sync")
            .build()

        workManager.enqueue(request)

        currentWorkInfoLiveData?.removeObserver(workInfoObserver)
        currentWorkInfoLiveData = workManager.getWorkInfoByIdLiveData(request.id)

        workInfoObserver = Observer { workInfo ->
            workInfo ?: return@Observer
            if (workInfo.state.isFinished) {
                progressBar.visibility = android.view.View.GONE
                syncButton.isEnabled = true

                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    val success = workInfo.outputData.getInt("SUCCESS_COUNT", 0)
                    val fail = workInfo.outputData.getInt("FAIL_COUNT", 0)
                    val msg = syncButton.context.getString(R.string.photo_sync_done, success, fail)
                    statusLabel.text = msg
                    statusLabel.visibility = android.view.View.VISIBLE
                    Toast.makeText(syncButton.context, msg, Toast.LENGTH_LONG).show()
                } else {
                    statusLabel.setText(R.string.photo_sync_failed_status)
                    statusLabel.visibility = android.view.View.VISIBLE
                    Toast.makeText(
                        syncButton.context, R.string.photo_sync_failed_toast, Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                val prog = workInfo.progress.getInt("progress", 0)
                val tot = workInfo.progress.getInt("total", 0)
                val guid = workInfo.progress.getString("currentGuid")
                if (tot > 0) {
                    progressBar.max = tot
                    progressBar.progress = prog
                    statusLabel.text = syncButton.context.getString(
                        R.string.photo_sync_progress, prog, tot, guid ?: ""
                    )
                    statusLabel.visibility = android.view.View.VISIBLE
                }
            }
        }
        currentWorkInfoLiveData!!.observe(lifecycleOwner, workInfoObserver)
        Toast.makeText(syncButton.context, "Foto-sinkronisasie begin…", Toast.LENGTH_SHORT).show()
    }

    /** Removes the WorkInfo observer. Call from onDestroy(). */
    fun cleanup() {
        currentWorkInfoLiveData?.removeObserver(workInfoObserver)
    }
}