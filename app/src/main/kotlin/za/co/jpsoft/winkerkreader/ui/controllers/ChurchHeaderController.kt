package za.co.jpsoft.winkerkreader.ui.controllers

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase
import za.co.jpsoft.winkerkreader.databinding.ActivityMainBinding
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.utils.SettingsManager

/**
 * Manages the church name header in the main activity.
 * Reads congregation names from the database, applies user-selected colours,
 * and updates the TextView with styled text.
 */
class ChurchHeaderController(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val settingsManager: SettingsManager,
    private val database: WinkerkDatabase
) {

    /**
     * Loads congregation info from the database and applies the header.
     * Safe to call from any thread – it launches a coroutine internally.
     */
    fun loadAndApply() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            // Query distinct congregations
            val cursor = database.openHelper.writableDatabase.query(
                "SELECT DISTINCT Gemeente, [Gemeente epos] FROM Members GROUP BY Gemeente, [Gemeente epos]"
            )
            var count = 0
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: ""
                val email = cursor.getString(1) ?: ""
                when (count) {
                    0 -> {
                        settingsManager.gemeenteNaam = name
                        settingsManager.gemeenteEpos = email
                    }
                    1 -> {
                        settingsManager.gemeente2Naam = name
                        settingsManager.gemeente2Epos = email
                    }
                    2 -> {
                        settingsManager.gemeente3Naam = name
                        settingsManager.gemeente3Epos = email
                    }
                }
                count++
            }
            cursor.close()

            // Apply on main thread
            withContext(Dispatchers.Main) {
                applyChurchHeader()
            }
        }
    }

    /**
     * Applies the current church header using the values in SettingsManager.
     * Must be called on the main thread.
     */
    fun refresh() {
        applyChurchHeader()
    }

    private fun applyChurchHeader() {
        val name1 = settingsManager.gemeenteNaam
        val name2 = settingsManager.gemeente2Naam
        val name3 = settingsManager.gemeente3Naam

        // Use defaults only if the setting is missing (Int.MIN_VALUE)
        val color1 = if (settingsManager.gemeenteKleur != Int.MIN_VALUE) settingsManager.gemeenteKleur
        else ContextCompat.getColor(activity, R.color.default_gemeente_1)
        val color2 = if (settingsManager.gemeente2Kleur != Int.MIN_VALUE) settingsManager.gemeente2Kleur
        else ContextCompat.getColor(activity, R.color.default_gemeente_2)
        val color3 = if (settingsManager.gemeente3Kleur != Int.MIN_VALUE) settingsManager.gemeente3Kleur
        else ContextCompat.getColor(activity, R.color.default_gemeente_3)

        // Build the full text
        val fullText = buildString {
            append(name1)
            if (name2.isNotEmpty()) append(" $name2")
            if (name3.isNotEmpty()) append(" $name3")
        }

        val spannable = SpannableString(fullText)
        var start = 0

        if (name1.isNotEmpty()) {
            val end1 = name1.length
            spannable.setSpan(RelativeSizeSpan(0.8f), 0, end1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(color1), 0, end1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(StyleSpan(Typeface.BOLD), 0, end1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            start = end1 + 1
        }

        if (name2.isNotEmpty()) {
            val end2 = start + name2.length
            spannable.setSpan(RelativeSizeSpan(0.8f), start, end2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(color2), start, end2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(StyleSpan(Typeface.BOLD), start, end2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            start = end2 + 1
        }

        if (name3.isNotEmpty()) {
            val end3 = start + name3.length
            spannable.setSpan(RelativeSizeSpan(0.8f), start, end3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(color3), start, end3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(StyleSpan(Typeface.BOLD), start, end3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.mainGemeentenaam.text = spannable
    }

    /**
     * Returns true if the colour is dark enough that white text would be readable.
     * Not used in the current implementation, but kept for future contrast adjustments.
     */
    @Suppress("unused")
    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (
                0.299 * Color.red(color) +
                        0.587 * Color.green(color) +
                        0.114 * Color.blue(color)
                ) / 255
        return darkness >= 0.5
    }
}