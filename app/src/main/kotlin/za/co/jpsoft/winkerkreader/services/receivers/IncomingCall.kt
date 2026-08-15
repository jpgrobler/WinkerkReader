package za.co.jpsoft.winkerkreader.services.receivers

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.KEY_OPROEPMONITOR
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.services.CallMonitoringService
import za.co.jpsoft.winkerkreader.services.OproepDetailService


class IncomingCall : BroadcastReceiver() {
    private fun String?.safeNumber(): String? {
        return this?.takeIf {
            it.isNotBlank() && it != "Unknown" && it != "-1"
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        // Note: EXTRA_INCOMING_NUMBER is deprecated since API 31, but still delivered for compatibility 
        // if READ_CALL_LOG permission is granted.
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                incomingNumber.safeNumber()?.let { number ->
                    saveCallerNumber(context, number)
                    // Always forward the number to CallMonitoringService so PhoneCallMonitor
                    // can use it. On API 31+, TelephonyCallback no longer provides the number
                    // directly, so this is the only reliable delivery path.
                    forwardNumberToCallMonitoringService(context, number)

                    if (isCallMonitorEnabled(context)) {
                        startCallerIdentificationService(context, number)
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (OproepDetailService.isOn) {
                    // Instead of stopping directly, send an intent to the service
                    val intent = Intent(context, OproepDetailService::class.java).apply {
                        action = OproepDetailService.ACTION_CALL_ENDED
                    }
                    context.startService(intent)
                }
            }
        }
    }

    private fun isCallMonitorEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_OPROEPMONITOR, false)
    }

    private fun saveCallerNumber(context: Context, phoneNumber: String) {
        val prefs = context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
        prefs.edit { putString("CallerNumber", phoneNumber) }
    }

    /**
     * Sends the incoming number to [CallMonitoringService] so [PhoneCallMonitor] can use it.
     * If the service is already running, it is re-started with the extra (startForegroundService
     * is idempotent – it calls onStartCommand again without creating a new instance).
     * If the service is not yet running this is a no-op because the number is already stored in
     * SharedPreferences and [startCallerIdentificationService] will start the service fresh.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun forwardNumberToCallMonitoringService(context: Context, number: String) {
        if (CallMonitoringService.isServiceRunning(context)) {
            val intent = Intent(context, CallMonitoringService::class.java).apply {
                putExtra("incoming_number", number)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Forwarded incoming number to CallMonitoringService: $number")
                }
            } catch (e: ForegroundServiceStartNotAllowedException) {
                // Android 12+ (API 31+): Background execution restriction prevents startForegroundService.
                // Catching this prevents an immediate fatal crash. The underlying
                // PhoneStateListener inside CallMonitoringService will still catch the call natively.
                if (BuildConfig.DEBUG) {
                    Log.w(
                        TAG,
                        "Cannot start foreground service from background; falling back to state listener",
                        e
                    )
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Unexpected error forwarding number", e)
                }
            }
        }
    }

    private fun startCallerIdentificationService(context: Context, number: String) {
        val serviceIntent = Intent(context, OproepDetailService::class.java)
            .putExtra(OproepDetailService.EXTRA_CALLER_ID, number)
        try {
            context.startForegroundService(serviceIntent)
            if (BuildConfig.DEBUG) Log.d(TAG, "Caller identification service started for $number")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(
                TAG,
                "Failed to start caller identification service: ${e.message}"
            )
        }
    }

    private fun scheduleServiceStop(context: Context) {
        Handler(Looper.getMainLooper()).postDelayed({
            stopCallerIdentificationService(context)
        }, CALL_END_DELAY_MS)
    }

    private fun stopCallerIdentificationService(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
        prefs.edit { putString("CallerNumber", PLACEHOLDER_NUMBER) }
        val serviceIntent = Intent(context, OproepDetailService::class.java)
        try {
            context.stopService(serviceIntent)
            if (BuildConfig.DEBUG) Log.d(TAG, "Caller identification service stopped")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(
                TAG,
                "Failed to stop caller identification service: ${e.message}"
            )
        }
    }

    companion object {
        private const val TAG = "IncomingCall"
        private const val CALL_END_DELAY_MS = 2000L
        private const val PLACEHOLDER_NUMBER = "XXXXXXXXXX"
    }
}
