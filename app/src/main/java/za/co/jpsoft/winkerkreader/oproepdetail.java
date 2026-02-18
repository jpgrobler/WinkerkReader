package za.co.jpsoft.winkerkreader;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import za.co.jpsoft.winkerkreader.data.WinkerkContract;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_LANDLYN;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_SELFOON;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_VAN;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_WERKFOON;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_INFO;

import androidx.core.app.NotificationCompat;

public class oproepdetail extends Service {
    public static boolean isOn = false;
    private WindowManager windowmanager;
    private View floatingview;
    public String Name = "";

    public oproepdetail() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();

        createForegroundNotification();

        SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE);
        String incomingNumber = settings.getString("CallerNumber", "");

        isOn = true;

        if (isValidPhoneNumber(incomingNumber)) {
            processIncomingCall(incomingNumber, settings);
        } else {
            stopSelf();
        }
    }

    private void createForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "WinkerkReader";
            String channelName = "Oproep Service";

            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setLightColor(Color.BLUE);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            channel.setShowBadge(false);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }

            Notification notification = new NotificationCompat.Builder(this, channelId)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.img)
                    .setContentTitle("Caller ID Service")
                    .setContentText("Monitoring incoming calls")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setShowWhen(false)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(2, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(2, notification);
            }
        }
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null &&
                !phoneNumber.isEmpty() &&
                !phoneNumber.equals("XXXXXXXXXX") &&
                !phoneNumber.equals("Unknown");
    }

    private void processIncomingCall(String incomingNumber, SharedPreferences settings) {
        Cursor cursor = null;
        try {
            cursor = searchForCaller(incomingNumber);

            if (cursor != null && cursor.getCount() > 0) {
                String callerInfo = buildCallerInfo(cursor, incomingNumber);
                createFloatingView(callerInfo, settings);
            } else {
                stopSelf();
            }
        } catch (SQLException e) {
            Log.e("WinkerkReader", "Error querying caller info: " + e.getMessage());
            stopSelf();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private Cursor searchForCaller(String phoneNumber) {
        String[] projection = {""};
        int numberLength = phoneNumber.length();
        int searchStart = Math.max(0, numberLength - 9);
        String searchNumber = phoneNumber.substring(searchStart, numberLength);

        Uri queryUri = ContentUris.withAppendedId(WinkerkContract.winkerkEntry.CONTENT_FOON_URI, 0);

        String selection = buildSearchQuery(searchNumber);

        Log.d("WinkerkReader", "Search query: " + selection);

        return getContentResolver().query(queryUri, projection, selection, null, null);
    }

    private String buildSearchQuery(String searchNumber) {
        String baseQuery = SELECTION_LIDMAAT_INFO + " FROM " + SELECTION_LIDMAAT_FROM + " WHERE ";

        String phoneConditions = String.format(
                "(REPLACE(%s,' ','') LIKE '%%%s') OR " +
                        "(REPLACE(%s,' ','') LIKE '%%%s') OR " +
                        "(REPLACE(%s,' ','') LIKE '%%%s')",
                LIDMATE_SELFOON, searchNumber,
                LIDMATE_LANDLYN, searchNumber,
                LIDMATE_WERKFOON, searchNumber
        );

        return " " + baseQuery + phoneConditions + ";";
    }

    private String buildCallerInfo(Cursor cursor, String phoneNumber) {
        StringBuilder output = new StringBuilder();
        output.append(phoneNumber).append("\r\n");

        int nameIndex = cursor.getColumnIndex(LIDMATE_NOEMNAAM);
        int surnameIndex = cursor.getColumnIndex(LIDMATE_VAN);

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            if (!cursor.isNull(nameIndex) && !cursor.isNull(surnameIndex)) {
                String name = cursor.getString(nameIndex);
                String surname = cursor.getString(surnameIndex);
                output.append(name).append("\t ").append(surname).append("\r\n");
            }
        }

        return output.toString();
    }

    private void createFloatingView(String callerInfo, SharedPreferences prefs) {
        floatingview = LayoutInflater.from(this).inflate(R.layout.oproepfloat, null);

        TextView callerTextView = floatingview.findViewById(R.id.oproepnommer);
        callerTextView.setText(callerInfo);

        // Copy caller info to calendar if needed
        OproepUtils utils = new OproepUtils(prefs, this);
        utils.copyNewCallsToCalendar(callerInfo);

        setupFloatingWindow();
        setupClickListeners(callerTextView);
        setupTouchListener();
    }

    private void setupFloatingWindow() {
        WindowManager.LayoutParams params = createWindowLayoutParams();

        params.gravity = Gravity.CENTER | Gravity.START;
        params.x = 0;
        params.y = 100;

        windowmanager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowmanager != null) {
            windowmanager.addView(floatingview, params);
        }
    }

    private WindowManager.LayoutParams createWindowLayoutParams() {
        int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
    }

    private void setupClickListeners(TextView callerTextView) {
        // Close button
        ImageView closeButton = floatingview.findViewById(R.id.close_btn);
        closeButton.setOnClickListener(v -> stopSelf());

        // Copy to clipboard on text click
        callerTextView.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                TextView textView = v.findViewById(R.id.oproepnommer);
                ClipData clip = ClipData.newPlainText("caller_info", textView.getText());
                clipboard.setPrimaryClip(clip);
            }
        });
    }

    private void setupTouchListener() {
        final WindowManager.LayoutParams params = (WindowManager.LayoutParams) floatingview.getLayoutParams();

        floatingview.findViewById(R.id.oproepfloaterbase).setOnTouchListener(
                new FloatingViewTouchListener(params, windowmanager, floatingview)
        );
    }

    // Inner class to handle touch events for the floating view
    private static class FloatingViewTouchListener implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private final WindowManager windowManager;
        private final View floatingView;

        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;

        public FloatingViewTouchListener(WindowManager.LayoutParams params,
                                         WindowManager windowManager,
                                         View floatingView) {
            this.params = params;
            this.windowManager = windowManager;
            this.floatingView = floatingView;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_UP:
                    // Calculate movement distance if needed for future features
                    int xDiff = (int) (event.getRawX() - initialTouchX);
                    int yDiff = (int) (event.getRawY() - initialTouchY);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    params.x = initialX + (int) (event.getRawX() - initialTouchX);
                    params.y = initialY + (int) (event.getRawY() - initialTouchY);

                    if (windowManager != null && floatingView != null) {
                        windowManager.updateViewLayout(floatingView, params);
                    }
                    return true;
            }
            return false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        final SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
        final SharedPreferences.Editor editor = settings.edit();
        editor.putString("CallerNumber", "XXXXXXXXXX");
        editor.apply();
        //MainActivity2.CallerNumber = "XXXXXXXXXX";
        if (floatingview != null)
            windowmanager.removeView(floatingview);
    } // END ON DESTROY


 }