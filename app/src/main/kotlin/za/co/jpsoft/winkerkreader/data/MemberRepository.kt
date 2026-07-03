// File: data/MemberRepository.kt
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

/**
 * Repository for member data.
 * Handles query building, caching, and cursor-to-model conversion.
 */
class MemberRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.applicationContext.contentResolver
    private val queryCache = mutableMapOf<String, MemberQueryBuilder.SqlRequest>()
    private var lastEventType = ""
    private var lastRecordStatus = "0"
    private var lastSearchTerm = ""
    private var lastFilterListSnapshot: ArrayList<FilterBox>? = null

    /**
     * Load members based on the provided parameters.
     * Uses caching to avoid rebuilding the same query.
     * @return List of MemberItem
     */
    suspend fun loadMembers(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String
    ): List<MemberItem> {
        val cacheKey = buildCacheKey(eventType, recordStatus, soek, filterList, sortOrder)
        val cachedQuery = queryCache[cacheKey]

        val sqlRequest = if (cachedQuery != null && !needsQueryRebuild(eventType, recordStatus, soek, filterList)) {
            cachedQuery
        } else {
            MemberQueryBuilder.buildQuery(
                eventType = eventType,
                recordStatus = recordStatus,
                soek = soek,
                filterList = filterList,
                sortOrder = sortOrder
            )?.also {
                queryCache[cacheKey] = it
                updateLastState(eventType, recordStatus, soek, filterList)
            } ?: run {
                if (BuildConfig.DEBUG) Log.e("MemberRepository", "Failed to build query for: $eventType")
                return emptyList()
            }
        }

        // Validate SQL
        val validation = SQLiteStatementValidator.validateAndFixSQLiteStatement(sqlRequest.sql)
        if (!validation.isValid) {
            if (BuildConfig.DEBUG) Log.e("MemberRepository", "SQL validation failed: ${validation.errorMessage}")
            return emptyList()
        }
        val finalSql = validation.fixedSql ?: sqlRequest.sql

        // Execute query and convert cursor to list
        return queryAndConvert(finalSql, sqlRequest.args, sortOrder)
    }

    /** Clears the internal query cache. */
    fun clearCache() {
        queryCache.clear()
        lastEventType = ""
        lastRecordStatus = "0"
        lastSearchTerm = ""
        lastFilterListSnapshot = null
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
        sortOrder: String
    ): String = buildString {
        append(eventType)
        append("_status_").append(recordStatus)
        when (eventType) {
            "SOEK_DATA" -> {
                append("_soek_").append(soek)
                append("_").append(sortOrder)
            }
            "FILTER_DATA" -> {
                filterList?.filter { it.checked }?.forEach { f ->
                    append("_").append(f.title).append("_").append(f.text1).append("_").append(f.text3)
                }
            }
        }
    }

    private fun needsQueryRebuild(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?
    ): Boolean = when {
        eventType != lastEventType -> true
        recordStatus != lastRecordStatus -> true
        eventType == "SOEK_DATA" && soek != lastSearchTerm -> true
        eventType == "FILTER_DATA" && !filterListsEqual(filterList, lastFilterListSnapshot) -> true
        else -> false
    }

    private fun filterListsEqual(
        a: ArrayList<FilterBox>?,
        b: ArrayList<FilterBox>?
    ): Boolean {
        if (a === b) return true
        if (a == null || b == null || a.size != b.size) return false
        return a.zip(b).all { (x, y) -> x.toString() == y.toString() }
    }

    private fun updateLastState(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?
    ) {
        lastEventType = eventType
        lastRecordStatus = recordStatus
        if (eventType == "SOEK_DATA") lastSearchTerm = soek
        if (eventType == "FILTER_DATA") lastFilterListSnapshot = filterList?.let { ArrayList(it) }
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
    // Cursor conversion (moved from ViewModel)
    // -------------------------------------------------------------------------

    private fun cursorToList(cursor: Cursor, sortOrder: String): List<MemberItem> {
        if (cursor.count == 0) return emptyList()

        val rawItems = mutableListOf<MemberItem>()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            rawItems.add(extractMemberItem(cursor))
            cursor.moveToNext()
        }

        return rawItems.mapIndexed { index, item ->
            val prev = if (index == 0) null else rawItems[index - 1]
            val (showSep, showSep2) = computeSeparators(item, prev, index == 0, sortOrder)
            val (label, wykLabel) = computeSeparatorLabels(item, showSep, showSep2, sortOrder)
            item.copy(
                showSeparator = showSep,
                showSeparator2 = showSep2,
                separatorLabel = label,
                separatorWykLabel = wykLabel
            )
        }
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
            } catch (_: Exception) {}
        }
        if (weddingDate.length >= 10) {
            try {
                parseDate(weddingDate.substring(0, 10))?.let {
                    val y = ChronoUnit.YEARS.between(it, LocalDate.now())
                    if (y >= 0) weddingYears = y.toString()
                }
            } catch (_: Exception) {}
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
            address = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_STRAATADRES).takeIf { it.isNotEmpty() } ?: "GEEN",
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

    private fun computeSeparators(
        item: MemberItem,
        prev: MemberItem?,
        isFirst: Boolean,
        sortOrder: String
    ): Pair<Boolean, Boolean> {
        if (isFirst || prev == null) return Pair(true, true)

        var showSep = false
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
                val label = if (showSep) "${item.ward}\n$addr" else addr
                Pair(label, "Wyk: ${item.ward}")
            }
            "VAN" -> Pair(
                if (item.surname.isNotEmpty()) item.surname.substring(0, 1) else "",
                ""
            )
            "GESINNE" -> Pair(cleanAddress(item.address), "Wyk: ${item.ward}")
            "ADRES" -> Pair(cleanAddress(item.address), "Wyk: ${item.ward}")
            "VERJAAR" -> Pair(
                if (item.birthday.length >= 5) getMonthFullName(item.birthday.substring(3, 5)) else "",
                ""
            )
            "HUWELIK" -> Pair(
                if (item.weddingDate.length >= 5) getMonthFullName(item.weddingDate.substring(3, 5)) else "",
                ""
            )
            "OUDERDOM" -> Pair("${item.age} jaar", "")
            else -> Pair("", "")
        }
    }

    private fun cleanAddress(raw: String): String {
        var s = raw.replace("\r", "\n").replace("\n\n", "\n")
        while (s.endsWith("\n")) s = s.dropLast(1)
        return s
    }

    private fun getMonthFullName(month: String): String = when (month) {
        "01" -> "Januarie"; "02" -> "Februarie"; "03" -> "Maart"; "04" -> "April"
        "05" -> "Mei"; "06" -> "Junie"; "07" -> "Julie"; "08" -> "Augustus"
        "09" -> "September"; "10" -> "Oktober"; "11" -> "November"; "12" -> "Desember"
        else -> ""
    }

    private fun parseDate(dateStr: String): LocalDate? = try {
        val parts = dateStr.split("-", "/")
        if (parts.size == 3) LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        else null
    } catch (_: Exception) { null }

    fun countMembers(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String
    ): Int {
        // Build the COUNT query – returns (sql, args)
        val (sql, args) = MemberQueryBuilder.buildCountQuery(
            eventType, recordStatus, soek, filterList, sortOrder
        )
        val cursor = contentResolver.query(
            WinkerkContract.winkerkEntry.CONTENT_URI,
            null,
            sql,       // ✅ the SQL string
            args,      // ✅ the arguments array
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
        todayDay: String
    ): Int {
        val (sql, args) = MemberQueryBuilder.buildCountBeforeBirthdayQuery(
            eventType, recordStatus, soek, filterList, sortOrder, todayMonth, todayDay
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