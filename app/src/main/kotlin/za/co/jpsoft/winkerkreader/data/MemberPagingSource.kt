package za.co.jpsoft.winkerkreader.data

import android.content.ContentResolver
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.models.MemberItem

class MemberPagingSource(
    private val contentResolver: ContentResolver,
    private val eventType: String,
    private val recordStatus: String,
    private val soek: String,
    private val filterList: ArrayList<FilterBox>?,
    private val sortOrder: String,
    private val congregations: List<String>?,  // ✅ ADD THIS
    private val pageSize: Int = 50
) : PagingSource<Int, MemberItem>() {
    private val TAG = "MemberPagingSource"

    init {
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "MemberPagingSource created with eventType=$eventType, filterList size=${filterList?.size ?: 0}, congregations=${congregations?.size ?: 0}"
        )
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MemberItem> {
        // Force all database operations onto the IO dispatcher
        return withContext(Dispatchers.IO) {
            val position = params.key ?: 0
            val limit = params.loadSize.coerceAtMost(pageSize)

            val sqlRequest = MemberQueryBuilder.buildQuery(
                eventType = eventType,
                recordStatus = recordStatus,
                soek = soek,
                filterList = filterList,
                sortOrder = sortOrder,
                congregations = congregations  // ✅ PASS congregations to query builder
            ) ?: return@withContext LoadResult.Error(IllegalStateException("Invalid query"))

            if (BuildConfig.DEBUG) Log.d(TAG, "🔍 SQL: ${sqlRequest.sql}")
            if (BuildConfig.DEBUG) Log.d(TAG, "🔍 Args: ${sqlRequest.args.joinToString()}")

            val finalSql = buildPagedQuery(sqlRequest.sql, position, limit)
            if (BuildConfig.DEBUG) Log.d(TAG, "Executing SQL: $finalSql")
            if (BuildConfig.DEBUG) Log.d(TAG, "Args: ${sqlRequest.args.joinToString()}")

            try {
                val cursor = contentResolver.query(
                    WinkerkContract.winkerkEntry.CONTENT_URI,
                    null,
                    finalSql,
                    sqlRequest.args,
                    null
                )
                    ?: return@withContext LoadResult.Error(IllegalStateException("Query returned null cursor"))

                val items = mutableListOf<MemberItem>()
                cursor.use {
                    while (it.moveToNext()) {
                        items.add(MemberItem.fromCursor(it))
                    }
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${items.size} items")

                // ✅ MOVED logging here - after items is defined
                if (BuildConfig.DEBUG && sortOrder == "WYK") {
                    val wards = items.map { it.ward }.distinct().sorted()
                    if (BuildConfig.DEBUG) Log.d(TAG, "📊 Wards found: $wards")
                    if (BuildConfig.DEBUG) Log.d(TAG, "📊 Total items: ${items.size}")
                }

                // Apply separators
                val itemsWithSeparators = MemberItemSeparator.applySeparators(items, sortOrder)
                if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${itemsWithSeparators.size} items")

                // Determine next key based on whether we received fewer items than requested
                val nextKey =
                    if (itemsWithSeparators.size < limit) null else position + itemsWithSeparators.size
                LoadResult.Page(
                    data = itemsWithSeparators,
                    prevKey = if (position == 0) null else (position - limit).coerceAtLeast(0),
                    nextKey = nextKey
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Paging load failed", e)
                LoadResult.Error(e)
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MemberItem>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        var itemsBefore = 0
        for (page in state.pages) {
            if (itemsBefore + page.data.size > anchorPosition) {
                val offsetInPage = anchorPosition - itemsBefore
                return (page.prevKey ?: 0) + offsetInPage
            }
            itemsBefore += page.data.size
        }
        return null
    }

    private fun buildPagedQuery(baseSql: String, offset: Int, limit: Int): String {
        var sql = baseSql.trim()
        if (sql.endsWith(";")) sql = sql.dropLast(1)
        return "$sql LIMIT $limit OFFSET $offset"
    }
}