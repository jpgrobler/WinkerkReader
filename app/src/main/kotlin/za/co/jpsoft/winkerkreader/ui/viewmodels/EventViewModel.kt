package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.content.Context
import android.database.Cursor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.col
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import java.time.LocalDate
import android.util.Log

class EventViewModel : ViewModel() {

    private val _eventList = MutableLiveData<List<MemberItem>>(emptyList())
    val eventList: LiveData<List<MemberItem>> = _eventList

    /**
     * Load members whose event (birthday, baptism, wedding, confession) falls on today's date.
     * The cursor is opened, converted to [MemberItem]s, and closed immediately.
     */
    fun loadEventData(context: Context, eventType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            val currentMonth = "%02d".format(today.monthValue)
            val currentDay = "%02d".format(today.dayOfMonth)

            val selection = when (eventType) {
                "Verjaar" -> buildBirthdaySelection(currentMonth, currentDay)
                "Doop"    -> buildBaptismSelection(currentMonth, currentDay)
                "Huwelik" -> buildWeddingSelection(currentMonth, currentDay)
                "Bely"    -> buildConfessionSelection(currentMonth, currentDay)
                else -> {
                    Log.e("EventViewModel", "Invalid event type: $eventType")
                    null
                }
            }

            val members = if (selection != null) {
                queryDatabase(context, selection)?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            try {
                                add(MemberItem.fromCursor(cursor))
                            } catch (e: Exception) {
                                Log.e("EventViewModel", "Failed to convert row", e)
                            }
                        }
                    }
                } ?: emptyList()
            } else {
                emptyList()
            }
            Log.d("EventViewModel", "Built ${members.size} members")
            members.take(3).forEach {
                Log.d("EventViewModel", "Sample: ${it.name} ${it.surname}, id=${it.id}")
            }
            _eventList.postValue(members)
        }
    }

    // ------------------------------------------------------------------------
    // Private SQL builders (identical to original EventViewModel)
    // ------------------------------------------------------------------------

    private fun buildBirthdaySelection(month: String, day: String) = """
        SELECT Members._rowid_ as _id, * FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
        WHERE (${col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)} = "0")
          AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)},4,2) = "$month")
          AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)},1,2) = "$day")
        ORDER BY substr(${col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)},4,2) ASC,
                 substr(${col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)},1,2) ASC,
                 ${col(WinkerkContract.winkerkEntry.LIDMATE_VAN)} ASC,
                 ${col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)} ASC
    """.trimIndent()

    private fun buildBaptismSelection(month: String, day: String) = """
        SELECT Members._rowid_ as _id, * FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
        WHERE (${col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)} = "0")
          AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM)},4,2) = "$month")
          AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM)},1,2) = "$day")
        ORDER BY substr(${col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM)},4,2) ASC,
                 substr(${col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM)},1,2) ASC,
                 ${col(WinkerkContract.winkerkEntry.LIDMATE_VAN)} ASC,
                 ${col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)} ASC
    """.trimIndent()

    private fun buildWeddingSelection(month: String, day: String) = """
        SELECT Members._rowid_ as _id, * FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
        WHERE (${col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)} = "0")
          AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)},4,2) = "$month")
          AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)},1,2) = "$day")
        ORDER BY substr(${col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)},4,2) ASC,
                 substr(${col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)},1,2) ASC,
                 ${col(WinkerkContract.winkerkEntry.LIDMATE_VAN)} ASC,
                 ${col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)} ASC
    """.trimIndent()

    private fun buildConfessionSelection(month: String, day: String) = """
        SELECT Members._rowid_ as _id, * FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
        WHERE (${col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)} = "0")
          AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM)},4,2) = "$month")
          AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM)},1,2) = "$day")
        ORDER BY substr(${col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM)},4,2) ASC,
                 substr(${col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM)},1,2) ASC,
                 ${col(WinkerkContract.winkerkEntry.LIDMATE_VAN)} ASC,
                 ${col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)} ASC
    """.trimIndent()

    private fun queryDatabase(context: Context, query: String): Cursor? {
        return try {
            context.contentResolver.query(
                WinkerkContract.winkerkEntry.CONTENT_URI,
                null,
                query,
                null,
                null
            ).also { cursor ->
                Log.d("EventViewModel", "Query returned ${cursor?.count ?: 0} rows")}
        } catch (e: Exception) {
            Log.e("EventViewModel", "Query failed: ${e.message}\nQuery: $query")
            null
        }
    }
}
//package za.co.jpsoft.winkerkreader.ui.viewmodels
//
//
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.MutableLiveData
//import androidx.lifecycle.ViewModel
//import android.content.Context
//import android.database.Cursor
//import android.util.Log
//import java.time.LocalDate
//import za.co.jpsoft.winkerkreader.data.WinkerkContract
//import za.co.jpsoft.winkerkreader.data.WinkerkContract.col
//
//class EventViewModel : ViewModel() {
//
//    private val birthdayData = MutableLiveData<Cursor?>()
//    private val baptismData = MutableLiveData<Cursor?>()
//    private val weddingData = MutableLiveData<Cursor?>()
//    private val confessionData = MutableLiveData<Cursor?>()
//
//    fun getBirthdayData(context: Context): LiveData<Cursor?> {
//        fetchData(context, "Verjaar")
//        return birthdayData
//    }
//
//    fun getBaptismData(context: Context): LiveData<Cursor?> {
//        fetchData(context, "Doop")
//        return baptismData
//    }
//
//    fun getWeddingData(context: Context): LiveData<Cursor?> {
//        fetchData(context, "Huwelik")
//        return weddingData
//    }
//
//    fun getConfessionData(context: Context): LiveData<Cursor?> {
//        fetchData(context, "Bely")
//        return confessionData
//    }
//
//    private fun fetchData(context: Context, eventType: String) {
//        val today = LocalDate.now()
//        val currentMonth = "%02d".format(today.monthValue)
//        val currentDay = "%02d".format(today.dayOfMonth)
//
//        val selection = when (eventType) {
//            "Verjaar" -> {
//                """
//                SELECT Members._rowid_ as _id, * FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
//                WHERE (${col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)} = "0")
//                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)},4,2) = "$currentMonth")
//                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)},1,2) = "$currentDay")
//                ORDER BY substr(${col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)},4,2) ASC,
//                         substr(${col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)},1,2) ASC,
//                         ${col(WinkerkContract.winkerkEntry.LIDMATE_VAN)} ASC,
//                         ${col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)} ASC
//                """.trimIndent()
//            }
//            "Doop" -> {
//                """
//                SELECT Members._rowid_ as _id, * FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
//                WHERE (${col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)} = "0")
//                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM)},4,2) = "$currentMonth")
//                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM)},1,2) = "$currentDay")
//                ORDER BY substr(${col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM)},4,2) ASC,
//                         substr(${col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM)},1,2) ASC,
//                         ${col(WinkerkContract.winkerkEntry.LIDMATE_VAN)} ASC,
//                         ${col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)} ASC
//                """.trimIndent()
//            }
//            "Huwelik" -> {
//                """
//                SELECT Members._rowid_ as _id, * FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
//                WHERE (${col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)} = "0")
//                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)},4,2) = "$currentMonth")
//                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)},1,2) = "$currentDay")
//                ORDER BY substr(${col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)},4,2) ASC,
//                         substr(${col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)},1,2) ASC,
//                         ${col(WinkerkContract.winkerkEntry.LIDMATE_VAN)} ASC,
//                         ${col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)} ASC
//                """.trimIndent()
//            }
//            "Bely" -> {
//                """
//                SELECT Members._rowid_ as _id, * FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
//                WHERE (${col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)} = "0")
//                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM)},4,2) = "$currentMonth")
//                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM)},1,2) = "$currentDay")
//                ORDER BY substr(${col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM)},4,2) ASC,
//                         substr(${col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM)},1,2) ASC,
//                         ${col(WinkerkContract.winkerkEntry.LIDMATE_VAN)} ASC,
//                         ${col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)} ASC
//                """.trimIndent()
//            }
//            else -> {
//                Log.e("EventViewModel", "Invalid event type: $eventType")
//                null
//            }
//        }
//
//        selection?.let {
//            Log.d("EventViewModel", "Fetching $eventType data: $it")
//            when (eventType) {
//                "Verjaar" -> birthdayData.postValue(queryDatabase(context, it))
//                "Doop" -> baptismData.postValue(queryDatabase(context, it))
//                "Huwelik" -> weddingData.postValue(queryDatabase(context, it))
//                "Bely" -> confessionData.postValue(queryDatabase(context, it))
//            }
//        }
//    }
//
//    private fun queryDatabase(context: Context, query: String): Cursor? {
//        return try {
//            context.contentResolver.query(
//                WinkerkContract.winkerkEntry.CONTENT_URI,
//                null,
//                query,
//                null,
//                null
//            )?.apply {
//                Log.d("EventViewModel", "Query returned $count rows")
//            } ?: run {
//                Log.e("EventViewModel", "Query returned null cursor")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e("EventViewModel", "Query failed: ${e.message}")
//            Log.e("EventViewModel", "Query was: $query")
//            null
//        }
//    }
//}