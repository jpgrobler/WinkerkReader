package za.co.jpsoft.winkerkreader.utils

import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel

// NavigationHandler.kt


import android.widget.TextView

/**
 * Handles navigation between different data views via swipe gestures
 */
object NavigationHandler {

    @JvmStatic
    fun handleLeftSwipe(activity: MainActivity, sortOrderView: TextView, viewModel: MemberViewModel) {
        when (viewModel.sortOrder) {
            "HUWELIK" -> switchTo(activity, sortOrderView, "VAN", "VAN", viewModel)
            "VAN" -> switchTo(activity, sortOrderView, "GESINNE", "GESINNE", viewModel)
            "GESINNE" -> switchTo(activity, sortOrderView, "WYK", "WYK", viewModel)
            "WYK" -> switchTo(activity, sortOrderView, "OUDERDOM", "OUDERDOM", viewModel)
            "OUDERDOM" -> switchTo(activity, sortOrderView, "ADRES", "ADRES", viewModel)
            "ADRES" -> switchTo(activity, sortOrderView, "VERJAAR", "VERJAAR", viewModel)
            "VERJAAR" -> switchTo(activity, sortOrderView, "HUWELIK", "HUWELIK", viewModel)
        }
    }

    @JvmStatic
    fun handleRightSwipe(activity: MainActivity, sortOrderView: TextView, viewModel: MemberViewModel) {
        when (viewModel.sortOrder) {
            "HUWELIK" -> switchTo(activity, sortOrderView, "VERJAAR", "VERJAAR", viewModel)
            "VERJAAR" -> switchTo(activity, sortOrderView, "ADRES", "ADRES", viewModel)
            "ADRES" -> switchTo(activity, sortOrderView, "OUDERDOM", "OUDERDOM", viewModel)
            "OUDERDOM" -> switchTo(activity, sortOrderView, "WYK", "WYK", viewModel)
            "WYK" -> switchTo(activity, sortOrderView, "GESINNE", "GESINNE", viewModel)
            "GESINNE" -> switchTo(activity, sortOrderView, "VAN", "VAN", viewModel)
            "VAN" -> switchTo(activity, sortOrderView, "HUWELIK", "HUWELIK", viewModel)
        }
    }

    private fun switchTo(activity: MainActivity, sortOrderView: TextView, sortOrder: String, layout: String, viewModel: MemberViewModel) {
        sortOrderView.background = null
        viewModel.sortOrder = sortOrder
        SettingsManager.getInstance(activity).defLayout = layout
        viewModel.soekList = false
        activity.observeDataset()
    }
}