package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.models.PendingReminderUiItem
import za.co.jpsoft.winkerkreader.databinding.ItemPendingReminderMiniBinding
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PendingReminderMiniAdapter(
    private val onComplete: (reminderId: String) -> Unit,
    private val onClick: (reminderId: String) -> Unit   // now passes only the ID
) : ListAdapter<PendingReminderUiItem, PendingReminderMiniAdapter.ViewHolder>(DIFF) {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val zoneId = ZoneId.systemDefault()

    inner class ViewHolder(
        private val binding: ItemPendingReminderMiniBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PendingReminderUiItem) {
            val symbol = item.symbol?.takeIf { it.isNotBlank() } ?: ""
            var titleText = if (symbol.isNotEmpty()) "$symbol${item.title}" else item.title

            if (item.contextSuffix != null) {
                titleText = "$titleText · ${item.contextSuffix}"
            }

            binding.tvMiniTitle.text = titleText

            val today = LocalDate.now(zoneId)
            binding.tvMiniDate.text = when {
                item.dueDate == today -> binding.root.context.getString(R.string.datum_vandag)
                item.dueDate == today.minusDays(1) -> binding.root.context.getString(R.string.datum_gister)
                else -> item.dueDate.format(dateFormatter)
            }

            binding.tvMiniOverdue.visibility = if (item.isOverdue) View.VISIBLE else View.GONE

            binding.btnMiniVoltooi.setOnClickListener {
                onComplete(item.reminderId)
            }

            binding.root.setOnClickListener {
                onClick(item.reminderId)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemPendingReminderMiniBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PendingReminderUiItem>() {
            override fun areItemsTheSame(a: PendingReminderUiItem, b: PendingReminderUiItem) =
                a.reminderId == b.reminderId

            override fun areContentsTheSame(a: PendingReminderUiItem, b: PendingReminderUiItem) =
                a == b
        }
    }
}