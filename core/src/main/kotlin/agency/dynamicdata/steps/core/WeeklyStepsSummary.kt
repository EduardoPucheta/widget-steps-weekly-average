package agency.dynamicdata.steps.core

import java.time.LocalDate

/**
 * The finished numbers the widget renders, plus enough context to explain them.
 */
data class WeeklyStepsSummary(
    /** First day of the week, inclusive. */
    val weekStart: LocalDate,
    /** Last day of the week, inclusive. Always [weekStart] + 6, even mid-week. */
    val weekEnd: LocalDate,
    /** Sum of steps reported within the week. */
    val totalSteps: Long,
    /** How many days of the week the provider reported, 0..7. */
    val daysWithData: Int,
    /** How many days of the week have started, 1..7. Equals 7 for a past week. */
    val elapsedDays: Int,
    /** Which divisor produced [averageStepsPerDay]. */
    val basis: AverageBasis,
    /** [totalSteps] divided by the basis, rounded to the nearest whole step. */
    val averageStepsPerDay: Long,
) {
    /** True while the week is still running, i.e. the average can still move. */
    val isPartialWeek: Boolean get() = elapsedDays < DAYS_IN_WEEK

    /** True when nothing at all was reported — the widget shows an empty state, not a zero. */
    val hasNoData: Boolean get() = daysWithData == 0

    companion object {
        const val DAYS_IN_WEEK: Int = 7
    }
}
