package za.co.jpsoft.winkerkreader.ui.bottomsheets.controllers

import android.app.DatePickerDialog
import android.content.Context
import android.text.InputType
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.google.android.material.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateContextSchema
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TemplateContextFormBuilder(
    private val context: Context,
    private val container: LinearLayout,
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
        "d MMM yyyy",
        Locale.getDefault()
    )
) {

    private val textFields = mutableMapOf<String, TextInputEditText>()
    private val dateButtons = mutableMapOf<String, MaterialButton>()
    private val dateButtonLabels = mutableMapOf<String, String>()

    fun buildFor(templateCode: String) {
        clear()
        val fields = TemplateContextSchema.fieldsFor(templateCode)
        container.isVisible = fields.isNotEmpty()

        fields.forEach { field ->
            when (field) {
                is TemplateContextSchema.Field.Text -> addTextField(field)
                is TemplateContextSchema.Field.DateField -> addDateButton(field)
            }
        }
    }

    private fun addTextField(field: TemplateContextSchema.Field.Text) {
        val til = TextInputLayout(context).apply {
            hint = field.labelAfr
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
        }
        val et = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_WORDS
            tag = field.key
        }
        til.addView(et)
        container.addView(til)
        textFields[field.key] = et
    }

    private fun addDateButton(field: TemplateContextSchema.Field.DateField) {
        val btn = MaterialButton(
            context,
            null,
            R.attr.materialButtonOutlinedStyle
        ).apply {
            text = field.labelAfr
            tag = field.key
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
            setOnClickListener {
                val current = extractDateFromButtonText(this) ?: LocalDate.now()
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val picked = LocalDate.of(year, month + 1, day)
                        text = "${field.labelAfr}: ${picked.format(dateFormatter)}"
                    },
                    current.year,
                    current.monthValue - 1,
                    current.dayOfMonth
                ).show()
            }
        }
        container.addView(btn)
        dateButtons[field.key] = btn
        dateButtonLabels[field.key] = field.labelAfr
    }

    private fun extractDateFromButtonText(button: MaterialButton): LocalDate? {
        val text = button.text.toString()
        val afterColon = text.substringAfter(':').trim()
        return try {
            LocalDate.parse(afterColon, dateFormatter)
        } catch (_: Exception) {
            null
        }
    }

    fun getTextValues(): Map<String, String> =
        textFields.mapValues { (_, et) -> et.text?.toString()?.trim() ?: "" }

    fun getDateValues(): Map<String, LocalDate> =
        dateButtons.mapNotNull { (key, btn) ->
            extractDateFromButtonText(btn)?.let { key to it }
        }.toMap()

    fun getTextField(key: String): TextInputEditText? = textFields[key]

    fun getDateButton(key: String): MaterialButton? = dateButtons[key]

    fun clear() {
        container.removeAllViews()
        textFields.clear()
        dateButtons.clear()
        dateButtonLabels.clear()
        container.isVisible = false
    }

    private fun dpToPx(dp: Int) = (dp * context.resources.displayMetrics.density).toInt()
}