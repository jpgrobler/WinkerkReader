package za.co.jpsoft.winkerkreader

import android.content.Context
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import za.co.jpsoft.winkerkreader.data.FilterBox
import za.co.jpsoft.winkerkreader.data.WinkerkContract.col
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import java.util.concurrent.atomic.AtomicBoolean

class MemberViewModel : ViewModel() {
    companion object {
        private val TAG = "MemberViewModel"
    }

    private val queryCache = QueryCache(10)
    private var lastEventType = ""
    private var lastSearchTerm = ""
    private var lastFilterList: ArrayList<FilterBox>? = null
    private val cursorLock = Any()
    private val isProcessing = AtomicBoolean(false)

    private val LIDMAAT_DATA = MutableLiveData<CursorWrapper>()
    private val LIDMAAT_DATA_WYK = MutableLiveData<CursorWrapper>()
    private val SOEK_DATA = MutableLiveData<CursorWrapper>()
    private val LIDMAAT_DATA_VERJAAR = MutableLiveData<CursorWrapper>()
    private val GEMEENTENAAM = MutableLiveData<CursorWrapper>()
    private val LIDMAAT_DATA_ADRES = MutableLiveData<CursorWrapper>()
    private val OUDERDOM_DATA = MutableLiveData<CursorWrapper>()
    private val INFO_DATA = MutableLiveData<CursorWrapper>()
    private val FILTER_DATA = MutableLiveData<CursorWrapper>()
    private val FOTO_UPDATE_DATA = MutableLiveData<CursorWrapper>()
    private val GESINNE_DATA = MutableLiveData<CursorWrapper>()
    private val TAGGED_DATA = MutableLiveData<CursorWrapper>()
    private val HUWELIK_DATA = MutableLiveData<CursorWrapper>()
    private val ARGIEF_DATA = MutableLiveData<CursorWrapper>()

    private var searchList: ArrayList<SearchCheckBox>? = null
    private var filterList: ArrayList<FilterBox>? = null
    private val textLiveData = MutableLiveData<String>()
    private val VerjaarFlag = MutableLiveData<Boolean>()
    private val rowCount = MutableLiveData<Int>()

    class CursorWrapper(private val cursor: Cursor?) {
        @Volatile
        private var isClosed = false
        private val lock = Any()

        fun getCursor(): Cursor? = synchronized(lock) { if (isClosed) null else cursor }

        fun close() = synchronized(lock) {
            if (cursor != null && !isClosed) {
                try {
                    cursor.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing cursor: ${e.message}")
                } finally {
                    isClosed = true
                }
            }
        }

        fun isClosed(): Boolean = synchronized(lock) { isClosed }
    }

    private class QueryCache(maxSize: Int) {
        private val cache = LruCache<String, String>(maxSize)
        fun get(key: String): String? = cache.get(key)
        fun put(key: String, value: String) = cache.put(key, value)
        fun clear() = cache.evictAll()
    }

    fun getRowCount(): LiveData<Int> = rowCount

    // All public LiveData getters now return nullable Cursor (Cursor?)
    fun getLIDMAAT_DATA(context: MainActivity2): LiveData<Cursor?> =
        observeCursorWrapper(LIDMAAT_DATA) { fetchData(context, "LIDMAAT_DATA") }

    fun getLIDMAAT_DATA_WYK(context: MainActivity2): LiveData<Cursor?> =
        observeCursorWrapper(LIDMAAT_DATA_WYK) { fetchData(context, "LIDMAAT_DATA_WYK") }

    fun getSOEK_DATA(context: MainActivity2): LiveData<Cursor?> =
        observeCursorWrapper(SOEK_DATA) { fetchData(context, "SOEK_DATA") }

    fun getLIDMAAT_DATA_VERJAAR(context: MainActivity2): LiveData<Cursor?> =
        observeCursorWrapper(LIDMAAT_DATA_VERJAAR) { fetchData(context, "LIDMAAT_DATA_VERJAAR") }

    fun getVerjaarFLag(): LiveData<Boolean> = VerjaarFlag

    fun getLIDMAAT_DATA_ADRES(context: MainActivity2): LiveData<Cursor?> =
        observeCursorWrapper(LIDMAAT_DATA_ADRES) { fetchData(context, "LIDMAAT_DATA_ADRES") }

    fun getOUDERDOM_DATA(context: MainActivity2): LiveData<Cursor?> =
        observeCursorWrapper(OUDERDOM_DATA) { fetchData(context, "OUDERDOM_DATA") }



    fun getFILTER_DATA(context: MainActivity2, filterLys: ArrayList<FilterBox>): LiveData<Cursor?> {
        filterList = filterLys
        return observeCursorWrapper(FILTER_DATA) { fetchData(context, "FILTER_DATA") }
    }

    fun getTextLiveData(): LiveData<String> = textLiveData



    fun getGESINNE_DATA(context: MainActivity2): LiveData<Cursor?> =
        observeCursorWrapper(GESINNE_DATA) { fetchData(context, "GESINNE_DATA") }



    fun getHUWELIK_DATA(context: MainActivity2): LiveData<Cursor?> =
        observeCursorWrapper(HUWELIK_DATA) { fetchData(context, "HUWELIK_DATA") }



    // observeCursorWrapper now returns LiveData<Cursor?> (nullable)
    private fun observeCursorWrapper(
        wrapperLiveData: MutableLiveData<CursorWrapper>,
        fetchAction: () -> Unit
    ): LiveData<Cursor?> {
        val cursorLiveData = MutableLiveData<Cursor?>()  // nullable type
        var lastWrapper: CursorWrapper? = null

        val observer = Observer<CursorWrapper> { newWrapper ->
            synchronized(cursorLock) {
                lastWrapper?.let { wrapper ->
                    if (!wrapper.isClosed()) {
                        val wrapperToClose = wrapper
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                wrapperToClose.close()
                            } catch (e: Exception) {
                                Log.w(TAG, "Error closing delayed cursor: ${e.message}")
                            }
                        }, 500)
                    }
                }

                lastWrapper = newWrapper
                val cursor = newWrapper.getCursor()
                cursorLiveData.postValue(cursor)   // cursor is Cursor?, OK now
//                if (newWrapper != null && !newWrapper.isClosed()) {
//                    val cursor = newWrapper.getCursor()
//                    cursorLiveData.postValue(cursor)   // cursor is Cursor?, OK now
//                } else {
//                    cursorLiveData.postValue(null)     // null allowed
//                }
            }
        }

        wrapperLiveData.observeForever(observer)
        fetchAction.invoke()
        return cursorLiveData
    }

    private fun needsQueryRebuild(eventType: String): Boolean {
        var changed = false
        if (eventType != lastEventType) changed = true
        if (eventType == "SOEK_DATA" && winkerkEntry.SOEK != lastSearchTerm) changed = true
        if (eventType == "FILTER_DATA" && !filterListsEqual(filterList, lastFilterList)) changed = true
        return changed
    }

    private fun filterListsEqual(a: ArrayList<FilterBox>?, b: ArrayList<FilterBox>?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        if (a.size != b.size) return false
        return a.zip(b).all { (itemA, itemB) -> itemA.toString() == itemB.toString() }
    }

    private fun fetchData(context: Context, eventType: String) {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.w(TAG, "Fetch already in progress, skipping: $eventType")
            return
        }

        try {
            VerjaarFlag.postValue(false)

            val cacheKey = buildCacheKey(eventType)
            val cachedQuery = queryCache.get(cacheKey)

            val selection: String
            var filtertextF = ""

            if (cachedQuery != null && !needsQueryRebuild(eventType)) {
                selection = cachedQuery
                Log.d(TAG, "Using cached query for: $eventType")
            } else {
                selection = buildQuery(context, eventType)
                    ?: run {
                        Log.e(TAG, "Failed to build query for: $eventType")
                        return
                    }

                queryCache.put(cacheKey, selection)

                lastEventType = eventType
                if (eventType == "SOEK_DATA") {
                    lastSearchTerm = winkerkEntry.SOEK
                }
                if (eventType == "FILTER_DATA") {
                    lastFilterList = filterList?.let { ArrayList(it) }
                }

                Log.d(TAG, "Built new query for: $eventType")
            }

            if (eventType == "FILTER_DATA") {
                filtertextF = buildFilterText()
            }

            val result = SQLiteStatementValidator.validateAndFixSQLiteStatement(selection)
            val finalSelection = if (result.isValid) {
                if (result.wasFixed) {
                    Log.i(TAG, "Query was fixed: ${result.fixedSql}")
                }
                result.fixedSql ?: error("fixedSql cannot be null when isValid is true")
            } else {
                Log.e(TAG, "Could not fix SQL: ${result.errorMessage}")
                return
            }

            val cursor = try {
                queryDatabase(context, finalSelection)
            } catch (e: Exception) {
                Log.e(TAG, "Error querying database: ${e.message}", e)
                return
            }

            synchronized(cursorLock) {
                val wrapper = CursorWrapper(cursor)
                val count = cursor?.count ?: 0
                rowCount.postValue(count)
                postCursorToLiveData(eventType, wrapper, filtertextF)
            }
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
                    append("_").append(winkerkEntry.SOEK)
                    append("_").append(winkerkEntry.SORTORDER)
                    searchList?.forEach { item ->
                        if (item.isChecked) {  // Fixed: removed parentheses
                            append("_").append(item.columnName)
                        }
                    }
                }
                "FILTER_DATA" -> {
                    filterList?.forEach { filter ->
                        if (filter.checked) {  // Fixed: removed parentheses
                            append("_").append(filter.title)
                                .append("_").append(filter.text1)
                                .append("_").append(filter.text3)
                        }
                    }
                }
            }
        }
    }

    private fun buildQuery(context: Context, eventType: String): String? {
        return when (eventType) {
            "GESINNE_DATA", "FILTER_DATA", "LIDMAAT_DATA", "LIDMAAT_DATA_WYK",
            "SOEK_DATA", "LIDMAAT_DATA_VERJAAR", "OUDERDOM_DATA", "LIDMAAT_DATA_ADRES",
            "TAGGED_DATA", "HUWELIK_DATA" -> buildMemberQuery(context, eventType)
            else -> {
                Log.e(TAG, "Invalid event type: $eventType")
                null
            }
        }
    }

    private fun buildMemberQuery(context: Context, eventType: String): String {
        winkerkEntry.SOEKLIST = false
        val selectionBase = winkerkEntry.SELECTION_LIDMAAT_INFO
        val from = " Members "
        val where = StringBuilder()
        val sortOrder = StringBuilder()

        // Base WHERE clause with proper column wrapping
        where.append(" (").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
            .append(col(winkerkEntry.LIDMATE_REKORDSTATUS)).append(" = '")
            .append(winkerkEntry.RECORDSTATUS).append("' )")

        appendWhereClause(context, eventType, where) //, sortOrder)
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
            "TAGGED_DATA" -> where.append(" AND (").append(col(winkerkEntry.LIDMATE_TAG)).append(" = 1 )")
            "HUWELIK_DATA" -> where.append(" AND ").append(winkerkEntry.SELECTION_HUWELIK_WHERE)
            "SOEK_DATA" -> {
                val prefsManager = SearchCheckBoxPreferences(context)
                searchList = prefsManager.getSearchCheckBoxList()
                searchList?.let { list ->
                    val searchFields = list.filter { it.isChecked }.size  // Fixed: removed parentheses
                    if (searchFields > 0) {
                        where.append(" AND (")
                        list.filter { it.isChecked }.forEachIndexed { index, item ->  // Fixed: removed parentheses
                            if (index > 0) where.append(" OR ")
                            where.append(col(item.columnName)).append(" LIKE '%").append(winkerkEntry.SOEK).append("%'")
                        }
                        where.append(" )")
                    }
                }
                winkerkEntry.SOEKLIST = true
            }
            "FILTER_DATA" -> {
                filterList?.let { list ->
                    val filterFields = list.filter { it.checked }  // Fixed: removed parentheses
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
                winkerkEntry.SOEKLIST = true
            }
        }
    }

    private fun appendOrderByClause(eventType: String, sortOrder: StringBuilder) {
        when (eventType) {
            "LIDMAAT_DATA" -> {
                winkerkEntry.SORTORDER = "VAN"
                sortOrder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "GESINNE_DATA" -> {
                winkerkEntry.SORTORDER = "GESINNE"
                sortOrder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "LIDMAAT_DATA_WYK" -> {
                winkerkEntry.SORTORDER = "WYK"
                sortOrder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_WYK)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "LIDMAAT_DATA_ADRES" -> {
                winkerkEntry.SORTORDER = "ADRES"
                sortOrder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_STRAATADRES)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "TAGGED_DATA" -> sortOrder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            "HUWELIK_DATA" -> {
                winkerkEntry.SORTORDER = "HUWELIK"
                sortOrder.append(" strftime('%m', ").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_HUWELIKSDATUM)).append(") ASC,  strftime('%d', ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_HUWELIKSDATUM)).append(") ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESLAG)).append(" DESC")
            }
            "SOEK_DATA" -> {
                when (winkerkEntry.SORTORDER) {
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
                winkerkEntry.SORTORDER = "Filter"
                if (sortOrder.isEmpty()) {
                    sortOrder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                } else {
                    sortOrder.append(", ").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                }
            }
            "LIDMAAT_DATA_VERJAAR" -> sortOrder.append(" strftime('%m', birthdate) ASC, strftime('%d', birthdate) ASC")
            "OUDERDOM_DATA" -> {
                winkerkEntry.SORTORDER = "OUDERDOM"
                sortOrder.append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC")
            }
        }
    }

    private fun buildFilterText(): String {
        if (filterList.isNullOrEmpty()) return ""
        val filtertext = StringBuilder()
        val filterFields = filterList!!.filter { it.checked }  // Fixed: removed parentheses
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



    private fun postCursorToLiveData(eventType: String, wrapper: CursorWrapper, filterText: String) {
        try {
            when (eventType) {
                "GESINNE_DATA" -> GESINNE_DATA.postValue(wrapper)
                "FILTER_DATA" -> {
                    FILTER_DATA.postValue(wrapper)
                    textLiveData.postValue(filterText)
                }
                "LIDMAAT_DATA" -> LIDMAAT_DATA.postValue(wrapper)
                "LIDMAAT_DATA_WYK" -> LIDMAAT_DATA_WYK.postValue(wrapper)
                "SOEK_DATA" -> {
                    SOEK_DATA.postValue(wrapper)
                    textLiveData.postValue(winkerkEntry.SOEK)
                }
                "LIDMAAT_DATA_VERJAAR" -> {
                    LIDMAAT_DATA_VERJAAR.postValue(wrapper)
                    VerjaarFlag.postValue(true)
                }
                "OUDERDOM_DATA" -> OUDERDOM_DATA.postValue(wrapper)
                "LIDMAAT_DATA_ADRES" -> LIDMAAT_DATA_ADRES.postValue(wrapper)
                "TAGGED_DATA" -> TAGGED_DATA.postValue(wrapper)
                "HUWELIK_DATA" -> HUWELIK_DATA.postValue(wrapper)
                "ARGIEF_DATA" -> ARGIEF_DATA.postValue(wrapper)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting cursor to LiveData for $eventType: ${e.message}")
            wrapper.close()
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
        super.onCleared()
        synchronized(cursorLock) {
            closeAllCursors()
            queryCache.clear()
            isProcessing.set(false)
            Log.d(TAG, "ViewModel cleared, all resources released")
        }
    }

    private fun closeAllCursors() {
        try {
            closeCursorInLiveData(LIDMAAT_DATA)
            closeCursorInLiveData(LIDMAAT_DATA_WYK)
            closeCursorInLiveData(SOEK_DATA)
            closeCursorInLiveData(LIDMAAT_DATA_VERJAAR)
            closeCursorInLiveData(GEMEENTENAAM)
            closeCursorInLiveData(LIDMAAT_DATA_ADRES)
            closeCursorInLiveData(OUDERDOM_DATA)
            closeCursorInLiveData(INFO_DATA)
            closeCursorInLiveData(FILTER_DATA)
            closeCursorInLiveData(FOTO_UPDATE_DATA)
            closeCursorInLiveData(GESINNE_DATA)
            closeCursorInLiveData(TAGGED_DATA)
            closeCursorInLiveData(HUWELIK_DATA)
            closeCursorInLiveData(ARGIEF_DATA)
        } catch (e: Exception) {
            Log.e(TAG, "Error closing all cursors: ${e.message}")
        }
    }

    private fun closeCursorInLiveData(liveData: MutableLiveData<CursorWrapper>?) {
        try {
            liveData?.value?.let { wrapper ->
                if (!wrapper.isClosed()) {
                    wrapper.close()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing cursor in LiveData: ${e.message}")
        }
    }
}