package za.co.jpsoft.winkerkreader.data

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

    private lateinit var inputMethodManager: InputMethodManager
    private var showKeyboard = false

    init {
        initializeWellBehavedEditText(context)
    }

    private fun initializeWellBehavedEditText(context: Context) {
        inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        // SAM conversion: OnGlobalLayoutListener
        viewTreeObserver.addOnGlobalLayoutListener {
            if (showKeyboard) {
                // Toggle showKeyboard based on whether soft input was successfully shown
                showKeyboard = !inputMethodManager.showSoftInput(this@WellBehavedEditText, 0)
            }
        }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if (!focused) {
            showKeyboard = false
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
    }

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        val result = super.requestFocus(direction, previouslyFocusedRect)
        showKeyboard = true
        // SAM conversion: Runnable
        post {
            showKeyboard = !inputMethodManager.showSoftInput(this@WellBehavedEditText, 0)
        }
        return result
    }
}