package za.co.jpsoft.winkerkreader.data;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Worker to check for app updates
 */
public class CheckUpdateWorker extends Worker {
    private static final String TAG = "CheckUpdateWorker";
    public static final String KEY_UPDATE_AVAILABLE = "update_available";
    public static final String KEY_CURRENT_VERSION = "current_version";
    public static final String KEY_SERVER_VERSION = "server_version";
    public static final String KEY_ERROR_MESSAGE = "error_message";

    public CheckUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting work: CheckUpdate");
        try {
            String currentVersion = getCurrentAppVersion();
            String serverVersion = fetchServerVersion();

            if (serverVersion == null || serverVersion.isEmpty()) {
                return Result.failure(createErrorData("Failed to fetch server version"));
            }

            boolean updateAvailable = isUpdateAvailable(currentVersion, serverVersion);

            Data outputData = new Data.Builder()
                    .putBoolean(KEY_UPDATE_AVAILABLE, updateAvailable)
                    .putString(KEY_CURRENT_VERSION, currentVersion)
                    .putString(KEY_SERVER_VERSION, serverVersion)
                    .build();

            Log.d(TAG, "Update check complete. Current: " + currentVersion +
                    ", Server: " + serverVersion + ", Available: " + updateAvailable);

            return Result.success(outputData);

        } catch (Exception e) {
            Log.e(TAG, "Error checking for update", e);
            return Result.failure(createErrorData(e.getMessage()));
        }
    }

    private String getCurrentAppVersion() {
        try {
            PackageInfo pInfo = getApplicationContext()
                    .getPackageManager()
                    .getPackageInfo(getApplicationContext().getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get current version", e);
            return "0.0.0";
        }
    }

    private String fetchServerVersion() {
        BufferedReader reader = null;
        HttpURLConnection connection = null;

        try {
            String versionUrl = AppUpdateManager.getVersionUrl();

            // First, check if version file exists on server using ServerFileValidator
            ServerFileValidator.ValidationResult validation =
                    ServerFileValidator.checkFileAvailability(versionUrl);

            if (!validation.isAvailable()) {
                Log.e(TAG, "Version file not available: " + validation.getMessage());
                return null;
            }

            Log.d(TAG, "Version file validated: " + validation);

            // Now fetch the version content
            URL url = new URL(versionUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned error code: " + responseCode);
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String version = reader.readLine();

            if (version != null) {
                version = version.trim();
            }

            return version;

        } catch (Exception e) {
            Log.e(TAG, "Error fetching server version", e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isUpdateAvailable(String currentVersion, String serverVersion) {
        try {
            // Simple version comparison (assumes format like "1.0.0")
            String[] current = currentVersion.split("\\.");
            String[] server = serverVersion.split("\\.");

            int length = Math.max(current.length, server.length);
            for (int i = 0; i < length; i++) {
                int currentPart = i < current.length ? Integer.parseInt(current[i]) : 0;
                int serverPart = i < server.length ? Integer.parseInt(server[i]) : 0;

                if (serverPart > currentPart) {
                    return true;
                } else if (serverPart < currentPart) {
                    return false;
                }
            }

            return false; // Versions are equal

        } catch (Exception e) {
            Log.e(TAG, "Error comparing versions", e);
            // If comparison fails, assume update is available to be safe
            return !currentVersion.equals(serverVersion);
        }
    }

    private Data createErrorData(String errorMessage) {
        return new Data.Builder()
                .putString(KEY_ERROR_MESSAGE, errorMessage != null ? errorMessage : "Unknown error")
                .putBoolean(KEY_UPDATE_AVAILABLE, false)
                .build();
    }
}