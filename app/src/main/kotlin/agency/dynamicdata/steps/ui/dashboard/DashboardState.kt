package agency.dynamicdata.steps.ui.dashboard

import agency.dynamicdata.steps.core.DayEntry
import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.StepsSummary

/** What the in-app screen is showing above its settings. */
sealed interface DashboardState {

    data object Loading : DashboardState

    data class HealthConnectUnavailable(val updatable: Boolean) : DashboardState

    data object PermissionRequired : DashboardState

    /**
     * A reading. [days] always has one entry per day of the window, in order,
     * including the days with nothing recorded.
     */
    data class Ready(
        val summary: StepsSummary,
        val progress: GoalProgress,
        val days: List<DayEntry>,
    ) : DashboardState {

        /** The tallest bar the chart has to fit, ignoring days with no answer. */
        val highestDay: Long get() = days.mapNotNull { it.steps }.maxOrNull() ?: 0L

        val trackedDays: Int get() = days.count { it.isTracked }
    }

    data object Error : DashboardState
}
