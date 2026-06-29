// File: ui/common/UiState.kt
package za.co.jpsoft.winkerkreader.ui.common

/**
 * Represents the state of a UI component that loads data.
 * @param T The type of data when successful.
 */
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : UiState<Nothing>()
}