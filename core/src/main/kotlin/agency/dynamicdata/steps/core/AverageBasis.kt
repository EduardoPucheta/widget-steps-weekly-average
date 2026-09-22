package agency.dynamicdata.steps.core

/**
 * What the weekly average is divided by.
 *
 * "Average steps per day" is ambiguous the moment the week is incomplete, and the
 * three sensible readings give very different numbers. Making the choice explicit
 * keeps the widget honest about which one it is showing.
 */
enum class AverageBasis {
    /**
     * Divide by every day in the week (always 7).
     *
     * Comparable week over week, but on a Monday it reports roughly one seventh of
     * a normal week and looks like a collapse in activity.
     */
    CALENDAR_DAYS,

    /**
     * Divide by the days of the week that have already happened, capped at today.
     *
     * The default. Days with no reported data still count as zero, so leaving the
     * phone at home does lower the average — which is the truthful reading of
     * "steps per day so far this week".
     */
    ELAPSED_DAYS,

    /**
     * Divide only by days the provider actually reported.
     *
     * Answers "on days I tracked, how much did I walk" and is the right basis when
     * tracking coverage is patchy, but it can not be compared across weeks with
     * different coverage.
     */
    DAYS_WITH_DATA,
}
