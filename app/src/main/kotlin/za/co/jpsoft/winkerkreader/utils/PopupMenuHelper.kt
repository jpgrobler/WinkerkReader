package za.co.jpsoft.winkerkreader.utils

import android.util.Log
import androidx.appcompat.widget.PopupMenu

// PopupMenuHelper.kt
fun PopupMenu.forceShowIcons() {
    try {
        val field = javaClass.getDeclaredField("mPopup")
        field.isAccessible = true
        val menuPopupHelper = field.get(this)
        val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
        val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.java)
        setForceIcons.invoke(menuPopupHelper, true)
    } catch (e: Exception) {
        Log.e("PopupMenuHelper", "Failed to force icons", e)
    }
}