package agency.dynamicdata.steps.core

import java.time.LocalDate

/**
 * A run of consecutive whole days that an average is taken over.
 *
 * Deliberately *not* a calendar week. The widget reports a rolling window that moves
 * forward every midnight, so "this week" never resets on a Monday and the number
 * always covers the same amount of time.
 */
data class StepsWindow(
    /** First day, inclusive. */
    val start: LocalDate,
    /** Last day, inclusive. */
    val end: LocalDate,
) {
    init {
        require(!end.isBefore(start)) { "a window must end on or after it starts, got $start..$end" }
    }

    /** Number of days covered, counting both ends. */
    val dayCount: Int get() = (end.toEpochDay() - start.toEpochDay()).toInt() + 1

    /** Every date in the window, in order. */
    val dates: List<LocalDate> get() = (0L until dayCount.toLong()).map { start.plusDays(it) }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    /** The window of the same length immediately before this one, for comparisons. */
    fun preceding(): StepsWindow =
        StepsWindow(start.minusDays(dayCount.toLong()), start.minusDays(1))

    companion object {
        const val DEFAULT_DAYS: Int = 7

        /**
         * The last [days] complete days as of [today] — ending yesterday.
         *
         * Today is excluded on purpose. A day in progress contributes a partial count
         * but would occupy a whole slot in the divisor, so including it makes the
         * average sag every morning and creep back up by evening. Ending at yesterday
         * gives a figure that is stable all day and only moves at midnight.
         */
        fun lastCompleteDays(today: LocalDate, days: Int = DEFAULT_DAYS): StepsWindow {
            require(days > 0) { "a window needs at least one day, got $days" }
            val end = today.minusDays(1)
            return StepsWindow(end.minusDays(days - 1L), end)
        }
    }
}
