package agency.dynamicdata.steps.core

/**
 * How the current window compares with the one immediately before it.
 *
 * Only meaningful when both are measured the same way, so [of] refuses to compare
 * summaries built on different bases or over different numbers of days.
 */
data class StepsTrend(
    val currentAverage: Long,
    val previousAverage: Long,
) {
    val deltaSteps: Long get() = currentAverage - previousAverage

    /**
     * Change as a fraction of the previous window, or null when that averaged zero —
     * dividing by it would report an infinite improvement over having done nothing.
     */
    val deltaFraction: Double?
        get() = if (previousAverage == 0L) null else deltaSteps.toDouble() / previousAverage

    val direction: Direction
        get() = when {
            deltaSteps > 0 -> Direction.UP
            deltaSteps < 0 -> Direction.DOWN
            else -> Direction.FLAT
        }

    enum class Direction { UP, DOWN, FLAT }

    companion object {
        /**
         * Trend between two summaries, or null when either has no data at all — a
         * comparison against a blank window is noise, not a signal.
         */
        fun of(current: StepsSummary, previous: StepsSummary): StepsTrend? {
            require(current.basis == previous.basis) {
                "cannot compare a ${current.basis} average with a ${previous.basis} one"
            }
            require(current.dayCount == previous.dayCount) {
                "cannot compare a ${current.dayCount}-day average with a ${previous.dayCount}-day one"
            }
            if (current.hasNoData || previous.hasNoData) return null
            return StepsTrend(current.averageStepsPerDay, previous.averageStepsPerDay)
        }
    }
}
