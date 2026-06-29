package za.co.jpsoft.winkerkreader.ui.bottomsheets

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.entities.PastoralNoteEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.NoteCategory
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralNoteRepository
import za.co.jpsoft.winkerkreader.databinding.BottomSheetVoegNotaByBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class VoegNotaByBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetVoegNotaByBinding? = null
    private val binding get() = _binding!!

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    private var selectedCategory: NoteCategory = NoteCategory.HUISBESOEK
    private var noteDate: LocalDate = LocalDate.now()

    /** Non-null when editing an existing note. */
    private var existingNote: PastoralNoteEntity? = null

    private val isEditMode get() = existingNote != null
    private var isSaving = false
    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetVoegNotaByBinding.inflate(inflater, container, false)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        setupCategoryChips()
        setupDateButton()
        setupNoteText()
        setupSaveButton()
        loadExistingNoteIfEditing()

        ViewCompat.setOnApplyWindowInsetsListener(binding.contentContainer) { v, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val originalPadding = resources.getDimensionPixelSize(R.dimen.bottom_sheet_bottom_padding)
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBarHeight + originalPadding)
            insets
        }
        ViewCompat.requestApplyInsets(binding.contentContainer)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private fun setupHeader() {
        val displayName = requireArguments().getString(ARG_DISPLAY_NAME) ?: ""
        binding.tvNotaMemberName.text = displayName
    }

    // ── Pre-populate for edit mode ─────────────────────────────────────────

    private fun loadExistingNoteIfEditing() {
        val noteId = requireArguments().getString(ARG_EXISTING_NOTE_ID) ?: return

        lifecycleScope.launch {
            val repo = PastoralNoteRepository(requireContext())
            val note = withContext(Dispatchers.IO) { repo.getById(noteId) } ?: return@launch
            existingNote = note

            // Update title
            binding.tvNotaSheetTitle.text = "Redigeer nota"
            binding.btnStoorNota.text = "Stoor wysigings"

            // Pre-populate fields
            binding.etNotaTeks.setText(note.noteText)

            // Date
            noteDate = Instant.ofEpochMilli(note.noteDateUtc)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            binding.btnNotaDatum.text = noteDate.format(dateFormatter)

            // Category
            selectedCategory = NoteCategory.fromStored(note.category)
            refreshChipSelection()

            // Confidential
            binding.switchVertroulik.isChecked = note.isConfidential

            // Hide "stel herinnering" checkbox when editing
            binding.checkStelHerinnering.visibility = View.GONE

            updateSaveButton()
        }
    }

    // ── Category chips ─────────────────────────────────────────────────────

    private fun setupCategoryChips() {
        NoteCategory.entries.forEach { category ->
            val chip = Chip(requireContext()).apply {
                tag = category
                text = "${category.symbol} ${category.labelAf}"
                isCheckable = true
                isChecked = (category == selectedCategory)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedCategory = category
                        for (i in 0 until binding.chipGroupCategory.childCount) {
                            val other = binding.chipGroupCategory.getChildAt(i) as? Chip
                            if (other != this) other?.isChecked = false
                        }
                    }
                }
            }
            binding.chipGroupCategory.addView(chip)
        }
    }

    private fun refreshChipSelection() {
        for (i in 0 until binding.chipGroupCategory.childCount) {
            val chip = binding.chipGroupCategory.getChildAt(i) as? Chip ?: continue
            chip.isChecked = (chip.tag as? NoteCategory) == selectedCategory
        }
    }

    // ── Date button ────────────────────────────────────────────────────────

    private fun setupDateButton() {
        binding.btnNotaDatum.text = noteDate.format(dateFormatter)
        binding.btnNotaDatum.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    noteDate = LocalDate.of(year, month + 1, day)
                    binding.btnNotaDatum.text = noteDate.format(dateFormatter)
                },
                noteDate.year,
                noteDate.monthValue - 1,
                noteDate.dayOfMonth
            ).show()
        }
    }

    // ── Note text ──────────────────────────────────────────────────────────

    private fun setupNoteText() {
        binding.etNotaTeks.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateSaveButton()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // ── Save button ────────────────────────────────────────────────────────

    private fun setupSaveButton() {
        updateSaveButton()

        binding.btnStoorNota.setOnClickListener {
            val noteText = binding.etNotaTeks.text?.toString()?.trim()
            if (noteText.isNullOrBlank()) return@setOnClickListener

            if (isEditMode) saveEdit(noteText) else saveNew(noteText)
        }

        binding.btnKanselleer.setOnClickListener { dismiss() }
    }

    private fun saveNew(noteText: String) {
        val memberGuid     = requireArguments().getString(ARG_MEMBER_GUID) ?: return
        val familyHeadGuid = requireArguments().getString(ARG_FAMILY_HEAD_GUID)
        val surname        = requireArguments().getString(ARG_SURNAME)
        val givenName      = requireArguments().getString(ARG_GIVEN_NAME)
        val displayName    = requireArguments().getString(ARG_DISPLAY_NAME) ?: ""
        val isConfidential = binding.switchVertroulik.isChecked
        isSaving = true
        binding.saveProgress.visibility = View.VISIBLE
        binding.btnStoorNota.isEnabled = false
        lifecycleScope.launch {
            try {
                val repo = PastoralNoteRepository(requireContext())
                val savedNote = withContext(Dispatchers.IO) {
                    repo.save(
                        memberGuid        = memberGuid,
                        familyHeadGuid    = familyHeadGuid,
                        memberSurname     = surname,
                        memberGivenName   = givenName,
                        memberDisplayName = displayName,
                        noteDate          = noteDate,
                        category          = selectedCategory,
                        noteText          = noteText,
                        isConfidential    = isConfidential
                    )
                }

                if (binding.checkStelHerinnering.isChecked) {
                    dismiss()
                    StelHerinneringBottomSheet
                        .newInstance(memberGuid = memberGuid, familyHeadGuid = familyHeadGuid)
                        .show(parentFragmentManager, StelHerinneringBottomSheet.TAG)
                    if (BuildConfig.DEBUG) Log.d(TAG, "Note saved: ${savedNote.noteId}")
                } else {
                    Toast.makeText(requireContext(), getString(R.string.nota_gestoor), Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to save note", e)
                Toast.makeText(requireContext(), getString(R.string.nota_stoor_fout), Toast.LENGTH_SHORT).show()
            } finally {
                isSaving = false
                binding.saveProgress.visibility = View.GONE
                binding.btnStoorNota.isEnabled = true
            }
        }
    }

    private fun saveEdit(noteText: String) {
        val existing = existingNote ?: return
        val isConfidential = binding.switchVertroulik.isChecked
        val noteDateUtc = noteDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        isSaving = true
        binding.saveProgress.visibility = View.VISIBLE
        binding.btnStoorNota.isEnabled = false
        lifecycleScope.launch {
            try {
                val repo = PastoralNoteRepository(requireContext())
                withContext(Dispatchers.IO) {
                    repo.update(
                        existing.copy(
                            noteDateUtc    = noteDateUtc,
                            category       = selectedCategory.name,
                            noteText       = noteText,
                            isConfidential = isConfidential,
                            updatedAt      = System.currentTimeMillis()
                        )
                    )
                }
                Toast.makeText(requireContext(), getString(R.string.nota_gewysig), Toast.LENGTH_SHORT).show()
                dismiss()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to update note", e)
                Toast.makeText(requireContext(), getString(R.string.nota_stoor_fout), Toast.LENGTH_SHORT).show()

            } finally {
                isSaving = false
                binding.saveProgress.visibility = View.GONE
                binding.btnStoorNota.isEnabled = true
            }
        }
    }

    private fun updateSaveButton() {
        binding.btnStoorNota.isEnabled = !binding.etNotaTeks.text.isNullOrBlank()
    }

    // ── Companion ──────────────────────────────────────────────────────────

    companion object {
        const val TAG = "VoegNotaByBottomSheet"

        private const val ARG_MEMBER_GUID       = "arg_member_guid"
        private const val ARG_FAMILY_HEAD_GUID  = "arg_family_head_guid"
        private const val ARG_DISPLAY_NAME      = "arg_display_name"
        private const val ARG_SURNAME           = "arg_surname"
        private const val ARG_GIVEN_NAME        = "arg_given_name"
        private const val ARG_EXISTING_NOTE_ID  = "arg_existing_note_id"

        /** Open in create mode. */
        fun newInstance(
            memberGuid: String,
            familyHeadGuid: String?  = null,
            memberDisplayName: String = "",
            memberSurname: String?   = null,
            memberGivenName: String? = null
        ) = VoegNotaByBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_MEMBER_GUID,      memberGuid)
                putString(ARG_FAMILY_HEAD_GUID, familyHeadGuid)
                putString(ARG_DISPLAY_NAME,     memberDisplayName)
                putString(ARG_SURNAME,          memberSurname)
                putString(ARG_GIVEN_NAME,       memberGivenName)
            }
        }

        /** Open in edit mode — pre-populates all fields from [existingNoteId]. */
        fun newInstanceForEdit(
            existingNoteId: String,
            memberDisplayName: String? = ""
        ) = VoegNotaByBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_EXISTING_NOTE_ID, existingNoteId)
                putString(ARG_DISPLAY_NAME,     memberDisplayName)
            }
        }
    }
}
