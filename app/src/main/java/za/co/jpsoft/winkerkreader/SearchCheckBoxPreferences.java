package za.co.jpsoft.winkerkreader;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;

public class SearchCheckBoxPreferences {
    private static final String PREF_NAME = "search_checkbox_prefs";
    private static final String KEY_CHECKBOX_LIST = "search_checkbox_list";

    private SharedPreferences preferences;
    private Gson gson;

    public SearchCheckBoxPreferences(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    // Save ArrayList to SharedPreferences
    public void saveSearchCheckBoxList(ArrayList<SearchCheckBox> list) {
        SharedPreferences.Editor editor = preferences.edit();
        String json = gson.toJson(list);
        editor.putString(KEY_CHECKBOX_LIST, json);
        editor.apply(); // or use commit() if you need immediate result
    }

    // Retrieve ArrayList from SharedPreferences
    public ArrayList<SearchCheckBox> getSearchCheckBoxList() {
        String json = preferences.getString(KEY_CHECKBOX_LIST, null);
        if (json == null) {
            return new ArrayList<>(); // Return empty list if nothing saved
        }

        try {
            Type type = new TypeToken<ArrayList<SearchCheckBox>>() {}.getType();
            return gson.fromJson(json, type);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // Clear the saved list
    public void clearSearchCheckBoxList() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(KEY_CHECKBOX_LIST);
        editor.apply();
    }

    // Check if list exists in preferences
    public boolean hasSavedList() {
        return preferences.contains(KEY_CHECKBOX_LIST);
    }
}