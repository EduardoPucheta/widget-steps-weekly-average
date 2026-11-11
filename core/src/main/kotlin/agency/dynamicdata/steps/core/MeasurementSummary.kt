package agency.dynamicdata.steps.core

/**
 * The few numbers above a body chart.
 *
 * [change] is measured on the trend, not on the raw readings. The first and last
 * weigh-ins alone can disagree by a kilo just from the time of day they were taken,
 * which would swamp a real change over the year.
 */
data class MeasurementSummary(
    val latest: Measurement,
    val lowest: Measurement,
    val highest: Measurement,
    val count: Int,
    /** Trend at the end minus trend at the start; null with fewer than two readings. */
    val change: Double?,
) {
    companion object {
        /** Null when there is nothing to summarise — not a summary of zeros. */
        fun of(readings: List<Measurement>, trend: List<TrendPoint>): MeasurementSummary? {
            if (readings.isEmpty()) return null
            val sortedTrend = trend.sortedBy { it.at }
            return MeasurementSummary(
                latest = readings.maxBy { it.at },
                lowest = readings.minBy { it.value },
                highest = readings.maxBy { it.value },
                count = readings.size,
                change = if (readings.size < 2 || sortedTrend.size < 2) {
                    null
                } else {
                    sortedTrend.last().value - sortedTrend.first().value
                },
            )
        }
    }
}
