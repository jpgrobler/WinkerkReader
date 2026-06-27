// File: ArgiefViewModel.kt
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

    private var currentSortBy: String = "Van"
    private var currentSearchTerm: String? = null
    private var isFirstLoad = true

    fun loadArchive(sortBy: String, searchTerm: String? = null) {
        if (!isFirstLoad && currentSortBy == sortBy && currentSearchTerm == searchTerm) {
            return
        }

        isFirstLoad = false
        currentSortBy = sortBy
        currentSearchTerm = searchTerm

        val selection = buildQuery(sortBy, searchTerm)
        val contentResolver = getApplication<Application>().contentResolver

        val newCursor = contentResolver.query(
            winkerkEntry.ARGIEF_URI,
            null,
            selection,
            null,
            null
        )

        // Close old cursor immediately
        val oldCursor = _archiveCursor.value
        if (oldCursor != null && !oldCursor.isClosed) {
            oldCursor.close()
        }

        _archiveCursor.postValue(newCursor)
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

    fun refresh() {
        loadArchive(currentSortBy, currentSearchTerm)
    }

    override fun onCleared() {
        _archiveCursor.value?.close()
        super.onCleared()
    }
}