package za.co.jpsoft.winkerkreader.ui.viewmodels

import za.co.jpsoft.winkerkreader.utils.AppSessionState
import za.co.jpsoft.winkerkreader.utils.SQLiteStatementValidator
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry

import android.content.Context
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import za.co.jpsoft.winkerkreader.data.WinkerkContract.col
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import java.util.concurrent.atomic.AtomicBoolean

class MemberViewModel : ViewModel() {
    companion object {
        private val TAG = "MemberViewModel"
    }

    // LiveData that holds the current cursor (nullable)
    private val LIDMAAT_DATA = MutableLiveData<Cursor?>()
    private val LIDMAAT_DATA_WYK = MutableLiveData<Cursor?>()
    private val SOEK_DATA = MutableLiveData<Cursor?>()
    private val LIDMAAT_DATA_VERJAAR = MutableLiveData<Cursor?>()
    private val LIDMAAT_DATA_ADRES = MutableLiveData<Cursor?>()
    private val OUDERDOM_DATA = MutableLiveData<Cursor?>()
    private val GESINNE_DATA = MutableLiveData<Cursor?>()
    private val HUWELIK_DATA = MutableLiveData<Cursor?>()
    private val FILTER_DATA = MutableLiveData<Cursor?>()

    // Additional LiveData for UI state
    private val textLiveData = MutableLiveData<String>()
    private val verjaarFlag = MutableLiveData<Boolean>()
    private val rowCount = MutableLiveData<Int>()

    // Helper to track pending close tasks
    private val closeTasks = mutableMapOf<String, Runnable>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // To prevent concurrent fetches
    private val isProcessing = AtomicBoolean(false)

    // Last state for query caching
    private var lastEventType = ""
    private var lastSearchTerm = ""
    private var currentFilterList: ArrayList<FilterBox>? = null
    private var lastFilterListSnapshot: ArrayList<FilterBox>? = null

    // Cache for generated SQL queries
    private val queryCache = mutableMapOf<String, String>()

    // Public LiveData getters
    fun getRowCount(): LiveData<Int> = rowCount

    fun getLIDMAAT_DATA(): LiveData<Cursor?> = LIDMAAT_DATA

    fun getLIDMAAT_DATA_WYK(): LiveData<Cursor?> = LIDMAAT_DATA_WYK

    fun getSOEK_DATA(): LiveData<Cursor?> = SOEK_DATA

    fun getLIDMAAT_DATA_VERJAAR(): LiveData<Cursor?> = LIDMAAT_DATA_VERJAAR

    fun getVerjaarFLag(): LiveData<Boolean> = verjaarFlag

    fun getLIDMAAT_DATA_ADRES(): LiveData<Cursor?> = LIDMAAT_DATA_ADRES

    fun getOUDERDOM_DATA(): LiveData<Cursor?> = OUDERDOM_DATA

    fun getGESINNE_DATA(): LiveData<Cursor?> = GESINNE_DATA

    fun getHUWELIK_DATA(): LiveData<Cursor?> = HUWELIK_DATA

    fun getFILTER_DATA(): LiveData<Cursor?> = FILTER_DATA

    fun loadData(context: Context, eventType: String, filterLys: ArrayList<FilterBox>? = null) {
        if (eventType == "FILTER_DATA") {
            currentFilterList = filterLys ?: arrayListOf()
        }
        fetchData(context, eventType)
    }

    fun getTextLiveData(): LiveData<String> = textLiveData

    // Core data fetching logic
    private fun fetchData(context: Context, eventType: String) {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "Fetch already in progress, skipping: $eventType")
            return
        }

        try {
            verjaarFlag.postValue(false)

            val cacheKey = buildCacheKey(eventType)
            val cachedQuery = queryCache[cacheKey]

            val selection: String = if (cachedQuery != null && !needsQueryRebuild(eventType)) {
                cachedQuery
            } else {
                buildQuery(context, eventType)?.also {
                    queryCache[cacheKey] = it
                    updateLastState(eventType)
                } ?: run {
                    Log.e(TAG, "Failed to build query for: $eventType")
                    return
                }
            }

            // Validate SQL
            val result = SQLiteStatementValidator.validateAndFixSQLiteStatement(selection)
            val finalSelection = if (result.isValid) {
                result.fixedSql ?: error("fixedSql cannot be null when isValid is true")
            } else {
                Log.e(TAG, "Could not fix SQL: ${result.errorMessage}")
                return
            }

            // Execute query
            val cursor = queryDatabase(context.applicationContext, finalSelection)

            // Update row count
            rowCount.postValue(cursor?.count ?: 0)

            // Post cursor to the appropriate LiveData
            postCursorToLiveData(eventType, cursor)

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in fetchData: ${e.message}", e)
        } finally {
            isProcessing.set(false)
        }
    }

    private fun buildCacheKey(eventType: String): String {
        return buildString {
            append(eventType)
            when (eventType) {
                "SOEK_DATA" -> {
                    append("_").append(AppSessionState.soek)
                    append("_").append(AppSessionState.sortOrder)
                    // Use the stored searchList (must be set by activity before any SOEK_DATA fetch)
                    searchList?.filter { it.isChecked }?.forEach {
                        append("_").append(it.columnName)
                    }
                }
                "FILTER_DATA" -> {
                    currentFilterList?.filter { it.checked }?.forEach { filter ->
                        append("_").append(filter.title)
                            .append("_").append(filter.text1)
                            .append("_").append(filter.text3)
                    }
                }
            }
        }
    }

    // Store searchList as member variable (set from activity when needed)
    private var searchList: List<SearchCheckBox>? = null

    fun setSearchList(list: List<SearchCheckBox>) {
        searchList = list
    }

    private fun needsQueryRebuild(eventType: String): Boolean {
        return when {
            eventType != lastEventType -> true
            eventType == "SOEK_DATA" && AppSessionState.soek != lastSearchTerm -> true
            eventType == "FILTER_DATA" && !filterListsEqual(currentFilterList, lastFilterListSnapshot) -> true
            else -> false
        }
    }

    private fun filterListsEqual(a: ArrayList<FilterBox>?, b: ArrayList<FilterBox>?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        if (a.size != b.size) return false
        return a.zip(b).all { (itemA, itemB) -> itemA.toString() == itemB.toString() }
    }

    private fun updateLastState(eventType: String) {
        lastEventType = eventType
        if (eventType == "SOEK_DATA") {
            lastSearchTerm = AppSessionState.soek
        }
        if (eventType == "FILTER_DATA") {
            lastFilterListSnapshot = currentFilterList?.let { ArrayList(it) }
        }
    }

    private fun buildQuery(context: Context, eventType: String): String? {
        return when (eventType) {
            "GESINNE_DATA", "FILTER_DATA", "LIDMAAT_DATA", "LIDMAAT_DATA_WYK",
            "SOEK_DATA", "LIDMAAT_DATA_VERJAAR", "OUDERDOM_DATA", "LIDMAAT_DATA_ADRES",
            "HUWELIK_DATA" -> buildMemberQuery(context, eventType)
            else -> {
                Log.e(TAG, "Invalid event type: $eventType")
                null
            }
        }
    }

    private fun buildMemberQuery(context: Context, eventType: String): String {
        AppSessionState.soekList = false
        val selectionBase = winkerkEntry.SELECTION_LIDMAAT_INFO
        val from = " Members "
        val where = StringBuilder()
        val sortOrder = StringBuilder()

        // Base WHERE clause
        where.append(" (").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
            .append(col(winkerkEntry.LIDMATE_REKORDSTATUS)).append(" = '")
            .append(AppSessionState.recordStatus).append("' )")

        appendWhereClause(context, eventType, where)
        appendOrderByClause(eventType, sortOrder)

        val finalFrom: String
        val finalSelection: String
        if (eventType == "GESINNE_DATA" || eventType == "LIDMAAT_DATA_WYK" || eventType == "LIDMAAT_DATA_ADRES") {
            finalSelection = selectionBase + winkerkEntry.SELECTION_LIDMAAT_INFO_GESINSHOOF
            finalFrom = winkerkEntry.SELECTION_LIDMAAT_FROM_GESINSHOOF
        } else {
            finalSelection = selectionBase
            finalFrom = from
        }

        return if (where.isEmpty()) {
            "$finalSelection From $finalFrom ORDER BY $sortOrder;"
        } else {
            "$finalSelection From $finalFrom WHERE $where ORDER BY $sortOrder;"
        }
    }

    private fun appendWhereClause(context: Context, eventType: String, where: StringBuilder) {
        when (eventType) {
            "HUWELIK_DATA" -> where.append(" AND ").append(winkerkEntry.SELECTION_HUWELIK_WHERE)
            "SOEK_DATA" -> {
                // Use the stored searchList (should be set before calling getSOEK_DATA)
                val list = searchList
                if (!list.isNullOrEmpty()) {
                    val searchFields = list.filter { it.isChecked }.size
                    if (searchFields > 0) {
                        where.append(" AND (")
                        list.filter { it.isChecked }.forEachIndexed { index, item ->
                            if (index > 0) where.append(" OR ")
                            where.append(col(item.columnName)).append(" LIKE '%").append(AppSessionState.soek).append("%'")
                        }
                        where.append(" )")
                    }
                }
                AppSessionState.soekList = true
            }
            "FILTER_DATA" -> {
                val list = currentFilterList
                if (!list.isNullOrEmpty()) {
                    val filterFields = list.filter { it.checked }
                    if (filterFields.isNotEmpty()) {
                        where.append(" AND (")
                        filterFields.forEachIndexed { index, filter ->
                            if (index > 0) where.append(") AND (")
                            val toets = filter.text3
                            when {
                                toets == "gelyk aan" -> where.append(col(filter.title)).append(" = '").append(filter.text1).append("'")
                                toets == "is nie" || toets == "nie gelyk aan" -> where.append(col(filter.title)).append(" != '").append(filter.text1).append("'")
                                toets == "begin met" -> where.append(col(filter.title)).append(" Like '").append(filter.text1).append("%'")
                                toets == "eindig met" -> where.append(col(filter.title)).append(" Like '%").append(filter.text1).append("'")
                                toets == "leeg" -> where.append(col(filter.title)).append(" IS NULL ")
                                toets == "kleiner as" -> where.append("((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', birthdate))) < ").append(filter.text1)
                                toets == "groter as" -> where.append("((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', birthdate))) > ").append(filter.text1)
                                toets == "tussen" && filter.title == "Ouderdom" -> where.append(" ( ((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', birthdate))) >= ").append(filter.text1).append(" ) AND ( ((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', birthdate))) <= ").append(filter.text2).append(" )")
                                toets == "gelyk" && filter.title == "Ouderdom" -> where.append("((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', birthdate))) = ").append(filter.text1)
                                filter.title == "Geslag" -> where.append(col(winkerkEntry.LIDMATE_GESLAG)).append(" = '").append(if (toets == "manlik") "Manlik" else "Vroulik").append("'")
                                filter.title == "Selfoon" -> where.append(" ( ").append(col(winkerkEntry.LIDMATE_SELFOON)).append(" IS NOT NULL AND ").append(col(winkerkEntry.LIDMATE_SELFOON)).append(" != '' )")
                                filter.title == "E-pos" -> where.append(" ( ").append(col(winkerkEntry.LIDMATE_EPOS)).append(" IS NOT NULL AND ").append(col(winkerkEntry.LIDMATE_EPOS)).append(" != '' )")
                                filter.title == "Landlyn" -> where.append(" ( ").append(col(winkerkEntry.LIDMATE_LANDLYN)).append(" IS NOT NULL AND ").append(col(winkerkEntry.LIDMATE_LANDLYN)).append(" != '' )")
                                filter.title == "Huwelikstatus" -> where.append(col(winkerkEntry.LIDMATE_HUWELIKSTATUS)).append(" = '").append(filter.text3).append("'")
                                filter.title == "Lidmaatskap" -> {
                                    if (filter.text3 == "Belydend") {
                                        where.append(col(winkerkEntry.LIDMATE_LIDMAATSTATUS)).append(" LIKE 'Bely%'")
                                    } else {
                                        where.append(col(winkerkEntry.LIDMATE_LIDMAATSTATUS)).append(" LIKE '").append(filter.text3).append("'")
                                    }
                                }
                                filter.title == "Gesinshoof" -> where.append(" quote(").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(") = quote(").append(col(winkerkEntry.LIDMATE_LIDMAATGUID)).append(")")
                            }
                        }
                        where.append(" )")
                    }
                }
                AppSessionState.soekList = true
            }
        }
    }

    private fun appendOrderByClause(eventType: String, sortOrder: StringBuilder) {
        when (eventType) {
            "LIDMAAT_DATA" -> {
                AppSessionState.sortOrder = "VAN"
                sortOrder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "GESINNE_DATA" -> {
                AppSessionState.sortOrder = "GESINNE"
                sortOrder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "LIDMAAT_DATA_WYK" -> {
                AppSessionState.sortOrder = "WYK"
                sortOrder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_WYK)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "LIDMAAT_DATA_ADRES" -> {
                AppSessionState.sortOrder = "ADRES"
                sortOrder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_STRAATADRES)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "HUWELIK_DATA" -> {
                AppSessionState.sortOrder = "HUWELIK"
                sortOrder.append(" strftime('%m', ").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_HUWELIKSDATUM)).append(") ASC,  strftime('%d', ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_HUWELIKSDATUM)).append(") ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESLAG)).append(" DESC")
            }
            "SOEK_DATA" -> {
                when (AppSessionState.sortOrder) {
                    "GESINNE" -> sortOrder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                        .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                        .append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,")
                        .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                    "VAN" -> sortOrder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                    "ADRES" -> sortOrder.append(col(winkerkEntry.LIDMATE_STRAATADRES)).append(" ASC, ")
                        .append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                        .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                        .append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,")
                        .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                    "WYK" -> sortOrder.append(col(winkerkEntry.LIDMATE_WYK)).append(" ASC, ")
                        .append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                        .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                        .append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,")
                        .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                    "VERJAAR" -> sortOrder.append(" strftime('%m', birthdate) ASC, strftime('%d', birthdate) ASC")
                    "OUDERDOM" -> sortOrder.append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC")
                    else -> sortOrder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                }
            }
            "FILTER_DATA" -> {
                AppSessionState.sortOrder = "Filter"
                if (sortOrder.isEmpty()) {
                    sortOrder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                } else {
                    sortOrder.append(", ").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                }
            }
            "LIDMAAT_DATA_VERJAAR" -> sortOrder.append(" strftime('%m', birthdate) ASC, strftime('%d', birthdate) ASC")
            "OUDERDOM_DATA" -> {
                AppSessionState.sortOrder = "OUDERDOM"
                sortOrder.append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC")
            }
        }
    }

    private fun buildFilterText(): String {
        if (currentFilterList.isNullOrEmpty()) return ""
        val filtertext = StringBuilder()
        val filterFields = currentFilterList!!.filter { it.checked }
        if (filterFields.isNotEmpty()) {
            filtertext.append("FILTER: (")
            filterFields.forEachIndexed { index, filter ->
                if (index > 0) filtertext.append(") EN (")
                val toets = filter.text3
                when {
                    toets == "gelyk aan" -> filtertext.append(filter.title).append(" = '").append(filter.text1).append("'")
                    toets == "is nie" || toets == "nie gelyk aan" -> filtertext.append(filter.title).append(" is nie '").append(filter.text1).append("'")
                    toets == "begin met" -> filtertext.append(filter.title).append(" begin met '").append(filter.text1).append("%'")
                    toets == "eindig met" -> filtertext.append(filter.title).append(" eindig met '").append(filter.text1).append("%'")
                    toets == "leeg" -> filtertext.append(filter.title).append(" is leeg")
                    toets == "kleiner as" -> filtertext.append("Ouderdom is kleiner as ").append(filter.text1)
                    toets == "groter as" -> filtertext.append("Ouderdom is groter as ").append(filter.text1)
                    toets == "tussen" && filter.title == "Ouderdom" -> filtertext.append("Ouderdom is tussen ").append(filter.text1).append(" en ").append(filter.text2)
                    toets == "gelyk" && filter.title == "Ouderdom" -> filtertext.append("Ouderdom = ").append(filter.text1)
                    filter.title == "Geslag" -> filtertext.append(if (toets == "manlik") "alle MANS" else "alle VROUE")
                    filter.title == "Selfoon" -> filtertext.append("Almal met selfoon")
                    filter.title == "E-pos" -> filtertext.append("Almal met epos")
                    filter.title == "Landlyn" -> filtertext.append("Almal met landlyn")
                    filter.title == "Huwelikstatus" -> filtertext.append("Almal wat ").append(filter.text3).append(" is")
                    filter.title == "Lidmaatskap" -> filtertext.append("Waar Lidmaatskapstatus ").append(filter.text3).append(" is")
                    filter.title == "Gesinshoof" -> filtertext.append("Almal wat GESINSHOOFDE is")
                }
            }
            filtertext.append(")")
        }
        return filtertext.toString()
    }

    private fun postCursorToLiveData(eventType: String, newCursor: Cursor?) {
        // Get the LiveData for this event type
        val liveData = when (eventType) {
            "LIDMAAT_DATA" -> LIDMAAT_DATA
            "LIDMAAT_DATA_WYK" -> LIDMAAT_DATA_WYK
            "SOEK_DATA" -> SOEK_DATA
            "LIDMAAT_DATA_VERJAAR" -> LIDMAAT_DATA_VERJAAR
            "LIDMAAT_DATA_ADRES" -> LIDMAAT_DATA_ADRES
            "OUDERDOM_DATA" -> OUDERDOM_DATA
            "GESINNE_DATA" -> GESINNE_DATA
            "HUWELIK_DATA" -> HUWELIK_DATA
            "FILTER_DATA" -> FILTER_DATA
            else -> return
        }

        // Get the old cursor before posting new one
        val oldCursor = liveData.value

        // Post the new cursor
        liveData.postValue(newCursor)

        // Schedule closing of the old cursor after a short delay
        // This gives the adapter time to swap to the new cursor
        if (oldCursor != null && !oldCursor.isClosed) {
            val closeTask = Runnable {
                try {
                    if (!oldCursor.isClosed) {
                        oldCursor.close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing old cursor for $eventType", e)
                } finally {
                    closeTasks.remove(eventType)
                }
            }
            closeTasks[eventType] = closeTask
            mainHandler.postDelayed(closeTask, 200) // 200 ms should be enough
        }

        // For filter and search, update the text live data
        when (eventType) {
            "SOEK_DATA" -> textLiveData.postValue(AppSessionState.soek)
            "FILTER_DATA" -> textLiveData.postValue(buildFilterText())
        }

        // For birthdays, set the flag
        if (eventType == "LIDMAAT_DATA_VERJAAR") {
            verjaarFlag.postValue(true)
        }
    }

    private fun queryDatabase(context: Context, query: String): Cursor? {
        return try {
            context.contentResolver.query(
                winkerkEntry.CONTENT_URI,
                null,
                query,
                null,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing query: ${e.message}", e)
            null
        }
    }

    override fun onCleared() {
        // Cancel all pending close tasks
        closeTasks.forEach { (_, task) ->
            mainHandler.removeCallbacks(task)
        }
        closeTasks.clear()

        // Close any cursors still held by LiveData
        val liveDataList = listOf(
            LIDMAAT_DATA, LIDMAAT_DATA_WYK, SOEK_DATA, LIDMAAT_DATA_VERJAAR,
            LIDMAAT_DATA_ADRES, OUDERDOM_DATA, GESINNE_DATA, HUWELIK_DATA, FILTER_DATA
        )
        liveDataList.forEach { liveData ->
            liveData.value?.let { cursor ->
                if (!cursor.isClosed) {
                    try { cursor.close() } catch (e: Exception) { Log.w(TAG, "Error closing cursor", e) }
                }
            }
            liveData.value = null
        }

        // Clear caches
        queryCache.clear()
        isProcessing.set(false)

        Log.d(TAG, "ViewModel cleared, all resources released")
        super.onCleared()
    }
}