package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.databinding.ItemStepEditorBinding

class StepEditorAdapter(
    private val onEdit: (TemplateStepEntity) -> Unit,
    private val onDelete: (TemplateStepEntity) -> Unit
) : RecyclerView.Adapter<StepEditorAdapter.ViewHolder>() {

    private val steps = mutableListOf<TemplateStepEntity>()

    fun submitSteps(newSteps: List<TemplateStepEntity>) {
        steps.clear()
        steps.addAll(newSteps)
        notifyDataSetChanged()
    }

    fun currentSteps(): List<TemplateStepEntity> = steps.toList()

    fun moveItem(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= steps.size || to >= steps.size) return
        val item = steps.removeAt(from)
        steps.add(to, item)
        notifyItemMoved(from, to)
    }

    class ViewHolder(val binding: ItemStepEditorBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemStepEditorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val step = steps[position]
        holder.binding.tvStepTitle.text = step.defaultTitleAf
        holder.binding.tvStepOffset.text = formatOffset(step)
        holder.binding.btnStepEdit.setOnClickListener { onEdit(step) }
        holder.binding.btnStepDelete.setOnClickListener { onDelete(step) }
    }

    override fun getItemCount() = steps.size

    private fun formatOffset(step: TemplateStepEntity): String {
        val parts = mutableListOf<String>()
        if (step.offsetMonths != 0) parts += "${step.offsetMonths}mnd"
        if (step.offsetDays != 0 || parts.isEmpty()) parts += "${step.offsetDays}d"
        val scheduleSuffix = if (ScheduleType.fromStored(step.scheduleType) == ScheduleType.TIMED)
            " · %02d:%02d".format(step.defaultHour ?: 8, step.defaultMinute ?: 0)
        else ""
        return parts.joinToString(" ") + scheduleSuffix
    }
}