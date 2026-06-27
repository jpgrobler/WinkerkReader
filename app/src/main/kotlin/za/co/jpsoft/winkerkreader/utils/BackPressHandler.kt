package za.co.jpsoft.winkerkreader.utils

import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.ui.viewmodels.MainViewModel

/**
 * Handles the back-press behaviour for the main activity.
 * Uses [MainViewModel.filterVisible] to decide whether to cancel the filter or finish the activity.
 */
class BackPressHandler(
    private val activity: AppCompatActivity,
    private val mainViewModel: MainViewModel,
    private val onCancelFilter: () -> Unit,
    private val onFinish: () -> Unit
) {

    private var callback: OnBackPressedCallback? = null

    /**
     * Register the back-press callback with the activity's [OnBackPressedDispatcher].
     * Call this once during activity creation.
     */
    fun register() {
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mainViewModel.filterVisible.value) {
                    onCancelFilter()
                } else {
                    onFinish()
                }
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, callback!!)
    }

    /**
     * Optionally remove the callback (e.g., if you need to disable it temporarily).
     * Usually not needed as it's tied to the activity lifecycle.
     */
    fun unregister() {
        callback?.remove()
        callback = null
    }
}