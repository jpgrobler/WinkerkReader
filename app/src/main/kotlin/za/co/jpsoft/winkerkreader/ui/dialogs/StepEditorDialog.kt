package za.co.jpsoft.winkerkreader.ui.dialogs

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.databinding.DialogStepEditorBinding

class StepEditorDialog : DialogFragment() {

    private var _binding: DialogStepEditorBinding? = null
    private val binding get() = _binding!!

    private var editingStep: TemplateStepEntity? = null
    private var defaultHour = 8
    private var defaultMinute = 0

    var onSave: ((
        offsetDays: Int,
        offsetMonths: Int,
        titleAf: String,
        noteAf: String?,
        scheduleType: ScheduleType,
        hour: Int?,
        minute: Int?
    ) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogStepEditorBinding.inflate(layoutInflater)

        editingStep = arguments?.getString(ARG_STEP_JSON)
            ?.let { /* not used — passed via setter below */ null }

        // Prefill if editing
        stepBeingEdited?.let { step ->
            binding.etTitle.setText(step.defaultTitleAf)
            binding.etNote.setText(step.defaultNoteAf)
            binding.etOffsetDays.setText(step.offsetDays.toString())
            binding.etOffsetMonths.setText(step.offsetMonths.toString())
            val isTimed = ScheduleType.fromStored(step.scheduleType) == ScheduleType.TIMED
            binding.switchTimed.isChecked = isTimed
            defaultHour = step.defaultHour ?: 8
            defaultMinute = step.defaultMinute ?: 0
            updateTimeButtonText()
            binding.btnPickTime.visibility = if (isTimed) View.VISIBLE else View.GONE
        }

        binding.switchTimed.setOnCheckedChangeListener { _, checked ->
            binding.btnPickTime.visibility = if (checked) View.VISIBLE else View.GONE
        }

        binding.btnPickTime.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    defaultHour = hour
                    defaultMinute = minute
                    updateTimeButtonText()
                },
                defaultHour, defaultMinute, true
            ).show()
        }
        updateTimeButtonText()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (stepBeingEdited != null) R.string.template_wysig_stap
                else R.string.template_nuwe_stap
            )
            .setView(binding.root)
            .setPositiveButton(R.string.herinnering_bevestig) { _, _ -> save() }
            .setNegativeButton(R.string.pastoral_import_nee, null)
            .create()
    }

    private fun save() {
        val title = binding.etTitle.text?.toString()?.trim()
        if (title.isNullOrBlank()) return

        val offsetDays = binding.etOffsetDays.text?.toString()?.toIntOrNull() ?: 0
        val offsetMonths = binding.etOffsetMonths.text?.toString()?.toIntOrNull() ?: 0
        val note = binding.etNote.text?.toString()?.trim()?.ifBlank { null }
        val isTimed = binding.switchTimed.isChecked

        onSave?.invoke(
            offsetDays,
            offsetMonths,
            title,
            note,
            if (isTimed) ScheduleType.TIMED else ScheduleType.DATE_ONLY,
            if (isTimed) defaultHour else null,
            if (isTimed) defaultMinute else null
        )
    }

    private fun updateTimeButtonText() {
        binding.btnPickTime.text = "%02d:%02d".format(defaultHour, defaultMinute)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_STEP_JSON = "arg_step_json"
        private var stepBeingEdited: TemplateStepEntity? =
            null   // simplest carrier; cleared on use

        /** For adding a new step. */
        fun newInstance(): StepEditorDialog {
            stepBeingEdited = null
            return StepEditorDialog()
        }

        /** For editing an existing step. */
        fun editInstance(step: TemplateStepEntity): StepEditorDialog {
            stepBeingEdited = step
            return StepEditorDialog()
        }
    }
}