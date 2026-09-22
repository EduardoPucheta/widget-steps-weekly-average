package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.AverageBasis
import agency.dynamicdata.steps.core.WeekOverWeekTrend
import agency.dynamicdata.steps.core.WeekWindow
import agency.dynamicdata.steps.core.WeeklyStepsCalculator
import agency.dynamicdata.steps.health.HealthConnectAvailability
import agency.dynamicdata.steps.health.StepsRepository
import android.util.Log
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Assembles the widget's state: check access, read the data, run the arithmetic.
 *
 * Kept apart from the Glance composable so the decision tree can be reasoned about
 * (and tested) without rendering anything.
 */
class StepsWidgetStateLoader(
    private val repository: StepsRepository,
    private val firstDayOfWeek: DayOfWeek,
    basis: AverageBasis = AverageBasis.ELAPSED_DAYS,
) {
    private val calculator = WeeklyStepsCalculator(firstDayOfWeek, basis)

    suspend fun load(today: LocalDate): StepsWidgetState {
        when (repository.availability()) {
            HealthConnectAvailability.NOT_SUPPORTED ->
                return StepsWidgetState.HealthConnectUnavailable(updatable = false)
            HealthConnectAvailability.UPDATE_REQUIRED ->
                return StepsWidgetState.HealthConnectUnavailable(updatable = true)
            HealthConnectAvailability.AVAILABLE -> Unit
        }

        if (!repository.hasReadPermission()) return StepsWidgetState.PermissionRequired

        val thisWeek = WeekWindow.containing(today, firstDayOfWeek)
        val lastWeek = WeekWindow.before(today, firstDayOfWeek, weeksAgo = 1)

        return try {
            // Last week is read alongside this one so the widget can show a trend;
            // both summaries are then derived from the same list.
            val history = repository.dailySteps(lastWeek) + repository.dailySteps(thisWeek)

            val summary = calculator.summarize(history, thisWeek, today)
            if (summary.hasNoData) {
                StepsWidgetState.NoData(summary)
            } else {
                val previous = calculator.summarize(history, lastWeek, today)
                StepsWidgetState.Ready(summary, WeekOverWeekTrend.of(summary, previous))
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check above and the read.
            Log.w(TAG, "step read denied", e)
            StepsWidgetState.PermissionRequired
        } catch (e: Exception) {
            Log.e(TAG, "step read failed", e)
            StepsWidgetState.Error(summary = null)
        }
    }

    private companion object {
        const val TAG = "StepsWidgetLoader"
    }
}
