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

    override fun onReceive(context: Context, intent: Intent) {
        if (context == null || intent == null) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val stateInt = when (state) {
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            else -> -1
        }
        if (stateInt != -1) {
            processCallState(context, stateInt, incomingNumber)
        }

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (telephonyManager != null) {
            registerCallStateListenerOnce(context, telephonyManager)
        }
    }

    private fun registerCallStateListenerOnce(context: Context, telephonyManager: TelephonyManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (sModernCallback == null) {
                sModernCallback = ModernCallStateHandler(context.applicationContext)
                telephonyManager.registerTelephonyCallback(context.mainExecutor, sModernCallback!!)
                Log.d(TAG, "Modern call state listener registered")
            }
        } else {
            if (sLegacyListener == null) {
                sLegacyListener = LegacyPhoneStateListener(context.applicationContext)
                telephonyManager.listen(sLegacyListener, PhoneStateListener.LISTEN_CALL_STATE)
                Log.d(TAG, "Legacy call state listener registered")
            }
        }
    }

    companion object {
        private const val TAG = "IncomingCall"
        private const val CALL_END_DELAY_MS = 2000L
        private const val UNKNOWN_NUMBER = "Unknown"
        private const val PLACEHOLDER_NUMBER = "XXXXXXXXXX"

        @Volatile
        private var sLegacyListener: LegacyPhoneStateListener? = null
        @Volatile
        private var sModernCallback: ModernCallStateHandler? = null

        private fun processCallState(context: Context, state: Int, incomingNumber: String?) {
            val prefs = context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
            val sanitizedNumber = sanitizePhoneNumber(incomingNumber)

            if (state == TelephonyManager.CALL_STATE_RINGING
                && !TextUtils.isEmpty(incomingNumber)
                && incomingNumber != UNKNOWN_NUMBER) {
                saveCallerNumber(prefs, sanitizedNumber)
            }

            val callMonitorEnabled = prefs.getBoolean(KEY_OPROEPMONITOR, true)
            val callLogEnabled = prefs.getBoolean(KEY_OPROEPLOG, true)

            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> handleIncomingCall(context, sanitizedNumber, callMonitorEnabled)
                TelephonyManager.CALL_STATE_IDLE -> handleCallEnded(context)
                TelephonyManager.CALL_STATE_OFFHOOK -> Log.d(TAG, "Call answered")
            }
        }

        private fun sanitizePhoneNumber(phoneNumber: String?): String {
            return if (phoneNumber.isNullOrBlank()) UNKNOWN_NUMBER else phoneNumber.trim()
        }

        private fun saveCallerNumber(prefs: SharedPreferences, phoneNumber: String) {
            prefs.edit().putString("CallerNumber", phoneNumber).apply()
        }

        private fun handleIncomingCall(context: Context, phoneNumber: String, callMonitorEnabled: Boolean) {
            Log.d(TAG, "Incoming call from: $phoneNumber")
            if (callMonitorEnabled) {
                val monitorIntent = Intent(context, CallMonitoringService::class.java)
                monitorIntent.putExtra("incoming_number", phoneNumber)
                context.startService(monitorIntent)
                startCallerIdentificationService(context)
            }
        }

        private fun handleCallEnded(context: Context) {
            if (OproepDetailService.isOn) {
                scheduleServiceStop(context)
            }
        }

        private fun startCallerIdentificationService(context: Context) {
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
    }

    // Legacy PhoneStateListener for API < 31
    private class LegacyPhoneStateListener(context: Context) : PhoneStateListener() {
        private val contextRef = WeakReference(context)

        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            val ctx = contextRef.get() ?: return
            processCallState(ctx, state, phoneNumber)
        }
    }

    // Modern TelephonyCallback for API 31+
    @RequiresApi(Build.VERSION_CODES.S)
    private class ModernCallStateHandler(context: Context) : TelephonyCallback(), TelephonyCallback.CallStateListener {
        private val contextRef = WeakReference(context)

        override fun onCallStateChanged(state: Int) {
            val ctx = contextRef.get() ?: return
            processCallState(ctx, state, UNKNOWN_NUMBER)
        }
    }
}