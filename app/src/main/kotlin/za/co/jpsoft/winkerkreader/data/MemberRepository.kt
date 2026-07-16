package za.co.jpsoft.winkerkreader.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.utils.SQLiteStatementValidator
import za.co.jpsoft.winkerkreader.utils.getIntOrDefault
import za.co.jpsoft.winkerkreader.utils.getStringOrEmpty
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MemberRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.applicationContext.contentResolver
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
        return suspendCoroutine { continuation ->
            try {
                val cursor = contentResolver.query(
                    winkerkEntry.CONTENT_URI,
                    null,
                    sql,
                    args,
                    sortOrder
                )
                val items = cursor?.use { cursorToList(it, sortOrder) } ?: emptyList()
                continuation.resume(items)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("MemberRepository", "Query failed", e)
                continuation.resume(emptyList())
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cursor conversion - uses MemberItemSeparator for separator logic
    // -------------------------------------------------------------------------

    private fun cursorToList(cursor: Cursor, sortOrder: String): List<MemberItem> {
        if (cursor.count == 0) return emptyList()

        val rawItems = mutableListOf<MemberItem>()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            rawItems.add(extractMemberItem(cursor))
            cursor.moveToNext()
        }

        // ✅ Use MemberItemSeparator for all separator logic
        return MemberItemSeparator.applySeparators(rawItems, sortOrder)
    }

    private fun extractMemberItem(cursor: Cursor): MemberItem {
        val birthday = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEBOORTEDATUM)
        val weddingDate = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_HUWELIKSDATUM)

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

        val idIdx = cursor.getColumnIndex("_id")
        val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L

        return MemberItem(
            id = id,
            name = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM),
            surname = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN),
            gender = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GESLAG),
            congregation = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEMEENTE),
            familyHead = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GESINSHOOFGUID),
            cellphone = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_SELFOON),
            landline = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LANDLYN),
            email = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_EPOS),
            ward = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_WYK),
            address = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_STRAATADRES)
                .takeIf { it.isNotEmpty() } ?: "GEEN",
            birthday = birthday,
            weddingDate = weddingDate,
            picturePath = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_PICTUREPATH),
            tag = cursor.getIntOrDefault(winkerkEntry.LIDMATE_TAG, 0),
            guid = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATGUID),
            age = age,
            weddingYears = weddingYears,
            recordstatus = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_REKORDSTATUS)
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
        val cursor = contentResolver.query(
            WinkerkContract.winkerkEntry.CONTENT_URI,
            null,
            sql,
            args,
            null
        )
        cursor?.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
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
            val cursor = contentResolver.query(
                WinkerkContract.winkerkEntry.CONTENT_URI,
                null,
                sql,
                args,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            } ?: 0
        }
    }
}