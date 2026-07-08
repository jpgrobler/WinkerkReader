// data/repositories/ChurchInfoRepository.kt
package za.co.jpsoft.winkerkreader.data.repositories

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase
import za.co.jpsoft.winkerkreader.utils.SettingsManager

object ChurchInfoRepository {

    private const val TAG = "ChurchInfoRepo"

    suspend fun loadChurchInfo(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val db = WinkerkDatabase.getInstance(context).openHelper.writableDatabase
                val settingsManager = SettingsManager.getInstance(context)

                val cursor = db.query(
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
                        settingsManager.gemeenteNaam = names.getOrElse(0) { "" }
                        settingsManager.gemeenteEpos = emails.getOrElse(0) { "" }
                        settingsManager.gemeente2Naam = names.getOrElse(1) { "" }
                        settingsManager.gemeente2Epos = emails.getOrElse(1) { "" }
                        settingsManager.gemeente3Naam = names.getOrElse(2) { "" }
                        settingsManager.gemeente3Epos = emails.getOrElse(2) { "" }
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${names.size} congregation(s)")
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load church info", e)
            }
        }
    }
}