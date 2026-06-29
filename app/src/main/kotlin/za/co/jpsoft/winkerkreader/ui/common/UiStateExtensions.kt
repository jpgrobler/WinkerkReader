// File: ui/common/UiStateExtensions.kt
package za.co.jpsoft.winkerkreader.ui.common

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Collect a UiState Flow safely in a Fragment/Activity.
 * Provides automatic handling of loading, success, and error states.
 */
inline fun <T> LifecycleOwner.collectUiState(
    flow: Flow<UiState<T>>,
    crossinline onLoading: () -> Unit = {},   // now non‑nullable with empty default
    crossinline onSuccess: (T) -> Unit,
    crossinline onError: (String) -> Unit
) {
    lifecycleScope.launch {
        flow.collectLatest { state ->
            when (state) {
                is UiState.Loading -> onLoading()
                is UiState.Success -> onSuccess(state.data)
                is UiState.Error -> onError(state.message)
            }
        }
    }
}