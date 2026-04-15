package za.co.jpsoft.winkerkreader.utils

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object Utils {

    @JvmStatic
    fun fixphonenumber(number: String?): String? {
        var result = number
        if (result != null && result.isNotEmpty()) {
            result = result.replace("[^0-9+]".toRegex(), "").replace("\\s+".toRegex(), "")
            if (result.isNotEmpty()) {
                result = when {
                    result.startsWith("0") -> "+27" + result.substring(1)
                    !result.startsWith("+") -> {
                        if (result.startsWith("27")) {
                            "+$result"
                        } else {
                            "+27$result"
                        }
                    }
                    else -> result
                }
            } else {
                result = " "
            }
        }
        return result
    }

    @JvmStatic
    fun parseDate(date: String): DateTime? {
        val patterns = listOf("dd/MM/yyyy", "dd.MM.yyyy", "dd MM yyy", "dd-MM-yyyy")
        for (pattern in patterns) {
            try {
                return DateTime.parse(date, DateTimeFormat.forPattern(pattern))
            } catch (_: Exception) {
                // continue
            }
        }
        return null
    }
}