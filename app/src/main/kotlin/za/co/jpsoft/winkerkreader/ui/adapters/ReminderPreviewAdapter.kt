package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.ItemReminderPreviewBinding
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReminderPreviewAdapter :
    ListAdapter<LidmaatDetailPastoralViewModel.PreviewItem, ReminderPreviewAdapter.ViewHolder>(DIFF) {

    private val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    inner class ViewHolder(
        private val binding: ItemReminderPreviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LidmaatDetailPastoralViewModel.PreviewItem) {
            binding.tvPreviewTitle.text = item.stepTitle
            binding.tvPreviewDate.text = item.dueDate.format(formatter)

            // Grey out past dates — warn but still allow creation
            val alpha = if (item.isInPast) 0.4f else 1f
            binding.tvPreviewTitle.alpha = alpha
            binding.tvPreviewDate.alpha = alpha
            binding.viewDot.alpha = alpha
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemReminderPreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LidmaatDetailPastoralViewModel.PreviewItem>() {
            override fun areItemsTheSame(
                a: LidmaatDetailPastoralViewModel.PreviewItem,
                b: LidmaatDetailPastoralViewModel.PreviewItem
            ) = a.stepTitle == b.stepTitle && a.dueDate == b.dueDate
            override fun areContentsTheSame(
                a: LidmaatDetailPastoralViewModel.PreviewItem,
                b: LidmaatDetailPastoralViewModel.PreviewItem
            ) = a == b
        }
    }
}