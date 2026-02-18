package za.co.jpsoft.winkerkreader;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks for app updates from remote server
 */
public class UpdateCheck {
    private static final String TAG = "UpdateCheck";
    private static final String VERSION_URL = "http://www.jpsoft.co.za/wkr/version2024.txt";
    private static final int CONNECTION_TIMEOUT = 60000;

    private final Context context;
    private final ExecutorService executor;

    public UpdateCheck(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<Boolean> checkForUpdate(int currentVersionCode) {
        MutableLiveData<Boolean> updateAvailable = new MutableLiveData<>();

        executor.execute(() -> {
            try {
                String webVersionString = downloadVersionFromWeb();
                boolean hasUpdate = false;

                if (!webVersionString.isEmpty()) {
                    try {
                        int webVersionCode = Integer.parseInt(webVersionString.trim());
                        hasUpdate = currentVersionCode < webVersionCode;
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Invalid version format from server: " + webVersionString, e);
                    }
                }

                updateAvailable.postValue(hasUpdate);

            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
                updateAvailable.postValue(false);
            }
        });

        return updateAvailable;
    }

    private String downloadVersionFromWeb() {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        StringBuilder result = new StringBuilder();

        try {
            URL url = new URL(VERSION_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(CONNECTION_TIMEOUT);
            connection.setAllowUserInteraction(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
            } else {
                Log.w(TAG, "HTTP response code: " + responseCode);
            }

        } catch (IOException e) {
            Log.e(TAG, "Error downloading version info", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing input stream", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }

        return result.toString();
    }

    public void cleanup() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
