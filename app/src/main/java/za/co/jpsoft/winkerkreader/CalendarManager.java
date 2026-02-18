package za.co.jpsoft.winkerkreader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import za.co.jpsoft.winkerkreader.data.CursorDataExtractor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class CalendarManager {

    private static final String TAG = "CalendarManager";
    private Context context;

    public CalendarManager(Context context) {
        this.context = context;
    }

    public List<CalendarInfo> getAvailableCalendars() {
        List<CalendarInfo> calendars = new ArrayList<>();

        try {
            String[] projection = {
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.NAME,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
            };

            String selection = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?";
            String[] selectionArgs = {String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)};

            Cursor cursor = context.getContentResolver().query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
            );

            if (cursor != null) {
                try {
                    if (cursor.getCount() == 0) {
                        Log.w(TAG, "No calendars found on device");
                        return calendars; // Return empty list
                    }
                    while (cursor.moveToNext()) {
                        long id = CursorDataExtractor.getSafeLong(cursor, CalendarContract.Calendars._ID, -1L);
                        String name = CursorDataExtractor.getSafeString(cursor, CalendarContract.Calendars.NAME, "");
                        String displayName = CursorDataExtractor.getSafeString(cursor, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "");
                        String accountName = CursorDataExtractor.getSafeString(cursor, CalendarContract.Calendars.ACCOUNT_NAME, "");

                        calendars.add(new CalendarInfo(id, name, displayName, accountName));
                    }
//                    while (cursor.moveToNext()) {
//                        long id = cursor.getLong(cursor.getColumnIndex(CalendarContract.Calendars._ID));
//                        String name = cursor.getString(cursor.getColumnIndex(CalendarContract.Calendars.NAME));
//                        if (name == null) name = "";
//                        String displayName = cursor.getString(cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME));
//                        if (displayName == null) displayName = "";
//                        String accountName = cursor.getString(cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME));
//                        if (accountName == null) accountName = "";
//
//                        calendars.add(new CalendarInfo(id, name, displayName, accountName));
//                    }

                    Log.d(TAG, "Found " + calendars.size() + " calendars");
                } finally {
                    cursor.close();
                }
            } else {
                Log.w(TAG, "Calendar query returned null cursor - no calendars available or permission denied");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied accessing calendars", e);
        } catch (Exception e) {
            Log.e(TAG, "Error getting calendars", e);
        }

        return calendars;
    }

    public boolean addCallEventToCalendar(long calendarId, String callerInfo, long timestamp,
                                          CallType callType, String source, long duration) {
        try {
            // Check if we have any calendars first
            List<CalendarInfo> availableCalendars = getAvailableCalendars();
            if (availableCalendars.isEmpty()) {
                Log.w(TAG, "No calendars available on device - cannot add event");
                return false;
            }

            // Check if the specified calendar ID exists
            boolean calendarExists = false;
            for (CalendarInfo calendar : availableCalendars) {
                if (calendar.getId() == calendarId) {
                    calendarExists = true;
                    break;
                }
            }

            if (!calendarExists) {
                Log.e(TAG, "Calendar with ID " + calendarId + " not found");
                return false;
            }

            // Check for duplicate calendar events first
            if (isDuplicateCalendarEvent(calendarId, callerInfo, timestamp, callType, source)) {
                Log.d(TAG, "Duplicate calendar event detected, skipping - Contact: " + callerInfo +
                        ", Type: " + callType + ", Source: " + source);
                return true; // Return true as it's not really an error
            }

            ContentValues values = new ContentValues();
            values.put(CalendarContract.Events.DTSTART, timestamp);
            values.put(CalendarContract.Events.DTEND, timestamp + (duration * 1000)); // Convert seconds to milliseconds
            values.put(CalendarContract.Events.TITLE, createEventTitle(callerInfo, callType, source));
            values.put(CalendarContract.Events.DESCRIPTION, createEventDescription(callerInfo, callType, source, duration, timestamp));
            values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
            values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            values.put(CalendarContract.Events.EVENT_COLOR, getEventColor(callType, source));
            values.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE);
            values.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE);

            Uri uri = context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);

            if (uri != null) {
                Log.d(TAG, "Call event added to calendar successfully");
                return true;
            } else {
                Log.e(TAG, "Failed to add call event to calendar");
                return false;
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied adding event to calendar", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error adding event to calendar", e);
            return false;
        }
    }

    private boolean isDuplicateCalendarEvent(long calendarId, String callerInfo, long timestamp,
                                             CallType callType, String source) {
        try {
            long timeWindow = 120000L; // 2 minutes in milliseconds
            long startTime = timestamp - timeWindow;
            long endTime = timestamp + timeWindow;

            String[] projection = {
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART
            };

            String selection = CalendarContract.Events.CALENDAR_ID + " = ? AND " +
                    CalendarContract.Events.DTSTART + " >= ? AND " +
                    CalendarContract.Events.DTSTART + " <= ?";

            String[] selectionArgs = {
                    String.valueOf(calendarId),
                    String.valueOf(startTime),
                    String.valueOf(endTime)
            };

            Cursor cursor = context.getContentResolver().query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
            );

            if (cursor != null) {
                try {

                    while (cursor.moveToNext()) {
                        String existingTitle = CursorDataExtractor.getSafeString(cursor, CalendarContract.Events.TITLE, "");
                        String existingDescription = CursorDataExtractor.getSafeString(cursor, CalendarContract.Events.DESCRIPTION, "");
                        long existingTime = CursorDataExtractor.getSafeLong(cursor, CalendarContract.Events.DTSTART, 0L);

                        String expectedTitle = createEventTitle(callerInfo, callType, source);

                        // Check if title matches and time is very close
                        if (existingTitle.equals(expectedTitle) && Math.abs(existingTime - timestamp) < timeWindow) {
                            Log.d(TAG, "Found duplicate calendar event: " + existingTitle + " at " + existingTime);
                            return true;
                        }

                        // Also check if description contains same caller and source info
                        if (existingDescription.contains(callerInfo) &&
                                existingDescription.contains(source) &&
                                existingDescription.contains(callType.name()) &&
                                Math.abs(existingTime - timestamp) < timeWindow) {
                            Log.d(TAG, "Found similar calendar event based on description");
                            return true;
                        }
                    }
                } finally {
                    cursor.close();
                }
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "Error checking for duplicate calendar events", e);
            return false; // If error, allow event to be created
        }
    }

    private String createEventTitle(String callerInfo, CallType callType, String source) {
        String typeEmoji;
        switch (callType) {
            case INCOMING:
                typeEmoji = "📞";
                break;
            case OUTGOING:
                typeEmoji = "📤";
                break;
            case MISSED:
                typeEmoji = "📵";
                break;
            case ENDED:
                typeEmoji = "📞";
                break;
            default:
                typeEmoji = "📞";
                break;
        }

        String sourceEmoji;
        if ("WhatsApp".equals(source)) {
            sourceEmoji = "💬";
        } else if ("Phone Call".equals(source)) {
            sourceEmoji = "📱";
        } else {
            sourceEmoji = "📞";
        }

        return typeEmoji + " " + sourceEmoji + " " + callType.name() + " Oproep - " + callerInfo;
    }

    private String createEventDescription(String callerInfo, CallType callType, String source,
                                          long duration, long timestamp) {
        StringBuilder sb = new StringBuilder();
        sb.append("Oproep Besonderhede:\n");
        sb.append("Kontak: ").append(callerInfo).append("\n");
        sb.append("Tipe: ").append(callType.name()).append("\n");
        sb.append("Bron: ").append(source).append("\n");

        if (duration > 0) {
            long minutes = duration / 60;
            long seconds = duration % 60;
            sb.append("Duur: ").append(minutes).append("m ").append(seconds).append("s\n");
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sb.append("Tyd: ").append(dateFormat.format(new Date(timestamp))).append("\n");
        sb.append("\nBygevoeg deur WinkerkReader App");

        return sb.toString();
    }

    private int getEventColor(CallType callType, String source) {
        // Return a color based on call type and source
        // Note: Actual color values depend on the calendar provider
        return CalendarContract.Colors.TYPE_EVENT;
    }

    public boolean hasCalendarPermissions() {
        try {
            List<CalendarInfo> calendars = getAvailableCalendars();
            return !calendars.isEmpty();
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permissions denied", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking calendar permissions", e);
            return false;
        }
    }

    public boolean hasCalendarsAvailable() {
        List<CalendarInfo> calendars = getAvailableCalendars();
        return !calendars.isEmpty();
    }

    public String getCalendarStatusMessage() {
        try {
            List<CalendarInfo> calendars = getAvailableCalendars();
            if (calendars.isEmpty()) {
                return "No calendars found. Please add a Google account or other calendar provider to enable calendar logging.";
            }
            return "Found " + calendars.size() + " calendar(s) available for logging.";
        } catch (SecurityException e) {
            return "Calendar permission denied. Please grant calendar access to enable logging.";
        } catch (Exception e) {
            return "Error accessing calendars: " + e.getMessage();
        }
    }
}