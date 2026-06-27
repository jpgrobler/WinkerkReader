package za.co.jpsoft.winkerkreader.data

import android.content.ContentResolver
import android.database.Cursor
import androidx.paging.PagingSource
import androidx.paging.PagingState
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.utils.CursorDataExtractor
import za.co.jpsoft.winkerkreader.utils.getStringOrEmpty
import kotlin.math.ceil

class MemberPagingSource(
    private val contentResolver: ContentResolver,
    private val eventType: String,
    private val recordStatus: String,
    private val soek: String,
    private val filterList: ArrayList<FilterBox>?,
    private val sortOrder: String,
    private val pageSize: Int = 50
) : PagingSource<Int, MemberItem>() {

    private var totalCount = -1

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MemberItem> {
        val position = params.key ?: 0
        val limit = params.loadSize.coerceAtMost(pageSize)

        val sqlRequest = MemberQueryBuilder.buildQuery(
            eventType = eventType,
            recordStatus = recordStatus,
            soek = soek,
            filterList = filterList,
            sortOrder = sortOrder
        ) ?: return LoadResult.Error(IllegalStateException("Invalid query"))

        // Add LIMIT and OFFSET to the SQL
        val finalSql = buildPagedQuery(sqlRequest.sql, position, limit)

        return try {
            val cursor = contentResolver.query(
                WinkerkContract.winkerkEntry.CONTENT_URI,
                null,
                finalSql,
                sqlRequest.args,
                null
            ) ?: return LoadResult.Error(IllegalStateException("Query returned null cursor"))

            val items = mutableListOf<MemberItem>()
            cursor.use {
                if (totalCount < 0) {
                    // Get total count (optimization: separate count query if needed)
                    totalCount = getTotalCount(sqlRequest.sql, sqlRequest.args)
                }
                while (it.moveToNext()) {
                    items.add(MemberItem.fromCursor(it))
                }
            }

            val prevKey = if (position == 0) null else position - 1
            val nextKey = if (position + items.size < totalCount) position + items.size else null

            LoadResult.Page(
                data = items,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MemberItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    private fun buildPagedQuery(baseSql: String, offset: Int, limit: Int): String {
        // Remove trailing semicolon if present
        var sql = baseSql.trim()
        if (sql.endsWith(";")) sql = sql.dropLast(1)
        return "$sql LIMIT $limit OFFSET $offset"
    }

    private fun getTotalCount(sql: String, args: Array<String>): Int {
        // Remove ORDER BY and LIMIT clauses to count all rows
        var countSql = sql.replace(Regex("ORDER BY.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("LIMIT.*$", RegexOption.IGNORE_CASE), "")
            .trim()
        if (countSql.endsWith(";")) countSql = countSql.dropLast(1)
        // Wrap in SELECT COUNT(*)
        countSql = "SELECT COUNT(*) FROM ($countSql)"
        val cursor = contentResolver.query(
            WinkerkContract.winkerkEntry.CONTENT_URI,
            null,
            countSql,
            args,
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        } ?: 0
    }
}