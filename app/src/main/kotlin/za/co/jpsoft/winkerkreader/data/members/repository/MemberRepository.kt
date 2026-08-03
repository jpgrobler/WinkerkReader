package za.co.jpsoft.winkerkreader.data.members.repository

import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.dao.MemberDao
import za.co.jpsoft.winkerkreader.data.members.entities.MemberEntity
import za.co.jpsoft.winkerkreader.data.members.models.MemberItem
import za.co.jpsoft.winkerkreader.data.members.queries.MemberItemSeparator
import za.co.jpsoft.winkerkreader.data.members.queries.MemberQueryBuilder
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.utils.db.SQLiteStatementValidator
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Singleton
class MemberRepository @Inject constructor(
    private val memberDao: MemberDao          // Hilt provides this; no Context needed
) {

    companion object {
        private const val TAG = "MemberRepository"
    }

    // ─── LRU cache ───────────────────────────────────────────────────────────
    // Capacity 24; access-ordered so the least-recently-used entry is evicted first.
    // All access is wrapped in synchronized() for thread safety.
    private val queryCache =
        object : LinkedHashMap<String, MemberQueryBuilder.SqlRequest>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, MemberQueryBuilder.SqlRequest>
            ) = size > 24
        }

    // ─── Public API ──────────────────────────────────────────────────────────

    suspend fun loadMembers(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String,
        congregations: List<String>? = null
    ): List<MemberItem> {
        val cacheKey =
            buildCacheKey(eventType, recordStatus, soek, filterList, sortOrder, congregations)

        val sqlRequest = synchronized(queryCache) {
            queryCache[cacheKey] ?: MemberQueryBuilder.buildQuery(
                eventType = eventType,
                recordStatus = recordStatus,
                soek = soek,
                filterList = filterList,
                sortOrder = sortOrder,
                congregations = congregations
            )?.also { queryCache[cacheKey] = it }
        }

        if (sqlRequest == null) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to build query for: $eventType")
            return emptyList()
        }

        val validation = SQLiteStatementValidator.validateAndFixSQLiteStatement(sqlRequest.sql)
        if (!validation.isValid) {
            if (BuildConfig.DEBUG) Log.e(TAG, "SQL validation failed: ${validation.errorMessage}")
            return emptyList()
        }
        val finalSql = validation.fixedSql ?: sqlRequest.sql

        return queryAndConvert(finalSql, sqlRequest.args, sortOrder)
    }

    fun clearCache() {
        synchronized(queryCache) { queryCache.clear() }
        if (BuildConfig.DEBUG) Log.d(TAG, "Cache cleared")
    }

    /**
     * Maps a [MemberEntity] to a [MemberItem].
     * Public because [MemberPagingSource] calls this directly.
     */
    fun mapEntityToItem(entity: MemberEntity): MemberItem {
        val birthday = entity.geboortedatum ?: ""
        val weddingDate = entity.huwelikDate ?: ""

        val age = birthday.take(10).let { parseDate(it)?.yearsUntilNow() }?.toString() ?: "?"
        val weddingYears =
            weddingDate.take(10).let { parseDate(it)?.yearsUntilNow() }?.toString() ?: "?"

        return MemberItem(
            id = entity.id,
            name = entity.noemnaam ?: "",
            surname = entity.van ?: "",
            gender = entity.geslag ?: "",
            congregation = entity.gemeente ?: "",
            familyHead = entity.familyHeadGUID ?: "",
            cellphone = entity.selfoon ?: "",
            landline = entity.landlyn ?: "",
            email = entity.epos ?: "",
            ward = entity.wyk ?: "",
            address = entity.straatadres?.takeIf { it.isNotEmpty() } ?: "GEEN",
            birthday = birthday,
            weddingDate = weddingDate,
            picturePath = entity.fotostoorplek ?: "",
            tag = entity.tag?.toIntOrNull() ?: 0,
            guid = entity.memberGUID ?: "",
            age = age,
            weddingYears = weddingYears,
            recordstatus = entity.rekordstatus ?: ""
        )
    }

    // ─── Count methods ───────────────────────────────────────────────────────

    /**
     * Returns the total number of members matching the given parameters.
     * Runs on [Dispatchers.IO] internally; safe to call from any coroutine.
     */
    suspend fun countMembers(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String,
        congregations: List<String>? = null
    ): Int = withContext(Dispatchers.IO) {
        val (sql, args) = MemberQueryBuilder.buildCountQuery(
            eventType, recordStatus, soek, filterList, sortOrder, congregations
        )
        try {
            memberDao.countRaw(SimpleSQLiteQuery(sql, args))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Count failed", e)
            0
        }
    }

    suspend fun countMembersBeforeBirthday(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String,
        todayMonth: String,
        todayDay: String,
        congregations: List<String>? = null
    ): Int = withContext(Dispatchers.IO) {
        val (sql, args) = MemberQueryBuilder.buildCountBeforeBirthdayQuery(
            eventType, recordStatus, soek, filterList, sortOrder,
            todayMonth, todayDay, congregations
        )
        try {
            memberDao.countRaw(SimpleSQLiteQuery(sql, args))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Count birthday failed", e)
            0
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private suspend fun queryAndConvert(
        sql: String,
        args: Array<String>,
        sortOrder: String
    ): List<MemberItem> = withContext(Dispatchers.IO) {
        try {
            val entities = memberDao.getMembersRaw(SimpleSQLiteQuery(sql, args))
            val rawItems = entities.map { mapEntityToItem(it) }
            MemberItemSeparator.applySeparators(rawItems, sortOrder)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Query failed", e)
            emptyList()
        }
    }

    private fun buildCacheKey(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String,
        congregations: List<String>?
    ): String = buildString {
        append(eventType)
        append("_status_").append(recordStatus)
        if (!congregations.isNullOrEmpty()) {
            append("_cong_").append(congregations.sorted().joinToString("|"))
        }
        when (eventType) {
            "SOEK_DATA" -> {
                append("_soek_").append(soek)
                append("_").append(sortOrder)
            }

            "FILTER_DATA" -> {
                filterList?.filter { it.checked }?.forEach { f ->
                    append("_").append(f.title)
                        .append("_").append(f.text1)
                        .append("_").append(f.text3)
                }
            }
        }
        append("_sort_").append(sortOrder)
    }

    /**
     * Parses a date string in either WinKerk format (DD-MM-YYYY) or ISO format (YYYY-MM-DD).
     * WinKerk stores dates as DD-MM-YYYY; ISO is included defensively.
     * Returns null on any parse failure.
     */
    private fun parseDate(dateStr: String): LocalDate? {
        if (dateStr.length < 10) return null
        return try {
            val parts = dateStr.split("-", "/")
            if (parts.size != 3) return null
            val first = parts[0].toInt()
            if (first > 31) {
                // First part is a 4-digit year → ISO format YYYY-MM-DD
                LocalDate.of(first, parts[1].toInt(), parts[2].toInt())
            } else {
                // First part is a day → WinKerk format DD-MM-YYYY
                LocalDate.of(parts[2].toInt(), parts[1].toInt(), first)
            }
        } catch (_: Exception) {
            null
        }
    }

    // Extension to keep mapEntityToItem concise
    private fun LocalDate.yearsUntilNow(): Long? {
        val years = ChronoUnit.YEARS.between(this, LocalDate.now())
        return if (years >= 0) years else null
    }
}