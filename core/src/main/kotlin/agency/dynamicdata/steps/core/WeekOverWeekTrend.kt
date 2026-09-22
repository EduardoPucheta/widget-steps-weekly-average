package agency.dynamicdata.steps.core

/**
 * How this week's average compares with last week's.
 *
 * Only meaningful when both weeks are measured the same way, so [of] refuses to
 * compare summaries built on different bases.
 */
data class WeekOverWeekTrend(
    val currentAverage: Long,
    val previousAverage: Long,
) {
    val deltaSteps: Long get() = currentAverage - previousAverage

    /**
     * Change as a fraction of last week, or null when last week averaged zero —
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
         * Trend between two summaries, or null when either week has no data at all
         * — a comparison against a blank week is noise, not a signal.
         */
        fun of(current: WeeklyStepsSummary, previous: WeeklyStepsSummary): WeekOverWeekTrend? {
            require(current.basis == previous.basis) {
                "cannot compare a ${current.basis} average with a ${previous.basis} one"
            }
            if (current.hasNoData || previous.hasNoData) return null
            return WeekOverWeekTrend(current.averageStepsPerDay, previous.averageStepsPerDay)
        }
    }
}
