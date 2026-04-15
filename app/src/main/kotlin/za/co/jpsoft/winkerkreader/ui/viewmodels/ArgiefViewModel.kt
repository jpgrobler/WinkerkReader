package za.co.jpsoft.winkerkreader.ui.viewmodels


import android.app.Application
import android.database.Cursor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry

class ArgiefViewModel(application: Application) : AndroidViewModel(application) {

    private val _archiveCursor = MutableLiveData<Cursor?>()
    val archiveCursor: LiveData<Cursor?> = _archiveCursor

    private val contentResolver = application.contentResolver

    fun loadArchive(sortBy: String, searchTerm: String? = null) {
        val selection = buildQuery(sortBy, searchTerm)
        val cursor = contentResolver.query(
            winkerkEntry.ARGIEF_URI,
            null,
            selection,
            null,
            null
        )
        // Close previous cursor if any (handled in activity via observe)
        _archiveCursor.postValue(cursor)
    }

    private fun buildQuery(sortBy: String, searchTerm: String?): String {
        val baseQuery = "Select Argief._rowid_ as _id, * from Argief"
        val whereClause = if (!searchTerm.isNullOrBlank()) {
            " WHERE (Surname LIKE '%$searchTerm%') OR (Name LIKE '%$searchTerm%')"
        } else ""
        val orderClause = when (sortBy) {
            "Van" -> " ORDER BY Surname, Name, DepartureDate"
            "Rede" -> " ORDER BY Reason, Surname, Name, DepartureDate"
            "Datum" -> " ORDER BY substr(DepartureDate,7,4), substr(DepartureDate,4,2), substr(DepartureDate,1,2), Surname, Name"
            else -> ""
        }
        return baseQuery + whereClause + orderClause
    }

    override fun onCleared() {
        _archiveCursor.value?.close()
        super.onCleared()
    }
}