package za.co.jpsoft.winkerkreader;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CallLog;
import android.util.Log;

import java.util.ArrayList;
import java.util.TimeZone;

public class OproepUtils {
    private Context context;
    private boolean logIncoming;
    private boolean logMissed;
    private boolean logOutgoing;
    private SharedPreferences prefs;

    public OproepUtils(SharedPreferences prefs, Context context) {
        this.prefs = prefs;
        this.context = context;
        this.logMissed = prefs.getBoolean(context.getString(R.string.log_missed_preference_key), true);
        this.logOutgoing = prefs.getBoolean(context.getString(R.string.log_outgoing_preference_key), true);
        this.logIncoming = prefs.getBoolean(context.getString(R.string.log_incoming_preference_key), true);
    }

    public void copyNewCallsToCalendar(String naam) {
        String[] projection = {"number", "type", "duration", "name", "date", "numbertype"};
        Cursor cur = this.context.getContentResolver().query(CallLog.Calls.CONTENT_URI, projection, null, null, "date DESC");
        if (cur != null) {
            int numberColumn = cur.getColumnIndex("number");
            int typeColumn = cur.getColumnIndex("type");
            int durationColumn = cur.getColumnIndex("duration");
            int dateColumn = cur.getColumnIndex("date");
            int cNameColumn = cur.getColumnIndex("name");
            int cTypeColumn = cur.getColumnIndex("numbertype");
            if (cur.moveToFirst()) {
                ArrayList<ContentValues> eventsArray = new ArrayList<>();
                int i = 1;
                do {
                    String number = cur.getString(numberColumn);
                    int type = cur.getInt(typeColumn);
                    int duration = cur.getInt(durationColumn);
                    long date = roundTimeToSecond(cur.getLong(dateColumn));
                    String name = naam;//cur.getString(cNameColumn);
                    int numberType = cur.getInt(cTypeColumn);
                    String calId = this.prefs.getString(this.context.getString(R.string.kalender_pref_key), "1");
                    CallRecord cr = new CallRecord(number, name, type, duration, date, numberType, this.context);
                    if (!isEventInCalendar(calId, cr) && ((this.logMissed && type == 3) || ((this.logOutgoing && type == 2) || (this.logIncoming && type == 1)))) {
                        ContentValues event = createEvent(calId, cr.titel, cr.beskrywing, cr.startTime, cr.endTime);
                        eventsArray.add(event);
                    }
                    i++;
                    if (!cur.moveToNext()) {
                        break;
                    }
                } while (i < 5);
                if (eventsArray.size() != 0) {
                    Uri eventsUri = CalendarContract.Events.CONTENT_URI;
                    ContentValues[] cv = (ContentValues[]) eventsArray.toArray(new ContentValues[0]);
                    try {
                        this.context.getContentResolver().bulkInsert(eventsUri, cv);
                    } catch (Exception e) {
                        Log.e("CallTrack", "Kan nie na kalender skryf nie.");
                    } finally {
                        cur.close();
                    }
                }
            }
        }
    }

    public void copyAllCallsToCalendar() {
        String[] projection = {"number", "type", "duration", "name", "date", "numbertype"};
        Cursor cur = this.context.getContentResolver().query(CallLog.Calls.CONTENT_URI, projection, null, null, "date DESC");
        if (cur != null) {
            int numberColumn = cur.getColumnIndex("number");
            int typeColumn = cur.getColumnIndex("type");
            int durationColumn = cur.getColumnIndex("duration");
            int dateColumn = cur.getColumnIndex("date");
            int cNameColumn = cur.getColumnIndex("name");
            int cTypeColumn = cur.getColumnIndex("numbertype");
            if (cur.moveToFirst()) {
                ArrayList<ContentValues> eventsArray = new ArrayList<>();
                do {
                    String number = cur.getString(numberColumn);
                    int type = cur.getInt(typeColumn);
                    int duration = cur.getInt(durationColumn);
                    long date = roundTimeToSecond(cur.getLong(dateColumn));
                    String name = cur.getString(cNameColumn);
                    int numberType = cur.getInt(cTypeColumn);
                    String calId = this.prefs.getString(this.context.getString(R.string.kalender_pref_key), "1");
                    CallRecord cr = new CallRecord(number, name, type, duration, date, numberType, this.context);
                    if (!isEventInCalendar(calId, cr) && ((this.logMissed && type == 3) || ((this.logOutgoing && type == 2) || (this.logIncoming && type == 1)))) {
                        ContentValues event = createEvent(calId, cr.titel, cr.beskrywing, cr.startTime, cr.endTime);
                        eventsArray.add(event);
                    }
                } while (cur.moveToNext());
                Uri eventsUri = CalendarContract.Events.CONTENT_URI;
                ContentValues[] cv = (ContentValues[]) eventsArray.toArray(new ContentValues[0]);
                try {
                    this.context.getContentResolver().bulkInsert(eventsUri, cv);
                } catch (Exception e) {
                    Log.e("CallTrack", "Kan nie alle oproepe na kalender skryf nie");
                } finally {
                    cur.close();
                }
            }
        }
    }

    public boolean isEventInCalendar(String calId, CallRecord rec) {
        String[] projection = {"calendar_id", "dtstart", "dtend", "title"};
        Uri eventsUri = CalendarContract.Events.CONTENT_URI;
        String[] selectionArgs = {calId, Long.toString(roundTimeToSecond(rec.startTime)), Long.toString(roundTimeToSecond(rec.endTime)), rec.titel};
        Cursor cur = this.context.getContentResolver().query(eventsUri, projection, "calendar_id = ? AND dtstart = ? AND dtend = ? AND title = ?", selectionArgs, null);
        if (cur == null) {
            return false;
        }
        int count = cur.getCount();
        cur.close();
        return count > 0;
    }

    public long roundTimeToSecond(long time) {
        double temp = time / 1000.0d;
        return Math.round(temp) * 1000;
    }

    public ContentValues createEvent(String id, String title, String desc, long start, long end) {
        ContentValues event = new ContentValues();
        event.put("calendar_id", id);
        event.put("title", title);
        event.put("description", desc);
        event.put("dtstart", Long.valueOf(start));
        event.put("dtend", Long.valueOf(end));
        event.put("eventTimezone", TimeZone.getDefault().getID());
        return event;
    }
}

