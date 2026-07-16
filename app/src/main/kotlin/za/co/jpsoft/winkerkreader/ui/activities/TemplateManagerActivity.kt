package za.co.jpsoft.winkerkreader.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import za.co.jpsoft.winkerkreader.databinding.ActivityTemplateManagerBinding
import za.co.jpsoft.winkerkreader.ui.adapters.TemplateManagerAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.TemplateManagerViewModel
import za.co.jpsoft.winkerkreader.utils.MainNavigationController

class TemplateManagerActivity : BaseActivity() {

    private lateinit var binding: ActivityTemplateManagerBinding
    private lateinit var adapter: TemplateManagerAdapter

    private val viewModel: TemplateManagerViewModel by viewModels {
        TemplateManagerViewModel.Factory(PastoralReminderRepository.create(this))
    }

    private val navigationController by lazy { MainNavigationController(this) }
    private val _isLoading = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplateManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = TemplateManagerAdapter(
            onOpen = { templateId -> navigationController.navigateToTemplateEditor(templateId) },
            onToggleActive = { templateId, isActive -> viewModel.setActive(templateId, isActive) },
            onDelete = { templateId, titleAf -> confirmDelete(templateId, titleAf) }
        )
        binding.rvTemplates.apply {
            adapter = this@TemplateManagerActivity.adapter
            layoutManager = LinearLayoutManager(this@TemplateManagerActivity)
        }

        binding.fabNewTemplate.setOnClickListener { showCreateDialog() }

        lifecycleScope.launch {
            viewModel.templates.collect { templates ->
                adapter.submitList(templates)
            }
        }
        lifecycleScope.launch {
            viewModel.templateCreated.collect { templateId ->
                navigationController.navigateToTemplateEditor(templateId)
            }
        }
        lifecycleScope.launch {
            viewModel.error.collect { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            }
        }
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showCreateDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_new_template, null)
        val etTitle = view.findViewById<android.widget.EditText>(R.id.et_new_template_title)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.template_nuwe_sjabloon)
            .setView(view)
            .setPositiveButton(R.string.template_skep) { _, _ ->
                val title = etTitle.text?.toString()?.trim()
                if (!title.isNullOrBlank()) {
                    viewModel.createTemplate(title, null)
                }
            }
            .setNegativeButton(R.string.pastoral_import_nee, null)
            .show()
    }

    private fun confirmDelete(templateId: String, titleAf: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.template_verwyder_titel)
            .setMessage(getString(R.string.template_verwyder_boodskap, titleAf))
            .setPositiveButton(R.string.pastoral_import_ja) { _, _ ->
                viewModel.deletePermanently(templateId)
            }
            .setNegativeButton(R.string.pastoral_import_nee, null)
            .show()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, TemplateManagerActivity::class.java))
        }
    }
}