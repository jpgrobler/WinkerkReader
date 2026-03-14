// SearchCheckBoxPreferences.kt
package za.co.jpsoft.winkerkreader

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class SearchCheckBoxPreferences(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // Save ArrayList to SharedPreferences
    fun saveSearchCheckBoxList(list: ArrayList<SearchCheckBox>) {
        val json = gson.toJson(list)
        preferences.edit().putString(KEY_CHECKBOX_LIST, json).apply()
    }

    // Retrieve ArrayList from SharedPreferences
    fun getSearchCheckBoxList(): ArrayList<SearchCheckBox> {
        val json = preferences.getString(KEY_CHECKBOX_LIST, null) ?: return arrayListOf()
        return try {
            val type: Type = object : TypeToken<ArrayList<SearchCheckBox>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            arrayListOf()
        }
    }

    // Clear the saved list
    fun clearSearchCheckBoxList() {
        preferences.edit().remove(KEY_CHECKBOX_LIST).apply()
    }

    // Check if list exists in preferences
    fun hasSavedList(): Boolean = preferences.contains(KEY_CHECKBOX_LIST)

    companion object {
        private const val PREF_NAME = "search_checkbox_prefs"
        private const val KEY_CHECKBOX_LIST = "search_checkbox_list"
    }
}