package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.databinding.ItemCallLogBinding
import za.co.jpsoft.winkerkreader.data.models.CallLog

class CallLogAdapter(initialLogs: List<CallLog>) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

    private var callLogs: List<CallLog> = initialLogs.toList()

    class CallLogViewHolder(val binding: ItemCallLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val binding = ItemCallLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CallLogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CallLogViewHolder, position: Int) {
        val callLog = callLogs[position]
        val icon = when {
            callLog.source == "Phone Call" -> {
                when (callLog.callType) {
                    "INCOMING" -> "📞"
                    "OUTGOING" -> "📤"
                    "MISSED" -> "📵"
                    else -> "📞"
                }
            }
            callLog.source == "WhatsApp" -> "💬"
            else -> "📞"
        }
        with(holder.binding) {
            callIcon.text = icon
            callerName.text = callLog.callerInfo
            dateTime.text = callLog.formattedDateTime
            val callInfo = StringBuilder().apply {
                append(callLog.callType).append(" • ").append(callLog.source)
                if (callLog.duration > 0) {
                    val minutes = callLog.duration / 60
                    val seconds = callLog.duration % 60
                    append(" • ").append(minutes).append("m ").append(seconds).append("s")
                }
            }
            callNumber.text = callInfo.toString()
        }
    }

    override fun getItemCount() = callLogs.size

    fun updateLogs(newLogs: List<CallLog>) {
        val diffResult = DiffUtil.calculateDiff(CallLogDiffCallback(callLogs, newLogs))
        callLogs = newLogs.toList()
        diffResult.dispatchUpdatesTo(this)
    }

    private class CallLogDiffCallback(
        private val oldList: List<CallLog>,
        private val newList: List<CallLog>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}