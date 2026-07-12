package za.co.jpsoft.winkerkreader.utils

import android.util.Log
import androidx.appcompat.widget.PopupMenu
import za.co.jpsoft.winkerkreader.BuildConfig

// PopupMenuHelper.kt
//fun PopupMenu.forceShowIcons() {
//    try {
//        val field = javaClass.getDeclaredField("mPopup")
//        field.isAccessible = true
//        val menuPopupHelper = field.get(this)
//        val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
//        val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.java)
//        setForceIcons.invoke(menuPopupHelper, true)
//    } catch (e: Exception) {
//        if (BuildConfig.DEBUG) Log.e("PopupMenuHelper", "Failed to force icons", e)
//    }
//}
fun PopupMenu.forceShowIcons() {
    try {
        val field = PopupMenu::class.java.getDeclaredField("mPopup")
        field.isAccessible = true
        val helper = field.get(this)
        val method =
            helper.javaClass.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
        method.invoke(helper, true)
    } catch (e: Exception) {
        try {
            val field = PopupMenu::class.java.getDeclaredField("mMenuPopupHelper")
            field.isAccessible = true
            val helper = field.get(this)
            val method =
                helper.javaClass.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
            method.invoke(helper, true)
        } catch (e2: Exception) {
            try {
                // For some Samsung devices
                val field = PopupMenu::class.java.getDeclaredField("mPopupContext")
                field.isAccessible = true
                val context = field.get(this)
                // Usually we don't need this; but fallback
            } catch (e3: Exception) {
                if (BuildConfig.DEBUG) Log.w("PopupMenu", "Failed to force show icons", e3)
            }
        }
    }
}