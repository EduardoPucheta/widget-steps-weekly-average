package agency.dynamicdata.steps.core

import java.time.LocalDate

/**
 * Turns a list of daily step totals into the one number the widget shows.
 *
 * Pure and time-zone free on purpose: the caller resolves "what day is it" once, at
 * the edge, and passes [today] in. That keeps the arithmetic testable without
 * freezing a clock, and keeps daylight-saving and time-zone handling in the one place
 * that reads from Health Connect.
 */
class StepsAverageCalculator(
    private val days: Int = StepsWindow.DEFAULT_DAYS,
    private val basis: AverageBasis = AverageBasis.ALL_DAYS,
) {

    /** Summary for the last [days] complete days as of [today], ending yesterday. */
    fun summarizeRecentDays(dailySteps: List<DailySteps>, today: LocalDate): StepsSummary =
        summarize(dailySteps, StepsWindow.lastCompleteDays(today, days))

    /**
     * Summary for an explicit [window].
     *
     * Days outside the window are ignored, so the caller may hand over a longer
     * history without pre-filtering. Duplicate entries for the same date are summed,
     * which is what a provider returning several sources for one day means.
     */
    fun summarize(dailySteps: List<DailySteps>, window: StepsWindow): StepsSummary {
        val stepsByDate: Map<LocalDate, Long> = dailySteps
            .filter { it.date in window }
            .groupBy { it.date }
            .mapValues { (_, entries) -> entries.sumOf { it.steps } }

        val totalSteps = stepsByDate.values.sum()
        val daysWithData = stepsByDate.size

        val divisor = when (basis) {
            AverageBasis.ALL_DAYS -> window.dayCount
            AverageBasis.DAYS_WITH_DATA -> daysWithData
        }

        return StepsSummary(
            window = window,
            totalSteps = totalSteps,
            daysWithData = daysWithData,
            basis = basis,
            // A zero divisor means DAYS_WITH_DATA with nothing reported. That is "no
            // average exists", not zero steps — `hasNoData` is what the UI branches on.
            averageStepsPerDay = if (divisor <= 0) 0L else divideRounded(totalSteps, divisor),
        )
    }

    /** Integer division rounding half away from zero, so 3.5 reports as 4 not 3. */
    private fun divideRounded(total: Long, divisor: Int): Long = (total + divisor / 2) / divisor
}
