package za.co.jpsoft.winkerkreader.utils

import za.co.jpsoft.winkerkreader.ui.activities.MainActivity

// NavigationHandler.kt


import android.widget.TextView

/**
 * Handles navigation between different data views via swipe gestures
 */
object NavigationHandler {

    @JvmStatic
    fun handleLeftSwipe(activity: MainActivity, sortOrderView: TextView) {
        when (AppSessionState.sortOrder) {
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
    fun handleRightSwipe(activity: MainActivity, sortOrderView: TextView) {
        when (AppSessionState.sortOrder) {
            "HUWELIK" -> switchTo(activity, sortOrderView, "VERJAAR", "VERJAAR")
            "VERJAAR" -> switchTo(activity, sortOrderView, "ADRES", "ADRES")
            "ADRES" -> switchTo(activity, sortOrderView, "OUDERDOM", "OUDERDOM")
            "OUDERDOM" -> switchTo(activity, sortOrderView, "WYK", "WYK")
            "WYK" -> switchTo(activity, sortOrderView, "GESINNE", "GESINNE")
            "GESINNE" -> switchTo(activity, sortOrderView, "VAN", "VAN")
            "VAN" -> switchTo(activity, sortOrderView, "HUWELIK", "HUWELIK")
        }
    }

    private fun switchTo(activity: MainActivity, sortOrderView: TextView, sortOrder: String, layout: String) {
        sortOrderView.background = null
        AppSessionState.sortOrder = sortOrder
        za.co.jpsoft.winkerkreader.utils.SettingsManager(activity).defLayout = layout
        AppSessionState.soekList = false
        activity.observeDataset()
    }
}