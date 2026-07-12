package za.co.jpsoft.winkerkreader.data.pastoral.model

/**
 * Defines the context input fields required by each template code.
 * The schema lives in code; the actual values are stored as JSON on the reminder entity.
 */
object TemplateContextSchema {

    sealed class Field(
        val key: String,
        val labelAfr: String,
        val required: Boolean = false
    ) {
        class Text(key: String, labelAfr: String, required: Boolean = false) :
            Field(key, labelAfr, required)

        class DateField(key: String, labelAfr: String, required: Boolean = false) :
            Field(key, labelAfr, required)
    }

    /** Returns context fields for [templateCode], empty list if none needed. */
    fun fieldsFor(templateCode: String): List<Field> = when (templateCode) {

        "NA_STERF" -> listOf(
            Field.Text(
                key = "deceasedName",
                labelAfr = "Naam van oorledene",
                required = false
            ),
            Field.DateField(
                key = "deceasedDob",
                labelAfr = "Geboortedatum van oorledene",
                required = false
            ),
            Field.DateField(
                key = "deceasedDate",
                labelAfr = "Datum van afsterwe",
                required = false
            ),
        )

        "OPERASIE" -> listOf(
            Field.Text(
                key = "hospital",
                labelAfr = "Hospitaal",
                required = true
            )
            // Anchor date = operation date — no separate date field needed
        )

        "SIEKTE" -> listOf(
            Field.Text(
                key = "illness",
                labelAfr = "Tipe siekte",
                required = false
            )
        )

        "TRAUMA" -> listOf(
            Field.Text(
                key = "traumaType",
                labelAfr = "Tipe trauma",
                required = false
            ),
            Field.DateField(
                key = "traumaDate",
                labelAfr = "Datum van trauma",
                required = false
            )
        )


        else -> emptyList()
    }

    /** Returns a human-readable anchor date label for [templateCode]. */
    fun anchorDateLabel(templateCode: String): String = when (templateCode) {
        "NA_STERF" -> "Datum van afsterwe"
        "OPERASIE" -> "Hospitalisasiedatum"
        "NUWE_LID" -> "Datum van aansluiting"
        else -> "Verwysingsdatum"
    }

    fun hasContext(templateCode: String) = fieldsFor(templateCode).isNotEmpty()
}