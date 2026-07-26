package za.co.jpsoft.winkerkreader.ui.controllers

import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.ui.activities.LaaiDatabasisActivity
import za.co.jpsoft.winkerkreader.workers.FileDownloadWorker
import za.co.jpsoft.winkerkreader.workers.FileDownloadWorkerOld
import java.io.File
import java.util.UUID

/**
 * Manages WorkManager-based database downloads over WiFi and USB.
 *
 * Extracted from LaaiDatabasisActivity. Owns:
 *  - Toggle/cancel logic for WiFi and USB download buttons
 *  - WorkManager request construction and WorkInfo observation
 *  - Delegating successful downloads to [onFileDownloaded] for import
 *
 * ── Wiring ───────────────────────────────────────────────────────────────────
 *
 *   networkController = NetworkTransferController(
 *       lifecycleOwner   = this,
 *       lifecycleScope   = lifecycleScope,
 *       workManager      = WorkManager.getInstance(this),
 *       serverIpInput    = binding.serverIp,
 *       progressLabel    = binding.laaiBoodskap,
 *       wifiButton       = binding.laaiSocket,
 *       usbButton        = binding.laaiUSB,
 *       protocolVersion  = { pcProtocolVersion },
 *       saveIp           = { ip -> settings.edit { putString("IP", ip) } },
 *       onFileDownloaded = { file -> importController.processTempFile(file) },
 *       onNavigateBack   = { navigateBackToMain() }
 *   )
 *
 *   binding.laaiSocket.setOnClickListener { networkController.handleWiFiClick() }
 *   binding.laaiUSB.setOnClickListener    { networkController.handleUSBClick() }
 *
 *   // In cancelOngoingDownloads():
 *   networkController.cancel()
 */
class NetworkTransferController(
    private val lifecycleOwner: LifecycleOwner,
    private val lifecycleScope: CoroutineScope,
    private val workManager: WorkManager,
    private val serverIpInput: EditText,
    private val progressLabel: TextView,
    private val wifiButton: Button,
    private val usbButton: Button,
    private val protocolVersion: () -> String,
    private val saveIp: (String) -> Unit,
    private val onFileDownloaded: suspend (File) -> Boolean,
    private val onNavigateBack: () -> Unit
) {
    private val tag = "NetworkTransferController"

    private var flagCancelledWiFi = false
    private var flagCancelledUSB = false
    private var workId: UUID? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /** Active WorkManager job ID, exposed for [cancelOngoingDownloads] in the Activity. */
    val activeWorkId: UUID? get() = workId

    /**
     * Handles a click on the WiFi download button — starts or cancels
     * the download depending on [flagCancelledWiFi].
     *
     * Was the body of [LaaiDatabasisActivity.handleNetworkTransfer].
     */
    fun handleWiFiClick() {
        if (flagCancelledWiFi) {
            cancelWork()
            wifiButton.background.clearColorFilter()
            progressLabel.setText(R.string.download_cancelled)
            flagCancelledWiFi = false
        } else {
            val ip = serverIpInput.text.toString()
            if (ip.isNotEmpty() && LaaiDatabasisActivity.checkIPv4(ip)) {
                wifiButton.background.clearColorFilter()
                saveIp(ip)
                startDownload(ip, 49514, isWiFi = true)
                flagCancelledWiFi = true
            } else {
                progressLabel.setText(R.string.error_invalid_ip)
            }
        }
    }

    /**
     * Handles a click on the USB download button — starts or cancels
     * the download depending on [flagCancelledUSB].
     *
     * Was the body of [LaaiDatabasisActivity.handleUSBTransfer].
     */
    fun handleUSBClick() {
        if (flagCancelledUSB) {
            cancelWork()
            usbButton.background.clearColorFilter()
            serverIpInput.setText("")
            progressLabel.setText(R.string.download_cancelled)
            flagCancelledUSB = false
        } else {
            usbButton.background.clearColorFilter()
            serverIpInput.setText("127.0.0.1")
            startDownload("127.0.0.1", 49514, isWiFi = false)
            flagCancelledUSB = true
        }
    }

    /** Cancels the active WorkManager job without changing button state. */
    fun cancel() {
        cancelWork()
        flagCancelledWiFi = false
        flagCancelledUSB = false
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Enqueues a [FileDownloadWorker] or [FileDownloadWorkerOld] depending on
     * [protocolVersion], then observes the result.
     *
     * Was [LaaiDatabasisActivity.startFileDownload].
     */
    private fun startDownload(serverIp: String, port: Int, isWiFi: Boolean) {
        progressLabel.setText(R.string.download_starting)

        val inputData = Data.Builder()
            .putString(FileDownloadWorker.KEY_SERVER_IP, serverIp)
            .putInt(FileDownloadWorker.KEY_SERVER_PORT, port)
            .build()

        val workerClass = if (protocolVersion() == "v3")
            FileDownloadWorker::class.java
        else
            FileDownloadWorkerOld::class.java

        val request = OneTimeWorkRequest.Builder(workerClass)
            .setInputData(inputData)
            .addTag("file_download")
            .build()

        workManager.enqueue(request)
        workId = request.id

        workManager.getWorkInfoByIdLiveData(request.id).observe(lifecycleOwner) { workInfo ->
            workInfo ?: return@observe

            val progress = workInfo.progress.getInt(FileDownloadWorker.KEY_PROGRESS, 0)
            if (progress > 0) {
                progressLabel.context?.let {
                    progressLabel.text = it.getString(R.string.download_received_percent, progress)
                }
            }

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val path = workInfo.outputData.getString(FileDownloadWorker.KEY_FILE_PATH)
                    if (path.isNullOrEmpty()) {
                        Toast.makeText(
                            progressLabel.context, "Geen lêerpad ontvang", Toast.LENGTH_LONG
                        ).show()
                        resetFlags(isWiFi)
                        workId = null
                        return@observe
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        Toast.makeText(
                            progressLabel.context, "Aflaaileer nie gevind nie", Toast.LENGTH_LONG
                        ).show()
                        resetFlags(isWiFi)
                        workId = null
                        return@observe
                    }
                    lifecycleScope.launch {
                        try {
                            val ok = onFileDownloaded(file)
                            if (ok) withContext(Dispatchers.Main) {
                                progressLabel.setText(R.string.download_completed)
                                Toast.makeText(
                                    progressLabel.context,
                                    R.string.db_received_success,
                                    Toast.LENGTH_SHORT
                                ).show()
                                Handler(Looper.getMainLooper()).postDelayed(
                                    { onNavigateBack() }, 1500
                                )
                            }
                        } finally {
                            resetFlags(isWiFi)
                            workId = null
                        }
                    }
                }

                WorkInfo.State.FAILED -> {
                    progressLabel.setText(R.string.download_failed)
                    Toast.makeText(
                        progressLabel.context, R.string.db_download_failed, Toast.LENGTH_LONG
                    ).show()
                    resetFlags(isWiFi)
                    workId = null
                }

                else -> { /* RUNNING/ENQUEUED — progress update already handled above */
                }
            }
        }
    }

    private fun cancelWork() {
        workId?.let { workManager.cancelWorkById(it) }
        workId = null
    }

    private fun resetFlags(isWiFi: Boolean) {
        if (isWiFi) flagCancelledWiFi = false else flagCancelledUSB = false
    }
}