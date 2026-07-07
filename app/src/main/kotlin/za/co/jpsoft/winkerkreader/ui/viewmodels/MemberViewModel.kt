package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.data.MemberPagingSource
import za.co.jpsoft.winkerkreader.data.MemberRepository
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.ui.models.MainQueryMode
import za.co.jpsoft.winkerkreader.BuildConfig

/**
 * Standardized on [AndroidViewModel] so the application [Context][android.content.Context]
 * is always available the moment this ViewModel is constructed — no manual
 * `initRepository(context)` call required (and none possible to forget).
 *
 * Instantiate via `SavedStateViewModelFactory` (or the default factory an
 * Activity/Fragment already provides), which supplies both [Application] and
 * [SavedStateHandle] automatically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MemberViewModel"
        private const val KEY_SORT_ORDER = "sortOrder"
        private const val KEY_SOEK = "soek"
        private const val KEY_RECORD_STATUS = "recordStatus"
        private const val KEY_SOEK_LIST = "soekList"
    }

    // -------------------------------------------------------------------------
    // UI State (saved in SavedStateHandle)
    // -------------------------------------------------------------------------

    var sortOrder: String
        get() = savedStateHandle[KEY_SORT_ORDER] ?: "VAN"
        set(value) {
            savedStateHandle.set(KEY_SORT_ORDER, value)
            _sortOrder.value = value
        }

    var recordStatus: String
        get() = savedStateHandle[KEY_RECORD_STATUS] ?: "0"
        set(value) {
            savedStateHandle.set(KEY_RECORD_STATUS, value)
            _recordStatus.value = value   // ✅ update the flow
        }

    var soek: String
        get() = savedStateHandle[KEY_SOEK] ?: ""
        set(value) {
            savedStateHandle.set(KEY_SOEK, value)
            _soek.value = value           // ✅ update the flow
        }

    var soekList: Boolean
        get() = savedStateHandle[KEY_SOEK_LIST] ?: false
        set(value) = savedStateHandle.set(KEY_SOEK_LIST, value)

    // -------------------------------------------------------------------------
    // LiveData for legacy UI (row count, search text, etc.)
    // -------------------------------------------------------------------------

    private val _memberList = MutableLiveData<List<MemberItem>>(emptyList())
    fun getMemberList(): LiveData<List<MemberItem>> = _memberList

    private val textLiveData = MutableLiveData<String>()
    fun getTextLiveData(): LiveData<String> = textLiveData

    private val verjaarFlag = MutableLiveData<Boolean>()
    fun getVerjaarFLag(): LiveData<Boolean> = verjaarFlag

    private val rowCount = MutableLiveData<Int>()
    fun getRowCount(): LiveData<Int> = rowCount

    private val _memberGuidsWithPendingReminders = MutableLiveData<Set<String>>(emptySet())
    val memberGuidsWithPendingReminders: LiveData<Set<String>> = _memberGuidsWithPendingReminders

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    // Built eagerly from the Application context — no lateinit, no manual
    // init call, and therefore no path to an UninitializedPropertyAccessException.
    private val repository: MemberRepository by lazy {
        MemberRepository(getApplication())
    }

    // State flows that trigger Pager recreation when query parameters change.
    // These MUST be declared before init{} below — Kotlin runs property
    // initializers and init blocks in textual/declaration order, and init{}
    // uses combine() on these flows immediately. Previously these were
    // declared further down the class body (in the Paging 3 section), so at
    // the moment init{} ran they were still null, causing a
    // NullPointerException in Flow.collect the instant MemberViewModel was
    // constructed.
    private val _sortOrder = MutableStateFlow("VAN")
    private val _soek = MutableStateFlow("")
    private val _recordStatus = MutableStateFlow("0")
    private val _filterList = MutableStateFlow<ArrayList<FilterBox>?>(null)
    private val _eventType = MutableStateFlow("LIDMAAT_DATA")

    init {
        viewModelScope.launch {
            // Collect paging parameters and update total count
            combine(
                _sortOrder,
                _soek,
                _recordStatus,
                _filterList,
                _eventType
            ) { args ->
                PagingParams(
                    sort = args[0] as String,
                    search = args[1] as String,
                    status = args[2] as String,
                    filters = args[3] as ArrayList<FilterBox>?,
                    eventType = args[4] as String
                )
            }.debounce(300) // avoid frequent DB queries
                .collect { params ->
                    val count = withContext(Dispatchers.IO) {
                        repository.countMembers(
                            eventType = params.eventType,
                            recordStatus = params.status,
                            soek = params.search,
                            filterList = params.filters,
                            sortOrder = params.sort
                        )
                    }
                    _totalCount.value = count
                }
        }
        syncPagingStateFlows()
    }

    private var pagingStateFlowsSynced = false

    private fun syncPagingStateFlows() {
        if (pagingStateFlowsSynced) return
        _sortOrder.value = sortOrder
        _soek.value = soek
        _recordStatus.value = recordStatus
        pagingStateFlowsSynced = true
    }

    // -------------------------------------------------------------------------
    // Data loading – legacy (still used for filter text and count)
    // -------------------------------------------------------------------------

    private var currentFilterList: ArrayList<FilterBox>? = null
    private var searchList: List<SearchCheckBox>? = null

    fun setSearchList(list: List<SearchCheckBox>) {
        searchList = list
    }

    fun updatePendingRemindersSet(guids: Set<String>) {
        _memberGuidsWithPendingReminders.value = guids
    }
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

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
                || _soek.value != nextSoek
                || _recordStatus.value != nextRecordStatus
                || _filterList.value != nextFilterList
                || _sortOrder.value != nextSortOrder

        _eventType.value = nextEventType
        _soek.value = nextSoek
        _recordStatus.value = nextRecordStatus
        _filterList.value = nextFilterList
        if (_sortOrder.value != nextSortOrder) {
            _sortOrder.value = nextSortOrder
        }

        // Same query → invalidate so getRefreshKey() preserves scroll position.
        // Changed query → flatMapLatest recreates the Pager automatically.
        if (!paramsChanged) {
            refresh()
        }

        viewModelScope.launch(Dispatchers.IO) {
            fetchData(request.eventType)
        }
    }

    /**
     * Fetches data from the repository on a background thread and updates LiveData.
     * This is a suspend function that runs the database query on Dispatchers.IO.
     */
    private suspend fun fetchData(eventType: String) {
        withContext(Dispatchers.IO) {
            val items = repository.loadMembers(
                eventType = eventType,
                recordStatus = recordStatus,
                soek = soek,
                filterList = currentFilterList,
                sortOrder = sortOrder
            )
            // Update LiveData on the main thread
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
                    toets == "gelyk aan" -> sb.append(f.title).append(" = '").append(f.text1).append("'")
                    toets == "is nie" || toets == "nie gelyk aan" -> sb.append(f.title).append(" is nie '").append(f.text1).append("'")
                    toets == "begin met" -> sb.append(f.title).append(" begin met '").append(f.text1).append("%'")
                    toets == "eindig met" -> sb.append(f.title).append(" eindig met '").append(f.text1).append("%'")
                    toets == "leeg" -> sb.append(f.title).append(" is leeg")
                    toets == "kleiner as" -> sb.append("Ouderdom is kleiner as ").append(f.text1)
                    toets == "groter as" -> sb.append("Ouderdom is groter as ").append(f.text1)
                    toets == "tussen" && f.title == "Ouderdom" -> sb.append("Ouderdom is tussen ").append(f.text1).append(" en ").append(f.text2)
                    toets == "gelyk" && f.title == "Ouderdom" -> sb.append("Ouderdom = ").append(f.text1)
                    f.title == "Geslag" -> sb.append(if (toets == "manlik") "alle MANS" else "alle VROUE")
                    f.title == "Selfoon" -> sb.append("Almal met selfoon")
                    f.title == "E-pos" -> sb.append("Almal met epos")
                    f.title == "Landlyn" -> sb.append("Almal met landlyn")
                    f.title == "Huwelikstatus" -> sb.append("Almal wat ").append(f.text3).append(" is")
                    f.title == "Lidmaatskap" -> sb.append("Waar Lidmaatskapstatus ").append(f.text3).append(" is")
                    f.title == "Gesinshoof" -> sb.append("Almal wat GESINSHOOFDE is")
                }
            }
            sb.append(")")
        }
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // Query mode conversion
    // -------------------------------------------------------------------------

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
    // Paging 3 support
    // -------------------------------------------------------------------------

    private val pagingConfig = PagingConfig(
        pageSize = 50,
        prefetchDistance = 500,   // was 10 – loads more pages ahead
        enablePlaceholders = false
    )

    @Volatile
    private var currentPagingSource: MemberPagingSource? = null

    /**
     * Reload the current page set without recreating the Pager.
     * [MemberPagingSource.getRefreshKey] keeps the list scroll position.
     */
    fun refresh() {
        currentPagingSource?.invalidate()
    }

    /**
     * Internal data class to hold paging parameters.
     */
    private data class PagingParams(
        val sort: String,
        val search: String,
        val status: String,
        val filters: ArrayList<FilterBox>?,
        val eventType: String
    )

    /**
     * Main paging data flow – recreates the Pager only when query parameters change.
     * Same-parameter reloads use [refresh] → [PagingSource.invalidate].
     */
    val pagingDataFlowWithRefresh: Flow<PagingData<MemberItem>> by lazy {
        combine(
            _sortOrder,
            _soek,
            _recordStatus,
            _filterList,
            _eventType
        ) { args ->
            PagingParams(
                sort = args[0] as String,
                search = args[1] as String,
                status = args[2] as String,
                filters = args[3] as ArrayList<FilterBox>?,
                eventType = args[4] as String
            )
        }.flatMapLatest { params ->
            Pager(pagingConfig) {
                MemberPagingSource(
                    contentResolver = getApplication<Application>().contentResolver,
                    eventType = params.eventType,
                    recordStatus = params.status,
                    soek = params.search,
                    filterList = params.filters,
                    sortOrder = params.sort,
                    pageSize = 50
                ).also { currentPagingSource = it }
            }.flow
        }.cachedIn(viewModelScope)
    }
    /**
     * Updates the sort order and the corresponding event type,
     * then invalidates the current PagingSource to reload with the new parameters.
     */
    fun updateSortOrder(newSort: String) {

        if (BuildConfig.DEBUG) Log.d("MemberViewModel", "updateSortOrder: newSort=$newSort")
        sortOrder = newSort

        val newEventType = when (newSort) {
            "ADRES" -> "LIDMAAT_DATA_ADRES"
            "GESINNE" -> "GESINNE_DATA"
            "WYK" -> "LIDMAAT_DATA_WYK"
            "VERJAAR" -> "LIDMAAT_DATA_VERJAAR"
            "OUDERDOM" -> "OUDERDOM_DATA"
            "HUWELIK" -> "HUWELIK_DATA"
            else -> "LIDMAAT_DATA"
        }
        if (BuildConfig.DEBUG) Log.d("MemberViewModel", "updateSortOrder: newEventType=$newEventType")
        _eventType.value = newEventType

        refresh()
    }

    /**
     * Updates the ViewModel state for a filter operation.
     * @param filters The list of active filters.
     */
    fun updateFilter(filters: ArrayList<FilterBox>) {
        if (BuildConfig.DEBUG) Log.d(TAG, "updateFilter: filters size=${filters.size}")
        _filterList.value = filters
        _eventType.value = "FILTER_DATA"
        sortOrder = "Filter"
        soekList = false
        soek = ""

        currentFilterList = filters  // store for buildFilterText()
        // ✅ Build and set the filter summary text
        textLiveData.value = buildFilterText()
    }

    /**
     * Updates the ViewModel state for a search operation.
     * @param searchTerm The search string (non‑blank).
     */
    fun updateSearch(searchTerm: String) {
        soek = searchTerm
        soekList = true
        _eventType.value = "SOEK_DATA"
        sortOrder = "SOEK_DATA"
        _filterList.value = null
        // ✅ Set the search term in the summary
        textLiveData.value = searchTerm
    }
    fun clearFilterSummary() {
        textLiveData.value = ""
    }
    /**
     * Resets search/filter state and restores a regular sort order.
     * @param sort The sort order to restore (e.g., "VAN", "GESINNE").
     */
    fun resetToSort(sort: String) {
        soek = ""
        soekList = false
        _filterList.value = null
        val newEventType = when (sort) {
            "ADRES" -> "LIDMAAT_DATA_ADRES"
            "GESINNE" -> "GESINNE_DATA"
            "WYK" -> "LIDMAAT_DATA_WYK"
            "VERJAAR" -> "LIDMAAT_DATA_VERJAAR"
            "OUDERDOM" -> "OUDERDOM_DATA"
            "HUWELIK" -> "HUWELIK_DATA"
            else -> "LIDMAAT_DATA"
        }
        _eventType.value = newEventType
        sortOrder = sort
        //refresh()
    }
    fun getEventType(): String = _eventType.value
    fun getFilterListSize(): Int = _filterList.value?.size ?: 0
    override fun onCleared() {
        clearCache()
        super.onCleared()
    }

    suspend fun getBirthdayOffset(sortOrder: String): Int {
        val today = java.time.LocalDate.now()
        val month = "%02d".format(today.monthValue)
        val day = "%02d".format(today.dayOfMonth)

        // Determine the correct event type for the given sort order
        val eventType = when (sortOrder) {
            "VERJAAR", "VERJAARSDAG" -> "LIDMAAT_DATA_VERJAAR"
            else -> "LIDMAAT_DATA" // fallback
        }

        return repository.countMembersBeforeBirthday(
            eventType = eventType,
            recordStatus = recordStatus,
            soek = soek,
            filterList = _filterList.value,
            sortOrder = sortOrder,
            todayMonth = month,
            todayDay = day
        )
    }
}