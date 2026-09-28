package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.AverageBasis
import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.StepGoal
import agency.dynamicdata.steps.core.StepsAverageCalculator
import agency.dynamicdata.steps.core.StepsSummary
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
    private val cache: ReadingCache = ReadingCache.None,
    private val days: Int = StepsWindow.DEFAULT_DAYS,
    basis: AverageBasis = AverageBasis.ALL_DAYS,
) {
    private val calculator = StepsAverageCalculator(days, basis)

    /**
     * Why the last [load] could not read, or null if it could. The widget itself
     * falls back quietly; the refresh tap reports this, so a failure says what
     * happened instead of just leaving the old face up.
     */
    var lastFailure: Exception? = null
        private set

    suspend fun load(today: LocalDate, goal: StepGoal): StepsWidgetState {
        lastFailure = null
        when (repository.availability()) {
            HealthConnectAvailability.NOT_SUPPORTED ->
                return StepsWidgetState.HealthConnectUnavailable(updatable = false)
            HealthConnectAvailability.UPDATE_REQUIRED ->
                return StepsWidgetState.HealthConnectUnavailable(updatable = true)
            HealthConnectAvailability.AVAILABLE -> Unit
        }

        if (!repository.hasReadPermission()) return StepsWidgetState.PermissionRequired

        val window = StepsWindow.lastCompleteDays(today, days)

        val summary = try {
            calculator.summarize(repository.dailySteps(window), window)
        } catch (e: SecurityException) {
            // Access was confirmed a moment ago, so this is not a missing permission:
            // Health Connect refuses reads from an app that is not on screen unless it
            // holds the background-read permission, and the widget is nearly always
            // redrawn from the background. Show the last good reading instead of
            // telling the user to allow what they already allowed.
            Log.i(TAG, "step read refused, falling back to the last reading", e)
            lastFailure = e
            return fallback(window, goal)
        } catch (e: Exception) {
            Log.e(TAG, "step read failed, falling back to the last reading", e)
            lastFailure = e
            return fallback(window, goal)
        }

        // Outside the try: failing to remember a good reading is no reason to throw
        // it away and show an older one.
        runCatching { cache.save(summary) }.onFailure { Log.w(TAG, "could not save the reading", it) }
        return stateOf(summary, goal, current = true)
    }

    private suspend fun fallback(window: StepsWindow, goal: StepGoal): StepsWidgetState {
        val last = runCatching { cache.last() }
            .onFailure { Log.w(TAG, "could not read the last reading", it) }
            .getOrNull()
            ?: return StepsWidgetState.Error
        // A reading of this very window is as good as a fresh one: the days in it are
        // over. One from an earlier window is still shown — a real number beats a
        // blank — but flagged, so it is never mistaken for this week's.
        return stateOf(last, goal, current = last.window == window)
    }

    private fun stateOf(summary: StepsSummary, goal: StepGoal, current: Boolean): StepsWidgetState =
        if (summary.hasNoData) {
            StepsWidgetState.NoData(summary)
        } else {
            StepsWidgetState.Ready(
                summary = summary,
                // Against today's goal, not the one saved with the reading: the goal
                // is a setting, and changing it should show up at once.
                progress = GoalProgress(goal, summary.averageStepsPerDay),
                current = current,
            )
        }

    private companion object {
        const val TAG = "StepsWidgetLoader"
    }
}
