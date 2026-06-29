package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.content.Context
import android.database.Cursor
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.col
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import java.time.LocalDate

class EventViewModel : ViewModel() {

    private val _eventList = MutableLiveData<List<MemberItem>>(emptyList())
    val eventList: LiveData<List<MemberItem>> = _eventList

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * Load members whose event (birthday, baptism, wedding, confession) falls on today's date.
     * The cursor is opened, converted to [MemberItem]s, and closed immediately.
     */
    fun loadEventData(context: Context, eventType: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val members = withContext(Dispatchers.IO) {
                    queryMembersForEvent(context, eventType)
                }
                _eventList.value = members
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("EventViewModel", "loadEventData failed", e)
                _eventList.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun queryMembersForEvent(context: Context, eventType: String): List<MemberItem> {
        val today = LocalDate.now()
        val currentMonth = "%02d".format(today.monthValue)
        val currentDay = "%02d".format(today.dayOfMonth)

        val selection = when (eventType) {
            "Verjaar" -> buildBirthdaySelection(currentMonth, currentDay)
            "Doop"    -> buildBaptismSelection(currentMonth, currentDay)
            "Huwelik" -> buildWeddingSelection(currentMonth, currentDay)
            "Bely"    -> buildConfessionSelection(currentMonth, currentDay)
            else -> {
                if (BuildConfig.DEBUG) Log.e("EventViewModel", "Invalid event type: $eventType")
                return emptyList()
            }
        }

        val members = queryDatabase(context, selection)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    try {
                        add(MemberItem.fromCursor(cursor))
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e("EventViewModel", "Failed to convert row", e)
                    }
                }
            }
        } ?: emptyList()

        if (BuildConfig.DEBUG) Log.d("EventViewModel", "Built ${members.size} members")
        members.take(3).forEach {
            if (BuildConfig.DEBUG) Log.d("EventViewModel", "Sample: ${it.name} ${it.surname}, id=${it.id}")
        }
        return members
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
                if (BuildConfig.DEBUG) Log.d("EventViewModel", "Query returned ${cursor?.count ?: 0} rows")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("EventViewModel", "Query failed: ${e.message}\nQuery: $query")
            null
        }
    }
}
