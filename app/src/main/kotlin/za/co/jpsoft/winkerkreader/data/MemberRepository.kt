package za.co.jpsoft.winkerkreader.data

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.utils.SQLiteStatementValidator
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MemberRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.applicationContext.contentResolver
    private val memberDao =
        za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase.getInstance(context).memberDao()
    private val queryCache = mutableMapOf<String, MemberQueryBuilder.SqlRequest>()

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

        val sqlRequest = queryCache[cacheKey] ?: run {
            val request = MemberQueryBuilder.buildQuery(
                eventType = eventType,
                recordStatus = recordStatus,
                soek = soek,
                filterList = filterList,
                sortOrder = sortOrder,
                congregations = congregations
            ) ?: run {
                if (BuildConfig.DEBUG) Log.e(
                    "MemberRepository",
                    "Failed to build query for: $eventType"
                )
                return emptyList()
            }
            queryCache[cacheKey] = request
            request
        }

        val validation = SQLiteStatementValidator.validateAndFixSQLiteStatement(sqlRequest.sql)
        if (!validation.isValid) {
            if (BuildConfig.DEBUG) Log.e(
                "MemberRepository",
                "SQL validation failed: ${validation.errorMessage}"
            )
            return emptyList()
        }
        val finalSql = validation.fixedSql ?: sqlRequest.sql

        return queryAndConvert(finalSql, sqlRequest.args, sortOrder)
    }

    fun clearCache() {
        queryCache.clear()
        if (BuildConfig.DEBUG) Log.d("MemberRepository", "Cache cleared")
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

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
                    append("_").append(f.title).append("_").append(f.text1).append("_")
                        .append(f.text3)
                }
            }
        }
        append("_sort_").append(sortOrder)
    }

    private suspend fun queryAndConvert(
        sql: String,
        args: Array<String>,
        sortOrder: String
    ): List<MemberItem> {
        return withContext(Dispatchers.IO) {
            try {
                val query = androidx.sqlite.db.SimpleSQLiteQuery(sql, args)
                val entities = memberDao.getMembersRaw(query)
                val rawItems = entities.map { mapEntityToItem(it) }
                MemberItemSeparator.applySeparators(rawItems, sortOrder)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("MemberRepository", "Query failed", e)
                emptyList()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Entity mapping - uses MemberItemSeparator for separator logic
    // -------------------------------------------------------------------------

    fun mapEntityToItem(entity: za.co.jpsoft.winkerkreader.data.room.MemberEntity): MemberItem {
        val birthday = entity.geboortedatum ?: ""
        val weddingDate = entity.huwelikDate ?: ""

        var age = "?"
        var weddingYears = "?"

        if (birthday.length >= 10) {
            try {
                parseDate(birthday.substring(0, 10))?.let {
                    val y = ChronoUnit.YEARS.between(it, LocalDate.now())
                    if (y >= 0) age = y.toString()
                }
            } catch (_: Exception) {
            }
        }
        if (weddingDate.length >= 10) {
            try {
                parseDate(weddingDate.substring(0, 10))?.let {
                    val y = ChronoUnit.YEARS.between(it, LocalDate.now())
                    if (y >= 0) weddingYears = y.toString()
                }
            } catch (_: Exception) {
            }
        }

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
            address = (entity.straatadres ?: "").takeIf { it.isNotEmpty() } ?: "GEEN",
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


    private fun parseDate(dateStr: String): LocalDate? = try {
        val parts = dateStr.split("-", "/")
        if (parts.size == 3) LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        else null
    } catch (_: Exception) {
        null
    }

    // -------------------------------------------------------------------------
    // Count methods
    // -------------------------------------------------------------------------

    fun countMembers(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String,
        congregations: List<String>? = null
    ): Int {
        val (sql, args) = MemberQueryBuilder.buildCountQuery(
            eventType, recordStatus, soek, filterList, sortOrder, congregations
        )
        return try {
            memberDao.countRaw(androidx.sqlite.db.SimpleSQLiteQuery(sql, args))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("MemberRepository", "Count failed", e)
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
    ): Int {
        val (sql, args) = MemberQueryBuilder.buildCountBeforeBirthdayQuery(
            eventType,
            recordStatus,
            soek,
            filterList,
            sortOrder,
            todayMonth,
            todayDay,
            congregations
        )
        return withContext(Dispatchers.IO) {
            try {
                memberDao.countRaw(androidx.sqlite.db.SimpleSQLiteQuery(sql, args))
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("MemberRepository", "Count birthday failed", e)
                0
            }
        }
    }
}