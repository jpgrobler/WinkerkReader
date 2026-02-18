package za.co.jpsoft.winkerkreader;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO;

import android.app.Notification;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WhatsAppNotificationService extends NotificationListenerService {

    private static final String TAG = "VoIPCallLogger";

    // VoIP apps we monitor
    private static final Map<String, String> VOIP_PACKAGES = new HashMap<String, String>() {{
        put("com.whatsapp", "WhatsApp");
        put("com.whatsapp.w4b", "WhatsApp Business");
        put("com.skype.raider", "Skype");
        put("us.zoom.videomeetings", "Zoom");
        put("com.microsoft.teams", "Microsoft Teams");
        put("com.discord", "Discord");
        put("org.telegram.messenger", "Telegram");
        put("com.viber.voip", "Viber");
        put("com.facebook.orca", "Messenger");
        put("com.google.android.apps.tachyon", "Google Meet");
        put("com.zhiliaoapp.musically", "TikTok");
        put("com.snapchat.android", "Snapchat");
        put("jp.naver.line.android", "LINE");
        put("com.kakao.talk", "KakaoTalk");
        put("com.imo.android.imoim", "imo");
        put("com.rebtel.contacts", "Rebtel");
        put("com.enflick.android.TextNow", "TextNow");
    }};

    private DatabaseHelper databaseHelper;
    private SettingsManager settingsManager;

    @Override
    public void onCreate() {
        super.onCreate();
        initializeServices();
        Log.d(TAG, "VoIP Call Notification Service started");
    }

    /**
     * Initialize services with null checks
     * This method ensures services are initialized before use
     */
    private void initializeServices() {
        try {
            if (databaseHelper == null) {
                databaseHelper = new DatabaseHelper(this);
            }
            if (settingsManager == null) {
                settingsManager = SettingsManager.getInstance(this);
            }
            Log.d(TAG, "Services initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing services", e);
        }
    }

    /**
     * Ensure services are initialized
     * Call this before using settingsManager or databaseHelper
     */
    private void ensureServicesInitialized() {
        if (settingsManager == null || databaseHelper == null) {
            Log.w(TAG, "Services not initialized, initializing now");
            initializeServices();
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        try {
            // Ensure services are initialized before checking settings
            ensureServicesInitialized();

            // CHECK IF VOIP LOGGING IS ENABLED
            if (settingsManager == null) {
                Log.e(TAG, "SettingsManager is null, cannot check VOIP logging status");
                return;
            }

            if (!settingsManager.isVoipLogEnabled()) {
                Log.d(TAG, "VOIP logging is disabled, skipping notification");
                return;
            }

            if (sbn != null) {
                String packageName = sbn.getPackageName();

                // Check if it's from any monitored VoIP app
                if (VOIP_PACKAGES.containsKey(packageName)) {
                    String appName = VOIP_PACKAGES.get(packageName);
                    if (appName == null) appName = "Unknown VoIP App";
                    processVoIPNotification(sbn, appName);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onNotificationPosted", e);
        }
    }

    private void processVoIPNotification(StatusBarNotification sbn, String appName) {
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text = extras.getString(Notification.EXTRA_TEXT, "");
        String bigText = extras.getString(Notification.EXTRA_BIG_TEXT, "");
        String subText = extras.getString(Notification.EXTRA_SUB_TEXT, "");
        String infoText = extras.getString(Notification.EXTRA_INFO_TEXT, "");
        String summaryText = extras.getString(Notification.EXTRA_SUMMARY_TEXT, "");

        // Enhanced debugging
        Log.d(TAG, "=== " + appName + " Notification Debug ===");
        Log.d(TAG, "Title: '" + title + "'");
        Log.d(TAG, "Text: '" + text + "'");
        Log.d(TAG, "BigText: '" + bigText + "'");
        Log.d(TAG, "SubText: '" + subText + "'");
        Log.d(TAG, "InfoText: '" + infoText + "'");
        Log.d(TAG, "SummaryText: '" + summaryText + "'");

        String callerInfo = extractCallerInfo(title, text, bigText, subText);

        // PRIORITY ORDER: Check in order of specificity to avoid misclassification
        // 1. Missed calls (most specific)
        if (isMissedCall(title, text, bigText, subText)) {
            logCall(callerInfo, CallType.MISSED, appName);
            return; // Exit early
        }

        // 2. Call ended (very specific)
        if (isCallEndedNotification(title, text, bigText, subText)) {
            logCall(callerInfo, CallType.ENDED, appName);
            return; // Exit early
        }

        // 3. Incoming calls (check before outgoing due to "calling" overlap)
        if (isIncomingCall(title, text, bigText, subText)) {
            logCall(callerInfo, CallType.INCOMING, appName);
            return; // Exit early
        }

        // 4. Outgoing calls (last, as it has broadest keywords)
        if (isPossibleOutgoingCall(title, text, bigText, subText)) {
            logCall(callerInfo, CallType.OUTGOING, appName);
            return; // Exit early
        }
    }

    private boolean isIncomingCall(String title, String text, String bigText, String subText) {
        String combinedText = (title + " " + text + " " + bigText + " " + subText).toLowerCase();

        // Strong incoming indicators - these are very specific
        String[] strongIncoming = {
                "is calling you",
                "wants to call you",
                "incoming call",
                "incoming video call",
                "incoming voice call"
        };

        for (String keyword : strongIncoming) {
            if (combinedText.contains(keyword)) {
                return true;
            }
        }

        // Exclude if it has outgoing indicators
        if (combinedText.contains("you called") ||
                combinedText.contains("you are calling") ||
                combinedText.contains("outgoing") ||
                combinedText.contains("call started")) {
            return false;
        }

        // Check for "calling" but only if context suggests incoming
        if (combinedText.contains("calling")) {
            // "John calling" or "calling..." without "you" suggests incoming
            return !combinedText.contains("you") || combinedText.contains("calling you");
        }

        return false;
    }

    // Improved isPossibleOutgoingCall - more restrictive
    private boolean isPossibleOutgoingCall(String title, String text, String bigText, String subText) {
        String combinedText = (title + " " + text + " " + bigText + " " + subText).toLowerCase();

        // Very strong outgoing indicators only
        String[] strongOutgoing = {
                "you called",
                "you are calling",
                "outgoing call",
                "call started",
                "calling...", // WhatsApp often shows this when YOU initiate
        };

        for (String keyword : strongOutgoing) {
            if (combinedText.contains(keyword)) {
                return true;
            }
        }

        // Exclude if it has clear incoming indicators
        if (combinedText.contains("is calling") ||
                combinedText.contains("calling you") ||
                combinedText.contains("wants to call") ||
                combinedText.contains("incoming")) {
            return false;
        }

        return false;
    }

    private boolean isCallEndedNotification(String title, String text, String bigText, String subText) {
        String[] endedKeywords = {
                "call ended", "call finished", "call completed", "call duration",
                "call lasted", "hung up", "disconnected", "call time"
        };

        String combinedText = (title + " " + text + " " + bigText + " " + subText).toLowerCase();

        for (String keyword : endedKeywords) {
            if (combinedText.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean isMissedCall(String title, String text, String bigText, String subText) {
        String[] missedKeywords = {
                "missed call", "missed video call", "missed voice call",
                "unanswered", "didn't answer", "no answer"
        };

        String combinedText = (title + " " + text + " " + bigText + " " + subText).toLowerCase();

        for (String keyword : missedKeywords) {
            if (combinedText.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String extractCallerInfo(String title, String text, String bigText, String subText) {
        Log.d(TAG, "Extracting caller from - Title: '" + title + "', Text: '" + text +
                "', BigText: '" + bigText + "', SubText: '" + subText + "'");

        // Try all possible extraction methods in order of reliability
        String[] candidates = {
                extractFromTitle(title),
                extractFromText(text),
                extractFromBigText(bigText),
                extractFromSubText(subText),
                extractPhoneNumber(title, text, bigText, subText),
                extractFromTickerText(title, text, bigText, subText)
        };

        for (String candidate : candidates) {
            if (!candidate.trim().isEmpty() && !candidate.equals("Unknown")) {
                String result = candidate.trim();
                Log.d(TAG, "Extracted caller: '" + result + "'");
                return result;
            }
        }

        Log.d(TAG, "Extracted caller: 'Unknown Contact'");
        return "Unknown Contact";
    }

    private String extractFromTitle(String title) {
        if (title.isEmpty()) return "";

        // Clean up title by removing common call-related phrases
        String cleaned = title
                .replaceAll("(?i)[📞📹☎️📱🎥]", "")
                .replaceAll("(?i)(incoming call|calling|video call|voice call|missed call|call from).*", "")
                .replaceAll("(?i).*(whatsapp|skype|zoom|teams|discord|telegram|viber|messenger|meet).*", "")
                .trim();

        // If what remains looks like a name or number, use it
        if (!cleaned.isEmpty() && cleaned.length() > 2 && !containsOnlyCallKeywords(cleaned)) {
            return cleaned;
        }
        return "";
    }

    private String extractFromText(String text) {
        if (text.isEmpty()) return "";

        // Try to extract name before common call phrases
        Pattern[] patterns = {
                Pattern.compile("^(.+?)\\s+(is calling|calling you|wants to call|started a call)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("^(.+?)\\s+(voice call|video call|missed call)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("Call from\\s+(.+?)\\s*$", Pattern.CASE_INSENSITIVE),
                Pattern.compile("^(.+?)\\s+.*call.*$", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : patterns) {
            Matcher match = pattern.matcher(text);
            if (match.find() && match.groupCount() > 0) {
                String name = match.group(1).trim();
                if (!name.isEmpty() && !containsAppKeywords(name)) {
                    return cleanExtractedName(name);
                }
            }
        }

        return "";
    }

    private String extractFromBigText(String bigText) {
        if (bigText.isEmpty()) return "";

        // BigText often contains more detailed info
        String[] lines = bigText.split("\\n|\n");
        for (String line : lines) {
            String cleaned = line.trim();
            if (!cleaned.isEmpty() && !containsAppKeywords(cleaned) && !containsOnlyCallKeywords(cleaned)) {
                // Try to extract first meaningful part
                String[] words = cleaned.split("\\s+");
                if (words.length > 0 && words[0].length() > 2) {
                    return cleanExtractedName(words[0]);
                }
            }
        }

        return "";
    }

    private String extractFromSubText(String subText) {
        if (subText.isEmpty()) return "";

        String cleaned = subText.trim();
        if (!cleaned.isEmpty() && !containsAppKeywords(cleaned) && !containsOnlyCallKeywords(cleaned)) {
            return cleanExtractedName(cleaned);
        }
        return "";
    }

    private String extractPhoneNumber(String title, String text, String bigText, String subText) {
        String combinedText = title + " " + text + " " + bigText + " " + subText;

        // Look for phone number patterns
        Pattern[] phonePatterns = {
                Pattern.compile("\\+?\\d{1,4}[\\s-]?\\(?\\d{1,4}\\)?[\\s-]?\\d{1,4}[\\s-]?\\d{1,9}"), // International format
                Pattern.compile("\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}"), // US format
                Pattern.compile("\\d{10,}") // Simple long number
        };

        for (Pattern pattern : phonePatterns) {
            Matcher match = pattern.matcher(combinedText);
            if (match.find()) {
                String number = match.group().trim();
                if (number.length() >= 7) { // Minimum reasonable phone number length
                    return number;
                }
            }
        }

        return "";
    }

    private String extractFromTickerText(String title, String text, String bigText, String subText) {
        // Sometimes the actual caller name is in unexpected places
        // Try to find any quoted text or text in parentheses
        String combinedText = title + " " + text + " " + bigText + " " + subText;

        Pattern[] patterns = {
                Pattern.compile("\"(.+?)\""), // Quoted text
                Pattern.compile("\\((.+?)\\)"), // Parentheses
                Pattern.compile("from\\s+(.+?)\\s*$", Pattern.CASE_INSENSITIVE), // "from John"
                Pattern.compile("^(.+?)\\s*:", Pattern.CASE_INSENSITIVE) // "John:"
        };

        for (Pattern pattern : patterns) {
            Matcher match = pattern.matcher(combinedText);
            if (match.find() && match.groupCount() > 0) {
                String extracted = match.group(1).trim();
                if (!extracted.isEmpty() && !containsAppKeywords(extracted) && extracted.length() > 2) {
                    return cleanExtractedName(extracted);
                }
            }
        }

        return "";
    }

    private boolean containsOnlyCallKeywords(String text) {
        String[] callOnlyWords = {"call", "calling", "voice", "video", "incoming", "missed", "ended"};
        String[] words = text.toLowerCase().split("\\s+");

        for (String word : words) {
            boolean isCallWord = false;
            for (String callWord : callOnlyWords) {
                if (callWord.equals(word)) {
                    isCallWord = true;
                    break;
                }
            }
            if (!isCallWord && word.length() >= 3) {
                return false;
            }
        }
        return true;
    }

    private String cleanExtractedName(String name) {
        String cleaned = name
                .replaceAll("[📞📹☎️📱🎥]+", "")
                .replaceAll("\\s+", " ")
                .trim();

        return cleaned.length() > 1 ? cleaned : "";
    }

    private boolean containsAppKeywords(String text) {
        String[] appKeywords = {
                "whatsapp", "skype", "zoom", "teams", "discord", "telegram",
                "viber", "messenger", "meet", "notification", "app", "calling",
                "call", "video", "voice", "missed", "incoming", "ended"
        };

        String lowerText = text.toLowerCase();
        for (String keyword : appKeywords) {
            if (lowerText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void logCall(String callerInfo, CallType callType, String appName) {
        try {
            // Ensure database helper is initialized
            ensureServicesInitialized();

            if (databaseHelper == null) {
                Log.e(TAG, "DatabaseHelper is null, cannot log call");
                return;
            }

            long timestamp = System.currentTimeMillis();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String dateTime = dateFormat.format(new Date(timestamp));

            Log.d(TAG, appName + " call detected - Type: " + callType + ", Contact: " + callerInfo + ", Time: " + dateTime);

            // Check for duplicates before logging
            if (isDuplicateCall(callerInfo, timestamp, callType, appName)) {
                Log.d(TAG, "Duplicate call detected, skipping - Contact: " + callerInfo + ", Type: " + callType + ", App: " + appName);
                return;
            }

            // Save to database with app-specific source
            boolean success = databaseHelper.insertCallLogWithType(callerInfo, timestamp, callType, appName);

            if (success) {
                Log.d(TAG, appName + " call log saved to database successfully");

                // Also log to calendar if configured
                logToCalendar(callerInfo, timestamp, callType, appName);

                broadcastCallLogUpdate();
            } else {
                Log.e(TAG, "Failed to save " + appName + " call log");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error logging call", e);
        }
    }

    private boolean isDuplicateCall(String callerInfo, long timestamp, CallType callType, String appName) {
        try {
            if (databaseHelper == null) {
                Log.w(TAG, "DatabaseHelper is null, cannot check for duplicates");
                return false;
            }

            // Reduce time window to 30 seconds (WhatsApp notifications come quickly)
            long timeWindow = 30_000L; // 30 seconds
            List<CallLog> recentCalls = databaseHelper.getRecentCallLogs(timeWindow);

            for (CallLog call : recentCalls) {
                boolean callerMatch = isSimilarCaller(call.getCallerInfo(), callerInfo);
                boolean sourceMatch = call.getSource().equalsIgnoreCase(appName);
                boolean timeMatch = Math.abs(call.getTimestamp() - timestamp) < timeWindow;

                // NEW: If same caller, same app, within 30 seconds - it's duplicate
                // regardless of call type (prevents incoming/outgoing duplicates)
                if (callerMatch && sourceMatch && timeMatch) {
                    Log.d(TAG, "Duplicate detected (same caller, app, time) - " +
                            "Existing: " + call.getCallType() + ", New: " + callType);
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking for duplicates", e);
            return false;
        }
    }

    private boolean isSimilarCaller(String existing, String newCaller) {
        if (existing.equalsIgnoreCase(newCaller)) return true;

        // Handle cases where one might be "Unknown Contact" and other has actual info
        if ((existing.equals("Unknown Contact") && !newCaller.equals("Unknown Contact")) ||
                (newCaller.equals("Unknown Contact") && !existing.equals("Unknown Contact"))) {
            return false;
        }

        // Check if they're very similar (accounting for small variations)
        String existingClean = existing.toLowerCase().replaceAll("[^a-z0-9]", "");
        String newClean = newCaller.toLowerCase().replaceAll("[^a-z0-9]", "");

        // If one contains the other or they're very similar
        return existingClean.contains(newClean) || newClean.contains(existingClean) ||
                levenshteinDistance(existingClean, newClean) <= 2;
    }

    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }

        return dp[len1][len2];
    }

    private void logToCalendar(String callerInfo, long timestamp, CallType callType, String appName) {
        try {
            CalendarManager calendarManager = new CalendarManager(this);
            SharedPreferences prefs = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE);
            long selectedCalendarId = prefs.getLong("selected_calendar_id", -1);

            if (selectedCalendarId != -1) {
                boolean calendarSuccess = calendarManager.addCallEventToCalendar(
                        selectedCalendarId, callerInfo, timestamp, callType, appName, 0);

                if (calendarSuccess) {
                    Log.d(TAG, appName + " call logged to calendar successfully");
                } else {
                    Log.e(TAG, "Failed to log " + appName + " call to calendar");
                }
            } else {
                Log.d(TAG, "No calendar selected, skipping calendar logging");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error logging " + appName + " call to calendar", e);
        }
    }

    private void broadcastCallLogUpdate() {
        try {
            Intent intent = new Intent("za.co.jpsoft.winkerkreader.CALL_LOG_UPDATED");
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting call log update", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        // Handle notification removal if needed
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "Notification listener connected");
        ensureServicesInitialized();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "Notification listener disconnected - requesting rebind");

        try {
            // Request rebind on API 24+ (Android 7.0+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                requestRebind(new android.content.ComponentName(this, WhatsAppNotificationService.class));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting rebind", e);
        }
    }

    @Override
    public void onDestroy() {
        try {
            Log.d(TAG, "Service destroying - cleaning up resources");

            // Close database helper
            if (databaseHelper != null) {
                try {
                    databaseHelper.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing database", e);
                }
                databaseHelper = null;
            }

            // Clear references to prevent memory leaks
            settingsManager = null;

            Log.d(TAG, "VoIP Call Notification Service destroyed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        } finally {
            super.onDestroy();
        }
    }
}