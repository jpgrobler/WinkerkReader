package za.co.jpsoft.winkerkreader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CallLogAdapter extends RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder> {

    private List<CallLog> callLogs;

    public CallLogAdapter(List<CallLog> callLogs) {
        this.callLogs = callLogs;
    }

    public static class CallLogViewHolder extends RecyclerView.ViewHolder {
        public TextView callIcon;
        public TextView callerName;
        public TextView dateTime;
        public TextView callNumber;

        public CallLogViewHolder(View itemView) {
            super(itemView);
            callIcon = itemView.findViewById(R.id.callIcon);
            callerName = itemView.findViewById(R.id.callerName);
            dateTime = itemView.findViewById(R.id.dateTime);
            callNumber = itemView.findViewById(R.id.callNumber);
        }
    }

    @NonNull
    @Override
    public CallLogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_call_log, parent, false);
        return new CallLogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CallLogViewHolder holder, int position) {
        CallLog callLog = callLogs.get(position);
        
        // Set appropriate icon based on call type and source
        String icon;
        if ("Phone Call".equals(callLog.getSource())) {
            switch (callLog.getCallType()) {
                case "INCOMING":
                    icon = "📞";
                    break;
                case "OUTGOING":
                    icon = "📤";
                    break;
                case "MISSED":
                    icon = "📵";
                    break;
                default:
                    icon = "📞";
                    break;
            }
        } else if ("WhatsApp".equals(callLog.getSource())) {
            icon = "💬";
        } else {
            icon = "📞";
        }
        
        holder.callIcon.setText(icon);
        holder.callerName.setText(callLog.getCallerInfo());
        holder.dateTime.setText(callLog.getFormattedDateTime());
        
        // Show call type, source, and duration
        StringBuilder callInfo = new StringBuilder();
        callInfo.append(callLog.getCallType()).append(" • ").append(callLog.getSource());
        
        if (callLog.getDuration() > 0) {
            long minutes = callLog.getDuration() / 60;
            long seconds = callLog.getDuration() % 60;
            callInfo.append(" • ").append(minutes).append("m ").append(seconds).append("s");
        }
        
        holder.callNumber.setText(callInfo.toString());
    }

    @Override
    public int getItemCount() {
        return callLogs.size();
    }

    public void updateLogs(List<CallLog> newLogs) {
        callLogs.clear();
        callLogs.addAll(newLogs);
        notifyDataSetChanged();
    }
    
    public void addLog(CallLog callLog) {
        callLogs.add(0, callLog); // Add to beginning for latest first
        notifyItemInserted(0);
    }
}