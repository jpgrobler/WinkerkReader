package za.co.jpsoft.winkerkreader;

import static android.content.Context.MODE_PRIVATE;
import static za.co.jpsoft.winkerkreader.data.CursorDataExtractor.getSafeString;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static za.co.jpsoft.winkerkreader.data.CursorDataExtractor.*;
public class PhoneCallMonitor {

    private static final String TAG = "PhoneCallMonitor";
    private static final String PLACEHOLDER_NUMBER = "XXXXXXXXXX";
    private static final int CALL_END_DELAY_MS = 2000;
    private final Long calendarId;
    private Context context;
    private DatabaseHelper databaseHelper;
    private CalendarManager calendarManager;
    private CalendarIdProvider calendarIdProvider;

    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;

    // Call state tracking
    private String currentIncomingNumber;
    private String currentOutgoingNumber;
    private long callStartTime;
    private boolean isCallActive = false;
    private CallType currentCallType;

    public interface CalendarIdProvider {
        long getSelectedCalendarId();
    }

    public PhoneCallMonitor(Context context, DatabaseHelper databaseHelper,
                                    CalendarManager calendarManager, Long calendarId) {
        this.context = context;
        this.databaseHelper = databaseHelper;
        this.calendarManager = calendarManager;
        this.calendarId = calendarId;
    }

    public void startMonitoring() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions for phone monitoring");
            return;
        }

        try {
            telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            phoneStateListener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String phoneNumber) {
                    super.onCallStateChanged(state, phoneNumber);

                    Log.d(TAG, "Call state changed: " + getCallStateName(state) + ", Number: " + phoneNumber);

                    switch (state) {
                        case TelephonyManager.CALL_STATE_RINGING:
                            handleRingingState(phoneNumber);
                            break;

                        case TelephonyManager.CALL_STATE_OFFHOOK:
                            handleOffHookState(phoneNumber);
                            break;

                        case TelephonyManager.CALL_STATE_IDLE:
                            handleIdleState();
                            break;
                    }
                }
            };

            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

            Log.d(TAG, "Phone call monitoring started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting phone call monitoring", e);
        }
    }

    public void stopMonitoring() {
        try {
            if (telephonyManager != null && phoneStateListener != null) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            }
            phoneStateListener = null;
            telephonyManager = null;
            Log.d(TAG, "Phone call monitoring stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping phone call monitoring", e);
        }
        if (oproepdetail.isOn) {
            scheduleServiceStop(context);
        }
    }

    private void handleRingingState(String phoneNumber) {
        // Incoming call detected
        currentIncomingNumber = phoneNumber != null ? phoneNumber : "Unknown Number";
        currentCallType = CallType.INCOMING;
        callStartTime = System.currentTimeMillis();
        final Context mContext = context;
        final SharedPreferences settings = mContext.getSharedPreferences(PREFS_USER_INFO, 0);
        final SharedPreferences.Editor editor = settings.edit();
        editor.putString("CallerNumber", phoneNumber);
        editor.apply();
        Log.d(TAG, "INCOMING call detected: " + currentIncomingNumber);
        startCallerIdentificationService(context);
        // Log incoming call immediately
        String contactName = getContactName(currentIncomingNumber);
        logPhoneCall(contactName, CallType.INCOMING, callStartTime, 0);
    }

    private void handleOffHookState(String phoneNumber) {
        if (currentIncomingNumber != null) {
            // Incoming call was answered
            Log.d(TAG, "Incoming call ANSWERED: " + currentIncomingNumber);
            isCallActive = true;
        } else {
            // Outgoing call started
            currentOutgoingNumber = phoneNumber != null ? phoneNumber : "Unknown Number";
            currentCallType = CallType.OUTGOING;
            callStartTime = System.currentTimeMillis();
            isCallActive = true;

            Log.d(TAG, "OUTGOING call detected: " + currentOutgoingNumber);

            // Log outgoing call
            String contactName = getContactName(currentOutgoingNumber);
            logPhoneCall(contactName, CallType.OUTGOING, callStartTime, 0);
        }
    }

    private void handleIdleState() {
        if (isCallActive) {
            // Call ended - calculate duration
            long callEndTime = System.currentTimeMillis();
            long duration = (callEndTime - callStartTime) / 1000; // Convert to seconds

            String number = currentIncomingNumber != null ? currentIncomingNumber : currentOutgoingNumber;
            String contactName = getContactName(number);

            Log.d(TAG, "Call ENDED: " + contactName + ", Duration: " + duration + "s, Type: " + currentCallType);

            // Update the call log with duration
            updateCallLogWithDuration(contactName, callStartTime, currentCallType, duration);

        } else if (currentIncomingNumber != null) {
            // Incoming call was missed (never went off-hook)
            Log.d(TAG, "Call MISSED: " + currentIncomingNumber);

            String contactName = getContactName(currentIncomingNumber);
            logPhoneCall(contactName, CallType.MISSED, callStartTime, 0);
        }
        if (oproepdetail.isOn) {
            scheduleServiceStop(context);
        }
        // Reset state
        resetCallState();

        // Also check call log for any missed calls
        checkRecentCallLogs();
    }

    private void resetCallState() {
        currentIncomingNumber = null;
        currentOutgoingNumber = null;
        callStartTime = 0;
        isCallActive = false;
        currentCallType = null;
    }

    private String getContactName(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "Unknown Number";
        }

        // Try to get contact name from call log first (fastest)
        String contactName = getContactNameFromCallLog(phoneNumber);
        if (contactName != null && !contactName.equals(phoneNumber)) {
            return contactName;
        }

        // Could add contacts database lookup here if needed
        return phoneNumber;
    }

    private String getContactNameFromCallLog(String phoneNumber) {
        try {
            String[] projection = {CallLog.Calls.CACHED_NAME};
            String selection = CallLog.Calls.NUMBER + " = ?";
            String[] selectionArgs = {phoneNumber};

            Cursor cursor = context.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    CallLog.Calls.DATE + " DESC"
            );

            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        String cachedName = getSafeString(cursor, CallLog.Calls.CACHED_NAME, "");
                        if (cachedName != null && !cachedName.trim().isEmpty()) {
                            return cachedName;
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting contact name from call log", e);
        }
        return phoneNumber;
    }

//    private void checkRecentCallLogs() {
//        // Check system call log for any calls we might have missed
//        try {
//            String[] projection = {
//                    CallLog.Calls._ID,
//                    CallLog.Calls.NUMBER,
//                    CallLog.Calls.TYPE,
//                    CallLog.Calls.DATE,
//                    CallLog.Calls.DURATION,
//                    CallLog.Calls.CACHED_NAME
//            };
//
//            String selection = CallLog.Calls.DATE + " > ?";
//            String[] selectionArgs = {String.valueOf(System.currentTimeMillis() - 60000)}; // Last minute
//            String sortOrder = CallLog.Calls.DATE + " DESC LIMIT 3";
//            Cursor cursor = null;
//            try{
//                cursor = context.getContentResolver().query(
//                        CallLog.Calls.CONTENT_URI,
//                        projection,
//                        selection,
//                        selectionArgs,
//                        sortOrder
//                );
//            }
//            catch (Exception e) {
//                Log.e(TAG, "Error getting recent call logs", e);
//            }
//            if (cursor != null) {
//                try {
//                    while (cursor.moveToNext()) {
//                        String number = getSafeString(cursor, CallLog.Calls.NUMBER, "");
//                        int type = getSafeInt(cursor, CallLog.Calls.TYPE, -1);
//                        long date = getSafeLong(cursor, CallLog.Calls.DATE, 0L);
//                        long duration = getSafeLong(cursor, CallLog.Calls.DURATION, 0L);
//                        String cachedName = getSafeString(cursor, CallLog.Calls.CACHED_NAME, null);
//
//                        CallType callType;
//                        switch (type) {
//                            case CallLog.Calls.INCOMING_TYPE:
//                                callType = CallType.INCOMING;
//                                break;
//                            case CallLog.Calls.OUTGOING_TYPE:
//                                callType = CallType.OUTGOING;
//                                break;
//                            case CallLog.Calls.MISSED_TYPE:
//                            case CallLog.Calls.REJECTED_TYPE:
//                                callType = CallType.MISSED;
//                                break;
//                            default:
//                                continue;
//                        }
//
//                        String displayName = cachedName != null ? cachedName : (!number.isEmpty() ? number : "Unknown");
//
//                        // Only log if not already logged by our real-time detection
//                        if (!isDuplicateCall(displayName, date, callType)) {
//                            logPhoneCall(displayName, callType, date, duration);
//                        }
//                    }
//                } finally {
//                    cursor.close();
//                }
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error checking recent call logs", e);
//        }
//    }
private void checkRecentCallLogs() {
    // Check system call log for any calls we might have missed
    try {
        String[] projection = {
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_NAME
        };

        String selection = CallLog.Calls.DATE + " > ?";
        String[] selectionArgs = {String.valueOf(System.currentTimeMillis() - 60000)}; // Last minute
        String sortOrder = CallLog.Calls.DATE + " DESC";
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
            );
        } catch (Exception e) {
            Log.e(TAG, "Error getting recent call logs", e);
        }
        if (cursor != null) {
            try {
                int count = 0;
                int maxResults = 3;

                while (cursor.moveToNext() && count < maxResults) {
                    String number = getSafeString(cursor, CallLog.Calls.NUMBER, "");
                    int type = getSafeInt(cursor, CallLog.Calls.TYPE, -1);
                    long date = getSafeLong(cursor, CallLog.Calls.DATE, 0L);
                    long duration = getSafeLong(cursor, CallLog.Calls.DURATION, 0L);
                    String cachedName = getSafeString(cursor, CallLog.Calls.CACHED_NAME, null);

                    CallType callType;
                    switch (type) {
                        case CallLog.Calls.INCOMING_TYPE:
                            callType = CallType.INCOMING;
                            break;
                        case CallLog.Calls.OUTGOING_TYPE:
                            callType = CallType.OUTGOING;
                            break;
                        case CallLog.Calls.MISSED_TYPE:
                        case CallLog.Calls.REJECTED_TYPE:
                            callType = CallType.MISSED;
                            break;
                        default:
                            continue;
                    }

                    String displayName = cachedName != null ? cachedName : (!number.isEmpty() ? number : "Unknown");

                    // Only log if not already logged by our real-time detection
                    if (!isDuplicateCall(displayName, date, callType)) {
                        logPhoneCall(displayName, callType, date, duration);
                    }

                    count++; // ✅ Increment counter
                }
            } finally {
                cursor.close();
            }
        }
    } catch (Exception e) {
        Log.e(TAG, "Error checking recent call logs", e);
    }
}
    private void logPhoneCall(String contactInfo, CallType callType, long timestamp, long duration) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String dateTime = dateFormat.format(new Date(timestamp));

        Log.d(TAG, "Logging phone call - Type: " + callType + ", Contact: " + contactInfo +
                ", Time: " + dateTime + ", Duration: " + duration + "s");

        // Check for duplicates
        if (isDuplicateCall(contactInfo, timestamp, callType)) {
            Log.d(TAG, "Duplicate call detected, skipping");
            return;
        }

        // Save to database
        boolean success = databaseHelper.insertCallLogWithType(contactInfo, timestamp, callType, "Phone Call", duration);

        if (success) {
            Log.d(TAG, "Phone call logged to database successfully");

            // Also log to calendar if configured
            logToCalendar(contactInfo, timestamp, callType, duration);

            broadcastCallLogUpdate();
        } else {
            Log.e(TAG, "Failed to log phone call to database");
        }
    }

    private void updateCallLogWithDuration(String contactInfo, long timestamp, CallType callType, long duration) {
        // For now, just log a new entry with duration
        // Could be enhanced to update existing entry
        logPhoneCall(contactInfo, callType, timestamp, duration);
    }

    private void logToCalendar(String contactInfo, long timestamp, CallType callType, long duration) {
        if (calendarManager != null && calendarId != -1) {
            //long calendarId = calendarIdProvider.getSelectedCalendarId();

                boolean success = calendarManager.addCallEventToCalendar(
                        calendarId, contactInfo, timestamp, callType, "Phone Call", duration);

                if (success) {
                    Log.d(TAG, "Phone call logged to calendar successfully");
                } else {
                    Log.e(TAG, "Failed to log phone call to calendar");
                }

        }
    }

    private boolean isDuplicateCall(String contactInfo, long timestamp, CallType callType) {
        try {
            List<za.co.jpsoft.winkerkreader.CallLog> recentCalls = databaseHelper.getRecentCallLogs(30000); // 30 seconds

            for (za.co.jpsoft.winkerkreader.CallLog call : recentCalls) {
                if (call.getCallerInfo().equals(contactInfo) &&
                        Math.abs(call.getTimestamp() - timestamp) < 30000 && // Within 30 seconds
                        call.getCallType().equals(callType.name()) &&
                        "Phone Call".equals(call.getSource())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking for duplicate calls", e);
            return false;
        }
    }

    private void broadcastCallLogUpdate() {
        Intent intent = new Intent("za.co.jpsoft.winkerkreader.CALL_LOG_UPDATED");
        context.sendBroadcast(intent);
    }

    private boolean hasRequiredPermissions() {
        String[] requiredPermissions = {
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG
        };

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String getCallStateName(int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                return "IDLE";
            case TelephonyManager.CALL_STATE_RINGING:
                return "RINGING";
            case TelephonyManager.CALL_STATE_OFFHOOK:
                return "OFFHOOK";
            default:
                return "UNKNOWN";
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
}