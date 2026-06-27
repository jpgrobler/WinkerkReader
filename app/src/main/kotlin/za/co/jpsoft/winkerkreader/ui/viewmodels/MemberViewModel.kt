package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.MemberPagingSource
import za.co.jpsoft.winkerkreader.data.MemberRepository
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox
import za.co.jpsoft.winkerkreader.ui.models.MainQueryMode

@OptIn(ExperimentalCoroutinesApi::class)
class MemberViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

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

    var soek: String
        get() = savedStateHandle[KEY_SOEK] ?: ""
        set(value) = savedStateHandle.set(KEY_SOEK, value)

    var recordStatus: String
        get() = savedStateHandle[KEY_RECORD_STATUS] ?: "0"
        set(value) = savedStateHandle.set(KEY_RECORD_STATUS, value)

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

    private lateinit var repository: MemberRepository
    private lateinit var context: Context

    /** Call once with application context. */
    fun initRepository(context: Context) {
        if (!::repository.isInitialized) {
            repository = MemberRepository(context.applicationContext)
        }
        this.context = context.applicationContext
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

    @Deprecated("Use pagingDataFlow for the main list; kept for compatibility")
    fun loadData(context: Context, mode: MainQueryMode) {
        initRepository(context)
        val request = mode.toQueryRequest()
        if (request.eventType == "FILTER_DATA") {
            currentFilterList = request.filterList ?: arrayListOf()
        }
        // Update state flows so paging source refreshes
        sortOrder = sortOrder
        _soek.value = soek
        _recordStatus.value = recordStatus
        _filterList.value = currentFilterList
        // Trigger refresh
        refresh()
        // For legacy LiveData, we can still fetch the first page or ignore
        // We'll keep the old fetchData for count and search text
        fetchData(context, request.eventType)
    }

    private fun fetchData(context: Context, eventType: String) {
        // Keep for search text and row count
        viewModelScope.launch {
            val items = repository.loadMembers(
                eventType = eventType,
                recordStatus = recordStatus,
                soek = soek,
                filterList = currentFilterList,
                sortOrder = sortOrder
            )
            _memberList.postValue(items)
            rowCount.postValue(items.size)
            if (eventType == "SOEK_DATA") {
                textLiveData.postValue(soek)
            } else if (eventType == "FILTER_DATA") {
                textLiveData.postValue(buildFilterText())
            }
            if (eventType == "LIDMAAT_DATA_VERJAAR") {
                verjaarFlag.postValue(true)
            }
        }
    }

    fun clearCache() {
        if (::repository.isInitialized) {
            repository.clearCache()
        }
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
    // Query mode conversion (unchanged)
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
        prefetchDistance = 10,
        enablePlaceholders = false
    )

    // State flows that trigger Pager recreation
    private val _sortOrder = MutableStateFlow("VAN")
    private val _soek = MutableStateFlow("")
    private val _recordStatus = MutableStateFlow("0")
    private val _filterList = MutableStateFlow<ArrayList<FilterBox>?>(null)

    private val _dummyRefresh = MutableStateFlow(0)

    /**
     * Refresh the current paging data by incrementing the dummy flow.
     */
    fun refresh() {
        _dummyRefresh.value++
    }

    /**
     * Main paging data flow – recreates the Pager whenever any parameter changes.
     */
    val pagingDataFlowWithRefresh: Flow<PagingData<MemberItem>> = combine(
        _sortOrder,
        _soek,
        _recordStatus,
        _filterList,
        _dummyRefresh
    ) { sort, search, status, filters, _ ->
        Pager(pagingConfig) {
            MemberPagingSource(
                contentResolver = context.contentResolver,
                eventType = resolveEventType(sort),
                recordStatus = status,
                soek = search,
                filterList = filters,
                sortOrder = sort,
                pageSize = 50
            )
        }.flow
    }.flatMapLatest { it }

    private fun resolveEventType(sort: String): String = when (sort) {
        "SOEK_DATA" -> "SOEK_DATA"
        "FILTER_DATA" -> "FILTER_DATA"
        "ADRES" -> "LIDMAAT_DATA_ADRES"
        "GESINNE" -> "GESINNE_DATA"
        "HUWELIK" -> "HUWELIK_DATA"
        "OUDERDOM" -> "OUDERDOM_DATA"
        "VAN" -> "LIDMAAT_DATA"
        "VERJAAR" -> "LIDMAAT_DATA_VERJAAR"
        "WYK" -> "LIDMAAT_DATA_WYK"
        else -> "LIDMAAT_DATA"
    }


    override fun onCleared() {
        clearCache()
        super.onCleared()
    }
}