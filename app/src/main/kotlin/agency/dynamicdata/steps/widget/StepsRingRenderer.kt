package agency.dynamicdata.steps.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap

/**
 * Draws the progress ring into a Bitmap.
 *
 * Glance has no arc primitive — its `CircularProgressIndicator` is indeterminate
 * only — so a ring showing an actual value has to be rasterised and shown as an
 * `Image`. That is the supported route, not a workaround.
 */
object StepsRingRenderer {

    /**
     * A widget's RemoteViews payload is capped at roughly 1.5 MB, and a bitmap counts
     * against it at 4 bytes a pixel. 512px squared is 1 MB, which leaves room for the
     * rest of the view tree; beyond that the launcher drops the widget entirely.
     */
    private const val MAX_SIDE_PX = 512

    /** Below this the ring is a smudge, so it is not worth drawing smaller. */
    private const val MIN_SIDE_PX = 96

    /** Ring thickness as a share of the widget's width. */
    private const val STROKE_RATIO = 0.075f

    /** Gap between the disc's edge and the outside of the ring. */
    private const val INSET_RATIO = 0.045f

    /** Arcs are measured from 3 o'clock; the ring should start at 12. */
    private const val START_ANGLE_DEGREES = -90f

    private const val FULL_CIRCLE_DEGREES = 360f

    /** Opacity of the completed first lap, once a second one is drawn over it. */
    private const val COMPLETED_LAP_ALPHA = 90

    /**
     * @param sidePx requested width and height in pixels, clamped to a safe range.
     * @param fraction first-lap progress in 0..1.
     * @param overflowFraction second-lap progress in 0..1, drawn over a completed
     *   first lap. Without it a ring at 101% and one at 300% are the same picture.
     * @param goalMet switches the ring to the "met" colour.
     * @param night resolves the palette; passed in rather than read from a Context so
     *   the ring and the text on top of it cannot disagree about the theme.
     * @param showTrack draws the unfilled remainder; false for states with no value
     *   to show, where an empty track would imply a real zero.
     */
    fun render(
        sidePx: Int,
        fraction: Float,
        overflowFraction: Float = 0f,
        goalMet: Boolean,
        night: Boolean,
        showTrack: Boolean = true,
    ): Bitmap {
        val side = sidePx.coerceIn(MIN_SIDE_PX, MAX_SIDE_PX)
        val bitmap = createBitmap(side, side)
        val canvas = Canvas(bitmap)

        val centre = side / 2f
        val strokeWidth = side * STROKE_RATIO
        val inset = strokeWidth / 2f + side * INSET_RATIO

        // The disc. Drawn here rather than as a Glance background so the widget's
        // corners stay transparent and the shape reads as a circle on the wallpaper.
        canvas.drawCircle(
            centre,
            centre,
            centre,
            fill(StepsRingPalette.surface.toColorInt(night)),
        )

        val bounds = RectF(inset, inset, side - inset, side - inset)

        if (showTrack) {
            canvas.drawArc(
                bounds,
                START_ANGLE_DEGREES,
                FULL_CIRCLE_DEGREES,
                false,
                stroke(StepsRingPalette.track.toColorInt(night), strokeWidth),
            )
        }

        val ringColour =
            (if (goalMet) StepsRingPalette.accentMet else StepsRingPalette.accent)
                .toColorInt(night)
        val lap = overflowFraction.coerceIn(0f, 1f)

        if (fraction > 0f) {
            canvas.drawArc(
                bounds,
                START_ANGLE_DEGREES,
                fraction.coerceIn(0f, 1f) * FULL_CIRCLE_DEGREES,
                false,
                // The completed lap fades once a second one sits on top, so the two
                // stay tellable apart where they overlap.
                stroke(
                    colour = if (lap > 0f) ringColour.withAlpha(COMPLETED_LAP_ALPHA) else ringColour,
                    width = strokeWidth,
                    rounded = lap == 0f,
                ),
            )
        }

        if (lap > 0f) {
            canvas.drawArc(
                bounds,
                START_ANGLE_DEGREES,
                lap * FULL_CIRCLE_DEGREES,
                false,
                stroke(ringColour, strokeWidth, rounded = true),
            )
        }

        return bitmap
    }

    private fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha shl 24)

    private fun fill(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colour
    }

    private fun stroke(colour: Int, width: Float, rounded: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = colour
            strokeWidth = width
            // A round cap on the progress arc gives the leading dot for free; the
            // track keeps a butt cap so a full circle does not overlap itself.
            strokeCap = if (rounded) Paint.Cap.ROUND else Paint.Cap.BUTT
        }
}
