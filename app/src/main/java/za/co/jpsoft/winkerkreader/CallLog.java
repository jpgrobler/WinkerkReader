package za.co.jpsoft.winkerkreader;

public class CallLog {
    private long id;
    private String callerInfo;
    private long timestamp;
    private String formattedDateTime;
    private String callType;
    private String source;
    private long duration;

    public CallLog(long id, String callerInfo, long timestamp, String formattedDateTime) {
        this.id = id;
        this.callerInfo = callerInfo;
        this.timestamp = timestamp;
        this.formattedDateTime = formattedDateTime;
        this.callType = "INCOMING";
        this.source = "WhatsApp";
        this.duration = 0;
    }

    public CallLog(long id, String callerInfo, long timestamp, String formattedDateTime, 
                   String callType, String source, long duration) {
        this.id = id;
        this.callerInfo = callerInfo;
        this.timestamp = timestamp;
        this.formattedDateTime = formattedDateTime;
        this.callType = callType != null ? callType : "INCOMING";
        this.source = source != null ? source : "WhatsApp";
        this.duration = duration;
    }

    // Getters
    public long getId() {
        return id;
    }

    public String getCallerInfo() {
        return callerInfo;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedDateTime() {
        return formattedDateTime;
    }

    public String getCallType() {
        return callType;
    }

    public String getSource() {
        return source;
    }

    public long getDuration() {
        return duration;
    }

    // Setters
    public void setId(long id) {
        this.id = id;
    }

    public void setCallerInfo(String callerInfo) {
        this.callerInfo = callerInfo;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setFormattedDateTime(String formattedDateTime) {
        this.formattedDateTime = formattedDateTime;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    @Override
    public String toString() {
        return "CallLog{" +
                "id=" + id +
                ", callerInfo='" + callerInfo + '\'' +
                ", timestamp=" + timestamp +
                ", formattedDateTime='" + formattedDateTime + '\'' +
                ", callType='" + callType + '\'' +
                ", source='" + source + '\'' +
                ", duration=" + duration +
                '}';
    }
}