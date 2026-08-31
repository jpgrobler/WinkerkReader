package za.co.jpsoft.winkerkreader.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView   // <-- CORRECT import
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import za.co.jpsoft.winkerkreader.R

class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val subtitleView: TextView
    private val actionButton: MaterialButton
    private val iconView: ImageView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.empty_state_layout, this, true)
        titleView = findViewById(R.id.empty_title)
        subtitleView = findViewById(R.id.empty_subtitle)
        actionButton = findViewById(R.id.empty_action_button)
        iconView = findViewById(R.id.empty_icon)
    }

    fun setIcon(resId: Int) {
        iconView.setImageResource(resId)   // now resolves correctly
    }

    fun setTitle(text: String) {
        titleView.text = text
    }

    fun setSubtitle(text: String) {
        subtitleView.text = text
    }

    fun setActionText(text: String) {
        actionButton.text = text
        actionButton.visibility = VISIBLE
    }

    fun setActionListener(listener: OnClickListener) {
        actionButton.setOnClickListener(listener)
    }

    fun showEmptyState() {
        visibility = VISIBLE
    }

    fun hideEmptyState() {
        visibility = GONE
    }
}