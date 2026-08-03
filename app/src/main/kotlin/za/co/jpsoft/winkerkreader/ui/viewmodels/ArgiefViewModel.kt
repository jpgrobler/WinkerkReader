package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.app.Application
import android.database.Cursor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.data.members.setup.WinkerkDatabase

class ArgiefViewModel(application: Application) : AndroidViewModel(application) {

    // Resolve on each use — companion instance is replaced after DB import.
    private fun argiefDao() = WinkerkDatabase.getInstance(getApplication()).argiefDao()

    private val _archiveCursor = MutableLiveData<Cursor?>()
    val archiveCursor: LiveData<Cursor?> = _archiveCursor

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

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

        _isLoading.postValue(true)

        viewModelScope.launch(Dispatchers.IO) {
            val (sql, args) = buildQuery(sortBy, searchTerm)
            val newCursor = argiefDao().queryRaw(SimpleSQLiteQuery(sql, args))

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

    /**
     * Force-reloads the current data set (e.g. after a database sync).
     * Resets the deduplication guard so the same sort/search params trigger a real query.
     */
    fun refresh() {
        isFirstLoad = true
        loadArchive(currentSortBy, currentSearchTerm)
    }

    override fun onCleared() {
        _archiveCursor.value?.close()
        super.onCleared()
    }

    // ─── Query builder ────────────────────────────────────────────────────────

    /**
     * Builds a parameterised query safe against SQL injection.
     * Search terms are bound as positional args, never interpolated into the SQL string.
     */
    private fun buildQuery(sortBy: String, searchTerm: String?): Pair<String, Array<Any>> {
        val sql = StringBuilder("SELECT Argief._rowid_ AS _id, * FROM Argief")
        val args = mutableListOf<Any>()

        if (!searchTerm.isNullOrBlank()) {
            sql.append(" WHERE (Surname LIKE ? OR Name LIKE ?)")
            val pattern = "%$searchTerm%"
            args.add(pattern)
            args.add(pattern)
        }

        sql.append(
            when (sortBy) {
                "Rede" -> " ORDER BY Reason, Surname, Name, DepartureDate"
                "Datum" -> " ORDER BY substr(DepartureDate,7,4)," +
                        " substr(DepartureDate,4,2)," +
                        " substr(DepartureDate,1,2)," +
                        " Surname, Name"

                else -> " ORDER BY Surname, Name, DepartureDate"  // "Van" + fallback
            }
        )

        return sql.toString() to args.toTypedArray()
    }
}