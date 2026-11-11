package agency.dynamicdata.steps.core

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** The trend's value at one reading. */
data class TrendPoint(
    val at: LocalDateTime,
    val value: Double,
)

/**
 * A smoothed line through the readings, so the direction shows through the
 * day-to-day noise (water, salt, time of day) that makes single readings jumpy.
 *
 * Each reading gets the mean of every reading within [HALF_WINDOW_DAYS] days either
 * side of it. A window in days rather than a count of readings, because readings are
 * irregular: "the last 7" is a week for someone who weighs daily and two months for
 * someone who doesn't, and the line would mean different things for each. Centred
 * rather than trailing, so the line sits on the data instead of lagging behind it —
 * except at the newest end, where there is no future to centre on.
 */
object MeasurementTrend {

    /** 15 days either side: a 30-day trend. */
    const val HALF_WINDOW_DAYS: Long = 15

    /**
     * Readings further apart than this break the line. A line drawn across two
     * months with nothing measured would claim a smooth change nobody saw.
     */
    const val GAP_DAYS: Long = 30

    fun of(readings: List<Measurement>, halfWindowDays: Long = HALF_WINDOW_DAYS): List<TrendPoint> {
        val sorted = readings.sortedBy { it.at }
        return sorted.map { reading ->
            val near = sorted.filter { abs(daysBetween(it.at, reading.at)) <= halfWindowDays }
            TrendPoint(reading.at, near.sumOf { it.value } / near.size)
        }
    }

    /** The trend split wherever two neighbouring points are more than [gapDays] apart. */
    fun runs(points: List<TrendPoint>, gapDays: Long = GAP_DAYS): List<List<TrendPoint>> {
        val runs = mutableListOf<MutableList<TrendPoint>>()
        points.sortedBy { it.at }.forEach { point ->
            val current = runs.lastOrNull()
            if (current == null || daysBetween(current.last().at, point.at) > gapDays) {
                runs += mutableListOf(point)
            } else {
                current += point
            }
        }
        return runs
    }

    private fun daysBetween(from: LocalDateTime, to: LocalDateTime): Long = ChronoUnit.DAYS.between(from, to)
}
