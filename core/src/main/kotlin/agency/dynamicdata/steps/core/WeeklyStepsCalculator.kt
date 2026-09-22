package agency.dynamicdata.steps.core

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Turns a list of daily step totals into the one number the widget shows.
 *
 * Pure and time-zone free on purpose: the caller resolves "what day is it" once, at
 * the edge, and passes [today] in. That keeps the arithmetic testable without
 * freezing a clock, and keeps the daylight-saving and time-zone handling in the one
 * place that reads from Health Connect.
 */
class WeeklyStepsCalculator(
    private val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    private val basis: AverageBasis = AverageBasis.ELAPSED_DAYS,
) {

    /** Summary for the week containing [today]. */
    fun summarizeCurrentWeek(dailySteps: List<DailySteps>, today: LocalDate): WeeklyStepsSummary =
        summarize(dailySteps, WeekWindow.containing(today, firstDayOfWeek), today)

    /**
     * Summary for an explicit [window].
     *
     * Days outside the window are ignored, so the caller may hand over a longer
     * history without pre-filtering. Duplicate entries for the same date are summed,
     * which is what a provider returning several sources for one day means.
     */
    fun summarize(
        dailySteps: List<DailySteps>,
        window: WeekWindow,
        today: LocalDate,
    ): WeeklyStepsSummary {
        val stepsByDate: Map<LocalDate, Long> = dailySteps
            .filter { it.date in window }
            .groupBy { it.date }
            .mapValues { (_, entries) -> entries.sumOf { it.steps } }

        val totalSteps = stepsByDate.values.sum()
        val daysWithData = stepsByDate.size
        val elapsedDays = elapsedDaysIn(window, today)

        val divisor = when (basis) {
            AverageBasis.CALENDAR_DAYS -> WeeklyStepsSummary.DAYS_IN_WEEK
            AverageBasis.ELAPSED_DAYS -> elapsedDays
            AverageBasis.DAYS_WITH_DATA -> daysWithData
        }

        return WeeklyStepsSummary(
            weekStart = window.start,
            weekEnd = window.end,
            totalSteps = totalSteps,
            daysWithData = daysWithData,
            elapsedDays = elapsedDays,
            basis = basis,
            // A zero divisor is reachable: DAYS_WITH_DATA with nothing reported, or a
            // window entirely in the future. Both mean "no average exists", not zero
            // steps — `hasNoData` is what the UI branches on.
            averageStepsPerDay = if (divisor <= 0) 0L else divideRounded(totalSteps, divisor),
        )
    }

    /**
     * How many days of [window] have started as of [today], clamped to 1..7.
     *
     * A finished week gives 7. A future week has not started, but returning 0 would
     * make the average undefined for a reason the UI already covers with
     * [WeeklyStepsSummary.hasNoData], so it clamps to 1 and reports a zero total.
     */
    private fun elapsedDaysIn(window: WeekWindow, today: LocalDate): Int = when {
        today.isAfter(window.end) -> WeeklyStepsSummary.DAYS_IN_WEEK
        today.isBefore(window.start) -> 1
        else -> today.toEpochDay().minus(window.start.toEpochDay()).toInt() + 1
    }

    /** Integer division rounding half away from zero, so 3.5 reports as 4 not 3. */
    private fun divideRounded(total: Long, divisor: Int): Long =
        (total + divisor / 2) / divisor
}
