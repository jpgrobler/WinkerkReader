// File: CheckUpdateWorker.kt
package za.co.jpsoft.winkerkreader.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Worker to check for app updates
 */
class CheckUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val KEY_UPDATE_AVAILABLE = "update_available"
        const val KEY_CURRENT_VERSION = "current_version"
        const val KEY_SERVER_VERSION = "server_version"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val TAG = "CheckUpdateWorker"
    }

    override fun doWork(): Result {
        Log.d(TAG, "Starting work: CheckUpdate")
        return try {
            val currentVersion = getCurrentAppVersion()
            val serverVersion = fetchServerVersion()

            if (serverVersion.isNullOrEmpty()) {
                return Result.failure(createErrorData("Failed to fetch server version"))
            }

            val updateAvailable = isUpdateAvailable(currentVersion, serverVersion)

            val outputData = Data.Builder()
                .putBoolean(KEY_UPDATE_AVAILABLE, updateAvailable)
                .putString(KEY_CURRENT_VERSION, currentVersion)
                .putString(KEY_SERVER_VERSION, serverVersion)
                .build()

            Log.d(TAG, "Update check complete. Current: $currentVersion, Server: $serverVersion, Available: $updateAvailable")

            Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update", e)
            Result.failure(createErrorData(e.message))
        }
    }

    private fun getCurrentAppVersion(): String {
        return try {
            val pInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get current version", e)
            "0.0.0"
        }
    }

    private fun fetchServerVersion(): String? {
        var reader: BufferedReader? = null
        var connection: HttpURLConnection? = null

        return try {
            val versionUrl = AppUpdateManager.getVersionUrl()

            // First, check if version file exists on server using ServerFileValidator
            val validation = ServerFileValidator.checkFileAvailability(versionUrl)

            if (!validation.available) {
                Log.e(TAG, "Version file not available: ${validation.message}")
                return null
            }

            Log.d(TAG, "Version file validated: $validation")

            // Now fetch the version content
            val url = URL(versionUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned error code: $responseCode")
                return null
            }

            reader = BufferedReader(InputStreamReader(connection.inputStream))
            reader.readLine()?.trim()

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching server version", e)
            null
        } finally {
            try { reader?.close() } catch (_: Exception) { }
            connection?.disconnect()
        }
    }

    private fun isUpdateAvailable(currentVersion: String, serverVersion: String): Boolean {
        return try {
            // Simple version comparison (assumes format like "1.0.0")
            val current = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val server = serverVersion.split(".").map { it.toIntOrNull() ?: 0 }

            val length = maxOf(current.size, server.size)
            for (i in 0 until length) {
                val currentPart = if (i < current.size) current[i] else 0
                val serverPart = if (i < server.size) server[i] else 0

                when {
                    serverPart > currentPart -> return true
                    serverPart < currentPart -> return false
                }
            }
            false // Versions are equal
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions", e)
            // If comparison fails, assume update is available to be safe
            currentVersion != serverVersion
        }
    }

    private fun createErrorData(errorMessage: String?): Data {
        return Data.Builder()
            .putString(KEY_ERROR_MESSAGE, errorMessage ?: "Unknown error")
            .putBoolean(KEY_UPDATE_AVAILABLE, false)
            .build()
    }
}