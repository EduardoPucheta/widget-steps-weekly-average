package agency.dynamicdata.steps.core

import java.time.LocalDate

/** One day of the window, and what is known about it. */
data class DayEntry(
    val date: LocalDate,
    /**
     * Steps recorded that day, or null when the provider reported nothing.
     *
     * Null rather than zero, all the way to the screen. A day nobody tracked and a
     * day spent on the sofa are different facts, and a chart that draws them the
     * same way is lying about one of them.
     */
    val steps: Long?,
) {
    val isTracked: Boolean get() = steps != null
}

/**
 * Expands a sparse list of readings into one entry per day of a window.
 *
 * The repository only returns days the provider knows about, so the gaps have to be
 * put back before anything can be plotted — a chart needs a slot for every day,
 * including the ones with no answer.
 */
object DailyBreakdown {

    fun of(dailySteps: List<DailySteps>, window: StepsWindow): List<DayEntry> {
        val byDate: Map<LocalDate, Long> = dailySteps
            .filter { it.date in window }
            .groupBy { it.date }
            // Several sources for one day — a phone and a watch — are summed, the
            // same way the average does it, so the chart and the number agree.
            .mapValues { (_, entries) -> entries.sumOf { it.steps } }

        return window.dates.map { date -> DayEntry(date, byDate[date]) }
    }
}
