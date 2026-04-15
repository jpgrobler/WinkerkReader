package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.models.CallLog
import kotlin.collections.toMutableList

class CallLogAdapter(callLogs: List<CallLog>) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

    private val callLogs = callLogs.toMutableList()

    class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val callIcon: TextView = itemView.findViewById(R.id.callIcon)
        val callerName: TextView = itemView.findViewById(R.id.callerName)
        val dateTime: TextView = itemView.findViewById(R.id.dateTime)
        val callNumber: TextView = itemView.findViewById(R.id.callNumber)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_call_log, parent, false)
        return CallLogViewHolder(view)
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
        holder.callIcon.text = icon
        holder.callerName.text = callLog.callerInfo
        holder.dateTime.text = callLog.formattedDateTime
        val callInfo = StringBuilder().apply {
            append(callLog.callType).append(" • ").append(callLog.source)
            if (callLog.duration > 0) {
                val minutes = callLog.duration / 60
                val seconds = callLog.duration % 60
                append(" • ").append(minutes).append("m ").append(seconds).append("s")
            }
        }
        holder.callNumber.text = callInfo.toString()
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