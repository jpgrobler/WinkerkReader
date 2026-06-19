package za.co.jpsoft.winkerkreader.data.pastoral.model

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Typed wrapper around the `contextJson` string stored on [FollowUpReminderEntity].
 * Use [build] to create, [from] to parse, [toJson] to serialize.
 */
data class TemplateContext(
    val values: Map<String, String> = emptyMap()
) {

    fun getString(key: String): String? = values[key]?.ifBlank { null }

    fun getDate(key: String): LocalDate? = values[key]?.let {
        try { LocalDate.parse(it, ISO) } catch (e: Exception) { null }
    }

    /** Returns a single-line display string for the reminder card. */
    fun toDisplayLine(): String? {
        val parts = mutableListOf<String>()

        values["deceasedName"]?.ifBlank { null }?.let { parts += it }
        values["deceasedDob"]?.let { dob ->
            try {
                val date = LocalDate.parse(dob, ISO)
                parts += date.format(DISPLAY)
            } catch (_: Exception) {}
        }
        values["deceasedDate"]?.let { dd ->
            try {
                val date = LocalDate.parse(dd, ISO)
                parts += "† ${date.format(DISPLAY)}"
            } catch (_: Exception) {}
        }
        values["hospital"]?.ifBlank { null }?.let { parts += "🏥 $it" }
        values["illness"]?.ifBlank { null }?.let { parts += "💊 $it" }
        values["traumaType"]?.ifBlank { null }?.let { parts += "⚠️ $it" }
        values["traumaDate"]?.let { td ->
            try {
                val date = LocalDate.parse(td, ISO)
                parts += "⚠️ ${date.format(DISPLAY)}"
            } catch (_: Exception) {}
        }

        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    fun toJson(): String = JSONObject(values).toString()

    companion object {
        private val ISO     = DateTimeFormatter.ISO_LOCAL_DATE
        private val DISPLAY = DateTimeFormatter.ofPattern("d MMM yyyy")

        fun from(json: String?): TemplateContext {
            if (json.isNullOrBlank()) return TemplateContext()
            return try {
                val obj = JSONObject(json)
                val map = mutableMapOf<String, String>()
                obj.keys().forEach { key -> map[key] = obj.getString(key) }
                TemplateContext(map)
            } catch (e: Exception) {
                TemplateContext()
            }
        }

        fun build(block: Builder.() -> Unit): TemplateContext =
            Builder().apply(block).build()
    }

    class Builder {
        private val values = mutableMapOf<String, String>()

        fun put(key: String, value: String?) {
            if (!value.isNullOrBlank()) values[key] = value.trim()
        }

        fun put(key: String, date: LocalDate?) {
            if (date != null) values[key] = date.format(ISO)
        }

        fun build() = TemplateContext(values)
    }
}