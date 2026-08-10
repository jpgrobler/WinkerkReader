package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.dao.MemberDao
import za.co.jpsoft.winkerkreader.data.members.models.MemberItem
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.members.queries.MemberPagingSource
import za.co.jpsoft.winkerkreader.data.members.repository.MemberRepository
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.ui.models.MainQueryMode

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MemberViewModel @Inject constructor(
    private val repository: MemberRepository,
    private val memberDao: MemberDao,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "MemberViewModel"
        private const val KEY_SORT_ORDER = "sortOrder"
        private const val KEY_SOEK = "soek"
        private const val KEY_RECORD_STATUS = winkerkEntry.LIDMATE_REKORDSTATUS //"recordStatus"
        private const val KEY_SOEK_LIST = "soekList"
    }

    @Volatile
    private var birthdayInitialKey: Int = 0
    // ─── SavedStateHandle‑backed StateFlows ──────────────────────────────────
    private val _congregationFilter = MutableStateFlow<Set<String>>(emptySet())

    private val sortOrderFlow = savedStateHandle.getStateFlow(KEY_SORT_ORDER, "VAN")
    private val soekFlow = savedStateHandle.getStateFlow(KEY_SOEK, "")
    private val recordStatusFlow = savedStateHandle.getStateFlow(KEY_RECORD_STATUS, "0")

    var sortOrder: String
        get() = savedStateHandle[KEY_SORT_ORDER] ?: "VAN"
        set(value) {
            savedStateHandle.set(KEY_SORT_ORDER, value)
        }

    var recordStatus: String
        get() = savedStateHandle[KEY_RECORD_STATUS] ?: "0"
        set(value) {
            savedStateHandle.set(KEY_RECORD_STATUS, value)
        }

    var soek: String
        get() = savedStateHandle[KEY_SOEK] ?: ""
        set(value) {
            savedStateHandle.set(KEY_SOEK, value)
        }

    var soekList: Boolean
        get() = savedStateHandle[KEY_SOEK_LIST] ?: false
        set(value) = savedStateHandle.set(KEY_SOEK_LIST, value)

    // ─── Legacy LiveData ──────────────────────────────────────────────────────
    private val _memberList = MutableLiveData<List<MemberItem>>(emptyList())
    fun getMemberList(): LiveData<List<MemberItem>> = _memberList

    private val textLiveData = MutableLiveData<String>()
    fun getTextLiveData(): LiveData<String> = textLiveData

    private val verjaarFlag = MutableLiveData<Boolean>()
    fun getVerjaarFlag(): LiveData<Boolean> = verjaarFlag

    private val rowCount = MutableLiveData<Int>()
    fun getRowCount(): LiveData<Int> = rowCount

    private val _memberGuidsWithPendingReminders = MutableLiveData<Set<String>>(emptySet())
    val memberGuidsWithPendingReminders: LiveData<Set<String>> = _memberGuidsWithPendingReminders

    // ─── Paging‑driving StateFlows ──────────────────────────────────────────
    private val _filterList = MutableStateFlow<ArrayList<FilterBox>?>(null)
    private val _eventType = MutableStateFlow("LIDMAAT_DATA")

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)
    fun refresh() {
        _refreshTrigger.value++
    }

    private val _scrollToPosition = MutableSharedFlow<Int>()
    val scrollToPosition: SharedFlow<Int> = _scrollToPosition.asSharedFlow()

    // ─── Init ─────────────────────────────────────────────────────────────────
    init {
        viewModelScope.launch {
            // Use list‑based combine for 6 flows
            combine(
                listOf(
                    sortOrderFlow,
                    soekFlow,
                    recordStatusFlow,
                    _filterList,
                    _eventType,
                    _congregationFilter
                )
            ) { args ->
                @Suppress("UNCHECKED_CAST")
                PagingParams(
                    sort = args[0] as String,
                    search = args[1] as String,
                    status = args[2] as String,
                    filters = args[3] as ArrayList<FilterBox>?,
                    eventType = args[4] as String,
                    congregations = args[5] as Set<String>
                )
            }.debounce(300)
                .collect { params ->
                    val count = repository.countMembers(
                            eventType = params.eventType,
                            recordStatus = params.status,
                            soek = params.search,
                            filterList = params.filters,
                            sortOrder = params.sort,
                            congregations = params.congregations.toList()
                        )
                    _totalCount.value = count
                }
        }
    }

    // ─── Legacy data loading ──────────────────────────────────────────────────
    private var currentFilterList: ArrayList<FilterBox>? = null
    private var searchList: List<SearchCheckBox>? = null

    fun setSearchList(list: List<SearchCheckBox>) {
        searchList = list
    }

    fun updatePendingRemindersSet(guids: Set<String>) {
        _memberGuidsWithPendingReminders.value = guids
    }

    @Deprecated("Use pagingDataFlow for the main list; kept for compatibility")
    fun loadData(mode: MainQueryMode) {
        val request = mode.toQueryRequest()
        if (request.eventType == "FILTER_DATA") {
            currentFilterList = request.filterList ?: arrayListOf()
        }
        val nextEventType = request.eventType
        val nextSoek = soek
        val nextRecordStatus = recordStatus
        val nextFilterList = currentFilterList
        val nextSortOrder = sortOrder

        val paramsChanged = _eventType.value != nextEventType
                || soekFlow.value != nextSoek
                || recordStatusFlow.value != nextRecordStatus
                || _filterList.value != nextFilterList
                || sortOrderFlow.value != nextSortOrder

        _eventType.value = nextEventType
        soek = nextSoek
        recordStatus = nextRecordStatus
        _filterList.value = nextFilterList
        if (sortOrderFlow.value != nextSortOrder) {
            sortOrder = nextSortOrder
        }
        if (!paramsChanged) refresh()
        viewModelScope.launch(Dispatchers.IO) { fetchData(request.eventType) }
    }

    private suspend fun fetchData(eventType: String) {
        withContext(Dispatchers.IO) {
            val items = repository.loadMembers(
                eventType = eventType,
                recordStatus = recordStatus,
                soek = soek,
                filterList = currentFilterList,
                sortOrder = sortOrder,
                congregations = _congregationFilter.value.toList()
            )
            withContext(Dispatchers.Main) {
                _memberList.value = items
                rowCount.value = items.size
                if (eventType == "SOEK_DATA") {
                    textLiveData.value = soek
                } else if (eventType == "FILTER_DATA") {
                    textLiveData.value = buildFilterText()
                }
                if (eventType == "LIDMAAT_DATA_VERJAAR") {
                    verjaarFlag.value = true
                }
            }
        }
    }

    fun clearCache() {
        repository.clearCache()
    }

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
                    toets == "gelyk aan" -> sb.append(f.title).append(" = '").append(f.text1)
                        .append("'")

                    toets == "is nie" || toets == "nie gelyk aan" -> sb.append(f.title)
                        .append(" is nie '").append(f.text1).append("'")

                    toets == "begin met" -> sb.append(f.title).append(" begin met '")
                        .append(f.text1).append("%'")

                    toets == "eindig met" -> sb.append(f.title).append(" eindig met '")
                        .append(f.text1).append("%'")
                    toets == "leeg" -> sb.append(f.title).append(" is leeg")
                    toets == "kleiner as" -> sb.append("Ouderdom is kleiner as ").append(f.text1)
                    toets == "groter as" -> sb.append("Ouderdom is groter as ").append(f.text1)
                    toets == "tussen" && f.title == "Ouderdom" -> sb.append("Ouderdom is tussen ")
                        .append(f.text1).append(" en ").append(f.text2)

                    toets == "gelyk" && f.title == "Ouderdom" -> sb.append("Ouderdom = ")
                        .append(f.text1)
                    f.title == "Geslag" -> sb.append(if (toets == "manlik") "alle MANS" else "alle VROUE")
                    f.title == "Selfoon" -> sb.append("Almal met selfoon")
                    f.title == "E-pos" -> sb.append("Almal met epos")
                    f.title == "Landlyn" -> sb.append("Almal met landlyn")
                    f.title == "Huwelikstatus" -> sb.append("Almal wat ").append(f.text3)
                        .append(" is")

                    f.title == "Lidmaatskap" -> sb.append("Waar Lidmaatskapstatus ").append(f.text3)
                        .append(" is")
                    f.title == "Gesinshoof" -> sb.append("Almal wat GESINSHOOFDE is")
                }
            }
            sb.append(")")
        }
        return sb.toString()
    }

    // ─── Query mode conversion ──────────────────────────────────────────────
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

    // ─── Paging 3 ─────────────────────────────────────────────────────────────
    private val pagingConfig =
        PagingConfig(pageSize = 50, prefetchDistance = 50, enablePlaceholders = false)

    private data class PagingParams(
        val sort: String,
        val search: String,
        val status: String,
        val filters: ArrayList<FilterBox>?,
        val eventType: String,
        val congregations: Set<String>
    )

    // ─── Paging flow — use initialKey for birthday sort ─────────────────────────
    private val pagingDataFlow = combine(
        listOf(
            sortOrderFlow, soekFlow, recordStatusFlow,
            _filterList, _eventType, _congregationFilter, _refreshTrigger
        )
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        PagingParams(
            sort = args[0] as String,
            search = args[1] as String,
            status = args[2] as String,
            filters = args[3] as ArrayList<FilterBox>?,
            eventType = args[4] as String,
            congregations = args[5] as Set<String>
        )
    }.flatMapLatest { params ->
        if (params.eventType != "LIDMAAT_DATA_VERJAAR") {
            Pager(pagingConfig, initialKey = 0) {
                memberPagingSource(params)
            }.flow
        } else {
            // Recompute on every birthday reload (sort/filter/congregation change).
            flow {
                val startKey = resolveBirthdayScrollOffset().also {
                    birthdayInitialKey = it
                    if (BuildConfig.DEBUG) Log.d("Paging", "birthday startKey = $it")
                }
                emit(startKey)
            }.flatMapLatest { startKey ->
                Pager(pagingConfig, initialKey = startKey) {
                    memberPagingSource(params)
                }.flow
            }
        }
    }.cachedIn(viewModelScope)

    private fun memberPagingSource(params: PagingParams) = MemberPagingSource(
        memberDao = memberDao,
        memberRepository = repository,
        eventType = params.eventType,
        recordStatus = params.status,
        soek = params.search,
        filterList = params.filters,
        sortOrder = params.sort,
        congregations = params.congregations.toList(),
        pageSize = 50
    )

// ─── New public method for birthday sort ────────────────────────────────────
    /**
     * Calculates today's birthday offset BEFORE triggering the sort change.
     * This guarantees [birthdayInitialKey] is set before [flatMapLatest]
     * re-creates the Pager, so the first load starts at the right position.
     */
    private var lastBirthdaySortTime = 0L
    fun switchToBirthdaySort() {
        if (System.currentTimeMillis() - lastBirthdaySortTime < 1000) return // debounce 1s
        lastBirthdaySortTime = System.currentTimeMillis()
        viewModelScope.launch {
            if (sortOrder != "VERJAAR") {
                sortOrder = "VERJAAR"
                _eventType.value = eventTypeFor("VERJAAR")
            }
            // Offset is resolved inside paging flatMapLatest; refresh recreates the pager.
            refresh()
        }
    }

    val pagingDataFlowWithRefresh = pagingDataFlow.distinctUntilChanged()

    // ─── Public API ──────────────────────────────────────────────────────────
    fun updateSortOrder(newSort: String) {
        if (BuildConfig.DEBUG) Log.d(
            "SortChange",
            "updateSortOrder called with $newSort, stack:",
            Exception()
        )
        sortOrder = newSort
        _eventType.value = eventTypeFor(newSort)
        if (newSort != "VERJAAR") {
            birthdayInitialKey = 0
            viewModelScope.launch { _scrollToPosition.emit(0) }
        }
        refresh()
    }

    fun updateFilter(filters: ArrayList<FilterBox>) {
        _filterList.value = filters
        _eventType.value = "FILTER_DATA"
        sortOrder = "Filter"
        soekList = false
        soek = ""
        currentFilterList = filters
        textLiveData.value = buildFilterText()
    }

    fun updateSearch(searchTerm: String) {
        soek = searchTerm
        soekList = true
        _eventType.value = "SOEK_DATA"
        sortOrder = "SOEK_DATA"
        _filterList.value = null
        textLiveData.value = searchTerm
    }

    fun clearFilterSummary() {
        textLiveData.value = ""
    }

    fun resetToSort(sort: String) {
        soek = ""
        soekList = false
        _filterList.value = null
        _eventType.value = eventTypeFor(sort)
        sortOrder = sort
        if (BuildConfig.DEBUG) Log.d("SortReset", sort)
        refresh()
    }

    private fun updateEventTypeFromSortOrder() {
        _eventType.value = eventTypeFor(sortOrder)
    }

    private fun eventTypeFor(sort: String): String = when (sort) {
        "ADRES" -> "LIDMAAT_DATA_ADRES"
        "GESINNE" -> "GESINNE_DATA"
        "WYK" -> "LIDMAAT_DATA_WYK"
        "VERJAAR" -> "LIDMAAT_DATA_VERJAAR"
        "OUDERDOM" -> "OUDERDOM_DATA"
        "HUWELIK" -> "HUWELIK_DATA"
        else -> "LIDMAAT_DATA"
    }

    fun setCongregationFilter(congregations: Set<String>) {
        _congregationFilter.value = congregations
        if (soekList && soek.isNotEmpty()) {
            refresh()
            return
        }
        if (_filterList.value != null && _filterList.value!!.any { it.checked }) {
            refresh()
            return
        }
        _filterList.value = null
        currentFilterList = null
        if (BuildConfig.DEBUG) Log.d("SortChange", "Set CongregatinFilter")
        updateEventTypeFromSortOrder()
        refresh()
    }

    fun getEventType(): String = _eventType.value
    fun getFilterListSize(): Int = _filterList.value?.size ?: 0
    fun getCurrentFilterList(): ArrayList<FilterBox>? = _filterList.value

    fun clearFilters() {
        _filterList.value = null
        currentFilterList = null
        _eventType.value = "LIDMAAT_DATA"
        refresh()
    }

    override fun onCleared() {
        clearCache()
        super.onCleared()
    }

    fun refreshBirthdaySort() {
        refresh()
    }

    /**
     * SQL offset for the first member whose birthday is today, or the next
     * upcoming one. Wraps to January when no birthdays remain this calendar year.
     */
    suspend fun resolveBirthdayScrollOffset(): Int {
        val offset = getBirthdayOffset("VERJAAR")
        val total = repository.countMembers(
            eventType = "LIDMAAT_DATA_VERJAAR",
            recordStatus = recordStatus,
            soek = soek,
            filterList = _filterList.value,
            sortOrder = "VERJAAR",
            congregations = _congregationFilter.value.toList()
        )
        if (BuildConfig.DEBUG) Log.d("BirthdaySort", "offset = $offset, total = $total")
        return if (total > 0 && offset >= total) 0 else offset
    }

    suspend fun getBirthdayOffset(sortOrder: String): Int {
        val today = java.time.LocalDate.now()
        val month = "%02d".format(today.monthValue)
        val day = "%02d".format(today.dayOfMonth)
        val eventType = when (sortOrder) {
            "VERJAAR", "VERJAARSDAG" -> "LIDMAAT_DATA_VERJAAR"
            else -> "LIDMAAT_DATA"
        }
        return repository.countMembersBeforeBirthday(
            eventType = eventType,
            recordStatus = recordStatus,
            soek = soek,
            filterList = _filterList.value,
            sortOrder = sortOrder,
            todayMonth = month,
            todayDay = day,
            congregations = _congregationFilter.value.toList()
        )
    }
}