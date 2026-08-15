package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.ui.ColorUtils

class CongregationChipController(
    private val context: Context,
    private val chipContainer: LinearLayout,
    private val congregationPrefs: CongregationPrefs,
    private val onFilterChanged: (selected: Set<String>) -> Unit
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("congregation_filter", Context.MODE_PRIVATE)
    private val SELECTED_KEY = "selected_congregations"

    private val selectedCongregations = mutableSetOf<String>()
    private var isUpdating = false

    // ─── Debounce handler ───────────────────────────────────────────────
    private val filterHandler = Handler(Looper.getMainLooper())
    private var filterRunnable: Runnable? = null
    private var isSetup = false
    private var lastCongregations: List<String>? = null
    // ─── Public API ──────────────────────────────────────────────────────

    fun refresh() {
        // Check if congregations have changed before refreshing
        val current = listOfNotNull(
            congregationPrefs.gemeenteNaam.takeIf { it.isNotBlank() },
            congregationPrefs.gemeente2Naam.takeIf { it.isNotBlank() },
            congregationPrefs.gemeente3Naam.takeIf { it.isNotBlank() }
        )
        if (current == lastCongregations && isSetup) {
            if (BuildConfig.DEBUG) Log.d(
                "ChipController",
                "Refresh skipped – no congregation changes"
            )
            return
        }
        // Force a full setup if changed
        setup()
    }

    fun setup() {
        chipContainer.post {
            isUpdating = true
            chipContainer.removeAllViews()
            selectedCongregations.clear()

            val allCongregations = listOfNotNull(
                congregationPrefs.gemeenteNaam.takeIf { it.isNotBlank() },
                congregationPrefs.gemeente2Naam.takeIf { it.isNotBlank() },
                congregationPrefs.gemeente3Naam.takeIf { it.isNotBlank() }
            )

            if (allCongregations.isEmpty()) {
                isUpdating = false
                return@post
            }
            if (isSetup && allCongregations == lastCongregations) {
                if (BuildConfig.DEBUG) Log.d("ChipController", "Skipping setup – no changes")
                return@post
            }
            val savedSelection = loadSavedSelection(allCongregations)
            chipContainer.weightSum = allCongregations.size.toFloat()

            allCongregations.forEach { name ->
                val color = congregationColor(name)
                val chip = buildChip(name, color)

                val isInitiallyChecked = savedSelection.contains(name)
                chip.isChecked = isInitiallyChecked
                if (isInitiallyChecked) {
                    selectedCongregations.add(name)
                    chip.applyCheckedStyle(color)
                } else {
                    chip.applyUncheckedStyle()
                }

                val params = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    weight = 1f
                    marginStart = 4.dpToPx()
                    marginEnd = 4.dpToPx()
                }
                chipContainer.addView(chip, params)
            }

            isUpdating = false
            applyFilter() // debounced
            if (BuildConfig.DEBUG) Log.d(
                "ChipController",
                "Found ${allCongregations.size} congregations: $allCongregations"
            )
        }
    }

    fun selectAll() {
        isUpdating = true
        for (i in 0 until chipContainer.childCount) {
            val chip = chipContainer.getChildAt(i) as? Chip ?: continue
            val name = chip.text.toString()
            chip.isChecked = true
            selectedCongregations.add(name)
            val color = congregationColor(name)
            chip.applyCheckedStyle(color)
        }
        isUpdating = false
        applyFilter()
    }

    fun deselectAll() {
        isUpdating = true
        for (i in 0 until chipContainer.childCount) {
            val chip = chipContainer.getChildAt(i) as? Chip ?: continue
            val name = chip.text.toString()
            chip.isChecked = false
            selectedCongregations.remove(name)
            chip.applyUncheckedStyle()
        }
        isUpdating = false
        applyFilter()
    }

    // ─── Private helpers ───────────────────────────────────────────────

    private fun congregationColor(name: String): Int = when (name) {
        congregationPrefs.gemeenteNaam -> congregationPrefs.gemeenteKleur
        congregationPrefs.gemeente2Naam -> congregationPrefs.gemeente2Kleur
        congregationPrefs.gemeente3Naam -> congregationPrefs.gemeente3Kleur
        else -> ContextCompat.getColor(context, R.color.md_theme_primary)
    }

    private fun buildChip(name: String, color: Int): Chip = Chip(context).apply {
        text = name
        isCheckable = true
        textSize = 12f

        setOnClickListener {
            if (isUpdating) return@setOnClickListener
            // ✅ CHIP CLICK FIX: use the already‑toggled state
            if (isChecked) {
                selectedCongregations.add(name)
                applyCheckedStyle(color)
            } else {
                selectedCongregations.remove(name)
                applyUncheckedStyle()
            }
            applyFilter() // debounced
        }
    }

    private fun Chip.applyCheckedStyle(color: Int) {
        val validColor = if (color == Int.MIN_VALUE) {
            ContextCompat.getColor(context, R.color.md_theme_primary)
        } else color
        setChipBackgroundColor(android.content.res.ColorStateList.valueOf(validColor))
        setTextColor(ColorUtils.contrastingTextColor(validColor))
        chipStrokeWidth = 2f
        chipStrokeColor = android.content.res.ColorStateList.valueOf(
            ColorUtils.contrastingTextColor(validColor)
        )
    }

    private fun Chip.applyUncheckedStyle() {
        setChipBackgroundColor(
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.md_theme_surfaceVariant)
            )
        )
        setTextColor(ContextCompat.getColor(context, R.color.md_theme_onSurface))
        chipStrokeWidth = 0f
    }

    // ─── Persistence ────────────────────────────────────────────────────

    private fun saveSelection(selected: Set<String>) {
        val joined = selected.joinToString(",")
        prefs.edit().putString(SELECTED_KEY, joined).apply()
        if (BuildConfig.DEBUG) Log.d("ChipController", "Saved selection: $joined")
    }

    private fun loadSavedSelection(allCongregations: List<String>): Set<String> {
        val saved = prefs.getString(SELECTED_KEY, null)
        return if (saved.isNullOrBlank()) {
            allCongregations.toSet()
        } else {
            val selected = saved.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
                .intersect(allCongregations.toSet())
            // If none of the saved congregations match the current list,
            // default to selecting all congregations.
            if (selected.isEmpty()) allCongregations.toSet() else selected
        }
    }

    // ─── Debounced filter ──────────────────────────────────────────────

    private fun applyFilter() {
        // Cancel pending runnable
        filterRunnable?.let { filterHandler.removeCallbacks(it) }
        filterRunnable = Runnable {
            saveSelection(selectedCongregations)
            onFilterChanged(selectedCongregations.toSet())
        }
        // Schedule new one with 300ms debounce
        filterRunnable?.let { filterHandler.postDelayed(it, 300) }
    }

    // ─── Utility ────────────────────────────────────────────────────────

    private fun Int.dpToPx(): Int = (this * context.resources.displayMetrics.density).toInt()
}