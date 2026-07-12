package za.co.jpsoft.winkerkreader.data.models

import android.database.Cursor
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.utils.Utils.parseDate
import za.co.jpsoft.winkerkreader.utils.getIntOrDefault
import za.co.jpsoft.winkerkreader.utils.getStringOrEmpty
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Immutable data model representing a single row in the member list.
 */
data class MemberItem(
    val id: Long = 0L,
    val name: String = "",
    val surname: String = "",
    val gender: String = "",
    val congregation: String = "",
    val familyHead: String = "",
    val cellphone: String = "",
    val landline: String = "",
    val email: String = "",
    val ward: String = "",
    val address: String = "",
    val birthday: String = "",
    val weddingDate: String = "",
    val picturePath: String = "",
    val tag: Int = 0,
    val guid: String = "",
    val age: String = "?",
    val weddingYears: String = "?",
    // Pre-computed separator display info
    val showSeparator: Boolean = false,
    val showSeparator2: Boolean = false,
    val separatorLabel: String = "",
    val separatorWykLabel: String = "",
    val recordstatus: String = ""
) {
    companion object {
        /**
         * Safely creates a MemberItem from a database cursor.
         * Uses getStringOrEmpty / getIntOrDefault to avoid crashes.
         */
        fun fromCursor(cursor: Cursor): MemberItem {
            // Extract basic fields safely
            val birthday =
                cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)
            val weddingDate =
                cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)

            // Compute age and wedding years (same logic as before)
            var age = "?"
            var weddingYears = "?"
            if (birthday.length >= 10) {
                try {
                    val dt = parseDate(birthday.substring(0, 10))
                    if (dt != null) {
                        val y = ChronoUnit.YEARS.between(dt, LocalDate.now())
                        if (y >= 0) age = y.toString()
                    }
                } catch (_: Exception) {
                }
            }
            if (weddingDate.length >= 10) {
                try {
                    val dt = parseDate(weddingDate.substring(0, 10))
                    if (dt != null) {
                        val y = ChronoUnit.YEARS.between(dt, LocalDate.now())
                        if (y >= 0) weddingYears = y.toString()
                    }
                } catch (_: Exception) {
                }
            }

            // Get column indexes safely (or use getStringOrEmpty for each)
            // Using getStringOrEmpty is simpler and crash‑proof
            return MemberItem(
                id = cursor.getIntOrDefault("_id", 0).toLong(),
                name = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM),
                surname = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_VAN),
                gender = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_GESLAG),
                congregation = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_GEMEENTE),
                familyHead = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_GESINSHOOFGUID),
                cellphone = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_SELFOON),
                landline = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_LANDLYN),
                email = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_EPOS),
                ward = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_WYK),
                address = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_STRAATADRES)
                    .takeIf { it.isNotEmpty() } ?: "GEEN",
                birthday = birthday,
                weddingDate = weddingDate,
                picturePath = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_PICTUREPATH),
                tag = cursor.getIntOrDefault(WinkerkContract.winkerkEntry.LIDMATE_TAG, 0),
                guid = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_LIDMAATGUID),
                age = age,
                weddingYears = weddingYears,
                recordstatus = cursor.getStringOrEmpty(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)
                // showSeparator, showSeparator2, separatorLabel, separatorWykLabel are left as defaults
                // because they are computed in MemberViewModel, not from the raw cursor.
            )
        }
    }
}