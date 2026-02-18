package za.co.jpsoft.winkerkreader;

import static android.content.Context.MODE_PRIVATE;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.KEY_OPROEPLOG;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.KEY_OPROEPMONITOR;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.concurrent.Executor;

/**
 * BroadcastReceiver for handling incoming call events
 * Optimized for API 34 with backward compatibility to API 26
 */
public class IncomingCall extends BroadcastReceiver {
    private static final String TAG = "IncomingCall";
    private static final int CALL_END_DELAY_MS = 2000;
    private static final String UNKNOWN_NUMBER = "Unknown";
    private static final String PLACEHOLDER_NUMBER = "XXXXXXXXXX";

    private CallStateHandler callStateHandler;
    private static boolean isRunning = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE);
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager == null) {
            Log.e(TAG, "TelephonyManager is null");
            return;
        }

        registerCallStateListener(context, telephonyManager, prefs);
    }

    private void registerCallStateListener(Context context, TelephonyManager telephonyManager, SharedPreferences prefs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ - Use TelephonyCallback
            registerModernCallStateListener(context, telephonyManager, prefs);
        } else {
            // API 26-30 - Use deprecated PhoneStateListener
            registerLegacyCallStateListener(context, telephonyManager, prefs);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void registerModernCallStateListener(Context context, TelephonyManager telephonyManager, SharedPreferences prefs) {
        if (callStateHandler != null) {
            telephonyManager.unregisterTelephonyCallback(callStateHandler);
        }

        callStateHandler = new CallStateHandler(context, prefs);
        Executor executor = context.getMainExecutor();
        telephonyManager.registerTelephonyCallback(executor, callStateHandler);
    }

    @SuppressWarnings("deprecation")
    private void registerLegacyCallStateListener(Context context, TelephonyManager telephonyManager, SharedPreferences prefs) {
        PhoneStateListener phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                super.onCallStateChanged(state, incomingNumber);
                handleCallStateChange(context, prefs, state, incomingNumber);
            }
        };

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void handleCallStateChange(Context context, SharedPreferences prefs, int state, String incomingNumber) {
        String sanitizedNumber = sanitizePhoneNumber(incomingNumber);
        saveCallerNumber(prefs, sanitizedNumber);

        boolean callMonitorEnabled = prefs.getBoolean(KEY_OPROEPMONITOR, true);
        boolean callLogEnabled = prefs.getBoolean(KEY_OPROEPLOG, true);

        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                handleIncomingCall(context, sanitizedNumber, callMonitorEnabled);
                break;

            case TelephonyManager.CALL_STATE_IDLE:
                handleCallEnded(context);
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK:
                // Call answered - could add logging here if needed
                Log.d(TAG, "Call answered");
                break;
        }
    }

    private String sanitizePhoneNumber(String phoneNumber) {
        return (phoneNumber == null || phoneNumber.trim().isEmpty()) ? UNKNOWN_NUMBER : phoneNumber.trim();
    }

    private void saveCallerNumber(SharedPreferences prefs, String phoneNumber) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("CallerNumber", phoneNumber);
        editor.apply();
    }

    private void handleIncomingCall(Context context, String phoneNumber, boolean callMonitorEnabled) {
        Log.d(TAG, "Incoming call from: " + phoneNumber);

        if (callMonitorEnabled) {
            startCallerIdentificationService(context);
        }
    }

    private void handleCallEnded(Context context) {
        if (oproepdetail.isOn) {
            scheduleServiceStop(context);
        }
    }

    private void startCallerIdentificationService(Context context) {
        Intent serviceIntent = new Intent(context, oproepdetail.class);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "Caller identification service started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start caller identification service: " + e.getMessage());
        }
    }

    private void scheduleServiceStop(Context context) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            stopCallerIdentificationService(context);
        }, CALL_END_DELAY_MS);
    }

    private void stopCallerIdentificationService(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("CallerNumber", PLACEHOLDER_NUMBER);
        editor.apply();

        Intent serviceIntent = new Intent(context, oproepdetail.class);
        try {
            context.stopService(serviceIntent);
            Log.d(TAG, "Caller identification service stopped");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop caller identification service: " + e.getMessage());
        }
    }

    /**
     * Modern TelephonyCallback implementation for API 31+
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    class CallStateHandler extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        private final Context context;
        private final SharedPreferences prefs;

        public CallStateHandler(Context context, SharedPreferences prefs) {
            this.context = context;
            this.prefs = prefs;
        }

        @Override
        public void onCallStateChanged(int state) {
            // Note: In API 31+, incoming phone number is not provided in the callback
            // due to privacy restrictions. We need to handle this differently.
            handleCallStateChangeModern(state);
        }

        private void handleCallStateChangeModern(int state) {
            // For API 31+, we can't get the incoming phone number directly
            // We'll use a placeholder and let the service handle caller identification
            String phoneNumber = UNKNOWN_NUMBER;

            boolean callMonitorEnabled = prefs.getBoolean(KEY_OPROEPMONITOR, true);

            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    saveCallerNumber(prefs, phoneNumber);
                    handleIncomingCall(context, phoneNumber, callMonitorEnabled);
                    break;

                case TelephonyManager.CALL_STATE_IDLE:
                    handleCallEnded(context);
                    break;

                case TelephonyManager.CALL_STATE_OFFHOOK:
                    Log.d(TAG, "Call answered (modern API)");
                    break;
            }
        }
    }
}