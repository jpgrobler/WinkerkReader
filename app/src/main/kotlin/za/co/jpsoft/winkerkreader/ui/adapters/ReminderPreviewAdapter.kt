package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.ItemReminderPreviewBinding
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReminderPreviewAdapter(
    private val onToggleSelected: (LidmaatDetailPastoralViewModel.PreviewItem) -> Unit,
    private val onDateClick: (LidmaatDetailPastoralViewModel.PreviewItem) -> Unit
) : ListAdapter<LidmaatDetailPastoralViewModel.PreviewItem, ReminderPreviewAdapter.ViewHolder>(DIFF) {

    private val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    inner class ViewHolder(
        private val binding: ItemReminderPreviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LidmaatDetailPastoralViewModel.PreviewItem) {
            binding.tvPreviewTitle.text = item.stepTitle
            binding.tvPreviewDate.text = item.dueDate.format(formatter)

            // Filled dot = selected, hollow dot = deselected. Reminders that were
            // already overdue when the preview was built start deselected — see
            // LidmaatDetailPastoralViewModel.previewTemplateDates().
            binding.viewDot.setBackgroundResource(
                if (item.isSelected) R.drawable.bg_badge_red else R.drawable.bg_dot_outline
            )

            // Dim the whole row when deselected; a selected-but-overdue reminder
            // (the user opted back in) still shows at full opacity.
            val alpha = if (item.isSelected) 1f else 0.4f
            binding.tvPreviewTitle.alpha = alpha
            binding.tvPreviewDate.alpha = alpha
            binding.viewDot.alpha = alpha

            binding.dotTouchTarget.setOnClickListener { onToggleSelected(item) }
            binding.root.setOnClickListener { onDateClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemReminderPreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF =
            object : DiffUtil.ItemCallback<LidmaatDetailPastoralViewModel.PreviewItem>() {
                override fun areItemsTheSame(
                    a: LidmaatDetailPastoralViewModel.PreviewItem,
                    b: LidmaatDetailPastoralViewModel.PreviewItem
                ) = a.stepId == b.stepId

                override fun areContentsTheSame(
                    a: LidmaatDetailPastoralViewModel.PreviewItem,
                    b: LidmaatDetailPastoralViewModel.PreviewItem
                ) = a == b
            }
    }
}