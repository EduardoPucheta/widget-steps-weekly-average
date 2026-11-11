package agency.dynamicdata.steps.core

import java.time.LocalDate

/**
 * Twelve months ending on [end], both ends inclusive: the span the body charts show.
 *
 * Rolling rather than a calendar year, so the current view is always a full year —
 * in January a calendar year would be a few dots and nothing to read a trend from.
 * Earlier years are the same span stepped back a whole year at a time, so every
 * page covers the same amount of time and pages never overlap.
 */
data class YearPeriod(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        require(!end.isBefore(start)) { "a period must end on or after it starts, got $start..$end" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    companion object {
        /** The year ending [today], or [yearsBack] whole years before it. */
        fun ending(today: LocalDate, yearsBack: Int = 0): YearPeriod {
            require(yearsBack >= 0) { "cannot look forward, got $yearsBack years back" }
            val end = today.minusYears(yearsBack.toLong())
            return YearPeriod(end.minusYears(1).plusDays(1), end)
        }
    }
}
