package za.co.jpsoft.winkerkreader.ui.controllers

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.models.PendingReminderUiItem
import za.co.jpsoft.winkerkreader.data.pastoral.entities.PastoralNoteEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContext
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralNoteRepository
import za.co.jpsoft.winkerkreader.databinding.LidmaatDetailBinding
import za.co.jpsoft.winkerkreader.ui.adapters.PastoralNoteAdapter
import za.co.jpsoft.winkerkreader.ui.adapters.PendingReminderMiniAdapter
import za.co.jpsoft.winkerkreader.ui.bottomsheets.StelHerinneringBottomSheet
import za.co.jpsoft.winkerkreader.ui.bottomsheets.VoegNotaByBottomSheet
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModel
import za.co.jpsoft.winkerkreader.utils.ReminderUiMapper
import za.co.jpsoft.winkerkreader.utils.Utils.toLocalDateSafe
import za.co.jpsoft.winkerkreader.utils.security.NoteAuthManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Manages the Bediening section inside LidmaatDetailActivity:
 * - Pending reminders mini‑list
 * - Add new reminder / note
 * - Collapsible pastoral notes with biometric reveal
 */
class LidmaatPastoralSectionController(
    private val activity: FragmentActivity,
    private val binding: LidmaatDetailBinding,
    private val memberGuid: String,
    private val familyHeadGuid: String?,
    private val memberDisplayName: String,
    private val memberSurname: String?,
    private val memberGivenName: String?,
    private val pastoralViewModel: LidmaatDetailPastoralViewModel
) {

    companion object {
        private const val TAG = "LidmaatPastoralSection"
    }

    // ─── Dependencies ─────────────────────────────────────────────────────────
    private val noteRepo = PastoralNoteRepository(activity)
    private val authManager = NoteAuthManager(activity)

    // ─── Adapters ────────────────────────────────────────────────────────────
    private val miniAdapter = PendingReminderMiniAdapter(
        onComplete = { reminderId -> pastoralViewModel.completeReminder(reminderId) },
        onClick = { reminderId -> showReminderDetailsDialog(reminderId) }   // passes ID
    )

    private val notaAdapter = PastoralNoteAdapter(
        onEdit = { note ->
            VoegNotaByBottomSheet.newInstanceForEdit(
                existingNoteId = note.noteId,
                memberDisplayName = note.memberDisplayNameCache
            ).show(activity.supportFragmentManager, VoegNotaByBottomSheet.TAG)
        },
        onDelete = { note ->
            MaterialAlertDialogBuilder(activity)
                .setTitle("Verwyder nota?")
                .setMessage("Hierdie nota sal permanent verwyder word.")
                .setPositiveButton("Verwyder") { _, _ ->
                    activity.lifecycleScope.launch {
                        withContext(Dispatchers.IO) { noteRepo.delete(note.noteId) }
                    }
                }
                .setNegativeButton("Kanselleer", null)
                .show()
        },
        onConfidentialTap = { note ->
            if (!NoteAuthManager.isAuthAvailable(activity)) {
                revealNoteTemporarily(note.noteId)
                return@PastoralNoteAdapter
            }
            authManager.authenticate(
                onSuccess = { revealNoteTemporarily(note.noteId) },
                onFailure = { reason ->
                    Snackbar.make(binding.root, reason, Snackbar.LENGTH_SHORT).show()
                }
            )
        }
    )

    // ─── State ───────────────────────────────────────────────────────────────
    private var allPendingUiItems: List<PendingReminderUiItem> = emptyList()
    private var allNotes: List<PastoralNoteEntity> = emptyList()
    private val autoHideTokens = mutableMapOf<String, Runnable>()

    // ─── Setup ───────────────────────────────────────────────────────────────

    fun setup() {
        setupReminderList()
        setupNoteList()
        setupButtons()
        observeData()
    }

    fun cleanup() {
        autoHideTokens.values.forEach { authManager.cancelAutoHide(it) }
        autoHideTokens.clear()
    }

    // ─── UI Setup ────────────────────────────────────────────────────────────

    private fun setupReminderList() {
        binding.detailPendingReminders.apply {
            adapter = miniAdapter
            layoutManager = LinearLayoutManager(activity)
            setHasFixedSize(false)
        }
    }

    private fun setupNoteList() {
        binding.rvDetailNotas.apply {
            adapter = notaAdapter
            layoutManager = LinearLayoutManager(activity)
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }
        binding.layoutDetailNotasInhoud.visibility = View.GONE
        binding.ivDetailNotasChevron.rotation = 0f
    }

    private fun setupButtons() {
        // ── Stel herinnering ──────────────────────────────────────────────────
        binding.detailStelHerinnering.setOnClickListener {
            StelHerinneringBottomSheet
                .newInstance(memberGuid, familyHeadGuid)
                .show(activity.supportFragmentManager, StelHerinneringBottomSheet.TAG)
        }

        // ── Wys al herinneringe ─────────────────────────────────────────────
        binding.btnWysAlHerinneringe.setOnClickListener {
            binding.btnWysAlHerinneringe.tag = "expanded"
            miniAdapter.submitList(allPendingUiItems)   // list of UI items
            binding.btnWysAlHerinneringe.visibility = View.GONE
        }

        // ── Nota header (collapsible) ──────────────────────────────────────
        binding.layoutDetailNotasHeader.setOnClickListener {
            val isExpanded = binding.layoutDetailNotasInhoud.visibility == View.VISIBLE
            if (isExpanded) {
                binding.layoutDetailNotasInhoud.visibility = View.GONE
                binding.ivDetailNotasChevron.animate().rotation(0f).setDuration(200).start()
            } else {
                binding.layoutDetailNotasInhoud.visibility = View.VISIBLE
                binding.ivDetailNotasChevron.animate().rotation(180f).setDuration(200).start()
                renderNotes(allNotes, showAll = false)
            }
        }

        // ── Nuwe nota ────────────────────────────────────────────────────────
        binding.btnDetailNuweNota.setOnClickListener {
            VoegNotaByBottomSheet.newInstance(
                memberGuid = memberGuid,
                familyHeadGuid = familyHeadGuid,
                memberDisplayName = memberDisplayName,
                memberSurname = memberSurname,
                memberGivenName = memberGivenName
            ).show(activity.supportFragmentManager, VoegNotaByBottomSheet.TAG)
        }

        // ── Wys al notas ─────────────────────────────────────────────────────
        binding.btnDetailWysAlNotas.setOnClickListener {
            binding.btnDetailWysAlNotas.tag = "expanded"
            notaAdapter.submitNotes(allNotes)
            binding.btnDetailWysAlNotas.visibility = View.GONE
        }
    }

    // ─── Observers ───────────────────────────────────────────────────────────

    private fun observeData() {
        val scope = activity.lifecycleScope

        // Pending reminders → map to UI items
        scope.launch {
            pastoralViewModel.pendingReminders
                .map { entities -> entities.map { ReminderUiMapper.toUiItem(it) } }
                .collect { uiItems ->
                    allPendingUiItems = uiItems
                    val showAll = binding.btnWysAlHerinneringe.tag == "expanded"
                    val toDisplay = if (showAll) uiItems else uiItems.take(3)
                    miniAdapter.submitList(toDisplay)

                    binding.detailPendingReminders.visibility =
                        if (uiItems.isEmpty()) View.GONE else View.VISIBLE

                    binding.btnWysAlHerinneringe.visibility =
                        if (!showAll && uiItems.size > 3) View.VISIBLE else View.GONE
                    binding.btnWysAlHerinneringe.text =
                        "Wys al ${uiItems.size} herinneringe…"

                    binding.detailHerinneringCount.visibility =
                        if (uiItems.isEmpty()) View.GONE else View.VISIBLE
                    binding.detailHerinneringCount.text =
                        activity.resources.getQuantityString(
                            R.plurals.herinnering_created_count,
                            uiItems.size,
                            uiItems.size
                        )
                }
        }

        // Notes
        scope.launch {
            noteRepo.observeForMember(memberGuid).collect { notes ->
                allNotes = notes
                binding.tvDetailNotaCount.visibility =
                    if (notes.isEmpty()) View.GONE else View.VISIBLE
                binding.tvDetailNotaCount.text = "(${notes.size})"

                if (binding.layoutDetailNotasInhoud.visibility == View.VISIBLE) {
                    renderNotes(notes, showAll = binding.btnDetailWysAlNotas.tag == "expanded")
                }
            }
        }

        // Toast for created reminders
        scope.launch {
            pastoralViewModel.created.collect { count ->
                val msg = activity.resources.getQuantityString(
                    R.plurals.herinnering_created_count, count, count
                )
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }

        // Errors
        scope.launch {
            pastoralViewModel.error.collect { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Render helpers ──────────────────────────────────────────────────────

    private fun renderNotes(notes: List<PastoralNoteEntity>, showAll: Boolean) {
        if (notes.isEmpty()) {
            binding.rvDetailNotas.visibility = View.GONE
            binding.btnDetailWysAlNotas.visibility = View.GONE
            binding.tvDetailGeenNotas.visibility = View.VISIBLE
            return
        }

        binding.tvDetailGeenNotas.visibility = View.GONE
        binding.rvDetailNotas.visibility = View.VISIBLE

        val toDisplay = if (showAll) notes else notes.take(3)
        notaAdapter.submitNotes(toDisplay)

        binding.btnDetailWysAlNotas.visibility =
            if (!showAll && notes.size > 3) View.VISIBLE else View.GONE
        binding.btnDetailWysAlNotas.text =
            if (notes.size > 3) "Wys al ${notes.size} notas…" else "Wys al ${notes.size} notas…"
    }

    // ─── Dialog ──────────────────────────────────────────────────────────────

    /**
     * Fetches the full reminder entity by ID and shows the detail dialog.
     * This is called from the adapter's `onClick` which now passes only the ID.
     */
    private fun showReminderDetailsDialog(reminderId: String) {
        activity.lifecycleScope.launch {
            val reminder = pastoralViewModel.getReminderById(reminderId)
            if (reminder == null) {
                Snackbar.make(binding.root, "Herinnering nie gevind nie", Snackbar.LENGTH_SHORT)
                    .show()
                return@launch
            }

            val dueDate = reminder.dueDateUtc.toLocalDateSafe() ?: LocalDate.now()
            val dateStr =
                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).format(dueDate)
            val isOverdue = dueDate.isBefore(LocalDate.now())

            val details = buildString {
                append("Titel: ${reminder.title}")
                append("\nDatum: $dateStr")
                if (isOverdue) append(" (Agterstallig)")
                if (!reminder.note.isNullOrBlank()) {
                    append("\n\nNota:\n${reminder.note}")
                }
                val contextLine = TemplateContext.from(reminder.contextJson).toDisplayLine()
                if (contextLine != null) {
                    append("\n\nKontekst: $contextLine")
                }
                append("\n\nSkema: ${reminder.scheduleType}")
                append("\nStatus: ${reminder.status}")
            }

            MaterialAlertDialogBuilder(activity)
                .setTitle("Herinnering besonderhede")
                .setMessage(details)
                .setPositiveButton("Sluit", null)
                .show()
        }
    }

    // ─── Biometric reveal ────────────────────────────────────────────────────

    private fun revealNoteTemporarily(noteId: String) {
        autoHideTokens[noteId]?.let { authManager.cancelAutoHide(it) }
        notaAdapter.revealNote(noteId)

        val token = authManager.scheduleAutoHide {
            notaAdapter.hideNote(noteId)
            autoHideTokens.remove(noteId)
        }
        autoHideTokens[noteId] = token
    }
}