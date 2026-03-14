// File: DownloadUpdateWorker.kt
package za.co.jpsoft.winkerkreader.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Worker to download app updates with progress notification
 */
class DownloadUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val TAG = "DownloadUpdateWorker"
        private const val CHANNEL_ID = "app_update_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    override fun doWork(): Result {
        Log.d(TAG, "Starting work: UpdateDownload")
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL)

        if (downloadUrl.isNullOrEmpty()) {
            return Result.failure(createErrorData("Download URL is missing"))
        }

        return try {
            // Show initial progress notification without foreground service
            showProgressNotification(0, "Starting download...")

            val apkFile = downloadApk(downloadUrl)

            if (apkFile == null || !apkFile.exists()) {
                showErrorNotification("Download failed")
                return Result.failure(createErrorData("Download failed"))
            }

            val outputData = Data.Builder()
                .putString(KEY_FILE_PATH, apkFile.absolutePath)
                .build()

            showCompletionNotification()
            Log.d(TAG, "Download complete: ${apkFile.absolutePath}")

            Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            showErrorNotification(e.message)
            Result.failure(createErrorData(e.message))
        }
    }

    private fun downloadApk(downloadUrl: String): File? {
        // Validate APK exists before downloading
        val validation = ServerFileValidator.checkFileAvailability(downloadUrl)
        if (!validation.available) {
            throw Exception("APK not available: ${validation.message}")
        }

        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        var output: FileOutputStream? = null

        return try {
            // Create updates directory in internal storage
            val updateDir = File(applicationContext.filesDir, "updates")
            if (!updateDir.exists()) {
                updateDir.mkdirs()
            }

            val apkFile = File(updateDir, "WinkerkReader_update.apk")

            // Delete old file if exists
            if (apkFile.exists()) {
                apkFile.delete()
            }

            // Download file
            val url = URL(downloadUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 30000
                connect()
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned error code: $responseCode")
            }

            val fileLength = connection.contentLength
            input = connection.inputStream
            output = FileOutputStream(apkFile)

            val buffer = ByteArray(4096)
            var total: Long = 0
            var count: Int

            while (input.read(buffer).also { count = it } != -1) {
                if (isStopped) {
                    Log.d(TAG, "Download cancelled")
                    return null
                }

                total += count
                output.write(buffer, 0, count)

                // Update progress notification
                if (fileLength > 0) {
                    val progress = ((total * 100) / fileLength).toInt()
                    showProgressNotification(progress, "$progress% complete")
                }
            }

            output.flush()

            Log.d(TAG, "Downloaded $total bytes to ${apkFile.absolutePath}")
            apkFile

        } finally {
            try { output?.close() } catch (_: Exception) { }
            try { input?.close() } catch (_: Exception) { }
            connection?.disconnect()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for app update downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(progress: Int, message: String) {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading Update")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun showCompletionNotification() {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Update Downloaded")
            .setContentText("Tap to install the update")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun showErrorNotification(errorMessage: String?) {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText(errorMessage ?: "Unknown error occurred")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createErrorData(errorMessage: String?): Data {
        return Data.Builder()
            .putString(KEY_ERROR_MESSAGE, errorMessage ?: "Unknown error")
            .build()
    }
}