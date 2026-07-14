// utils/EdgeToEdgeHelper.kt
package za.co.jpsoft.winkerkreader.utils

import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

object EdgeToEdgeHelper {

    fun setupEdgeToEdge(window: Window, isLightStatusBar: Boolean = true) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insetsController.isAppearanceLightStatusBars = isLightStatusBar
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (isLightStatusBar) {
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }

        // Set navigation bar to transparent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarColor(0)
        }
    }
}