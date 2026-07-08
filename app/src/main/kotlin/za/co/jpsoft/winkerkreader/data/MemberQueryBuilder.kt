// File: data/MemberQueryBuilder.kt
package za.co.jpsoft.winkerkreader.data

import za.co.jpsoft.winkerkreader.data.WinkerkContract.col
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.FilterBox

/**
 * Builds SQL queries for the member list.
 * All raw SQL strings are isolated here for easier maintenance and future migration.
 */
object MemberQueryBuilder {

    /**
     * Represents a SQL statement with its arguments.
     */
    data class SqlRequest(val sql: String, val args: Array<String>)

    /**
     * Build a query based on event type and current filter/sort state.
     * @param eventType   e.g. "LIDMAAT_DATA", "SOEK_DATA", "FILTER_DATA"
     * @param recordStatus "0", "2", or "*" for all
     * @param soek         search term (for SOEK_DATA)
     * @param filterList   list of active filters (for FILTER_DATA)
     * @param sortOrder    current sort order (e.g. "VAN", "GESINNE")
     * @return SqlRequest or null if eventType is invalid
     */
    fun buildQuery(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String
    ): SqlRequest? = when (eventType) {
        "GESINNE_DATA", "FILTER_DATA", "LIDMAAT_DATA", "LIDMAAT_DATA_WYK",
        "SOEK_DATA", "LIDMAAT_DATA_VERJAAR", "OUDERDOM_DATA", "LIDMAAT_DATA_ADRES",
        "HUWELIK_DATA" -> buildMemberQuery(eventType, recordStatus, soek, filterList, sortOrder)
        else -> null
    }

    private fun buildMemberQuery(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String
    ): SqlRequest {
        val selectionBase = winkerkEntry.SELECTION_LIDMAAT_INFO
        val where = StringBuilder()
        val argsList = mutableListOf<String>()
        val sortOrderBuilder = StringBuilder()

        // Always filter by record status
        if (recordStatus != "*") {
            where.append(" (").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                .append(col(winkerkEntry.LIDMATE_REKORDSTATUS)).append(" = '")
                .append(recordStatus).append("' )")
        } else {
            where.append(" ((").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                .append(col(winkerkEntry.LIDMATE_REKORDSTATUS)).append(" = '0' ) OR ")
                .append(" (").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                .append(col(winkerkEntry.LIDMATE_REKORDSTATUS)).append(" = '2' ))")
        }

        // Append WHERE clauses (search, filter)
        appendWhereClause(eventType, where, argsList, soek, filterList)

        // Append ORDER BY
        appendOrderByClause(eventType, sortOrderBuilder, sortOrder)

        // Determine FROM clause
        val finalFrom = if (eventType == "GESINNE_DATA" || eventType == "LIDMAAT_DATA_WYK" || eventType == "LIDMAAT_DATA_ADRES") {
            winkerkEntry.SELECTION_LIDMAAT_FROM_GESINSHOOF
        } else {
            " Members "
        }

        val finalSelection = selectionBase + winkerkEntry.SELECTION_LIDMAAT_INFO_GESINSHOOF

        val sql = if (where.isEmpty()) {
            "$finalSelection From $finalFrom ORDER BY $sortOrderBuilder;"
        } else {
            "$finalSelection From $finalFrom WHERE $where ORDER BY $sortOrderBuilder;"
        }

        return SqlRequest(sql, argsList.toTypedArray())
    }

    private fun appendWhereClause(
        eventType: String,
        where: StringBuilder,
        argsList: MutableList<String>,
        soek: String,
        filterList: ArrayList<FilterBox>?
    ) {
        when (eventType) {
            "HUWELIK_DATA" -> where.append(" AND ").append(winkerkEntry.SELECTION_HUWELIK_WHERE)

            "SOEK_DATA" -> {
                val allSearchColumns = listOf(
                    winkerkEntry.LIDMATE_VAN,
                    winkerkEntry.LIDMATE_NOEMNAAM,
                    winkerkEntry.LIDMATE_VOORNAME,
                    winkerkEntry.LIDMATE_WYK,
                    winkerkEntry.LIDMATE_SELFOON,
                    winkerkEntry.LIDMATE_LANDLYN,
                    winkerkEntry.LIDMATE_NOOIENSVAN,
                    winkerkEntry.LIDMATE_BEROEP,
                    winkerkEntry.LIDMATE_EPOS,
                    winkerkEntry.LIDMATE_STRAATADRES
                )
                if (soek.isNotBlank()) {
                    where.append(" AND (")
                    allSearchColumns.forEachIndexed { i, column ->
                        if (i > 0) where.append(" OR ")
                        where.append(col(column)).append(" LIKE ?")
                        argsList.add("%${soek}%")
                    }
                    where.append(" )")
                }
            }

            "FILTER_DATA" -> {
                val list = filterList
                if (!list.isNullOrEmpty()) {
                    val filterFields = list.filter { it.checked }
                    if (filterFields.isNotEmpty()) {
                        val birthdateExpr = "date(SUBSTR(" + col(winkerkEntry.LIDMATE_GEBOORTEDATUM) + ", 7, 4) || '-' || SUBSTR(" + col(winkerkEntry.LIDMATE_GEBOORTEDATUM) + ", 4, 2) || '-' || SUBSTR(" + col(winkerkEntry.LIDMATE_GEBOORTEDATUM) + ", 1, 2))"
                        where.append(" AND (")
                        filterFields.forEachIndexed { i, filter ->
                            if (i > 0) where.append(") AND (")
                            val toets = filter.text3
                            when {
                                filter.title == "Noemnaam" -> {
                                    val colNoem = col(winkerkEntry.LIDMATE_NOEMNAAM)
                                    val colNaam = col(winkerkEntry.LIDMATE_VOORNAME)
                                    when (toets) {
                                        "gelyk aan" -> {
                                            where.append("($colNoem = ? OR $colNaam = ?)")
                                            argsList.add(filter.text1)
                                            argsList.add(filter.text1)
                                        }
                                        "is nie", "nie gelyk aan" -> {
                                            where.append("($colNoem != ? AND $colNaam != ?)")
                                            argsList.add(filter.text1)
                                            argsList.add(filter.text1)
                                        }
                                        "begin met" -> {
                                            where.append("($colNoem LIKE ? OR $colNaam LIKE ?)")
                                            argsList.add("${filter.text1}%")
                                            argsList.add("${filter.text1}%")
                                        }
                                        "eindig met" -> {
                                            where.append("($colNoem LIKE ? OR $colNaam LIKE ?)")
                                            argsList.add("%${filter.text1}")
                                            argsList.add("%${filter.text1}")
                                        }
                                        "leeg" -> {
                                            where.append("($colNoem IS NULL AND $colNaam IS NULL)")
                                        }
                                    }
                                }
                                toets == "gelyk aan" -> {
                                    where.append(col(filter.title)).append(" = ?")
                                    argsList.add(filter.text1)
                                }
                                toets == "is nie" || toets == "nie gelyk aan" -> {
                                    where.append(col(filter.title)).append(" != ?")
                                    argsList.add(filter.text1)
                                }
                                toets == "begin met" -> {
                                    where.append(col(filter.title)).append(" LIKE ?")
                                    argsList.add("${filter.text1}%")
                                }
                                toets == "eindig met" -> {
                                    where.append(col(filter.title)).append(" LIKE ?")
                                    argsList.add("%${filter.text1}")
                                }
                                toets == "leeg" -> where.append(col(filter.title)).append(" IS NULL ")
                                toets == "kleiner as" -> {
                                    where.append("((strftime('%Y', 'now') - strftime('%Y', $birthdateExpr)) - (strftime('%m-%d', 'now') < strftime('%m-%d', $birthdateExpr))) < CAST(? AS INTEGER)")
                                    argsList.add(filter.text1)
                                }
                                toets == "groter as" -> {
                                    where.append("((strftime('%Y', 'now') - strftime('%Y', $birthdateExpr)) - (strftime('%m-%d', 'now') < strftime('%m-%d', $birthdateExpr))) > CAST(? AS INTEGER)")
                                    argsList.add(filter.text1)
                                }
                                toets == "tussen" && filter.title == "Ouderdom" -> {
                                    where.append(" ( ((strftime('%Y', 'now') - strftime('%Y', $birthdateExpr)) - (strftime('%m-%d', 'now') < strftime('%m-%d', $birthdateExpr))) >= CAST(? AS INTEGER) ) AND ( ((strftime('%Y', 'now') - strftime('%Y', $birthdateExpr)) - (strftime('%m-%d', 'now') < strftime('%m-%d', $birthdateExpr))) <= CAST(? AS INTEGER) )")
                                    argsList.add(filter.text1)
                                    argsList.add(filter.text2)
                                }
                                toets == "gelyk" && filter.title == "Ouderdom" -> {
                                    where.append("((strftime('%Y', 'now') - strftime('%Y', $birthdateExpr)) - (strftime('%m-%d', 'now') < strftime('%m-%d', $birthdateExpr))) = CAST(? AS INTEGER)")
                                    argsList.add(filter.text1)
                                }
                                filter.title == "Geslag" -> {
                                    where.append(col(winkerkEntry.LIDMATE_GESLAG)).append(" = ?")
                                    argsList.add(if (toets == "manlik") "Manlik" else "Vroulik")
                                }
                                filter.title == "Selfoon" -> where.append(" ( ").append(col(winkerkEntry.LIDMATE_SELFOON)).append(" IS NOT NULL AND ").append(col(winkerkEntry.LIDMATE_SELFOON)).append(" != '' )")
                                filter.title == "E-pos" -> where.append(" ( ").append(col(winkerkEntry.LIDMATE_EPOS)).append(" IS NOT NULL AND ").append(col(winkerkEntry.LIDMATE_EPOS)).append(" != '' )")
                                filter.title == "Landlyn" -> where.append(" ( ").append(col(winkerkEntry.LIDMATE_LANDLYN)).append(" IS NOT NULL AND ").append(col(winkerkEntry.LIDMATE_LANDLYN)).append(" != '' )")
                                filter.title == "Huwelikstatus" -> {
                                    where.append(col(winkerkEntry.LIDMATE_HUWELIKSTATUS)).append(" = ?")
                                    argsList.add(filter.text3)
                                }
                                filter.title == "Lidmaatskap" -> {
                                    if (filter.text3 == "Belydend") {
                                        where.append(col(winkerkEntry.LIDMATE_LIDMAATSTATUS)).append(" LIKE ?")
                                        argsList.add("Bely%")
                                    } else {
                                        where.append(col(winkerkEntry.LIDMATE_LIDMAATSTATUS)).append(" LIKE ?")
                                        argsList.add(filter.text3)
                                    }
                                }
                                filter.title == "Gesinshoof" -> where.append(" quote(").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(") = quote(").append(col(winkerkEntry.LIDMATE_LIDMAATGUID)).append(")")
                            }
                        }
                        where.append(" )")
                    }
                }
            }
        }
    }

    private fun appendOrderByClause(
        eventType: String,
        sortOrderBuilder: StringBuilder,
        sortOrder: String
    ) {
        when (eventType) {
            "LIDMAAT_DATA" -> {
                sortOrderBuilder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "GESINNE_DATA" -> {
                sortOrderBuilder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "LIDMAAT_DATA_WYK" -> {
                sortOrderBuilder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_WYK)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "LIDMAAT_DATA_ADRES" -> {
                sortOrderBuilder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_STRAATADRES)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
            "HUWELIK_DATA" -> {
                sortOrderBuilder.append(" strftime('%m', ").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_HUWELIKSDATUM)).append(") ASC,  strftime('%d', ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_HUWELIKSDATUM)).append(") ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".").append(col(winkerkEntry.LIDMATE_GESLAG)).append(" DESC")
            }
            "SOEK_DATA" -> {
                // For search, use the provided sortOrder (which may be different from eventType)
                when (sortOrder) {
                    "GESINNE" -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ").append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                    "VAN"     -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                    "ADRES"   -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_STRAATADRES)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ").append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                    "WYK"     -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_WYK)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ").append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                    "VERJAAR" -> sortOrderBuilder.append(" strftime('%m', birthdate) ASC, strftime('%d', birthdate) ASC")
                    "OUDERDOM"-> sortOrderBuilder.append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC")
                    else      -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                }
            }
            "FILTER_DATA" -> {
                // When filtering, we sort by the user's current sortOrder (passed in)
                if (sortOrderBuilder.isEmpty()) {
                    sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                } else {
                    sortOrderBuilder.append(", ").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                }
            }
            "LIDMAAT_DATA_VERJAAR" -> sortOrderBuilder.append(" strftime('%m', birthdate) ASC, strftime('%d', birthdate) ASC")
            "OUDERDOM_DATA" -> {
                sortOrderBuilder.append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC")
            }
            else -> {
                // Fallback for any other event type (should not happen)
                sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
        }
    }
    // File: data/MemberQueryBuilder.kt

    /**
     * Build a COUNT query for the current filters (no ORDER BY).
     * Returns the SQL string and arguments, similar to SqlRequest but just the SQL.
     * We'll return a pair (sql, args) for simplicity.
     */
    fun buildCountQuery(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String
    ): Pair<String, Array<String>> {
        val where = StringBuilder()
        val argsList = mutableListOf<String>()

        // Record status filter (same as in buildMemberQuery)
        if (recordStatus != "*") {
            where.append(" (").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                .append(col(winkerkEntry.LIDMATE_REKORDSTATUS)).append(" = '")
                .append(recordStatus).append("' )")
        } else {
            where.append(" ((").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                .append(col(winkerkEntry.LIDMATE_REKORDSTATUS)).append(" = '0' ) OR ")
                .append(" (").append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                .append(col(winkerkEntry.LIDMATE_REKORDSTATUS)).append(" = '2' ))")
        }

        // Append search/filter WHERE clauses (same as buildMemberQuery)
        appendWhereClause(eventType, where, argsList, soek, filterList)

        // Determine FROM clause (same as buildMemberQuery)
        val fromClause = if (eventType == "GESINNE_DATA" || eventType == "LIDMAAT_DATA_WYK" || eventType == "LIDMAAT_DATA_ADRES") {
            winkerkEntry.SELECTION_LIDMAAT_FROM_GESINSHOOF
        } else {
            " Members "
        }

        val sql = if (where.isEmpty()) {
            "SELECT COUNT(*) FROM $fromClause;"
        } else {
            "SELECT COUNT(*) FROM $fromClause WHERE $where;"
        }

        return sql to argsList.toTypedArray()
    }
    /**
     * Builds a COUNT query with an additional condition that the birthday month/day
     * is strictly before the given [todayMonth] and [todayDay].
     */
    /**
     * Builds a COUNT query with an additional condition that the birthday month/day
     * is strictly before the given [todayMonth] and [todayDay].
     */
    /**
     * Builds a COUNT query that counts members whose birthday month/day is before today.
     * The WHERE clause includes all existing filters from the event type, and adds the birthday condition.
     */
    /**
     * Builds a COUNT query that counts members whose birthday (month/day) is strictly
     * before today's month/day. This gives the offset (number of items before the
     * first birthday >= today) in the filtered result set.
     */
    fun buildCountBeforeBirthdayQuery(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String,
        todayMonth: String,
        todayDay: String
    ): Pair<String, Array<String>> {
        // Reuse the existing count query to get the base SQL and arguments
        val (baseSql, baseArgs) = buildCountQuery(eventType, recordStatus, soek, filterList, sortOrder)

        // Build the birthday-before condition
        val birthdayCondition = "((CAST(SUBSTR(Geboortedatum, 4, 2) AS INTEGER) < ?) OR (CAST(SUBSTR(Geboortedatum, 4, 2) AS INTEGER) = ? AND CAST(SUBSTR(Geboortedatum, 1, 2) AS INTEGER) < ?))"

        // Insert the condition into the WHERE clause
        val sql = insertBirthdayCondition(baseSql, birthdayCondition)

        // Append the three extra parameters (todayMonth, todayMonth, todayDay)
        val args = baseArgs + arrayOf(todayMonth, todayMonth, todayDay)
        return sql to args
    }

    /**
     * Inserts the birthday condition into the SQL query, handling cases where
     * there is no WHERE clause or an existing WHERE clause.
     */
    private fun insertBirthdayCondition(sql: String, condition: String): String {
        val whereIndex = sql.indexOf(" WHERE ", ignoreCase = true)
        return if (whereIndex == -1) {
            // No WHERE clause – add one before ORDER BY (if any)
            val orderIndex = sql.indexOf(" ORDER BY ", ignoreCase = true)
            if (orderIndex == -1) {
                // No ORDER BY – just append WHERE at the end
                val fromIndex = sql.indexOf(" FROM ", ignoreCase = true)
                val beforeFrom = sql.substring(0, fromIndex + " FROM ".length)
                val rest = sql.substring(fromIndex + " FROM ".length)
                "$beforeFrom$rest WHERE $condition"
            } else {
                val beforeOrder = sql.substring(0, orderIndex)
                val afterOrder = sql.substring(orderIndex)
                "$beforeOrder WHERE $condition $afterOrder"
            }
        } else {
            // Existing WHERE – insert after it, before ORDER BY (if any)
            val orderIndex = sql.indexOf(" ORDER BY ", ignoreCase = true)
            if (orderIndex == -1) {
                val beforeWhere = sql.substring(0, whereIndex + " WHERE ".length)
                val afterWhere = sql.substring(whereIndex + " WHERE ".length)
                "$beforeWhere$condition AND $afterWhere"
            } else {
                val beforeOrder = sql.substring(0, orderIndex)
                val afterOrder = sql.substring(orderIndex)
                "$beforeOrder AND $condition $afterOrder"
            }
        }
    }
}