package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.databinding.ItemPastoralBackupBinding
import za.co.jpsoft.winkerkreader.utils.db.PastoralDatabaseBackup
import java.time.format.DateTimeFormatter

class BackupListAdapter(
    private val onRestore: (PastoralDatabaseBackup.BackupFileInfo) -> Unit,
    private val onDelete: (PastoralDatabaseBackup.BackupFileInfo) -> Unit
) : RecyclerView.Adapter<BackupListAdapter.ViewHolder>() {

    private var items = listOf<PastoralDatabaseBackup.BackupFileInfo>()

    fun submitList(list: List<PastoralDatabaseBackup.BackupFileInfo>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPastoralBackupBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemPastoralBackupBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

        fun bind(item: PastoralDatabaseBackup.BackupFileInfo) {
            binding.tvName.text = item.displayName
            binding.tvSize.text = "Grootte: ${String.format("%.2f", item.size / 1_048_576.0)} MB"
            binding.tvDate.text = if (item.isLatest) {
                "Huidige rugsteun"
            } else {
                item.date?.format(dateFormatter) ?: "Onbekend"
            }
            binding.btnRestore.setOnClickListener { onRestore(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
            // Versteek delete-knoppie vir die huidige rugsteun (beskerm teen per ongeluk verwyder)
            binding.btnDelete.visibility = if (item.isLatest) View.GONE else View.VISIBLE
        }
    }
}