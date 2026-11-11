package agency.dynamicdata.steps.ui.body

import agency.dynamicdata.steps.core.MeasurementSummary
import agency.dynamicdata.steps.core.MeasurementTrend
import agency.dynamicdata.steps.core.YearPeriod
import agency.dynamicdata.steps.health.BodyMetric
import agency.dynamicdata.steps.health.BodyRepository
import agency.dynamicdata.steps.health.HealthConnectAvailability
import android.util.Log
import java.time.LocalDate

/**
 * Builds the body charts for one year.
 *
 * Reads a little past each end of the year: the trend at the first and last
 * readings of the year averages the 15 days either side, and those days belong to
 * the neighbouring years. Without them the line would bend at every page edge just
 * because half its window was cut off. Only the year's own readings are drawn.
 */
class BodyLoader(private val repository: BodyRepository) {

    suspend fun load(today: LocalDate, yearsBack: Int): BodyState {
        if (repository.availability() != HealthConnectAvailability.AVAILABLE) return BodyState.Unavailable

        val period = YearPeriod.ending(today, yearsBack)
        return BodyState.Loaded(
            period = period,
            yearsBack = yearsBack,
            weight = metric(BodyMetric.WEIGHT, period, today),
            bodyFat = metric(BodyMetric.BODY_FAT, period, today),
            historyBlocked = repository.historyBlocked(),
        )
    }

    private suspend fun metric(metric: BodyMetric, period: YearPeriod, today: LocalDate): MetricState {
        if (!repository.canRead(metric)) return MetricState.PermissionRequired

        val pad = MeasurementTrend.HALF_WINDOW_DAYS
        val from = period.start.minusDays(pad)
        val to = minOf(period.end.plusDays(pad), today)

        return try {
            val all = repository.measurements(metric, from, to).sortedBy { it.at }
            val trend = MeasurementTrend.of(all).filter { it.at.toLocalDate() in period }
            val readings = all.filter { it.at.toLocalDate() in period }
            MetricState.Ready(readings, trend, MeasurementSummary.of(readings, trend))
        } catch (e: SecurityException) {
            // Access was confirmed just above, so this is Health Connect refusing the
            // read, not a missing permission.
            Log.w(TAG, "$metric read refused", e)
            MetricState.Error("Health Connect refused the read: ${e.message ?: "no reason given"}")
        } catch (e: Exception) {
            Log.e(TAG, "$metric read failed", e)
            MetricState.Error("${e.javaClass.simpleName}: ${e.message ?: "no details"}")
        }
    }

    private companion object {
        const val TAG = "BodyLoader"
    }
}
