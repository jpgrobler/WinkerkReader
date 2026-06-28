package za.co.jpsoft.winkerkreader.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import za.co.jpsoft.winkerkreader.databinding.ActivityTemplateEditorBinding
import za.co.jpsoft.winkerkreader.ui.adapters.StepEditorAdapter
import za.co.jpsoft.winkerkreader.ui.dialogs.StepEditorDialog

class TemplateEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTemplateEditorBinding
    private lateinit var repository: PastoralReminderRepository
    private lateinit var templateId: String
    private lateinit var stepAdapter: StepEditorAdapter
    private var isSystemTemplate = false
    private var templateCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplateEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID)
            ?: run { finish(); return }

        repository = PastoralReminderRepository.create(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupStepList()
        loadTemplate()

        binding.btnAddStep.setOnClickListener {
            StepEditorDialog.newInstance().apply {
                onSave = { offsetDays, offsetMonths, title, note, scheduleType, hour, minute ->
                    lifecycleScope.launch {
                        repository.addStep(
                            templateId, offsetDays, offsetMonths, title, note,
                            scheduleType, hour, minute
                        )
                        loadTemplate()
                    }
                }
            }.show(supportFragmentManager, "step_add")
        }

        binding.btnSaveMeta.setOnClickListener { saveMeta() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_template_editor, menu)
        menu.findItem(R.id.action_reset_default)?.isVisible = isSystemTemplate
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_reset_default -> { confirmResetToDefault(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun setupStepList() {
        stepAdapter = StepEditorAdapter(
            onEdit = { step -> editStep(step) },
            onDelete = { step -> confirmDeleteStep(step) }
        )
        binding.rvSteps.apply {
            adapter = stepAdapter
            layoutManager = LinearLayoutManager(this@TemplateEditorActivity)
        }

        // Drag-to-reorder
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: androidx.recyclerview.widget.RecyclerView,
                source: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean {
                stepAdapter.moveItem(source.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: androidx.recyclerview.widget.RecyclerView, vh: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                lifecycleScope.launch {
                    repository.reorderSteps(stepAdapter.currentSteps())
                }
            }
        })
        touchHelper.attachToRecyclerView(binding.rvSteps)
    }

    private fun loadTemplate() {
        lifecycleScope.launch {
            val data = repository.getTemplateWithSteps(templateId) ?: return@launch
            isSystemTemplate = data.template.isSystem
            templateCode = data.template.code
            binding.etTemplateTitle.setText(data.template.titleAf)
            binding.etTemplateDescription.setText(data.template.descriptionAf)
            stepAdapter.submitSteps(data.steps.sortedBy { it.stepOrder })
            binding.etTemplateSymbol.setText(data.template.symbol ?: "")
            invalidateOptionsMenu()
        }
    }

    private fun saveMeta() {
        val title = binding.etTemplateTitle.text?.toString()?.trim()
        if (title.isNullOrBlank()) return
        val symbol = binding.etTemplateSymbol.text?.toString()?.trim()?.ifBlank { null }
        lifecycleScope.launch {
            repository.updateTemplateMeta(
                templateId,
                title,
                binding.etTemplateDescription.text?.toString(),
                symbol
            )
            Snackbar.make(binding.root, getString(R.string.template_gestoor), Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun editStep(step: TemplateStepEntity) {
        StepEditorDialog.editInstance(step).apply {
            onSave = { offsetDays, offsetMonths, title, note, scheduleType, hour, minute ->
                lifecycleScope.launch {
                    repository.updateStep(
                        step.copy(
                            offsetDays     = offsetDays,
                            offsetMonths   = offsetMonths,
                            defaultTitleAf = title,
                            defaultNoteAf  = note,
                            scheduleType   = scheduleType.name,
                            defaultHour    = hour,
                            defaultMinute  = minute
                        )
                    )
                    loadTemplate()
                }
            }
        }.show(supportFragmentManager, "step_edit")
    }

    private fun confirmDeleteStep(step: TemplateStepEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.template_verwyder_stap_titel)
            .setMessage(R.string.template_verwyder_stap_boodskap)
            .setPositiveButton(R.string.pastoral_import_ja) { _, _ ->
                lifecycleScope.launch {
                    repository.deleteStep(step.stepId)
                    loadTemplate()
                }
            }
            .setNegativeButton(R.string.pastoral_import_nee, null)
            .show()
    }

    private fun confirmResetToDefault() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.template_herstel_titel)
            .setMessage(R.string.template_herstel_boodskap)
            .setPositiveButton(R.string.pastoral_import_ja) { _, _ ->
                lifecycleScope.launch {
                    repository.resetTemplateToDefault(templateId)
                    loadTemplate()
                    Snackbar.make(binding.root, getString(R.string.template_herstel_sukses), Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.pastoral_import_nee, null)
            .show()
    }

    companion object {
        private const val EXTRA_TEMPLATE_ID = "extra_template_id"

        fun launch(context: Context, templateId: String) {
            context.startActivity(
                Intent(context, TemplateEditorActivity::class.java)
                    .putExtra(EXTRA_TEMPLATE_ID, templateId)
            )
        }
    }
}