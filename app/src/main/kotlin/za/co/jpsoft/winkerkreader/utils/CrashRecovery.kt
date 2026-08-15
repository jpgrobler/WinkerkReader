package za.co.jpsoft.winkerkreader.utils

import android.content.Context

// utils/CrashRecovery.kt
object CrashRecovery {
    private const val PREF_NAME = "crash_recovery"
    private const val KEY_CRASH_COUNT = "crash_count"
    private const val KEY_LAST_SUCCESS = "last_success"
    private const val MAX_CRASHES = 3

    fun recordStart(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, 0L)
        val now = System.currentTimeMillis()

        // Reset teller as laaste sukses meer as 1 minuut gelede was (app was lank oop)
        if (now - lastSuccess > 60_000) {
            prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply()
        }

        // Verhoog teller
        val count = prefs.getInt(KEY_CRASH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_CRASH_COUNT, count).apply()
    }

    fun recordSuccess(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
            .putInt(KEY_CRASH_COUNT, 0)
            .apply()
    }

    fun shouldForce60Hz(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CRASH_COUNT, 0) >= MAX_CRASHES
    }
}