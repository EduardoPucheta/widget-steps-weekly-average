package agency.dynamicdata.steps.ui.dashboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colours for the daily chart.
 *
 * Not the ring's colours, on purpose. A 6dp stroke and a filled bar have different
 * requirements: the ring's teal is too low in chroma to read as anything but grey
 * once it fills an area, and its dark-mode step is too light for a large block.
 * These were re-stepped from the same hue and checked against the lightness band,
 * chroma floor and contrast ratio for each surface rather than picked by eye.
 */
@Immutable
data class ChartPalette(
    val bar: Color,
    /**
     * The moving-average line. Orange against the teal bars: a warm/cool pair that
     * stays apart under red-green colour blindness (ΔE 15.6 light, 16.7 dark, both
     * well clear of the 8 target) and clears 3:1 against the surface. That last one
     * matters more for a 2dp line than for a filled bar — a thin mark needs the
     * contrast a block gets away without.
     */
    val average: Color,
    /**
     * The unknown-day slot.
     *
     * A neutral shade one step off the surface, not a tint of the series colour. A
     * faded version of the bar colour reads as "a small amount of data"; a grey
     * block reads as "no answer here", which is what it means.
     */
    val absent: Color,
    /** The goal threshold rule. */
    val goalLine: Color,
    val axis: Color,
    val surface: Color,
) {
    companion object {
        @Composable
        fun forTheme(): ChartPalette = if (isSystemInDarkTheme()) Dark else Light

        private val Light = ChartPalette(
            bar = Color(0xFF0E9299),
            average = Color(0xFFD95926),
            absent = Color(0xFFDBE2DE),
            goalLine = Color(0xFF5A625C),
            axis = Color(0xFFCBD3CD),
            surface = Color(0xFFE9EDEA),
        )

        private val Dark = ChartPalette(
            bar = Color(0xFF2BA8AE),
            average = Color(0xFFD95926),
            absent = Color(0xFF262D29),
            goalLine = Color(0xFFA8B2AC),
            axis = Color(0xFF39403B),
            surface = Color(0xFF1B201D),
        )
    }
}
