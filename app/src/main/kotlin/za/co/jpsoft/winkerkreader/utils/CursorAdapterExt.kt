package za.co.jpsoft.winkerkreader.utils

import android.database.Cursor
import androidx.cursoradapter.widget.CursorAdapter

/**
 * Extension function to safely swap cursors with automatic closing of old cursor
 */
fun CursorAdapter.safeSwapCursor(newCursor: Cursor?): Cursor? {
    val oldCursor = swapCursor(newCursor)
    oldCursor?.close()
    return oldCursor
}