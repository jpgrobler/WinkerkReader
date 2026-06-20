package za.co.jpsoft.winkerkreader.utils

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object PastoralTaskScriptManager {

    private const val TAG = "PastoralTaskScript"
    private const val TIMEOUT_MS = 15_000

    /**
     * Pushes a reminder to Google Tasks via the pastor's own Apps Script.
     * @return The Google Task ID on success, null on failure.
     */
    fun pushTask(
        scriptUrl: String,
        secret: String,
        title: String,
        notes: String?,
        dueDateUtc: Long
    ): String? {
        val dueDate = Instant.ofEpochMilli(dueDateUtc)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)   // YYYY-MM-DD

        val url = buildUrl(scriptUrl) {
            param("secret", secret)
            param("action", "add")
            param("title", title)
            param("due", dueDate)
            if (!notes.isNullOrBlank()) param("notes", notes)
        }

        val response = get(url) ?: return null
        if (!response.startsWith("OK:")) {
            Log.w(TAG, "pushTask unexpected response: $response")
            return null
        }
        val taskId = response.removePrefix("OK:").trim()
        Log.i(TAG, "Task created: $taskId")
        return taskId
    }

    /**
     * Deletes a task previously created by this app.
     * Safe to call if the task no longer exists — the script returns DELETED either way.
     */
    fun deleteTask(scriptUrl: String, secret: String, googleTaskId: String): Boolean {
        val url = buildUrl(scriptUrl) {
            param("secret", secret)
            param("action", "delete")
            param("taskId", googleTaskId)
        }
        val response = get(url) ?: return false
        return response.trim() == "DELETED"
    }

    /**
     * Marks a task as completed in Google Tasks.
     */
    fun completeTask(scriptUrl: String, secret: String, googleTaskId: String): Boolean {
        val url = buildUrl(scriptUrl) {
            param("secret", secret)
            param("action", "complete")
            param("taskId", googleTaskId)
        }
        val response = get(url) ?: return false
        return response.trim() == "COMPLETED"
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private fun get(url: String): String? {
        Log.d(TAG, "GET $url")
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true   // Apps Script responds with a 302 redirect
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "Response: $response")
                response
            } else {
                Log.w(TAG, "HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed", e)
            null
        }
    }

    private fun buildUrl(base: String, block: UrlBuilder.() -> Unit): String =
        UrlBuilder(base).apply(block).build()

    private class UrlBuilder(private val base: String) {
        private val params = mutableListOf<String>()
        fun param(key: String, value: String) {
            params += "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        fun build() = if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }
}