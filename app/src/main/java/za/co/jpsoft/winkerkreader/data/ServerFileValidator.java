package za.co.jpsoft.winkerkreader.data;

import android.util.Log;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Utility class for validating server file availability
 * Uses HTTP HEAD requests to check files without downloading them
 */
public class ServerFileValidator {
    private static final String TAG = "ServerFileValidator";
    private static final int DEFAULT_TIMEOUT = 10000; // 10 seconds

    /**
     * Check if a file exists on the server
     * @param fileUrl URL of the file to check
     * @return ValidationResult with status and details
     */
    public static ValidationResult checkFileAvailability(String fileUrl) {
        return checkFileAvailability(fileUrl, DEFAULT_TIMEOUT);
    }

    /**
     * Check if a file exists on the server with custom timeout
     * @param fileUrl URL of the file to check
     * @param timeoutMs Connection timeout in milliseconds
     * @return ValidationResult with status and details
     */
    public static ValidationResult checkFileAvailability(String fileUrl, int timeoutMs) {
        HttpURLConnection connection = null;

        try {
            // Validate URL format
            if (fileUrl == null || fileUrl.trim().isEmpty()) {
                return new ValidationResult(false, 0, -1, "URL is null or empty");
            }

            // Check if URL uses HTTPS (security best practice)
            if (!fileUrl.toLowerCase().startsWith("https://")) {
                Log.w(TAG, "URL does not use HTTPS: " + fileUrl);
            }

            URL url = new URL(fileUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(true);

            // Set user agent to avoid some servers blocking requests
            connection.setRequestProperty("User-Agent", "WinkerkReader-UpdateChecker/1.0");

            int responseCode = connection.getResponseCode();
            long fileSize = connection.getContentLengthLong();
            String contentType = connection.getContentType();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "File available: " + fileUrl);
                Log.d(TAG, "Response code: " + responseCode);
                Log.d(TAG, "File size: " + (fileSize > 0 ? fileSize + " bytes" : "unknown"));
                Log.d(TAG, "Content type: " + (contentType != null ? contentType : "unknown"));

                return new ValidationResult(true, fileSize, responseCode, "File available");
            } else {
                String message = "File not available. HTTP " + responseCode;
                Log.w(TAG, message + " for " + fileUrl);
                return new ValidationResult(false, 0, responseCode, message);
            }

        } catch (java.net.MalformedURLException e) {
            String message = "Invalid URL format: " + e.getMessage();
            Log.e(TAG, message, e);
            return new ValidationResult(false, 0, -1, message);
        } catch (java.net.SocketTimeoutException e) {
            String message = "Connection timeout after " + timeoutMs + "ms";
            Log.e(TAG, message, e);
            return new ValidationResult(false, 0, -1, message);
        } catch (java.net.UnknownHostException e) {
            String message = "Unknown host: " + e.getMessage();
            Log.e(TAG, message, e);
            return new ValidationResult(false, 0, -1, message);
        } catch (javax.net.ssl.SSLException e) {
            String message = "SSL error: " + e.getMessage();
            Log.e(TAG, message, e);
            return new ValidationResult(false, 0, -1, message);
        } catch (Exception e) {
            String message = "Error checking file: " + e.getMessage();
            Log.e(TAG, message, e);
            return new ValidationResult(false, 0, -1, message);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Check if multiple files exist on the server
     * @param fileUrls Array of URLs to check
     * @return ValidationResult for the first failed file, or success if all pass
     */
    public static ValidationResult checkMultipleFiles(String... fileUrls) {
        if (fileUrls == null || fileUrls.length == 0) {
            return new ValidationResult(false, 0, -1, "No URLs provided");
        }

        for (String fileUrl : fileUrls) {
            ValidationResult result = checkFileAvailability(fileUrl);
            if (!result.isAvailable()) {
                return result;
            }
        }

        return new ValidationResult(true, 0, 200, "All files available");
    }

    /**
     * Result of server file validation
     */
    public static class ValidationResult {
        private final boolean available;
        private final long fileSize;
        private final int httpCode;
        private final String message;

        public ValidationResult(boolean available, long fileSize, int httpCode, String message) {
            this.available = available;
            this.fileSize = fileSize;
            this.httpCode = httpCode;
            this.message = message;
        }

        public boolean isAvailable() {
            return available;
        }

        public long getFileSize() {
            return fileSize;
        }

        public String getFileSizeFormatted() {
            if (fileSize <= 0) {
                return "Unknown";
            }

            double kb = fileSize / 1024.0;
            if (kb < 1024) {
                return String.format("%.2f KB", kb);
            }

            double mb = kb / 1024.0;
            return String.format("%.2f MB", mb);
        }

        public int getHttpCode() {
            return httpCode;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "ValidationResult{" +
                    "available=" + available +
                    ", fileSize=" + getFileSizeFormatted() +
                    ", httpCode=" + httpCode +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
}