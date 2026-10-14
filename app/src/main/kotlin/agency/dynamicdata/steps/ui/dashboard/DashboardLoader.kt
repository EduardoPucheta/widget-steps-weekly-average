package agency.dynamicdata.steps.ui.dashboard

import agency.dynamicdata.steps.core.AverageBasis
import agency.dynamicdata.steps.core.DailyBreakdown
import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.MovingAverage
import agency.dynamicdata.steps.core.StepGoal
import agency.dynamicdata.steps.core.StepsAverageCalculator
import agency.dynamicdata.steps.core.StepsWindow
import agency.dynamicdata.steps.health.HealthConnectAvailability
import agency.dynamicdata.steps.health.StepsRepository
import android.util.Log
import java.time.LocalDate

/**
 * Builds the in-app view of the same window the widget shows, plus today so far.
 *
 * The summary, the per-day breakdown, the moving average and today all come from one
 * read, so the numbers on screen can never be computed from different data and
 * disagree. That read reaches back further than the window — the first point of a
 * 7-day average needs the six days before it — and forward to today, which is shown
 * on its own and never enters the average.
 */
class DashboardLoader(
    private val repository: StepsRepository,
    private val days: Int = StepsWindow.DEFAULT_DAYS,
    basis: AverageBasis = AverageBasis.ALL_DAYS,
) {
    private val calculator = StepsAverageCalculator(days, basis)

    suspend fun load(today: LocalDate, goal: StepGoal): DashboardState {
        when (repository.availability()) {
            HealthConnectAvailability.NOT_SUPPORTED ->
                return DashboardState.HealthConnectUnavailable(updatable = false)
            HealthConnectAvailability.UPDATE_REQUIRED ->
                return DashboardState.HealthConnectUnavailable(updatable = true)
            HealthConnectAvailability.AVAILABLE -> Unit
        }

        if (!repository.hasReadPermission()) return DashboardState.PermissionRequired

        val window = StepsWindow.lastCompleteDays(today, days)

        return try {
            val history = MovingAverage.readWindowFor(window, days)
            val readings = repository.dailySteps(StepsWindow(history.start, today))
            val summary = calculator.summarize(readings, window)

            // No early return for an empty window: the chart still has seven labelled
            // slots to draw, all of them unknown, which says more than a blank screen.
            DashboardState.Ready(
                summary = summary,
                progress = GoalProgress(goal, summary.averageStepsPerDay),
                days = DailyBreakdown.of(readings, window),
                movingAverage = MovingAverage.of(readings, window, calculator, days),
                // Everything above filters to the window, which ends yesterday, so
                // today's partial count cannot leak into the average.
                today = readings.filter { it.date == today }
                    .takeIf { it.isNotEmpty() }
                    ?.sumOf { it.steps },
            )
        } catch (e: SecurityException) {
            // Access was confirmed just above, so this is Health Connect refusing the
            // read, not a missing permission. Saying "allow step access" here sent
            // people to grant something they already had.
            Log.w(TAG, "step read refused", e)
            DashboardState.Error("Health Connect refused the read: ${e.message ?: "no reason given"}")
        } catch (e: Exception) {
            Log.e(TAG, "step read failed", e)
            DashboardState.Error("${e.javaClass.simpleName}: ${e.message ?: "no details"}")
        }
    }

    private companion object {
        const val TAG = "DashboardLoader"
    }
}
