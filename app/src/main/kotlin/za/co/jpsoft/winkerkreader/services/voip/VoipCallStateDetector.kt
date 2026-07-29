package za.co.jpsoft.winkerkreader.services.voip

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import java.util.Locale

/**
 * Detects the call state (INCOMING, OUTGOING, MISSED, ENDED, SCREENING, UNKNOWN)
 * from notification metadata and text content.
 */
class VoipCallStateDetector {

    enum class CallState {
        INCOMING, OUTGOING, MISSED, ENDED, SCREENING, UNKNOWN
    }

    /**
     * Primary detection method using category, callType extra, and fallback text inference.
     */
    fun detectCallState(
        category: String?,
        callTypeExtra: Int,
        isCallStyle: Boolean,
        extras: Bundle,
        sbn: StatusBarNotification
    ): CallState {
        when (category) {
            Notification.CATEGORY_CALL -> {
                if (isCallStyle) {
                    return when (callTypeExtra) {
                        1 -> CallState.INCOMING
                        2 -> CallState.OUTGOING
                        3 -> CallState.SCREENING
                        else -> CallState.INCOMING
                    }
                }
                return inferCallStateFromExtras(extras)
            }
            Notification.CATEGORY_MISSED_CALL -> return CallState.MISSED
        }

        if (isCallStyle) {
            return when (callTypeExtra) {
                1 -> CallState.INCOMING
                2 -> CallState.OUTGOING
                3 -> CallState.SCREENING
                else -> CallState.UNKNOWN
            }
        }
        return CallState.UNKNOWN
    }

    /**
     * Fallback inference from extras when category is not explicit.
     */
    fun inferCallStateFromExtras(extras: Bundle): CallState {
        if (extras.getBoolean("is_incoming", false)) return CallState.INCOMING
        if (extras.getBoolean("is_outgoing", false)) return CallState.OUTGOING

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""

        if (isCallEndedNotification(title, text, bigText, subText)) return CallState.ENDED
        if (isMissedCall(title, text, bigText, subText)) return CallState.MISSED
        if (isIncomingCall(title, text, bigText, subText)) return CallState.INCOMING
        if (isPossibleOutgoingCall(title, text, bigText, subText)) return CallState.OUTGOING
        return CallState.UNKNOWN
    }

    // ---- Text matchers (pure string) ----

    fun isIncomingCall(title: String, text: String, bigText: String, subText: String): Boolean {
        val combined = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        val strongIncoming = arrayOf(
            "is calling you",
            "wants to call you",
            "incoming call",
            "incoming video call",
            "incoming voice call",
            "inkomende oproep",
            "inkomende video-oproep",
            "inkomende stemoproep",
            "bel jou",
            "wil jou bel",
            "eingehender anruf",
            "eingehender videoanruf",
            "eingehender sprachanruf",
            "ruft dich an",
            "appel entrant",
            "appel video entrant",
            "appel vocal entrant",
            "vous appelle",
            "llamada entrante",
            "videollamada entrante",
            "llamada de voz entrante",
            "te esta llamando",
            "te está llamando",
            "chamada recebida",
            "chamada de entrada",
            "chamada de video recebida",
            "está ligando para você",
            "esta ligando para voce"
        )
        if (strongIncoming.any { combined.contains(it) }) return true
        if (combined.contains("you called") || combined.contains("you are calling") ||
            combined.contains("outgoing") || combined.contains("call started") ||
            combined.contains("uitgaande oproep") || combined.contains("ausgehender anruf") ||
            combined.contains("appel sortant") || combined.contains("llamada saliente") ||
            combined.contains("chamada efetuada")
        ) return false
        return combined.contains("calling") &&
                (!combined.contains("you") || combined.contains("calling you"))
    }

    fun isPossibleOutgoingCall(title: String, text: String, bigText: String, subText: String): Boolean {
        val combined = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        val strongOutgoing = arrayOf(
            "you called", "you are calling", "outgoing call", "call started", "calling…",
            "uitgaande oproep", "jy het gebel", "jy bel", "ausgehender anruf", "du rufst an",
            "appel sortant", "vous appelez", "llamada saliente", "estas llamando", "estás llamando",
            "chamada efetuada", "ligacao efetuada", "ligação efetuada", "voce esta ligando",
            "você está ligando"
        )
        if (strongOutgoing.any { combined.contains(it) }) return true
        if (combined.contains("is calling") || combined.contains("calling you") ||
            combined.contains("wants to call") || combined.contains("incoming")
        ) return false
        return false
    }

    fun isCallEndedNotification(title: String, text: String, bigText: String, subText: String): Boolean {
        val endedKeywords = arrayOf(
            "call ended", "call finished", "call completed", "call duration", "call lasted",
            "hung up", "disconnected", "call time",
            "oproep beeindig", "oproep beëindig", "gesprek beeindig", "gesprek beëindig",
            "oproep klaar", "gesprek klaar", "gesprekstyd",
            "anruf beendet", "gesprach beendet", "gespräch beendet",
            "appel termine", "appel terminé",
            "llamada finalizada", "llamada terminada",
            "duracion de la llamada", "duración de la llamada",
            "chamada encerrada", "ligacao encerrada", "ligação encerrada",
            "duracao da chamada", "duração da chamada"
        )
        val combined = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        return endedKeywords.any { combined.contains(it) }
    }

    fun isMissedCall(title: String, text: String, bigText: String, subText: String): Boolean {
        val missedKeywords = arrayOf(
            "missed call", "missed video call", "missed voice call", "unanswered",
            "didn't answer", "no answer",
            "gemiste oproep", "gemisde oproep", "onbeantwoord",
            "verpasster anruf", "nicht beantwortet",
            "appel manque", "appel manqué", "sans reponse", "sans réponse",
            "llamada perdida", "no respondio", "no respondió",
            "chamada perdida", "ligacao perdida", "ligação perdida", "nao atendida", "não atendida"
        )
        val combined = "$title $text $bigText $subText".lowercase(Locale.ROOT)
        return missedKeywords.any { combined.contains(it) }
    }
}