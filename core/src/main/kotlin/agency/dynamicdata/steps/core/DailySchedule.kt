package agency.dynamicdata.steps.core

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * When a once-a-day reminder should next fire.
 *
 * Pure, and takes the current time rather than reading a clock, so the awkward
 * cases — already past the hour, a day that has no 09:00 because the clocks jumped,
 * a day with two — can be tested without waiting for a March morning.
 */
object DailySchedule {

    /**
     * The next moment at [timeOfDay] strictly after [now], in [now]'s zone.
     *
     * "Strictly" matters: firing at exactly 09:00 and then asking for the next
     * occurrence must give tomorrow, not the same instant again, or the reminder
     * reschedules itself into a loop.
     */
    fun next(now: ZonedDateTime, timeOfDay: LocalTime): ZonedDateTime {
        val todayAt = resolve(now, timeOfDay, daysFromNow = 0)
        return if (todayAt.isAfter(now)) todayAt else resolve(now, timeOfDay, daysFromNow = 1)
    }

    /**
     * [timeOfDay] on a given day, adjusted for clock changes.
     *
     * On the morning a zone springs forward, the wall clock can skip the hour
     * entirely; `ZonedDateTime.of` then moves to the next valid instant, which is
     * what a person expects from an alarm. On the morning it falls back the hour
     * happens twice and the earlier of the two is used, so the reminder does not
     * arrive an hour late.
     */
    private fun resolve(now: ZonedDateTime, timeOfDay: LocalTime, daysFromNow: Long): ZonedDateTime =
        now.toLocalDate()
            .plusDays(daysFromNow)
            .atTime(timeOfDay)
            .atZone(now.zone)
            .withEarlierOffsetAtOverlap()
}
