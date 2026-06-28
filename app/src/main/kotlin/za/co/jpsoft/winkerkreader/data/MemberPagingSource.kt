package za.co.jpsoft.winkerkreader.data

import android.content.ContentResolver
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.models.MemberItem

class MemberPagingSource(
    private val contentResolver: ContentResolver,
    private val eventType: String,
    private val recordStatus: String,
    private val soek: String,
    private val filterList: ArrayList<FilterBox>?,
    private val sortOrder: String,
    private val pageSize: Int = 50
) : PagingSource<Int, MemberItem>() {

    private val TAG = "MemberPagingSource"

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
                sortOrder = sortOrder
            ) ?: return@withContext LoadResult.Error(IllegalStateException("Invalid query"))

            val finalSql = buildPagedQuery(sqlRequest.sql, position, limit)
            Log.d(TAG, "Executing SQL: $finalSql")
            Log.d(TAG, "Args: ${sqlRequest.args.joinToString()}")

            try {
                val cursor = contentResolver.query(
                    WinkerkContract.winkerkEntry.CONTENT_URI,
                    null,
                    finalSql,
                    sqlRequest.args,
                    null
                ) ?: return@withContext LoadResult.Error(IllegalStateException("Query returned null cursor"))

                val items = mutableListOf<MemberItem>()
                cursor.use {
                    while (it.moveToNext()) {
                        items.add(MemberItem.fromCursor(it))
                    }
                }
                Log.d(TAG, "Loaded ${items.size} items")

                // Apply separators
                val itemsWithSeparators = MemberItemSeparator.applySeparators(items, sortOrder)
                Log.d(TAG, "Loaded ${itemsWithSeparators.size} items")

                // Determine next key based on whether we received fewer items than requested
                val nextKey = if (itemsWithSeparators.size < limit) null else position + itemsWithSeparators.size
                LoadResult.Page(
                    data = itemsWithSeparators,   // ✅ use the separated list
                    prevKey = if (position == 0) null else position - 1,
                    nextKey = nextKey
                )
            } catch (e: Exception) {
                Log.e(TAG, "Paging load failed", e)
                LoadResult.Error(e)
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MemberItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    private fun buildPagedQuery(baseSql: String, offset: Int, limit: Int): String {
        var sql = baseSql.trim()
        if (sql.endsWith(";")) sql = sql.dropLast(1)
        return "$sql LIMIT $limit OFFSET $offset"
    }
}