// File: AppUpdateManager.kt
package za.co.jpsoft.winkerkreader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.LiveData
import androidx.work.*
import java.io.File

/**
 * Modern app update manager using WorkManager
 * Target API: 34, Min API: 26
 */
class AppUpdateManager(context: Context) {
    private val appContext: Context = context.applicationContext // Prevent memory leaks
    private val workManager: WorkManager = WorkManager.getInstance(context)

    companion object {
        private const val UPDATE_WORK_TAG = "app_update_work"
        private const val UPDATE_URL = "https://www.jpsoft.co.za/wkr/WinkerkReader.apk"
        private const val VERSION_URL = "https://www.jpsoft.co.za/wkr/version10.txt"

        @JvmStatic
        fun getVersionUrl(): String = VERSION_URL

        @JvmStatic
        fun getUpdateUrl(): String = UPDATE_URL
    }

    // Private log tag – accessible within the class
    private val TAG = "AppUpdateManager"

    /**
     * Check for available updates
     * @return LiveData to observe the work status
     */
    fun checkForUpdate(): LiveData<WorkInfo?> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val checkUpdateWork = OneTimeWorkRequest.Builder(CheckUpdateWorker::class.java)
            .setConstraints(constraints)
            .addTag(UPDATE_WORK_TAG)
            .build()

        workManager.enqueue(checkUpdateWork)
        return workManager.getWorkInfoByIdLiveData(checkUpdateWork.id)
    }

    /**
     * Download and install update
     * @return LiveData to observe the download progress
     */
    fun downloadUpdate(): LiveData<WorkInfo?> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only for large downloads
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putString(DownloadUpdateWorker.KEY_DOWNLOAD_URL, UPDATE_URL)
            .build()

        val downloadWork = OneTimeWorkRequest.Builder(DownloadUpdateWorker::class.java)
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(UPDATE_WORK_TAG)
            .build()

        workManager.enqueue(downloadWork)
        return workManager.getWorkInfoByIdLiveData(downloadWork.id)
    }

    /**
     * Install downloaded APK
     * @param apkFile The downloaded APK file
     */
    fun installUpdate(apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                // Use FileProvider for API 24+
                val apkUri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    apkFile
                )
                setDataAndType(apkUri, "application/vnd.android.package-archive")
            }
            appContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install update", e)
        }
    }

    /**
     * Cancel all pending update work
     */
    fun cancelAllUpdates() {
        workManager.cancelAllWorkByTag(UPDATE_WORK_TAG)
    }

    /**
     * Clean up old update files
     */
    fun cleanupOldUpdates() {
        try {
            val updateDir = File(appContext.filesDir, "updates")
            if (updateDir.exists() && updateDir.isDirectory) {
                updateDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".apk")) {
                        val deleted = file.delete()
                        Log.d(TAG, "Deleted old update file: ${file.name} - $deleted")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old updates", e)
        }
    }
}