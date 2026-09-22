package agency.dynamicdata.steps.core

/**
 * What the average is divided by.
 *
 * Every day in a rolling window has already finished, so the only question left is
 * how a day the provider never reported should count. The two answers give different
 * numbers and it is worth being explicit about which one is on screen.
 */
enum class AverageBasis {
    /**
     * Divide by every day in the window.
     *
     * The default. An untracked day counts as zero, so leaving the phone at home does
     * lower the average — which is the truthful reading of "steps per day".
     */
    ALL_DAYS,

    /**
     * Divide only by days the provider actually reported.
     *
     * Answers "on days I tracked, how much did I walk". Useful when tracking coverage
     * is patchy, but two windows with different coverage cannot be compared.
     */
    DAYS_WITH_DATA,
}
