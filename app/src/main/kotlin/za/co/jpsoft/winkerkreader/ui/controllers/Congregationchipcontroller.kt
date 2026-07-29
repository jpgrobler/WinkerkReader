package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.utils.ColorUtils
import za.co.jpsoft.winkerkreader.utils.SettingsManager

class CongregationChipController(
    private val context: Context,
    private val chipGroup: ChipGroup,
    private val loadingBar: View,
    private val settings: SettingsManager,
    private val onFilterChanged: (selected: Set<String>) -> Unit
) {

    private var isUpdating = false

    fun setup() {
        chipGroup.removeAllViews()
        chipGroup.isSingleSelection = false
        chipGroup.isSelectionRequired = false

        val congregations = listOfNotNull(
            settings.gemeenteNaam.takeIf { it.isNotBlank() },
            settings.gemeente2Naam.takeIf { it.isNotBlank() },
            settings.gemeente3Naam.takeIf { it.isNotBlank() }
        )

        congregations.forEach { name ->
            val color = congregationColor(name)
            chipGroup.addView(buildChip(name, color))
        }

        loadingBar.post { loadingBar.visibility = View.GONE }
    }

    /**
     * Select all chips and apply filter with all congregations.
     */
    fun selectAll() {
        isUpdating = true
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip ?: continue
            chip.isChecked = true
            val name = chip.text.toString()
            val color = congregationColor(name)
            chip.applyCheckedStyle(color)
        }
        isUpdating = false
        applyFilter()
    }

    /**
     * Deselect all chips (clear congregation filter).
     */
    fun deselectAll() {
        isUpdating = true
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip ?: continue
            chip.isChecked = false
            chip.applyUncheckedStyle()
        }
        isUpdating = false
        applyFilter()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun congregationColor(name: String): Int = when (name) {
        settings.gemeenteNaam  -> settings.gemeenteKleur
        settings.gemeente2Naam -> settings.gemeente2Kleur
        settings.gemeente3Naam -> settings.gemeente3Kleur
        else -> ContextCompat.getColor(context, R.color.md_theme_primary)
    }

    private fun buildChip(name: String, color: Int): Chip = Chip(context).apply {
        text = name
        isCheckable = true
        textSize = 12f
        isChecked = true

        applyCheckedStyle(color)

        setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdating) {
                if (isChecked) applyCheckedStyle(color) else applyUncheckedStyle()
                applyFilter()
            }
        }
    }

    private fun Chip.applyCheckedStyle(color: Int) {
        setChipBackgroundColor(android.content.res.ColorStateList.valueOf(color))
        setTextColor(ColorUtils.contrastingTextColor(color))
        chipStrokeWidth = 2f
        chipStrokeColor = android.content.res.ColorStateList.valueOf(
            ColorUtils.contrastingTextColor(color)
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

    private fun getSelected(): Set<String> {
        val selected = mutableSetOf<String>()
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip
            if (chip?.isChecked == true) selected.add(chip.text.toString())
        }
        return selected
    }

    private fun applyFilter() {
        onFilterChanged(getSelected())
    }
}