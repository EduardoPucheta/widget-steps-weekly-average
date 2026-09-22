package agency.dynamicdata.steps.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The seven-day window a summary covers.
 *
 * Which day a week starts on is a locale question, not a universal one: Monday in
 * most of Europe, Sunday in the US and much of Latin America. It is passed in rather
 * than assumed so the widget can follow the device locale instead of hard-coding one
 * and quietly reporting a different week than the user's other apps do.
 */
data class WeekWindow(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        require(end == start.plusDays(6)) { "a week window must span exactly 7 days, got $start..$end" }
    }

    /** Every date in the window, in order. */
    val dates: List<LocalDate> get() = (0L..6L).map { start.plusDays(it) }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    companion object {
        /** The window containing [date], given a [firstDayOfWeek]. */
        fun containing(date: LocalDate, firstDayOfWeek: DayOfWeek): WeekWindow {
            val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            return WeekWindow(start, start.plusDays(6))
        }

        /** The window [weeksAgo] weeks before the one containing [date]. */
        fun before(date: LocalDate, firstDayOfWeek: DayOfWeek, weeksAgo: Long): WeekWindow {
            val current = containing(date, firstDayOfWeek)
            return WeekWindow(current.start.minusWeeks(weeksAgo), current.end.minusWeeks(weeksAgo))
        }
    }
}
