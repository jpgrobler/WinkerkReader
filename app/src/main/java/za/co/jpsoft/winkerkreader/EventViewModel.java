package za.co.jpsoft.winkerkreader;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import za.co.jpsoft.winkerkreader.data.WinkerkContract;

import org.joda.time.DateTime;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.col;

public class EventViewModel extends ViewModel {

    private final MutableLiveData<Cursor> birthdayData = new MutableLiveData<>();
    private final MutableLiveData<Cursor> baptismData = new MutableLiveData<>();
    private final MutableLiveData<Cursor> weddingData = new MutableLiveData<>();
    private final MutableLiveData<Cursor> confessionData = new MutableLiveData<>();

    public LiveData<Cursor> getBirthdayData(Context context) {
        fetchData(context, "Verjaar");
        return birthdayData;
    }

    public LiveData<Cursor> getBaptismData(Context context) {
        fetchData(context, "Doop");
        return baptismData;
    }

    public LiveData<Cursor> getWeddingData(Context context) {
        fetchData(context, "Huwelik");
        return weddingData;
    }

    public LiveData<Cursor> getConfessionData(Context context) {
        fetchData(context, "Bely");
        return confessionData;
    }

    private void fetchData(Context context, String eventType) {
        String selection = "";
        // Use proper date formatting instead of substring
        String currentMonth = String.format("%02d", DateTime.now().getMonthOfYear());
        String currentDay = String.format("%02d", DateTime.now().getDayOfMonth());

        switch (eventType) {
            case "Verjaar":
                selection = "SELECT Members._rowid_ as _id, * FROM " +
                        WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM +
                        " WHERE (" + col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS) + " = \"0\") AND " +
                        "(substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM) + ",4,2) = \"" + currentMonth + "\") AND " +
                        "(substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM) + ",1,2) = \"" + currentDay + "\") " +
                        "ORDER BY substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM) + ",4,2) ASC, " +
                        "substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM) + ",1,2) ASC, " +
                        col(WinkerkContract.winkerkEntry.LIDMATE_VAN) + " ASC, " + col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM) + " ASC ";
                Log.d("EventViewModel", "Fetching Birthday data: " + selection);
                birthdayData.postValue(queryDatabase(context, selection));
                break;

            case "Doop":
                selection = "SELECT Members._rowid_ as _id, * FROM " +
                        WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM +
                        " WHERE (" + col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS) + " = \"0\") AND " +
                        "(substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM) + ",4,2) = \"" + currentMonth + "\") AND " +
                        "(substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM) + ",1,2) = \"" + currentDay + "\") " +
                        "ORDER BY substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM) + ",4,2) ASC, " +
                        "substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM) + ",1,2) ASC, " +
                        col(WinkerkContract.winkerkEntry.LIDMATE_VAN) + " ASC, " + col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM) + " ASC ";
                Log.d("EventViewModel", "Fetching Baptism data: " + selection);
                baptismData.postValue(queryDatabase(context, selection));
                break;

            case "Huwelik":
                selection = "SELECT Members._rowid_ as _id, * FROM " +
                        WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM +
                        " WHERE (" + col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS) + " = \"0\") AND " +
                        "(substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM) + ",4,2) = \"" + currentMonth + "\") AND " +
                        "(substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM) + ",1,2) = \"" + currentDay + "\") " +
                        "ORDER BY substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM) + ",4,2) ASC, " +
                        "substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM) + ",1,2) ASC, " +
                        col(WinkerkContract.winkerkEntry.LIDMATE_VAN) + " ASC, " + col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM) + " ASC ";
                Log.d("EventViewModel", "Fetching Wedding data: " + selection);
                weddingData.postValue(queryDatabase(context, selection));
                break;

            case "Bely":
                selection = "SELECT Members._rowid_ as _id, * FROM " +
                        WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM +
                        " WHERE (" + col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS) + " = \"0\") AND " +
                        "(substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM) + ",4,2) = \"" + currentMonth + "\") AND " +
                        "(substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM) + ",1,2) = \"" + currentDay + "\") " +
                        "ORDER BY substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM) + ",4,2) ASC, " +
                        "substr(" + col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM) + ",1,2) ASC, " +
                        col(WinkerkContract.winkerkEntry.LIDMATE_VAN) + " ASC, " + col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM) + " ASC ";
                Log.d("EventViewModel", "Fetching Confession data: " + selection);
                confessionData.postValue(queryDatabase(context, selection));
                break;

            default:
                Log.e("EventViewModel", "Invalid event type: " + eventType);
        }
    }

    private Cursor queryDatabase(Context context, String query) {
        try {
            Cursor cursor = context.getContentResolver().query(
                    WinkerkContract.winkerkEntry.CONTENT_URI,
                    null,
                    query,
                    null,
                    null
            );

            if (cursor != null) {
                Log.d("EventViewModel", "Query returned " + cursor.getCount() + " rows");
            } else {
                Log.e("EventViewModel", "Query returned null cursor");
            }

            return cursor;
        } catch (Exception e) {
            Log.e("EventViewModel", "Query failed: " + e.getMessage());
            Log.e("EventViewModel", "Query was: " + query);
            return null;
        }
    }
}