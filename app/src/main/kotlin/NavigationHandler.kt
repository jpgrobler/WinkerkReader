// NavigationHandler.kt
package za.co.jpsoft.winkerkreader

import android.widget.TextView
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry

/**
 * Handles navigation between different data views via swipe gestures
 */
object NavigationHandler {

    @JvmStatic
    fun handleLeftSwipe(activity: MainActivity2, sortOrderView: TextView) {
        when (winkerkEntry.SORTORDER) {
            "HUWELIK" -> switchTo(activity, sortOrderView, "VAN", "VAN")
            "VAN" -> switchTo(activity, sortOrderView, "GESINNE", "GESINNE")
            "GESINNE" -> switchTo(activity, sortOrderView, "WYK", "WYK")
            "WYK" -> switchTo(activity, sortOrderView, "OUDERDOM", "OUDERDOM")
            "OUDERDOM" -> switchTo(activity, sortOrderView, "ADRES", "ADRES")
            "ADRES" -> switchTo(activity, sortOrderView, "VERJAAR", "VERJAAR")
            "VERJAAR" -> switchTo(activity, sortOrderView, "HUWELIK", "HUWELIK")
        }
    }

    @JvmStatic
    fun handleRightSwipe(activity: MainActivity2, sortOrderView: TextView) {
        when (winkerkEntry.SORTORDER) {
            "HUWELIK" -> switchTo(activity, sortOrderView, "VERJAAR", "VERJAAR")
            "VERJAAR" -> switchTo(activity, sortOrderView, "ADRES", "ADRES")
            "ADRES" -> switchTo(activity, sortOrderView, "OUDERDOM", "OUDERDOM")
            "OUDERDOM" -> switchTo(activity, sortOrderView, "WYK", "WYK")
            "WYK" -> switchTo(activity, sortOrderView, "GESINNE", "GESINNE")
            "GESINNE" -> switchTo(activity, sortOrderView, "VAN", "VAN")
            "VAN" -> switchTo(activity, sortOrderView, "HUWELIK", "HUWELIK")
        }
    }

    private fun switchTo(activity: MainActivity2, sortOrderView: TextView, sortOrder: String, layout: String) {
        sortOrderView.background = null
        winkerkEntry.SORTORDER = sortOrder
        winkerkEntry.DEFLAYOUT = layout
        winkerkEntry.SOEKLIST = false
        activity.observeDataset()
    }
}