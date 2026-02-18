package za.co.jpsoft.winkerkreader.data;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.io.File;

/**
 * Modern app update manager using WorkManager
 * Target API: 34, Min API: 26
 */
public class AppUpdateManager {
    private static final String TAG = "AppUpdateManager";
    private static final String UPDATE_WORK_TAG = "app_update_work";
    private static final String UPDATE_URL = "https://www.jpsoft.co.za/wkr/WinkerkReader.apk";
    private static final String VERSION_URL = "https://www.jpsoft.co.za/wkr/version10.txt";

    private final Context context;
    private final WorkManager workManager;

    public AppUpdateManager(@NonNull Context context) {
        this.context = context.getApplicationContext(); // Prevent memory leaks
        this.workManager = WorkManager.getInstance(context);
    }

    /**
     * Check for available updates
     * @return LiveData to observe the work status
     */
    public LiveData<WorkInfo> checkForUpdate() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        OneTimeWorkRequest checkUpdateWork = new OneTimeWorkRequest.Builder(CheckUpdateWorker.class)
                .setConstraints(constraints)
                .addTag(UPDATE_WORK_TAG)
                .build();

        workManager.enqueue(checkUpdateWork);
        return workManager.getWorkInfoByIdLiveData(checkUpdateWork.getId());
    }

    /**
     * Download and install update
     * @return LiveData to observe the download progress
     */
    public LiveData<WorkInfo> downloadUpdate() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only for large downloads
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build();

        Data inputData = new Data.Builder()
                .putString(DownloadUpdateWorker.KEY_DOWNLOAD_URL, UPDATE_URL)
                .build();

        OneTimeWorkRequest downloadWork = new OneTimeWorkRequest.Builder(DownloadUpdateWorker.class)
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(UPDATE_WORK_TAG)
                .build();

        workManager.enqueue(downloadWork);
        return workManager.getWorkInfoByIdLiveData(downloadWork.getId());
    }

    /**
     * Install downloaded APK
     * @param apkFile The downloaded APK file
     */
    public void installUpdate(@NonNull File apkFile) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist: " + apkFile.getAbsolutePath());
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Use FileProvider for API 24+
            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    apkFile
            );

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to install update", e);
        }
    }

    /**
     * Cancel all pending update work
     */
    public void cancelAllUpdates() {
        workManager.cancelAllWorkByTag(UPDATE_WORK_TAG);
    }

    /**
     * Clean up old update files
     */
    public void cleanupOldUpdates() {
        try {
            File updateDir = new File(context.getFilesDir(), "updates");
            if (updateDir.exists() && updateDir.isDirectory()) {
                File[] files = updateDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().endsWith(".apk")) {
                            boolean deleted = file.delete();
                            Log.d(TAG, "Deleted old update file: " + file.getName() + " - " + deleted);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to cleanup old updates", e);
        }
    }

    public static String getVersionUrl() {
        return VERSION_URL;
    }

    public static String getUpdateUrl() {
        return UPDATE_URL;
    }
}