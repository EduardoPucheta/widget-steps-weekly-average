package agency.dynamicdata.steps.ui.dashboard

import kotlin.math.abs

/**
 * Keeps two labels in the chart's right-hand gutter from landing on each other.
 *
 * The goal label sits at the goal line's height and the average label at the end of
 * the average line. When the average is near the goal — the most interesting week to
 * look at — those two heights nearly coincide, and the labels would overprint.
 */
internal object GutterLabels {

    /**
     * Vertical centres for the [goal] and [average] labels, at least [minGap] apart,
     * both kept between [top] and [bottom].
     *
     * Each label moves as little as possible, and the pair keeps its order: if the
     * average is above the goal on the chart, its label stays above the goal's, so a
     * reader can match each label to its line by position.
     */
    fun separate(
        goal: Float,
        average: Float,
        minGap: Float,
        top: Float,
        bottom: Float,
    ): Pair<Float, Float> {
        require(bottom - top >= minGap) { "no room for two labels between $top and $bottom" }
        if (abs(goal - average) >= minGap) return clamp(goal, top, bottom) to clamp(average, top, bottom)

        val averageAbove = average <= goal
        val middle = (goal + average) / 2f
        var upper = middle - minGap / 2f
        var lower = middle + minGap / 2f

        // Slide the pair back inside the plot rather than squashing it.
        if (upper < top) {
            lower += top - upper
            upper = top
        }
        if (lower > bottom) {
            upper -= lower - bottom
            lower = bottom
        }

        return if (averageAbove) lower to upper else upper to lower
    }

    private fun clamp(value: Float, top: Float, bottom: Float) = value.coerceIn(top, bottom)
}
