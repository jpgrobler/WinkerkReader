package za.co.jpsoft.winkerkreader.ui.components

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText

/**
 * Created by Pieter Grobler on 04/09/2017.
 */
class WellBehavedEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatEditText(context, attrs, defStyleAttr) {

    init {
        // No need for custom keyboard logic; EditText already does it.
        // If you must force keyboard, use a simple post in requestFocus.
    }

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        val result = super.requestFocus(direction, previouslyFocusedRect)
        if (result) {
            post {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(this, 0)
            }
        }
        return result
    }
}