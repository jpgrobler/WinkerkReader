package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateWithSteps
import za.co.jpsoft.winkerkreader.databinding.ItemTemplatePickerBinding

class TemplatePickerAdapter(
    private val onSelected: (TemplateWithSteps?) -> Unit
) : RecyclerView.Adapter<TemplatePickerAdapter.ViewHolder>() {

    // Full list of all templates (never filtered)
    private var fullList: List<TemplateWithSteps> = emptyList()

    // Currently displayed list (filtered based on selection)
    private var displayList: List<TemplateWithSteps> = emptyList()
    private var selectedId: String? = null

    /**
     * Submit the complete list of templates.
     * The adapter will filter it based on the current selection state.
     */
    fun submitList(list: List<TemplateWithSteps>) {
        fullList = list
        updateDisplayList()
    }

    private fun updateDisplayList() {
        displayList = if (selectedId != null) {
            // Show only the selected template
            fullList.filter { it.template.templateId == selectedId }
        } else {
            // Show all
            fullList
        }
        notifyDataSetChanged()
    }

    /**
     * Toggle selection for a template.
     * If already selected, deselect; otherwise select it.
     */
    private fun toggleSelection(item: TemplateWithSteps) {
        val id = item.template.templateId
        val newSelectedId = if (selectedId == id) null else id
        selectedId = newSelectedId
        updateDisplayList()
        // Notify listener with the selected template (or null if deselected)
        val selected = if (selectedId != null) {
            fullList.find { it.template.templateId == selectedId }
        } else null
        onSelected(selected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTemplatePickerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = displayList[position]
        holder.bind(item, selectedId == item.template.templateId)
        holder.itemView.setOnClickListener {
            toggleSelection(item)
        }
    }

    override fun getItemCount(): Int = displayList.size

    class ViewHolder(
        private val binding: ItemTemplatePickerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateWithSteps, isSelected: Boolean) {
            val symbol = item.template.symbol ?: ""
            val title = item.template.titleAf
            binding.tvTemplateTitle.text = "$symbol $title"
            binding.tvTemplateDescription.text = item.template.descriptionAf
            binding.tvTemplateSteps.text =
                binding.root.context.resources.getQuantityString(
                    R.plurals.herinnering_stap_count,
                    item.steps.size,
                    item.steps.size
                )
            // Radio button state
            binding.rbTemplate.isChecked = isSelected
        }
    }
}