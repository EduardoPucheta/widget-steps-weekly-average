package agency.dynamicdata.steps.core

import java.time.LocalDateTime

/**
 * One body measurement — a weigh-in, a body-fat reading — at the local time it was
 * taken, in the measurement's own unit (kilograms, percent).
 *
 * Kept as recorded, every submission rather than one per day, because the body
 * charts exist to show each of them. Two readings on the same day are two dots.
 */
data class Measurement(
    val at: LocalDateTime,
    val value: Double,
) {
    init {
        require(value > 0.0) { "a body measurement must be positive, was $value at $at" }
    }
}
