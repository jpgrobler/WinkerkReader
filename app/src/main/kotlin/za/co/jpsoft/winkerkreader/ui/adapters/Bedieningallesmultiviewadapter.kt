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
import za.co.jpsoft.winkerkreader.databinding.ItemBedieningCelebrationBinding
import za.co.jpsoft.winkerkreader.databinding.ItemBedieningReminderBinding
import za.co.jpsoft.winkerkreader.databinding.ItemSectionHeaderBinding
import za.co.jpsoft.winkerkreader.utils.Utils.toLocalDateSafe
import za.co.jpsoft.winkerkreader.utils.VandagAllesDisplayItem
import za.co.jpsoft.winkerkreader.utils.files.PhotoHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class BedieningAllesMultiViewAdapter(
    private val onCallMember: (String, String) -> Unit,
    private val onSendSms: (String, String) -> Unit,
    private val onWhatsApp: (VandagAllesItem.Celebration) -> Unit,
    private val onAddNote: (String, String) -> Unit,
    private val onSetReminder: (String) -> Unit,
    private val onOpenMember: (String) -> Unit,
    private val onComplete: (String) -> Unit,
    private val onSnooze: (String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onDeleteSeries: (String) -> Unit,
    private val onAddCalendar: (String) -> Unit,
    private val onAddGoogleTask: (String) -> Unit
) : ListAdapter<VandagAllesDisplayItem, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CELEBRATION = 1
        private const val VIEW_TYPE_REMINDER = 2

        private val DIFF = object : DiffUtil.ItemCallback<VandagAllesDisplayItem>() {
            override fun areItemsTheSame(
                old: VandagAllesDisplayItem,
                new: VandagAllesDisplayItem
            ): Boolean = old.id == new.id

            override fun areContentsTheSame(
                old: VandagAllesDisplayItem,
                new: VandagAllesDisplayItem
            ): Boolean = old == new
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is VandagAllesDisplayItem.Header -> VIEW_TYPE_HEADER
        is VandagAllesDisplayItem.Celebration -> VIEW_TYPE_CELEBRATION
        is VandagAllesDisplayItem.Reminder -> VIEW_TYPE_REMINDER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                HeaderViewHolder(ItemSectionHeaderBinding.inflate(inflater, parent, false))
            }

            VIEW_TYPE_CELEBRATION -> {
                CelebrationViewHolder(
                    ItemBedieningCelebrationBinding.inflate(
                        inflater,
                        parent,
                        false
                    )
                )
            }

            VIEW_TYPE_REMINDER -> {
                ReminderViewHolder(ItemBedieningReminderBinding.inflate(inflater, parent, false))
            }

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind((getItem(position) as VandagAllesDisplayItem.Header))
            is CelebrationViewHolder -> holder.bind((getItem(position) as VandagAllesDisplayItem.Celebration))
            is ReminderViewHolder -> holder.bind((getItem(position) as VandagAllesDisplayItem.Reminder))
        }
    }

    private inner class HeaderViewHolder(
        private val binding: ItemSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: VandagAllesDisplayItem.Header) {
            binding.tvSectionTitle.text = header.title
        }
    }

    private inner class CelebrationViewHolder(
        private val binding: ItemBedieningCelebrationBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(celebrationItem: VandagAllesDisplayItem.Celebration) {
            val celebration = celebrationItem.item

            binding.tvCelebrationTitle.text = "${celebration.eventType.emoji} ${celebration.name}"
            binding.tvCelebrationDetail.text = celebration.detailText

            val photoFile =
                PhotoHelper.getSyncedPhotoFile(binding.root.context, celebration.memberGuid)
            if (photoFile != null && photoFile.exists()) {
                Glide.with(binding.ivCelebrationPhoto)
                    .load(photoFile)
                    .circleCrop()
                    .placeholder(R.drawable.kontak)
                    .error(R.drawable.kontak)
                    .into(binding.ivCelebrationPhoto)
            } else {
                binding.ivCelebrationPhoto.setImageResource(R.drawable.kontak)
            }

            binding.btnWhatsapp.isEnabled = !celebration.cellphone.isNullOrBlank()
            binding.btnWhatsapp.setOnClickListener {
                onWhatsApp(celebration)
            }

            binding.btnCall.isEnabled = !celebration.cellphone.isNullOrBlank()
            binding.btnCall.setOnClickListener {
                celebration.cellphone?.let { phone ->
                    onCallMember(celebration.memberGuid, phone)
                }
            }

            binding.btnSms.isEnabled = !celebration.cellphone.isNullOrBlank()
            binding.btnSms.setOnClickListener {
                celebration.cellphone?.let { phone ->
                    onSendSms(celebration.memberGuid, phone)
                }
            }

            binding.btnAddNote.setOnClickListener {
                onAddNote(celebration.memberGuid, celebration.name)
            }

            binding.btnSetReminder.setOnClickListener {
                onSetReminder(celebration.memberGuid)
            }

            val openMemberAction: (View) -> Unit = {
                if (celebration.memberGuid.isNotBlank()) {
                    onOpenMember(celebration.memberGuid)
                }
            }
            binding.root.setOnClickListener(openMemberAction)
            binding.ivCelebrationPhoto.setOnClickListener(openMemberAction)
        }
    }

    private inner class ReminderViewHolder(
        private val binding: ItemBedieningReminderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val zoneId = ZoneId.systemDefault()
        private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

        fun bind(reminderItem: VandagAllesDisplayItem.Reminder) {
            val reminderWithMember = reminderItem.item.reminderWithMember
            val reminder = reminderWithMember.reminder
            val displayName = reminderWithMember.displayName

            binding.tvReminderTitle.text = reminder.title
            binding.tvMemberName.text = displayName
            binding.tvDueDate.text = formatDueDate(reminder)

            val startOfTodayUtc =
                LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val isOverdue = reminder.dueDateUtc < startOfTodayUtc

            binding.tvOverdueBadge.visibility = if (isOverdue) View.VISIBLE else View.GONE
            val context = binding.root.context
            if (isOverdue) {
                val lightRedColor = ContextCompat.getColor(context, R.color.md_theme_errorContainer)
                binding.root.setCardBackgroundColor(lightRedColor)
            } else {
                val surfaceColor = ContextCompat.getColor(context, R.color.md_theme_surface)
                binding.root.setCardBackgroundColor(surfaceColor)
            }

            val anchorDate = reminder.anchorDateUtc?.let {
                Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
            }
            binding.tvAnchorDate.text = if (anchorDate != null) "⚓$anchorDate" else ""
            binding.tvAnchorDate.visibility = if (anchorDate != null) View.VISIBLE else View.GONE

            val photoFile =
                PhotoHelper.getSyncedPhotoFile(binding.root.context, reminder.memberGuid)
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

            binding.btnBel.isEnabled = !reminderWithMember.cellphone.isNullOrBlank()
            binding.btnBel.setOnClickListener {
                reminderWithMember.cellphone?.let { phone ->
                    onCallMember(reminder.memberGuid, phone)
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

            val openMemberAction: (View) -> Unit = {
                if (reminder.memberGuid.isNotBlank()) {
                    onOpenMember(reminder.memberGuid)
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

                else -> dueDate.format(dateFormatter)
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