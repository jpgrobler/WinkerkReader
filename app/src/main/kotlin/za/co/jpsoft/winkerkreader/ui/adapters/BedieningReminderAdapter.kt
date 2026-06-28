package za.co.jpsoft.winkerkreader.ui.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.pastoral.entities.FollowUpReminderEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderWithMember
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContext
import za.co.jpsoft.winkerkreader.databinding.ItemBedieningReminderBinding
import za.co.jpsoft.winkerkreader.utils.PhotoHelper
import za.co.jpsoft.winkerkreader.utils.Utils.toLocalDateSafe
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class BedieningReminderAdapter(
    private val onVoltooi:      (reminderId: String) -> Unit,
    private val onSnooze:       (reminderId: String) -> Unit,
    private val onAddCalendar:  (reminderId: String) -> Unit,
    private val onOpenMember:   (memberGuid: String) -> Unit,
    private val onAddGoogleTask: (reminderId: String) -> Unit,
    private val onDelete: (reminderId: String) -> Unit,
    private val onDeleteSeries:   (reminderId: String) -> Unit
) : ListAdapter<ReminderWithMember, BedieningReminderAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(
        private val binding: ItemBedieningReminderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReminderWithMember) {
            val reminder = item.reminder
            val today = LocalDate.now(ZoneId.systemDefault())
            val dueDate = reminder.dueDateUtc.toLocalDateSafe() ?: LocalDate.now()
            val isOverdue = dueDate.isBefore(today)

            binding.ivMemberPhoto.setOnClickListener {
                onOpenMember(item.reminder.memberGuid)
            }

            binding.tvMemberName.text = item.displayName

            val symbol = reminder.symbol?.takeIf { it.isNotBlank() } ?: ""
            binding.tvReminderTitle.text = "$symbol${reminder.title}"

            binding.tvDueDate.text = formatDueDate(reminder)

            val contextLine = TemplateContext.from(reminder.contextJson).toDisplayLine()
            if (contextLine != null) {
                binding.tvContextLine.text = contextLine
                binding.tvContextLine.visibility = View.VISIBLE
            } else {
                binding.tvContextLine.visibility = View.GONE
            }

            binding.tvOverdueBadge.visibility =
                if (isOverdue) View.VISIBLE else View.GONE

            // Load photo
            val guid = item.reminder.memberGuid
            val photoPath = PhotoHelper.getSyncedPhotoPath(binding.ivMemberPhoto.context, guid)
            if (photoPath != null) {
                val file = File(photoPath)
                if (file.exists()) {
                    Glide.with(binding.ivMemberPhoto)
                        .load(file)
                        .circleCrop()
                        .placeholder(R.drawable.kontaks)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(binding.ivMemberPhoto)
                } else {
                    binding.ivMemberPhoto.setImageResource(R.drawable.kontaks)
                }
            } else {
                binding.ivMemberPhoto.setImageResource(R.drawable.kontaks)
            }

            binding.ivMemberPhoto.setOnClickListener {
                onOpenMember(item.reminder.memberGuid)
            }
            // Actions
            binding.btnBel.isEnabled = !item.cellphone.isNullOrBlank()
            binding.btnBel.setOnClickListener {
                item.cellphone?.let { phone ->
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    it.context.startActivity(intent)
                }
            }

            binding.btnWhatsapp.isEnabled = !item.cellphone.isNullOrBlank()
            binding.btnWhatsapp.setOnClickListener {
                item.cellphone?.let { phone ->
                    val wa = formatWhatsAppNumber(phone)
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/$wa"))
                    it.context.startActivity(intent)
                }
            }

            binding.btnVoltooi.setOnClickListener {
                onVoltooi(reminder.reminderId)
            }

            binding.btnOverflow.setOnClickListener { anchor ->
                showOverflowMenu(anchor, item)
            }

            val anchorDate = reminder.anchorDateUtc.toLocalDateSafe()
                ?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
            binding.tvAnchorDate.text = "⚓${anchorDate ?: ""}"
            binding.tvAnchorDate.visibility = if (anchorDate != null) View.VISIBLE else View.GONE
        }

        private fun showOverflowMenu(anchor: View, item: ReminderWithMember) {
            val menu = PopupMenu(anchor.context, anchor)
            menu.inflate(R.menu.menu_bediening_reminder_overflow)

            // Hide "Voeg by kalender" if already synced
            menu.menu.findItem(R.id.action_voeg_by_kalender)?.isVisible =
                !item.reminder.calendarSynced

            // Hide "Voeg by Google Tasks" if already synced
            menu.menu.findItem(R.id.action_voeg_by_google_tasks)?.isVisible =
                !item.reminder.googleTaskSynced

            menu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_uitstel           -> { onSnooze(item.reminder.reminderId); true }
                    R.id.action_voeg_by_kalender  -> { onAddCalendar(item.reminder.reminderId); true }
                    R.id.action_voeg_by_google_tasks -> { onAddGoogleTask(item.reminder.reminderId); true }
                    R.id.action_verwyder          -> { onDelete(item.reminder.reminderId); true }
                    R.id.action_verwyder_reeks    -> { onDeleteSeries(item.reminder.reminderId); true }
                    else -> false
                }
            }
            try {
                val field = PopupMenu::class.java.getDeclaredField("mPopup")
                field.isAccessible = true
                val popup = field.get(menu)
                val method = popup.javaClass.getMethod("setForceShowIcon", Boolean::class.java)
                method.invoke(popup, true)
            } catch (e: Exception) {
                // Fallback: ignore – icons won't show
            }
            menu.show()
        }

        private fun formatDueDate(reminder: FollowUpReminderEntity): String {
            val zoneId = ZoneId.systemDefault()
            val dueDate = reminder.dueDateUtc.toLocalDateSafe() ?: LocalDate.now()
            val today = LocalDate.now(zoneId)

            val dateStr = when {
                dueDate == today ->
                    binding.root.context.getString(R.string.datum_vandag)
                dueDate == today.minusDays(1) ->
                    binding.root.context.getString(R.string.datum_gister)
                else -> {
                    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                    dueDate.format(formatter)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBedieningReminderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun positionOf(reminderId: String): Int {
        return currentList.indexOfFirst { it.reminder.reminderId == reminderId }
    }
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ReminderWithMember>() {
            override fun areItemsTheSame(a: ReminderWithMember, b: ReminderWithMember) =
                a.reminder.reminderId == b.reminder.reminderId

            override fun areContentsTheSame(a: ReminderWithMember, b: ReminderWithMember) =
                a == b
        }
    }
}