package za.co.jpsoft.winkerkreader;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.Observer;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.util.LruCache;

import za.co.jpsoft.winkerkreader.data.FilterBox;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.col;

import java.util.ArrayList;
import java.util.Objects;

public class MemberViewModel extends ViewModel {
    private static final String TAG = "MemberViewModel";

    private final QueryCache queryCache = new QueryCache(10);
    private String lastEventType = "";
    private String lastSearchTerm = "";
    private ArrayList<FilterBox> lastFilterList = null;
    private final Object cursorLock = new Object();
    private final java.util.concurrent.atomic.AtomicBoolean isProcessing =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private final MutableLiveData<CursorWrapper> LIDMAAT_DATA = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> LIDMAAT_DATA_WYK = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> SOEK_DATA = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> LIDMAAT_DATA_VERJAAR = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> GEMEENTENAAM = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> LIDMAAT_DATA_ADRES = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> OUDERDOM_DATA = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> INFO_DATA = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> FILTER_DATA = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> FOTO_UPDATE_DATA = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> GESINNE_DATA = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> TAGGED_DATA = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> HUWELIK_DATA = new MutableLiveData<>();
    private final MutableLiveData<CursorWrapper> ARGIEF_DATA = new MutableLiveData<>();

    private ArrayList<SearchCheckBox> searchList;
    private ArrayList<FilterBox> filterList;
    private final MutableLiveData<String> textLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> VerjaarFlag = new MutableLiveData<>();
    private final MutableLiveData<Integer> rowCount = new MutableLiveData<>();

    public static class CursorWrapper {
        private final Cursor cursor;
        private volatile boolean isClosed = false;
        private final Object lock = new Object();

        public CursorWrapper(Cursor cursor) {
            this.cursor = cursor;
        }

        public Cursor getCursor() {
            synchronized (lock) {
                return isClosed ? null : cursor;
            }
        }

        public void close() {
            synchronized (lock) {
                if (cursor != null && !isClosed) {
                    try {
                        cursor.close();
                    } catch (Exception e) {
                        Log.w(TAG, "Error closing cursor: " + e.getMessage());
                    } finally {
                        isClosed = true;
                    }
                }
            }
        }

        public boolean isClosed() {
            synchronized (lock) {
                return isClosed;
            }
        }
    }

    private static class QueryCache {
        private final LruCache<String, String> cache;

        QueryCache(int maxSize) {
            this.cache = new LruCache<>(maxSize);
        }

        String get(String key) {
            return cache.get(key);
        }

        void put(String key, String value) {
            cache.put(key, value);
        }

        void clear() {
            cache.evictAll();
        }
    }

    public LiveData<Integer> getRowCount() {
        return rowCount;
    }

    public LiveData<Cursor> getLIDMAAT_DATA(MainActivity2 context) {
        return observeCursorWrapper(LIDMAAT_DATA, () -> fetchData(context, "LIDMAAT_DATA"));
    }

    public LiveData<Cursor> getLIDMAAT_DATA_WYK(MainActivity2 context) {
        return observeCursorWrapper(LIDMAAT_DATA_WYK, () -> fetchData(context, "LIDMAAT_DATA_WYK"));
    }

    public LiveData<Cursor> getSOEK_DATA(MainActivity2 context) {
        return observeCursorWrapper(SOEK_DATA, () -> fetchData(context, "SOEK_DATA"));
    }

    public LiveData<Cursor> getLIDMAAT_DATA_VERJAAR(MainActivity2 context) {
        return observeCursorWrapper(LIDMAAT_DATA_VERJAAR, () -> fetchData(context, "LIDMAAT_DATA_VERJAAR"));
    }

    public LiveData<Boolean> getVerjaarFLag() {
        return VerjaarFlag;
    }

    public LiveData<Cursor> getLIDMAAT_DATA_ADRES(MainActivity2 context) {
        return observeCursorWrapper(LIDMAAT_DATA_ADRES, () -> fetchData(context, "LIDMAAT_DATA_ADRES"));
    }

    public LiveData<Cursor> getOUDERDOM_DATA(MainActivity2 context) {
        return observeCursorWrapper(OUDERDOM_DATA, () -> fetchData(context, "OUDERDOM_DATA"));
    }

    public LiveData<Cursor> getINFO_DATA(MainActivity2 context) {
        return observeCursorWrapper(INFO_DATA, () -> fetchData(context, "INFO_DATA"));
    }

    public LiveData<Cursor> getFILTER_DATA(MainActivity2 context, ArrayList<FilterBox> filterLys) {
        filterList = filterLys;
        return observeCursorWrapper(FILTER_DATA, () -> fetchData(context, "FILTER_DATA"));
    }

    public LiveData<String> getTextLiveData() {
        return textLiveData;
    }

    public LiveData<Cursor> getFOTO_UPDATE_DATA(MainActivity2 context) {
        return observeCursorWrapper(FOTO_UPDATE_DATA, () -> fetchData(context, "FOTO_UPDATE_DATA"));
    }

    public LiveData<Cursor> getGESINNE_DATA(MainActivity2 context) {
        return observeCursorWrapper(GESINNE_DATA, () -> fetchData(context, "GESINNE_DATA"));
    }

    public LiveData<Cursor> getTAGGED_DATA(MainActivity2 context) {
        return observeCursorWrapper(TAGGED_DATA, () -> fetchData(context, "TAGGED_DATA"));
    }

    public LiveData<Cursor> getHUWELIK_DATA(MainActivity2 context) {
        return observeCursorWrapper(HUWELIK_DATA, () -> fetchData(context, "HUWELIK_DATA"));
    }

    public LiveData<Cursor> getARGIEF_DATA(MainActivity2 context) {
        return observeCursorWrapper(ARGIEF_DATA, () -> fetchData(context, "ARGIEF_DATA"));
    }

    private LiveData<Cursor> observeCursorWrapper(MutableLiveData<CursorWrapper> wrapperLiveData,
                                                  Runnable fetchAction) {
        MutableLiveData<Cursor> cursorLiveData = new MutableLiveData<>();

        wrapperLiveData.observeForever(new Observer<CursorWrapper>() {
            private CursorWrapper lastWrapper = null;

            @Override
            public void onChanged(CursorWrapper newWrapper) {
                synchronized (cursorLock) {
                    if (lastWrapper != null && !lastWrapper.isClosed()) {
                        final CursorWrapper wrapperToClose = lastWrapper;
                        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                        handler.postDelayed(() -> {
                            try {
                                wrapperToClose.close();
                            } catch (Exception e) {
                                Log.w(TAG, "Error closing delayed cursor: " + e.getMessage());
                            }
                        }, 500);
                    }

                    lastWrapper = newWrapper;

                    if (newWrapper != null && !newWrapper.isClosed()) {
                        Cursor cursor = newWrapper.getCursor();
                        if (cursor != null) {
                            cursorLiveData.postValue(cursor);
                        } else {
                            cursorLiveData.postValue(null);
                        }
                    } else {
                        cursorLiveData.postValue(null);
                    }
                }
            }
        });

        fetchAction.run();
        return cursorLiveData;
    }

    private boolean needsQueryRebuild(String eventType) {
        boolean changed = false;

        if (!eventType.equals(lastEventType)) {
            changed = true;
        }

        if (eventType.equals("SOEK_DATA") && !winkerkEntry.SOEK.equals(lastSearchTerm)) {
            changed = true;
        }

        if (eventType.equals("FILTER_DATA") && !filterListsEqual(filterList, lastFilterList)) {
            changed = true;
        }

        return changed;
    }

    private boolean filterListsEqual(ArrayList<FilterBox> a, ArrayList<FilterBox> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;

        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i).toString(), b.get(i).toString())) {
                return false;
            }
        }
        return true;
    }

    private void fetchData(Context context, String eventType) {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.w(TAG, "Fetch already in progress, skipping: " + eventType);
            return;
        }

        try {
            VerjaarFlag.postValue(false);

            String cacheKey = buildCacheKey(eventType);
            String cachedQuery = queryCache.get(cacheKey);

            String selection;
            String filtertextF = "";

            if (cachedQuery != null && !needsQueryRebuild(eventType)) {
                selection = cachedQuery;
                Log.d(TAG, "Using cached query for: " + eventType);
            } else {
                selection = buildQuery(context, eventType);

                if (selection == null || selection.isEmpty()) {
                    Log.e(TAG, "Failed to build query for: " + eventType);
                    return;
                }

                queryCache.put(cacheKey, selection);

                lastEventType = eventType;
                if (eventType.equals("SOEK_DATA")) {
                    lastSearchTerm = winkerkEntry.SOEK;
                }
                if (eventType.equals("FILTER_DATA")) {
                    lastFilterList = filterList != null ? new ArrayList<>(filterList) : null;
                }

                Log.d(TAG, "Built new query for: " + eventType);
            }

            if (eventType.equals("FILTER_DATA")) {
                filtertextF = buildFilterText();
            }

            SQLiteStatementValidator.ValidationResult result =
                    SQLiteStatementValidator.validateAndFixSQLiteStatement(selection);

            if (result.isValid()) {
                selection = result.getFixedSql();

                if (result.wasFixed()) {
                    Log.i(TAG, "Query was fixed: " + selection);
                }
            } else {
                Log.e(TAG, "Could not fix SQL: " + result.getErrorMessage());
                return;
            }

            Cursor cursor = null;
            try {
                cursor = queryDatabase(context, selection);
            } catch (Exception e) {
                Log.e(TAG, "Error querying database: " + e.getMessage(), e);
                return;
            }

            synchronized (cursorLock) {
                CursorWrapper wrapper = new CursorWrapper(cursor);

                int count = 0;
                if (cursor != null) {
                    try {
                        count = cursor.getCount();
                    } catch (Exception e) {
                        Log.w(TAG, "Error getting cursor count: " + e.getMessage());
                    }
                }
                rowCount.postValue(count);

                postCursorToLiveData(eventType, wrapper, filtertextF);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in fetchData: " + e.getMessage(), e);
        } finally {
            isProcessing.set(false);
        }
    }

    private String buildCacheKey(String eventType) {
        StringBuilder key = new StringBuilder(eventType);

        if (eventType.equals("SOEK_DATA")) {
            key.append("_").append(winkerkEntry.SOEK);
            key.append("_").append(winkerkEntry.SORTORDER);
            if (searchList != null) {
                for (SearchCheckBox item : searchList) {
                    if (item.isChecked()) {
                        key.append("_").append(item.getColumnName());
                    }
                }
            }
        } else if (eventType.equals("FILTER_DATA")) {
            if (filterList != null) {
                for (FilterBox filter : filterList) {
                    if (filter.checked()) {
                        key.append("_").append(filter.getTitle())
                                .append("_").append(filter.getText1())
                                .append("_").append(filter.getText3());
                    }
                }
            }
        }

        return key.toString();
    }

    private String buildQuery(Context context, String eventType) {
        String selection = "";

        switch (eventType) {
            case "GESINNE_DATA":
            case "FILTER_DATA":
            case "LIDMAAT_DATA":
            case "LIDMAAT_DATA_WYK":
            case "SOEK_DATA":
            case "LIDMAAT_DATA_VERJAAR":
            case "OUDERDOM_DATA":
            case "LIDMAAT_DATA_ADRES":
            case "TAGGED_DATA":
            case "HUWELIK_DATA":
                selection = buildMemberQuery(context, eventType);
                break;
            default:
                Log.e(TAG, "Invalid event type: " + eventType);
        }

        return selection;
    }

    private String buildMemberQuery(Context context, String eventType) {
        winkerkEntry.SOEKLIST = false;
        String selection = winkerkEntry.SELECTION_LIDMAAT_INFO;
        String from = " Members ";
        StringBuilder where = new StringBuilder();
        StringBuilder sortOrder = new StringBuilder();

        // Base WHERE clause with proper column wrapping
        where.append(" (").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                .append(col(winkerkEntry.LIDMATE_REKORDSTATUS)).append(" = '")
                .append(winkerkEntry.RECORDSTATUS).append("' )");

        appendWhereClause(context, eventType, where, sortOrder);
        appendOrderByClause(eventType, sortOrder);

        String finalFrom = from;
        String finalSelection = selection;

        if (eventType.equals("GESINNE_DATA") || eventType.equals("LIDMAAT_DATA_WYK") ||
                eventType.equals("LIDMAAT_DATA_ADRES")) {
            finalSelection = selection + winkerkEntry.SELECTION_LIDMAAT_INFO_GESINSHOOF;
            finalFrom = winkerkEntry.SELECTION_LIDMAAT_FROM_GESINSHOOF;
        }

        String w = where.toString();
        if (w.isEmpty()) {
            return finalSelection + " From " + finalFrom + " ORDER BY " + sortOrder + ";";
        } else {
            return finalSelection + " From " + finalFrom + " WHERE " + w + " ORDER BY " + sortOrder + ";";
        }
    }

    private void appendWhereClause(Context context, String eventType,
                                   StringBuilder where, StringBuilder sortOrder) {

        if (eventType.equals("TAGGED_DATA")) {
            where.append(" AND (").append(col(winkerkEntry.LIDMATE_TAG)).append(" = 1 )");
        }

        if (eventType.equals("HUWELIK_DATA")) {
            where.append(" AND ").append(winkerkEntry.SELECTION_HUWELIK_WHERE);
        }

        if (eventType.equals("SOEK_DATA")) {
            SearchCheckBoxPreferences prefsManager = new SearchCheckBoxPreferences(context);
            searchList = prefsManager.getSearchCheckBoxList();

            if (searchList != null) {
                int search_fields = 0;
                for (int i = 0; i < searchList.size(); i++) {
                    if (searchList.get(i).isChecked()) {
                        search_fields++;
                        if (search_fields == 1) {
                            where.append(" AND (");
                        } else {
                            where.append(" OR ");
                        }
                        where.append(col(searchList.get(i).getColumnName()))
                                .append(" LIKE '%").append(winkerkEntry.SOEK).append("%'");
                    }
                }
                if (search_fields > 0) {
                    where.append(" )");
                }
            }
            winkerkEntry.SOEKLIST = true;
        }

        if (eventType.equals("FILTER_DATA")) {
            if (filterList != null) {
                int filter_fields = 0;
                for (int i = 0; i < filterList.size(); i++) {
                    if (filterList.get(i).checked()) {
                        filter_fields++;
                        if (filter_fields == 1) {
                            where.append(" AND (");
                        } else {
                            where.append(") AND (");
                        }
                        String toets = filterList.get(i).getText3();

                        if (toets.equals("gelyk aan")) {
                            where.append(col(filterList.get(i).getTitle()))
                                    .append(" = '")
                                    .append(filterList.get(i).getText1())
                                    .append("'");
                        }
                        if (toets.equals("is nie") || (toets.equals("nie gelyk aan"))) {
                            where.append(col(filterList.get(i).getTitle()))
                                    .append(" != '")
                                    .append(filterList.get(i).getText1())
                                    .append("'");
                        }
                        if (toets.equals("begin met")) {
                            where.append(col(filterList.get(i).getTitle()))
                                    .append(" Like '")
                                    .append(filterList.get(i).getText1())
                                    .append("%'");
                        }
                        if (toets.equals("eindig met")) {
                            where.append(col(filterList.get(i).getTitle()))
                                    .append(" Like '%")
                                    .append(filterList.get(i).getText1())
                                    .append("'");
                        }
                        if (toets.equals("leeg")) {
                            where.append(col(filterList.get(i).getTitle())).append(" IS NULL ");
                        }
                        if (toets.equals("kleiner as")) {
                            where.append("((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', ")
                                    .append("birthdate)))").append(" < ").append(filterList.get(i).getText1());
                        }
                        if (toets.equals("groter as")) {
                            where.append("((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', ")
                                    .append("birthdate)))").append(" > ").append(filterList.get(i).getText1());
                        }
                        if (toets.equals("tussen") && filterList.get(i).getTitle().equals("Ouderdom")) {
                            where.append(" ( ((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', ")
                                    .append("birthdate)))").append(" >= ")
                                    .append(filterList.get(i).getText1()).append(" ) AND ( ")
                                    .append("((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', ")
                                    .append("birthdate)))").append(" <= ")
                                    .append(filterList.get(i).getText2()).append(" )");
                        }
                        if (toets.equals("gelyk") && filterList.get(i).getTitle().equals("Ouderdom")) {
                            where.append("((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', ")
                                    .append("birthdate)))").append(" = ")
                                    .append(filterList.get(i).getText1());
                        }
                        if (filterList.get(i).getTitle().equals("Geslag")) {
                            if (toets.equals("manlik")) {
                                where.append(col(winkerkEntry.LIDMATE_GESLAG)).append(" = 'Manlik'");
                            } else {
                                where.append(col(winkerkEntry.LIDMATE_GESLAG)).append(" = 'Vroulik'");
                            }
                        }
                        if (filterList.get(i).getTitle().equals("Selfoon")) {
                            where.append(" ( ")
                                    .append(col(winkerkEntry.LIDMATE_SELFOON))
                                    .append(" IS NOT NULL AND ")
                                    .append(col(winkerkEntry.LIDMATE_SELFOON))
                                    .append(" != '' )");
                        }
                        if (filterList.get(i).getTitle().equals("E-pos")) {
                            where.append(" ( ").append(col(winkerkEntry.LIDMATE_EPOS))
                                    .append(" IS NOT NULL AND ").append(col(winkerkEntry.LIDMATE_EPOS)).append(" != '' )");
                        }
                        if (filterList.get(i).getTitle().equals("Landlyn")) {
                            where.append(" ( ").append(col(winkerkEntry.LIDMATE_LANDLYN))
                                    .append(" IS NOT NULL AND ").append(col(winkerkEntry.LIDMATE_LANDLYN)).append(" != '' )");
                        }
                        if (filterList.get(i).getTitle().equals("Huwelikstatus")) {
                            where.append(col(winkerkEntry.LIDMATE_HUWELIKSTATUS)).append(" = '")
                                    .append(filterList.get(i).getText3()).append("'");
                        }
                        if (filterList.get(i).getTitle().equals("Lidmaatskap")) {
                            if (filterList.get(i).getText3().equals("Belydend")) {
                                where.append(col(winkerkEntry.LIDMATE_LIDMAATSTATUS)).append(" LIKE 'Bely%'");
                            } else {
                                where.append(col(winkerkEntry.LIDMATE_LIDMAATSTATUS)).append(" LIKE '")
                                        .append(filterList.get(i).getText3()).append("'");
                            }
                        }
                        if (filterList.get(i).getTitle().equals("Gesinshoof")) {
                            where.append(" quote(").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID))
                                    .append(") = quote(").append(col(winkerkEntry.LIDMATE_LIDMAATGUID)).append(")");
                        }
                    }
                }
                if (filter_fields > 0) {
                    where.append(" )");
                }
            }
            winkerkEntry.SOEKLIST = true;
        }
    }

    private void appendOrderByClause(String eventType, StringBuilder sortOrder) {

        if (eventType.equals("LIDMAAT_DATA")) {
            winkerkEntry.SORTORDER = "VAN";
            sortOrder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ");
        }

        if (eventType.equals("GESINNE_DATA")) {
            winkerkEntry.SORTORDER = "GESINNE";
            sortOrder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ");
        }

        if (eventType.equals("LIDMAAT_DATA_WYK")) {
            winkerkEntry.SORTORDER = "WYK";
            sortOrder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_WYK)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ");
        }

        if (eventType.equals("LIDMAAT_DATA_ADRES")) {
            winkerkEntry.SORTORDER = "ADRES";
            sortOrder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_STRAATADRES)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ");
        }

        if (eventType.equals("TAGGED_DATA")) {
            sortOrder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ");
        }

        if (eventType.equals("HUWELIK_DATA")) {
            winkerkEntry.SORTORDER = "HUWELIK";
            sortOrder.append(" strftime('%m', ").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_HUWELIKSDATUM)).append(") ASC,  strftime('%d', ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_HUWELIKSDATUM)).append(") ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESLAG)).append(" DESC");
        }

        if (eventType.equals("SOEK_DATA")) {
            switch (winkerkEntry.SORTORDER) {
                case "GESINNE":
                    sortOrder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                            .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                            .append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,")
                            .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ");
                    break;
                case "VAN":
                    sortOrder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                            .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ");
                    break;
                case "ADRES":
                    sortOrder.append(col(winkerkEntry.LIDMATE_STRAATADRES)).append(" ASC, ")
                            .append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                            .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                            .append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,")
                            .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ");
                    break;
                case "WYK":
                    sortOrder.append(col(winkerkEntry.LIDMATE_WYK)).append(" ASC, ")
                            .append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                            .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                            .append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,")
                            .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ");
                    break;
                case "VERJAAR":
                    sortOrder.append(" strftime('%m', birthdate) ASC, strftime('%d', birthdate) ASC");
                    break;
                case "OUDERDOM":
                    sortOrder.append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC");
                    break;
                default:
                    sortOrder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                            .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ");
            }
        }

        if (eventType.equals("FILTER_DATA")) {
            winkerkEntry.SORTORDER = "Filter";
            if (sortOrder.length() == 0) {
                sortOrder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ");
            } else {
                sortOrder.append(", ").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ");
            }
        }

        if (eventType.equals("LIDMAAT_DATA_VERJAAR")) {
            sortOrder.append(" strftime('%m', birthdate) ASC, strftime('%d', birthdate) ASC");
        }

        if (eventType.equals("OUDERDOM_DATA")) {
            winkerkEntry.SORTORDER = "OUDERDOM";
            sortOrder.append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC");
        }
    }

    private String buildFilterText() {
        if (filterList == null || filterList.isEmpty()) {
            return "";
        }

        StringBuilder filtertext = new StringBuilder();
        int filter_fields = 0;

        for (int i = 0; i < filterList.size(); i++) {
            if (filterList.get(i).checked()) {
                filter_fields++;
                if (filter_fields == 1) {
                    filtertext.append("FILTER: (");
                } else {
                    filtertext.append(") EN (");
                }
                String toets = filterList.get(i).getText3();

                if (toets.equals("gelyk aan")) {
                    filtertext.append(filterList.get(i).getTitle())
                            .append(" = '")
                            .append(filterList.get(i).getText1())
                            .append("'");
                }
                if (toets.equals("is nie") || (toets.equals("nie gelyk aan"))) {
                    filtertext.append(filterList.get(i).getTitle())
                            .append(" is nie '")
                            .append(filterList.get(i).getText1())
                            .append("'");
                }
                if (toets.equals("begin met")) {
                    filtertext.append(filterList.get(i).getTitle())
                            .append(" begin met '")
                            .append(filterList.get(i).getText1())
                            .append("%'");
                }
                if (toets.equals("eindig met")) {
                    filtertext.append(filterList.get(i).getTitle())
                            .append(" eindig met '")
                            .append(filterList.get(i).getText1())
                            .append("%'");
                }
                if (toets.equals("leeg")) {
                    filtertext.append(filterList.get(i).getTitle())
                            .append(" is leeg");
                }
                if (toets.equals("kleiner as")) {
                    filtertext.append("Ouderdom is kleiner as ")
                            .append(filterList.get(i).getText1());
                }
                if (toets.equals("groter as")) {
                    filtertext.append("Ouderdom is groter as ")
                            .append(filterList.get(i).getText1());
                }
                if (toets.equals("tussen") && filterList.get(i).getTitle().equals("Ouderdom")) {
                    filtertext.append("Ouderdom is tussen ")
                            .append(filterList.get(i).getText1())
                            .append(" en ")
                            .append(filterList.get(i).getText2());
                }
                if (toets.equals("gelyk") && filterList.get(i).getTitle().equals("Ouderdom")) {
                    filtertext.append("Ouderdom = ")
                            .append(filterList.get(i).getText1());
                }
                if (filterList.get(i).getTitle().equals("Geslag")) {
                    if (toets.equals("manlik")) {
                        filtertext.append("alle MANS");
                    } else {
                        filtertext.append("alle VROUE");
                    }
                }
                if (filterList.get(i).getTitle().equals("Selfoon")) {
                    filtertext.append("Almal met selfoon");
                }
                if (filterList.get(i).getTitle().equals("E-pos")) {
                    filtertext.append("Almal met epos");
                }
                if (filterList.get(i).getTitle().equals("Landlyn")) {
                    filtertext.append("Almal met landlyn");
                }
                if (filterList.get(i).getTitle().equals("Huwelikstatus")) {
                    filtertext.append("Almal wat ")
                            .append(filterList.get(i).getText3())
                            .append(" is");
                }
                if (filterList.get(i).getTitle().equals("Lidmaatskap")) {
                    filtertext.append("Waar Lidmaatskapstatus ")
                            .append(filterList.get(i).getText3()).append(" is");
                }
                if (filterList.get(i).getTitle().equals("Gesinshoof")) {
                    filtertext.append("Almal wat GESINSHOOFDE is");
                }
            }
        }

        if (filter_fields > 0) {
            filtertext.append(")");
        }

        return filtertext.toString();
    }

    private MutableLiveData<CursorWrapper> getLiveDataForEventType(String eventType) {
        switch (eventType) {
            case "GESINNE_DATA": return GESINNE_DATA;
            case "FILTER_DATA": return FILTER_DATA;
            case "LIDMAAT_DATA": return LIDMAAT_DATA;
            case "LIDMAAT_DATA_WYK": return LIDMAAT_DATA_WYK;
            case "SOEK_DATA": return SOEK_DATA;
            case "LIDMAAT_DATA_VERJAAR": return LIDMAAT_DATA_VERJAAR;
            case "OUDERDOM_DATA": return OUDERDOM_DATA;
            case "LIDMAAT_DATA_ADRES": return LIDMAAT_DATA_ADRES;
            case "TAGGED_DATA": return TAGGED_DATA;
            case "HUWELIK_DATA": return HUWELIK_DATA;
            case "ARGIEF_DATA": return ARGIEF_DATA;
            default: return null;
        }
    }

    private void postCursorToLiveData(String eventType, CursorWrapper wrapper, String filterText) {
        try {
            switch (eventType) {
                case "GESINNE_DATA":
                    GESINNE_DATA.postValue(wrapper);
                    break;
                case "FILTER_DATA":
                    FILTER_DATA.postValue(wrapper);
                    textLiveData.postValue(filterText);
                    break;
                case "LIDMAAT_DATA":
                    LIDMAAT_DATA.postValue(wrapper);
                    break;
                case "LIDMAAT_DATA_WYK":
                    LIDMAAT_DATA_WYK.postValue(wrapper);
                    break;
                case "SOEK_DATA":
                    SOEK_DATA.postValue(wrapper);
                    textLiveData.postValue(winkerkEntry.SOEK);
                    break;
                case "LIDMAAT_DATA_VERJAAR":
                    LIDMAAT_DATA_VERJAAR.postValue(wrapper);
                    VerjaarFlag.postValue(true);
                    break;
                case "OUDERDOM_DATA":
                    OUDERDOM_DATA.postValue(wrapper);
                    break;
                case "LIDMAAT_DATA_ADRES":
                    LIDMAAT_DATA_ADRES.postValue(wrapper);
                    break;
                case "TAGGED_DATA":
                    TAGGED_DATA.postValue(wrapper);
                    break;
                case "HUWELIK_DATA":
                    HUWELIK_DATA.postValue(wrapper);
                    break;
                case "ARGIEF_DATA":
                    ARGIEF_DATA.postValue(wrapper);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error posting cursor to LiveData for " + eventType + ": " + e.getMessage());
            if (wrapper != null) {
                wrapper.close();
            }
        }
    }

    private Cursor queryDatabase(Context context, String query) {
        try {
            return context.getContentResolver().query(
                    winkerkEntry.CONTENT_URI,
                    null,
                    query,
                    null,
                    null
            );
        } catch (Exception e) {
            Log.e(TAG, "Error executing query: " + e.getMessage(), e);
            return null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        synchronized (cursorLock) {
            closeAllCursors();
            queryCache.clear();
            isProcessing.set(false);
            Log.d(TAG, "ViewModel cleared, all resources released");
        }
    }

    private void closeAllCursors() {
        try {
            closeCursorInLiveData(LIDMAAT_DATA);
            closeCursorInLiveData(LIDMAAT_DATA_WYK);
            closeCursorInLiveData(SOEK_DATA);
            closeCursorInLiveData(LIDMAAT_DATA_VERJAAR);
            closeCursorInLiveData(GEMEENTENAAM);
            closeCursorInLiveData(LIDMAAT_DATA_ADRES);
            closeCursorInLiveData(OUDERDOM_DATA);
            closeCursorInLiveData(INFO_DATA);
            closeCursorInLiveData(FILTER_DATA);
            closeCursorInLiveData(FOTO_UPDATE_DATA);
            closeCursorInLiveData(GESINNE_DATA);
            closeCursorInLiveData(TAGGED_DATA);
            closeCursorInLiveData(HUWELIK_DATA);
            closeCursorInLiveData(ARGIEF_DATA);
        } catch (Exception e) {
            Log.e(TAG, "Error closing all cursors: " + e.getMessage());
        }
    }

    private void closeCursorInLiveData(MutableLiveData<CursorWrapper> liveData) {
        try {
            if (liveData != null && liveData.getValue() != null) {
                CursorWrapper wrapper = liveData.getValue();
                if (wrapper != null && !wrapper.isClosed()) {
                    wrapper.close();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error closing cursor in LiveData: " + e.getMessage());
        }
    }

    public void clearQueryCache() {
        queryCache.clear();
        lastEventType = "";
        lastSearchTerm = "";
        lastFilterList = null;
        Log.d(TAG, "Query cache manually cleared");
    }
}