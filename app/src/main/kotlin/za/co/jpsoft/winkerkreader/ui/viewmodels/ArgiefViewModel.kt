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

    // Keep reference to old cursor for delayed closing
    private var pendingCloseCursor: Cursor? = null
    private var currentSortBy: String = "Van"
    private var currentSearchTerm: String? = null
    private var isFirstLoad = true

    fun loadArchive(sortBy: String, searchTerm: String? = null) {
        // Don't reload if same parameters (optimization) - but allow first load
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

        // Store old cursor for later closing (after adapter has finished with it)
        val oldCursor = _archiveCursor.value
        if (oldCursor != null && !oldCursor.isClosed) {
            pendingCloseCursor = oldCursor
        }

        // Post new cursor immediately
        _archiveCursor.postValue(newCursor)

        // Close old cursor after a delay to ensure adapter has released it
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            pendingCloseCursor?.close()
            pendingCloseCursor = null
        }, 500)
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

    // Force refresh (useful for retry scenarios)
    fun refresh() {
        loadArchive(currentSortBy, currentSearchTerm)
    }

    override fun onCleared() {
        pendingCloseCursor?.close()
        pendingCloseCursor = null
        _archiveCursor.value?.close()
        super.onCleared()
    }
}