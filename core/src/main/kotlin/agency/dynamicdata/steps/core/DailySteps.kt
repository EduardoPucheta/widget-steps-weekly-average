package agency.dynamicdata.steps.core

import java.time.LocalDate

/**
 * Step count for a single calendar day, in the user's local time zone.
 *
 * One row per day that the health provider actually reported. A day the provider
 * knows nothing about is *absent* from the list rather than present with zero —
 * "no data" and "genuinely did not move" are different facts, and collapsing them
 * would silently drag the average down.
 */
data class DailySteps(
    val date: LocalDate,
    val steps: Long,
) {
    init {
        require(steps >= 0) { "steps must be non-negative, was $steps for $date" }
    }
}
