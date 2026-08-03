package za.co.jpsoft.winkerkreader.ui.controllers

import android.view.View
import android.widget.TextView
import za.co.jpsoft.winkerkreader.databinding.LaaidatabasisBinding
import za.co.jpsoft.winkerkreader.utils.prefs.SyncPrefs

class CollapsibleCardController(private val syncPrefs: SyncPrefs) {

    /**
     * Wires a single card. The getter/setter lambdas read/write the persistent state.
     */
    fun setup(
        headerView: View,
        contentView: View,
        arrowView: TextView,
        getExpanded: () -> Boolean,
        setExpanded: (Boolean) -> Unit,
        defaultOpen: Boolean
    ) {
        var isOpen = getExpanded()  // reads current stored state

        fun applyState() {
            contentView.visibility = if (isOpen) View.VISIBLE else View.GONE
            arrowView.text = if (isOpen) "▼" else "▶"
        }

        // If no value was ever stored, use defaultOpen
        // Our getter will return default if not set, but we need to initialise if not present.
        // We can just call setExpanded(defaultOpen) if the preference doesn't exist,
        // but our SyncPrefs properties already have defaults, so isOpen is fine.

        applyState()

        headerView.setOnClickListener {
            isOpen = !isOpen
            applyState()
            setExpanded(isOpen)   // persist via typed property
        }
    }

    fun setupAll(binding: LaaidatabasisBinding) {
        setup(
            binding.headerLocal,
            binding.contentLocal,
            binding.arrowLocal,
            getExpanded = { syncPrefs.cardLocalExpanded },
            setExpanded = { syncPrefs.cardLocalExpanded = it },
            defaultOpen = false
        )
        setup(
            binding.headerDropbox,
            binding.contentDropbox,
            binding.arrowDropbox,
            getExpanded = { syncPrefs.cardDropboxExpanded },
            setExpanded = { syncPrefs.cardDropboxExpanded = it },
            defaultOpen = false
        )
        setup(
            binding.headerWifi,
            binding.contentWifi,
            binding.arrowWifi,
            getExpanded = { syncPrefs.cardWifiExpanded },
            setExpanded = { syncPrefs.cardWifiExpanded = it },
            defaultOpen = true
        )
        setup(
            binding.headerPhoto,
            binding.contentPhoto,
            binding.arrowPhoto,
            getExpanded = { syncPrefs.cardPhotoExpanded },
            setExpanded = { syncPrefs.cardPhotoExpanded = it },
            defaultOpen = true
        )
    }
}