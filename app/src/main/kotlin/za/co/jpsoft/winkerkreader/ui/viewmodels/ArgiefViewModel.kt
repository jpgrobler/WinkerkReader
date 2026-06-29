package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.app.Application
import android.database.Cursor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry

class ArgiefViewModel(application: Application) : AndroidViewModel(application) {

    private val _archiveCursor = MutableLiveData<Cursor?>()
    val archiveCursor: LiveData<Cursor?> = _archiveCursor

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var currentSortBy: String = "Van"
    private var currentSearchTerm: String? = null
    private var isFirstLoad = true

    fun loadArchive(sortBy: String, searchTerm: String? = null) {
        // If we're already loading the same data, skip
        if (!isFirstLoad && currentSortBy == sortBy && currentSearchTerm == searchTerm) {
            return
        }

        isFirstLoad = false
        currentSortBy = sortBy
        currentSearchTerm = searchTerm

        _isLoading.postValue(true)

        viewModelScope.launch(Dispatchers.IO) {
            val selection = buildQuery(sortBy, searchTerm)
            val contentResolver = getApplication<Application>().contentResolver

            // Query on background thread
            val newCursor = contentResolver.query(
                winkerkEntry.ARGIEF_URI,
                null,
                selection,
                null,
                null
            )

            // Close old cursor and update LiveData on main thread
            withContext(Dispatchers.Main) {
                val oldCursor = _archiveCursor.value
                if (oldCursor != null && !oldCursor.isClosed) {
                    oldCursor.close()
                }
                _archiveCursor.value = newCursor
                _isLoading.value = false
            }
        }
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