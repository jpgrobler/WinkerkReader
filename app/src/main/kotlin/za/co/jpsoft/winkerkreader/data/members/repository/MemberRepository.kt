package za.co.jpsoft.winkerkreader.data.members.repository

import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.dao.ArgiefDao
import za.co.jpsoft.winkerkreader.data.members.dao.MemberDao
import za.co.jpsoft.winkerkreader.data.members.entities.MemberEntity
import za.co.jpsoft.winkerkreader.data.members.models.MemberItem
import za.co.jpsoft.winkerkreader.data.members.queries.MemberItemSeparator
import za.co.jpsoft.winkerkreader.data.members.queries.MemberQueryBuilder
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.data.pastoral.model.VandagAllesItem
import za.co.jpsoft.winkerkreader.utils.Utils.parseDate
import za.co.jpsoft.winkerkreader.utils.db.SQLiteStatementValidator
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Singleton
class MemberRepository @Inject constructor(
    private val memberDao: MemberDao,
    private val argiefDao: ArgiefDao
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

    // Extension to keep mapEntityToItem concise
    private fun LocalDate.yearsUntilNow(): Long? {
        val years = ChronoUnit.YEARS.between(this, LocalDate.now())
        return if (years >= 0) years else null
    }

    suspend fun getCelebrationsForToday(): List<VandagAllesItem.Celebration> =
        withContext(Dispatchers.IO) {
            val today = LocalDate.now()
            val targetMonth = "%02d".format(today.monthValue)
            val targetDay = "%02d".format(today.dayOfMonth)

            val celebrations = mutableListOf<VandagAllesItem.Celebration>()

            try {
                // Fetch all active members safely via Room
                val query = SimpleSQLiteQuery("SELECT * FROM Members WHERE Rekordstatus = '0'")
                val entities = memberDao.getMembersRaw(query)

                for (entity in entities) {
                    val name = entity.noemnaam ?: ""
                    val surname = entity.van ?: ""
                    val displayName = "$name $surname".trim()
                    val guid = entity.memberGUID ?: ""
                    val phone = entity.selfoon

                    // 1. Birthday Check
                    entity.geboortedatum?.let { bday ->
                        if (bday.length >= 10 && bday.substring(
                                3,
                                5
                            ) == targetMonth && bday.substring(0, 2) == targetDay
                        ) {
                            val dob = parseDate(bday.take(10))
                            val years = if (dob != null) {
                                val diff = ChronoUnit.YEARS.between(dob, today)
                                if (diff >= 0) diff else 0L
                            } else 0L

                            celebrations.add(
                                VandagAllesItem.Celebration(
                                    id = guid.ifEmpty { entity.id.toString() },
                                    name = displayName,
                                    eventType = VandagAllesItem.CelebrationType.BIRTHDAY,
                                    detailText = "Verjaar ($years jaar)",
                                    memberGuid = guid,
                                    cellphone = phone
                                )
                            )
                        }
                    }

                    // 2. Baptism Check
                    entity.doopDate?.let { dDate ->
                        if (dDate.length >= 10 && dDate.substring(
                                3,
                                5
                            ) == targetMonth && dDate.substring(0, 2) == targetDay
                        ) {
                            val parsed = parseDate(dDate.take(10))
                            val years = if (parsed != null) {
                                val diff = ChronoUnit.YEARS.between(parsed, today)
                                if (diff >= 0) diff else 0L
                            } else 0L

                            celebrations.add(
                                VandagAllesItem.Celebration(
                                    id = guid.ifEmpty { entity.id.toString() },
                                    name = displayName,
                                    eventType = VandagAllesItem.CelebrationType.BAPTISM,
                                    detailText = "Doop ($years jaar)",
                                    memberGuid = guid,
                                    cellphone = phone
                                )
                            )
                        }
                    }

                    // 3. Wedding Check
                    entity.huwelikDate?.let { hDate ->
                        if (hDate.length >= 10 && hDate.substring(
                                3,
                                5
                            ) == targetMonth && hDate.substring(0, 2) == targetDay
                        ) {
                            val parsed = parseDate(hDate.take(10))
                            val years = if (parsed != null) {
                                val diff = ChronoUnit.YEARS.between(parsed, today)
                                if (diff >= 0) diff else 0L
                            } else 0L

                            celebrations.add(
                                VandagAllesItem.Celebration(
                                    id = guid.ifEmpty { entity.id.toString() },
                                    name = displayName,
                                    eventType = VandagAllesItem.CelebrationType.WEDDING,
                                    detailText = "Huweliksdag ($years jaar)",
                                    memberGuid = guid,
                                    cellphone = phone
                                )
                            )
                        }
                    }

                    // 4. Confession Check
                    entity.belydenisafleggingDate?.let { cDate ->
                        if (cDate.length >= 10 && cDate.substring(
                                3,
                                5
                            ) == targetMonth && cDate.substring(0, 2) == targetDay
                        ) {
                            val parsed = parseDate(cDate.take(10))
                            val years = if (parsed != null) {
                                val diff = ChronoUnit.YEARS.between(parsed, today)
                                if (diff >= 0) diff else 0L
                            } else 0L

                            celebrations.add(
                                VandagAllesItem.Celebration(
                                    id = guid.ifEmpty { entity.id.toString() },
                                    name = displayName,
                                    eventType = VandagAllesItem.CelebrationType.BAPTISM,
                                    detailText = "Belydenisaflegging ($years jaar)",
                                    memberGuid = guid,
                                    cellphone = phone
                                )
                            )
                        }
                    }
                }

                // 5. Deaths from Archive
                val deathQuery = """
                SELECT _id, Name AS Noemnaam, Surname AS Van, DepartureDate 
                FROM Argief 
                WHERE Reason = 'Oorlede' AND DepartureDate IS NOT NULL AND LENGTH(DepartureDate) >= 10
            """.trimIndent()

                val deathCursor = argiefDao.queryRaw(SimpleSQLiteQuery(deathQuery))
                deathCursor.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(cursor.getColumnIndex("Noemnaam")) ?: ""
                        val surname = cursor.getString(cursor.getColumnIndex("Van")) ?: ""
                        val id = cursor.getLong(cursor.getColumnIndex("_id"))
                        val departureDate =
                            cursor.getString(cursor.getColumnIndex("DepartureDate")) ?: ""

                        if (departureDate.length >= 10 && departureDate.substring(
                                3,
                                5
                            ) == targetMonth && departureDate.substring(0, 2) == targetDay
                        ) {
                            val displayName = "$name $surname".trim()
                            val parsed = parseDate(departureDate.take(10))
                            val years = if (parsed != null) {
                                val diff = ChronoUnit.YEARS.between(parsed, today)
                                if (diff >= 0) diff else 0L
                            } else 0L

                            // Check if within 2 years of passing away
                            if (years <= 2) {
                                celebrations.add(
                                    VandagAllesItem.Celebration(
                                        id = id.toString(),
                                        name = displayName,
                                        eventType = VandagAllesItem.CelebrationType.DEATH,
                                        detailText = "Sterfgeval ($years jaar gelede)",
                                        memberGuid = "",
                                        cellphone = null
                                    )
                                )
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error fetching celebrations", e)
            }

            return@withContext celebrations.distinctBy { it.memberGuid.ifEmpty { it.id } }
        }

    // Extension function to check if a date string matches month/day
    private fun String?.matchesDate(month: String, day: String): Boolean {
        if (this.isNullOrBlank() || this.length < 10) return false
        return this.substring(3, 5) == month && this.substring(0, 2) == day
    }

    // Helper to build detail text
    private fun buildDetailText(
        entity: MemberEntity,
        eventType: VandagAllesItem.CelebrationType,
        today: LocalDate
    ): String {
        val dateStr = when (eventType) {
            VandagAllesItem.CelebrationType.BIRTHDAY -> entity.geboortedatum
            VandagAllesItem.CelebrationType.BAPTISM -> entity.doopDate
            VandagAllesItem.CelebrationType.WEDDING -> entity.huwelikDate
            else -> null
        }
        val years = dateStr?.takeIf { it.length >= 10 }?.let { d ->
            parseDate(d.take(10))?.let { parsed ->
                val diff = ChronoUnit.YEARS.between(parsed, today)
                if (diff >= 0) diff else 0
            }
        } ?: 0

        return when (eventType) {
            VandagAllesItem.CelebrationType.BIRTHDAY -> "Verjaar (${years} jaar)"
            VandagAllesItem.CelebrationType.BAPTISM -> "Doop (${years} jaar)"
            VandagAllesItem.CelebrationType.WEDDING -> "Huweliksdag (${years} jaar)"
            VandagAllesItem.CelebrationType.DEATH -> "Sterfgeval (${years} jaar gelede)"
        }
    }
}