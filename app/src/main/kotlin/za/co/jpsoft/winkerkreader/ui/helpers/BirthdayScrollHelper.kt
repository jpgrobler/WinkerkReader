package za.co.jpsoft.winkerkreader.ui.helpers

import android.content.Context
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.models.MemberItem
import za.co.jpsoft.winkerkreader.data.members.setup.WinkerkDatabase
import za.co.jpsoft.winkerkreader.ui.helpers.BirthdayScrollHelper.findNextBirthdayPosition
import java.time.LocalDate
import java.util.concurrent.ExecutorService

/**
 * Utility object for auto-scrolling the member list to the next upcoming birthday.
 *
 * Extracted from [MainActivity] to reduce its size. Logic is identical to the
 * original implementation — the background executor runs [findNextBirthdayPosition]
 * off the main thread, then posts the scroll back onto the RecyclerView's own queue.
 */
object BirthdayScrollHelper {

    private const val TAG = "BirthdayScrollHelper"

    /**
     * Scrolls [recyclerView] to the next upcoming birthday in [items].
     *
     * The position search runs on [executor] (off the main thread). The scroll
     * itself is posted back to the RecyclerView's handler so it executes safely
     * after any pending layout passes.
     *
     * @param recyclerView the list to scroll
     * @param items        the full member list already submitted to the adapter
     * @param executor     a background executor (e.g. the Activity's single-thread pool)
     */
    fun scrollToNextBirthday(
        recyclerView: RecyclerView,
        items: List<MemberItem>,
        executor: ExecutorService
    ) {
        executor.execute {
            try {
                val today = LocalDate.now()
                val currentMonth = today.monthValue.toString().padStart(2, '0')
                val currentDay = today.dayOfMonth.toString().padStart(2, '0')
                val targetPosition = findNextBirthdayPosition(items, currentMonth, currentDay)
                if (targetPosition != -1) {
                    // Post onto the RecyclerView's own handler — avoids the need
                    // for a direct Activity.runOnUiThread reference.
                    recyclerView.post { recyclerView.scrollToPosition(targetPosition) }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error scrolling to next birthday", e)
            }
        }
    }

    /**
     * Finds the position of the first birthday >= today's month-day, wrapping
     * around to the first birthday of the year if none found after today.
     *
     * Birthday format assumed: dd-MM-yyyy (index 0-1 = day, index 3-4 = month).
     *
     * @return the 0-based list position, or -1 if [items] is empty / has no valid dates.
     */
    fun findNextBirthdayPosition(
        items: List<MemberItem>,
        todayMonth: String,
        todayDay: String
    ): Int {
        val todayMD = todayMonth.toInt() * 100 + todayDay.toInt()
        var firstCandidatePos = -1
        var firstCandidateMD = Int.MAX_VALUE

        for ((pos, item) in items.withIndex()) {
            val birthday = item.birthday
            if (birthday.length >= 10) {
                try {
                    val month = birthday.substring(3, 5).trim()
                    val day = birthday.substring(0, 2).trim()
                    val monthDay = month.toInt() * 100 + day.toInt()
                    // First entry on or after today wins immediately
                    if (monthDay >= todayMD) return pos
                    // Track earliest entry of the year for year-wrap fallback
                    if (monthDay < firstCandidateMD) {
                        firstCandidateMD = monthDay
                        firstCandidatePos = pos
                    }
                } catch (_: NumberFormatException) { /* skip malformed dates */
                }
            }
        }
        return firstCandidatePos
    }

    /**
     * Returns the 0-based index (offset) of the first member whose birthday
     * is on or after today, when the list is sorted by month/day.
     */
    fun getNextBirthdayOffset(context: Context, todayMonth: String, todayDay: String): Int {
        val query = """
        SELECT COUNT(*) FROM Members
        WHERE Rekordstatus = '0'
          AND (
              (CAST(substr(Geboortedatum, 4, 2) AS INTEGER) < $todayMonth)
              OR (CAST(substr(Geboortedatum, 4, 2) AS INTEGER) = $todayMonth
                  AND CAST(substr(Geboortedatum, 1, 2) AS INTEGER) < $todayDay)
          )
    """
        val db = WinkerkDatabase.getInstance(context).openHelper.readableDatabase
        val cursor = db.query(query, emptyArray())
        return try {
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } finally {
            cursor.close()
        }
    }
}
