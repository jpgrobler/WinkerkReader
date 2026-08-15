package za.co.jpsoft.winkerkreader.ui.bottomsheets.controllers

import android.R
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import za.co.jpsoft.winkerkreader.data.pastoral.model.FamilyMember

class FamilyMemberSpinnerController(
    private val context: Context,
    private val container: LinearLayout,
    private val onMemberSelected: (FamilyMember?) -> Unit
) {

    private var spinner: Spinner? = null
    private var label: TextView? = null
    private var members: List<FamilyMember> = emptyList()

    fun show(members: List<FamilyMember>) {
        remove()
        this.members = members
        container.isVisible = true

        // Label
        label = TextView(context).apply {
            text = "Kies gesinslid of tik naam van oorledene"
            setTextAppearance(R.style.TextAppearance_Small)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
        }
        container.addView(label, 0)

        // Spinner items
        val displayItems = mutableListOf("Kies gesinslid")
        displayItems.addAll(members.map { "${it.displayName} (${it.birthday})" })

        val adapter = object : ArrayAdapter<String>(
            context,
            R.layout.simple_spinner_item,
            displayItems
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.apply {
                    setTextColor(
                        MaterialColors.getColor(
                            context,
                            com.google.android.material.R.attr.colorOnSurface,
                            Color.BLACK
                        )
                    )
                    textSize = 16f
                }
                return view
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.apply {
                    setTextColor(
                        MaterialColors.getColor(
                            context,
                            com.google.android.material.R.attr.colorOnSurface,
                            Color.BLACK
                        )
                    )
                    textSize = 16f
                }
                return view
            }
        }
        adapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)

        spinner = Spinner(context).apply {
            this.adapter = adapter
            setSelection(0, false)

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position == 0) {
                        onMemberSelected(null)
                        return
                    }
                    onMemberSelected(members[position - 1])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            backgroundTintList = ColorStateList.valueOf(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurfaceVariant,
                    Color.LTGRAY
                )
            )
        }

        container.addView(spinner, 1)
    }

    fun remove() {
        spinner?.let { (it.parent as? ViewGroup)?.removeView(it) }
        label?.let { (it.parent as? ViewGroup)?.removeView(it) }
        spinner = null
        label = null
        members = emptyList()
        container.isVisible = false
    }

    private fun dpToPx(dp: Int) = (dp * context.resources.displayMetrics.density).toInt()
}