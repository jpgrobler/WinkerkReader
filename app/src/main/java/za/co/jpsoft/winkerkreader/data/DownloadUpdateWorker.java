package za.co.jpsoft.winkerkreader.data;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Worker to download app updates with progress notification
 */
public class DownloadUpdateWorker extends Worker {
    private static final String TAG = "DownloadUpdateWorker";
    private static final String CHANNEL_ID = "app_update_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String KEY_DOWNLOAD_URL = "download_url";
    public static final String KEY_FILE_PATH = "file_path";
    public static final String KEY_ERROR_MESSAGE = "error_message";

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    public DownloadUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }
    private void showProgressNotification(int progress, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle("Downloading Update")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setProgress(100, progress, progress == 0)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting work: UpdateDownload");
        String downloadUrl = getInputData().getString(KEY_DOWNLOAD_URL);

        if (downloadUrl == null || downloadUrl.isEmpty()) {
            return Result.failure(createErrorData("Download URL is missing"));
        }

        try {
            // Show initial progress notification without foreground service
            showProgressNotification(0, "Starting download...");

            File apkFile = downloadApk(downloadUrl);

            if (apkFile == null || !apkFile.exists()) {
                showErrorNotification("Download failed");
                return Result.failure(createErrorData("Download failed"));
            }

            Data outputData = new Data.Builder()
                    .putString(KEY_FILE_PATH, apkFile.getAbsolutePath())
                    .build();

            showCompletionNotification();
            Log.d(TAG, "Download complete: " + apkFile.getAbsolutePath());

            return Result.success(outputData);

        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            showErrorNotification(e.getMessage());
            return Result.failure(createErrorData(e.getMessage()));
        }
    }

    private File downloadApk(String downloadUrl) throws Exception {
        // Validates WinkerkReader.apk exists before downloading
        ServerFileValidator.ValidationResult validation =
                ServerFileValidator.checkFileAvailability(downloadUrl);

        if (!validation.isAvailable()) {
            throw new Exception("APK not available: " + validation.getMessage());
        }
        HttpURLConnection connection = null;
        InputStream input = null;
        FileOutputStream output = null;

        try {
            // Create updates directory in internal storage
            File updateDir = new File(getApplicationContext().getFilesDir(), "updates");
            if (!updateDir.exists()) {
                updateDir.mkdirs();
            }

            File apkFile = new File(updateDir, "WinkerkReader_update.apk");

            // Delete old file if exists
            if (apkFile.exists()) {
                apkFile.delete();
            }

            // Download file
            URL url = new URL(downloadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("Server returned error code: " + responseCode);
            }

            int fileLength = connection.getContentLength();
            input = connection.getInputStream();
            output = new FileOutputStream(apkFile);

            byte[] buffer = new byte[4096];
            long total = 0;
            int count;

            while ((count = input.read(buffer)) != -1) {
                if (isStopped()) {
                    Log.d(TAG, "Download cancelled");
                    return null;
                }

                total += count;
                output.write(buffer, 0, count);

                // Update progress notification
                if (fileLength > 0) {
                    int progress = (int) ((total * 100) / fileLength);
                    showProgressNotification(progress, progress + "% complete");
                }
            }

            output.flush();

            Log.d(TAG, "Downloaded " + total + " bytes to " + apkFile.getAbsolutePath());
            return apkFile;

        } finally {
            if (output != null) {
                try { output.close(); } catch (Exception ignored) {}
            }
            if (input != null) {
                try { input.close(); } catch (Exception ignored) {}
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notifications for app update downloads");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private ForegroundInfo createForegroundInfo(int progress) {
        notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle("Downloading Update")
                .setContentText(progress > 0 ? progress + "% complete" : "Starting download...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setProgress(100, progress, progress == 0);

        return new ForegroundInfo(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void showCompletionNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle("Update Downloaded")
                .setContentText("Tap to install the update")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void showErrorNotification(String errorMessage) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle("Download Failed")
                .setContentText(errorMessage != null ? errorMessage : "Unknown error occurred")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private Data createErrorData(String errorMessage) {
        return new Data.Builder()
                .putString(KEY_ERROR_MESSAGE, errorMessage != null ? errorMessage : "Unknown error")
                .build();
    }
}