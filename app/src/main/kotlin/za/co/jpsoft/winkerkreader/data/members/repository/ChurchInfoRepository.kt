package za.co.jpsoft.winkerkreader.data.members.repository

import android.database.Cursor
import android.util.Log
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.setup.WinkerkDatabase
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import javax.inject.Singleton

@Singleton
class ChurchInfoRepository @Inject constructor(
    private val database: WinkerkDatabase,
    private val congregationPrefs: CongregationPrefs
) {

    private val TAG = "ChurchInfoRepo"

    suspend fun loadChurchInfo() {
        withContext(Dispatchers.IO) {
            try {
                val db = database.openHelper.writableDatabase
                val cursor: Cursor = db.query(
                    "SELECT DISTINCT Gemeente, [Gemeente epos] FROM Members GROUP BY Gemeente, [Gemeente epos]"
                )
                cursor.use {
                    val names = mutableListOf<String>()
                    val emails = mutableListOf<String>()
                    while (it.moveToNext()) {
                        names.add(it.getString(0) ?: "")
                        emails.add(it.getString(1) ?: "")
                    }
                    if (names.isNotEmpty()) {
                        congregationPrefs.gemeenteNaam = names.getOrElse(0) { "" }
                        congregationPrefs.gemeenteEpos = emails.getOrElse(0) { "" }
                        congregationPrefs.gemeente2Naam = names.getOrElse(1) { "" }
                        congregationPrefs.gemeente2Epos = emails.getOrElse(1) { "" }
                        congregationPrefs.gemeente3Naam = names.getOrElse(2) { "" }
                        congregationPrefs.gemeente3Epos = emails.getOrElse(2) { "" }
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${names.size} congregation(s)")
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load church info", e)
            }
        }
    }
}