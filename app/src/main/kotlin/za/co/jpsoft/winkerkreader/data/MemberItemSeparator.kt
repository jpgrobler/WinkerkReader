package za.co.jpsoft.winkerkreader.data

import za.co.jpsoft.winkerkreader.data.models.MemberItem

object MemberItemSeparator {

    fun applySeparators(items: List<MemberItem>, sortOrder: String): List<MemberItem> {
        if (items.isEmpty()) return items

        val result = mutableListOf<MemberItem>()
        items.forEachIndexed { index, item ->
            val prev = if (index == 0) null else items[index - 1]
            val (showSep, showSep2) = computeSeparators(item, prev, index == 0, sortOrder)
            val (label, wykLabel) = computeSeparatorLabels(item, showSep, showSep2, sortOrder)
            result.add(
                item.copy(
                    showSeparator = showSep,
                    showSeparator2 = showSep2,
                    separatorLabel = label,
                    separatorWykLabel = wykLabel
                )
            )
        }
        return result
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
                // Only show separator when ward changes, handling empty wards
                val prevWard = prev.ward.takeIf { it.isNotEmpty() } ?: "Onbekend"
                val currentWard = item.ward.takeIf { it.isNotEmpty() } ?: "Onbekend"
                if (prevWard != currentWard) showSep = true
                // No family head separators in WYK view
                showSep2 = false
            }

            "GESINNE" -> {
                if (prev.familyHead != item.familyHead) showSep = true
            }

            "VAN" -> {
                if (prev.surname.isNotEmpty() && item.surname.isNotEmpty() &&
                    prev.surname[0] != item.surname[0]
                ) showSep = true
            }

            "ADRES" -> {
                if (prev.address != item.address) showSep = true
            }

            "VERJAAR" -> {
                if (prev.birthday.length >= 5 && item.birthday.length >= 5 &&
                    prev.birthday.substring(3, 5) != item.birthday.substring(3, 5)
                ) showSep = true
            }

            "HUWELIK" -> {
                if (prev.weddingDate.length >= 5 && item.weddingDate.length >= 5 &&
                    prev.weddingDate.substring(3, 5) != item.weddingDate.substring(3, 5)
                ) showSep = true
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
                val displayWard = item.ward.takeIf { it.isNotEmpty() } ?: "Onbekend"
                Pair("Wyk: $displayWard", "")
            }

            "VAN" -> Pair(
                if (item.surname.isNotEmpty()) item.surname.substring(0, 1) else "",
                ""
            )

            "GESINNE" -> Pair(cleanAddress(item.address), "Wyk: ${item.ward}")
            "ADRES" -> Pair(cleanAddress(item.address), "Wyk: ${item.ward}")
            "VERJAAR" -> Pair(
                if (item.birthday.length >= 5) getMonthFullName(
                    item.birthday.substring(
                        3,
                        5
                    )
                ) else "",
                ""
            )

            "HUWELIK" -> Pair(
                if (item.weddingDate.length >= 5) getMonthFullName(
                    item.weddingDate.substring(
                        3,
                        5
                    )
                ) else "",
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
}