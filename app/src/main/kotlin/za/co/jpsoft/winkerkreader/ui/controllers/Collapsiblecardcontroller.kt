package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.SharedPreferences
import android.view.View
import android.widget.TextView
import androidx.core.content.edit
import za.co.jpsoft.winkerkreader.databinding.LaaidatabasisBinding

/**
 * Manages expandable/collapsible card sections on LaaiDatabasisActivity.
 *
 * Each card remembers its open/closed state in [SharedPreferences].
 * Extracted from LaaiDatabasisActivity.setupCollapsibleCard() and
 * initializeCollapsibleCards().
 *
 * ── Wiring ───────────────────────────────────────────────────────────────────
 *
 *   collapsibleCardController = CollapsibleCardController(settings)
 *   collapsibleCardController.setupAll(binding)
 *
 *   // Replace initializeCollapsibleCards() call in onCreate().
 */
class CollapsibleCardController(private val prefs: SharedPreferences) {

    /**
     * Wires a single collapsible card. The header click toggles [contentView]
     * and updates [arrowView] to ▼/▶. State is persisted under [prefKey].
     *
     * Was [LaaiDatabasisActivity.setupCollapsibleCard].
     */
    fun setup(
        headerView: View,
        contentView: View,
        arrowView: TextView,
        prefKey: String,
        defaultOpen: Boolean
    ) {
        var isOpen = prefs.getBoolean(prefKey, defaultOpen)

        fun applyState() {
            contentView.visibility = if (isOpen) View.VISIBLE else View.GONE
            arrowView.text = if (isOpen) "▼" else "▶"
        }

        applyState()

        headerView.setOnClickListener {
            isOpen = !isOpen
            applyState()
            prefs.edit { putBoolean(prefKey, isOpen) }
        }
    }

    /**
     * Sets up all four collapsible cards used in LaaiDatabasisActivity.
     *
     * Was [LaaiDatabasisActivity.initializeCollapsibleCards].
     */
    fun setupAll(binding: LaaidatabasisBinding) {
        setup(
            binding.headerLocal,
            binding.contentLocal,
            binding.arrowLocal,
            "CARD_LOCAL_EXPANDED",
            defaultOpen = false
        )
        setup(
            binding.headerDropbox,
            binding.contentDropbox,
            binding.arrowDropbox,
            "CARD_DROPBOX_EXPANDED",
            defaultOpen = false
        )
        setup(
            binding.headerWifi,
            binding.contentWifi,
            binding.arrowWifi,
            "CARD_WIFI_EXPANDED",
            defaultOpen = true
        )
        setup(
            binding.headerPhoto,
            binding.contentPhoto,
            binding.arrowPhoto,
            "CARD_PHOTO_EXPANDED",
            defaultOpen = true
        )
    }
}