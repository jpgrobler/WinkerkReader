package za.co.jpsoft.winkerkreader

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.content.Context
import android.database.Cursor
import android.util.Log
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.col
import org.joda.time.DateTime

class EventViewModel : ViewModel() {

    private val birthdayData = MutableLiveData<Cursor?>()
    private val baptismData = MutableLiveData<Cursor?>()
    private val weddingData = MutableLiveData<Cursor?>()
    private val confessionData = MutableLiveData<Cursor?>()

    fun getBirthdayData(context: Context): LiveData<Cursor?> {
        fetchData(context, "Verjaar")
        return birthdayData
    }

    fun getBaptismData(context: Context): LiveData<Cursor?> {
        fetchData(context, "Doop")
        return baptismData
    }

    fun getWeddingData(context: Context): LiveData<Cursor?> {
        fetchData(context, "Huwelik")
        return weddingData
    }

    fun getConfessionData(context: Context): LiveData<Cursor?> {
        fetchData(context, "Bely")
        return confessionData
    }

    private fun fetchData(context: Context, eventType: String) {
        val currentMonth = "%02d".format(DateTime.now().monthOfYear)
        val currentDay = "%02d".format(DateTime.now().dayOfMonth)

        val selection = when (eventType) {
            "Verjaar" -> {
                """
                SELECT Members._rowid_ as _id, * FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
                WHERE (${col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)} = "0")
                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)},4,2) = "$currentMonth")
                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)},1,2) = "$currentDay")
                ORDER BY substr(${col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)},4,2) ASC,
                         substr(${col(WinkerkContract.winkerkEntry.LIDMATE_GEBOORTEDATUM)},1,2) ASC,
                         ${col(WinkerkContract.winkerkEntry.LIDMATE_VAN)} ASC,
                         ${col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)} ASC
                """.trimIndent()
            }
            "Doop" -> {
                """
                SELECT Members._rowid_ as _id, * FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
                WHERE (${col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)} = "0")
                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM)},4,2) = "$currentMonth")
                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM)},1,2) = "$currentDay")
                ORDER BY substr(${col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM)},4,2) ASC,
                         substr(${col(WinkerkContract.winkerkEntry.LIDMATE_DOOPDATUM)},1,2) ASC,
                         ${col(WinkerkContract.winkerkEntry.LIDMATE_VAN)} ASC,
                         ${col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)} ASC
                """.trimIndent()
            }
            "Huwelik" -> {
                """
                SELECT Members._rowid_ as _id, * FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
                WHERE (${col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)} = "0")
                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)},4,2) = "$currentMonth")
                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)},1,2) = "$currentDay")
                ORDER BY substr(${col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)},4,2) ASC,
                         substr(${col(WinkerkContract.winkerkEntry.LIDMATE_HUWELIKSDATUM)},1,2) ASC,
                         ${col(WinkerkContract.winkerkEntry.LIDMATE_VAN)} ASC,
                         ${col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)} ASC
                """.trimIndent()
            }
            "Bely" -> {
                """
                SELECT Members._rowid_ as _id, * FROM ${WinkerkContract.winkerkEntry.SELECTION_LIDMAAT_FROM}
                WHERE (${col(WinkerkContract.winkerkEntry.LIDMATE_REKORDSTATUS)} = "0")
                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM)},4,2) = "$currentMonth")
                  AND (substr(${col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM)},1,2) = "$currentDay")
                ORDER BY substr(${col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM)},4,2) ASC,
                         substr(${col(WinkerkContract.winkerkEntry.LIDMATE_BELYDENISDATUM)},1,2) ASC,
                         ${col(WinkerkContract.winkerkEntry.LIDMATE_VAN)} ASC,
                         ${col(WinkerkContract.winkerkEntry.LIDMATE_NOEMNAAM)} ASC
                """.trimIndent()
            }
            else -> {
                Log.e("EventViewModel", "Invalid event type: $eventType")
                null
            }
        }

        selection?.let {
            Log.d("EventViewModel", "Fetching $eventType data: $it")
            when (eventType) {
                "Verjaar" -> birthdayData.postValue(queryDatabase(context, it))
                "Doop" -> baptismData.postValue(queryDatabase(context, it))
                "Huwelik" -> weddingData.postValue(queryDatabase(context, it))
                "Bely" -> confessionData.postValue(queryDatabase(context, it))
            }
        }
    }

    private fun queryDatabase(context: Context, query: String): Cursor? {
        return try {
            context.contentResolver.query(
                WinkerkContract.winkerkEntry.CONTENT_URI,
                null,
                query,
                null,
                null
            )?.apply {
                Log.d("EventViewModel", "Query returned $count rows")
            } ?: run {
                Log.e("EventViewModel", "Query returned null cursor")
                null
            }
        } catch (e: Exception) {
            Log.e("EventViewModel", "Query failed: ${e.message}")
            Log.e("EventViewModel", "Query was: $query")
            null
        }
    }
}