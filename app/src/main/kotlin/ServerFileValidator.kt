// File: ServerFileValidator.kt
package za.co.jpsoft.winkerkreader.data

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility class for validating server file availability
 * Uses HTTP HEAD requests to check files without downloading them
 */
object ServerFileValidator {
    private const val TAG = "ServerFileValidator"
    private const val DEFAULT_TIMEOUT = 10000 // 10 seconds

    /**
     * Check if a file exists on the server
     * @param fileUrl URL of the file to check
     * @return ValidationResult with status and details
     */
    @JvmStatic
    fun checkFileAvailability(fileUrl: String): ValidationResult {
        return checkFileAvailability(fileUrl, DEFAULT_TIMEOUT)
    }

    /**
     * Check if a file exists on the server with custom timeout
     * @param fileUrl URL of the file to check
     * @param timeoutMs Connection timeout in milliseconds
     * @return ValidationResult with status and details
     */
    @JvmStatic
    fun checkFileAvailability(fileUrl: String, timeoutMs: Int): ValidationResult {
        var connection: HttpURLConnection? = null

        return try {
            // Validate URL format
            if (fileUrl.isBlank()) {
                return ValidationResult(false, 0, -1, "URL is null or empty")
            }

            // Check if URL uses HTTPS (security best practice)
            if (!fileUrl.lowercase().startsWith("https://")) {
                Log.w(TAG, "URL does not use HTTPS: $fileUrl")
            }

            val url = URL(fileUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "WinkerkReader-UpdateChecker/1.0")
            }

            val responseCode = connection.responseCode
            val fileSize = connection.contentLengthLong
            val contentType = connection.contentType

            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "File available: $fileUrl")
                Log.d(TAG, "Response code: $responseCode")
                Log.d(TAG, "File size: ${if (fileSize > 0) "$fileSize bytes" else "unknown"}")
                Log.d(TAG, "Content type: ${contentType ?: "unknown"}")

                ValidationResult(true, fileSize, responseCode, "File available")
            } else {
                val message = "File not available. HTTP $responseCode"
                Log.w(TAG, "$message for $fileUrl")
                ValidationResult(false, 0, responseCode, message)
            }

        } catch (e: java.net.MalformedURLException) {
            val message = "Invalid URL format: ${e.message}"
            Log.e(TAG, message, e)
            ValidationResult(false, 0, -1, message)
        } catch (e: java.net.SocketTimeoutException) {
            val message = "Connection timeout after ${timeoutMs}ms"
            Log.e(TAG, message, e)
            ValidationResult(false, 0, -1, message)
        } catch (e: java.net.UnknownHostException) {
            val message = "Unknown host: ${e.message}"
            Log.e(TAG, message, e)
            ValidationResult(false, 0, -1, message)
        } catch (e: javax.net.ssl.SSLException) {
            val message = "SSL error: ${e.message}"
            Log.e(TAG, message, e)
            ValidationResult(false, 0, -1, message)
        } catch (e: Exception) {
            val message = "Error checking file: ${e.message}"
            Log.e(TAG, message, e)
            ValidationResult(false, 0, -1, message)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Check if multiple files exist on the server
     * @param fileUrls Array of URLs to check
     * @return ValidationResult for the first failed file, or success if all pass
     */
    @JvmStatic
    fun checkMultipleFiles(vararg fileUrls: String): ValidationResult {
        if (fileUrls.isEmpty()) {
            return ValidationResult(false, 0, -1, "No URLs provided")
        }

        for (fileUrl in fileUrls) {
            val result = checkFileAvailability(fileUrl)
            if (!result.available) {
                return result
            }
        }

        return ValidationResult(true, 0, 200, "All files available")
    }

    /**
     * Result of server file validation
     */
    data class ValidationResult(
        val available: Boolean,
        val fileSize: Long,
        val httpCode: Int,
        val message: String
    ) {
        val fileSizeFormatted: String
            get() {
                if (fileSize <= 0) return "Unknown"
                val kb = fileSize / 1024.0
                return if (kb < 1024) {
                    String.format("%.2f KB", kb)
                } else {
                    val mb = kb / 1024.0
                    String.format("%.2f MB", mb)
                }
            }

        override fun toString(): String {
            return "ValidationResult(available=$available, fileSize=$fileSizeFormatted, httpCode=$httpCode, message='$message')"
        }
    }
}