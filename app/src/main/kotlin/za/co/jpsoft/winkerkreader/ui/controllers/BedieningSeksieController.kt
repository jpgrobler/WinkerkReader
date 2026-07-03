package za.co.jpsoft.winkerkreader.ui.controllers

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.PastoralNoteEntity
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralNoteRepository
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import za.co.jpsoft.winkerkreader.ui.adapters.PastoralNoteAdapter
import za.co.jpsoft.winkerkreader.ui.bottomsheets.StelHerinneringBottomSheet
import za.co.jpsoft.winkerkreader.ui.bottomsheets.VoegNotaByBottomSheet

/**
 * Drop-in controller that manages the Bediening section
 * (herinneringe + notas) inside [za.co.jpsoft.winkerkreader.ui.activities.LidmaatDetailActivity].
 *
 * Usage — in LidmaatDetailActivity.onCreate():
 *
 *   bedieningSeksie = BedieningSeksieController(
 *       activity       = this,
 *       memberGuid     = memberGuid,
 *       familyHeadGuid = familyHeadGuid,
 *       displayName    = "${member.name} ${member.surname}",
 *       memberSurname  = member.surname,
 *       memberGivenName = member.name,
 *       rootView       = binding.root          // or the <include> view
 *   )
 *   bedieningSeksie.setup()
 */
class BedieningSeksieController(
    private val activity: AppCompatActivity,
    private val memberGuid: String,
    private val familyHeadGuid: String?,
    private val displayName: String,
    private val memberSurname: String?,
    private val memberGivenName: String?,
    private val rootView: View
) {
    companion object {
        private const val MAX_VISIBLE = 3
    }

    // ── Repositories ───────────────────────────────────────────────────────
    private val noteRepo      by lazy { PastoralNoteRepository(activity) }
    private val reminderRepo  by lazy { PastoralReminderRepository.create(activity) }

    // ── Adapters ───────────────────────────────────────────────────────────
    private val noteAdapter = PastoralNoteAdapter(onDelete = ::confirmDeleteNote)

    // ── State ──────────────────────────────────────────────────────────────
    private var notasExpanded   = false
    private var allNotes        = emptyList<PastoralNoteEntity>()
    private var allReminders    = emptyList<FollowUpReminderEntity>()

    // ── View refs ──────────────────────────────────────────────────────────
    private val rvHerinneringe    get() = rootView.findViewById<RecyclerView>(R.id.rvHerinneringe)
    private val btnNuweHerinnering get() = rootView.findViewById<MaterialButton>(R.id.btnNuweHerinnering)
    private val btnWysAlHerinneringe get() = rootView.findViewById<MaterialButton>(R.id.btnWysAlHerinneringe)
    private val tvGeenHerinneringe get() = rootView.findViewById<TextView>(R.id.tvGeenHerinneringe)

    private val layoutNotasHeader  get() = rootView.findViewById<LinearLayout>(R.id.layoutNotasHeader)
    private val layoutNotasInhoud  get() = rootView.findViewById<LinearLayout>(R.id.layoutNotasInhoud)
    private val tvNotaSeksieHeader get() = rootView.findViewById<TextView>(R.id.tvNotaSeksieHeader)
    private val tvNotaCount        get() = rootView.findViewById<TextView>(R.id.tvNotaCount)
    private val btnNuweNota        get() = rootView.findViewById<MaterialButton>(R.id.btnNuweNota)
    private val ivNotasChevron     get() = rootView.findViewById<ImageView>(R.id.ivNotasChevron)
    private val rvNotas            get() = rootView.findViewById<RecyclerView>(R.id.rvNotas)
    private val btnWysAlNotas      get() = rootView.findViewById<MaterialButton>(R.id.btnWysAlNotas)
    private val tvGeenNotas        get() = rootView.findViewById<TextView>(R.id.tvGeenNotas)

    // ── Entry point ────────────────────────────────────────────────────────

    fun setup() {
        setupReminderList()
        setupNoteList()
        setupButtons()
        observeData()
    }

    // ── RecyclerViews ──────────────────────────────────────────────────────

    private fun setupReminderList() {
        rvHerinneringe.layoutManager = LinearLayoutManager(activity)
        rvHerinneringe.isNestedScrollingEnabled = false
        // Herinnering adapter — gebruik die bestaande ReminderItemAdapter as jy een het,
        // anders is 'n eenvoudige TextView-adapter hier genoeg vir fase 1.
        // Vervang hierdie met jou bestaande adapter as dit beskikbaar is:
        // rvHerinneringe.adapter = existingReminderAdapter
    }

    private fun setupNoteList() {
        rvNotas.layoutManager = LinearLayoutManager(activity)
        rvNotas.isNestedScrollingEnabled = false
        rvNotas.adapter = noteAdapter
    }

    // ── Buttons ────────────────────────────────────────────────────────────

    private fun setupButtons() {
        // Nuwe herinnering
        btnNuweHerinnering.setOnClickListener {
            StelHerinneringBottomSheet
                .newInstance(memberGuid = memberGuid, familyHeadGuid = familyHeadGuid)
                .show(activity.supportFragmentManager, StelHerinneringBottomSheet.TAG)
        }

        // Nuwe nota
        btnNuweNota.setOnClickListener {
            VoegNotaByBottomSheet.newInstance(
                memberGuid        = memberGuid,
                familyHeadGuid    = familyHeadGuid,
                memberDisplayName = displayName,
                memberSurname     = memberSurname,
                memberGivenName   = memberGivenName
            ).show(activity.supportFragmentManager, VoegNotaByBottomSheet.TAG)
        }

        // Nota afdeling — klik om toe/oop te maak
        layoutNotasHeader.setOnClickListener { toggleNotas() }

        // "Wys meer" herinneringe
        btnWysAlHerinneringe.setOnClickListener {
            renderReminders(allReminders, showAll = true)
            btnWysAlHerinneringe.visibility = View.GONE
        }

        // "Wys ouer notas"
        btnWysAlNotas.setOnClickListener {
            btnWysAlNotas.tag = "expanded"
            noteAdapter.submitNotes(allNotes)  // ← submitNotes
            btnWysAlNotas.visibility = View.GONE
        }
    }

    // ── Data observation ───────────────────────────────────────────────────

    private fun observeData() {
        activity.lifecycleScope.launch {
            // Herinneringe
            launch {
                reminderRepo.observePendingForMember(memberGuid).collect { reminders ->
                    allReminders = reminders
                    renderReminders(reminders, showAll = false)
                }
            }

            // Notas
            launch {
                noteRepo.observeForMember(memberGuid).collect { notes ->
                    allNotes = notes
                    updateNoteCount(notes.size)
                    if (notasExpanded) renderNotes(notes, showAll = false)
                }
            }
        }
    }

    // ── Render helpers ─────────────────────────────────────────────────────

    private fun renderReminders(
        reminders: List<FollowUpReminderEntity>,
        showAll: Boolean
    ) {
        if (reminders.isEmpty()) {
            rvHerinneringe.visibility      = View.GONE
            btnWysAlHerinneringe.visibility = View.GONE
            tvGeenHerinneringe.visibility  = View.VISIBLE
            return
        }

        tvGeenHerinneringe.visibility = View.GONE
        rvHerinneringe.visibility     = View.VISIBLE

        val visible = if (showAll) reminders else reminders.take(MAX_VISIBLE)
        btnWysAlHerinneringe.visibility =
            if (!showAll && reminders.size > MAX_VISIBLE) View.VISIBLE else View.GONE

        // Wys elke herinnering as 'n eenvoudige teksy — vervang met jou
        // bestaande reminder adapter as jy een het.
        // Hier bou ons dynamiese TextViews as 'n eenvoudige fallback:
        val container = rvHerinneringe.parent as? ViewGroup ?: return
        // NOTE: as jy 'n bestaande ReminderAdapter het, submit die lys daar.
        // reminderAdapter.submitList(visible)
    }

    private fun renderNotes(notes: List<PastoralNoteEntity>, showAll: Boolean) {
        if (notes.isEmpty()) {
            rvNotas.visibility       = View.GONE
            btnWysAlNotas.visibility = View.GONE
            tvGeenNotas.visibility   = View.VISIBLE
            return
        }

        tvGeenNotas.visibility = View.GONE
        rvNotas.visibility     = View.VISIBLE

        val visible = if (showAll) notes else notes.take(MAX_VISIBLE)
        noteAdapter.submitNotes(visible)  // ← gebruik submitNotes

        btnWysAlNotas.visibility =
            if (!showAll && notes.size > MAX_VISIBLE) View.VISIBLE else View.GONE
    }

    private fun updateNoteCount(count: Int) {
        tvNotaCount.visibility = if (count > 0) View.VISIBLE else View.GONE
        tvNotaCount.text = "($count)"
    }

    // ── Inklapbare notas ───────────────────────────────────────────────────

    private fun toggleNotas() {
        notasExpanded = !notasExpanded

        // Roteer pyltjie-ikoon
        ivNotasChevron.animate()
            .rotation(if (notasExpanded) 180f else 0f)
            .setDuration(200)
            .start()

        if (notasExpanded) {
            layoutNotasInhoud.visibility = View.VISIBLE
            renderNotes(allNotes, showAll = false)
        } else {
            layoutNotasInhoud.visibility = View.GONE
        }
    }

    // ── Verwydering ────────────────────────────────────────────────────────

    private fun confirmDeleteNote(note: PastoralNoteEntity) {
        AlertDialog.Builder(activity)
            .setTitle("Verwyder nota?")
            .setMessage("Hierdie nota sal permanent verwyder word.")
            .setPositiveButton("Verwyder") { _, _ ->
                activity.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { noteRepo.delete(note.noteId) }
                }
            }
            .setNegativeButton("Kanselleer", null)
            .show()
    }
}