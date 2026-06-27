package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateWithSteps
import za.co.jpsoft.winkerkreader.databinding.ItemTemplateManagerBinding

class TemplateManagerAdapter(
    private val onOpen: (templateId: String) -> Unit,
    private val onToggleActive: (templateId: String, isActive: Boolean) -> Unit,
    private val onDelete: (templateId: String, titleAf: String) -> Unit
) : ListAdapter<TemplateWithSteps, TemplateManagerAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemTemplateManagerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemTemplateManagerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val template = item.template

        holder.binding.tvTitle.text = template.titleAf
        holder.binding.tvStepCount.text = holder.binding.root.context.resources.getQuantityString(
            R.plurals.herinnering_stap_count, item.steps.size, item.steps.size
        )
        holder.binding.tvSystemBadge.visibility = if (template.isSystem) View.VISIBLE else View.GONE
        holder.binding.switchActive.setOnCheckedChangeListener(null)
        holder.binding.switchActive.isChecked = template.isActive
        holder.binding.switchActive.setOnCheckedChangeListener { _, checked ->
            onToggleActive(template.templateId, checked)
        }
        holder.binding.btnDelete.visibility = if (template.isSystem) View.GONE else View.VISIBLE
        holder.binding.btnDelete.setOnClickListener { onDelete(template.templateId, template.titleAf) }
        holder.binding.root.setOnClickListener { onOpen(template.templateId) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TemplateWithSteps>() {
            override fun areItemsTheSame(a: TemplateWithSteps, b: TemplateWithSteps) =
                a.template.templateId == b.template.templateId
            override fun areContentsTheSame(a: TemplateWithSteps, b: TemplateWithSteps) = a == b
        }
    }
}