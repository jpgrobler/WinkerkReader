package za.co.jpsoft.winkerkreader.ui.helpers

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.ui.adapters.MemberListAdapter

object MemberListScrollHelper {

    data class ScrollState(
        val position: Int,          // fallback position
        val offset: Int,
        val itemId: Long = -1L      // unique ID of the first visible item
    )

    fun saveScrollState(recyclerView: RecyclerView, adapter: MemberListAdapter?): ScrollState? {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return null
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return null
        val view = layoutManager.findViewByPosition(position)
        val offset = view?.top ?: 0

        // Get the item ID from the first visible item (if any)
        val items = adapter?.snapshot() ?: emptyList()
        val itemId = items.getOrNull(position)?.id ?: -1L
        return ScrollState(position, offset, itemId)
    }

    fun restoreScrollState(
        recyclerView: RecyclerView,
        state: ScrollState?,
        adapter: MemberListAdapter?
    ) {
        if (state == null) return

        // Try to restore by item ID first
        if (state.itemId != -1L && adapter != null) {
            val items = adapter.snapshot() // List<MemberItem?>
            val newPosition = items.indexOfFirst { it?.id == state.itemId }
            if (newPosition != -1) {
                recyclerView.post {
                    (recyclerView.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(newPosition, state.offset)
                }
                return
            }
        }

        // Fallback: use the saved position + offset
        recyclerView.post {
            (recyclerView.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(state.position, state.offset)
        }
    }
}