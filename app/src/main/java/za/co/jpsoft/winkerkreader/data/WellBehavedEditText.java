package za.co.jpsoft.winkerkreader.data;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import androidx.appcompat.widget.AppCompatEditText;
/**
 * Created by Pieter Grobler on 04/09/2017.
 */

public class WellBehavedEditText extends AppCompatEditText {
    private InputMethodManager inputMethodManager;
    private boolean showKeyboard = false;

    public WellBehavedEditText(Context context) {
        super(context);
        this.initializeWellBehavedEditText(context);
    }

    public WellBehavedEditText(Context context, AttributeSet attributes) {
        super(context, attributes);
        this.initializeWellBehavedEditText(context);
    }

    public WellBehavedEditText(Context context, AttributeSet attributes, int defStyleAttr) {
        super(context, attributes, defStyleAttr);
        this.initializeWellBehavedEditText(context);
    }
/**
    public WellBehavedEditText(Context context, AttributeSet attributes, int defStyleAttr, int defStyleRes) {
        super(context, attributes, defStyleAttr, defStyleRes);
        this.initializeWellBehavedEditText(context);
    }
*/
    private void initializeWellBehavedEditText(Context context) {
        this.inputMethodManager = (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);

        final WellBehavedEditText editText = this;
        this.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if(showKeyboard) {
                    showKeyboard = !(inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_FORCED));
                }
            }
        });
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if(!focused) this.showKeyboard = false;
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        boolean result = super.requestFocus(direction, previouslyFocusedRect);
        this.showKeyboard = true;
        final WellBehavedEditText self = this;
        this.post(new Runnable() {
            @Override
            public void run() {
                showKeyboard = !(inputMethodManager.showSoftInput(self, InputMethodManager.SHOW_FORCED));
            }
        });
        return result;
    }
}
