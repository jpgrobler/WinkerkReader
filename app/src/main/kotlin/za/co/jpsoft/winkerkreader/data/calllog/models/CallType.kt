package za.co.jpsoft.winkerkreader.data.calllog.models

enum class CallType {
    INCOMING, OUTGOING, MISSED, ENDED,
    OTHER,      // confirmed call-shaped notification, direction couldn't be classified
    UNKNOWN     // internal safety-net value; never persisted
}