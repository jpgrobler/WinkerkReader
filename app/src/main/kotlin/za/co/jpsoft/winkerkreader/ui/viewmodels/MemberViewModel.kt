package za.co.jpsoft.winkerkreader.ui.viewmodels

import za.co.jpsoft.winkerkreader.utils.SQLiteStatementValidator
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.data.models.FilterBox

import android.content.Context
import android.database.Cursor
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.data.WinkerkContract.col
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.getIntOrDefault
import za.co.jpsoft.winkerkreader.utils.getStringOrEmpty
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import za.co.jpsoft.winkerkreader.ui.models.MainQueryMode
// import java.text.Spannable
// import android.text.SpannableString
// import android.text.style.RelativeSizeSpan
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ViewModel for the main member list screen.
 *
 * ### Threading model
 * [loadData] is called from the main thread. The query is executed synchronously via
 * [ContentResolver.query] (same as the previous implementation). The cursor-to-list
 * conversion also runs on the calling thread and the cursor is closed immediately
 * after conversion — no delayed-close hacks required.
 *
 * ### Data flow
 * ```
 * observeDataset() → viewModel.loadData()
 *   → buildQuery()  → queryDatabase()  → cursorToList()  → _memberList.postValue()
 *   → MainActivity.observer → memberListAdapter.submitList()
 * ```
 */
class MemberViewModel : ViewModel() {

    companion object {
        private const val TAG = "MemberViewModel"
    }

    // -------------------------------------------------------------------------
    // Single consolidated LiveData (replaces the previous 9 separate streams)
    // -------------------------------------------------------------------------
    private val _memberList = MutableLiveData<List<MemberItem>>(emptyList())
    private var lastRecordStatus = "0"

    /** The rendered member list ready for [MemberListAdapter.submitList]. */
    fun getMemberList(): LiveData<List<MemberItem>> = _memberList

    // -------------------------------------------------------------------------
    // UI-state LiveData (unchanged)
    // -------------------------------------------------------------------------
    private val textLiveData = MutableLiveData<String>()
    private val verjaarFlag  = MutableLiveData<Boolean>()
    private val rowCount     = MutableLiveData<Int>()

    fun getRowCount(): LiveData<Int>      = rowCount
    fun getTextLiveData(): LiveData<String> = textLiveData
    fun getVerjaarFLag(): LiveData<Boolean> = verjaarFlag

    // -------------------------------------------------------------------------
    // Session State (Migrated from AppSessionState)
    // -------------------------------------------------------------------------
    private var _sortOrder: String = "VAN"
    private var _soek: String = ""
    private var _soekList: Boolean = false
    private var _recordStatus: String = "0"

    var sortOrder: String
        get() = _sortOrder
        set(value) { _sortOrder = value }

    var soek: String
        get() = _soek
        set(value) { _soek = value }

    var soekList: Boolean
        get() = _soekList
        set(value) { _soekList = value }

    var recordStatus: String
        get() = _recordStatus
        set(value) { _recordStatus = value }

    private var searchList: List<SearchCheckBox>? = null

    fun setSearchList(list: List<SearchCheckBox>) { searchList = list }

    // -------------------------------------------------------------------------
    // Concurrency & caching (unchanged logic)
    // -------------------------------------------------------------------------
    private val isProcessing = AtomicBoolean(false)

    private var lastEventType = ""
    private var lastSearchTerm = ""
    private var currentFilterList: ArrayList<FilterBox>? = null
    private var lastFilterListSnapshot: ArrayList<FilterBox>? = null

    data class SqlRequest(val sql: String, val args: Array<String>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SqlRequest
            if (sql != other.sql) return false
            if (!args.contentEquals(other.args)) return false
            return true
        }
        override fun hashCode(): Int {
            var result = sql.hashCode()
            result = 31 * result + args.contentHashCode()
            return result
        }
    }

    private val queryCache = mutableMapOf<String, SqlRequest>()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun loadData(context: Context, mode: MainQueryMode) {
        val request = mode.toQueryRequest()
        if (request.eventType == "FILTER_DATA") {
            currentFilterList = request.filterList ?: arrayListOf()
        }
        fetchData(context, request.eventType)
    }

    @Deprecated("Use loadData(context, mode) with MainQueryMode")
    fun loadData(context: Context, eventType: String, filterLys: ArrayList<FilterBox>? = null) {
        if (eventType == "FILTER_DATA") {
            currentFilterList = filterLys ?: arrayListOf()
        }
        fetchData(context, eventType)
    }

    private data class QueryRequest(
        val eventType: String,
        val filterList: ArrayList<FilterBox>? = null
    )

    private fun MainQueryMode.toQueryRequest(): QueryRequest = when (this) {
        MainQueryMode.Search -> QueryRequest("SOEK_DATA")
        is MainQueryMode.Filter -> QueryRequest("FILTER_DATA", filters)
        MainQueryMode.Address -> QueryRequest("LIDMAAT_DATA_ADRES")
        MainQueryMode.Family -> QueryRequest("GESINNE_DATA")
        MainQueryMode.Wedding -> QueryRequest("HUWELIK_DATA")
        MainQueryMode.Age -> QueryRequest("OUDERDOM_DATA")
        MainQueryMode.Surname -> QueryRequest("LIDMAAT_DATA")
        MainQueryMode.Birthday -> QueryRequest("LIDMAAT_DATA_VERJAAR")
        MainQueryMode.Ward -> QueryRequest("LIDMAAT_DATA_WYK")
        is MainQueryMode.Raw -> QueryRequest(layout)
    }

    // -------------------------------------------------------------------------
    // Fetch pipeline
    // -------------------------------------------------------------------------

    private fun fetchData(context: Context, eventType: String) {
        val settingsManager = SettingsManager.getInstance(context)
//        if (!isProcessing.compareAndSet(false, true)) {
//            Log.d(TAG, "Fetch already in progress, skipping: $eventType")
//            return
//        }

        // For SOEK_DATA, we want to process even if already processing
        if (eventType != "SOEK_DATA" && !isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "Fetch already in progress, skipping: $eventType")
            return
        }
        // Signal the birthday flag reset immediately on the main thread before
        // launching the coroutine so the UI clears the scroll-trigger right away.
        verjaarFlag.postValue(false)

        // All heavy work — query building, SQL validation, ContentResolver I/O and
        // cursor-to-list conversion — runs on Dispatchers.IO.
        // postValue() is safe from any thread, so LiveData updates need no hop.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheKey    = buildCacheKey(eventType)
                val cachedQuery = queryCache[cacheKey]
                if (eventType == "SOEK_DATA") {
                    withContext(Dispatchers.Main) {
                        _memberList.value = emptyList()
                        rowCount.value = 0
                    }
                }
                val sqlRequest = if (cachedQuery != null && !needsQueryRebuild(eventType)) {
                    cachedQuery
                } else {
                    buildQuery(context, eventType)?.also {
                        queryCache[cacheKey] = it
                        updateLastState(eventType)
                    } ?: run {
                        Log.e(TAG, "Failed to build query for: $eventType")
                        return@launch
                    }
                }

                // Validate / sanitise the SQL
                val result = SQLiteStatementValidator.validateAndFixSQLiteStatement(sqlRequest.sql)
                val finalSelection = if (result.isValid) {
                    result.fixedSql ?: error("fixedSql cannot be null when isValid is true")
                } else {
                    Log.e(TAG, "Could not fix SQL: ${result.errorMessage}")
                    return@launch
                }

                // Snapshot sort order on the IO thread before the query so that a
                // concurrent main-thread write does not affect the
                // separator computation mid-flight.
                val sortOrderSnapshot = sortOrder

                // Execute query and convert cursor → list, closing cursor immediately
                queryDatabase(context.applicationContext, finalSelection, sqlRequest.args, sortOrderSnapshot)?.use { cursor ->
                    val items  = cursorToList(cursor, sortOrderSnapshot)
                    rowCount.postValue(items.size)
                    _memberList.postValue(items)
                }

                // Update UI-state LiveData for search/filter banners
                when (eventType) {
                    "SOEK_DATA"   -> textLiveData.postValue(soek)
                    "FILTER_DATA" -> textLiveData.postValue(buildFilterText())
                }

                // Signal birthday auto-scroll
                if (eventType == "LIDMAAT_DATA_VERJAAR") {
                    verjaarFlag.postValue(true)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in fetchData: ${e.message}", e)
            } finally {
                // Reset the guard inside the coroutine's finally so that the next
                // call can proceed only after this one has fully completed, not
                // merely been scheduled.
                isProcessing.set(false)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cursor → List<MemberItem> with pre-computed separators
    // -------------------------------------------------------------------------

    private fun cursorToList(cursor: Cursor?, sortOrder: String): List<MemberItem> {
        if (cursor == null || cursor.isClosed || cursor.count == 0) return emptyList()

        // Pass 1 — extract raw fields from every cursor row
        val rawItems = mutableListOf<MemberItem>()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            rawItems.add(extractMemberItem(cursor))
            cursor.moveToNext()
        }

        // Pass 2 — compute separator display info by comparing adjacent rows
        return rawItems.mapIndexed { index, item ->
            val prev = if (index == 0) null else rawItems[index - 1]
            val (showSep, showSep2) = computeSeparators(item, prev, index == 0, sortOrder)
            val (label, wykLabel)   = computeSeparatorLabels(item, showSep, showSep2, sortOrder)
            item.copy(
                showSeparator    = showSep,
                showSeparator2   = showSep2,
                separatorLabel   = label,
                separatorWykLabel = wykLabel
            )
        }
    }

    private fun extractMemberItem(cursor: Cursor): MemberItem {
        val birthday    = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEBOORTEDATUM)
        val weddingDate = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_HUWELIKSDATUM)

        var age          = "?"
        var weddingYears = "?"

        if (birthday.length >= 10) {
            try {
                val dt = parseDate(birthday.substring(0, 10))
                if (dt != null) {
                    val y = ChronoUnit.YEARS.between(dt, LocalDate.now())
                    if (y >= 0) age = y.toString()
                }
            } catch (_: Exception) {}
        }
        if (weddingDate.length >= 10) {
            try {
                val dt = parseDate(weddingDate.substring(0, 10))
                if (dt != null) {
                    val y = ChronoUnit.YEARS.between(dt, LocalDate.now())
                    if (y >= 0) weddingYears = y.toString()
                }
            } catch (_: Exception) {}
        }

        val idIdx = cursor.getColumnIndex("_id")
        val id    = if (idIdx != -1) cursor.getLong(idIdx) else 0L

        return MemberItem(
            id           = id,
            name         = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM),
            surname      = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN),
            gender       = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GESLAG),
            congregation = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEMEENTE),
            familyHead   = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GESINSHOOFGUID),
            cellphone    = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_SELFOON),
            landline     = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LANDLYN),
            email        = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_EPOS),
            ward         = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_WYK),
            address      = run {
                val a = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_STRAATADRES)
                if (a.isNotEmpty()) a else "GEEN"
            },
            birthday     = birthday,
            weddingDate  = weddingDate,
            picturePath  = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_PICTUREPATH),
            tag          = cursor.getIntOrDefault(winkerkEntry.LIDMATE_TAG, 0),
            guid         = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATGUID),
            age          = age,
            weddingYears = weddingYears,
            recordstatus = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_REKORDSTATUS)
        )
    }

    /** Mirrors the separator-decision logic from the old WinkerkCursorAdapter.handleSeparators(). */
    private fun computeSeparators(
        item: MemberItem,
        prev: MemberItem?,
        isFirst: Boolean,
        sortOrder: String
    ): Pair<Boolean, Boolean> {
        if (isFirst || prev == null) return Pair(true, true)

        var showSep  = false
        var showSep2 = false

        when (sortOrder) {
            "WYK" -> {
                if (prev.ward.isNotEmpty() && item.ward.isNotEmpty() && prev.ward != item.ward)
                    showSep = true
                if (prev.familyHead != item.familyHead)
                    showSep2 = true
            }
            "GESINNE" -> {
                if (prev.familyHead != item.familyHead) showSep = true
            }
            "VAN" -> {
                if (prev.surname.isNotEmpty() && item.surname.isNotEmpty() &&
                    prev.surname[0] != item.surname[0]) showSep = true
            }
            "ADRES" -> {
                if (prev.address != item.address) showSep = true
            }
            "VERJAAR" -> {
                if (prev.birthday.length >= 5 && item.birthday.length >= 5 &&
                    prev.birthday.substring(3, 5) != item.birthday.substring(3, 5)) showSep = true
            }
            "HUWELIK" -> {
                if (prev.weddingDate.length >= 5 && item.weddingDate.length >= 5 &&
                    prev.weddingDate.substring(3, 5) != item.weddingDate.substring(3, 5)) showSep = true
            }
            "OUDERDOM" -> {
                if (prev.age != item.age) showSep = true
            }
        }
        return Pair(showSep, showSep2)
    }

    /** Mirrors configureSeparatorDisplay() from the old adapter — builds header text strings. */
    private fun computeSeparatorLabels(
        item: MemberItem,
        showSep: Boolean,
        showSep2: Boolean,
        sortOrder: String
    ): Pair<String, String> {
        if (!showSep && !showSep2) return Pair("", "")

        return when (sortOrder) {
            "WYK" -> {
                val addr = cleanAddress(item.address)
                val label = when {
                    showSep -> "${item.ward}\n$addr"
                    else    -> addr
                }
                Pair(label, "Wyk: ${item.ward}")
            }
            "VAN" -> Pair(
                if (item.surname.isNotEmpty()) item.surname.substring(0, 1) else "",
                ""
            )
            "GESINNE" -> Pair(cleanAddress(item.address), "Wyk: ${item.ward}")
            "ADRES"   -> Pair(cleanAddress(item.address), "Wyk: ${item.ward}")
            "VERJAAR" -> Pair(
                if (item.birthday.length >= 5) getMonthFullName(item.birthday.substring(3, 5)) else "",
                ""
            )
            "HUWELIK" -> Pair(
                if (item.weddingDate.length >= 5) getMonthFullName(item.weddingDate.substring(3, 5)) else "",
                ""
            )
            "OUDERDOM" -> Pair("${item.age} jaar", "")
            else       -> Pair("", "")
        }
    }

    private fun cleanAddress(raw: String): String {
        var s = raw.replace("\r", "\n").replace("\n\n", "\n")
        while (s.endsWith("\n")) s = s.dropLast(1)
        return s
    }

    private fun getMonthFullName(month: String): String = when (month) {
        "01" -> "Januarie"; "02" -> "Februarie"; "03" -> "Maart"; "04" -> "April"
        "05" -> "Mei";      "06" -> "Junie";      "07" -> "Julie"; "08" -> "Augustus"
        "09" -> "September";"10" -> "Oktober";   "11" -> "November"; "12" -> "Desember"
        else -> ""
    }

    private fun parseDate(dateStr: String): LocalDate? = try {
        // Expected format: dd-MM-yyyy or dd/MM/yyyy
        val parts = dateStr.split("-", "/")
        if (parts.size == 3) LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        else null
    } catch (_: Exception) { null }

    // -------------------------------------------------------------------------
    // Query building (unchanged from previous ViewModel)
    // -------------------------------------------------------------------------

    private fun buildCacheKey(eventType: String): String = buildString {
        append(eventType)
        append("_status_").append(recordStatus)
        when (eventType) {
            "SOEK_DATA" -> {
                append("_soek_").append(soek)  // Add this line
                append("_").append(soek)
                append("_").append(sortOrder)
                //searchList?.filter { it.isChecked }?.forEach { append("_").append(it.columnName) }
            }
            "FILTER_DATA" -> {
                currentFilterList?.filter { it.checked }?.forEach { f ->
                    append("_").append(f.title).append("_").append(f.text1).append("_").append(f.text3)
                }
            }
        }
    }

//    private fun needsQueryRebuild(eventType: String): Boolean = when {
//        eventType != lastEventType -> true
//        recordStatus != lastRecordStatus -> true
//        eventType == "SOEK_DATA"   && soek != lastSearchTerm -> true
//        eventType == "FILTER_DATA" && !filterListsEqual(currentFilterList, lastFilterListSnapshot) -> true
//        else -> false
//    }
    private fun needsQueryRebuild(eventType: String): Boolean = when {
        eventType != lastEventType -> true
        recordStatus != lastRecordStatus -> true
        eventType == "SOEK_DATA" && soek != lastSearchTerm -> true  // This line is correct
        eventType == "FILTER_DATA" && !filterListsEqual(currentFilterList, lastFilterListSnapshot) -> true
        else -> false
    }

    private fun filterListsEqual(a: ArrayList<FilterBox>?, b: ArrayList<FilterBox>?): Boolean {
        if (a === b) return true
        if (a == null || b == null || a.size != b.size) return false
        return a.zip(b).all { (x, y) -> x.toString() == y.toString() }
    }

    private fun updateLastState(eventType: String) {
        lastEventType = eventType
        lastRecordStatus = recordStatus
        if (eventType == "SOEK_DATA") lastSearchTerm = soek
        if (eventType == "FILTER_DATA") lastFilterListSnapshot =
            currentFilterList?.let { ArrayList(it) }
    }

    private fun buildQuery(context: Context, eventType: String): SqlRequest? = when (eventType) {
        "GESINNE_DATA", "FILTER_DATA", "LIDMAAT_DATA", "LIDMAAT_DATA_WYK",
        "SOEK_DATA", "LIDMAAT_DATA_VERJAAR", "OUDERDOM_DATA", "LIDMAAT_DATA_ADRES",
        "HUWELIK_DATA" -> buildMemberQuery(context, eventType)
        else -> { Log.e(TAG, "Invalid event type: $eventType"); null }
    }

    fun clearCache() {
        queryCache.clear()
        lastEventType = ""
        lastRecordStatus = ""
        isProcessing.set(false)
        Log.d(TAG, "Cache cleared")
    }

    private fun buildMemberQuery(context: Context, eventType: String): SqlRequest {
        soekList = false
        val selectionBase = winkerkEntry.SELECTION_LIDMAAT_INFO
        val from = " Members "
        val where = StringBuilder()
        val sortOrderBuilder = StringBuilder()
        val argsList = mutableListOf<String>()

        if (recordStatus != "*") {
            where.append(" (").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                .append(col(winkerkEntry.LIDMATE_REKORDSTATUS)).append(" = '")
                .append(recordStatus).append("' )")
        } else {
            where.append(" ((").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                .append(col(winkerkEntry.LIDMATE_REKORDSTATUS)).append(" = '0' ) OR ")
                .append(" (").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                .append(col(winkerkEntry.LIDMATE_REKORDSTATUS)).append(" = '2' ))")
        }


        appendWhereClause(context, eventType, where, argsList)
        appendOrderByClause(eventType, sortOrderBuilder)

        val finalFrom: String
        val finalSelection: String
        if (eventType == "GESINNE_DATA" || eventType == "LIDMAAT_DATA_WYK" || eventType == "LIDMAAT_DATA_ADRES") {
            finalSelection = selectionBase + winkerkEntry.SELECTION_LIDMAAT_INFO_GESINSHOOF
            finalFrom = winkerkEntry.SELECTION_LIDMAAT_FROM_GESINSHOOF
        } else {
            finalSelection = selectionBase
            finalFrom = from
        }

        val sql = if (where.isEmpty()) "$finalSelection From $finalFrom ORDER BY $sortOrderBuilder;"
        else "$finalSelection From $finalFrom WHERE $where ORDER BY $sortOrderBuilder;"

        return SqlRequest(sql, argsList.toTypedArray())
    }

    private fun appendWhereClause(context: Context, eventType: String, where: StringBuilder, argsList: MutableList<String>) {
        when (eventType) {
            "HUWELIK_DATA" -> where.append(" AND ").append(winkerkEntry.SELECTION_HUWELIK_WHERE)
            "SOEK_DATA" -> {
                // Always search all relevant columns, ignoring searchList
                val allSearchColumns = listOf(
                    winkerkEntry.LIDMATE_VAN,
                    winkerkEntry.LIDMATE_NOEMNAAM,
                    winkerkEntry.LIDMATE_VOORNAME,
                    winkerkEntry.LIDMATE_WYK,
                    winkerkEntry.LIDMATE_SELFOON,
                    winkerkEntry.LIDMATE_LANDLYN,
                    winkerkEntry.LIDMATE_NOOIENSVAN,
                    winkerkEntry.LIDMATE_BEROEP,
                    winkerkEntry.LIDMATE_EPOS,
                    winkerkEntry.LIDMATE_STRAATADRES
                )
                if (soek.isNotBlank()) {
                    where.append(" AND (")
                    allSearchColumns.forEachIndexed { i, column ->
                        if (i > 0) where.append(" OR ")
                        where.append(col(column)).append(" LIKE ?")
                        argsList.add("%${soek}%")
                    }
                    where.append(" )")
                }
                soekList = true
            }
            "FILTER_DATA" -> {
                val list = currentFilterList
                if (!list.isNullOrEmpty()) {
                    val filterFields = list.filter { it.checked }
                    if (filterFields.isNotEmpty()) {
                        where.append(" AND (")
                        filterFields.forEachIndexed { i, filter ->
                            if (i > 0) where.append(") AND (")
                            val toets = filter.text3
                            when {
                                toets == "gelyk aan"     -> {
                                    where.append(col(filter.title)).append(" = ?")
                                    argsList.add(filter.text1)
                                }
                                toets == "is nie" || toets == "nie gelyk aan" -> {
                                    where.append(col(filter.title)).append(" != ?")
                                    argsList.add(filter.text1)
                                }
                                toets == "begin met"     -> {
                                    where.append(col(filter.title)).append(" LIKE ?")
                                    argsList.add("${filter.text1}%")
                                }
                                toets == "eindig met"    -> {
                                    where.append(col(filter.title)).append(" LIKE ?")
                                    argsList.add("%${filter.text1}")
                                }
                                toets == "leeg"          -> where.append(col(filter.title)).append(" IS NULL ")
                                toets == "kleiner as"    -> {
                                    where.append("((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', birthdate))) < ?")
                                    argsList.add(filter.text1)
                                }
                                toets == "groter as"     -> {
                                    where.append("((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', birthdate))) > ?")
                                    argsList.add(filter.text1)
                                }
                                toets == "tussen" && filter.title == "Ouderdom" -> {
                                    where.append(" ( ((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', birthdate))) >= ? ) AND ( ((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', birthdate))) <= ? )")
                                    argsList.add(filter.text1)
                                    argsList.add(filter.text2)
                                }
                                toets == "gelyk" && filter.title == "Ouderdom" -> {
                                    where.append("((strftime('%Y', 'now') - strftime('%Y', birthdate)) - (strftime('%m-%d', 'now') < strftime('%m-%d', birthdate))) = ?")
                                    argsList.add(filter.text1)
                                }
                                filter.title == "Geslag"        -> {
                                    where.append(col(winkerkEntry.LIDMATE_GESLAG)).append(" = ?")
                                    argsList.add(if (toets == "manlik") "Manlik" else "Vroulik")
                                }
                                filter.title == "Selfoon"       -> where.append(" ( ").append(col(winkerkEntry.LIDMATE_SELFOON)).append(" IS NOT NULL AND ").append(col(winkerkEntry.LIDMATE_SELFOON)).append(" != '' )")
                                filter.title == "E-pos"         -> where.append(" ( ").append(col(winkerkEntry.LIDMATE_EPOS)).append(" IS NOT NULL AND ").append(col(winkerkEntry.LIDMATE_EPOS)).append(" != '' )")
                                filter.title == "Landlyn"       -> where.append(" ( ").append(col(winkerkEntry.LIDMATE_LANDLYN)).append(" IS NOT NULL AND ").append(col(winkerkEntry.LIDMATE_LANDLYN)).append(" != '' )")
                                filter.title == "Huwelikstatus" -> {
                                    where.append(col(winkerkEntry.LIDMATE_HUWELIKSTATUS)).append(" = ?")
                                    argsList.add(filter.text3)
                                }
                                filter.title == "Lidmaatskap"   -> {
                                    if (filter.text3 == "Belydend") {
                                        where.append(col(winkerkEntry.LIDMATE_LIDMAATSTATUS)).append(" LIKE ?")
                                        argsList.add("Bely%")
                                    } else {
                                        where.append(col(winkerkEntry.LIDMATE_LIDMAATSTATUS)).append(" LIKE ?")
                                        argsList.add(filter.text3)
                                    }
                                }
                                filter.title == "Gesinshoof"    -> where.append(" quote(").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(") = quote(").append(col(winkerkEntry.LIDMATE_LIDMAATGUID)).append(")")
                            }
                        }
                        where.append(" )")
                    }
                }
                soekList = true
            }
        }
    }

    private fun appendOrderByClause(eventType: String, sortOrderBuilder: StringBuilder) {
        when (eventType) {
            "LIDMAAT_DATA" -> {
                sortOrder = "VAN"
                sortOrderBuilder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "GESINNE_DATA" -> {
                sortOrder = "GESINNE"
                sortOrderBuilder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "LIDMAAT_DATA_WYK" -> {
                sortOrder = "WYK"
                sortOrderBuilder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_WYK)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "LIDMAAT_DATA_ADRES" -> {
                sortOrder = "ADRES"
                sortOrderBuilder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_STRAATADRES)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "HUWELIK_DATA" -> {
                sortOrder = "HUWELIK"
                sortOrderBuilder.append(" strftime('%m', ").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_HUWELIKSDATUM)).append(") ASC,  strftime('%d', ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_HUWELIKSDATUM)).append(") ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESLAG)).append(" DESC")
            }
            "SOEK_DATA" -> {
                when (sortOrder) {
                    "GESINNE" -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ").append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                    "VAN"     -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                    "ADRES"   -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_STRAATADRES)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ").append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                    "WYK"     -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_WYK)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ").append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                    "VERJAAR" -> sortOrderBuilder.append(" strftime('%m', birthdate) ASC, strftime('%d', birthdate) ASC")
                    "OUDERDOM"-> sortOrderBuilder.append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC")
                    else      -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                }
            }
            "FILTER_DATA" -> {
                sortOrder = "Filter"
                if (sortOrderBuilder.isEmpty()) sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                else sortOrderBuilder.append(", ").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "LIDMAAT_DATA_VERJAAR" -> sortOrderBuilder.append(" strftime('%m', birthdate) ASC, strftime('%d', birthdate) ASC")
            "OUDERDOM_DATA" -> {
                sortOrder = "OUDERDOM"
                sortOrderBuilder.append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Filter banner text
    // -------------------------------------------------------------------------

    private fun buildFilterText(): String {
        if (currentFilterList.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        val filterFields = currentFilterList!!.filter { it.checked }
        if (filterFields.isNotEmpty()) {
            sb.append("FILTER: (")
            filterFields.forEachIndexed { i, f ->
                if (i > 0) sb.append(") EN (")
                val toets = f.text3
                when {
                    toets == "gelyk aan"     -> sb.append(f.title).append(" = '").append(f.text1).append("'")
                    toets == "is nie" || toets == "nie gelyk aan" -> sb.append(f.title).append(" is nie '").append(f.text1).append("'")
                    toets == "begin met"     -> sb.append(f.title).append(" begin met '").append(f.text1).append("%'")
                    toets == "eindig met"    -> sb.append(f.title).append(" eindig met '").append(f.text1).append("%'")
                    toets == "leeg"          -> sb.append(f.title).append(" is leeg")
                    toets == "kleiner as"    -> sb.append("Ouderdom is kleiner as ").append(f.text1)
                    toets == "groter as"     -> sb.append("Ouderdom is groter as ").append(f.text1)
                    toets == "tussen" && f.title == "Ouderdom" -> sb.append("Ouderdom is tussen ").append(f.text1).append(" en ").append(f.text2)
                    toets == "gelyk" && f.title == "Ouderdom"  -> sb.append("Ouderdom = ").append(f.text1)
                    f.title == "Geslag"        -> sb.append(if (toets == "manlik") "alle MANS" else "alle VROUE")
                    f.title == "Selfoon"       -> sb.append("Almal met selfoon")
                    f.title == "E-pos"         -> sb.append("Almal met epos")
                    f.title == "Landlyn"       -> sb.append("Almal met landlyn")
                    f.title == "Huwelikstatus" -> sb.append("Almal wat ").append(f.text3).append(" is")
                    f.title == "Lidmaatskap"   -> sb.append("Waar Lidmaatskapstatus ").append(f.text3).append(" is")
                    f.title == "Gesinshoof"    -> sb.append("Almal wat GESINSHOOFDE is")
                }
            }
            sb.append(")")
        }
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // DB query execution (unchanged)
    // -------------------------------------------------------------------------

    private fun queryDatabase(context: Context, query: String, args: Array<String>? = null, sortOrder: String? = null): Cursor? = try {
        context.contentResolver.query(winkerkEntry.CONTENT_URI, null, query, args, sortOrder)
    } catch (e: Exception) {
        Log.e(TAG, "Error executing query: ${e.message}", e)
        null
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCleared() {
        queryCache.clear()
        isProcessing.set(false)
        Log.d(TAG, "ViewModel cleared")
        super.onCleared()
    }
}