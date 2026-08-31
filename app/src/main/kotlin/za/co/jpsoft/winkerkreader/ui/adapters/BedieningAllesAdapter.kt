package za.co.jpsoft.winkerkreader.ui.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.VandagAllesItem
import za.co.jpsoft.winkerkreader.databinding.ItemBedieningReminderBinding
import za.co.jpsoft.winkerkreader.utils.Utils.toLocalDateSafe
import za.co.jpsoft.winkerkreader.utils.files.PhotoHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Adapter for reminder items in "Vandag (Alles)" and "Vandag" tabs.
 * Handles reminders with proper overdue background coloring.
 */
class BedieningAllesAdapter(
    private val onCallMember: (String, String) -> Unit,
    private val onSendSms: (String, String) -> Unit,
    private val onWhatsApp: (VandagAllesItem.Celebration) -> Unit,
    private val onAddNote: (String, String) -> Unit,
    private val onSetReminder: (String) -> Unit,
    private val onOpenMember: (String) -> Unit,
    // Reminder actions
    private val onComplete: (String) -> Unit,
    private val onSnooze: (String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onDeleteSeries: (String) -> Unit,
    private val onAddCalendar: (String) -> Unit,
    private val onAddGoogleTask: (String) -> Unit
) : ListAdapter<VandagAllesItem.Reminder, BedieningAllesAdapter.ReminderViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VandagAllesItem.Reminder>() {
            override fun areItemsTheSame(
                old: VandagAllesItem.Reminder,
                new: VandagAllesItem.Reminder
            ): Boolean {
                return old.reminderWithMember.reminder.reminderId == new.reminderWithMember.reminder.reminderId
            }

            override fun areContentsTheSame(
                old: VandagAllesItem.Reminder,
                new: VandagAllesItem.Reminder
            ): Boolean =
                old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemBedieningReminderBinding.inflate(inflater, parent, false)
        return ReminderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReminderViewHolder(
        private val binding: ItemBedieningReminderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val zoneId = ZoneId.systemDefault()
        private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

        fun bind(reminderItem: VandagAllesItem.Reminder) {
            val reminderWithMember = reminderItem.reminderWithMember
            val reminder = reminderWithMember.reminder
            val displayName = reminderWithMember.displayName

            // Basic info
            binding.tvReminderTitle.text = reminder.title
            binding.tvMemberName.text = displayName
            binding.tvDueDate.text = formatDueDate(reminder)

            // ========== OVERDUE CHECK & BACKGROUND COLOR ==========
            val startOfTodayUtc =
                LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val isOverdue = reminder.dueDateUtc < startOfTodayUtc

            // Show/hide overdue badge
            binding.tvOverdueBadge.visibility = if (isOverdue) View.VISIBLE else View.GONE

            // Apply background color based on overdue status
            val context = binding.root.context
            if (isOverdue) {
                // Light red background for overdue items
                val errorContainerColor =
                    ContextCompat.getColor(context, R.color.md_theme_errorContainer)
                binding.root.setCardBackgroundColor(errorContainerColor)
            } else {
                // Standard surface color for non-overdue items
                val surfaceColor = ContextCompat.getColor(context, R.color.md_theme_surface)
                binding.root.setCardBackgroundColor(surfaceColor)
            }

            // Anchor date (optional)
            val anchorDate = reminder.anchorDateUtc?.let {
                Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
            }
            binding.tvAnchorDate.text = if (anchorDate != null) "⚓$anchorDate" else ""
            binding.tvAnchorDate.visibility = if (anchorDate != null) View.VISIBLE else View.GONE

            // Load member photo
            val memberGuid = reminder.memberGuid
            val photoFile = PhotoHelper.getSyncedPhotoFile(binding.root.context, memberGuid)
            if (photoFile != null && photoFile.exists()) {
                Glide.with(binding.ivMemberPhoto)
                    .load(photoFile)
                    .circleCrop()
                    .placeholder(R.drawable.kontak)
                    .error(R.drawable.kontak)
                    .into(binding.ivMemberPhoto)
            } else {
                binding.ivMemberPhoto.setImageResource(R.drawable.kontak)
            }

            // Action buttons
            binding.btnBel.isEnabled = !reminderWithMember.cellphone.isNullOrBlank()
            binding.btnBel.setOnClickListener {
                reminderWithMember.cellphone?.let { phone ->
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    it.context.startActivity(intent)
                }
            }

            binding.btnWhatsapp.isEnabled = !reminderWithMember.cellphone.isNullOrBlank()
            binding.btnWhatsapp.setOnClickListener {
                reminderWithMember.cellphone?.let { phone ->
                    val wa = formatWhatsAppNumber(phone)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa"))
                    it.context.startActivity(intent)
                }
            }

            binding.btnVoltooi.setOnClickListener {
                onComplete(reminder.reminderId)
            }

            binding.btnOverflow.setOnClickListener { anchor ->
                showOverflowMenu(anchor, reminder.reminderId)
            }

            // Click on the whole row or photo → open member detail
            val openMemberAction: (View) -> Unit = {
                if (memberGuid.isNotBlank()) {
                    onOpenMember(memberGuid)
                }
            }
            binding.root.setOnClickListener(openMemberAction)
            binding.ivMemberPhoto.setOnClickListener(openMemberAction)
        }

        private fun showOverflowMenu(anchor: View, reminderId: String) {
            val popup = PopupMenu(anchor.context, anchor)
            popup.menuInflater.inflate(R.menu.menu_bediening_reminder_overflow, popup.menu)

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_uitstel -> {
                        onSnooze(reminderId)
                        true
                    }

                    R.id.action_verwyder -> {
                        onDelete(reminderId)
                        true
                    }

                    R.id.action_verwyder_reeks -> {
                        onDeleteSeries(reminderId)
                        true
                    }

                    R.id.action_voeg_by_kalender -> {
                        onAddCalendar(reminderId)
                        true
                    }

                    R.id.action_voeg_by_google_tasks -> {
                        onAddGoogleTask(reminderId)
                        true
                    }

                    else -> false
                }
            }
            popup.show()
        }

        private fun formatDueDate(reminder: FollowUpReminderEntity): String {
            val dueDate = reminder.dueDateUtc.toLocalDateSafe() ?: LocalDate.now()
            val today = LocalDate.now(zoneId)

            val dateStr = when {
                dueDate == today ->
                    binding.root.context.getString(R.string.datum_vandag)

                dueDate == today.minusDays(1) ->
                    binding.root.context.getString(R.string.datum_gister)

                else -> {
                    dueDate.format(dateFormatter)
                }
            }

            val scheduleType = ScheduleType.fromStored(reminder.scheduleType)
            return if (scheduleType == ScheduleType.TIMED) {
                val time = Instant.ofEpochMilli(reminder.dueDateUtc)
                    .atZone(zoneId).toLocalTime()
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                "$dateStr $time"
            } else {
                dateStr
            }
        }

        private fun formatWhatsAppNumber(phone: String): String {
            return phone.replace(Regex("[^0-9+]"), "")
                .let {
                    if (it.startsWith("0")) "+27${it.drop(1)}" else it
                }
                .trimStart('+')
        }
    }
}