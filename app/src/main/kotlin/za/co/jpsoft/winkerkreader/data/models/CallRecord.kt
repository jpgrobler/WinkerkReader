package za.co.jpsoft.winkerkreader.data.models

// CallRecord.kt


import android.content.Context
import za.co.jpsoft.winkerkreader.R

class CallRecord(
    val nommer: String,
    val naam: String?,
    val type: Int,
    val duur: Int,
    date: Long,
    val nommerType: Int,
    context: Context
) {
    val startTime: Long = date
    val endTime: Long = date + duur * 1000L

    var titel: String
    var beskrywing: String

    init {
        titel = when (type) {
            3 -> context.getString(R.string.missed_call_pref)
            1 -> context.getString(R.string.incoming_call_pref)
            else -> context.getString(R.string.outgoing_call_pref)
        }

        val nommerTypes = arrayOf("Work", "Home", "Mobile", "Other", "Other", "Other")

        if (!naam.isNullOrEmpty()) {
            titel = "$titel $naam"
            if (nommerType in nommerTypes.indices) {
                beskrywing = "$titel ${nommerTypes[nommerType]}"
            } else {
                beskrywing = titel
            }
        } else {
            if (nommer.isNullOrEmpty() || nommer.length < 3) {
                titel = "$titel Unknown nommer"
            } else {
                titel = "$titel $nommer"
            }
            beskrywing = titel
        }

        beskrywing = "$beskrywing\nnommer: $nommer\n".plus("duur: $duur seconds")
    }
}