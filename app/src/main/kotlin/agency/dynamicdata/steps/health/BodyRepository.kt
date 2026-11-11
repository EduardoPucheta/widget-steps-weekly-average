package agency.dynamicdata.steps.health

import agency.dynamicdata.steps.core.Measurement
import java.time.LocalDate

/** The body measurements the app charts. */
enum class BodyMetric {
    /** In kilograms; converted for display. */
    WEIGHT,

    /** In percent of body mass. */
    BODY_FAT,
}

/**
 * Source of body measurements — every reading, not daily totals.
 *
 * Separate from [StepsRepository] because access is separate: a person can share
 * steps and not their weight, and the steps half of the app must not notice.
 */
interface BodyRepository {

    fun availability(): HealthConnectAvailability

    suspend fun canRead(metric: BodyMetric): Boolean

    /**
     * True when Health Connect can share data older than 30 days but has not been
     * allowed to. Until then a year's chart only ever shows its last month.
     */
    suspend fun historyBlocked(): Boolean

    /**
     * Every [metric] reading taken from the start of [from] to the end of [to], at
     * its local time.
     *
     * @throws SecurityException if Health Connect refuses the read.
     */
    suspend fun measurements(metric: BodyMetric, from: LocalDate, to: LocalDate): List<Measurement>
}
