package za.co.jpsoft.winkerkreader.services.receivers

import za.co.jpsoft.winkerkreader.services.OproepDetailService
import za.co.jpsoft.winkerkreader.services.CallMonitoringService

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import za.co.jpsoft.winkerkreader.data.WinkerkContract.KEY_OPROEPLOG
import za.co.jpsoft.winkerkreader.data.WinkerkContract.KEY_OPROEPMONITOR
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import java.lang.ref.WeakReference

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
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                incomingNumber.safeNumber()?.let { number ->
                    saveCallerNumber(context, number)

                    if (isCallMonitorEnabled(context)) {
                        //startCallMonitoringService(context, number)
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

    private fun isCallLogEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_OPROEPLOG, false)
    }

    private fun saveCallerNumber(context: Context, phoneNumber: String) {
        val prefs = context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
        prefs.edit().putString("CallerNumber", phoneNumber).apply()
    }

    private fun startCallMonitoringService(context: Context, incomingNumber: String) {
        // Optional: Check if service is already running to prevent duplicates
        if (CallMonitoringService.isServiceRunning()) {
            Log.d(TAG, "Call monitoring service already running, not starting another instance")
            // Still send the incoming number to the existing service
            val monitorIntent = Intent(context, CallMonitoringService::class.java)
            monitorIntent.putExtra("incoming_number", incomingNumber)
            context.startService(monitorIntent)
            return
        }

        val monitorIntent = Intent(context, CallMonitoringService::class.java)
        monitorIntent.putExtra("incoming_number", incomingNumber)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(monitorIntent)
            } else {
                context.startService(monitorIntent)
            }
            Log.d(TAG, "Call monitoring service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call monitoring service: ${e.message}")
        }
    }

    // In IncomingCall.kt - update startCallerIdentificationService()
    private fun startCallerIdentificationService(context: Context) {
        // When the dedicated monitor service is active, it owns overlay start/stop.
        if (CallMonitoringService.isServiceRunning()) {
            Log.d(TAG, "CallMonitoringService is active; skip receiver-based overlay start")
            return
        }

        // Check if service is already running to prevent duplicates
        if (OproepDetailService.isOn || OproepDetailService.isServiceActive()) {
            Log.d(TAG, "Caller identification service already running, skipping")
            return
        }

        val serviceIntent = Intent(context, OproepDetailService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
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
        prefs.edit().putString("CallerNumber", PLACEHOLDER_NUMBER).apply()
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
        private const val UNKNOWN_NUMBER = "Unknown"
        private const val PLACEHOLDER_NUMBER = "XXXXXXXXXX"
    }
}