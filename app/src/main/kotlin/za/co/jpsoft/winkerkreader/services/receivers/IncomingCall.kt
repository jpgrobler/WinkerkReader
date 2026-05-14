package za.co.jpsoft.winkerkreader.services.receivers

import za.co.jpsoft.winkerkreader.services.OproepDetailService
import za.co.jpsoft.winkerkreader.services.CallMonitoringService

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.edit
import za.co.jpsoft.winkerkreader.data.WinkerkContract.KEY_OPROEPMONITOR
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO

class IncomingCall : BroadcastReceiver() {
    private fun String?.safeNumber(): String? {
        return this?.takeIf {
            it.isNotBlank() && it != "Unknown" && it != "-1"
        }
    }

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

                    if (isCallMonitorEnabled(context)) {
                        startCallerIdentificationService(context)
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (OproepDetailService.isOn) {
                    scheduleServiceStop(context)
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

    private fun startCallerIdentificationService(context: Context) {
        if (CallMonitoringService.isServiceRunning()) {
            Log.d(TAG, "CallMonitoringService is active; skip receiver-based overlay start")
            return
        }

        if (OproepDetailService.isOn || OproepDetailService.isServiceActive()) {
            Log.d(TAG, "Caller identification service already running, skipping")
            return
        }

        val serviceIntent = Intent(context, OproepDetailService::class.java)
        try {
            context.startForegroundService(serviceIntent)
            Log.d(TAG, "Caller identification service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start caller identification service: ${e.message}")
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
            Log.d(TAG, "Caller identification service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop caller identification service: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "IncomingCall"
        private const val CALL_END_DELAY_MS = 2000L
        private const val PLACEHOLDER_NUMBER = "XXXXXXXXXX"
    }
}