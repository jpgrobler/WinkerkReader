package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.data.pastoral.entities.PastoralNoteEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.NoteCategory
import za.co.jpsoft.winkerkreader.databinding.ItemPastoralNoteBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PastoralNoteAdapter(
    private val onEdit:            (PastoralNoteEntity) -> Unit = {},
    private val onDelete:          (PastoralNoteEntity) -> Unit = {},
    private val onConfidentialTap: (PastoralNoteEntity) -> Unit = {}
) : ListAdapter<PastoralNoteAdapter.NoteUiState, PastoralNoteAdapter.ViewHolder>(DIFF) {

    /**
     * Wraps a note with its current reveal state.
     * [revealed] is true while the confidential content is temporarily visible.
     */
    data class NoteUiState(
        val note: PastoralNoteEntity,
        val revealed: Boolean = false
    )

    private val dateFormatter = DateTimeFormatter
        .ofPattern("d MMM yyyy", Locale.getDefault())

    // ── Reveal state ───────────────────────────────────────────────────────
    private val revealedIds  = mutableSetOf<String>()
    private var rawNotes: List<PastoralNoteEntity> = emptyList()

    fun revealNote(noteId: String) {
        revealedIds.add(noteId)
        rebuildUiState()
    }

    fun hideNote(noteId: String) {
        revealedIds.remove(noteId)
        rebuildUiState()
    }

    fun submitNotes(notes: List<PastoralNoteEntity>) {
        rawNotes = notes
        rebuildUiState()
    }

    private fun rebuildUiState() {
        submitList(rawNotes.map { note ->
            NoteUiState(note, revealed = note.noteId in revealedIds)
        })
    }

    // ── Adapter ────────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPastoralNoteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPastoralNoteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(state: NoteUiState) {
            val note     = state.note
            val category = NoteCategory.fromStored(note.category)

            // Kategorie + datum
            binding.tvNoteSymbol.text   = category.symbol
            binding.tvNoteCategory.text = category.labelAf
            binding.tvNoteDate.text     = Instant.ofEpochMilli(note.noteDateUtc)
                .atZone(ZoneId.systemDefault())
                .format(dateFormatter)

            // Nota teks / vertroulik
            when {
                !note.isConfidential -> {
                    binding.tvNoteText.visibility         = View.VISIBLE
                    binding.tvNoteText.text               = note.noteText
                    binding.tvNoteConfidential.visibility = View.GONE
                    binding.tvNoteRevealHint.visibility   = View.GONE
                }
                state.revealed -> {
                    binding.tvNoteText.visibility         = View.VISIBLE
                    binding.tvNoteText.text               = note.noteText
                    binding.tvNoteConfidential.visibility = View.GONE
                    binding.tvNoteRevealHint.visibility   = View.VISIBLE
                    binding.tvNoteRevealHint.text         = "🔓 Vertroulik · versteek oor 30s"
                }
                else -> {
                    binding.tvNoteText.visibility         = View.GONE
                    binding.tvNoteConfidential.visibility = View.VISIBLE
                    binding.tvNoteRevealHint.visibility   = View.GONE
                }
            }

            // Herinnering-koppeling badge
            binding.tvNoteLinkedReminder.visibility =
                if (note.linkedReminderId != null) View.VISIBLE else View.GONE

            // Tik op vertroulike nota → biometrie
            binding.root.setOnClickListener {
                if (note.isConfidential && !state.revealed) {
                    onConfidentialTap(note)
                }
            }

            // Lang-druk → redigeer / verwyder
            binding.root.setOnLongClickListener {
                showContextMenu(binding.root, note)
                true
            }
        }

        private fun showContextMenu(anchor: View, note: PastoralNoteEntity) {
            val popup = PopupMenu(anchor.context, anchor)
            popup.menu.apply {
                add(0, MENU_EDIT,   0, "✏️  Redigeer nota")
                add(0, MENU_DELETE, 1, "🗑️  Verwyder nota")
            }
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EDIT   -> { onEdit(note);   true }
                    MENU_DELETE -> { onDelete(note); true }
                    else        -> false
                }
            }
            popup.show()
        }
    }

    companion object {
        private const val MENU_EDIT   = 1
        private const val MENU_DELETE = 2

        private val DIFF = object : DiffUtil.ItemCallback<NoteUiState>() {
            override fun areItemsTheSame(a: NoteUiState, b: NoteUiState) =
                a.note.noteId == b.note.noteId
            override fun areContentsTheSame(a: NoteUiState, b: NoteUiState) =
                a == b
        }
    }
}
