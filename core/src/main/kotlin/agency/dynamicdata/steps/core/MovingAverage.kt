package agency.dynamicdata.steps.core

import java.time.LocalDate

/** The trailing average as it stood at the end of [date]. */
data class MovingAveragePoint(
    val date: LocalDate,
    /**
     * Steps per day over the [MovingAverage.of] span ending on [date], or null when
     * nothing at all was recorded in that span — the same "no reading is not a zero"
     * rule the headline follows.
     */
    val averageStepsPerDay: Long?,
)

/**
 * The rolling average, evaluated at every day of a window.
 *
 * Each point is computed by the same [StepsAverageCalculator] as the headline, over
 * the span of days ending on that date. The last point's span is the window itself,
 * so the line's right-hand end is the headline figure by construction rather than by
 * two pieces of arithmetic happening to agree.
 */
object MovingAverage {

    /**
     * The days that must be read to plot a [days]-day average across [window].
     *
     * The first point needs the [days] − 1 days before the window starts, so a 7-day
     * window with a 7-day average needs 13 days of readings, not 7.
     */
    fun readWindowFor(window: StepsWindow, days: Int = StepsWindow.DEFAULT_DAYS): StepsWindow {
        require(days > 0) { "an average needs at least one day, got $days" }
        return StepsWindow(window.start.minusDays(days - 1L), window.end)
    }

    fun of(
        readings: List<DailySteps>,
        window: StepsWindow,
        calculator: StepsAverageCalculator,
        days: Int = StepsWindow.DEFAULT_DAYS,
    ): List<MovingAveragePoint> = window.dates.map { date ->
        val span = StepsWindow(date.minusDays(days - 1L), date)
        val summary = calculator.summarize(readings, span)
        MovingAveragePoint(
            date = date,
            averageStepsPerDay = if (summary.hasNoData) null else summary.averageStepsPerDay,
        )
    }
}
