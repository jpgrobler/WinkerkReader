package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.databinding.ItemCallLogBinding
import za.co.jpsoft.winkerkreader.data.models.CallLog
import kotlin.collections.toMutableList

class CallLogAdapter(callLogs: List<CallLog>) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

    private val callLogs = callLogs.toMutableList()

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
        callLogs.clear()
        callLogs.addAll(newLogs)
        notifyDataSetChanged()
    }

    fun addLog(callLog: CallLog) {
        callLogs.add(0, callLog)
        notifyItemInserted(0)
    }
}