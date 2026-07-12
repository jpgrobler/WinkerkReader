package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContext
import za.co.jpsoft.winkerkreader.databinding.ItemPendingReminderMiniBinding
import za.co.jpsoft.winkerkreader.utils.Utils.toLocalDateSafe
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PendingReminderMiniAdapter(
    private val onComplete: (reminderId: String) -> Unit,
    private val onClick: (reminder: FollowUpReminderEntity) -> Unit
) : ListAdapter<FollowUpReminderEntity, PendingReminderMiniAdapter.ViewHolder>(DIFF) {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val zoneId = ZoneId.systemDefault()

    inner class ViewHolder(
        private val binding: ItemPendingReminderMiniBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FollowUpReminderEntity) {
            // Get symbol (if any) and build the title
            val symbol = item.symbol?.takeIf { it.isNotBlank() } ?: ""
            var titleText = if (symbol.isNotEmpty()) "$symbol${item.title}" else item.title

            // Append context suffix if available
            val contextSuffix = TemplateContext.from(item.contextJson).let { ctx ->
                ctx.getString("hospital")
                    ?: ctx.getString("deceasedName")
                    ?: ctx.getString("illness")
                    ?: ctx.getString("traumaType")
            }
            if (contextSuffix != null) {
                titleText = "$titleText · $contextSuffix"
            }

            binding.tvMiniTitle.text = titleText

            // Due date
            val dueDate = item.dueDateUtc.toLocalDateSafe() ?: LocalDate.now()
            val today = LocalDate.now(zoneId)
            val isOverdue = dueDate.isBefore(today)

            binding.tvMiniDate.text = when {
                dueDate == today -> binding.root.context.getString(R.string.datum_vandag)
                dueDate == today.minusDays(1) -> binding.root.context.getString(R.string.datum_gister)
                else -> dueDate.format(dateFormatter)
            }

            binding.tvMiniOverdue.visibility = if (isOverdue) View.VISIBLE else View.GONE

            binding.btnMiniVoltooi.setOnClickListener {
                onComplete(item.reminderId)
            }

            // Click on the whole item to show details
            binding.root.setOnClickListener {
                onClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemPendingReminderMiniBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FollowUpReminderEntity>() {
            override fun areItemsTheSame(a: FollowUpReminderEntity, b: FollowUpReminderEntity) =
                a.reminderId == b.reminderId

            override fun areContentsTheSame(a: FollowUpReminderEntity, b: FollowUpReminderEntity) =
                a == b
        }
    }
}