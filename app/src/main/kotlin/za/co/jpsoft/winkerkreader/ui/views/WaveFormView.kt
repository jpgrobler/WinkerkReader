package za.co.jpsoft.winkerkreader.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * A custom view that draws a series of vertical bars whose heights animate
 * based on the audio RMS amplitude received from the speech recogniser.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // Current amplitude in range 0..1 (scaled from RMS)
    private var amplitude = 0f

    // Number of bars to draw
    private var barCount = 20

    // Optional idle animation (gentle shimmer when not recording)
    private var idleAnimator: ValueAnimator? = null

    init {
        // Idle animation: subtle breathing effect when not recording
        idleAnimator = ValueAnimator.ofFloat(0.1f, 0.3f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                if (amplitude < 0.01f) {  // only when not actively recording
                    amplitude = it.animatedValue as Float
                    invalidate()
                }
            }
        }
        startIdleAnimation()
    }

    /**
     * Call this from the UI thread whenever a new RMS value is received.
     * @param rmsdB The RMS value in decibels (typically negative, e.g. -20 to 0).
     */
    fun updateAmplitude(rmsdB: Float) {
        // Map decibels (typically -30..0) to 0..1
        // Clamp to -30..0, then map to 0..1
        val clamped = rmsdB.coerceIn(-30f, 0f)
        val newAmplitude = (clamped + 30f) / 30f  // 0 → 0, -30 → 0, 0 → 1
        amplitude = newAmplitude.coerceIn(0f, 1f)
        invalidate()
    }

    /**
     * Call when recording starts to stop the idle animation and show live data.
     */
    fun startRecording() {
        idleAnimator?.cancel()
        // Optionally set a default amplitude if the first RMS is slow
        amplitude = 0.2f
        invalidate()
    }

    /**
     * Call when recording stops to revert to idle animation.
     */
    fun stopRecording() {
        amplitude = 0f
        invalidate()
        startIdleAnimation()
    }

    private fun startIdleAnimation() {
        idleAnimator?.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width == 0f || height == 0f) return

        // ✅ Simple math calculations (not expensive)
        for (i in 0 until barCount) {
            val variation = 0.5f + 0.5f * sin(i.toDouble() / barCount * 2 * Math.PI).toFloat()
            val barHeight = height * amplitude * variation * 0.9f
            canvas.drawRect(
                left.toFloat(),
                top.toFloat(),
                right.toFloat(),
                bottom.toFloat(),
                paint
            )  // ✅ Single paint object (reused)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        idleAnimator?.cancel()
    }
}