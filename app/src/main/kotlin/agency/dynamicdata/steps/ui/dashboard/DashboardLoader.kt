package agency.dynamicdata.steps.ui.dashboard

import agency.dynamicdata.steps.core.AverageBasis
import agency.dynamicdata.steps.core.DailyBreakdown
import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.StepGoal
import agency.dynamicdata.steps.core.StepsAverageCalculator
import agency.dynamicdata.steps.core.StepsWindow
import agency.dynamicdata.steps.health.HealthConnectAvailability
import agency.dynamicdata.steps.health.StepsRepository
import android.util.Log
import java.time.LocalDate

/**
 * Builds the in-app view of the same window the widget shows.
 *
 * The summary and the per-day breakdown come from one read, so the headline and the
 * chart can never be computed from different data and disagree on screen.
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
            val readings = repository.dailySteps(window)
            val summary = calculator.summarize(readings, window)

            // No early return for an empty window: the chart still has seven labelled
            // slots to draw, all of them unknown, which says more than a blank screen.
            DashboardState.Ready(
                summary = summary,
                progress = GoalProgress(goal, summary.averageStepsPerDay),
                days = DailyBreakdown.of(readings, window),
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "step read denied", e)
            DashboardState.PermissionRequired
        } catch (e: Exception) {
            Log.e(TAG, "step read failed", e)
            DashboardState.Error
        }
    }

    private companion object {
        const val TAG = "DashboardLoader"
    }
}
