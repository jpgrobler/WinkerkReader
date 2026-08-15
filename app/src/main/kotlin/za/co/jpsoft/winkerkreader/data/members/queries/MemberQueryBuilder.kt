package za.co.jpsoft.winkerkreader.data.members.queries

import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.col
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.FilterBox

object MemberQueryBuilder {

    data class SqlRequest(val sql: String, val args: Array<String>)

    fun buildQuery(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String,
        congregations: List<String>? = null,
        noteGuids: Set<String>? = null,
        reminderGuids: Set<String>? = null
    ): SqlRequest? = when (eventType) {
        "GESINNE_DATA", "FILTER_DATA", "LIDMAAT_DATA", "LIDMAAT_DATA_WYK",
        "SOEK_DATA", "LIDMAAT_DATA_VERJAAR", "OUDERDOM_DATA", "LIDMAAT_DATA_ADRES",
        "HUWELIK_DATA" -> buildMemberQuery(
            eventType,
            recordStatus,
            soek,
            filterList,
            sortOrder,
            congregations,
            noteGuids,
            reminderGuids
        )
        else -> null
    }

    private fun buildMemberQuery(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String,
        congregations: List<String>?,
        noteGuids: Set<String>?,
        reminderGuids: Set<String>?
    ): SqlRequest {
        val selectionBase = winkerkEntry.SELECTION_LIDMAAT_INFO
        val where = StringBuilder()
        val argsList = mutableListOf<String>()
        val sortOrderBuilder = StringBuilder()

        // Record status
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

        // Congregation IN clause
        if (!congregations.isNullOrEmpty()) {
            where.append(" AND ")
                .append(winkerkEntry.LIDMATE_TABLE_NAME)
                .append(".")
                .append(col(winkerkEntry.LIDMATE_GEMEENTE))
                .append(" IN (")
            congregations.forEachIndexed { index, name ->
                if (index > 0) where.append(",")
                where.append("?")
                argsList.add(name)
            }
            where.append(")")
        }

        appendWhereClause(eventType, where, argsList, soek, filterList, noteGuids, reminderGuids)
        appendOrderByClause(eventType, sortOrderBuilder, sortOrder)

        val finalFrom =
            if (eventType == "GESINNE_DATA" || eventType == "LIDMAAT_DATA_WYK" || eventType == "LIDMAAT_DATA_ADRES") {
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
        filterList: ArrayList<FilterBox>?,
        noteGuids: Set<String>?,
        reminderGuids: Set<String>?
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
                        val birthdateExpr =
                            "date(SUBSTR(" + col(winkerkEntry.LIDMATE_GEBOORTEDATUM) + ", 7, 4) || '-' || SUBSTR(" + col(
                                winkerkEntry.LIDMATE_GEBOORTEDATUM
                            ) + ", 4, 2) || '-' || SUBSTR(" + col(winkerkEntry.LIDMATE_GEBOORTEDATUM) + ", 1, 2))"
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
                                toets == "leeg" -> where.append(col(filter.title))
                                    .append(" IS NULL ")

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

                                // ─── NEW: Note and Reminder filters ──────────
                                filter.title == "Note" -> {
                                    val guids = noteGuids ?: emptySet()
                                    if (guids.isNotEmpty()) {
                                        where.append(" [${winkerkEntry.LIDMATE_LIDMAATGUID}] IN (")
                                        where.append(guids.joinToString(",") { "'$it'" })
                                        where.append(") ")
                                    } else {
                                        where.append(" 1=0 ") // exclude all if no GUIDs
                                    }
                                }

                                filter.title == "Reminder" -> {
                                    val guids = reminderGuids ?: emptySet()
                                    if (guids.isNotEmpty()) {
                                        where.append(" [${winkerkEntry.LIDMATE_LIDMAATGUID}] IN (")
                                        where.append(guids.joinToString(",") { "'$it'" })
                                        where.append(") ")
                                    } else {
                                        where.append(" 1=0 ")
                                    }
                                }

                                filter.title == "Geslag" -> {
                                    where.append(col(winkerkEntry.LIDMATE_GESLAG)).append(" = ?")
                                    argsList.add(if (toets == "manlik") "Manlik" else "Vroulik")
                                }
                                filter.title == "Selfoon" -> where.append(" ( ")
                                    .append(col(winkerkEntry.LIDMATE_SELFOON))
                                    .append(" IS NOT NULL AND ")
                                    .append(col(winkerkEntry.LIDMATE_SELFOON)).append(" != '' )")

                                filter.title == "E-pos" -> where.append(" ( ")
                                    .append(col(winkerkEntry.LIDMATE_EPOS))
                                    .append(" IS NOT NULL AND ")
                                    .append(col(winkerkEntry.LIDMATE_EPOS)).append(" != '' )")

                                filter.title == "Landlyn" -> where.append(" ( ")
                                    .append(col(winkerkEntry.LIDMATE_LANDLYN))
                                    .append(" IS NOT NULL AND ")
                                    .append(col(winkerkEntry.LIDMATE_LANDLYN)).append(" != '' )")

                                filter.title == "Huwelikstatus" -> {
                                    where.append(col(winkerkEntry.LIDMATE_HUWELIKSTATUS))
                                        .append(" = ?")
                                    argsList.add(filter.text3)
                                }
                                filter.title == "Lidmaatskap" -> {
                                    if (filter.text3 == "Belydend") {
                                        where.append(col(winkerkEntry.LIDMATE_LIDMAATSTATUS))
                                            .append(" LIKE ?")
                                        argsList.add("Bely%")
                                    } else {
                                        where.append(col(winkerkEntry.LIDMATE_LIDMAATSTATUS))
                                            .append(" LIKE ?")
                                        argsList.add(filter.text3)
                                    }
                                }
                                filter.title == "Gesinshoof" -> where.append(" quote(")
                                    .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID))
                                    .append(") = quote(")
                                    .append(col(winkerkEntry.LIDMATE_LIDMAATGUID)).append(")")
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
                sortOrderBuilder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }

            "GESINNE_DATA" -> {
                sortOrderBuilder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }

            "LIDMAAT_DATA_WYK" -> {
                sortOrderBuilder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_WYK)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }

            "LIDMAAT_DATA_ADRES" -> {
                sortOrderBuilder.append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_STRAATADRES)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" DESC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_GESINSROL)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }

            "HUWELIK_DATA" -> {
                sortOrderBuilder.append(" strftime('%m', ").append(winkerkEntry.LIDMATE_TABLE_NAME)
                    .append(".").append(col(winkerkEntry.LIDMATE_HUWELIKSDATUM))
                    .append(") ASC,  strftime('%d', ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_HUWELIKSDATUM)).append(") ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                    .append(winkerkEntry.LIDMATE_TABLE_NAME).append(".")
                    .append(col(winkerkEntry.LIDMATE_GESLAG)).append(" DESC")
            }

            "SOEK_DATA" -> {
                when (sortOrder) {
                    "GESINNE" -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN))
                        .append(" ASC, ").append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID))
                        .append(" ASC, ")
                        .append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,")
                        .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")

                    "VAN" -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                        .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")

                    "ADRES" -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_STRAATADRES))
                        .append(" ASC, ").append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                        .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                        .append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,")
                        .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")

                    "WYK" -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_WYK)).append(" ASC, ")
                        .append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                        .append(col(winkerkEntry.LIDMATE_GESINSHOOFGUID)).append(" ASC, ")
                        .append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC,")
                        .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")

                    "VERJAAR" -> sortOrderBuilder.append(" strftime('%m', birthdate) ASC, strftime('%d', birthdate) ASC")
                    "OUDERDOM" -> sortOrderBuilder.append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC")
                    else -> sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                        .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                }
            }

            "FILTER_DATA" -> {
                if (sortOrderBuilder.isEmpty()) {
                    sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                        .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                } else {
                    sortOrderBuilder.append(", ").append(col(winkerkEntry.LIDMATE_VAN))
                        .append(" ASC, ").append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
                }
            }

            "LIDMAAT_DATA_VERJAAR" -> sortOrderBuilder.append(" strftime('%m', birthdate) ASC, strftime('%d', birthdate) ASC")
            "OUDERDOM_DATA" -> {
                sortOrderBuilder.append(" strftime('%Y', birthdate) DESC, strftime('%m', birthdate) DESC, strftime('%d', birthdate) DESC")
            }

            else -> {
                sortOrderBuilder.append(col(winkerkEntry.LIDMATE_VAN)).append(" ASC, ")
                    .append(col(winkerkEntry.LIDMATE_NOEMNAAM)).append(" ASC ")
            }
        }
    }

    // ========================================================================
    // COUNT QUERIES - FIXED VERSION
    // ========================================================================

    fun buildCountQuery(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String,
        congregations: List<String>? = null,
        noteGuids: Set<String>? = null,
        reminderGuids: Set<String>? = null
    ): Pair<String, Array<String>> {
        val where = StringBuilder()
        val argsList = mutableListOf<String>()

        // Record status
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

        // Congregation IN clause
        if (!congregations.isNullOrEmpty()) {
            where.append(" AND ")
                .append(winkerkEntry.LIDMATE_TABLE_NAME)
                .append(".")
                .append(col(winkerkEntry.LIDMATE_GEMEENTE))
                .append(" IN (")
            congregations.forEachIndexed { index, name ->
                if (index > 0) where.append(",")
                where.append("?")
                argsList.add(name)
            }
            where.append(")")
        }

        appendWhereClause(eventType, where, argsList, soek, filterList, noteGuids, reminderGuids)


        val fromClause =
            if (eventType == "GESINNE_DATA" || eventType == "LIDMAAT_DATA_WYK" || eventType == "LIDMAAT_DATA_ADRES") {
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
     * FIXED: Builds a count query for members with birthday before today.
     * Now properly handles the congregation filter.
     */
    fun buildCountBeforeBirthdayQuery(
        eventType: String,
        recordStatus: String,
        soek: String,
        filterList: ArrayList<FilterBox>?,
        sortOrder: String,
        todayMonth: String,
        todayDay: String,
        congregations: List<String>? = null
    ): Pair<String, Array<String>> {
        // First, build the base WHERE clause with all filters INCLUDING congregations
        val where = StringBuilder()
        val argsList = mutableListOf<String>()

        // Record status
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

        // ✅ CRITICAL FIX: Add congregation filter HERE, not in buildCountQuery
        if (!congregations.isNullOrEmpty()) {
            where.append(" AND ")
                .append(winkerkEntry.LIDMATE_TABLE_NAME)
                .append(".")
                .append(col(winkerkEntry.LIDMATE_GEMEENTE))
                .append(" IN (")
            congregations.forEachIndexed { index, name ->
                if (index > 0) where.append(",")
                where.append("?")
                argsList.add(name)
            }
            where.append(")")
        }

        // Add other filters (search, filters, etc.)
        appendWhereClause(eventType, where, argsList, soek, filterList, null, null)

        // Add the birthday condition - members with birthday BEFORE today
        val birthdayColumn =
            winkerkEntry.LIDMATE_TABLE_NAME + "." + col(winkerkEntry.LIDMATE_GEBOORTEDATUM)
        val birthdateExpr =
            "date(SUBSTR(" + col(winkerkEntry.LIDMATE_GEBOORTEDATUM) + ", 7,4) || '-' || SUBSTR(" + col(
                winkerkEntry.LIDMATE_GEBOORTEDATUM
            ) + ", 4, 2) || '-' || SUBSTR(" + col(winkerkEntry.LIDMATE_GEBOORTEDATUM) + ", 1, 2))"
        val birthdayCondition = "strftime('%m-%d', $birthdateExpr) < '${todayMonth}-${todayDay}'"

        // Add the birthday condition
        if (where.isEmpty()) {
            where.append(birthdayCondition)
        } else {
            where.append(" AND ").append(birthdayCondition)
        }

        // Add the parameters for the birthday condition
//        argsList.add(todayMonth)
//        argsList.add(todayMonth)
//        argsList.add(todayDay)

        // Build the FROM clause
        val fromClause =
            if (eventType == "GESINNE_DATA" || eventType == "LIDMAAT_DATA_WYK" || eventType == "LIDMAAT_DATA_ADRES") {
                winkerkEntry.SELECTION_LIDMAAT_FROM_GESINSHOOF
            } else {
                " Members "
            }

        val sql = "SELECT COUNT(*) FROM $fromClause WHERE $where;"

        return sql to argsList.toTypedArray()
    }
}