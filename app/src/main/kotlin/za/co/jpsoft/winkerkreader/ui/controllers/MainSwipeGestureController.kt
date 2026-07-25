package za.co.jpsoft.winkerkreader.ui.controllers

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/**
 * Handles swipe gestures on the main activity.
 * Calls [onSwipeLeft] or [onSwipeRight] when a fling is detected.
 */
class MainSwipeGestureController(
    private val activity: AppCompatActivity,
    private val onSwipeLeft: () -> Unit,
    private val onSwipeRight: () -> Unit
) {

    private val gestureDetector = GestureDetector(
        activity,
        object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_MIN_DISTANCE = 80      // Reduced from 120 (easier to trigger)
            private val SWIPE_MAX_OFF_PATH = 150     // Reduced from 200 (more precise)
            private val SWIPE_THRESHOLD_VELOCITY = 150  // Reduced from 200 (easier to trigger)

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                try {
                    val diffAbs = Math.abs(e1.y - e2.y)
                    val diff = e1.x - e2.x
                    if (diffAbs > SWIPE_MAX_OFF_PATH) return false
                    if (diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                        onSwipeLeft()
                        return true
                    } else if (-diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                        onSwipeRight()
                        return true
                    }
                } catch (e: Exception) {
                    // Log error if needed, but we don't have a logger here – the activity can handle it.
                }
                return false
            }
        }
    )

    /**
     * Call this from the Activity's onTouchEvent.
     * @return true if the gesture was handled.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    /**
     * Call this from the Activity's dispatchTouchEvent to capture all touch events.
     * Usually you can just call onTouchEvent from dispatchTouchEvent and return true
     * if it's handled, otherwise call super.
     */
    fun handleTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    /**
     * Forwards [event] to the gesture detector only when the touch falls
     * *outside* [excludedView].
     *
     * Replaces the two-step pattern in MainActivity.dispatchTouchEvent():
     *   if (isTouchInsideView(event, chipView)) return super.dispatchTouchEvent(event)
     *   swipeGestureController.handleTouchEvent(event)
     *
     * New call site (MainActivity.dispatchTouchEvent):
     *   swipeGestureController.handleTouchEventIfOutside(event, binding.chipScrollView)
     *   return super.dispatchTouchEvent(event)
     */
    fun handleTouchEventIfOutside(event: MotionEvent, excludedView: View) {
        if (!isTouchInsideView(event, excludedView)) {
            gestureDetector.onTouchEvent(event)
        }
    }

    /**
     * Returns true when [event] raw coordinates fall within [view]'s screen bounds.
     *
     * Moved from MainActivity where it was a private helper that existed solely
     * to gate whether this controller receives a touch event.
     */
    private fun isTouchInsideView(event: MotionEvent, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = event.rawX
        val y = event.rawY
        return x >= location[0] && x <= location[0] + view.width &&
                y >= location[1] && y <= location[1] + view.height
    }
}