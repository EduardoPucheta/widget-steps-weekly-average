package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.AverageBasis
import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.StepGoal
import agency.dynamicdata.steps.core.StepsAverageCalculator
import agency.dynamicdata.steps.core.StepsWindow
import agency.dynamicdata.steps.health.HealthConnectAvailability
import agency.dynamicdata.steps.health.StepsRepository
import android.util.Log
import java.time.LocalDate

/**
 * Assembles the widget's state: check access, read the data, run the arithmetic.
 *
 * Kept apart from the Glance composable so the decision tree can be reasoned about
 * (and tested) without rendering anything.
 */
class StepsWidgetStateLoader(
    private val repository: StepsRepository,
    private val days: Int = StepsWindow.DEFAULT_DAYS,
    basis: AverageBasis = AverageBasis.ALL_DAYS,
) {
    private val calculator = StepsAverageCalculator(days, basis)

    suspend fun load(today: LocalDate, goal: StepGoal): StepsWidgetState {
        when (repository.availability()) {
            HealthConnectAvailability.NOT_SUPPORTED ->
                return StepsWidgetState.HealthConnectUnavailable(updatable = false)
            HealthConnectAvailability.UPDATE_REQUIRED ->
                return StepsWidgetState.HealthConnectUnavailable(updatable = true)
            HealthConnectAvailability.AVAILABLE -> Unit
        }

        if (!repository.hasReadPermission()) return StepsWidgetState.PermissionRequired

        val window = StepsWindow.lastCompleteDays(today, days)

        return try {
            val summary = calculator.summarize(repository.dailySteps(window), window)
            if (summary.hasNoData) {
                StepsWidgetState.NoData(summary)
            } else {
                StepsWidgetState.Ready(
                    summary = summary,
                    progress = GoalProgress(goal, summary.averageStepsPerDay),
                )
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check above and the read.
            Log.w(TAG, "step read denied", e)
            StepsWidgetState.PermissionRequired
        } catch (e: Exception) {
            Log.e(TAG, "step read failed", e)
            StepsWidgetState.Error
        }
    }

    private companion object {
        const val TAG = "StepsWidgetLoader"
    }
}
