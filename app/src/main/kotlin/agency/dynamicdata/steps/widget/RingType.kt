package agency.dynamicdata.steps.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Type sizes for the ring, derived from how big the widget actually is.
 *
 * Fixed sizes were the bug this replaces: 28sp looks right on a small widget and
 * lost inside a large one, and a widget's size is the user's choice, not the
 * developer's. Everything here is a share of the shorter side, so the face fills
 * the circle the same way whatever that side turns out to be.
 */
data class RingType(
    val label: TextUnit,
    val number: TextUnit,
    val goal: TextUnit,
    val sidePadding: Dp,
) {
    companion object {
        /**
         * The widest the headline may be drawn, as a share of the shorter side.
         *
         * The ring's inner circle is `1 - 2 * (INSET_RATIO + STROKE_RATIO)` across.
         * The headline sits on the circle's centre line, where it is widest, so it
         * gets nearly all of that; the lines above and below are small enough that
         * the narrowing toward the top and bottom of the circle does not reach them.
         */
        private const val INNER_RATIO = 0.76f

        /**
         * Headline size as a share of the side.
         *
         * The worst realistic string is five glyphs — "14.1k", or "9,999" just under
         * the abbreviation threshold. Measured against the inner circle's centre
         * line, those fit up to about 0.276; this sits below that so a device whose
         * default font is wider than Roboto still has room. Glance cannot shrink
         * text to fit, so anything too wide is simply clipped.
         */
        private const val NUMBER_RATIO = 0.23f
        private const val LABEL_RATIO = 0.095f
        private const val GOAL_RATIO = 0.115f

        /** Bounds, so a tiny or an enormous widget still lands somewhere sane. */
        private const val MIN_NUMBER_SP = 18f
        private const val MAX_NUMBER_SP = 52f
        private const val MIN_SMALL_SP = 9f
        private const val MAX_SMALL_SP = 20f

        fun forSide(sideDp: Float): RingType = RingType(
            label = (sideDp * LABEL_RATIO).coerceIn(MIN_SMALL_SP, MAX_SMALL_SP).sp,
            number = (sideDp * NUMBER_RATIO).coerceIn(MIN_NUMBER_SP, MAX_NUMBER_SP).sp,
            goal = (sideDp * GOAL_RATIO).coerceIn(MIN_SMALL_SP, MAX_SMALL_SP).sp,
            // Pads in to the ring's inner circle, so text cannot sit under the stroke.
            sidePadding = (sideDp * (1f - INNER_RATIO) / 2f).dp,
        )
    }
}
