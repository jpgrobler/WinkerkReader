package za.co.jpsoft.winkerkreader.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ReminderEventBus {
    private val _refreshPending = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshPending: SharedFlow<Unit> = _refreshPending.asSharedFlow()

    fun notifyReminderChanged() {
        _refreshPending.tryEmit(Unit)
    }
}