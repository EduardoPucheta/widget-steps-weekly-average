package agency.dynamicdata.steps.core

/**
 * The finished numbers the widget renders, plus enough context to explain them.
 */
data class StepsSummary(
    /** The days this average covers. */
    val window: StepsWindow,
    /** Sum of steps reported within the window. */
    val totalSteps: Long,
    /** How many days of the window the provider reported. */
    val daysWithData: Int,
    /** Which divisor produced [averageStepsPerDay]. */
    val basis: AverageBasis,
    /** [totalSteps] divided by the basis, rounded to the nearest whole step. */
    val averageStepsPerDay: Long,
) {
    /** True when nothing at all was reported — the widget shows an empty state, not a zero. */
    val hasNoData: Boolean get() = daysWithData == 0

    /** How many days the window covers. */
    val dayCount: Int get() = window.dayCount
}
