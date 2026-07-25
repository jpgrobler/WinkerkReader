package za.co.jpsoft.winkerkreader.utils

import android.graphics.Color

/**
 * Shared colour utility functions.
 *
 * Previously isColorDark() was copy-pasted in both MainActivity and
 * UitlegVertoonFragment. This object is the single source of truth.
 *
 * Usage:
 *   ColorUtils.isColorDark(color)
 *   // or with the extension:
 *   color.isDark()
 */
object ColorUtils {

    /**
     * Returns true when the perceived luminance of [color] is dark enough
     * that white text should be used on top of it.
     *
     * Uses the standard relative-luminance formula (ITU-R BT.601):
     *   darkness = 1 − (0.299·R + 0.587·G + 0.114·B) / 255
     * A value ≥ 0.5 is considered dark.
     */
    fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (
                0.299 * Color.red(color) +
                        0.587 * Color.green(color) +
                        0.114 * Color.blue(color)
                ) / 255
        return darkness >= 0.5
    }

    /**
     * Returns [Color.WHITE] or [Color.BLACK] depending on which gives
     * better contrast against [backgroundColor].
     *
     * Convenience wrapper used by chip and colour-preview views.
     */
    fun contrastingTextColor(backgroundColor: Int): Int =
        if (isColorDark(backgroundColor)) Color.WHITE else Color.BLACK
}

/** Extension so call sites can write `color.isDark()` instead of `ColorUtils.isColorDark(color)`. */
fun Int.isDark(): Boolean = ColorUtils.isColorDark(this)